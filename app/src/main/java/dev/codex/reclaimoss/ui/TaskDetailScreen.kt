package dev.codex.reclaimoss.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.material3.TextButton
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
import dev.codex.reclaimoss.domain.model.RecurrenceEndMode
import dev.codex.reclaimoss.domain.model.RecurrenceRule
import dev.codex.reclaimoss.domain.model.RecurrenceType
import dev.codex.reclaimoss.domain.model.Reminder
import dev.codex.reclaimoss.domain.model.ReminderStatus
import dev.codex.reclaimoss.domain.model.ScheduleBlock
import dev.codex.reclaimoss.domain.model.ScheduleTask
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.Timeframe
import dev.codex.reclaimoss.domain.model.TaskStatus
import dev.codex.reclaimoss.domain.model.TimePeriod
import dev.codex.reclaimoss.domain.model.TimePeriodType
import dev.codex.reclaimoss.domain.scheduling.ScheduleRebuildReason
import dev.codex.reclaimoss.domain.service.PlannerCoordinator
import dev.codex.reclaimoss.domain.service.TaskCreationResult
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.min
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun TaskDetailScreen(
    padding: PaddingValues,
    task: ScheduleTask,
    blocks: List<ScheduleBlock>,
    linkedReminder: Reminder?,
    timeframes: List<Timeframe> = emptyList(),
    onBack: () -> Unit,
    onAddReminder: () -> Unit,
    onFollowUp: () -> Unit,
    onEdit: () -> Unit,
    onReschedule: () -> Unit,
    onDone: (ScheduleBlock) -> Unit,
    onDoneAllRecurring: () -> Unit,
    onDelete: () -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val repeatCalendarInitialMonth = remember(task.dueAt, zoneId) {
        defaultRepeatCalendarMonth(task.dueAt, zoneId)
    }
    val firstBlock = blocks.firstOrNull()
    var showActionsMenu by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showDoneAllConfirm by rememberSaveable { mutableStateOf(false) }
    var showRepeatCalendar by rememberSaveable { mutableStateOf(false) }
    var repeatCalendarMonth by remember(task.id, repeatCalendarInitialMonth) {
        mutableStateOf(repeatCalendarInitialMonth)
    }
    val isRecurringTask = task.recurrenceSeriesId != null || task.recurrenceRule.type != RecurrenceType.NONE
    val sortedBlocks = remember(blocks) { blocks.sortedBy { it.startAt } }
    val timeframeName = remember(timeframes, task.timeframeId) {
        task.timeframeId?.let { id -> timeframes.find { it.id == id }?.name }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit task")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete task")
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sortedBlocks.scheduleDisplayText(timeFormatter, zoneId),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (task.priority == TaskPriority.URGENT) {
                            Text(
                                "· Urgent",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (task.description.isNotBlank()) {
                        Text(task.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    val hasDivider = { false } // placeholder, handled in loop
                    // Deadline
                    if (task.hasDeadline) {
                        TaskInfoRow("Deadline", task.dueDisplayText(formatter, zoneId))
                    }
                    // Window
                    if (task.preferredTimeOfDay != PreferredTimeOfDay.ANYTIME) {
                        if (task.hasDeadline) TaskInfoDivider()
                        TaskInfoRow("Window", task.preferredTimeOfDay.displayText())
                    }
                    // Timeframe
                    if (timeframeName != null) {
                        if (task.hasDeadline || task.preferredTimeOfDay != PreferredTimeOfDay.ANYTIME) TaskInfoDivider()
                        TaskInfoRow("Timeframe", timeframeName)
                    }
                    // Schedule (repeat with calendar)
                    if (task.recurrenceRule.type != RecurrenceType.NONE) {
                        if (task.hasDeadline || task.preferredTimeOfDay != PreferredTimeOfDay.ANYTIME || timeframeName != null) TaskInfoDivider()
                        RepeatDetailRow(
                            rule = task.recurrenceRule,
                            dueAt = task.dueAt,
                            zoneId = zoneId,
                            onClick = {
                                repeatCalendarMonth = repeatCalendarInitialMonth
                                showRepeatCalendar = true
                            },
                        )
                    }
                    // Dependency splitting
                    if (!task.allowSplitting) {
                        if (task.hasDeadline || task.preferredTimeOfDay != PreferredTimeOfDay.ANYTIME || timeframeName != null || task.recurrenceRule.type != RecurrenceType.NONE) TaskInfoDivider()
                        TaskInfoRow("Splitting", "Disabled")
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            firstBlock?.let { onDone(it) }
                        },
                        enabled = firstBlock != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(if (firstBlock != null) "Done" else "Not scheduled")
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showActionsMenu = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Icon(Icons.Outlined.MoreHoriz, contentDescription = "More options")
                            Spacer(Modifier.width(8.dp))
                            Text("More")
                        }
                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false },
                            offset = DpOffset(x = 0.dp, y = 8.dp),
                            shape = RoundedCornerShape(24.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            shadowElevation = 12.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.widthIn(min = 220.dp, max = 240.dp),
                        ) {
                            if (isRecurringTask) {
                                ActionMenuItem(
                                    label = "Done all recurring",
                                    icon = Icons.Outlined.Checklist,
                                    onClick = {
                                        showActionsMenu = false
                                        showDoneAllConfirm = true
                                    },
                                )
                            }
                            ActionMenuItem(
                                label = if (linkedReminder == null) "Add Reminder" else "Dismiss Reminder",
                                icon = Icons.Outlined.Notifications,
                                onClick = {
                                    showActionsMenu = false
                                    onAddReminder()
                                },
                            )
                            ActionMenuItem(
                                label = "Follow up",
                                icon = Icons.Outlined.Add,
                                onClick = {
                                    showActionsMenu = false
                                    onFollowUp()
                                },
                            )
                            ActionMenuItem(
                                label = "Reschedule",
                                icon = Icons.Outlined.CalendarMonth,
                                onClick = {
                                    showActionsMenu = false
                                    onReschedule()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete task?") },
            text = { Text("Are you sure?") },
        )
    }

    if (showDoneAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDoneAllConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDoneAllConfirm = false
                        onDoneAllRecurring()
                    },
                ) {
                    Text("Done all")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDoneAllConfirm = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Done all recurring?") },
            text = { Text("Are you sure?") },
        )
    }

    if (showRepeatCalendar) {
        RepeatCalendarSheet(
            taskTitle = task.title,
            rule = task.recurrenceRule,
            dueAt = task.dueAt,
            zoneId = zoneId,
            visibleMonth = repeatCalendarMonth,
            onVisibleMonthChange = { repeatCalendarMonth = it },
            onDismiss = { showRepeatCalendar = false },
        )
    }
}

@Composable
fun ReminderDetailScreen(
    padding: PaddingValues,
    reminder: Reminder,
    linkedTask: ScheduleTask?,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    val dateOnlyFormatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val description = linkedTask?.description?.takeIf { it.isNotBlank() } ?: reminder.description

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.width(48.dp))
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(reminder.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        reminder.dueDisplayText(formatter, dateOnlyFormatter, zoneId),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!description.isNullOrBlank()) {
                        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DetailRow("Due", reminder.dueDisplayText(formatter, dateOnlyFormatter, zoneId))
                    if (linkedTask != null) {
                        DetailRow("Task", linkedTask.title)
                    }
                    if (reminder.recurrenceRule.type != RecurrenceType.NONE) {
                        DetailRow("Repeat", recurrenceSummary(reminder.recurrenceRule))
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text("Dismiss Reminder")
            }
        }
    }
}

