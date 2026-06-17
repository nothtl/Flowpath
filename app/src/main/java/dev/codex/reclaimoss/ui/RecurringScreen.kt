package dev.codex.reclaimoss.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.codex.reclaimoss.domain.model.RecurrenceType
import dev.codex.reclaimoss.domain.model.ScheduleTask
import dev.codex.reclaimoss.domain.model.TaskKind
import dev.codex.reclaimoss.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// -- helpers (keep existing) --

fun sleepCoverageByWeekday(tasks: List<ScheduleTask>): Set<DayOfWeek> {
    val sleepTasks = tasks.filter { it.taskKind == TaskKind.SLEEP }
    if (sleepTasks.isEmpty()) return emptySet()
    // If any sleep covers all days (daily), all days are covered
    if (sleepTasks.any { it.recurrenceRule.type == RecurrenceType.DAILY }) {
        return DayOfWeek.entries.toSet()
    }
    // Weekly: covered days from daysOfWeek
    val weeklyDays = sleepTasks
        .filter { it.recurrenceRule.type == RecurrenceType.WEEKLY }
        .flatMap { it.recurrenceRule.daysOfWeek }
        .toSet()
    if (weeklyDays.isNotEmpty()) return weeklyDays
    // Monthly or one-time: check which days have blocks scheduled
    val zoneId = ZoneId.systemDefault()
    return sleepTasks
        .flatMap { task ->
            val startDay = task.fixedStartAt?.atZone(zoneId)?.dayOfWeek
            listOfNotNull(startDay)
        }
        .toSet()
}

fun sleepTimeForDay(tasks: List<ScheduleTask>, day: DayOfWeek): Pair<LocalTime, LocalTime>? {
    val zoneId = ZoneId.systemDefault()
    val task = tasks.firstOrNull { task ->
        if (task.taskKind != TaskKind.SLEEP) return@firstOrNull false
        when (task.recurrenceRule.type) {
            RecurrenceType.DAILY -> true
            RecurrenceType.WEEKLY -> day in task.recurrenceRule.daysOfWeek
            RecurrenceType.MONTHLY -> task.fixedStartAt?.atZone(zoneId)?.dayOfWeek == day
            RecurrenceType.NONE -> task.fixedStartAt?.atZone(zoneId)?.dayOfWeek == day
        }
    } ?: return null
    val start = task.fixedStartAt?.atZone(zoneId)?.toLocalTime() ?: return null
    val end = task.fixedEndAt?.atZone(zoneId)?.toLocalTime() ?: return null
    return start to end
}

private fun weekdayLabel(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun weekdayLabelShort(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).first().toString()

private fun recurrenceSummary(task: ScheduleTask): String {
    val rule = task.recurrenceRule
    if (rule.type == RecurrenceType.NONE) return "One-time"
    val interval = if (rule.interval > 1) "Every ${rule.interval} " else "Every "
    val unit = when (rule.type) {
        RecurrenceType.DAILY -> if (rule.interval > 1) "days" else "day"
        RecurrenceType.WEEKLY -> if (rule.interval > 1) "weeks" else "week"
        RecurrenceType.MONTHLY -> if (rule.interval > 1) "months" else "month"
        RecurrenceType.NONE -> ""
    }
    val days = if (rule.type == RecurrenceType.WEEKLY && rule.daysOfWeek.isNotEmpty()) {
        " on " + rule.daysOfWeek.sortedBy { it.value }.joinToString(", ") { weekdayLabel(it) }
    } else ""
    val until = when (rule.endMode) {
        dev.codex.reclaimoss.domain.model.RecurrenceEndMode.NEVER -> ", forever"
        dev.codex.reclaimoss.domain.model.RecurrenceEndMode.ON_DATE -> ", until ${rule.until?.atZone(ZoneId.systemDefault())?.toLocalDate()?.toString() ?: "?"}"
        dev.codex.reclaimoss.domain.model.RecurrenceEndMode.AFTER_OCCURRENCES -> ", ${rule.occurrenceCount ?: "?"} times"
    }
    return "$interval$unit$days$until"
}

// -- screen --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    existingSleepTasks: List<ScheduleTask>,
    allTasks: List<ScheduleTask>,
    onBack: () -> Unit,
    onAddSleep: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val covered = remember(existingSleepTasks) { sleepCoverageByWeekday(existingSleepTasks) }
    val coveredCount = covered.size
    val sleepConfigured = coveredCount == 7

    // One card per recurring series (grouped by series ID or task ID)
    val recurringSeries = remember(allTasks) {
        allTasks
            .filter {
                it.taskKind != TaskKind.SLEEP &&
                    it.taskKind != TaskKind.BLOCKER &&
                    it.recurrenceRule.type != RecurrenceType.NONE &&
                    it.status == TaskStatus.ACTIVE
            }
            .groupBy { it.recurrenceSeriesId ?: it.id }
            .mapNotNull { (_, tasks) -> tasks.minByOrNull { it.dueAt } }
            .sortedBy { it.dueAt }
    }

    var expandedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var sleepExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // --- SLEEP CARD (always first) ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sleepExpanded = !sleepExpanded }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Sleep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Icon(
                                if (sleepExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (sleepExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Dropdown: per-day times
                        if (sleepExpanded && coveredCount > 0) {
                            Column(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                covered.sortedBy { it.value }.forEach { day ->
                                    val sleepTask = existingSleepTasks.firstOrNull {
                                        it.taskKind == TaskKind.SLEEP && day in it.recurrenceRule.daysOfWeek
                                    }
                                    val time = sleepTimeForDay(existingSleepTasks, day) ?: return@forEach
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (sleepTask != null) Modifier.clickable { onOpenTask(sleepTask.id) }
                                                else Modifier
                                            )
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(weekdayLabel(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${time.first.formatAsClock()} – ${time.second.formatAsClock()}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (sleepTask != null) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Outlined.ChevronRight, null, Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- RECURRING TASK CARDS (one per series) ---
            if (recurringSeries.isEmpty()) {
                item {
                    Text(
                        "No recurring tasks yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            } else {
                items(recurringSeries, key = { it.recurrenceSeriesId ?: it.id }) { task ->
                    val occurrenceCount = allTasks.count {
                        it.recurrenceSeriesId == task.recurrenceSeriesId && it.status == TaskStatus.ACTIVE
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTask(task.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
                                Icon(
                                    Icons.Outlined.ChevronRight, null, Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                            }
                            Text(
                                recurrenceSummary(task),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
