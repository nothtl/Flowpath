package dev.codex.reclaimoss.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import dev.codex.reclaimoss.domain.model.Timeframe
import dev.codex.reclaimoss.domain.scheduling.ScheduleRebuildReason
import dev.codex.reclaimoss.domain.service.PlannerCoordinator
import dev.codex.reclaimoss.domain.service.TaskCreationResult
import dev.codex.reclaimoss.settings.AppSettings
import dev.codex.reclaimoss.settings.TasksViewMode
import dev.codex.reclaimoss.ui.HeaderActionHeight
import dev.codex.reclaimoss.ui.HeaderActionShape
import dev.codex.reclaimoss.ui.HeaderActionSlotWidth
import dev.codex.reclaimoss.ui.HeaderActionWidth
import dev.codex.reclaimoss.ui.dueDisplayText
import dev.codex.reclaimoss.ui.formatHourLabel
import dev.codex.reclaimoss.ui.minutesFromStart
import dev.codex.reclaimoss.ui.parseTimeframeColor
import dev.codex.reclaimoss.ui.reminderDateTimeFormatter
import dev.codex.reclaimoss.ui.timelineBlockHeight
import dev.codex.reclaimoss.ui.timelineOffset
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class TaskDaySection(
    val date: LocalDate,
    val segments: List<VisibleTaskSegment>,
    val tasks: List<ScheduleTask>,
    val reminders: List<Reminder>,
    val timeframes: List<Timeframe>,
    val taskCount: Int,
    val reminderCount: Int,
)

internal data class TimeframeRailMetadata(
    val id: String,
    val name: String,
    val colorHex: String,
    val startDate: LocalDate,
    val laneIndex: Int,
    val continuesFromPreviousDay: Boolean,
    val continuesIntoNextDay: Boolean,
)

internal data class TimeframeHeaderLabel(
    val name: String,
    val colorHex: String,
)

internal data class ExpandedHeaderTransition(
    val pinnedDayIndex: Int,
    val incomingDayIndex: Int? = null,
    val progress: Float = 0f,
)

internal enum class StickyHeaderTimeframeChipMotion {
    PINNED,
    EXITING,
    ENTERING,
}

internal data class TimeframeChipPlacement(
    val id: String,
    val name: String,
    val colorHex: String,
    val motion: StickyHeaderTimeframeChipMotion,
    val fromSlot: Int,
    val toSlot: Int,
    val progress: Float,
    val trackDayIndex: Int = 0,  // day index whose date chip Y this chip follows
)

private data class HeaderChipVerticalOffsets(
    val outgoingDateYPx: Int,
    val incomingDateYPx: Int?,
)

data class VisibleTaskSegment(
    val block: ScheduleBlock,
    val continuesFromPreviousDay: Boolean,
    val continuesIntoNextDay: Boolean,
)

internal data class ExpandedTimelinePosition(
    val date: LocalDate,
    val dayOffsetPx: Int,
)

internal data class ExpandedTimelineBlockFramePx(
    val topPx: Int,
    val heightPx: Int,
)

internal data class ExpandedTimelineVisibleBlockFramePx(
    val topPx: Int,
    val heightPx: Int,
    val hasOriginalTop: Boolean,
    val hasOriginalBottom: Boolean,
)

internal fun activeTimeframesForDay(
    timeframes: List<Timeframe>,
    day: LocalDate,
): List<Timeframe> = timeframes
    .filter { !day.isBefore(it.startDate) && !day.isAfter(it.endDate) }
    .sortedWith(compareBy<Timeframe> { it.startDate }.thenBy { it.endDate }.thenBy { it.name })

internal fun buildTaskDaySection(
    date: LocalDate,
    blocks: List<ScheduleBlock>,
    tasksById: Map<String, ScheduleTask>,
    reminders: List<Reminder>,
    timeframes: List<Timeframe>,
    zoneId: ZoneId,
): TaskDaySection {
    val visibleSegments = visibleTaskSegmentsForDay(blocks, date, zoneId)
        .filter { it.block.completionState != dev.codex.reclaimoss.domain.model.BlockCompletionState.COMPLETED }
        .sortedBy { it.block.startAt }
    val tasks = visibleSegments
        .mapNotNull { tasksById[it.block.taskId] }
        .distinctBy { it.id }
    val dayReminders = reminders
        .filter { it.status != ReminderStatus.COMPLETED }
        .filter { it.dueAt.atZone(zoneId).toLocalDate() == date }
        .sortedBy { it.dueAt }
    val dayTimeframes = activeTimeframesForDay(timeframes, date)
    return TaskDaySection(
        date = date,
        segments = visibleSegments,
        tasks = tasks,
        reminders = dayReminders,
        timeframes = dayTimeframes,
        taskCount = tasks.size,
        reminderCount = dayReminders.size,
    )
}

internal fun buildTimeframeRailMetadata(
    currentDayTimeframes: List<Timeframe>,
    previousDayTimeframes: List<Timeframe> = emptyList(),
    nextDayTimeframes: List<Timeframe> = emptyList(),
): List<TimeframeRailMetadata> {
    val previousIds = previousDayTimeframes.map { it.id }.toSet()
    val nextIds = nextDayTimeframes.map { it.id }.toSet()
    return currentDayTimeframes.mapIndexed { index, timeframe ->
        TimeframeRailMetadata(
            id = timeframe.id,
            name = timeframe.name,
            colorHex = timeframe.colorHex,
            startDate = timeframe.startDate,
            laneIndex = index,
            continuesFromPreviousDay = timeframe.id in previousIds,
            continuesIntoNextDay = timeframe.id in nextIds,
        )
    }
}

private const val TaskFeedDayCount = 20001
private const val TaskFeedCenterIndex = TaskFeedDayCount / 2
private const val MaxOverlappingTimeframeRails = 5
private val ExpandedDayHeaderHeight = 48.dp
private val ExpandedDayHeaderTopInset = 10.dp
private val ExpandedDateChipSlotHeight = 30.dp
private val ExpandedDateChipGap = 16.dp
private val ExpandedDateChipAboveMidnightOffset = 38.dp
private val ExpandedTaskStickyTitleTopInset = 8.dp
private val TimeframeHeaderChipMaxWidth = 96.dp
private val TimeframeHeaderChipSlotStep = 38.dp
private val TaskTimelineLabelWidth = 64.dp
private val TaskTimelineContentInset = 6.dp
private val TaskTimelineCompactRailWidth = 2.dp
private val TaskTimelineExpandedRailWidth = 2.dp
private val TaskTimelineDividerWidth = 0.75.dp
private val TaskTimelineRailGap = 4.dp
private val TaskTimelineBoundaryOverlap = 2.dp

