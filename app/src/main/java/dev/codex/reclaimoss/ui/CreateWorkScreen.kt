package dev.codex.reclaimoss.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.NumberPicker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import dev.codex.reclaimoss.domain.model.TaskContinuationMode
import dev.codex.reclaimoss.domain.model.TaskKind
import dev.codex.reclaimoss.domain.model.TaskOverlapPolicy
import dev.codex.reclaimoss.domain.model.TaskSchedulingMode
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.TaskStatus
import dev.codex.reclaimoss.domain.model.Timeframe
import dev.codex.reclaimoss.domain.model.TimePeriod
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
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun TaskSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun CreateFormCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateWorkScreen(
    padding: PaddingValues,
    timeframes: List<Timeframe>,
    availableTasks: List<ScheduleTask>,
    allowConcurrentTasks: Boolean,
    defaultTaskSplitting: Boolean = true,
    currentTaskId: String? = null,
    sessionKey: Int,
    initialTaskDraft: TaskDraft? = null,
    followUpMode: Boolean = false,
    editMode: Boolean = false,
    rescheduleMode: Boolean = false,
    sleepMode: Boolean = false,
    showTaskTutorial: Boolean = false,
    existingSleepTasks: List<ScheduleTask> = emptyList(),
    onBack: () -> Unit,
    onSaveTask: (TaskDraft) -> Unit,
    isSaving: Boolean = false,
) {
    val context = LocalContext.current
    var taskDraft by rememberSaveable(
        sessionKey,
        stateSaver = listSaver(
            save = {
                listOf(
                    it.title,
                    it.description,
                    it.priority.name,
                    it.preferredTimePeriodId ?: "",
                    it.timeframeId ?: "",
                    it.hasDeadline,
                    it.continuationParentTaskId ?: "",
                    it.continuationMode?.name ?: "",
                    it.noGap.toString(),
                    it.overlapPolicy.name,
                    it.allowSplitting,
                    it.deadline.toString(),
                    it.schedulingMode.name,
                    it.hasWindow,
                    it.startDate?.toString() ?: "",
                    it.fixedDate.toString(),
                    it.fixedStartAt.toString(),
                    it.fixedEndAt.toString(),
                    it.repeatsForever,
                    it.estimatedMinutes,
                    it.addReminder,
                    it.recurrenceType.name,
                    it.recurrenceInterval,
                    it.recurrenceDays.joinToString(",") { day -> day.name },
                )
            },
            restore = { saved ->
                if (saved.size != 24) {
                    TaskDraft()
                } else {
                    try {
                        TaskDraft(
                            title = saved[0] as String,
                            description = saved[1] as String,
                            priority = TaskPriority.valueOf(saved[2] as String),
                            preferredTimePeriodId = (saved[3] as String).ifBlank { null },
                            timeframeId = (saved[4] as String).ifBlank { null },
                            hasDeadline = saved[5] as Boolean,
                            continuationParentTaskId = (saved[6] as String).ifBlank { null },
                            continuationMode = (saved[7] as String).ifBlank { null }?.let(TaskContinuationMode::valueOf),
                            noGap = (saved[8] as String).toBooleanStrictOrNull() ?: false,
                            overlapPolicy = TaskOverlapPolicy.valueOf(saved[9] as String),
                            allowSplitting = saved[10] as Boolean,
                            deadline = LocalDateTime.parse(saved[11] as String),
                            schedulingMode = TaskSchedulingMode.valueOf(saved[12] as String),
                            hasWindow = saved[13] as Boolean,
                            startDate = (saved[14] as String).ifBlank { null }?.let(LocalDate::parse),
                            fixedDate = LocalDate.parse(saved[15] as String),
                            fixedStartAt = LocalDateTime.parse(saved[16] as String),
                            fixedEndAt = LocalDateTime.parse(saved[17] as String),
                            repeatsForever = saved[18] as Boolean,
                            estimatedMinutes = saved[19] as Int,
                            addReminder = saved[20] as Boolean,
                            recurrenceType = RecurrenceType.valueOf(saved[21] as String),
                            recurrenceInterval = saved[22] as Int,
                            recurrenceDays = (saved[23] as String)
                                .takeIf { it.isNotBlank() }
                                ?.split(",")
                                ?.map { DayOfWeek.valueOf(it) }
                                ?.toSet()
                                ?: emptySet(),
                        )
                    } catch (_: Exception) {
                        TaskDraft()
                    }
                }
            },
        ),
    ) {
        mutableStateOf(
            (initialTaskDraft ?: defaultCreateTaskDraft(
                    allowConcurrentTasks = allowConcurrentTasks,
                    defaultTaskSplitting = defaultTaskSplitting,
                ))
                .resolvedOverlapPolicy(allowConcurrentTasks),
        )
    }
    var showAdvancedOptions by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var showScheduleSheet by rememberSaveable(sessionKey) { mutableStateOf(false) }

    // Sleep coverage
    val coveredSleepDays = remember(existingSleepTasks) {
        existingSleepTasks.filter { it.taskKind == TaskKind.SLEEP && it.recurrenceRule.type == RecurrenceType.WEEKLY }
            .flatMap { it.recurrenceRule.daysOfWeek }.toSet()
    }
    val coveredCount = coveredSleepDays.size
    val showTutorial = (sleepMode && coveredCount < 7) || showTaskTutorial

    // Tutorial state
    var tutorialStep by remember { mutableIntStateOf(0) }
    val tutorialSteps = when {
        showTaskTutorial -> taskCreationSteps()
        coveredCount > 0 -> singleMissingStep()
        else -> missingDaysSteps(coveredCount)
    }
    val tutorialStepEnum = tutorialSteps.getOrNull(tutorialStep)
    var durationSectionBounds by remember { mutableStateOf<Rect?>(null) }
    var daysSectionBounds by remember { mutableStateOf<Rect?>(null) }
    var windowSectionBounds by remember { mutableStateOf<Rect?>(null) }
    var saveBarBounds by remember { mutableStateOf<Rect?>(null) }
    var moreOptionsBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
    val currentTutorialBounds = when {
        showTaskTutorial -> when (tutorialStep) {
            0 -> durationSectionBounds
            1 -> moreOptionsBounds
            2 -> saveBarBounds
            else -> null
        }
        else -> when (tutorialStep) {
            0 -> durationSectionBounds
            1 -> daysSectionBounds
            2 -> windowSectionBounds
            3 -> saveBarBounds
            else -> null
        }
    }

    // Scroll LazyColumn to tutorial step
    val listState = rememberLazyListState()
    LaunchedEffect(tutorialStep) {
        when (tutorialStep) {
            0 -> listState.animateScrollToItem(0)
            1 -> listState.animateScrollToItem(1)
            2 -> listState.animateScrollToItem(2)
            3 -> {} // save bar is always visible
        }
    }
    // Auto-expand "More options" when tutorial highlights it
    LaunchedEffect(tutorialStep, showTaskTutorial) {
        if (showTaskTutorial && tutorialStep == 1) {
            showAdvancedOptions = true
        }
    }
    // Auto-scroll to show expanded "More options" content
    LaunchedEffect(showAdvancedOptions) {
        if (showAdvancedOptions) {
            listState.animateScrollToItem(1)
        }
    }
    var showWindowSheet by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var showRepeatSheet by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var showRulesSheet by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var scheduleDraft by remember(sessionKey) { mutableStateOf(taskDraft) }
    var windowDraft by remember(sessionKey) { mutableStateOf(taskDraft) }
    var repeatDraft by remember(sessionKey) { mutableStateOf(taskDraft) }
    var rulesDraft by remember(sessionKey) { mutableStateOf(taskDraft) }
    val continuationTasks = remember(availableTasks, currentTaskId, taskDraft.continuationParentTaskId) {
        availableTasks
            .filter { it.status == TaskStatus.ACTIVE }
            .filterNot { it.id == currentTaskId }
            .sortedBy { it.dueAt }
    }
    LaunchedEffect(continuationTasks, taskDraft.continuationParentTaskId) {
        if (taskDraft.continuationParentTaskId != null && continuationTasks.none { it.id == taskDraft.continuationParentTaskId }) {
            taskDraft = taskDraft.copy(
                continuationParentTaskId = null,
                continuationMode = null,
            )
        } else if (taskDraft.continuationParentTaskId != null && taskDraft.continuationMode == null) {
            taskDraft = taskDraft.copy(continuationMode = TaskContinuationMode.AFTER_PARENT_SCHEDULED_END)
        }
    }
    LaunchedEffect(followUpMode, rescheduleMode) {
        if (followUpMode || rescheduleMode) {
            taskDraft = taskDraft.copy(
                addReminder = false,
                recurrenceType = if (followUpMode) RecurrenceType.NONE else taskDraft.recurrenceType,
                recurrenceInterval = if (followUpMode) 1 else taskDraft.recurrenceInterval,
                recurrenceDays = if (followUpMode) emptySet() else taskDraft.recurrenceDays,
            )
        }
    }
    LaunchedEffect(sleepMode) {
        if (sleepMode) {
            val today = java.time.LocalDate.now()
            val defaultStart = java.time.LocalDateTime.of(today, java.time.LocalTime.of(20, 0)) // 8 PM
            val defaultEnd = java.time.LocalDateTime.of(today.plusDays(1), java.time.LocalTime.of(8, 0)) // 8 AM next day
            taskDraft = taskDraft.copy(
                title = "Sleep",
                recurrenceType = RecurrenceType.WEEKLY,
                recurrenceInterval = 1,
                hasWindow = true,
                schedulingMode = TaskSchedulingMode.FLEXIBLE_WINDOW,
                fixedStartAt = defaultStart,
                fixedEndAt = defaultEnd,
                estimatedMinutes = 480,
                priority = TaskPriority.URGENT,
                overlapPolicy = TaskOverlapPolicy.DISALLOW,
                allowSplitting = false,
            )
        }
    }
    val availableTimeframes = remember(timeframes, taskDraft) {
        val cutoffDate = taskDraft.timeframeCutoffDate()
        timeframes
            .filter { cutoffDate == null || !it.startDate.isAfter(cutoffDate) }
            .sortedWith(compareBy<Timeframe> { it.startDate }.thenBy { it.endDate }.thenBy { it.name })
    }
    LaunchedEffect(availableTimeframes, taskDraft.timeframeId) {
        if (taskDraft.timeframeId != null && availableTimeframes.none { it.id == taskDraft.timeframeId }) {
            taskDraft = taskDraft.copy(timeframeId = null)
        }
    }
    val rulesSummary = remember(
        taskDraft.continuationParentTaskId,
        taskDraft.overlapPolicy,
        taskDraft.addReminder,
        taskDraft.priority,
        allowConcurrentTasks,
    ) {
        taskRulesSummary(taskDraft, allowConcurrentTasks)
    }
    val scheduleRowSummary = remember(
        taskDraft.schedulingMode,
        taskDraft.hasDeadline,
        taskDraft.deadline,
        taskDraft.fixedDate,
        taskDraft.fixedStartAt,
        taskDraft.fixedEndAt,
        taskDraft.recurrenceType,
    ) {
        taskScheduleRowSummary(taskDraft)
    }
    val repeatSummary = remember(
        taskDraft.recurrenceType,
        taskDraft.recurrenceInterval,
        taskDraft.recurrenceDays,
        taskDraft.repeatsForever,
        taskDraft.deadline,
        taskDraft.hasDeadline,
    ) {
        taskRepeatSummary(taskDraft)
    }
    val timingSummary = remember(
        taskDraft.hasWindow,
        taskDraft.fixedStartAt,
        taskDraft.fixedEndAt,
        taskDraft.schedulingMode,
        taskDraft.timeframeId,
        availableTimeframes,
    ) {
        taskTimingSummary(taskDraft, availableTimeframes)
    }
    val saveSummary = remember(
        taskDraft.schedulingMode,
        taskDraft.hasDeadline,
        taskDraft.deadline,
        taskDraft.fixedDate,
        taskDraft.fixedStartAt,
        taskDraft.fixedEndAt,
        taskDraft.estimatedMinutes,
    ) {
        taskScheduleSummary(taskDraft)
    }
    val canSaveTask = taskDraft.title.isNotBlank() &&
        (!taskDraft.hasWindow || taskDraft.fixedEndAt.isAfter(taskDraft.fixedStartAt)) &&
        (taskDraft.recurrenceType != RecurrenceType.WEEKLY || taskDraft.recurrenceDays.isNotEmpty())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .onGloballyPositioned { coords ->
                overlayOffset = coords.positionInRoot()
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            item {
                Box(
                    modifier = if (showTutorial) Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        val sz = coords.size
                        durationSectionBounds = Rect(pos, Size(sz.width.toFloat(), sz.height.toFloat()))
                    } else Modifier,
                ) {
                    CreateFormCard {
                        OutlinedTextField(
                        value = taskDraft.title,
                        onValueChange = { taskDraft = taskDraft.copy(title = it) },
                        label = { Text(if (sleepMode) "Name" else "Task title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    if (!sleepMode) {
                        OutlinedTextField(
                            value = taskDraft.description,
                            onValueChange = { taskDraft = taskDraft.copy(description = it) },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                    }
                    DurationWheelPicker(
                        minutes = taskDraft.estimatedMinutes,
                        onMinutesChanged = {
                            taskDraft = if (taskDraft.schedulingMode == TaskSchedulingMode.FIXED_EXACT) {
                                val updatedEnd = taskDraft.fixedStartAt.plusMinutes(it.toLong())
                                taskDraft.copy(
                                    estimatedMinutes = it,
                                    fixedEndAt = updatedEnd,
                                    deadline = updatedEnd,
                                )
                            } else {
                                taskDraft.copy(estimatedMinutes = it)
                            }
                        },
                    )
                }
                } // close Box
            }
            if (sleepMode) {
                // Inline weekly recurrence picker
                item {
                    Box(
                        modifier = if (showTutorial) Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot(); val sz = coords.size
                            daysSectionBounds = Rect(pos, Size(sz.width.toFloat(), sz.height.toFloat()))
                        } else Modifier,
                    ) {
                        CreateFormCard {
                            TaskSectionTitle("Repeat")
                            // Recurrence type chips
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                RecurrenceType.entries.forEach { type ->
                                    FilterChip(
                                        selected = taskDraft.recurrenceType == type,
                                        onClick = {
                                            taskDraft = taskDraft.copy(
                                                recurrenceType = type,
                                                recurrenceDays = if (type == RecurrenceType.WEEKLY) taskDraft.recurrenceDays else emptySet(),
                                            )
                                        },
                                        label = { Text(type.displayNameForCreate()) },
                                    )
                                }
                            }
                            // Weekday toggles — only shown for weekly recurrence
                            if (taskDraft.recurrenceType == RecurrenceType.WEEKLY) {
                            Box(
                                modifier = if (showTutorial) Modifier
                                    .padding(top = 12.dp)
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot(); val sz = coords.size
                                        daysSectionBounds = Rect(pos, Size(sz.width.toFloat(), sz.height.toFloat()))
                                    } else Modifier.padding(top = 12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    DayOfWeek.entries.forEach { day ->
                                        val isSelected = day in taskDraft.recurrenceDays
                                        Surface(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clickable {
                                                    taskDraft = taskDraft.copy(
                                                        recurrenceDays = taskDraft.recurrenceDays.toggleForCreate(day),
                                                    )
                                                },
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).first().toString(),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                    } // close Box
                }

                // Warning when overwriting existing sleep
                val sleepOverlapWarning = if (existingSleepTasks.isEmpty()) {
                    null
                } else when (taskDraft.recurrenceType) {
                    RecurrenceType.DAILY -> {
                        val coveredDays = existingSleepTasks
                            .filter { it.recurrenceRule.type == RecurrenceType.WEEKLY || it.recurrenceRule.type == RecurrenceType.DAILY }
                            .flatMap { it.recurrenceRule.daysOfWeek }.toSet()
                        if (coveredDays.isNotEmpty()) {
                            val dayNames = coveredDays.sortedBy { it.value }
                                .joinToString(", ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
                            "Sleep already set for $dayNames. Saving will replace those days."
                        } else {
                            "Sleep is already scheduled. Saving will replace it for all days."
                        }
                    }
                    RecurrenceType.WEEKLY -> {
                        val overlappingDays = taskDraft.recurrenceDays.filter { day ->
                            existingSleepTasks.any { it.recurrenceRule.type == RecurrenceType.WEEKLY && day in it.recurrenceRule.daysOfWeek }
                        }.toSet()
                        if (overlappingDays.isNotEmpty()) {
                            val dayNames = overlappingDays.sortedBy { it.value }
                                .joinToString(", ") { it.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) }
                            "Sleep already set for $dayNames. Saving will replace it."
                        } else null
                    }
                    RecurrenceType.MONTHLY -> if (existingSleepTasks.isNotEmpty()) {
                        "Sleep is already scheduled. Saving will replace all monthly occurrences."
                    } else null
                    RecurrenceType.NONE -> {
                        val defaultZone = ZoneId.systemDefault()
                        val oneTimeDay = taskDraft.deadline.atZone(defaultZone).toLocalDate()
                        val conflicts = existingSleepTasks.any { existing ->
                            when (existing.recurrenceRule.type) {
                                RecurrenceType.DAILY -> true
                                RecurrenceType.WEEKLY -> oneTimeDay.dayOfWeek in existing.recurrenceRule.daysOfWeek
                                RecurrenceType.MONTHLY -> oneTimeDay.dayOfMonth == existing.dueAt.atZone(defaultZone).dayOfMonth
                                RecurrenceType.NONE -> oneTimeDay == existing.dueAt.atZone(defaultZone).toLocalDate()
                            }
                        }
                        if (conflicts) {
                            "Sleep is already scheduled for this day. Saving will replace it."
                        } else null
                    }
                    else -> null
                }
                if (sleepOverlapWarning != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("⚠", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    sleepOverlapWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }

                // Repeat deadline controls
                item {
                    CreateFormCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Repeats forever",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Switch(
                                checked = taskDraft.repeatsForever,
                                onCheckedChange = { taskDraft = taskDraft.copy(repeatsForever = it) },
                            )
                        }
                        if (!taskDraft.repeatsForever) {
                            Spacer(Modifier.height(12.dp))
                            DateTimeSection(
                                title = "Repeat until",
                                dateTime = taskDraft.deadline,
                                onDateTimeChanged = { taskDraft = taskDraft.copy(deadline = it) },
                                context = context,
                            )
                        }
                    }
                }

                // Inline daily window configurator
                item {
                    Box(
                        modifier = if (showTutorial) Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot(); val sz = coords.size
                            windowSectionBounds = Rect(pos, Size(sz.width.toFloat(), sz.height.toFloat()))
                        } else Modifier,
                    ) {
                        CreateFormCard {
                            DailyWindowConfigurator(
                            hasWindow = taskDraft.hasWindow,
                            canDisable = false,
                            startTime = taskDraft.fixedStartAt.toLocalTime(),
                            endTime = taskDraft.fixedEndAt.toLocalTime(),
                            endsNextDay = taskDraft.fixedEndAt.toLocalTime() <= taskDraft.fixedStartAt.toLocalTime(),
                            minimumWindowMinutes = taskDraft.estimatedMinutes.coerceAtLeast(240),
                            onWindowEnabledChanged = {},
                            onWindowChanged = { start, end, overnight ->
                                val today = java.time.LocalDate.now()
                                val startDate = today
                                val endDate = if (overnight) today.plusDays(1) else today
                                val newFixedStart = java.time.LocalDateTime.of(startDate, start)
                                val newFixedEnd = java.time.LocalDateTime.of(endDate, end)
                                taskDraft = taskDraft.copy(
                                    hasWindow = true,
                                    fixedStartAt = newFixedStart,
                                    fixedEndAt = newFixedEnd,
                                )
                            },
                            context = context,
                        )
                    }
                    } // close Box
                }
            } else {
                item {
                    Box {
                        CreateFormCard {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (showTutorial && showTaskTutorial) Modifier.onGloballyPositioned { coords ->
                                            val pos = coords.positionInRoot(); val sz = coords.size
                                            moreOptionsBounds = Rect(pos, Size(sz.width.toFloat(), sz.height.toFloat()))
                                        } else Modifier
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showAdvancedOptions = !showAdvancedOptions },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "More options",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.graphicsLayer(
                                        rotationZ = if (showAdvancedOptions) 90f else 0f
                                    ),
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = showAdvancedOptions,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                SettingsSummaryRow(
                                    title = "Schedule",
                                    summary = scheduleRowSummary,
                                    onClick = {
                                        scheduleDraft = taskDraft
                                        showScheduleSheet = true
                                    },
                                )
                                if (taskDraft.schedulingMode == TaskSchedulingMode.FLEXIBLE || taskDraft.schedulingMode == TaskSchedulingMode.FIXED_DAY) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    SettingsSummaryRow(
                                        title = "Availability",
                                        summary = timingSummary,
                                        onClick = {
                                            windowDraft = taskDraft
                                            showWindowSheet = true
                                        },
                                    )
                                }
                                if (!followUpMode && !rescheduleMode) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    SettingsSummaryRow(
                                        title = "Repeat",
                                        summary = repeatSummary,
                                        onClick = {
                                            repeatDraft = taskDraft
                                            showRepeatSheet = true
                                        },
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                SettingsSummaryRow(
                                    title = "Rules",
                                    summary = rulesSummary,
                                    onClick = {
                                        rulesDraft = taskDraft.resolvedOverlapPolicy(allowConcurrentTasks)
                                        showRulesSheet = true
                                    },
                                )
                            }
                        }
                    }
                    } // close Box
                }
            }
        }

        Box(
            modifier = if (showTutorial) Modifier.onGloballyPositioned { coords ->
                val pos = coords.positionInRoot(); val sz = coords.size
                saveBarBounds = Rect(pos, Size(sz.width.toFloat(), sz.height.toFloat()))
            } else Modifier,
        ) {
            StickySaveBar(
                summary = saveSummary,
                actionLabel = when {
                    sleepMode -> "Save sleep"
                    editMode -> "Save Changes"
                    rescheduleMode -> "Save Reschedule"
                    else -> "Save Task"
                },
                enabled = canSaveTask && !isSaving,
                onClick = { onSaveTask(taskDraft) },
            )
        }
    }

    // Tutorial overlay
    if (showTutorial && tutorialStepEnum != null) {
        val adjustedBounds = currentTutorialBounds?.let {
            it.translate(-overlayOffset.x, -overlayOffset.y)
        }
        // For single-step MISSING_DAYS, highlight the days section
        val bounds = if (tutorialStepEnum == TutorialStep.MISSING_DAYS) daysSectionBounds?.let {
            it.translate(-overlayOffset.x, -overlayOffset.y)
        } else adjustedBounds
        SleepTutorialOverlay(
            step = tutorialStepEnum,
            sectionBounds = bounds,
            isLastStep = tutorialStep == tutorialSteps.lastIndex,
            onNext = { tutorialStep++ },
            onDismiss = { tutorialStep = tutorialSteps.size }, // dismiss by moving past last step
        )
    }
    } // close Box

    if (showScheduleSheet) {
        TaskEditorSheet(
            title = "Schedule",
            onDismiss = { showScheduleSheet = false },
            onDone = {
                taskDraft = taskDraft.applyScheduleEditor(scheduleDraft)
                showScheduleSheet = false
            },
        ) {
            CreateFormCard {
                TaskScheduleEditor(
                    draft = scheduleDraft,
                    onDraftChange = { scheduleDraft = it },
                    noDeadlineEligible = scheduleDraft.schedulingMode == TaskSchedulingMode.FLEXIBLE,
                    context = context,
                )
            }
        }
    }

    if (showWindowSheet && (taskDraft.schedulingMode == TaskSchedulingMode.FLEXIBLE || taskDraft.schedulingMode == TaskSchedulingMode.FIXED_DAY)) {
        TaskEditorSheet(
            title = "Availability",
            onDismiss = { showWindowSheet = false },
            onDone = {
                taskDraft = taskDraft.applyWindowEditor(windowDraft)
                showWindowSheet = false
            },
        ) {
            CreateFormCard {
                TaskWindowEditor(
                    draft = windowDraft,
                    onDraftChange = { windowDraft = it },
                    availableTimeframes = availableTimeframes,
                    context = context,
                )
            }
        }
    }

    if (showRepeatSheet && !followUpMode && !rescheduleMode) {
        TaskEditorSheet(
            title = "Repeat",
            onDismiss = { showRepeatSheet = false },
            onDone = {
                taskDraft = taskDraft.applyRepeatEditor(repeatDraft)
                showRepeatSheet = false
            },
        ) {
            CreateFormCard {
                TaskRepeatEditor(
                    draft = repeatDraft,
                    onDraftChange = { repeatDraft = it },
                )
            }
        }
    }

    if (showRulesSheet) {
        TaskEditorSheet(
            title = "Rules",
            onDismiss = { showRulesSheet = false },
            onDone = {
                taskDraft = taskDraft.applyRulesEditor(rulesDraft)
                showRulesSheet = false
            },
        ) {
            CreateFormCard {
                TaskRulesEditor(
                    draft = rulesDraft,
                    onDraftChange = { rulesDraft = it },
                    continuationTasks = continuationTasks,
                    showReminderToggle = !followUpMode && !rescheduleMode,
                    globalAllowConcurrentTasks = allowConcurrentTasks,
                )
            }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SchedulingModeSection(
    schedulingMode: TaskSchedulingMode,
    onModeChanged: (TaskSchedulingMode) -> Unit,
) {
    val options = listOf(
        TaskSchedulingMode.FLEXIBLE to "Flexible",
        TaskSchedulingMode.FIXED_DAY to "Fixed date",
        TaskSchedulingMode.FIXED_EXACT to "Fixed time",
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = schedulingMode.labelForCreate(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Schedule") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (mode, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onModeChanged(mode)
                    },
                )
            }
        }
    }
}

