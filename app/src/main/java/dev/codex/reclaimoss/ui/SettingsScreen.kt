package dev.codex.reclaimoss.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.reclaimoss.AppGraph
import dev.codex.reclaimoss.data.repository.PlannerSnapshot
import dev.codex.reclaimoss.domain.model.BlockLockState
import dev.codex.reclaimoss.domain.model.PreferredTimeOfDay
import dev.codex.reclaimoss.domain.model.RecurrenceRule
import dev.codex.reclaimoss.domain.model.RecurrenceType
import dev.codex.reclaimoss.domain.model.Reminder
import dev.codex.reclaimoss.domain.model.ReminderStatus
import dev.codex.reclaimoss.domain.model.ScheduleBlock
import dev.codex.reclaimoss.domain.model.ScheduleTask
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.TaskStatus
import dev.codex.reclaimoss.domain.model.TimePeriod
import dev.codex.reclaimoss.domain.model.TimePeriodType
import dev.codex.reclaimoss.domain.scheduling.ScheduleRebuildReason
import dev.codex.reclaimoss.domain.service.PlannerCoordinator
import dev.codex.reclaimoss.domain.service.TaskCreationResult
import dev.codex.reclaimoss.settings.AppSettings
import dev.codex.reclaimoss.settings.HistoryRetention
import dev.codex.reclaimoss.settings.DateFormatPreference
import dev.codex.reclaimoss.settings.FontSizeScale
import dev.codex.reclaimoss.settings.ReminderTimingMode
import dev.codex.reclaimoss.settings.TasksViewMode
import dev.codex.reclaimoss.settings.ThemeMode
import dev.codex.reclaimoss.settings.WeekStart
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String) {
    Appearance("Appearance"),
    TaskRules("Task Rules"),
    Reminders("Reminders"),
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    settings: AppSettings,
    isActive: Boolean = true,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onFontSizeScaleChanged: (FontSizeScale) -> Unit,
    onTasksViewModeChanged: (TasksViewMode) -> Unit,
    onBreakBufferChanged: (Int) -> Unit,
    onAllowTaskSplittingChanged: (Boolean) -> Unit,
    onDefaultTaskSplittingChanged: (Boolean) -> Unit = {},
    onAllowConcurrentTasksChanged: (Boolean) -> Unit,
    onDefaultTaskReminderChanged: (Boolean) -> Unit,
    onReminderTimingModeChanged: (ReminderTimingMode) -> Unit,
    onHistoryRetentionChanged: (HistoryRetention) -> Unit,
    onTaskHourHeightDpChanged: (Int) -> Unit = {},
) {
    val isDarkSettings = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    LaunchedEffect(isActive) {
        if (!isActive) {
            section = null
        }
    }
    BackHandler(enabled = section != null) {
        section = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (section == null) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = settingsPrimaryTextColor(isDarkSettings),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    listOf(
                        SettingsSection.TaskRules,
                        SettingsSection.Appearance,
                        SettingsSection.Reminders,
                    ).forEach { item ->
                        item {
                            SettingsNavigationRow(
                                title = item.title,
                                icon = settingsSectionIcon(item),
                                isDarkSettings = isDarkSettings,
                                onClick = { section = item },
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = { section = null }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                                tint = settingsPrimaryTextColor(isDarkSettings),
                            )
                        }
                        Text(
                            section?.title ?: "Settings",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = settingsPrimaryTextColor(isDarkSettings),
                        )
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    item {
                        when (section) {
                        SettingsSection.Appearance -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsControlRow(
                                title = "Theme",
                            ) {
                                SegmentedEnumRow(
                                    options = ThemeMode.entries,
                                    selected = settings.themeMode,
                                    labelFor = {
                                        when (it) {
                                            ThemeMode.SYSTEM -> "System"
                                            ThemeMode.LIGHT -> "Light"
                                            ThemeMode.DARK -> "Dark"
                                        }
                                    },
                                    onSelected = onThemeModeChanged,
                                )
                            }
                            SettingsControlRow(
                                title = "Font size",
                            ) {
                                SegmentedEnumRow(
                                    options = FontSizeScale.entries,
                                    selected = settings.fontSizeScale,
                                    labelFor = {
                                        when (it) {
                                            FontSizeScale.SMALL -> "90%"
                                            FontSizeScale.DEFAULT -> "100%"
                                            FontSizeScale.LARGE -> "115%"
                                            FontSizeScale.EXTRA_LARGE -> "130%"
                                        }
                                    },
                                    onSelected = onFontSizeScaleChanged,
                                )
                            }
                            SettingsControlRow(
                                title = "Tasks view",
                            ) {
                                SegmentedEnumRow(
                                    options = TasksViewMode.entries,
                                    selected = settings.tasksViewMode,
                                    labelFor = {
                                        when (it) {
                                            TasksViewMode.COLLAPSED -> "Collapsed"
                                            TasksViewMode.EXPANDED -> "Expanded"
                                        }
                                    },
                                    onSelected = onTasksViewModeChanged,
                                )
                            }
                            SettingsControlRow(
                                title = "Hour spacing",
                            ) {
                                HourSpacingSlider(
                                    valueDp = settings.taskHourHeightDp,
                                    onValueChanged = onTaskHourHeightDpChanged,
                                )
                            }
                        }

                        SettingsSection.TaskRules -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            SettingsControlRow(
                                title = "Break buffer",
                            ) {
                                DurationSlider(
                                    minutes = settings.breakBufferMinutes,
                                    minMinutes = 0,
                                    maxMinutes = 60,
                                    onMinutesChanged = onBreakBufferChanged,
                                )
                            }
                            SettingsInlineSwitchRow(
                                title = "Allow task splitting",
                                checked = settings.allowTaskSplitting,
                                onCheckedChange = onAllowTaskSplittingChanged,
                            )
                            SettingsInlineSwitchRow(
                                title = "Default task splitting",
                                checked = settings.defaultTaskSplitting,
                                onCheckedChange = onDefaultTaskSplittingChanged,
                            )
                            SettingsInlineSwitchRow(
                                title = "Allow concurrent tasks",
                                checked = settings.allowConcurrentTasks,
                                onCheckedChange = onAllowConcurrentTasksChanged,
                            )
                            SettingsControlRow(
                                title = "Keep completed tasks",
                            ) {
                                EnumDropdownRow(
                                    title = "Retention",
                                    selected = settings.historyRetention,
                                    options = HistoryRetention.entries,
                                    labelFor = {
                                        when (it) {
                                            HistoryRetention.SEVEN_DAYS -> "7 days"
                                            HistoryRetention.THIRTY_DAYS -> "30 days"
                                            HistoryRetention.FOREVER -> "Forever"
                                        }
                                    },
                                    onSelected = onHistoryRetentionChanged,
                                )
                            }
                        }

                        SettingsSection.Reminders -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsInlineSwitchRow(
                                title = "Default reminder for tasks",
                                checked = settings.defaultTaskReminder,
                                onCheckedChange = onDefaultTaskReminderChanged,
                            )
                            SettingsControlRow(
                                title = "Reminder timing for tasks",
                            ) {
                                SegmentedEnumRow(
                                    options = ReminderTimingMode.entries,
                                    selected = settings.reminderTimingMode,
                                    labelFor = {
                                        when (it) {
                                            ReminderTimingMode.AT_DUE_DATE -> "At due date"
                                            ReminderTimingMode.AT_TASK_TIME -> "At task time"
                                        }
                                    },
                                    onSelected = onReminderTimingModeChanged,
                                )
                            }
                        }

                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsNavigationRow(
    title: String,
    icon: ImageVector,
    isDarkSettings: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = settingsPrimaryTextColor(isDarkSettings),
                modifier = Modifier.size(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = settingsPrimaryTextColor(isDarkSettings),
                )
            }
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = title,
            tint = settingsSecondaryTextColor(isDarkSettings),
        )
    }
}