@Composable
fun ActionMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    TaskInfoRow(label = label, value = value)
}

@Composable
fun TaskInfoDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 96.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
fun TaskInfoRow(
    label: String,
    value: String,
    secondaryValue: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
) {
    val rowShape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(rowShape)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.widthIn(min = 88.dp, max = 108.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryValue != null) {
                Text(
                    secondaryValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RepeatDetailRow(
    rule: RecurrenceRule,
    dueAt: Instant,
    zoneId: ZoneId,
    onClick: () -> Unit,
) {
    TaskInfoRow(
        label = "Repeat",
        value = recurrencePrimaryText(rule),
        secondaryValue = recurrenceSecondaryText(rule, dueAt, zoneId),
        onClick = onClick,
        showChevron = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatCalendarSheet(
    taskTitle: String,
    rule: RecurrenceRule,
    dueAt: Instant,
    zoneId: ZoneId,
    visibleMonth: YearMonth,
    onVisibleMonthChange: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val highlightedDates = remember(rule, dueAt, zoneId, visibleMonth) {
        recurrencePreviewDates(rule, dueAt, zoneId, visibleMonth).toSet()
    }
    val today = remember(zoneId) { LocalDate.now(zoneId) }
    val dueDate = remember(dueAt, zoneId) { dueAt.atZone(zoneId).toLocalDate() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Repeat calendar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    taskTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        recurrencePrimaryText(rule),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        recurrenceSecondaryText(rule, dueAt, zoneId) ?: "Starts ${dueDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { onVisibleMonthChange(visibleMonth.minusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    visibleMonth.format(monthFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { onVisibleMonthChange(visibleMonth.plusMonths(1)) }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month")
                }
            }

            RepeatCalendarMonthGrid(
                visibleMonth = visibleMonth,
                highlightedDates = highlightedDates,
                today = today,
                dueDate = dueDate,
            )

            Text(
                "Highlighted dates show when this task repeats. The outlined day is the original due date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RepeatCalendarMonthGrid(
    visibleMonth: YearMonth,
    highlightedDates: Set<LocalDate>,
    today: LocalDate,
    dueDate: LocalDate,
) {
    val cells = remember(visibleMonth) { calendarCellsForMonth(visibleMonth) }
    val weekLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    RepeatCalendarDayCell(
                        date = date,
                        highlighted = date != null && highlightedDates.contains(date),
                        today = date == today,
                        dueDate = date == dueDate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun RepeatCalendarDayCell(
    date: LocalDate?,
    highlighted: Boolean,
    today: Boolean,
    dueDate: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = when {
        highlighted -> MaterialTheme.colorScheme.primary
        today -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        highlighted -> MaterialTheme.colorScheme.onPrimary
        today -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier.height(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(999.dp),
                color = background,
                border = if (dueDate && !highlighted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (highlighted || today || dueDate) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

fun recurrencePreviewDates(
    rule: RecurrenceRule,
    dueAt: Instant,
    zoneId: ZoneId,
    visibleMonth: YearMonth,
): List<LocalDate> {
    val dueDateTime = dueAt.atZone(zoneId).toLocalDateTime()
    val startDate = dueDateTime.toLocalDate()
    val visibleStart = visibleMonth.atDay(1)
    val visibleEnd = visibleMonth.atEndOfMonth()
    val interval = rule.interval.coerceAtLeast(1)
    val maxOccurrences = if (rule.endMode == RecurrenceEndMode.AFTER_OCCURRENCES) {
        rule.occurrenceCount?.coerceAtLeast(0)
    } else {
        null
    }
    if (maxOccurrences == 0) return emptyList()

    val dates = mutableListOf<LocalDate>()
    var emitted = 0

    fun acceptCandidate(date: LocalDate): Boolean {
        if (date < startDate) return true
        val candidateInstant = LocalDateTime
            .of(date, dueDateTime.toLocalTime())
            .atZone(zoneId)
            .toInstant()
        if (rule.endMode == RecurrenceEndMode.ON_DATE && rule.until != null && candidateInstant > rule.until) {
            return false
        }
        if (maxOccurrences != null && emitted >= maxOccurrences) {
            return false
        }
        if (date in visibleStart..visibleEnd) {
            dates += date
        }
        emitted += 1
        return true
    }

    when (rule.type) {
        RecurrenceType.NONE -> {
            acceptCandidate(startDate)
        }

        RecurrenceType.DAILY -> {
            var candidate = startDate
            while (candidate <= visibleEnd) {
                if (!acceptCandidate(candidate)) break
                candidate = candidate.plusDays(interval.toLong())
            }
        }

        RecurrenceType.WEEKLY -> {
            val days = (rule.daysOfWeek.ifEmpty { setOf(startDate.dayOfWeek) }).sortedBy { it.value }
            var weekStart = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            while (weekStart <= visibleEnd) {
                for (day in days) {
                    val candidate = weekStart.plusDays((day.value - DayOfWeek.MONDAY.value).toLong())
                    if (candidate > visibleEnd) continue
                    if (!acceptCandidate(candidate)) return dates
                }
                weekStart = weekStart.plusWeeks(interval.toLong())
            }
        }

        RecurrenceType.MONTHLY -> {
            val originalDay = startDate.dayOfMonth
            var candidateMonth = YearMonth.from(startDate)
            while (candidateMonth <= visibleMonth) {
                val candidate = candidateMonth.atDay(min(originalDay, candidateMonth.lengthOfMonth()))
                if (!acceptCandidate(candidate)) break
                candidateMonth = candidateMonth.plusMonths(interval.toLong())
            }
        }
    }

    return dates.sorted()
}

fun calendarCellsForMonth(month: YearMonth): List<LocalDate?> {
    val leadingBlankCount = month.atDay(1).dayOfWeek.value % 7
    val monthDates = (1..month.lengthOfMonth()).map { day -> month.atDay(day) }
    val trailingBlankCount = (7 - ((leadingBlankCount + monthDates.size) % 7)) % 7
    val cells = List(leadingBlankCount) { null } + monthDates + List(trailingBlankCount) { null }
    // Always pad to exactly 42 cells (6 weeks) so the grid height is stable across months
    val totalCells = 42
    return if (cells.size < totalCells) cells + List(totalCells - cells.size) { null } else cells.take(totalCells)
}

fun defaultRepeatCalendarMonth(dueAt: Instant, zoneId: ZoneId): YearMonth {
    val dueMonth = YearMonth.from(dueAt.atZone(zoneId))
    val currentMonth = YearMonth.now(zoneId)
    return if (dueMonth.isAfter(currentMonth)) dueMonth else currentMonth
}

fun recurrencePrimaryText(rule: RecurrenceRule): String =
    when (rule.type) {
        RecurrenceType.NONE -> "Does not repeat"
        RecurrenceType.DAILY -> repeatIntervalText(rule.interval, "day", "days")
        RecurrenceType.WEEKLY -> repeatIntervalText(rule.interval, "week", "weeks")
        RecurrenceType.MONTHLY -> repeatIntervalText(rule.interval, "month", "months")
    }

fun recurrenceSecondaryText(
    rule: RecurrenceRule,
    dueAt: Instant,
    zoneId: ZoneId,
): String? {
    val dueDate = dueAt.atZone(zoneId).toLocalDate()
    val parts = mutableListOf<String>()
    if (rule.type == RecurrenceType.WEEKLY) {
        val days = rule.daysOfWeek.ifEmpty { setOf(dueDate.dayOfWeek) }
        parts += days
            .sortedBy { it.value }
            .joinToString(", ") { day ->
                day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
    }
    recurrenceEndText(rule, zoneId)?.let { parts += it }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" | ")
}

fun recurrenceEndText(rule: RecurrenceRule, zoneId: ZoneId): String? {
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return when (rule.endMode) {
        RecurrenceEndMode.NEVER -> null
        RecurrenceEndMode.ON_DATE -> rule.until
            ?.atZone(zoneId)
            ?.toLocalDate()
            ?.format(dateFormatter)
            ?.let { "Until $it" }

        RecurrenceEndMode.AFTER_OCCURRENCES -> rule.occurrenceCount?.let { count ->
            "$count ${if (count == 1) "time" else "times"}"
        }
    }
}

fun repeatIntervalText(interval: Int, singular: String, plural: String): String {
    val safeInterval = interval.coerceAtLeast(1)
    return if (safeInterval == 1) "Every $singular" else "Every $safeInterval $plural"
}

fun List<ScheduleBlock>.scheduleDisplayText(
    timeFormatter: DateTimeFormatter,
    zoneId: ZoneId,
): String =
    if (isEmpty()) {
        "Not scheduled yet"
    } else {
        joinToString(", ") { block ->
            val start = timeFormatter.format(block.startAt.atZone(zoneId))
            val end = timeFormatter.format(block.endAt.atZone(zoneId))
            "$start to $end"
        }
    }

fun TaskPriority.detailLabel(): String =
    when (this) {
        TaskPriority.LOW -> "Low"
        TaskPriority.URGENT -> "Urgent"
        TaskPriority.HIGH -> "High"
        TaskPriority.MEDIUM -> "Normal"
    }

fun PreferredTimeOfDay.displayText(): String = when (this) {
    PreferredTimeOfDay.MORNING -> "Morning"
    PreferredTimeOfDay.NOON -> "Noon"
    PreferredTimeOfDay.AFTERNOON -> "Afternoon"
    PreferredTimeOfDay.NIGHT -> "Night"
    PreferredTimeOfDay.ANYTIME -> "Anytime"
}