private enum class TasksSheetType {
    ADD_CHOOSER,
    DAY_SUMMARY,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    padding: PaddingValues,
    state: PlannerUiState,
    settings: AppSettings,
    isActive: Boolean,
    selectedDate: LocalDate,
    selectedDateScrollOffset: Int,
    autoScrollToNow: Boolean,
    onAutoScrollToNowConsumed: () -> Unit,
    onSelectedDateChange: (LocalDate) -> Unit,
    onScrollPositionChange: (LocalDate, Int) -> Unit,
    onTasksViewModeChanged: (TasksViewMode) -> Unit,
    onAddTask: () -> Unit,
    onAddReminder: () -> Unit,
    onAddBlocker: () -> Unit,
    onDeleteTask: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenReminder: (Reminder) -> Unit,
    sleepFullyConfigured: Boolean = true,
    sleepCoveredCount: Int = 7,
    hasAnyNormalTask: Boolean = true,
    onAddSleep: () -> Unit = {},
    onStartTaskTutorial: () -> Unit = {},
    onOpenRecurring: () -> Unit = {},
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember(zoneId) { LocalDate.now(zoneId) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val initialTaskFeedIndex = taskFeedIndexForDate(today, selectedDate)
    val collapsedListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialTaskFeedIndex,
        initialFirstVisibleItemScrollOffset = selectedDateScrollOffset,
    )
    val tasksById = remember(state.snapshot.tasks) { state.snapshot.tasks.associateBy { it.id } }
    val activeReminders = remember(state.snapshot.reminders) { state.snapshot.reminders.filter { it.status != ReminderStatus.COMPLETED } }
    val dateFormatter = remember(settings.dateFormatPreference) {
        when (settings.dateFormatPreference) {
            dev.codex.reclaimoss.settings.DateFormatPreference.MONTH_DAY_YEAR -> DateTimeFormatter.ofPattern("EEE, MMM d")
            dev.codex.reclaimoss.settings.DateFormatPreference.DAY_MONTH_YEAR -> DateTimeFormatter.ofPattern("EEE, d MMM")
        }
    }
    val reminderFormatter = remember(settings.dateFormatPreference) { reminderDateTimeFormatter(settings.dateFormatPreference) }
    val hourHeight = settings.taskHourHeightDp.dp
    val timelineHeight = timelineOffset(minutes = 24 * 60, hourHeight = hourHeight)
    val timelineHeightPx = with(density) { timelineHeight.roundToPx().coerceAtLeast(1) }
    var expandedScrollPx by rememberSaveable(today, timelineHeightPx) {
        mutableStateOf(
            expandedTimelineScrollPxForDate(
                today = today,
                date = selectedDate,
                dayHeightPx = timelineHeightPx,
                dayOffsetPx = selectedDateScrollOffset,
            ),
        )
    }
    var showingSheet by rememberSaveable { mutableStateOf<TasksSheetType?>(null) }
    var sleepHintDismissed by rememberSaveable { mutableStateOf(false) }
    var selectedDaySummaryEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedDaySummary = selectedDaySummaryEpoch?.let { epoch ->
        buildTaskDaySection(
            date = LocalDate.ofEpochDay(epoch),
            blocks = state.snapshot.blocks,
            tasksById = tasksById,
            reminders = activeReminders,
            timeframes = state.snapshot.timeframes,
            zoneId = zoneId,
        )
    }

    LaunchedEffect(isActive, settings.tasksViewMode) {
        if (!isActive) return@LaunchedEffect
        if (autoScrollToNow) return@LaunchedEffect
        when (settings.tasksViewMode) {
            TasksViewMode.COLLAPSED -> {
                collapsedListState.scrollToItem(taskFeedIndexForDate(today, selectedDate), selectedDateScrollOffset)
            }
            TasksViewMode.EXPANDED -> {
                expandedScrollPx = expandedTimelineScrollPxForDate(
                    today = today,
                    date = selectedDate,
                    dayHeightPx = timelineHeightPx,
                    dayOffsetPx = selectedDateScrollOffset,
                )
            }
        }
    }
    LaunchedEffect(isActive, settings.tasksViewMode, autoScrollToNow, hourHeight) {
        if (!isActive || !autoScrollToNow) return@LaunchedEffect
        val targetIndex = taskFeedIndexForDate(today, today)
        val offsetPx = if (settings.tasksViewMode == TasksViewMode.EXPANDED) {
            with(density) {
                val nowMinutes = minutesFromStart(LocalTime.now(zoneId))
                val scrollMinutes = (nowMinutes - 60).coerceAtLeast(0)
                timelineOffset(scrollMinutes, hourHeight).roundToPx()
            }
        } else {
            0
        }
        if (settings.tasksViewMode == TasksViewMode.COLLAPSED) {
            collapsedListState.scrollToItem(targetIndex, offsetPx)
        } else {
            expandedScrollPx = expandedTimelineScrollPxForDate(
                today = today,
                date = today,
                dayHeightPx = timelineHeightPx,
                dayOffsetPx = offsetPx,
            )
        }
        onAutoScrollToNowConsumed()
    }
    LaunchedEffect(isActive, settings.tasksViewMode, collapsedListState, timelineHeightPx) {
        if (!isActive) return@LaunchedEffect
        when (settings.tasksViewMode) {
            TasksViewMode.COLLAPSED -> {
                snapshotFlow { collapsedListState.firstVisibleItemIndex to collapsedListState.firstVisibleItemScrollOffset }
                    .collect { (index, offset) ->
                        onScrollPositionChange(
                            taskFeedDateForIndex(today, index),
                            offset,
                        )
                    }
            }
            TasksViewMode.EXPANDED -> {
                snapshotFlow { expandedScrollPx }
                    .collect { scrollPx ->
                        val position = expandedTimelinePositionForScrollPx(
                            today = today,
                            scrollPx = scrollPx,
                            dayHeightPx = timelineHeightPx,
                        )
                        onScrollPositionChange(position.date, position.dayOffsetPx)
                    }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    modifier = Modifier.size(38.dp),
                    onClick = {
                        when (settings.tasksViewMode) {
                            TasksViewMode.COLLAPSED -> {
                                val targetIndex = collapsedListState.firstVisibleItemIndex
                                val targetDate = taskFeedDateForIndex(today, targetIndex)
                                val expandedPosition = expandedTimelinePositionForScrollPx(
                                    today = today,
                                    scrollPx = expandedScrollPx,
                                    dayHeightPx = timelineHeightPx,
                                )
                                val expandedOffset = if (expandedPosition.date == targetDate) {
                                    expandedPosition.dayOffsetPx
                                } else {
                                    0
                                }
                                if (expandedPosition.date != targetDate) {
                                    expandedScrollPx = expandedTimelineScrollPxForDate(
                                        today = today,
                                        date = targetDate,
                                        dayHeightPx = timelineHeightPx,
                                        dayOffsetPx = 0,
                                    )
                                }
                                onScrollPositionChange(targetDate, expandedOffset)
                                onTasksViewModeChanged(TasksViewMode.EXPANDED)
                            }
                            TasksViewMode.EXPANDED -> {
                                val expandedPosition = expandedTimelinePositionForScrollPx(
                                    today = today,
                                    scrollPx = expandedScrollPx,
                                    dayHeightPx = timelineHeightPx,
                                )
                                onScrollPositionChange(expandedPosition.date, 0)
                                onTasksViewModeChanged(TasksViewMode.COLLAPSED)
                            }
                        }
                    },
                ) {
                    Icon(
                        if (settings.tasksViewMode == TasksViewMode.COLLAPSED) Icons.Outlined.ZoomIn else Icons.Outlined.ZoomOut,
                        contentDescription = if (settings.tasksViewMode == TasksViewMode.COLLAPSED) "Expand tasks view" else "Collapse tasks view",
                    )
                }
                IconButton(
                    modifier = Modifier.size(38.dp),
                    onClick = {
                        val targetDate = today
                        onSelectedDateChange(targetDate)
                        val targetIndex = taskFeedIndexForDate(today, targetDate)
                        val offsetPx = if (settings.tasksViewMode == TasksViewMode.EXPANDED) {
                            with(density) {
                                val nowMinutes = minutesFromStart(LocalTime.now(zoneId))
                                val scrollMinutes = (nowMinutes - 60).coerceAtLeast(0)
                                timelineOffset(scrollMinutes, hourHeight).roundToPx()
                            }
                        } else 0
                        if (settings.tasksViewMode == TasksViewMode.COLLAPSED) {
                            scope.launch {
                                collapsedListState.animateScrollToItem(targetIndex, offsetPx)
                            }
                        } else {
                            expandedScrollPx = expandedTimelineScrollPxForDate(
                                today = today,
                                date = targetDate,
                                dayHeightPx = timelineHeightPx,
                                dayOffsetPx = offsetPx,
                            )
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Home, contentDescription = "Go to today")
                }
                IconButton(
                    modifier = Modifier.size(38.dp),
                    onClick = onOpenRecurring,
                ) {
                    Icon(Icons.Outlined.Repeat, contentDescription = "Recurring")
                }
            }
            HeaderActionSlot {
                HeaderActionButton(label = "Add", icon = Icons.Outlined.Add, onClick = { showingSheet = TasksSheetType.ADD_CHOOSER })
            }
        }
        if (!sleepHintDismissed) {
            if (!sleepFullyConfigured) {
                SleepSetupHintBanner(
                    coveredCount = sleepCoveredCount,
                    onAddSleep = onAddSleep,
                    onDismiss = { sleepHintDismissed = true },
                )
            } else if (!hasAnyNormalTask) {
                SleepSetupHintBanner(
                    coveredCount = 7,
                    onAddSleep = onStartTaskTutorial,
                    onDismiss = { sleepHintDismissed = true },
                    readyForTasks = true,
                )
            }
        }
        Box(modifier = Modifier.weight(1f, fill = true)) {
            AnimatedContent(
                targetState = settings.tasksViewMode,
                transitionSpec = {
                    if (targetState == TasksViewMode.EXPANDED) {
                        (fadeIn(tween(300)) + expandVertically(tween(300), expandFrom = Alignment.Top))
                            .togetherWith(fadeOut(tween(200)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Top))
                    } else {
                        (fadeIn(tween(300)) + expandVertically(tween(300), expandFrom = Alignment.Bottom))
                            .togetherWith(fadeOut(tween(200)) + shrinkVertically(tween(200), shrinkTowards = Alignment.Bottom))
                    }
                },
                label = "tasks-view-mode",
            ) { mode ->
                when (mode) {
                    TasksViewMode.COLLAPSED -> {
                        LazyColumn(
                            state = collapsedListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                            contentPadding = PaddingValues(bottom = 260.dp),
                        ) {
                            items(TaskFeedDayCount, key = { index -> taskFeedDateForIndex(today, index).toEpochDay() }) { index ->
                                val date = taskFeedDateForIndex(today, index)
                                val section = buildTaskDaySection(
                                    date = date,
                                    blocks = state.snapshot.blocks,
                                    tasksById = tasksById,
                                    reminders = activeReminders,
                                    timeframes = state.snapshot.timeframes,
                                    zoneId = zoneId,
                                )
                                CollapsedTaskDayRow(
                                    section = section,
                                    railMetadata = railMetadataForDate(state.snapshot.timeframes, date),
                                    isScrollInProgress = collapsedListState.isScrollInProgress,
                                    onClick = {
                                        selectedDaySummaryEpoch = section.date.toEpochDay()
                                        showingSheet = TasksSheetType.DAY_SUMMARY
                                    },
                                )
                            }
                        }
                    }
                    TasksViewMode.EXPANDED -> {
                        ExpandedContinuousTimeline(
                            scrollPx = expandedScrollPx,
                            onScrollPxChange = { expandedScrollPx = it },
                            blocks = state.snapshot.blocks,
                            tasksById = tasksById,
                            timeframes = state.snapshot.timeframes,
                            today = today,
                            zoneId = zoneId,
                            hourHeight = hourHeight,
                            dayHeightPx = timelineHeightPx,
                            allowConcurrentTasks = settings.allowConcurrentTasks,
                            onOpenTask = onOpenTask,
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds(),
                        )
                    }
                }
            }
        }
    }