@Composable
fun SettingsControlRow(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDarkSettings = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = settingsPrimaryTextColor(isDarkSettings),
        )
        content()
    }
}

@Composable
fun SettingsInlineSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val isDarkSettings = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = settingsPrimaryTextColor(isDarkSettings),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun <T> SegmentedEnumRow(
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val isSelected = selected == option
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { onSelected(option) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            labelFor(option),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T> EnumDropdownRow(
    title: String,
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    TaskSectionTitle(title)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                labelFor(selected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(labelFor(option)) },
                onClick = {
                    onSelected(option)
                    expanded = false
                },
            )
        }
    }
}

@Composable
fun EmptyCard(message: String) {
    val isDarkSettings = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = settingsSurfaceColor(isDarkSettings)),
        border = androidx.compose.foundation.BorderStroke(1.dp, settingsDividerColor(isDarkSettings)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Text(
            message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = settingsSecondaryTextColor(isDarkSettings),
        )
    }
}

@Composable
private fun HourSpacingSlider(
    valueDp: Int,
    onValueChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "${valueDp}dp per hour",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Slider(
            value = valueDp.toFloat(),
            onValueChange = { raw ->
                val snapped = ((raw / 12f).roundToInt() * 12).coerceIn(72, 240)
                onValueChanged(snapped)
            },
            valueRange = 72f..240f,
            steps = 13,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Compact", style = MaterialTheme.typography.labelSmall)
            Text("Spacious", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun settingsSectionIcon(section: SettingsSection): ImageVector =
    when (section) {
        SettingsSection.Appearance -> Icons.Outlined.Settings
        SettingsSection.TaskRules -> Icons.Outlined.Checklist
        SettingsSection.Reminders -> Icons.Outlined.Notifications
    }

@Composable
private fun settingsSurfaceColor(isDarkSettings: Boolean): Color =
    MaterialTheme.colorScheme.surface

@Composable
private fun settingsPrimaryTextColor(isDarkSettings: Boolean): Color =
    MaterialTheme.colorScheme.onSurface

@Composable
private fun settingsSecondaryTextColor(isDarkSettings: Boolean): Color =
    MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun settingsDividerColor(isDarkSettings: Boolean): Color =
    MaterialTheme.colorScheme.outlineVariant

fun recurrenceSummary(rule: RecurrenceRule): String =
    when (rule.type) {
        RecurrenceType.NONE -> "One-time"
        RecurrenceType.DAILY -> buildString {
            append("Every ${rule.interval} day")
            if (rule.interval != 1) append("s")
            rule.until?.let {
                append(" until ")
                append(it.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d")))
            }
        }
        RecurrenceType.WEEKLY -> buildString {
            append("Every ${rule.interval} week")
            if (rule.interval != 1) append("s")
            append(": ")
            append(rule.daysOfWeek.sortedBy { it.value }.joinToString(", ") { it.shortLabel() })
            rule.until?.let {
                append(" until ")
                append(it.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d")))
            }
        }
        RecurrenceType.MONTHLY -> buildString {
            append("Every ${rule.interval} month")
            if (rule.interval != 1) append("s")
            rule.until?.let {
                append(" until ")
                append(it.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d")))
            }
        }
    }

fun periodDropdownLabel(period: TimePeriod): String =
    "${period.label} - ${period.start.formatAsClock()} - ${period.end.formatAsClock()}"

fun RecurrenceType.displayName(): String =
    when (this) {
        RecurrenceType.NONE -> "Once"
        RecurrenceType.DAILY -> "Daily"
        RecurrenceType.WEEKLY -> "Weekly"
        RecurrenceType.MONTHLY -> "Monthly"
    }

fun DayOfWeek.shortLabel(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun Set<DayOfWeek>.toggle(day: DayOfWeek): Set<DayOfWeek> =
    if (day in this) this - day else this + day

fun TimePeriod?.toPreferredTimeOfDay(): PreferredTimeOfDay {
    if (this == null) return PreferredTimeOfDay.ANYTIME
    return when {
        start < LocalTime.NOON -> PreferredTimeOfDay.MORNING
        start < LocalTime.of(14, 0) -> PreferredTimeOfDay.NOON
        start < LocalTime.of(18, 0) -> PreferredTimeOfDay.AFTERNOON
        else -> PreferredTimeOfDay.NIGHT
    }
}

fun LocalTime.formatAsClock(): String =
    format(DateTimeFormatter.ofPattern("h:mm a"))

fun LocalTime.formatHourLabel(): String =
    format(DateTimeFormatter.ofPattern("HH:mm"))

fun minutesFromStart(time: LocalTime): Int =
    time.hour * 60 + time.minute

fun periodDurationMinutes(start: LocalTime, end: LocalTime): Int {
    val startMinutes = minutesFromStart(start)
    val endMinutes = minutesFromStart(end)
    val raw = if (endMinutes > startMinutes) endMinutes - startMinutes else (24 * 60 - startMinutes) + endMinutes
    return raw.coerceAtLeast(30)
}

data class PeriodSegment(
    val start: LocalTime,
    val minutes: Int,
)

fun findOverlappingTimePeriod(
    candidate: TimePeriod,
    periods: List<TimePeriod>,
): TimePeriod? {
    val candidateRanges = periodSegments(candidate).map { segment ->
        minutesFromStart(segment.start) to (minutesFromStart(segment.start) + segment.minutes).coerceAtMost(24 * 60)
    }
    return periods
        .asSequence()
        .filterNot { it.id == candidate.id }
        .firstOrNull { existing ->
            periodSegments(existing).any { segment ->
                val existingStart = minutesFromStart(segment.start)
                val existingEnd = (existingStart + segment.minutes).coerceAtMost(24 * 60)
                candidateRanges.any { (candidateStart, candidateEnd) ->
                    candidateStart < existingEnd && existingStart < candidateEnd
                }
            }
        }
}

fun timePeriodOverlapMessage(
    candidateLabel: String,
    overlappingPeriod: TimePeriod,
): String {
    val subject = candidateLabel.ifBlank { "This period" }
    return "$subject overlaps with ${overlappingPeriod.label} (${overlappingPeriod.start.formatAsClock()} - ${overlappingPeriod.end.formatAsClock()}). Choose a different time."
}

fun periodSegments(period: TimePeriod): List<PeriodSegment> {
    val startMinutes = minutesFromStart(period.start)
    val endMinutes = minutesFromStart(period.end)
    return if (endMinutes > startMinutes) {
        listOf(PeriodSegment(period.start, endMinutes - startMinutes))
    } else {
        listOf(
            PeriodSegment(period.start, 24 * 60 - startMinutes),
            PeriodSegment(LocalTime.MIDNIGHT, endMinutes),
        ).filter { it.minutes > 0 }
    }
}

fun minutesToLocalTime(minutes: Int): LocalTime {
    val normalized = minutes.coerceIn(0, 24 * 60)
    if (normalized == 24 * 60) return LocalTime.MIDNIGHT
    return LocalTime.of(normalized / 60, normalized % 60)
}

fun snapToStep(value: Int, step: Int): Int =
    ((value + step / 2) / step) * step

fun timelineOffset(minutes: Int, hourHeight: Dp): Dp =
    (hourHeight.value * minutes.toFloat() / 60f).dp

fun timelineBlockHeight(minutes: Int, hourHeight: Dp, minHeight: Dp): Dp {
    val proportional = timelineOffset(minutes.coerceAtLeast(30), hourHeight)
    return if (proportional < minHeight) minHeight else proportional
}

fun headerDateLabel(date: LocalDate, formatPreference: DateFormatPreference): String {
    val order = when (formatPreference) {
        DateFormatPreference.MONTH_DAY_YEAR -> "${date.monthValue}/${date.dayOfMonth}"
        DateFormatPreference.DAY_MONTH_YEAR -> "${date.dayOfMonth}/${date.monthValue}"
    }
    return "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $order"
}

fun reminderDateTimeFormatter(formatPreference: DateFormatPreference): DateTimeFormatter =
    when (formatPreference) {
        DateFormatPreference.MONTH_DAY_YEAR -> DateTimeFormatter.ofPattern("MMM d, h:mm a")
        DateFormatPreference.DAY_MONTH_YEAR -> DateTimeFormatter.ofPattern("d MMM, h:mm a")
    }

fun Int.durationLabel(): String =
    if (this < 60) "${this}m" else "${this / 60}h${if (this % 60 == 0) "" else " ${this % 60}m"}"

fun taskDotColor(count: Int): Color =
    when {
        count <= 1 -> Color(0xFF9FD0B6)
        count == 2 -> Color(0xFF74AE92)
        count == 3 -> Color(0xFF4E8C70)
        else -> Color(0xFF2E6A52)
    }

fun String.titlecase(): String =
    lowercase().replaceFirstChar { it.titlecase() }