@Composable
fun DurationWheelPicker(
    minutes: Int,
    minMinutes: Int = 15,
    maxMinutes: Int = (23 * 60) + 45,
    onMinutesChanged: (Int) -> Unit,
) {
    val wheelState = remember(minutes, minMinutes, maxMinutes) {
        durationWheelState(
            minutes = minutes,
            minMinutes = minMinutes,
            maxMinutes = maxMinutes,
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DurationWheelColumn(
                modifier = Modifier.weight(1f),
                label = "H",
                options = wheelState.hourOptions,
                selectedValue = wheelState.selectedHours,
                valueText = { value -> value.toString() },
                onValueSelected = { selectedHours ->
                    onMinutesChanged(
                        durationFromWheelSelection(
                            selectedHours = selectedHours,
                            selectedMinute = wheelState.selectedMinute,
                            minMinutes = minMinutes,
                            maxMinutes = maxMinutes,
                        ),
                    )
                },
            )
            Text(
                "Duration",
                modifier = Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            DurationWheelColumn(
                modifier = Modifier.weight(1f),
                label = "M",
                options = wheelState.minuteOptions,
                selectedValue = wheelState.selectedMinute,
                valueText = { value -> value.toString().padStart(2, '0') },
                onValueSelected = { selectedMinute ->
                    onMinutesChanged(
                        durationFromWheelSelection(
                            selectedHours = wheelState.selectedHours,
                            selectedMinute = selectedMinute,
                            minMinutes = minMinutes,
                            maxMinutes = maxMinutes,
                        ),
                    )
                }
            )
        }
    }
}

@Composable
fun SettingsSummaryRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    summary,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    title: String,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(sheetScrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                content()
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text("Done")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun TaskScheduleEditor(
    draft: TaskDraft,
    onDraftChange: (TaskDraft) -> Unit,
    noDeadlineEligible: Boolean,
    context: android.content.Context,
) {
    SchedulingModeSection(
        schedulingMode = draft.schedulingMode,
        onModeChanged = { newMode ->
            onDraftChange(
                draft.copy(
                    schedulingMode = newMode,
                    hasDeadline = if (newMode == TaskSchedulingMode.FLEXIBLE) draft.hasDeadline else true,
                    fixedDate = draft.deadline.toLocalDate(),
                    fixedStartAt = when (newMode) {
                        TaskSchedulingMode.FIXED_EXACT -> draft.deadline.minusMinutes(draft.estimatedMinutes.toLong()).withSecond(0).withNano(0)
                        else -> draft.fixedStartAt.withSecond(0).withNano(0)
                    },
                    fixedEndAt = when (newMode) {
                        TaskSchedulingMode.FIXED_EXACT -> draft.deadline.withSecond(0).withNano(0)
                        else -> draft.fixedEndAt.withSecond(0).withNano(0)
                    },
                ),
            )
        },
    )
    when (draft.schedulingMode) {
        TaskSchedulingMode.FLEXIBLE -> {
            if (draft.hasDeadline) {
                DateTimeSection(
                    title = "Deadline",
                    dateTime = draft.deadline,
                    onDateTimeChanged = { selected -> onDraftChange(draft.copy(deadline = selected)) },
                    context = context,
                )
            }
            if (noDeadlineEligible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TaskSectionTitle("No deadline")
                    Switch(
                        checked = !draft.hasDeadline,
                        onCheckedChange = { enabled ->
                            onDraftChange(
                                draft.copy(
                                    hasDeadline = !enabled,
                                    repeatsForever = if (enabled && draft.recurrenceType != RecurrenceType.NONE) {
                                        true
                                    } else if (!enabled && draft.recurrenceType != RecurrenceType.NONE) {
                                        false
                                    } else {
                                        draft.repeatsForever
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
        TaskSchedulingMode.FIXED_DAY -> FixedDaySection(
            date = draft.fixedDate,
            onDateChanged = {
                onDraftChange(
                    draft.copy(
                        fixedDate = it,
                        deadline = draft.deadline.withYear(it.year).withMonth(it.monthValue).withDayOfMonth(it.dayOfMonth),
                        fixedStartAt = draft.fixedStartAt.withYear(it.year).withMonth(it.monthValue).withDayOfMonth(it.dayOfMonth),
                        fixedEndAt = draft.fixedEndAt.withYear(it.year).withMonth(it.monthValue).withDayOfMonth(it.dayOfMonth),
                    ),
                )
            },
            context = context,
        )
        TaskSchedulingMode.FIXED_EXACT -> FixedTimeSection(
            recurrenceType = draft.recurrenceType,
            dateTime = draft.fixedStartAt,
            onDateTimeChanged = {
                val adjustedEnd = it.plusMinutes(draft.estimatedMinutes.toLong())
                onDraftChange(
                    draft.copy(
                        fixedStartAt = it,
                        fixedEndAt = adjustedEnd,
                        deadline = adjustedEnd,
                    ),
                )
            },
            onTimeChanged = { selectedTime ->
                val adjustedStart = draft.fixedStartAt
                    .withHour(selectedTime.hour)
                    .withMinute(selectedTime.minute)
                    .withSecond(0)
                    .withNano(0)
                val adjustedEnd = adjustedStart.plusMinutes(draft.estimatedMinutes.toLong())
                onDraftChange(
                    draft.copy(
                        fixedStartAt = adjustedStart,
                        fixedEndAt = adjustedEnd,
                        deadline = adjustedEnd,
                    ),
                )
            },
            context = context,
        )
        TaskSchedulingMode.FLEXIBLE_WINDOW -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskWindowEditor(
    draft: TaskDraft,
    onDraftChange: (TaskDraft) -> Unit,
    availableTimeframes: List<Timeframe>,
    context: android.content.Context,
) {
    DailyWindowConfigurator(
        hasWindow = draft.hasWindow,
        canDisable = true,
        startTime = draft.fixedStartAt.toLocalTime(),
        endTime = draft.fixedEndAt.toLocalTime(),
        endsNextDay = draft.fixedEndAt.toLocalDate().isAfter(draft.fixedStartAt.toLocalDate()),
        minimumWindowMinutes = draft.estimatedMinutes,
        onWindowEnabledChanged = { enabled ->
            onDraftChange(
                if (enabled) {
                    val anchorDate = when (draft.schedulingMode) {
                        TaskSchedulingMode.FIXED_DAY -> draft.fixedDate
                        else -> draft.deadline.toLocalDate()
                    }
                    draft.copy(
                        hasWindow = true,
                        fixedStartAt = LocalDateTime.of(anchorDate, LocalTime.of(18, 0)),
                        fixedEndAt = LocalDateTime.of(anchorDate, LocalTime.of(21, 0)),
                    )
                } else {
                    draft.copy(hasWindow = false)
                },
            )
        },
        onWindowChanged = { startTime, endTime, endsNextDay ->
            onDraftChange(
                draft.withWindowTimes(
                    startTime = startTime,
                    endTime = endTime,
                    endsNextDay = endsNextDay,
                ),
            )
        },
        context = context,
    ) {
        TaskSectionTitle("Timeframe")
        TimeframeDropdown(
            timeframes = availableTimeframes,
            selectedTimeframeId = draft.timeframeId,
            onSelected = { timeframeId -> onDraftChange(draft.copy(timeframeId = timeframeId)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyWindowConfigurator(
    hasWindow: Boolean,
    canDisable: Boolean,
    startTime: LocalTime,
    endTime: LocalTime,
    endsNextDay: Boolean,
    minimumWindowMinutes: Int = 15,
    onWindowEnabledChanged: (Boolean) -> Unit,
    onWindowChanged: (LocalTime, LocalTime, Boolean) -> Unit,
    context: android.content.Context,
    footerContent: @Composable () -> Unit = {},
) {
    if (canDisable) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskSectionTitle("Use daily window")
            Switch(
                checked = hasWindow,
                onCheckedChange = onWindowEnabledChanged,
            )
        }
    } else {
        TaskSectionTitle("Daily window")
    }

    if (hasWindow) {
        val overnight = endsNextDay
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskSectionTitle("Overnight")
            Switch(
                checked = overnight,
                onCheckedChange = { enabled ->
                    val (newStart, newEnd) = adjustedWindowForOvernightToggle(
                        startTime = startTime,
                        endTime = endTime,
                        enableOvernight = enabled,
                        minimumWindowMinutes = minimumWindowMinutes,
                    )
                    onWindowChanged(newStart, newEnd, enabled)
                },
            )
        }

        DailyWindowRangeSlider(
            startTime = startTime,
            endTime = endTime,
            overnight = overnight,
            minimumWindowMinutes = minimumWindowMinutes,
            onWindowChanged = onWindowChanged,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WindowTimeField(
                title = "Start time",
                time = startTime,
                modifier = Modifier.weight(1f),
                onTimeChanged = { selectedTime ->
                    val adjustedEnd = if (!overnight && selectedTime >= endTime) {
                        selectedTime.plusMinutes(minimumWindowMinutes.toLong())
                    } else {
                        endTime
                    }
                    if (windowSupportsDuration(selectedTime, adjustedEnd, overnight, minimumWindowMinutes)) {
                        onWindowChanged(selectedTime, adjustedEnd, overnight)
                    }
                },
                context = context,
            )
            WindowTimeField(
                title = "End time",
                time = endTime,
                modifier = Modifier.weight(1f),
                onTimeChanged = { selectedTime ->
                    if (windowSupportsDuration(startTime, selectedTime, overnight, minimumWindowMinutes)) {
                        onWindowChanged(startTime, selectedTime, overnight)
                    }
                },
                context = context,
            )
        }
    }
    footerContent()
}

@Composable
private fun DailyWindowRangeSlider(
    startTime: LocalTime,
    endTime: LocalTime,
    overnight: Boolean,
    minimumWindowMinutes: Int,
    onWindowChanged: (LocalTime, LocalTime, Boolean) -> Unit,
) {
    val sliderRange = windowSliderRange(startTime, endTime, overnight)
    val inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val trackStrokeWidth = with(LocalDensity.current) { 6.dp.toPx() }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    ) {
                        val y = size.height / 2f
                        val sliderMax = WindowSliderMaxMinutes.toFloat()
                        val startX = (sliderRange.start / sliderMax) * size.width
                        val endX = (sliderRange.endInclusive / sliderMax) * size.width
                        drawLine(
                            color = inactiveTrackColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = trackStrokeWidth,
                            cap = StrokeCap.Round,
                        )
                        if (overnight) {
                            drawLine(
                                color = activeTrackColor,
                                start = Offset(0f, y),
                                end = Offset(startX, y),
                                strokeWidth = trackStrokeWidth,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                color = activeTrackColor,
                                start = Offset(endX, y),
                                end = Offset(size.width, y),
                                strokeWidth = trackStrokeWidth,
                                cap = StrokeCap.Round,
                            )
                        } else {
                            drawLine(
                                color = activeTrackColor,
                                start = Offset(startX, y),
                                end = Offset(endX, y),
                                strokeWidth = trackStrokeWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                    RangeSlider(
                        value = sliderRange,
                        onValueChange = { rawRange ->
                            val coercedRange = coerceWindowSliderRange(
                                rawRange = rawRange,
                                previousRange = sliderRange,
                                overnight = overnight,
                                minimumWindowMinutes = minimumWindowMinutes,
                            )
                            val (newStart, newEnd) = windowTimesFromSliderRange(coercedRange, overnight)
                            onWindowChanged(newStart, newEnd, overnight)
                        },
                        valueRange = 0f..WindowSliderMaxMinutes.toFloat(),
                        steps = WindowSliderStepCount,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                        ),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("12a", "6a", "12p", "6p", "12a").forEach { label ->
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeOfDaySliderRow(
    title: String,
    time: LocalTime,
    onTimeChanged: (LocalTime) -> Unit,
) {
    val minutes = minutesFromStart(time)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(time.formatHourLabel(), style = MaterialTheme.typography.bodyMedium)
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { raw ->
                        val snapped = snapToStep(raw.toInt(), 15).coerceIn(0, 24 * 60 - 15)
                        onTimeChanged(minutesToLocalTime(snapped))
                    },
                    valueRange = 0f..((24 * 60) - 15).toFloat(),
                    steps = ((24 * 60) / 15 - 2).coerceAtLeast(0),
                )
                val labels = listOf("12a", "6a", "12p", "6p", "12a")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEach { label ->
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun adjustedWindowForOvernightToggle(
    startTime: LocalTime,
    endTime: LocalTime,
    enableOvernight: Boolean,
    minimumWindowMinutes: Int,
): Pair<LocalTime, LocalTime> {
    val physicalRange = listOf(minutesFromStart(startTime), minutesFromStart(endTime))
        .sorted()
        .let { it[0].toFloat()..it[1].toFloat() }
    val adjustedRange = coerceWindowSliderRange(
        rawRange = physicalRange,
        previousRange = physicalRange,
        overnight = enableOvernight,
        minimumWindowMinutes = minimumWindowMinutes,
    )
    return windowTimesFromSliderRange(adjustedRange, enableOvernight)
}

@Composable
fun TaskRepeatEditor(
    draft: TaskDraft,
    onDraftChange: (TaskDraft) -> Unit,
) {
    RecurrenceSection(
        recurrenceType = draft.recurrenceType,
        recurrenceDays = draft.recurrenceDays,
        recurrenceInterval = draft.recurrenceInterval,
        onTypeChanged = {
            onDraftChange(
                draft.copy(
                    recurrenceType = it,
                    recurrenceDays = if (it == RecurrenceType.WEEKLY) draft.recurrenceDays else emptySet(),
                    repeatsForever = if (it == RecurrenceType.NONE) {
                        draft.repeatsForever
                    } else if (draft.hasDeadline) {
                        false
                    } else {
                        draft.repeatsForever
                    },
                ),
            )
        },
        onIntervalChanged = { onDraftChange(draft.copy(recurrenceInterval = it)) },
        disabledTypes = if (draft.schedulingMode == TaskSchedulingMode.FIXED_DAY) {
            setOf(RecurrenceType.DAILY, RecurrenceType.WEEKLY)
        } else {
            emptySet()
        },
        onDayToggle = { onDraftChange(draft.copy(recurrenceDays = draft.recurrenceDays.toggleForCreate(it))) },
        showTitle = false,
    )
    if (draft.recurrenceType != RecurrenceType.NONE) {
        if (!draft.hasDeadline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskSectionTitle("Repeats forever")
                Switch(
                    checked = draft.repeatsForever,
                    onCheckedChange = { onDraftChange(draft.copy(repeatsForever = it)) },
                )
            }
        }
        if (!draft.hasDeadline && !draft.repeatsForever) {
            DateTimeSection(
                title = "Repeat until",
                dateTime = draft.deadline,
                onDateTimeChanged = { onDraftChange(draft.copy(deadline = it)) },
                context = LocalContext.current,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskRulesEditor(
    draft: TaskDraft,
    onDraftChange: (TaskDraft) -> Unit,
    continuationTasks: List<ScheduleTask>,
    showReminderToggle: Boolean,
    globalAllowConcurrentTasks: Boolean = true,
) {
    // Dependency section — date-first picker with continuation mode and no-gap toggle
    if (continuationTasks.isNotEmpty()) {
        ContinuationSection(
            tasks = continuationTasks,
            selectedParentTaskId = draft.continuationParentTaskId,
            selectedMode = draft.continuationMode,
            onParentSelected = { taskId ->
                onDraftChange(draft.copy(
                    continuationParentTaskId = taskId,
                    continuationMode = if (taskId != null) {
                        draft.continuationMode ?: TaskContinuationMode.AFTER_PARENT_SCHEDULED_END
                    } else {
                        null
                    },
                    noGap = if (taskId == null) false else draft.noGap,
                ))
            },
            onModeSelected = { mode ->
                onDraftChange(draft.copy(continuationMode = mode))
            },
        )
        if (draft.continuationParentTaskId != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "No gap",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = draft.noGap,
                    onCheckedChange = { onDraftChange(draft.copy(noGap = it)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    TaskSectionTitle("Priority")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(TaskPriority.MEDIUM to "Normal", TaskPriority.URGENT to "Urgent").forEach { (priority, label) ->
            FilterChip(
                selected = draft.priority == priority,
                onClick = { onDraftChange(draft.copy(priority = priority)) },
                label = { Text(label) },
            )
        }
    }
    TaskSectionTitle("Overlap")
    if (!globalAllowConcurrentTasks) {
        Text(
            "Concurrent tasks are disabled globally. Enable in Settings to allow overlap.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            TaskOverlapPolicy.ALLOW to "Allow",
            TaskOverlapPolicy.DISALLOW to "No overlap",
        ).forEach { (policy, label) ->
            FilterChip(
                selected = draft.overlapPolicy == policy,
                onClick = { onDraftChange(draft.copy(overlapPolicy = policy)) },
                label = { Text(label) },
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Allow splitting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = draft.allowSplitting,
            onCheckedChange = { onDraftChange(draft.copy(allowSplitting = it)) },
        )
    }
    Spacer(Modifier.height(8.dp))

    if (showReminderToggle) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskSectionTitle("Add as reminder")
            Switch(
                checked = draft.addReminder,
                onCheckedChange = { onDraftChange(draft.copy(addReminder = it)) },
            )
        }
    }
}

@Composable
fun DurationWheelColumn(
    modifier: Modifier = Modifier,
    label: String,
    options: List<Int>,
    selectedValue: Int,
    valueText: (Int) -> String,
    onValueSelected: (Int) -> Unit,
) {
    val wheelBackgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val wheelTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val wheelHintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f).toArgb()
    val wheelDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f).toArgb()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                factory = { context ->
                    NumberPicker(context).apply {
                        minValue = 0
                        maxValue = options.lastIndex
                        displayedValues = options.map(valueText).toTypedArray()
                        wrapSelectorWheel = false
                        descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                        value = options.indexOf(selectedValue).coerceAtLeast(0)
                        styleDurationPicker(
                            backgroundColor = wheelBackgroundColor,
                            textColor = wheelTextColor,
                            hintColor = wheelHintColor,
                            dividerColor = wheelDividerColor,
                        )
                        setOnValueChangedListener { _, _, newVal ->
                            onValueSelected(options[newVal])
                        }
                    }
                },
                update = { picker ->
                    val displayValues = options.map(valueText).toTypedArray()
                    val targetIndex = options.indexOf(selectedValue).coerceAtLeast(0)
                    if (picker.maxValue != options.lastIndex) {
                        picker.displayedValues = null
                        picker.minValue = 0
                        picker.maxValue = options.lastIndex
                        picker.displayedValues = displayValues
                    } else {
                        picker.displayedValues = displayValues
                    }
                    picker.styleDurationPicker(
                        backgroundColor = wheelBackgroundColor,
                        textColor = wheelTextColor,
                        hintColor = wheelHintColor,
                        dividerColor = wheelDividerColor,
                    )
                    if (picker.value != targetIndex) {
                        picker.value = targetIndex
                    }
                    picker.setOnValueChangedListener { _, _, newVal ->
                        onValueSelected(options[newVal])
                    }
                },
            )
        }
    }
}

@Composable
fun FixedDaySection(
    date: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
    context: android.content.Context,
) {
    val dateLabel = remember(date) { DateTimeFormatter.ofPattern("EEE, MMM d").format(date) }
    TaskSectionTitle("Date")
    DateTimePickerCard(
        label = "Date",
        value = dateLabel,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onDateChanged(LocalDate.of(year, month + 1, dayOfMonth))
                },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            ).show()
        },
    )
}

@Composable
fun FixedTimeSection(
    recurrenceType: RecurrenceType,
    dateTime: LocalDateTime,
    onDateTimeChanged: (LocalDateTime) -> Unit,
    onTimeChanged: (LocalTime) -> Unit,
    context: android.content.Context,
) {
    if (recurrenceType == RecurrenceType.NONE) {
        DateTimeSection(
            title = "Time",
            dateTime = dateTime,
            onDateTimeChanged = onDateTimeChanged,
            context = context,
        )
    } else {
        TimeOnlySection(
            title = "Time",
            time = dateTime.toLocalTime(),
            onTimeChanged = onTimeChanged,
            context = context,
        )
    }
}

@Composable
fun StickySaveBar(
    summary: String,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                summary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(999.dp),
                enabled = enabled,
                onClick = onClick,
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun CreateReminderScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
    onSaveReminder: (ReminderDraft) -> Unit,
) {
    var reminderDraft by rememberSaveable(
        stateSaver = listSaver(
            save = {
                listOf(
                    it.title,
                    it.description,
                    it.dueAt.toString(),
                    it.isAllDay,
                    it.recurrenceType.name,
                    it.recurrenceInterval,
                    it.recurrenceDays.joinToString(",") { day -> day.name },
                )
            },
            restore = { saved ->
                ReminderDraft(
                    title = saved[0] as String,
                    description = saved[1] as String,
                    dueAt = LocalDateTime.parse(saved[2] as String),
                    isAllDay = saved[3] as Boolean,
                    recurrenceType = RecurrenceType.valueOf(saved[4] as String),
                    recurrenceInterval = saved[5] as Int,
                    recurrenceDays = (saved[6] as String)
                        .takeIf { it.isNotBlank() }
                        ?.split(",")
                        ?.map { DayOfWeek.valueOf(it) }
                        ?.toSet()
                        ?: emptySet(),
                )
            },
        ),
    ) { mutableStateOf(ReminderDraft()) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Create Reminder",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            item {
                CreateFormCard {
                    OutlinedTextField(
                        value = reminderDraft.title,
                        onValueChange = { reminderDraft = reminderDraft.copy(title = it) },
                        label = { Text("Reminder title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = reminderDraft.description,
                        onValueChange = { reminderDraft = reminderDraft.copy(description = it) },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            }
            item {
                CreateFormCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TaskSectionTitle("Whole day")
                        Switch(
                            checked = reminderDraft.isAllDay,
                            onCheckedChange = { enabled ->
                                reminderDraft = reminderDraft.copy(
                                    isAllDay = enabled,
                                    dueAt = if (enabled) {
                                        reminderDraft.dueAt.withHour(9).withMinute(0).withSecond(0).withNano(0)
                                    } else {
                                        reminderDraft.dueAt.withSecond(0).withNano(0)
                                    },
                                )
                            },
                        )
                    }
                    DateTimeSection(
                        title = if (reminderDraft.recurrenceType == RecurrenceType.NONE) "Remind me" else "First reminder time",
                        dateTime = reminderDraft.dueAt,
                        onDateTimeChanged = { reminderDraft = reminderDraft.copy(dueAt = it) },
                        context = context,
                        dateOnly = reminderDraft.isAllDay,
                    )
                    RecurrenceSection(
                        recurrenceType = reminderDraft.recurrenceType,
                        recurrenceDays = reminderDraft.recurrenceDays,
                        recurrenceInterval = reminderDraft.recurrenceInterval,
                        onTypeChanged = {
                            reminderDraft = reminderDraft.copy(
                                recurrenceType = it,
                                recurrenceDays = if (it == RecurrenceType.WEEKLY) reminderDraft.recurrenceDays else emptySet(),
                            )
                        },
                        onIntervalChanged = { reminderDraft = reminderDraft.copy(recurrenceInterval = it) },
                        onDayToggle = { reminderDraft = reminderDraft.copy(recurrenceDays = reminderDraft.recurrenceDays.toggleForCreate(it)) },
                    )
                }
            }
        }

        StickySaveBar(
            summary = reminderSummary(reminderDraft),
            actionLabel = "Save Reminder",
            enabled = reminderDraft.title.isNotBlank() &&
                (reminderDraft.recurrenceType != RecurrenceType.WEEKLY || reminderDraft.recurrenceDays.isNotEmpty()),
            onClick = { onSaveReminder(reminderDraft) },
        )
    }
}

@Composable
fun FlexibleWindowSection(
    recurrenceType: RecurrenceType,
    startDate: LocalDate,
    windowStart: LocalDateTime,
    windowEnd: LocalDateTime,
    onStartDateChanged: (LocalDate) -> Unit,
    onWindowStartChanged: (LocalDateTime) -> Unit,
    onWindowEndChanged: (LocalDateTime) -> Unit,
    context: android.content.Context,
) {
    if (recurrenceType == RecurrenceType.NONE) {
        DateTimeSection(
            title = "Window start",
            dateTime = windowStart,
            onDateTimeChanged = onWindowStartChanged,
            context = context,
        )
        DateTimeSection(
            title = "Window end",
            dateTime = windowEnd,
            onDateTimeChanged = onWindowEndChanged,
            context = context,
        )
    } else {
        FixedDaySection(
            date = startDate,
            onDateChanged = onStartDateChanged,
            context = context,
        )
        TimeOnlySection(
            title = "Window start",
            time = windowStart.toLocalTime(),
            onTimeChanged = { selectedTime ->
                onWindowStartChanged(
                    windowStart
                        .withHour(selectedTime.hour)
                        .withMinute(selectedTime.minute)
                        .withSecond(0)
                        .withNano(0),
                )
            },
            context = context,
        )
        TimeOnlySection(
            title = "Window end",
            time = windowEnd.toLocalTime(),
            onTimeChanged = { selectedTime ->
                onWindowEndChanged(
                    windowEnd
                        .withHour(selectedTime.hour)
                        .withMinute(selectedTime.minute)
                        .withSecond(0)
                        .withNano(0),
                )
            },
            context = context,
        )
    }
}

@Composable
fun AdvancedTimingRow(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("More options", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = if (expanded) "Collapse options" else "Expand options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer(rotationZ = if (expanded) 90f else 0f),
            )
        }
    }
}

@Composable
fun StartDateSection(
    date: LocalDate?,
    onDateChanged: (LocalDate?) -> Unit,
    onClear: () -> Unit,
    context: android.content.Context,
) {
    val dateLabel = remember(date) { date?.let { DateTimeFormatter.ofPattern("EEE, MMM d").format(it) } ?: "Not set" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DateTimePickerCard(
            label = "Start date",
            value = dateLabel,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val initial = date ?: LocalDate.now()
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateChanged(LocalDate.of(year, month + 1, dayOfMonth))
                    },
                    initial.year,
                    initial.monthValue - 1,
                    initial.dayOfMonth,
                ).show()
            },
        )
        if (date != null) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Clear start date")
            }
        }
    }
}

@Composable
fun DeadlineSummaryCard(taskDraft: TaskDraft) {
    val value = remember(taskDraft.hasDeadline, taskDraft.deadline, taskDraft.fixedDate, taskDraft.fixedStartAt, taskDraft.fixedEndAt, taskDraft.schedulingMode) {
        if (!taskDraft.hasDeadline && taskDraft.schedulingMode == TaskSchedulingMode.FLEXIBLE) {
            return@remember "No deadline"
        }
        when (taskDraft.schedulingMode) {
            TaskSchedulingMode.FLEXIBLE -> DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a").format(taskDraft.deadline)
            TaskSchedulingMode.FLEXIBLE_WINDOW -> {
                val dayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
                val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
                "${dayFormatter.format(taskDraft.fixedStartAt)} • ${timeFormatter.format(taskDraft.fixedStartAt)}-${timeFormatter.format(taskDraft.fixedEndAt)}"
            }
            TaskSchedulingMode.FIXED_DAY -> DateTimeFormatter.ofPattern("EEE, MMM d").format(taskDraft.fixedDate)
            TaskSchedulingMode.FIXED_EXACT -> DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a").format(taskDraft.fixedEndAt)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun DateTimeSection(
    title: String,
    dateTime: LocalDateTime,
    onDateTimeChanged: (LocalDateTime) -> Unit,
    context: android.content.Context,
    dateOnly: Boolean = false,
) {
    TaskSectionTitle(title)
    DateTimePickerRows(
        dateTime = dateTime,
        onDateTimeChanged = onDateTimeChanged,
        context = context,
        dateOnly = dateOnly,
    )
}

@Composable
fun DurationSlider(
    minutes: Int,
    minMinutes: Int = 15,
    maxMinutes: Int = 360,
    onMinutesChanged: (Int) -> Unit,
) {
    val stepMinutes = 15
    val effectiveMax = maxMinutes.coerceAtLeast(minMinutes)
    val snapped = snapToStepForCreate(minutes.coerceIn(minMinutes, effectiveMax), stepMinutes).coerceIn(minMinutes, effectiveMax)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Duration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(snapped.durationLabelForCreate(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = snapped.toFloat(),
                onValueChange = { raw ->
                    onMinutesChanged(snapToStepForCreate(raw.toInt(), stepMinutes).coerceIn(minMinutes, effectiveMax))
                },
                valueRange = minMinutes.toFloat()..effectiveMax.toFloat(),
                steps = ((effectiveMax - minMinutes) / stepMinutes - 1).coerceAtLeast(0),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(minMinutes.durationLabelForCreate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(effectiveMax.durationLabelForCreate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DateTimePickerRows(
    dateTime: LocalDateTime,
    onDateTimeChanged: (LocalDateTime) -> Unit,
    context: android.content.Context,
    dateOnly: Boolean = false,
) {
    val dateLabel = remember(dateTime) { DateTimeFormatter.ofPattern("EEE, MMM d").format(dateTime) }
    val timeLabel = remember(dateTime) { DateTimeFormatter.ofPattern("h:mm a").format(dateTime) }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        DateTimePickerCard(
            label = "Date",
            value = dateLabel,
            modifier = Modifier.weight(1f),
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateTimeChanged(dateTime.withYear(year).withMonth(month + 1).withDayOfMonth(dayOfMonth))
                    },
                    dateTime.year,
                    dateTime.monthValue - 1,
                    dateTime.dayOfMonth,
                ).show()
            },
        )
        if (!dateOnly) {
            DateTimePickerCard(
                label = "Time",
                value = timeLabel,
                modifier = Modifier.weight(1f),
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            onDateTimeChanged(
                                dateTime
                                    .withHour(hour)
                                    .withMinute(minute)
                                    .withSecond(0)
                                    .withNano(0),
                            )
                        },
                        dateTime.hour,
                        dateTime.minute,
                        false,
                    ).show()
                },
            )
        }
    }
}

@Composable
fun DateTimePickerCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(if (label.isBlank()) 0.dp else 4.dp),
        ) {
            if (label.isNotBlank()) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TimeframeDropdown(
    timeframes: List<Timeframe>,
    selectedTimeframeId: String?,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTimeframe = timeframes.firstOrNull { it.id == selectedTimeframeId }
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }
    val selectedLabel = selectedTimeframe?.let {
        "${it.name} · ${formatter.format(it.startDate)} - ${formatter.format(it.endDate)}"
    } ?: "None"

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("v", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        "None",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selectedTimeframeId == null) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            timeframes.forEach { timeframe ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                timeframe.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (selectedTimeframeId == timeframe.id) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                "${formatter.format(timeframe.startDate)} - ${formatter.format(timeframe.endDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelected(timeframe.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContinuationSection(
    tasks: List<ScheduleTask>,
    selectedParentTaskId: String?,
    selectedMode: TaskContinuationMode?,
    onParentSelected: (String?) -> Unit,
    onModeSelected: (TaskContinuationMode) -> Unit,
) {
    TaskSectionTitle("Dependency")
    val zoneId = remember { ZoneId.systemDefault() }
    val availableDates = remember(tasks) { dependencyDateOptions(tasks, zoneId) }
    val selectedTask = tasks.firstOrNull { it.id == selectedParentTaskId }
    var selectedDate by remember(tasks, selectedParentTaskId) {
        mutableStateOf(initialDependencyDate(tasks, selectedParentTaskId, zoneId))
    }
    val filteredTasks = remember(tasks, selectedDate) {
        tasksForDependencyDate(tasks, selectedDate, zoneId)
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    val context = LocalContext.current

    if (selectedDate != null && availableDates.none { it == selectedDate }) {
        selectedDate = availableDates.firstOrNull()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateTimePickerCard(
                label = "Day",
                value = selectedDate?.let(dateFormatter::format) ?: "Pick day",
                modifier = Modifier.weight(1f),
                onClick = {
                    val initialDate = selectedDate ?: availableDates.firstOrNull() ?: LocalDate.now()
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val picked = LocalDate.of(year, month + 1, dayOfMonth)
                            selectedDate = picked
                            if (selectedTask?.dependencyStartDate(zoneId) != picked) {
                                onParentSelected(null)
                            }
                        },
                        initialDate.year,
                        initialDate.monthValue - 1,
                        initialDate.dayOfMonth,
                    ).show()
                },
            )
            DependencyDropdown(
                label = "Task",
                value = selectedTask?.title ?: "None",
                options = filteredTasks.map { task -> task.id to task.title },
                enabled = selectedDate != null,
                modifier = Modifier.weight(1f),
                onClear = { onParentSelected(null) },
                onSelected = { taskId -> onParentSelected(taskId as String?) },
            )
        }
    }
    if (selectedParentTaskId != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TaskContinuationMode.AFTER_PARENT_SCHEDULED_END to TaskContinuationMode.AFTER_PARENT_SCHEDULED_END.labelForDependency(),
                TaskContinuationMode.AFTER_PARENT_DUE_AT to TaskContinuationMode.AFTER_PARENT_DUE_AT.labelForDependency(),
                TaskContinuationMode.BEFORE_PARENT_START to TaskContinuationMode.BEFORE_PARENT_START.labelForDependency(),
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun <T> DependencyDropdown(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
    onSelected: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clickable(enabled = enabled) { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("v", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp)),
        ) {
            DropdownMenuItem(
                text = { Text("None", style = MaterialTheme.typography.titleMedium) },
                onClick = {
                    onClear()
                    expanded = false
                },
            )
            options.forEach { (option, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

fun dependencyDateOptions(
    tasks: List<ScheduleTask>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LocalDate> = tasks
    .map { it.dependencyStartDate(zoneId) }
    .distinct()
    .sorted()

fun initialDependencyDate(
    tasks: List<ScheduleTask>,
    selectedParentTaskId: String?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate? = tasks.firstOrNull { it.id == selectedParentTaskId }?.dependencyStartDate(zoneId)
    ?: dependencyDateOptions(tasks, zoneId).firstOrNull()

fun tasksForDependencyDate(
    tasks: List<ScheduleTask>,
    date: LocalDate?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduleTask> = if (date == null) {
    emptyList()
} else {
    tasks
        .filter { it.dependencyStartDate(zoneId) == date }
        .sortedBy { it.dependencyStartInstant(zoneId) }
}

private fun ScheduleTask.dependencyStartDate(
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDate = dependencyStartInstant(zoneId).atZone(zoneId).toLocalDate()

private fun ScheduleTask.dependencyStartInstant(
    zoneId: ZoneId = ZoneId.systemDefault(),
): Instant = when (schedulingMode) {
    TaskSchedulingMode.FIXED_EXACT -> fixedStartAt ?: dueAt
    TaskSchedulingMode.FIXED_DAY -> fixedStartAt ?: dueAt.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
    TaskSchedulingMode.FLEXIBLE,
    TaskSchedulingMode.FLEXIBLE_WINDOW -> fixedStartAt ?: dueAt
}

private fun TaskDraft.timeframeCutoffDate(): LocalDate? = when {
    !hasDeadline -> null
    schedulingMode == TaskSchedulingMode.FIXED_DAY -> fixedDate
    schedulingMode == TaskSchedulingMode.FIXED_EXACT -> fixedStartAt.toLocalDate()
    hasWindow || schedulingMode == TaskSchedulingMode.FLEXIBLE_WINDOW -> fixedEndAt.toLocalDate()
    else -> deadline.toLocalDate()
}

fun defaultCreateTaskDraft(defaultTaskReminder: Boolean = false): TaskDraft =
    TaskDraft(
        hasDeadline = false,
        addReminder = defaultTaskReminder,
    )

fun defaultCreateTaskDraft(
    defaultTaskReminder: Boolean = false,
    allowConcurrentTasks: Boolean,
    defaultTaskSplitting: Boolean = true,
): TaskDraft =
    defaultCreateTaskDraft(defaultTaskReminder).copy(
        overlapPolicy = if (allowConcurrentTasks) TaskOverlapPolicy.ALLOW else TaskOverlapPolicy.DISALLOW,
        allowSplitting = defaultTaskSplitting,
    )

fun TaskDraft.applyScheduleEditor(editorDraft: TaskDraft): TaskDraft =
    copy(
        hasDeadline = editorDraft.hasDeadline,
        deadline = editorDraft.deadline,
        schedulingMode = editorDraft.schedulingMode,
        hasWindow = if (editorDraft.schedulingMode == TaskSchedulingMode.FIXED_EXACT) false else editorDraft.hasWindow,
        startDate = editorDraft.startDate,
        fixedDate = editorDraft.fixedDate,
        fixedStartAt = editorDraft.fixedStartAt,
        fixedEndAt = editorDraft.fixedEndAt,
        repeatsForever = editorDraft.repeatsForever,
    )

fun TaskDraft.applyWindowEditor(editorDraft: TaskDraft): TaskDraft =
    copy(
        hasWindow = editorDraft.hasWindow,
        fixedStartAt = editorDraft.fixedStartAt,
        fixedEndAt = editorDraft.fixedEndAt,
        timeframeId = editorDraft.timeframeId,
    )

fun TaskDraft.applyRepeatEditor(editorDraft: TaskDraft): TaskDraft =
    copy(
        recurrenceType = editorDraft.recurrenceType,
        recurrenceInterval = editorDraft.recurrenceInterval,
        recurrenceDays = editorDraft.recurrenceDays,
        repeatsForever = editorDraft.repeatsForever,
        deadline = editorDraft.deadline,
    )

fun TaskDraft.applyRulesEditor(editorDraft: TaskDraft): TaskDraft =
    copy(
        priority = editorDraft.priority,
        timeframeId = editorDraft.timeframeId,
        continuationParentTaskId = editorDraft.continuationParentTaskId,
        continuationMode = editorDraft.continuationMode,
        noGap = editorDraft.noGap,
        overlapPolicy = editorDraft.overlapPolicy,
        addReminder = editorDraft.addReminder,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceSection(
    recurrenceType: RecurrenceType,
    recurrenceDays: Set<DayOfWeek>,
    onTypeChanged: (RecurrenceType) -> Unit,
    recurrenceInterval: Int,
    onIntervalChanged: (Int) -> Unit,
    disabledTypes: Set<RecurrenceType> = emptySet(),
    onDayToggle: (DayOfWeek) -> Unit,
    showTitle: Boolean = true,
) {
    if (showTitle) {
        TaskSectionTitle("Repeat")
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RecurrenceType.entries.forEach { type ->
            FilterChip(
                selected = recurrenceType == type,
                onClick = { onTypeChanged(type) },
                enabled = type !in disabledTypes,
                label = { Text(type.displayNameForCreate()) },
            )
        }
    }
    if (recurrenceType != RecurrenceType.NONE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Every", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { if (recurrenceInterval > 1) onIntervalChanged(recurrenceInterval - 1) }) { Text("-") }
            Text(recurrenceInterval.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { onIntervalChanged((recurrenceInterval + 1).coerceAtMost(30)) }) { Text("+") }
            Text(
                when (recurrenceType) {
                    RecurrenceType.DAILY -> "day(s)"
                    RecurrenceType.WEEKLY -> "week(s)"
                    RecurrenceType.MONTHLY -> "month(s)"
                    RecurrenceType.NONE -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (recurrenceType == RecurrenceType.WEEKLY) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DayOfWeek.entries.forEach { day ->
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onDayToggle(day) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (day in recurrenceDays) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            day.shortLabelForCreate().first().toString(),
                            color = if (day in recurrenceDays) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeOnlySection(
    title: String,
    time: LocalTime,
    onTimeChanged: (LocalTime) -> Unit,
    context: android.content.Context,
) {
    val timeLabel = remember(time) { DateTimeFormatter.ofPattern("h:mm a").format(time) }
    TaskSectionTitle(title)
    DateTimePickerCard(
        label = "Time",
        value = timeLabel,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onTimeChanged(LocalTime.of(hour, minute)) },
                time.hour,
                time.minute,
                false,
            ).show()
        },
    )
}

@Composable
private fun WindowTimeField(
    title: String,
    time: LocalTime,
    modifier: Modifier = Modifier,
    onTimeChanged: (LocalTime) -> Unit,
    context: android.content.Context,
) {
    val timeLabel = remember(time) { DateTimeFormatter.ofPattern("h:mm a").format(time) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskSectionTitle(title)
        DateTimePickerCard(
            label = "",
            value = timeLabel,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTimeChanged(LocalTime.of(hour, minute)) },
                    time.hour,
                    time.minute,
                    false,
                ).show()
            },
        )
    }
}

fun taskScheduleSummary(taskDraft: TaskDraft): String {
    val duration = taskDraft.estimatedMinutes.durationLabelForCreate()
    return when (taskDraft.schedulingMode) {
        TaskSchedulingMode.FLEXIBLE -> {
            if (!taskDraft.hasDeadline) {
                "Flexible | No deadline | $duration"
            } else {
                val stamp = DateTimeFormatter.ofPattern("EEE, MMM d h:mm a").format(taskDraft.deadline)
                "Flexible | $stamp | $duration"
            }
        }
        TaskSchedulingMode.FIXED_DAY -> {
            val formatter = DateTimeFormatter.ofPattern("EEE, MMM d")
            "Fixed date | ${formatter.format(taskDraft.fixedDate)} | $duration"
        }
        TaskSchedulingMode.FIXED_EXACT -> {
            val formatter = DateTimeFormatter.ofPattern("EEE, MMM d h:mm a")
            "Fixed time | ${formatter.format(taskDraft.fixedStartAt)} | $duration"
        }
        TaskSchedulingMode.FLEXIBLE_WINDOW -> {
            "Flexible | No deadline | $duration"
        }
    }
}

fun taskScheduleRowSummary(taskDraft: TaskDraft): String = when (taskDraft.schedulingMode) {
    TaskSchedulingMode.FLEXIBLE -> {
        if (!taskDraft.hasDeadline) {
            "Flexible, No deadline"
        } else {
            "Flexible, ${DateTimeFormatter.ofPattern("EEE, MMM d h:mm a").format(taskDraft.deadline)}"
        }
    }
    TaskSchedulingMode.FIXED_DAY -> "Fixed date, ${DateTimeFormatter.ofPattern("EEE, MMM d").format(taskDraft.fixedDate)}"
    TaskSchedulingMode.FIXED_EXACT -> "Fixed time, ${DateTimeFormatter.ofPattern("EEE, MMM d h:mm a").format(taskDraft.fixedStartAt)}"
    TaskSchedulingMode.FLEXIBLE_WINDOW -> "Flexible, No deadline"
}

fun taskWindowSummary(taskDraft: TaskDraft): String {
    if (!taskDraft.hasWindow && taskDraft.schedulingMode != TaskSchedulingMode.FLEXIBLE_WINDOW) return "None"
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    return "${formatter.format(taskDraft.fixedStartAt)}-${formatter.format(taskDraft.fixedEndAt)}"
}

fun taskTimingSummary(
    taskDraft: TaskDraft,
    timeframes: List<Timeframe>,
): String {
    val parts = buildList {
        val windowSummary = taskWindowSummary(taskDraft)
        if (windowSummary != "None") {
            add(windowSummary)
        }
        val timeframeName = timeframes.firstOrNull { it.id == taskDraft.timeframeId }?.name
        if (timeframeName != null) {
            add(timeframeName)
        }
    }
    return if (parts.isEmpty()) "None" else parts.joinToString(", ")
}

fun taskRepeatSummary(taskDraft: TaskDraft): String {
    if (taskDraft.recurrenceType == RecurrenceType.NONE) return "Once"
    val untilInstant = when {
        taskDraft.hasDeadline -> taskDraft.deadline.atZone(ZoneId.systemDefault()).toInstant()
        taskDraft.repeatsForever -> null
        else -> taskDraft.deadline.atZone(ZoneId.systemDefault()).toInstant()
    }
    val base = recurrenceSummary(
        RecurrenceRule(
            type = taskDraft.recurrenceType,
            interval = taskDraft.recurrenceInterval,
            daysOfWeek = taskDraft.recurrenceDays,
            until = untilInstant,
        ),
    )
    return if (!taskDraft.hasDeadline && taskDraft.repeatsForever) {
        base.removeSuffix(" until ${taskDraft.deadline.toLocalDate()}")
    } else {
        base
    }
}

fun taskRulesSummary(taskDraft: TaskDraft, allowConcurrentTasks: Boolean): String {
    val activeRules = buildList {
        if (taskDraft.continuationParentTaskId != null) {
            add("Dependency")
        }
        if (allowConcurrentTasks && taskDraft.overlapPolicy == TaskOverlapPolicy.DISALLOW) {
            add("No overlap")
        }
        if (!taskDraft.allowSplitting) add("No split")
        if (taskDraft.addReminder) add("Reminder")
        if (taskDraft.priority == TaskPriority.URGENT) add("Urgent")
    }
    return if (activeRules.isEmpty()) "None" else activeRules.joinToString(", ")
}

fun reminderSummary(reminderDraft: ReminderDraft): String {
    val formatter = if (reminderDraft.isAllDay) {
        DateTimeFormatter.ofPattern("EEE, MMM d")
    } else {
        DateTimeFormatter.ofPattern("EEE, MMM d h:mm a")
    }
    val repeatLabel = if (reminderDraft.recurrenceType == RecurrenceType.NONE) {
        "Once"
    } else {
        recurrenceSummary(
            RecurrenceRule(
                type = reminderDraft.recurrenceType,
                interval = reminderDraft.recurrenceInterval,
                daysOfWeek = reminderDraft.recurrenceDays,
            ),
        )
    }
    return "${formatter.format(reminderDraft.dueAt)} | $repeatLabel"
}

data class DurationWheelState(
    val hourOptions: List<Int>,
    val minuteOptions: List<Int>,
    val selectedHours: Int,
    val selectedMinute: Int,
    val durationMinutes: Int,
)

fun durationWheelState(
    minutes: Int,
    minMinutes: Int,
    maxMinutes: Int,
): DurationWheelState {
    val durationMinutes = durationFromWheelSelection(
        selectedHours = minutes.coerceAtLeast(0) / 60,
        selectedMinute = snapToStepForCreate(minutes.coerceAtLeast(0) % 60, 15).coerceIn(0, 45),
        minMinutes = minMinutes,
        maxMinutes = maxMinutes,
    )
    return DurationWheelState(
        hourOptions = (0..(maxMinutes.coerceAtLeast(minMinutes) / 60)).toList(),
        minuteOptions = listOf(0, 15, 30, 45),
        selectedHours = durationMinutes / 60,
        selectedMinute = durationMinutes % 60,
        durationMinutes = durationMinutes,
    )
}

fun durationFromWheelSelection(
    selectedHours: Int,
    selectedMinute: Int,
    minMinutes: Int,
    maxMinutes: Int,
): Int {
    val rawMinutes = (selectedHours.coerceAtLeast(0) * 60) + snapToStepForCreate(selectedMinute.coerceAtLeast(0), 15).coerceIn(0, 45)
    return rawMinutes.coerceIn(minMinutes, maxMinutes.coerceAtLeast(minMinutes))
}

private fun TaskDraft.resolvedOverlapPolicy(allowConcurrentTasks: Boolean): TaskDraft = this

private fun TaskSchedulingMode.labelForCreate(): String =
    when (this) {
        TaskSchedulingMode.FLEXIBLE -> "Flexible"
        TaskSchedulingMode.FLEXIBLE_WINDOW -> "Flexible"
        TaskSchedulingMode.FIXED_DAY -> "Fixed date"
        TaskSchedulingMode.FIXED_EXACT -> "Fixed time"
    }

fun TaskContinuationMode.labelForDependency(): String =
    when (this) {
        TaskContinuationMode.AFTER_PARENT_SCHEDULED_END -> "After task ends"
        TaskContinuationMode.AFTER_PARENT_DUE_AT -> "After deadline"
        TaskContinuationMode.BEFORE_PARENT_START -> "Before task starts"
    }

data class WindowSliderState(
    val startMinutes: Float,
    val endMinutes: Float,
    val endsNextDay: Boolean,
)

private const val WindowSliderStepMinutes = 15
private const val WindowSliderMaxMinutes = (24 * 60) - WindowSliderStepMinutes
private const val WindowSliderStepCount = (WindowSliderMaxMinutes / WindowSliderStepMinutes) - 1

fun windowSliderState(
    start: LocalTime,
    end: LocalTime,
    overnight: Boolean,
): WindowSliderState {
    val startMinutes = (start.hour * 60 + start.minute).toFloat()
    val rawEndMinutes = (end.hour * 60 + end.minute).toFloat()
    return WindowSliderState(
        startMinutes = startMinutes,
        endMinutes = rawEndMinutes,
        endsNextDay = overnight,
    )
}

fun windowSliderRange(
    start: LocalTime,
    end: LocalTime,
    overnight: Boolean,
): ClosedFloatingPointRange<Float> {
    val startMinutes = minutesFromStart(start)
    val endMinutes = minutesFromStart(end)
    return if (overnight) {
        endMinutes.coerceAtMost(startMinutes).toFloat()..startMinutes.coerceAtLeast(endMinutes).toFloat()
    } else {
        startMinutes.toFloat()..endMinutes.coerceAtLeast(startMinutes).toFloat()
    }
}

fun coerceWindowSliderRange(
    rawRange: ClosedFloatingPointRange<Float>,
    previousRange: ClosedFloatingPointRange<Float>,
    overnight: Boolean,
    minimumWindowMinutes: Int,
): ClosedFloatingPointRange<Float> {
    val effectiveMinimum = minimumWindowMinutes
        .coerceIn(WindowSliderStepMinutes, WindowSliderMaxMinutes)
    val rawStart = snapToStepForCreate(rawRange.start.roundToInt(), WindowSliderStepMinutes)
        .coerceIn(0, WindowSliderMaxMinutes)
    val rawEnd = snapToStepForCreate(rawRange.endInclusive.roundToInt(), WindowSliderStepMinutes)
        .coerceIn(0, WindowSliderMaxMinutes)
    var start = rawStart.coerceAtMost(rawEnd)
    var end = rawEnd.coerceAtLeast(rawStart)
    val movedStart = kotlin.math.abs(rawRange.start - previousRange.start) >
        kotlin.math.abs(rawRange.endInclusive - previousRange.endInclusive)

    if (overnight) {
        val maxGapBetweenHandles = ((24 * 60) - effectiveMinimum).coerceAtLeast(0)
        if (end - start > maxGapBetweenHandles) {
            if (movedStart) {
                start = (end - maxGapBetweenHandles).coerceAtLeast(0)
            } else {
                end = (start + maxGapBetweenHandles).coerceAtMost(WindowSliderMaxMinutes)
            }
        }
    } else if (end - start < effectiveMinimum) {
        if (movedStart) {
            start = (end - effectiveMinimum).coerceAtLeast(0)
        } else {
            end = (start + effectiveMinimum).coerceAtMost(WindowSliderMaxMinutes)
        }
        if (end - start < effectiveMinimum) {
            start = (end - effectiveMinimum).coerceAtLeast(0)
        }
    }

    return start.toFloat()..end.toFloat()
}

fun windowTimesFromSliderRange(
    range: ClosedFloatingPointRange<Float>,
    overnight: Boolean,
): Pair<LocalTime, LocalTime> {
    val lower = snapToStepForCreate(range.start.roundToInt(), WindowSliderStepMinutes)
        .coerceIn(0, WindowSliderMaxMinutes)
    val upper = snapToStepForCreate(range.endInclusive.roundToInt(), WindowSliderStepMinutes)
        .coerceIn(0, WindowSliderMaxMinutes)
    return if (overnight) {
        sliderMinutesToLocalTime(upper) to sliderMinutesToLocalTime(lower)
    } else {
        sliderMinutesToLocalTime(lower) to sliderMinutesToLocalTime(upper)
    }
}

fun sliderTimesFromRange(
    startMinutes: Float,
    endMinutes: Float,
): Pair<LocalTime, LocalTime> {
    val snappedStart = snapToStepForCreate(startMinutes.roundToInt().coerceIn(0, 24 * 60), 15).coerceIn(0, 24 * 60)
    val snappedEnd = snapToStepForCreate(endMinutes.roundToInt().coerceIn(0, 24 * 60), 15).coerceIn(0, 24 * 60)
    return sliderMinutesToLocalTime(snappedStart) to sliderMinutesToLocalTime(snappedEnd)
}

private fun TaskDraft.windowDraftFromSlider(
    startMinutes: Float,
    endMinutes: Float,
    overnight: Boolean,
): TaskDraft {
    val snappedStart = startMinutes.roundToInt().coerceIn(0, 24 * 60)
    val snappedEnd = endMinutes.roundToInt().coerceIn(0, 24 * 60)
    return withWindowTimes(
        startTime = sliderMinutesToLocalTime(snappedStart),
        endTime = sliderMinutesToLocalTime(snappedEnd),
        endsNextDay = overnight,
    )
}

private fun TaskDraft.withWindowTimes(
    startTime: LocalTime,
    endTime: LocalTime,
    endsNextDay: Boolean = !endTime.isAfter(startTime),
): TaskDraft {
    val anchorDate = when (schedulingMode) {
        TaskSchedulingMode.FIXED_DAY -> fixedDate
        TaskSchedulingMode.FIXED_EXACT -> fixedStartAt.toLocalDate()
        else -> if (hasDeadline) deadline.toLocalDate() else fixedStartAt.toLocalDate()
    }
    return copy(
        hasWindow = true,
        fixedStartAt = LocalDateTime.of(anchorDate, startTime).withSecond(0).withNano(0),
        fixedEndAt = LocalDateTime.of(anchorDate.plusDays(if (endsNextDay) 1 else 0), endTime).withSecond(0).withNano(0),
    )
}

fun windowDurationMinutes(start: LocalTime, end: LocalTime, overnight: Boolean): Int {
    val startMinutes = minutesFromStart(start)
    val endMinutes = minutesFromStart(end)
    return if (overnight) {
        ((24 * 60) - startMinutes) + endMinutes
    } else {
        endMinutes - startMinutes
    }
}

fun windowSupportsDuration(
    startTime: LocalTime,
    endTime: LocalTime,
    overnight: Boolean,
    minimumWindowMinutes: Int,
): Boolean {
    return windowDurationMinutes(startTime, endTime, overnight) >= minimumWindowMinutes
}

private fun sliderMinutesToLocalTime(totalMinutes: Int): LocalTime =
    LocalTime.MIDNIGHT.plusMinutes((totalMinutes % (24 * 60)).toLong())

private fun NumberPicker.styleDurationPicker(
    backgroundColor: Int,
    textColor: Int,
    hintColor: Int,
    dividerColor: Int,
) {
    setBackgroundColor(backgroundColor)
    try {
        val dividerField = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
        dividerField.isAccessible = true
        dividerField.set(this, android.graphics.drawable.ColorDrawable(dividerColor))
    } catch (_: Throwable) {
    }
    try {
        val paintField = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
        paintField.isAccessible = true
        (paintField.get(this) as? android.graphics.Paint)?.color = hintColor
    } catch (_: Throwable) {
    }
    for (index in 0 until childCount) {
        (getChildAt(index) as? android.widget.EditText)?.let { editText ->
            editText.setTextColor(textColor)
            editText.setHintTextColor(hintColor)
            editText.textSize = 24f
            editText.setBackgroundColor(backgroundColor)
        }
    }
    invalidate()
}

private fun RecurrenceType.displayNameForCreate(): String =
    when (this) {
        RecurrenceType.NONE -> "Once"
        RecurrenceType.DAILY -> "Daily"
        RecurrenceType.WEEKLY -> "Weekly"
        RecurrenceType.MONTHLY -> "Monthly"
    }

private fun DayOfWeek.shortLabelForCreate(): String =
    getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun Set<DayOfWeek>.toggleForCreate(day: DayOfWeek): Set<DayOfWeek> =
    if (day in this) this - day else this + day

private fun LocalTime.formatAsClockForCreate(): String =
    format(DateTimeFormatter.ofPattern("h:mm a"))

private fun snapToStepForCreate(value: Int, step: Int): Int =
    ((value + step / 2) / step) * step

private fun Int.durationLabelForCreate(): String =
    if (this < 60) "${this}m" else "${this / 60}h${if (this % 60 == 0) "" else " ${this % 60}m"}"