    when (showingSheet) {
        TasksSheetType.ADD_CHOOSER -> {
            ModalBottomSheet(
                onDismissRequest = { showingSheet = null },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                TasksSheetActionList(
                    title = "Add",
                    actions = buildList {
                        if (sleepFullyConfigured || sleepHintDismissed) {
                            add("Add Task" to onAddTask)
                            add("Add Blocker" to onAddBlocker)
                        }
                        add("Add Sleep" to onAddSleep)
                    },
                    onDone = { showingSheet = null },
                )
            }
        }
        TasksSheetType.DAY_SUMMARY -> {
            val section = selectedDaySummary
            if (section != null) {
                ModalBottomSheet(
                    onDismissRequest = { showingSheet = null },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    DaySummarySheet(
                        section = section,
                        formatter = dateFormatter,
                        zoneId = zoneId,
                        onExpand = {
                            showingSheet = null
                            onSelectedDateChange(section.date)
                            onTasksViewModeChanged(TasksViewMode.EXPANDED)
                        },
                        onOpenTask = {
                            showingSheet = null
                            onOpenTask(it)
                        },
                    )
                }
            }
        }
        null -> Unit
    }
}

private fun taskFeedIndexForDate(today: LocalDate, date: LocalDate): Int =
    (TaskFeedCenterIndex + ChronoUnit.DAYS.between(today, date).toInt()).coerceIn(0, TaskFeedDayCount - 1)

private fun taskFeedDateForIndex(today: LocalDate, index: Int): LocalDate =
    today.plusDays((index - TaskFeedCenterIndex).toLong())

internal fun expandedTimelineScrollPxForDate(
    today: LocalDate,
    date: LocalDate,
    dayHeightPx: Int,
    dayOffsetPx: Int,
): Int {
    val safeDayHeightPx = dayHeightPx.coerceAtLeast(1)
    val index = taskFeedIndexForDate(today, date)
    val safeOffsetPx = dayOffsetPx.coerceIn(0, safeDayHeightPx - 1)
    return index * safeDayHeightPx + safeOffsetPx
}

internal fun expandedTimelinePositionForScrollPx(
    today: LocalDate,
    scrollPx: Int,
    dayHeightPx: Int,
): ExpandedTimelinePosition {
    val safeDayHeightPx = dayHeightPx.coerceAtLeast(1)
    val safeScrollPx = scrollPx.coerceIn(0, TaskFeedDayCount * safeDayHeightPx - 1)
    val index = (safeScrollPx / safeDayHeightPx).coerceIn(0, TaskFeedDayCount - 1)
    return ExpandedTimelinePosition(
        date = taskFeedDateForIndex(today, index),
        dayOffsetPx = safeScrollPx % safeDayHeightPx,
    )
}

internal fun expandedTimelineYPxForInstant(
    instant: Instant,
    today: LocalDate,
    zoneId: ZoneId,
    dayHeightPx: Int,
    hourHeightPx: Float,
): Int {
    val dateTime = instant.atZone(zoneId)
    val dayStartPx = taskFeedIndexForDate(today, dateTime.toLocalDate()) * dayHeightPx.coerceAtLeast(1)
    val minuteOffsetPx = ((minutesFromStart(dateTime.toLocalTime()) / 60f) * hourHeightPx).roundToInt()
    return dayStartPx + minuteOffsetPx
}

internal fun expandedTimelineBlockFramePx(
    block: ScheduleBlock,
    today: LocalDate,
    zoneId: ZoneId,
    dayHeightPx: Int,
    hourHeightPx: Float,
    minHeightPx: Int,
): ExpandedTimelineBlockFramePx {
    val topPx = expandedTimelineYPxForInstant(
        instant = block.startAt,
        today = today,
        zoneId = zoneId,
        dayHeightPx = dayHeightPx,
        hourHeightPx = hourHeightPx,
    )
    val bottomPx = expandedTimelineYPxForInstant(
        instant = block.endAt,
        today = today,
        zoneId = zoneId,
        dayHeightPx = dayHeightPx,
        hourHeightPx = hourHeightPx,
    )
    return ExpandedTimelineBlockFramePx(
        topPx = topPx,
        heightPx = (bottomPx - topPx).coerceAtLeast(minHeightPx),
    )
}

internal fun expandedTimelineVisibleBlockFramePx(
    topPx: Int,
    heightPx: Int,
    viewportHeightPx: Int,
): ExpandedTimelineVisibleBlockFramePx? {
    val safeViewportHeightPx = viewportHeightPx.coerceAtLeast(0)
    val safeHeightPx = heightPx.coerceAtLeast(0)
    val bottomPx = topPx + safeHeightPx
    val visibleTopPx = topPx.coerceAtLeast(0)
    val visibleBottomPx = bottomPx.coerceAtMost(safeViewportHeightPx)

    if (visibleBottomPx <= visibleTopPx) return null

    return ExpandedTimelineVisibleBlockFramePx(
        topPx = visibleTopPx,
        heightPx = visibleBottomPx - visibleTopPx,
        hasOriginalTop = topPx >= 0,
        hasOriginalBottom = bottomPx <= safeViewportHeightPx,
    )
}

private fun railMetadataForDate(
    timeframes: List<Timeframe>,
    date: LocalDate,
): List<TimeframeRailMetadata> = buildTimeframeRailMetadata(
    currentDayTimeframes = activeTimeframesForDay(timeframes, date),
    previousDayTimeframes = activeTimeframesForDay(timeframes, date.minusDays(1)),
    nextDayTimeframes = activeTimeframesForDay(timeframes, date.plusDays(1)),
)

