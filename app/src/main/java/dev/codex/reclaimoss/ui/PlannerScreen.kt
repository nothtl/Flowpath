package dev.codex.reclaimoss.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
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
import dev.codex.reclaimoss.domain.model.Timeframe
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.TaskStatus
import dev.codex.reclaimoss.domain.model.TimePeriod
import dev.codex.reclaimoss.domain.model.TimePeriodType
import dev.codex.reclaimoss.domain.scheduling.ScheduleRebuildReason
import dev.codex.reclaimoss.domain.service.PlannerCoordinator
import dev.codex.reclaimoss.domain.service.TaskCreationResult
import dev.codex.reclaimoss.settings.AppSettings
import dev.codex.reclaimoss.settings.HistoryRetention
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun PlannerScreen(
    padding: PaddingValues,
    state: PlannerUiState,
    settings: AppSettings,
    selectedDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    onRebuild: () -> Unit,
    onAddTimeframe: () -> Unit,
    onEditTimeframe: (String) -> Unit,
    onDeleteTimeframe: (String) -> Unit,
    onToggleLock: (ScheduleBlock) -> Unit,
    onMarkDone: (ScheduleBlock) -> Unit,
    onReschedule: (String) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    var visibleMonth by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate)) }
    val zoneId = remember { ZoneId.systemDefault() }
    var today by remember(zoneId) { mutableStateOf(LocalDate.now(zoneId)) }
    LaunchedEffect(zoneId) {
        while (true) {
            today = LocalDate.now(zoneId)
            kotlinx.coroutines.delay(60_000L)
        }
    }
    LaunchedEffect(selectedDate) {
        visibleMonth = YearMonth.from(selectedDate)
    }
    val activeBlocks = remember(state.snapshot.blocks) {
        state.snapshot.blocks.filter { it.completionState != dev.codex.reclaimoss.domain.model.BlockCompletionState.COMPLETED }
    }
    val blocksByDate = remember(activeBlocks) {
        activeBlocks.groupBy { it.startAt.atZone(zoneId).toLocalDate() }
    }
    val tasksById = remember(state.snapshot.tasks) { state.snapshot.tasks.associateBy { it.id } }
    val remindersByDate = remember(state.snapshot.reminders) {
        state.snapshot.reminders
            .filter { it.status != ReminderStatus.COMPLETED }
            .groupBy { it.dueAt.atZone(zoneId).toLocalDate() }
    }
    val historyCutoff = remember(settings.historyRetention) {
        when (settings.historyRetention) {
            HistoryRetention.SEVEN_DAYS -> Instant.now().minusSeconds(7L * 24L * 60L * 60L)
            HistoryRetention.THIRTY_DAYS -> Instant.now().minusSeconds(30L * 24L * 60L * 60L)
            HistoryRetention.FOREVER -> null
        }
    }
    val completedTasksByDate = remember(state.snapshot.tasks, settings.historyRetention) {
        state.snapshot.tasks
            .filter { it.status == TaskStatus.COMPLETED }
            .filter { historyCutoff == null || !it.updatedAt.isBefore(historyCutoff) }
            .groupBy { it.updatedAt.atZone(zoneId).toLocalDate() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            HeaderActionSlot {
                HeaderActionButton(label = "Add timeframe", icon = Icons.Outlined.Add, onClick = onAddTimeframe, width = 180.dp)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                CalendarCard(
                    month = visibleMonth,
                selectedDate = selectedDate,
                weekStart = settings.weekStart,
                timeframes = state.snapshot.timeframes,
                blocksByDate = blocksByDate,
                remindersByDate = remindersByDate,
                tasksById = tasksById,
                    onPreviousMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                    onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                    onDateSelected = { onSelectedDateChange(it) },
                )
            }
            selectedDayOverview(
                selectedDate = selectedDate,
                settings = settings,
                timeframes = state.snapshot.timeframes.filter { !selectedDate.isBefore(it.startDate) && !selectedDate.isAfter(it.endDate) },
                blocks = blocksByDate[selectedDate].orEmpty().sortedBy { it.startAt },
                completedTasks = completedTasksByDate[selectedDate].orEmpty().sortedByDescending { it.updatedAt },
                tasksById = tasksById,
                zoneId = zoneId,
                onEditTimeframe = onEditTimeframe,
                onDeleteTimeframe = onDeleteTimeframe,
                onToggleLock = onToggleLock,
                onMarkDone = onMarkDone,
                onReschedule = onReschedule,
                onOpenTask = onOpenTask,
            )
        }
    }
}

