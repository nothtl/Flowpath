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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
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
import dev.codex.reclaimoss.domain.model.RecurrenceRule
import dev.codex.reclaimoss.domain.model.RecurrenceType
import dev.codex.reclaimoss.domain.model.Reminder
import dev.codex.reclaimoss.domain.model.ReminderStatus
import dev.codex.reclaimoss.domain.model.ScheduleBlock
import dev.codex.reclaimoss.domain.model.ScheduleTask
import dev.codex.reclaimoss.domain.model.TaskKind
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.TaskStatus
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenReclaimApp(appGraph: AppGraph) {
    val viewModel: PlannerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(PlannerViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return PlannerViewModel(appGraph.plannerCoordinator, appGraph.appSettingsRepository) as T
                }
                throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestState by rememberUpdatedState(state)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Tasks) }
    var showingCreate by rememberSaveable { mutableStateOf(false) }
    var showingBlockerSheet by rememberSaveable { mutableStateOf(false) }
    var blockerTitle by rememberSaveable { mutableStateOf("") }
    var showingRecurring by rememberSaveable { mutableStateOf(false) }
    var createInSleepMode by rememberSaveable { mutableStateOf(false) }
    var showTaskTutorial by rememberSaveable { mutableStateOf(false) }
    var showingReminderCreate by rememberSaveable { mutableStateOf(false) }
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedReminderId by rememberSaveable { mutableStateOf<String?>(null) }
    var followUpSourceTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var editSourceTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var rescheduleSourceTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var createTaskDraftOverride by remember { mutableStateOf<TaskDraft?>(null) }
    var timeframeDraftOverride by remember { mutableStateOf<TimeframeDraft?>(null) }
    var timeframeErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var createSessionKey by rememberSaveable { mutableStateOf(0) }
    var showingTimeframeEditor by rememberSaveable { mutableStateOf(false) }
    var tasksSelectedDateEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var tasksScrollOffset by rememberSaveable { mutableStateOf(0) }
    var shouldAutoPositionTasksToNow by remember { mutableStateOf(true) }
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal, pageCount = { AppTab.entries.size })
    val sharedSelectedDate = LocalDate.ofEpochDay(tasksSelectedDateEpochDay)
    val onSharedDateChange: (LocalDate) -> Unit = { newDate ->
        tasksSelectedDateEpochDay = newDate.toEpochDay()
        tasksScrollOffset = 0
        shouldAutoPositionTasksToNow = false
    }

    LaunchedEffect(Unit) {
        viewModel.seedIfNeeded()
    }

    if (!state.settingsLoaded) {
        InitialLoadingScreen()
        return
    }

    val sleepFullyConfigured = hasCompleteSleepCoverage(state.snapshot.tasks)

    LaunchedEffect(state.settingsLoaded, state.settings.hasCompletedOnboarding, state.snapshot.tasks) {
        if (
            state.settingsLoaded &&
            !state.settings.hasCompletedOnboarding &&
            hasCompleteSleepCoverage(state.snapshot.tasks)
        ) {
            viewModel.setHasCompletedOnboarding(true)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            val tab = AppTab.entries[page]
            if (selectedTab != tab) selectedTab = tab
        }
    }
    val navigateToTab: (AppTab) -> Unit = { tab ->
        selectedTab = tab
        scope.launch {
            if (pagerState.currentPage != tab.ordinal) {
                pagerState.scrollToPage(tab.ordinal)
            }
        }
    }

    BackHandler(enabled = showingCreate) {
        showingCreate = false
        followUpSourceTaskId = null
        editSourceTaskId = null
        rescheduleSourceTaskId = null
        createTaskDraftOverride = null
        createInSleepMode = false
        showTaskTutorial = false
    }
    BackHandler(enabled = showingReminderCreate) {
        showingReminderCreate = false
    }
    BackHandler(enabled = showingBlockerSheet) {
        showingBlockerSheet = false
    }
    BackHandler(enabled = showingTimeframeEditor) {
        showingTimeframeEditor = false
        timeframeDraftOverride = null
        timeframeErrorMessage = null
    }
    BackHandler(enabled = showingRecurring) {
        showingRecurring = false
    }
    BackHandler(enabled = selectedReminderId != null) {
        selectedReminderId = null
    }
    BackHandler(enabled = selectedTaskId != null) {
        selectedTaskId = null
    }

    if (showingRecurring) {
        RecurringScreen(
            existingSleepTasks = state.snapshot.tasks.filter { it.taskKind == TaskKind.SLEEP },
            allTasks = state.snapshot.tasks,
            onBack = { showingRecurring = false },
            onOpenTask = { taskId ->
                showingRecurring = false
                selectedTaskId = taskId
            },
            onAddSleep = {
                showingRecurring = false
                followUpSourceTaskId = null
                editSourceTaskId = null
                rescheduleSourceTaskId = null
                createTaskDraftOverride = null
                createSessionKey += 1
                createInSleepMode = true
                showingCreate = true
            },
        )
        return
    }

    if (showingCreate) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = CreateScreenSnackbarBottomOffset,
                    ),
                )
            },
        ) { padding ->
            CreateWorkScreen(
                padding = padding,
                timeframes = state.snapshot.timeframes,
                availableTasks = state.snapshot.tasks,
                allowConcurrentTasks = state.settings.allowConcurrentTasks,
                currentTaskId = editSourceTaskId ?: rescheduleSourceTaskId,
                sessionKey = createSessionKey,
                initialTaskDraft = createTaskDraftOverride ?: defaultCreateTaskDraft(
                    defaultTaskReminder = state.settings.defaultTaskReminder,
                    allowConcurrentTasks = state.settings.allowConcurrentTasks,
                    defaultTaskSplitting = state.settings.defaultTaskSplitting,
                ),
                defaultTaskSplitting = state.settings.defaultTaskSplitting,
                followUpMode = followUpSourceTaskId != null,
                editMode = editSourceTaskId != null,
                rescheduleMode = rescheduleSourceTaskId != null,
                sleepMode = createInSleepMode,
                showTaskTutorial = showTaskTutorial,
                existingSleepTasks = state.snapshot.tasks.filter { it.taskKind == TaskKind.SLEEP },
                isSaving = isSaving,
                onBack = {
                    showingCreate = false
                    followUpSourceTaskId = null
                    editSourceTaskId = null
                    rescheduleSourceTaskId = null
                    createTaskDraftOverride = null
                    createInSleepMode = false
                    showTaskTutorial = false
                },
                onSaveTask = { draft ->
                    if (isSaving) return@CreateWorkScreen
                    isSaving = true
                    scope.launch {
                        try {
                        val sourceTaskId = followUpSourceTaskId
                        val editTaskId = editSourceTaskId
                        val rescheduleTaskId = rescheduleSourceTaskId
                        val isSleep = createInSleepMode
                        val result: TaskCreationResult? = if (isSleep) {
                            val sleepResults = viewModel.addSleepFromDraft(draft)
                            if (sleepResults.isNullOrEmpty()) {
                                null
                            } else {
                                TaskCreationResult(
                                    taskId = sleepResults.first().taskId,
                                    scheduled = sleepResults.all { it.scheduled },
                                    partial = sleepResults.any { !it.scheduled },
                                    reason = sleepResults.firstOrNull { !it.scheduled }?.reason,
                                )
                            }
                        } else if (sourceTaskId != null) {
                            viewModel.addFollowUpTask(sourceTaskId, draft)
                        } else if (editTaskId != null) {
                            viewModel.editTask(editTaskId, draft)
                        } else if (rescheduleTaskId != null) {
                            viewModel.rescheduleTaskWithUpdate(rescheduleTaskId, draft)
                        } else {
                            viewModel.addTask(draft)
                        }
                        if (result == null) {
                            snackbarHostState.showLatestSnackbar("Unable to schedule task")
                            return@launch
                        }
                        if (!result.scheduled) {
                            snackbarHostState.showLatestSnackbar(
                                result.reason ?: if (result.partial) {
                                    "Unable to fully schedule sleep. Try a different time or shorter duration."
                                } else {
                                    "Unable to schedule sleep. Try a different time or shorter duration."
                                },
                            )
                            return@launch
                        }
                        showingCreate = false
                        followUpSourceTaskId = null
                        editSourceTaskId = null
                        rescheduleSourceTaskId = null
                        createTaskDraftOverride = null
                        createInSleepMode = false
                        showTaskTutorial = false
                        if (!isSleep) navigateToTab(AppTab.Tasks)
                        snackbarHostState.showLatestSnackbar(
                            when {
                                isSleep -> "Sleep schedule saved"
                                result.partial -> "Task partially scheduled"
                                sourceTaskId != null -> "Follow-up task created"
                                editTaskId != null -> "Task updated"
                                rescheduleTaskId != null -> "Task rescheduled"
                                else -> "Task scheduled"
                            },
                        )
                        } catch (e: Exception) {
                            isSaving = false
                            snackbarHostState.showLatestSnackbar("Something went wrong. Please try again.")
                            return@launch
                        } finally {
                            isSaving = false
                        }
                    }
                },
            )
        }
        return
    }

    if (showingBlockerSheet) {
        var blockerStartDate by remember { mutableStateOf(LocalDate.now()) }
        var blockerStartTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
        var blockerEndDate by remember { mutableStateOf(LocalDate.now()) }
        var blockerEndTime by remember { mutableStateOf(LocalTime.of(17, 0)) }
        var blockerTitleLocal by remember { mutableStateOf(blockerTitle) }
        var showBlockerStartSheet by remember { mutableStateOf(false) }
        var showBlockerEndSheet by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val dtf = remember { DateTimeFormatter.ofPattern("MMM d, h:mm a") }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showingBlockerSheet = false }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text("Add Blocker", style = MaterialTheme.typography.titleLarge)
                }

                OutlinedTextField(
                    value = blockerTitleLocal,
                    onValueChange = { blockerTitleLocal = it },
                    label = { Text("Blocker name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                CreateFormCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsSummaryRow(
                            title = "Start",
                            summary = blockerStartDate.atTime(blockerStartTime).format(dtf),
                            onClick = { showBlockerStartSheet = true },
                        )
                        SettingsSummaryRow(
                            title = "End",
                            summary = blockerEndDate.atTime(blockerEndTime).format(dtf),
                            onClick = { showBlockerEndSheet = true },
                        )
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            val zoneId = ZoneId.systemDefault()
                            val startInstant = blockerStartDate.atTime(blockerStartTime).atZone(zoneId).toInstant()
                            val endInstant = blockerEndDate.atTime(blockerEndTime).atZone(zoneId).toInstant()
                            val title = blockerTitleLocal.ifBlank { "Blocker" }
                            val result = viewModel.addBlocker(title, startInstant, endInstant)
                            blockerTitle = blockerTitleLocal
                            showingBlockerSheet = false
                            snackbarHostState.showLatestSnackbar(
                                if (result.scheduled) "Blocker added" else result.reason ?: "Unable to add blocker"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Save Blocker", style = MaterialTheme.typography.titleMedium) }

                Spacer(Modifier.height(32.dp))
            }
        }

        if (showBlockerStartSheet) {
            ModalBottomSheet(onDismissRequest = { showBlockerStartSheet = false }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Start", style = MaterialTheme.typography.titleLarge)
                    DateTimeSection(
                        title = "",
                        dateTime = blockerStartDate.atTime(blockerStartTime),
                        onDateTimeChanged = { dt ->
                            blockerStartDate = dt.toLocalDate()
                            blockerStartTime = dt.toLocalTime()
                        },
                        context = context,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (showBlockerEndSheet) {
            ModalBottomSheet(onDismissRequest = { showBlockerEndSheet = false }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("End", style = MaterialTheme.typography.titleLarge)
                    DateTimeSection(
                        title = "",
                        dateTime = blockerEndDate.atTime(blockerEndTime),
                        onDateTimeChanged = { dt ->
                            blockerEndDate = dt.toLocalDate()
                            blockerEndTime = dt.toLocalTime()
                        },
                        context = context,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        return
    }

    if (showingReminderCreate) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = CreateScreenSnackbarBottomOffset,
                    ),
                )
            },
        ) { padding ->
            CreateReminderScreen(
                padding = padding,
                onBack = { showingReminderCreate = false },
                onSaveReminder = { draft ->
                    scope.launch {
                        viewModel.addReminder(draft)
                        showingReminderCreate = false
                        navigateToTab(AppTab.Tasks)
                        snackbarHostState.showLatestSnackbar("Reminder saved")
                    }
                },
            )
        }
        return
    }

    if (showingTimeframeEditor) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            TimeframeEditorScreen(
                padding = padding,
                initialDraft = timeframeDraftOverride ?: TimeframeDraft(),
                errorMessage = timeframeErrorMessage,
                onBack = {
                    showingTimeframeEditor = false
                    timeframeDraftOverride = null
                    timeframeErrorMessage = null
                },
                onSave = { draft ->
                    scope.launch {
                        val result = viewModel.saveTimeframe(draft)
                        if (!result.saved) {
                            timeframeErrorMessage = result.errorMessage
                            return@launch
                        }
                        timeframeErrorMessage = null
                        showingTimeframeEditor = false
                        timeframeDraftOverride = null
                        snackbarHostState.showLatestSnackbar("Timeframe saved")
                    }
                },
                onDelete = if ((timeframeDraftOverride ?: TimeframeDraft()).id.isNotBlank()) {
                    { timeframeId ->
                        scope.launch {
                            viewModel.deleteTimeframe(timeframeId)
                            timeframeErrorMessage = null
                            showingTimeframeEditor = false
                            timeframeDraftOverride = null
                            snackbarHostState.showLatestSnackbar("Timeframe deleted")
                        }
                    }
                } else {
                    null
                },
            )
        }
        return
    }

    selectedReminderId?.let { reminderId ->
        val reminder = state.snapshot.reminders.firstOrNull { it.id == reminderId }
        if (reminder != null) {
            val linkedTask = reminder.linkedTaskId?.let { taskId -> state.snapshot.tasks.firstOrNull { it.id == taskId } }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                ReminderDetailScreen(
                    padding = padding,
                    reminder = reminder,
                    linkedTask = linkedTask,
                    onBack = { selectedReminderId = null },
                    onDismiss = {
                        scope.launch {
                            viewModel.dismissReminder(reminder.id)
                            selectedReminderId = null
                            snackbarHostState.showLatestSnackbar("Reminder dismissed")
                        }
                    },
                )
            }
            return
        } else {
            selectedReminderId = null
        }
    }

    selectedTaskId?.let { taskId ->
        val task = state.snapshot.tasks.firstOrNull { it.id == taskId }
        if (task != null) {
            val blocks = state.snapshot.blocks.filter { it.taskId == taskId }.sortedBy { it.startAt }
            val reminder = state.snapshot.reminders.firstOrNull { it.linkedTaskId == taskId && it.status != ReminderStatus.COMPLETED }
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                TaskDetailScreen(
                    padding = padding,
                    task = task,
                    blocks = blocks,
                    linkedReminder = reminder,
                    timeframes = state.snapshot.timeframes,
                    onBack = { selectedTaskId = null },
                    onAddReminder = {
                        scope.launch {
                            if (reminder == null) {
                                viewModel.addReminderForTask(task.id)
                                snackbarHostState.showLatestSnackbar("Reminder added")
                            } else {
                                viewModel.dismissReminder(reminder.id)
                                snackbarHostState.showLatestSnackbar("Reminder dismissed")
                            }
                        }
                    },
                    onFollowUp = {
                        followUpSourceTaskId = task.id
                        editSourceTaskId = null
                        rescheduleSourceTaskId = null
                        createTaskDraftOverride = task.toFollowUpDraft()
                        createSessionKey += 1
                        selectedTaskId = null
                        showingCreate = true
                    },
                    onEdit = {
                        followUpSourceTaskId = null
                        editSourceTaskId = task.id
                        rescheduleSourceTaskId = null
                        createTaskDraftOverride = task.toEditDraft(addReminder = reminder != null)
                        createSessionKey += 1
                        selectedTaskId = null
                        showingCreate = true
                    },
                    onReschedule = {
                        followUpSourceTaskId = null
                        editSourceTaskId = null
                        rescheduleSourceTaskId = task.id
                        createTaskDraftOverride = task.toRescheduleDraft()
                        createSessionKey += 1
                        selectedTaskId = null
                        showingCreate = true
                    },
                    onDone = { block ->
                        scope.launch {
                            viewModel.completeBlock(block, latestState.snapshot.tasks)
                            selectedTaskId = null
                            snackbarHostState.showLatestSnackbar("Block done")
                        }
                    },
                    onDoneAllRecurring = {
                        scope.launch {
                            viewModel.completeRecurringSeries(task.id)
                            selectedTaskId = null
                            snackbarHostState.showLatestSnackbar("Recurring task series done")
                        }
                    },
                    onDelete = {
                        scope.launch {
                            viewModel.deleteTask(task.id)
                            selectedTaskId = null
                            snackbarHostState.showLatestSnackbar("Task deleted")
                        }
                    },
                )
            }
            return
        } else {
            selectedTaskId = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { navigateToTab(tab) },
                        icon = {
                            val icon = when (tab) {
                                AppTab.Tasks -> Icons.Outlined.Checklist
                                AppTab.Planner -> Icons.Outlined.CalendarMonth
                                AppTab.Settings -> Icons.Outlined.Settings
                            }
                            Icon(icon, contentDescription = tab.label)
                        },
                        label = {
                            Text(
                                tab.label,
                                color = if (selectedTab == tab) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !showingRecurring,
        ) { page ->
            when (AppTab.entries[page]) {
                AppTab.Tasks -> TasksScreen(
                    padding = padding,
                    state = state,
                    settings = state.settings,
                    isActive = selectedTab == AppTab.Tasks,
                    selectedDate = sharedSelectedDate,
                    selectedDateScrollOffset = tasksScrollOffset,
                    autoScrollToNow = shouldAutoPositionTasksToNow,
                    onAutoScrollToNowConsumed = { shouldAutoPositionTasksToNow = false },
                    onSelectedDateChange = onSharedDateChange,
                    onScrollPositionChange = { date, offset ->
                        tasksSelectedDateEpochDay = date.toEpochDay()
                        tasksScrollOffset = offset
                    },
                    onTasksViewModeChanged = { value -> scope.launch { viewModel.setTasksViewMode(value) } },
                    onAddTask = {
                        followUpSourceTaskId = null
                        editSourceTaskId = null
                        rescheduleSourceTaskId = null
                        createTaskDraftOverride = null
                        createSessionKey += 1
                        showingCreate = true
                    },
                    onAddReminder = {
                        showingReminderCreate = true
                    },
                    onAddBlocker = {
                        showingBlockerSheet = true
                    },
                    onDeleteTask = { taskId ->
                        scope.launch {
                            viewModel.deleteTask(taskId)
                            snackbarHostState.showLatestSnackbar("Task deleted")
                        }
                    },
                    onOpenTask = { selectedTaskId = it },
                    onOpenReminder = { reminder ->
                        val linkedTaskId = reminder.linkedTaskId
                        if (linkedTaskId != null && state.snapshot.tasks.any { it.id == linkedTaskId }) {
                            selectedTaskId = linkedTaskId
                        } else {
                            selectedReminderId = reminder.id
                        }
                    },
                    sleepFullyConfigured = sleepFullyConfigured,
                    sleepCoveredCount = coveredSleepWeekdays(state.snapshot.tasks.filter { it.taskKind == TaskKind.SLEEP }
                        .map { SleepOnboardingEntryDraft(weekdays = it.recurrenceRule.daysOfWeek) }).size,
                    hasAnyNormalTask = state.snapshot.tasks.any { it.taskKind == TaskKind.NORMAL },
                    onStartTaskTutorial = {
                        followUpSourceTaskId = null
                        editSourceTaskId = null
                        rescheduleSourceTaskId = null
                        createTaskDraftOverride = null
                        createSessionKey += 1
                        createInSleepMode = false
                        showTaskTutorial = true
                        showingCreate = true
                    },
                    onAddSleep = {
                        followUpSourceTaskId = null
                        editSourceTaskId = null
                        rescheduleSourceTaskId = null
                        createTaskDraftOverride = null
                        createSessionKey += 1
                        createInSleepMode = true
                        showingCreate = true
                    },
                    onOpenRecurring = { showingRecurring = true },
                )

                AppTab.Planner -> PlannerScreen(
                    padding = padding,
                    state = state,
                    settings = state.settings,
                    selectedDate = sharedSelectedDate,
                    onSelectedDateChange = onSharedDateChange,
                    onRebuild = {
                        scope.launch {
                            viewModel.rebuildSchedule()
                            snackbarHostState.showLatestSnackbar("Schedule rebuilt")
                        }
                    },
                    onAddTimeframe = {
                        timeframeDraftOverride = TimeframeDraft(startDate = sharedSelectedDate, endDate = sharedSelectedDate.plusDays(4))
                        timeframeErrorMessage = null
                        showingTimeframeEditor = true
                    },
                    onEditTimeframe = { timeframeId ->
                        val timeframe = state.snapshot.timeframes.firstOrNull { it.id == timeframeId } ?: return@PlannerScreen
                        timeframeDraftOverride = timeframe.toDraft()
                        timeframeErrorMessage = null
                        showingTimeframeEditor = true
                    },
                    onDeleteTimeframe = { timeframeId ->
                        scope.launch {
                            viewModel.deleteTimeframe(timeframeId)
                            snackbarHostState.showLatestSnackbar("Timeframe deleted")
                        }
                    },
                    onToggleLock = { block ->
                        scope.launch { viewModel.toggleLock(block) }
                    },
                    onMarkDone = { block ->
                        scope.launch {
                            viewModel.completeBlock(block, state.snapshot.tasks)
                            snackbarHostState.showLatestSnackbar("Task updated")
                        }
                    },
                    onReschedule = { taskId ->
                        scope.launch { viewModel.rescheduleMissed(taskId) }
                    },
                    onOpenTask = { selectedTaskId = it },
                )

                AppTab.Settings -> SettingsScreen(
                    padding = padding,
                    settings = state.settings,
                    onThemeModeChanged = { value -> scope.launch { viewModel.setThemeMode(value) } },
                    onFontSizeScaleChanged = { value -> scope.launch { viewModel.setFontSizeScale(value) } },
                    onTasksViewModeChanged = { value -> scope.launch { viewModel.setTasksViewMode(value) } },
                    onBreakBufferChanged = { value -> scope.launch { viewModel.setBreakBufferMinutes(value) } },
                    onAllowTaskSplittingChanged = { value -> scope.launch { viewModel.setAllowTaskSplitting(value) } },
                    onDefaultTaskSplittingChanged = { value -> scope.launch { viewModel.setDefaultTaskSplitting(value) } },
                    onAllowConcurrentTasksChanged = { value -> scope.launch { viewModel.setAllowConcurrentTasks(value) } },
                    onDefaultTaskReminderChanged = { value -> scope.launch { viewModel.setDefaultTaskReminder(value) } },
                    onReminderTimingModeChanged = { value -> scope.launch { viewModel.setReminderTimingMode(value) } },
                    onHistoryRetentionChanged = { value -> scope.launch { viewModel.setHistoryRetention(value) } },
                    onTaskHourHeightDpChanged = { value -> scope.launch { viewModel.setTaskHourHeightDp(value) } },
                    isActive = selectedTab == AppTab.Settings,
                )
            }
        }
    }
}

private fun hasCompleteSleepCoverage(tasks: List<ScheduleTask>): Boolean =
    coveredSleepWeekdays(
        tasks.filter { it.taskKind == TaskKind.SLEEP }.mapNotNull { task ->
            val weekdays = task.recurrenceRule.daysOfWeek
            if (weekdays.isEmpty()) {
                null
            } else {
                SleepOnboardingEntryDraft(
                    weekdays = weekdays,
                    windowStart = task.fixedStartAt?.atZone(ZoneId.systemDefault())?.toLocalTime() ?: LocalTime.of(22, 0),
                    windowEnd = task.fixedEndAt?.atZone(ZoneId.systemDefault())?.toLocalTime() ?: LocalTime.of(8, 0),
                    durationMinutes = task.estimatedMinutes,
                )
            }
        },
    ).size == DayOfWeek.entries.size

private suspend fun SnackbarHostState.showLatestSnackbar(message: String) {
    val current = currentSnackbarData
    if (current?.visuals?.message == message) return
    current?.dismiss()
    showSnackbar(message)
}

@Composable
private fun InitialLoadingScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Flowpath",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Loading your schedule...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