internal fun resolveExpandedHeaderTransition(
    scrollPx: Int,
    dayHeightPx: Int,
    maxIndex: Int,
    handoffHeightPx: Int = (dayHeightPx / 10).coerceAtLeast(1),
): ExpandedHeaderTransition {
    val safeDayHeightPx = dayHeightPx.coerceAtLeast(1)
    val safeHandoffHeightPx = handoffHeightPx.coerceIn(1, safeDayHeightPx)
    val safeScrollPx = scrollPx.coerceAtLeast(0)
    val baseIndex = (safeScrollPx / safeDayHeightPx).coerceIn(0, maxIndex)
    if (baseIndex >= maxIndex) {
        return ExpandedHeaderTransition(pinnedDayIndex = maxIndex)
    }

    val dayRemainderPx = safeScrollPx % safeDayHeightPx
    val handoffStartPx = safeDayHeightPx - safeHandoffHeightPx
    if (dayRemainderPx < handoffStartPx) {
        return ExpandedHeaderTransition(pinnedDayIndex = baseIndex)
    }

    val progress = ((dayRemainderPx - handoffStartPx).toFloat() / safeHandoffHeightPx)
        .coerceIn(0f, 1f)
    return ExpandedHeaderTransition(
        pinnedDayIndex = baseIndex,
        incomingDayIndex = (baseIndex + 1).coerceAtMost(maxIndex),
        progress = progress,
    )
}

internal fun buildTimeframeChipPlacements(
    visibleDayRails: List<Pair<Int, List<TimeframeRailMetadata>>>,
    firstVisibleDayIndex: Int,
    previousSlots: Map<String, Int> = emptyMap(),
): List<TimeframeChipPlacement> {
    if (visibleDayRails.isEmpty()) return emptyList()

    // For each unique timeframe ID, find its first and last visible day index
    data class DayRange(val firstDay: Int, val lastDay: Int, val rail: TimeframeRailMetadata)

    val ranges = linkedMapOf<String, DayRange>()
    for ((dayIndex, rails) in visibleDayRails) {
        for (rail in rails.distinctBy { it.id }) {
            val existing = ranges[rail.id]
            if (existing == null) {
                ranges[rail.id] = DayRange(dayIndex, dayIndex, rail)
            } else {
                ranges[rail.id] = existing.copy(lastDay = dayIndex)
            }
        }
    }

    // Determine motion and trackDayIndex for each timeframe.
    // Reverse order so chips appear c,b,a matching right-to-left rail strips.
    val ordered = ranges.entries.toList().reversed()
    // Compute slots only for header chips (PINNED + EXITING).
    // ENTERING chips render below the header and don't compete for header slots,
    // so they get separate slot assignments that don't displace pinned chips.
    val headerEntries = ordered.filter { (_, range) ->
        !(range.firstDay > firstVisibleDayIndex)
    }
    val enteringEntries = ordered.filter { (_, range) ->
        range.firstDay > firstVisibleDayIndex
    }
    val result = mutableListOf<TimeframeChipPlacement>()
    headerEntries.mapIndexed { slot, (id, range) ->
        val motion = when {
            range.lastDay == firstVisibleDayIndex && range.firstDay == firstVisibleDayIndex
                && visibleDayRails.size == 1 -> StickyHeaderTimeframeChipMotion.EXITING
            range.lastDay == firstVisibleDayIndex -> StickyHeaderTimeframeChipMotion.EXITING
            else -> StickyHeaderTimeframeChipMotion.PINNED
        }
        val previousSlot = previousSlots[id]
        result.add(
            TimeframeChipPlacement(
                id = id,
                name = range.rail.name,
                colorHex = range.rail.colorHex,
                motion = motion,
                fromSlot = previousSlot ?: slot,
                toSlot = slot,
                progress = 0f,
                trackDayIndex = when (motion) {
                    StickyHeaderTimeframeChipMotion.EXITING -> range.lastDay
                    StickyHeaderTimeframeChipMotion.PINNED -> firstVisibleDayIndex
                    else -> firstVisibleDayIndex
                },
            )
        )
    }
    // ENTERING chips get slots after all header chips so they don't displace them
    enteringEntries.mapIndexed { i, (id, range) ->
        val slot = headerEntries.size + i
        result.add(
            TimeframeChipPlacement(
                id = id,
                name = range.rail.name,
                colorHex = range.rail.colorHex,
                motion = StickyHeaderTimeframeChipMotion.ENTERING,
                fromSlot = slot,
                toSlot = slot,
                progress = 0f,
                trackDayIndex = range.firstDay,
            )
        )
    }
    return result
}

private fun collapsedDaySummaryText(section: TaskDaySection): String = buildString {
    append(if (section.taskCount == 1) "1 task" else "${section.taskCount} tasks")
}