fun LazyListScope.selectedDayOverview(
    selectedDate: LocalDate,
    settings: AppSettings,
    timeframes: List<Timeframe>,
    blocks: List<ScheduleBlock>,
    completedTasks: List<ScheduleTask>,
    tasksById: Map<String, ScheduleTask>,
    zoneId: ZoneId,
    onEditTimeframe: (String) -> Unit,
    onDeleteTimeframe: (String) -> Unit,
    onToggleLock: (ScheduleBlock) -> Unit,
    onMarkDone: (ScheduleBlock) -> Unit,
    onReschedule: (String) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    item {
        Text(
            headerDateLabel(selectedDate, settings.dateFormatPreference),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (timeframes.isNotEmpty()) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                timeframes.sortedWith(compareBy<Timeframe> { it.startDate }.thenBy { it.endDate }.thenBy { it.name }).forEach { timeframe ->
                    ExpandableTimeframeRow(
                        timeframe = timeframe,
                        onEdit = { onEditTimeframe(timeframe.id) },
                        onDelete = { onDeleteTimeframe(timeframe.id) },
                    )
                }
            }
        }
    }
    item {
        OverviewCard(title = "Tasks") {
            if (blocks.isEmpty()) {
                Text("No tasks scheduled.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val groupedBlocks = blocks.groupBy { it.taskId }.values.sortedBy { group -> group.minOf { it.startAt } }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    groupedBlocks.forEach { taskBlocks ->
                        val task = tasksById[taskBlocks.first().taskId]
                        CompactTaskRow(
                            blocks = taskBlocks.sortedBy { it.startAt },
                            task = task,
                            zoneId = zoneId,
                            onOpen = { onOpenTask(taskBlocks.first().taskId) },
                        )
                    }
                }
            }
        }
    }
    item {
        OverviewCard(title = "History") {
            if (completedTasks.isEmpty()) {
                Text("No completed tasks.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    completedTasks.forEach { task ->
                        CompletedTaskHistoryCard(task = task, zoneId = zoneId)
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderMiniCard(reminder: Reminder, zoneId: ZoneId) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(reminder.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(formatter.format(reminder.dueAt.atZone(zoneId)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun OverviewCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun CompactTaskRow(
    blocks: List<ScheduleBlock>,
    task: ScheduleTask?,
    zoneId: ZoneId,
    onOpen: () -> Unit,
) {
    val firstBlock = blocks.minByOrNull { it.startAt } ?: return
    val blockSummary = blocks
        .sortedBy { it.startAt }
        .joinToString(" · ") { block ->
            "${block.startAt.atZone(zoneId).toLocalTime().formatAsClock()}-${block.endAt.atZone(zoneId).toLocalTime().formatAsClock()}"
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpen)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(task?.title ?: firstBlock.taskId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    blockSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    weekStart: WeekStart,
    timeframes: List<Timeframe>,
    blocksByDate: Map<LocalDate, List<ScheduleBlock>>,
    remindersByDate: Map<LocalDate, List<Reminder>>,
    tasksById: Map<String, ScheduleTask>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    var today by remember(zoneId) { mutableStateOf(LocalDate.now(zoneId)) }
    LaunchedEffect(zoneId) {
        while (true) {
            today = LocalDate.now(zoneId)
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val dayLabels = remember(weekStart) {
        if (weekStart == WeekStart.MONDAY) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        } else {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        }
    }
    val weeks = remember(month, weekStart) { buildCalendarWeeks(month, weekStart) }
    val rowHeight = 64.dp
    val pillSize = 38.dp
    val timeframeStroke = 2.dp
    val timeframeInset = 5.dp
    val edgeOverhang = 8.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                dayLabels.forEach { dayName ->
                    Text(dayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                }
            }
            val density = LocalDensity.current
            val canvasStrokePx = remember(timeframeStroke) { with(density) { timeframeStroke.toPx() } }
            val canvasInsetPx = remember(timeframeInset) { with(density) { timeframeInset.toPx() } }
            val canvasOverhangPx = remember(edgeOverhang) { with(density) { edgeOverhang.toPx() } }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                weeks.forEachIndexed { rowIndex, week ->
                    var rowWidthPx by remember { mutableStateOf(0f) }
                    var rowHeightPxVal by remember { mutableStateOf(0f) }
                    val rowSpecs = remember(rowWidthPx, rowHeightPxVal, week, timeframes, canvasStrokePx, canvasInsetPx, canvasOverhangPx) {
                        if (rowWidthPx <= 0f || rowHeightPxVal <= 0f) emptyList()
                        else buildTimeframeRowDrawSpecs(
                            weeks = listOf(week),
                            timeframes = timeframes,
                            cellWidthPx = rowWidthPx / 7f,
                            rowHeightPx = rowHeightPxVal,
                            strokeWidthPx = canvasStrokePx,
                            baseInsetPx = canvasInsetPx,
                            edgeOverhangPx = canvasOverhangPx,
                        ).filter { it.rowIndex == 0 }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .onSizeChanged { size ->
                                rowWidthPx = size.width.toFloat()
                                rowHeightPxVal = size.height.toFloat()
                            },
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            rowSpecs.forEach { spec ->
                                drawRoundRect(
                                    color = parseTimeframeColor(spec.colorHex),
                                    topLeft = androidx.compose.ui.geometry.Offset(spec.left, spec.top),
                                    size = androidx.compose.ui.geometry.Size(spec.right - spec.left, spec.bottom - spec.top),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(spec.radius, spec.radius),
                                    style = Stroke(width = canvasStrokePx),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            week.forEach { date ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    if (date != null) {
                                        CalendarDayCell(
                                            date = date,
                                            isSelected = date == selectedDate,
                                            isToday = date == today,
                                            tasks = blocksByDate[date].orEmpty().mapNotNull { tasksById[it.taskId] }.distinctBy { it.id },
                                            hasReminders = remindersByDate[date].orEmpty().isNotEmpty(),
                                            pillSize = pillSize,
                                            onClick = { onDateSelected(date) },
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
}

@Composable
fun CalendarDayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    tasks: List<ScheduleTask>,
    hasReminders: Boolean,
    pillSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val pillColor = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.42f else 0.18f)
            else -> Color.Transparent
        }
        val pillBorderColor = when {
            isSelected -> Color.Transparent
            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
            else -> Color.Transparent
        }
        Column(
            modifier = Modifier
                .size(pillSize)
                .clip(RoundedCornerShape(12.dp))
                .background(pillColor)
                .then(
                    if (pillBorderColor != Color.Transparent) {
                        Modifier.border(1.dp, pillBorderColor, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (tasks.isNotEmpty() || hasReminders) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tasks.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size((4.dp + (tasks.size.coerceAtMost(4) * 1.5f).dp).coerceAtMost(10.dp))
                                .background(taskDotColor(tasks.size), RoundedCornerShape(999.dp)),
                        )
                    }
                    if (hasReminders) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)),
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ExpandableTimeframeRow(
    timeframe: Timeframe,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember(timeframe.id) { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(parseTimeframeColor(timeframe.colorHex), RoundedCornerShape(999.dp)),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(timeframe.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${formatter.format(timeframe.startDate)} - ${formatter.format(timeframe.endDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Text("Edit")
                    }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text("Delete")
                    }
                }
            }
        }
    }
}

private fun buildCalendarWeeks(
    month: YearMonth,
    weekStart: WeekStart,
): List<List<LocalDate?>> {
    val cells = buildList<LocalDate?> {
        val first = month.atDay(1)
        val leadingSlots = when (weekStart) {
            WeekStart.SUNDAY -> first.dayOfWeek.value % 7
            WeekStart.MONDAY -> first.dayOfWeek.value - 1
        }
        repeat(leadingSlots) { add(null) }
        for (day in 1..month.lengthOfMonth()) {
            add(month.atDay(day))
        }
        while (size % 7 != 0) add(null)
    }
    return cells.chunked(7)
}

internal fun parseTimeframeColor(colorHex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(colorHex))
}.getOrElse {
    Color(0xFFF4B6D2)
}