@Composable
private fun CollapsedTaskDayRow(
    section: TaskDaySection,
    railMetadata: List<TimeframeRailMetadata>,
    isScrollInProgress: Boolean = false,
    onClick: () -> Unit,
) {
    val rowHeight = 88.dp
    val scrolling by rememberUpdatedState(isScrollInProgress)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clickable(
                    enabled = !scrolling,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Timeframe rails — same max-5 lane logic, same x-position as expanded
            TimeframeRailStrip(
                rails = railMetadata,
                modifier = Modifier
                    .width(timelineRailStripWidth(railMetadata, compact = true))
                    .fillMaxHeight(),
                compact = true,
                segment = TimeframeRailSegment.COMPACT,
            )

            Spacer(Modifier.width(TaskTimelineRailGap))

            // Date chip — centered in the label column, same slot as expanded
            TimelineDateChipSlot(
                date = section.date,
                modifier = Modifier.fillMaxHeight(),
            )

            // Vertical divider — continuous across rows (same x, flush rows)
            Box(
                modifier = Modifier
                    .width(TaskTimelineDividerWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )

            Spacer(Modifier.width(12.dp))

            // Content — no card wrapper, just text
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = collapsedDaySummaryText(section),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // Horizontal divider — only in content area to preserve continuous timeline
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .widthIn(min = 0.dp)
                .fillMaxWidth(1f)
                .height(TaskTimelineDividerWidth)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun TimelineDateChipSlot(
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.widthIn(min = TaskTimelineLabelWidth),
        contentAlignment = Alignment.CenterStart,
    ) {
        DateChip(text = compactStickyDateText(date))
    }
}

@Composable
private fun ExpandedContinuousTimeline(
    scrollPx: Int,
    onScrollPxChange: (Int) -> Unit,
    blocks: List<ScheduleBlock>,
    tasksById: Map<String, ScheduleTask>,
    timeframes: List<Timeframe>,
    today: LocalDate,
    zoneId: ZoneId,
    hourHeight: Dp,
    dayHeightPx: Int,
    allowConcurrentTasks: Boolean,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentOnOpenTask by rememberUpdatedState(onOpenTask)

    BoxWithConstraints(modifier = modifier) {
        val viewportHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val bottomPaddingPx = with(density) { 260.dp.roundToPx() }
        val maxScrollPx = (TaskFeedDayCount * dayHeightPx + bottomPaddingPx - viewportHeightPx)
            .coerceAtLeast(0)
        val safeScrollPx = scrollPx.coerceIn(0, maxScrollPx)

        LaunchedEffect(scrollPx, safeScrollPx) {
            if (scrollPx != safeScrollPx) {
                onScrollPxChange(safeScrollPx)
            }
        }

        val scope = rememberCoroutineScope()
        // Track velocity and fling job for cancel-on-touch
        val flingVelocity = remember { mutableStateOf(0f) }
        val flingJob = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        val firstVisibleDayIndex = (safeScrollPx / dayHeightPx).coerceIn(0, TaskFeedDayCount - 1)
        val lastVisibleDayIndex = ((safeScrollPx + viewportHeightPx) / dayHeightPx + 1)
            .coerceIn(firstVisibleDayIndex, TaskFeedDayCount - 1)
        val visibleDayIndices = firstVisibleDayIndex..lastVisibleDayIndex
        val railStripWidth = maxTimelineRailStripWidth(compact = false)
        val contentStart = railStripWidth + TaskTimelineRailGap +
            TaskTimelineLabelWidth + TaskTimelineContentInset
        val contentWidth = maxWidth - contentStart
        val hourHeightPx = with(density) { hourHeight.toPx() }
        val minTaskHeightPx = with(density) { 64.dp.roundToPx() }
        val titleReservePx = with(density) { 88.dp.roundToPx() }
        val headerHeightPx = with(density) { ExpandedDayHeaderHeight.roundToPx() }
        val stickyTitleMinYPx = headerHeightPx + with(density) { ExpandedTaskStickyTitleTopInset.roundToPx() }
        val lineStart = railStripWidth + TaskTimelineRailGap + TaskTimelineLabelWidth

        val activeTaskSegments = remember(blocks, tasksById) {
            blocks
                .filter { it.completionState != dev.codex.reclaimoss.domain.model.BlockCompletionState.COMPLETED }
                .filter { tasksById[it.taskId]?.status == TaskStatus.ACTIVE }
                .sortedBy { it.startAt }
                .map {
                    VisibleTaskSegment(
                        block = it,
                        continuesFromPreviousDay = false,
                        continuesIntoNextDay = false,
                    )
                }
        }
        val positionedBlocks = remember(activeTaskSegments, allowConcurrentTasks) {
            if (allowConcurrentTasks) {
                computeTaskBlockLayout(activeTaskSegments)
            } else {
                activeTaskSegments.map { PositionedTaskBlock(it, laneIndex = 0, totalLanes = 1) }
            }
        }
        val headerTransition = resolveExpandedHeaderTransition(
            scrollPx = safeScrollPx,
            dayHeightPx = dayHeightPx,
            maxIndex = TaskFeedDayCount - 1,
            handoffHeightPx = headerHeightPx,
        )
        val stickyHeaderTopPx = with(density) { ExpandedDayHeaderTopInset.roundToPx() }
        val dateChipHeightPx = with(density) { ExpandedDateChipSlotHeight.roundToPx() }
        val dateChipGapPx = with(density) { ExpandedDateChipGap.roundToPx() }
        val dateChipAboveMidnightOffsetPx = with(density) { ExpandedDateChipAboveMidnightOffset.roundToPx() }

        fun dateChipYForDayIndex(index: Int): Int {
            val bodyOffsetPx = index * dayHeightPx - safeScrollPx - dateChipAboveMidnightOffsetPx
            val nextBodyOffsetPx = (index + 1) * dayHeightPx - safeScrollPx - dateChipAboveMidnightOffsetPx
            return resolveExpandedDateChipY(
                bodyOffsetPx = bodyOffsetPx,
                nextBodyOffsetPx = nextBodyOffsetPx,
                stickyYPx = stickyHeaderTopPx,
                chipHeightPx = dateChipHeightPx,
                chipGapPx = dateChipGapPx,
            )
        }

        val visibleDayRails = visibleDayIndices.map { dayIndex ->
            val date = taskFeedDateForIndex(today, dayIndex)
            dayIndex to railMetadataForDate(timeframes, date)
        }
        val previousTimeframeSlots = remember { mutableMapOf<String, Int>() }
        // Two-frame stability check: only commit a slot to history when it holds
        // for two consecutive frames, preventing single-frame flicker at scroll
        // boundaries from causing slot oscillation.
        val pendingTimeframeSlots = remember { mutableMapOf<String, Int>() }
        val timeframeChipPlacements = buildTimeframeChipPlacements(
            visibleDayRails = visibleDayRails,
            firstVisibleDayIndex = firstVisibleDayIndex,
            previousSlots = previousTimeframeSlots,
        )
        LaunchedEffect(timeframeChipPlacements) {
            val currentSlots = mutableMapOf<String, Int>()
            timeframeChipPlacements.forEach { p ->
                if (p.motion != StickyHeaderTimeframeChipMotion.ENTERING) {
                    currentSlots[p.id] = p.toSlot
                }
            }
            // Commit only when the slot matches the pending (previous frame) value
            previousTimeframeSlots.clear()
            currentSlots.forEach { (id, slot) ->
                if (pendingTimeframeSlots[id] == slot) {
                    previousTimeframeSlots[id] = slot
                }
            }
            // Update pending for next frame's comparison
            pendingTimeframeSlots.clear()
            pendingTimeframeSlots.putAll(currentSlots)
        }
        val headerDateChipOffsets = HeaderChipVerticalOffsets(
            outgoingDateYPx = dateChipYForDayIndex(firstVisibleDayIndex),
            incomingDateYPx = null,
        )

        val scrollableState = rememberScrollableState { delta ->
            // Cancel any running fling when user touches the screen
            flingJob.value?.cancel()
            if (abs(delta) > 1f) flingVelocity.value = delta
            val next = (safeScrollPx - delta.roundToInt()).coerceIn(0, maxScrollPx)
            if (next != safeScrollPx) onScrollPxChange(next)
            delta
        }

        // Fling: animate momentum when user lifts finger and velocity is significant
        val currentScroll by rememberUpdatedState(safeScrollPx)
        val currentMax by rememberUpdatedState(maxScrollPx)
        LaunchedEffect(flingVelocity.value) {
            val velocity = flingVelocity.value
            if (abs(velocity) < 10f) return@LaunchedEffect
            // Wait one frame to confirm drag has ended
            delay(32)
            if (abs(flingVelocity.value) > 1f) return@LaunchedEffect // still dragging
            val startPx = currentScroll
            flingJob.value = launch {
                var pos = 0f
                var vel = velocity
                while (abs(vel) > 1f) {
                    val delta = vel * 0.016f
                    pos += delta
                    vel *= 0.94f
                    val next = (startPx - pos.roundToInt()).coerceIn(0, currentMax)
                    onScrollPxChange(next)
                    if (next == 0 || next == currentMax) break
                    delay(16)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .scrollable(
                    state = scrollableState,
                    orientation = Orientation.Vertical,
                ),
        ) {
            // Vertical divider — spans full viewport, not per-day
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = railStripWidth + TaskTimelineRailGap + TaskTimelineLabelWidth - TaskTimelineDividerWidth)
                    .width(TaskTimelineDividerWidth)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )

            // Timeframe rails — full viewport overlay stitched from visible days
            visibleDayIndices.forEach { dayIndex ->
                val date = taskFeedDateForIndex(today, dayIndex)
                val dayTopPx = dayIndex * dayHeightPx - safeScrollPx
                val dayBottomPx = dayTopPx + dayHeightPx
                val visibleTop = dayTopPx.coerceAtLeast(0)
                val visibleBottom = dayBottomPx.coerceAtMost(viewportHeightPx)
                if (visibleBottom > visibleTop) {
                    val dayRailMetadata = railMetadataForDate(timeframes, date)
                    TimeframeRailStrip(
                        rails = dayRailMetadata,
                        modifier = Modifier
                            .offset { IntOffset(0, visibleTop) }
                            .height(with(density) { (visibleBottom - visibleTop).toDp() })
                            .width(timelineRailStripWidth(dayRailMetadata, compact = false)),
                        compact = false,
                        segment = TimeframeRailSegment.BODY,
                    )
                }
            }

            visibleDayIndices.forEach { dayIndex ->
                val date = taskFeedDateForIndex(today, dayIndex)
                val dayTopPx = dayIndex * dayHeightPx - safeScrollPx
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { dayHeightPx.toDp() })
                        .offset { IntOffset(0, dayTopPx) },
                    horizontalArrangement = Arrangement.spacedBy(TaskTimelineRailGap),
                ) {
                    // Spacer instead of rails (rails rendered as overlay above)
                    Spacer(Modifier
                        .width(timelineRailStripWidth(railMetadataForDate(timeframes, date), compact = false))
                        .fillMaxHeight()
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        FullDayTimeline(
                            segments = emptyList(),
                            tasksById = emptyMap(),
                            zoneId = zoneId,
                            day = date,
                            hourHeight = hourHeight,
                            showTaskCards = false,
                            showMidnightLabel = true,
                            drawVerticalDivider = false,
                            drawNowIndicator = false,
                            onOpenTask = {},
                            onDeleteTask = {},
                        )
                    }
                }
            }

            positionedBlocks.forEach { positioned ->
                val block = positioned.segment.block
                val frame = expandedTimelineBlockFramePx(
                    block = block,
                    today = today,
                    zoneId = zoneId,
                    dayHeightPx = dayHeightPx,
                    hourHeightPx = hourHeightPx,
                    minHeightPx = minTaskHeightPx,
                )
                val topPx = frame.topPx - safeScrollPx
                val visibleFrame = expandedTimelineVisibleBlockFramePx(
                    topPx = topPx,
                    heightPx = frame.heightPx,
                    viewportHeightPx = viewportHeightPx,
                )
                if (visibleFrame != null) {
                    val sourceStickyTitleOffsetPx = (stickyTitleMinYPx - topPx)
                        .coerceAtLeast(0)
                        .coerceAtMost((frame.heightPx - titleReservePx).coerceAtLeast(0))
                    val titleOffsetInVisibleSlicePx = (topPx + sourceStickyTitleOffsetPx - visibleFrame.topPx)
                        .coerceAtLeast(0)
                        .coerceAtMost((visibleFrame.heightPx - titleReservePx).coerceAtLeast(0))
                    val taskTopPx = visibleFrame.topPx
                    val taskBottomPx = taskTopPx + visibleFrame.heightPx
                    // now-overlap border is controlled by the separate NowIndicator composable;
                    // task blocks no longer depend on the minute timer for recomposition.
                    val nowOverlapsTask = false
                    FullDayTaskBlock(
                        positionedBlock = positioned,
                        task = tasksById[block.taskId],
                        zoneId = zoneId,
                        contentStart = contentStart,
                        contentWidth = contentWidth,
                        hourHeight = hourHeight,
                        onOpen = { currentOnOpenTask(block.taskId) },
                        absoluteY = with(density) { visibleFrame.topPx.toDp() },
                        absoluteHeight = with(density) { visibleFrame.heightPx.toDp() },
                        renderContinuesFromPrevious = !visibleFrame.hasOriginalTop,
                        renderContinuesIntoNext = !visibleFrame.hasOriginalBottom,
                        showTitle = true,
                        stickyTitleOffset = with(density) { titleOffsetInVisibleSlicePx.toDp() },
                        nowLineOverlaps = nowOverlapsTask,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(ExpandedDayHeaderHeight)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.background)
                    .zIndex(30f),
            ) {
                PinnedExpandedTimelineHeader(
                    timeframePlacements = timeframeChipPlacements,
                    outgoingDateYPx = headerDateChipOffsets.outgoingDateYPx,
                    incomingDateYPx = headerDateChipOffsets.incomingDateYPx,
                    dateChipYForIndex = ::dateChipYForDayIndex,
                    firstVisibleDayIndex = firstVisibleDayIndex,
                    modifier = Modifier,
                )
            }

            visibleDayIndices.forEach { dayIndex ->
                TimelineDateChipSlot(
                    date = taskFeedDateForIndex(today, dayIndex),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            IntOffset(
                                with(density) { (railStripWidth + TaskTimelineRailGap).roundToPx() },
                                dateChipYForDayIndex(dayIndex),
                            )
                        }
                        .height(ExpandedDateChipSlotHeight)
                        .zIndex(40f),
                )
            }
        }
    }
}

@Composable
private fun ExpandedTimelineNowIndicator(
    today: LocalDate,
    zoneId: ZoneId,
    hourHeight: Dp,
    dayHeightPx: Int,
    safeScrollPx: Int,
    lineStart: Dp,
    viewportHeightPx: Int,
) {
    val density = LocalDensity.current
    var now by remember { mutableStateOf(LocalTime.now(zoneId)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now(zoneId)
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val nowYPx = expandedTimelineScrollPxForDate(
        today = today,
        date = today,
        dayHeightPx = dayHeightPx,
        dayOffsetPx = with(density) {
            timelineOffset(minutesFromStart(now), hourHeight).roundToPx()
        },
    ) - safeScrollPx

    if (nowYPx in -8..viewportHeightPx) {
        Box(
            modifier = Modifier
                .offset(x = lineStart - 13.dp, y = with(density) { nowYPx.toDp() } - 7.dp)
                .size(14.dp)
                .zIndex(3f)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = lineStart)
                .height(2.dp)
                .offset(y = with(density) { nowYPx.toDp() })
                .zIndex(3f)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        )
    }
}

@Composable
private fun PinnedExpandedTimelineHeader(
    timeframePlacements: List<TimeframeChipPlacement>,
    outgoingDateYPx: Int,
    incomingDateYPx: Int?,
    dateChipYForIndex: (Int) -> Int = { 0 },
    firstVisibleDayIndex: Int = 0,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val headerTopInsetPx = with(density) { ExpandedDayHeaderTopInset.roundToPx() }
    val chipStartPx = with(density) {
        (maxTimelineRailStripWidth(compact = false) +
            TaskTimelineRailGap +
            TaskTimelineLabelWidth +
            TaskTimelineContentInset).roundToPx()
    }
    val chipStridePx = with(density) { TimeframeHeaderChipSlotStep.roundToPx() }

    // Track previous slots per chip ID for animation continuity
    val slotAnimations = remember { mutableMapOf<String, Animatable<Float, *>>() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ExpandedDayHeaderHeight),
    ) {
        val headerHeightPx = with(density) { ExpandedDayHeaderHeight.roundToPx() }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(chipStartPx, 0) },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Only PINNED/EXITING chips participate in the Row layout.
            // ENTERING chips render below the header; keeping them out of the Row
            // prevents them from displacing stable chips and causing slot flicker.
            val headerPlacements = timeframePlacements.filter {
                it.motion == StickyHeaderTimeframeChipMotion.PINNED ||
                    it.motion == StickyHeaderTimeframeChipMotion.EXITING
            }
            headerPlacements.forEach { placement ->
                androidx.compose.runtime.key(placement.id) {
                    // Animate horizontal translation when slot changes.
                    val fromSlot = placement.fromSlot.toFloat()
                    val toSlot = placement.toSlot.toFloat()
                    val needsAnimation = placement.fromSlot != placement.toSlot

                    val anim = slotAnimations.getOrPut(placement.id) {
                        Animatable(fromSlot)
                    }
                    LaunchedEffect(placement.fromSlot, placement.toSlot) {
                        if (needsAnimation) {
                            anim.snapTo(fromSlot)
                            anim.animateTo(toSlot, animationSpec = tween(250, easing = FastOutSlowInEasing))
                        } else {
                            anim.snapTo(toSlot)
                        }
                    }

                    // When fromSlot == toSlot, always use 0 delta so the chip
                    // renders at its natural Row position regardless of stale anim state.
                    val slotDelta = if (needsAnimation) anim.value - toSlot else 0f
                    val animTranslationX = (slotDelta * chipStridePx)

                    val chipOffsetY = (when (placement.motion) {
                        StickyHeaderTimeframeChipMotion.PINNED -> headerTopInsetPx
                        StickyHeaderTimeframeChipMotion.EXITING ->
                            if (placement.trackDayIndex == firstVisibleDayIndex) outgoingDateYPx
                            else headerTopInsetPx
                        else -> headerTopInsetPx
                    })
                    if (chipOffsetY > -headerHeightPx && chipOffsetY < headerHeightPx * 3) {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = animTranslationX
                                }
                                .offset { IntOffset(0, chipOffsetY) }
                                .height(ExpandedDateChipSlotHeight)
                                .zIndex(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            TimeframeNameChip(
                                text = placement.name,
                                borderColor = parseTimeframeColor(placement.colorHex),
                                modifier = Modifier.widthIn(max = TimeframeHeaderChipMaxWidth),
                            )
                        }
                    }
                }
            }
            // ENTERING chips render below the header, positioned absolutely near
            // their track day — they don't affect header Row layout at all.
            val enteringPlacements = timeframePlacements.filter {
                it.motion == StickyHeaderTimeframeChipMotion.ENTERING
            }
            enteringPlacements.forEach { placement ->
                androidx.compose.runtime.key(placement.id) {
                    val chipOffsetY = dateChipYForIndex(placement.trackDayIndex)
                    if (chipOffsetY > -headerHeightPx && chipOffsetY < headerHeightPx * 3) {
                        // Position entering chip near its track day, using toSlot for
                        // approximate horizontal placement (won't affect pinned chips).
                        val enteringX = chipStartPx + (placement.toSlot * chipStridePx)
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(enteringX, chipOffsetY) }
                                .height(ExpandedDateChipSlotHeight)
                                .zIndex(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            TimeframeNameChip(
                                text = placement.name,
                                borderColor = parseTimeframeColor(placement.colorHex),
                                modifier = Modifier.widthIn(max = TimeframeHeaderChipMaxWidth),
                            )
                        }
                    }
                }
            }
        }

        // Clean up animations for chips that are no longer present
        val currentIds = timeframePlacements.map { it.id }.toSet()
        LaunchedEffect(currentIds) {
            slotAnimations.keys.removeAll { it !in currentIds }
        }
        DisposableEffect(Unit) {
            onDispose {
                slotAnimations.clear()
            }
        }
    }
}

internal fun resolveExpandedDateChipY(
    bodyOffsetPx: Int,
    nextBodyOffsetPx: Int,
    stickyYPx: Int,
    chipHeightPx: Int,
    chipGapPx: Int = 0,
): Int {
    val pinnedY = bodyOffsetPx.coerceAtLeast(stickyYPx)
    return pinnedY.coerceAtMost(nextBodyOffsetPx - chipHeightPx - chipGapPx)
}

@Composable
private fun DateChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimeframeNameChip(
    text: String,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = 0.88f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class TimeframeRailSegment {
    COMPACT,
    HEADER,
    BODY,
}

internal fun stickyTimeframeHeaderLabels(rails: List<TimeframeRailMetadata>): List<TimeframeHeaderLabel> =
    rails.distinctBy { it.id }
        .map { TimeframeHeaderLabel(name = it.name, colorHex = it.colorHex) }

internal fun compactStickyDateText(date: LocalDate): String = "${date.dayOfMonth}/${date.monthValue}"

internal fun shouldShowTimelineHourLabel(hour: Int): Boolean = hour != 24

internal fun shouldShowTimelineHourDivider(hour: Int): Boolean = hour in 0..24

internal fun stickyTimeframeCardText(rails: List<TimeframeRailMetadata>): String? =
    rails.map { it.name }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" \u00B7 ")

internal fun stickyTimeframeHeaderNames(rails: List<TimeframeRailMetadata>): String? =
    rails.map { it.name }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" \u00B7 ")

internal fun stickyTimeframeHeaderText(rails: List<TimeframeRailMetadata>): String? =
    rails.map { it.name }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" \u00B7 ")

private fun timelineRailStripWidth(rails: List<TimeframeRailMetadata>, compact: Boolean): Dp {
    val railWidth = if (compact) TaskTimelineCompactRailWidth else TaskTimelineExpandedRailWidth
    return railWidth * MaxOverlappingTimeframeRails
}

private fun maxTimelineRailStripWidth(compact: Boolean): Dp {
    val railWidth = if (compact) TaskTimelineCompactRailWidth else TaskTimelineExpandedRailWidth
    return railWidth * MaxOverlappingTimeframeRails
}

private fun orderedTimeframeRailsForDisplay(
    rails: List<TimeframeRailMetadata>,
): List<TimeframeRailMetadata> {
    return rails
        .distinctBy { it.id }
        .sortedWith(
            compareBy<TimeframeRailMetadata> { it.startDate }
                .thenBy { it.name }
                .thenBy { it.id }
        )
        .take(MaxOverlappingTimeframeRails)
        .asReversed()
}

@Composable
private fun TimeframeRailStrip(
    rails: List<TimeframeRailMetadata>,
    modifier: Modifier = Modifier,
    compact: Boolean,
    segment: TimeframeRailSegment = TimeframeRailSegment.COMPACT,
) {
    val railWidth = if (compact) TaskTimelineCompactRailWidth else TaskTimelineExpandedRailWidth
    val orderedRails = orderedTimeframeRailsForDisplay(rails)
    val visible: List<TimeframeRailMetadata?> =
        if (orderedRails.isEmpty()) {
            List(MaxOverlappingTimeframeRails) { null }
        } else {
            val blanks = List((MaxOverlappingTimeframeRails - orderedRails.size).coerceAtLeast(0)) { null }
            blanks + orderedRails
        }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        visible.forEach { rail ->
            if (rail == null) {
                Spacer(Modifier.width(railWidth))
            } else {
                val color = parseTimeframeColor(rail.colorHex)
                val topConnected = when (segment) {
                    TimeframeRailSegment.COMPACT -> rail.continuesFromPreviousDay
                    TimeframeRailSegment.HEADER -> rail.continuesFromPreviousDay
                    TimeframeRailSegment.BODY -> true
                }
                val bottomConnected = when (segment) {
                    TimeframeRailSegment.COMPACT -> rail.continuesIntoNextDay
                    TimeframeRailSegment.HEADER -> true
                    TimeframeRailSegment.BODY -> rail.continuesIntoNextDay
                }
                val shape = RoundedCornerShape(
                    topStart = if (topConnected) 0.dp else 10.dp,
                    topEnd = if (topConnected) 0.dp else 10.dp,
                    bottomStart = if (bottomConnected) 0.dp else 10.dp,
                    bottomEnd = if (bottomConnected) 0.dp else 10.dp,
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(railWidth)
                        .clip(shape)
                        .background(color.copy(alpha = 0.88f)),
                )
            }
        }
    }
}

@Composable
private fun SleepSetupHintBanner(
    coveredCount: Int,
    onAddSleep: () -> Unit,
    onDismiss: () -> Unit,
    readyForTasks: Boolean = false,
) {
    val text = if (readyForTasks) "All 7 days covered. Ready to create tasks around your sleep schedule."
    else if (coveredCount == 0) "Add your sleeping hours"
    else "$coveredCount of 7 days configured — finish your sleep schedule"
    val action = if (readyForTasks) "Get started"
    else if (coveredCount == 0) "Set up" else "Continue"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onAddSleep) {
                Text(action, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun TasksSheetActionList(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        actions.forEach { (label, action) ->
            FilledTonalButton(
                onClick = action,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DaySummarySheet(
    section: TaskDaySection,
    formatter: DateTimeFormatter,
    zoneId: ZoneId,
    onExpand: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(formatter.format(section.date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(collapsedDaySummaryText(section), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (section.timeframes.isNotEmpty()) {
            Text(
                section.timeframes.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (section.tasks.isNotEmpty()) {
            Text("Tasks", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            section.tasks.forEach { task ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTask(task.id) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(task.status.name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        TextButton(onClick = onExpand, modifier = Modifier.align(Alignment.End)) {
            Text("Expand Day")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun HeaderActionSlot(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.width(HeaderActionSlotWidth),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun HeaderActionButton(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    width: Dp = HeaderActionWidth,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(HeaderActionHeight),
        shape = HeaderActionShape,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, maxLines = 1)
    }
}

@Composable
fun FullDayTimeline(
    segments: List<VisibleTaskSegment>,
    tasksById: Map<String, ScheduleTask>,
    zoneId: ZoneId,
    day: LocalDate,
    hourHeight: Dp,
    allowConcurrentTasks: Boolean = false,
    showTaskCards: Boolean = true,
    showCardTitles: Boolean = true,
    showMidnightLabel: Boolean = true,
    drawVerticalDivider: Boolean = true,
    drawNowIndicator: Boolean = true,
    onOpenTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
) {
    val labelWidth = TaskTimelineLabelWidth
    val timelineHeight = timelineOffset(minutes = 24 * 60, hourHeight = hourHeight)
    var now by remember { mutableStateOf(LocalTime.now(zoneId)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now(zoneId)
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val showNowIndicator = drawNowIndicator && day == LocalDate.now(zoneId)

    val positionedBlocks = remember(segments, allowConcurrentTasks, showTaskCards) {
        if (!showTaskCards) emptyList()
        else if (allowConcurrentTasks) {
            computeTaskBlockLayout(segments)
        } else {
            segments.sortedBy { it.block.startAt }.map { PositionedTaskBlock(it, laneIndex = 0, totalLanes = 1) }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(timelineHeight),
    ) {
        val contentStart = labelWidth + TaskTimelineContentInset
        val contentWidth = maxWidth - contentStart
        for (hour in 0..24) {
            val top = timelineOffset(minutes = hour * 60, hourHeight = hourHeight)
            if (shouldShowTimelineHourLabel(hour) && (hour != 0 || showMidnightLabel)) {
                Text(
                    LocalTime.of(hour, 0).formatHourLabel(),
                    modifier = Modifier
                        .width(labelWidth)
                        .offset(y = if (hour == 0) top else top - 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (shouldShowTimelineHourDivider(hour)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = labelWidth)
                        .height(TaskTimelineDividerWidth)
                        .offset(y = top)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                )
            }
        }
        if (drawVerticalDivider) {
            Box(
                modifier = Modifier
                    .offset(x = labelWidth - TaskTimelineDividerWidth)
                    .width(TaskTimelineDividerWidth)
                    .height(timelineHeight)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )
        }
        positionedBlocks.forEach { positioned ->
            FullDayTaskBlock(
                positionedBlock = positioned,
                task = tasksById[positioned.segment.block.taskId],
                zoneId = zoneId,
                contentStart = contentStart,
                contentWidth = contentWidth,
                hourHeight = hourHeight,
                showTitle = showCardTitles,
                onOpen = { onOpenTask(positioned.segment.block.taskId) },
            )
        }
        if (showNowIndicator) {
            val nowTop = timelineOffset(minutes = minutesFromStart(now), hourHeight = hourHeight)
            Box(
                modifier = Modifier
                    .offset(x = labelWidth - 13.dp, y = nowTop - 7.dp)
                    .size(14.dp)
                    .zIndex(3f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = labelWidth)
                    .height(2.dp)
                    .offset(y = nowTop)
                    .zIndex(3f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
            )
        }
    }
}

data class PositionedTaskBlock(
    val segment: VisibleTaskSegment,
    val laneIndex: Int,
    val totalLanes: Int,
)

fun visibleBlocksForDay(
    blocks: List<ScheduleBlock>,
    day: LocalDate,
    zoneId: ZoneId,
): List<ScheduleBlock> {
    return visibleTaskSegmentsForDay(blocks, day, zoneId).map { it.block }
}

fun visibleTaskSegmentsForDay(
    blocks: List<ScheduleBlock>,
    day: LocalDate,
    zoneId: ZoneId,
): List<VisibleTaskSegment> {
    val dayStart = day.atStartOfDay(zoneId).toInstant()
    val nextDayStart = day.plusDays(1).atStartOfDay(zoneId).toInstant()
    return blocks.mapNotNull { block ->
        val segmentStart = if (block.startAt >= dayStart) block.startAt else dayStart
        val segmentEnd = if (block.endAt <= nextDayStart) block.endAt else nextDayStart
        if (!segmentEnd.isAfter(segmentStart)) {
            null
        } else {
            VisibleTaskSegment(
                block = block.copy(startAt = segmentStart, endAt = segmentEnd),
                continuesFromPreviousDay = block.startAt < dayStart,
                continuesIntoNextDay = block.endAt > nextDayStart,
            )
        }
    }
}

internal fun expandedTaskSegmentsForDay(
    blocks: List<ScheduleBlock>,
    day: LocalDate,
    zoneId: ZoneId,
): List<VisibleTaskSegment> = visibleTaskSegmentsForDay(blocks, day, zoneId)

private fun computeTaskBlockLayout(segments: List<VisibleTaskSegment>): List<PositionedTaskBlock> {
    data class ActiveLane(val endAt: Instant, val laneIndex: Int)
    data class AssignedBlock(val segment: VisibleTaskSegment, val laneIndex: Int, val groupId: Int)

    val sorted = segments.sortedBy { it.block.startAt }
    val active = mutableListOf<ActiveLane>()
    val assigned = mutableListOf<AssignedBlock>()
    var groupId = -1

    for (segment in sorted) {
        active.removeAll { !it.endAt.isAfter(segment.block.startAt) }
        if (active.isEmpty()) groupId += 1
        val usedLanes = active.map { it.laneIndex }.toSet()
        var laneIndex = 0
        while (laneIndex in usedLanes) laneIndex += 1
        active += ActiveLane(segment.block.endAt, laneIndex)
        assigned += AssignedBlock(segment, laneIndex, groupId)
    }

    val groupLaneCounts = assigned.groupBy { it.groupId }.mapValues { (_, group) ->
        group.maxOf { it.laneIndex } + 1
    }
    return assigned.map { PositionedTaskBlock(it.segment, it.laneIndex, groupLaneCounts.getValue(it.groupId)) }
}

@Composable
fun CompletedTaskHistoryCard(task: ScheduleTask, zoneId: ZoneId) {
    val completedFormatter = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Completed ${task.updatedAt.atZone(zoneId).format(completedFormatter)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun FullDayTaskBlock(
    positionedBlock: PositionedTaskBlock,
    task: ScheduleTask?,
    zoneId: ZoneId,
    contentStart: Dp,
    contentWidth: Dp,
    hourHeight: Dp,
    onOpen: () -> Unit,
    absoluteY: Dp? = null,
    absoluteHeight: Dp? = null,
    renderContinuesFromPrevious: Boolean = positionedBlock.segment.continuesFromPreviousDay,
    renderContinuesIntoNext: Boolean = positionedBlock.segment.continuesIntoNextDay,
    showTitle: Boolean = true,
    stickyTitleOffset: Dp = 0.dp,
    nowLineOverlaps: Boolean = false,
) {
    val block = positionedBlock.segment.block
    val density = LocalDensity.current
    val start = block.startAt.atZone(zoneId).toLocalTime()
    val topExtension = if (renderContinuesFromPrevious) TaskTimelineBoundaryOverlap else 0.dp
    val bottomExtension = if (renderContinuesIntoNext) TaskTimelineBoundaryOverlap else 0.dp
    val computedTop = timelineOffset(minutes = minutesFromStart(start), hourHeight = hourHeight) - topExtension
    val durationMinutes = java.time.Duration.between(block.startAt, block.endAt).toMinutes().toInt().coerceAtLeast(30)
    val computedHeight = timelineBlockHeight(minutes = durationMinutes, hourHeight = hourHeight, minHeight = 64.dp) + topExtension + bottomExtension
    val top = absoluteY ?: computedTop
    val height = absoluteHeight ?: computedHeight
    val laneGap = 8.dp
    val laneCount = positionedBlock.totalLanes.coerceAtLeast(1)
    val laneWidth = (contentWidth - laneGap * (laneCount - 1)) / laneCount
    val xOffset = contentStart + (laneWidth + laneGap) * positionedBlock.laneIndex
    val shape = RoundedCornerShape(
        topStart = if (renderContinuesFromPrevious) 0.dp else 22.dp,
        topEnd = if (renderContinuesFromPrevious) 0.dp else 22.dp,
        bottomStart = if (renderContinuesIntoNext) 0.dp else 22.dp,
        bottomEnd = if (renderContinuesIntoNext) 0.dp else 22.dp,
    )
    val showsBoundaryContinuation =
        renderContinuesFromPrevious || renderContinuesIntoNext
    val cardModifier = Modifier
        .requiredWidth(laneWidth)
        .requiredHeight(height)
        .offset(x = xOffset, y = top)
        .zIndex(1f)
    val tapCallback by rememberUpdatedState(onOpen)
    val positionedModifier = cardModifier.clickable(onClick = { tapCallback() })
    // Shaded by default; only add outline when the now-time line overlaps this block
    val hasBorder = nowLineOverlaps && !showsBoundaryContinuation
    Card(
        modifier = positionedModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (hasBorder) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
        border = if (hasBorder) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (showsBoundaryContinuation || !hasBorder) 0.dp else 4.dp
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showTitle) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 18.dp)
                        .offset(y = stickyTitleOffset + 14.dp),
                ) {
                    Text(
                        text = task?.title ?: block.taskId,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

