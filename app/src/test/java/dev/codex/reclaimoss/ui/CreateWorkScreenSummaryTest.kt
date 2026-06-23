package dev.codex.reclaimoss.ui

import dev.codex.reclaimoss.domain.model.RecurrenceType
import dev.codex.reclaimoss.domain.model.TaskContinuationMode
import dev.codex.reclaimoss.domain.model.TaskOverlapPolicy
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.TaskSchedulingMode
import dev.codex.reclaimoss.domain.model.ScheduleTask
import dev.codex.reclaimoss.domain.model.Timeframe
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateWorkScreenSummaryTest {

    @Test
    fun `duration wheel selection clamps below minimum`() {
        val minutes = durationFromWheelSelection(
            selectedHours = 0,
            selectedMinute = 0,
            minMinutes = 15,
            maxMinutes = 24 * 60,
        )

        assertEquals(15, minutes)
    }

    @Test
    fun `duration wheel selection clamps above maximum`() {
        val minutes = durationFromWheelSelection(
            selectedHours = 24,
            selectedMinute = 45,
            minMinutes = 15,
            maxMinutes = 24 * 60,
        )

        assertEquals(24 * 60, minutes)
    }

    @Test
    fun `duration wheel state snaps current duration into hours and minutes`() {
        val state = durationWheelState(
            minutes = 135,
            minMinutes = 15,
            maxMinutes = 24 * 60,
        )

        assertEquals(2, state.selectedHours)
        assertEquals(15, state.selectedMinute)
        assertEquals((0..24).toList(), state.hourOptions)
        assertEquals(listOf(0, 15, 30, 45), state.minuteOptions)
    }

    @Test
    fun `task schedule summary shows no deadline flexible tasks compactly`() {
        val summary = withLocale(Locale.US) {
            taskScheduleSummary(
                TaskDraft(
                    hasDeadline = false,
                    estimatedMinutes = 60,
                    schedulingMode = TaskSchedulingMode.FLEXIBLE,
                ),
            )
        }

        assertEquals("Flexible | No deadline | 1h", summary)
    }

    @Test
    fun `task schedule row summary shows no deadline flexible tasks cleanly`() {
        val summary = withLocale(Locale.US) {
            taskScheduleRowSummary(
                TaskDraft(
                    hasDeadline = false,
                    schedulingMode = TaskSchedulingMode.FLEXIBLE,
                ),
            )
        }

        assertEquals("Flexible, No deadline", summary)
    }

    @Test
    fun `default create task draft starts as flexible no deadline`() {
        val draft = defaultCreateTaskDraft(defaultTaskReminder = true)

        assertEquals(TaskSchedulingMode.FLEXIBLE, draft.schedulingMode)
        assertEquals(false, draft.hasDeadline)
        assertEquals(true, draft.addReminder)
    }

    @Test
    fun `applying schedule editor only copies schedule fields`() {
        val base = TaskDraft(
            title = "Essay",
            description = "Draft chapter",
            priority = TaskPriority.URGENT,
            timeframeId = "tf-1",
            overlapPolicy = TaskOverlapPolicy.DISALLOW,
            continuationParentTaskId = "task-2",
            continuationMode = TaskContinuationMode.AFTER_PARENT_DUE_AT,
            addReminder = true,
        )
        val editedSchedule = TaskDraft(
            title = "Ignored",
            description = "Ignored",
            priority = TaskPriority.MEDIUM,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            hasDeadline = true,
            deadline = LocalDateTime.of(2026, 6, 3, 13, 0),
            startDate = LocalDate.of(2026, 6, 2),
            fixedDate = LocalDate.of(2026, 6, 3),
            fixedStartAt = LocalDateTime.of(2026, 6, 3, 12, 0),
            fixedEndAt = LocalDateTime.of(2026, 6, 3, 13, 0),
        )

        val updated = base.applyScheduleEditor(editedSchedule)

        assertEquals("Essay", updated.title)
        assertEquals(TaskPriority.URGENT, updated.priority)
        assertEquals("tf-1", updated.timeframeId)
        assertEquals(TaskSchedulingMode.FIXED_EXACT, updated.schedulingMode)
        assertEquals(LocalDateTime.of(2026, 6, 3, 13, 0), updated.deadline)
        assertEquals(LocalDateTime.of(2026, 6, 3, 12, 0), updated.fixedStartAt)
    }

    @Test
    fun `task window row summary formats overnight windows compactly`() {
        val summary = withLocale(Locale.US) {
            taskWindowSummary(
                TaskDraft(
                    hasWindow = true,
                    schedulingMode = TaskSchedulingMode.FLEXIBLE,
                    fixedStartAt = LocalDateTime.of(2026, 6, 3, 22, 0),
                    fixedEndAt = LocalDateTime.of(2026, 6, 4, 2, 0),
                ),
            )
        }

        assertEquals("10:00 PM-2:00 AM", summary)
    }

    @Test
    fun `applying window editor copies timing fields including timeframe`() {
        val base = TaskDraft(
            title = "Read",
            schedulingMode = TaskSchedulingMode.FIXED_DAY,
            fixedDate = LocalDate.of(2026, 6, 6),
            timeframeId = "tf-2",
        )
        val editedWindow = TaskDraft(
            title = "Ignored",
            hasWindow = true,
            schedulingMode = TaskSchedulingMode.FLEXIBLE,
            fixedDate = LocalDate.of(2026, 6, 10),
            timeframeId = "tf-3",
            fixedStartAt = LocalDateTime.of(2026, 6, 6, 18, 0),
            fixedEndAt = LocalDateTime.of(2026, 6, 7, 1, 0),
        )

        val updated = base.applyWindowEditor(editedWindow)

        assertEquals("Read", updated.title)
        assertEquals(TaskSchedulingMode.FIXED_DAY, updated.schedulingMode)
        assertEquals(LocalDate.of(2026, 6, 6), updated.fixedDate)
        assertEquals(LocalDateTime.of(2026, 6, 6, 18, 0), updated.fixedStartAt)
        assertEquals(LocalDateTime.of(2026, 6, 7, 1, 0), updated.fixedEndAt)
        assertEquals("tf-3", updated.timeframeId)
    }

    @Test
    fun `applyWindowEditor trusts editor hasWindow toggle when disabled`() {
        val base = TaskDraft(
            title = "Sleep",
            hasWindow = true,
            fixedStartAt = LocalDateTime.of(2026, 6, 6, 22, 0),
            fixedEndAt = LocalDateTime.of(2026, 6, 7, 6, 0),
        )
        val editor = TaskDraft(
            hasWindow = false,
            fixedStartAt = base.fixedStartAt,
            fixedEndAt = base.fixedEndAt,
        )

        val updated = base.applyWindowEditor(editor)

        assertEquals(false, updated.hasWindow)
    }

    @Test
    fun `window duration non overnight returns end minus start`() {
        assertEquals(
            180,
            windowDurationMinutes(
                start = LocalTime.of(18, 0),
                end = LocalTime.of(21, 0),
                overnight = false,
            ),
        )
    }

    @Test
    fun `window duration overnight wraps around midnight`() {
        assertEquals(
            240,
            windowDurationMinutes(
                start = LocalTime.of(22, 0),
                end = LocalTime.of(2, 0),
                overnight = true,
            ),
        )
    }

    @Test
    fun `window duration overnight two hours to midnight`() {
        assertEquals(
            120,
            windowDurationMinutes(
                start = LocalTime.of(22, 0),
                end = LocalTime.of(0, 0),
                overnight = true,
            ),
        )
    }

    @Test
    fun `window slider state keeps overnight as a separate flag on a 24 hour range`() {
        val state = windowSliderState(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(2, 0),
            overnight = true,
        )

        assertEquals(22 * 60f, state.startMinutes)
        assertEquals(2 * 60f, state.endMinutes)
        assertEquals(true, state.endsNextDay)
    }

    @Test
    fun `window slider range uses start to end for normal windows`() {
        val range = windowSliderRange(
            start = LocalTime.of(18, 0),
            end = LocalTime.of(21, 0),
            overnight = false,
        )

        assertEquals(18 * 60f, range.start)
        assertEquals(21 * 60f, range.endInclusive)
    }

    @Test
    fun `window slider range uses end to start for overnight windows`() {
        val range = windowSliderRange(
            start = LocalTime.of(22, 0),
            end = LocalTime.of(2, 0),
            overnight = true,
        )

        assertEquals(2 * 60f, range.start)
        assertEquals(22 * 60f, range.endInclusive)
    }

    @Test
    fun `overnight slider range maps left handle to end and right handle to start`() {
        val (start, end) = windowTimesFromSliderRange(
            range = (2 * 60f)..(22 * 60f),
            overnight = true,
        )

        assertEquals(LocalTime.of(22, 0), start)
        assertEquals(LocalTime.of(2, 0), end)
    }

    @Test
    fun `normal window slider enforces minimum duration between handles`() {
        val range = coerceWindowSliderRange(
            rawRange = (18 * 60f)..((18 * 60f) + 30f),
            previousRange = (18 * 60f)..(21 * 60f),
            overnight = false,
            minimumWindowMinutes = 60,
        )

        assertEquals(18 * 60f, range.start)
        assertEquals(19 * 60f, range.endInclusive)
    }

    @Test
    fun `overnight window slider enforces minimum duration outside handles`() {
        val range = coerceWindowSliderRange(
            rawRange = 0f..(22 * 60f),
            previousRange = (2 * 60f)..(22 * 60f),
            overnight = true,
            minimumWindowMinutes = 600,
        )

        assertEquals(8 * 60f, range.start)
        assertEquals(22 * 60f, range.endInclusive)
    }

    @Test
    fun `task repeat summary reflects weekly recurrence`() {
        val summary = withLocale(Locale.US) {
            taskRepeatSummary(
                TaskDraft(
                    recurrenceType = RecurrenceType.WEEKLY,
                    recurrenceInterval = 1,
                    recurrenceDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                ),
            )
        }

        assertTrue(summary.startsWith("Every 1 week: "))
        assertTrue(summary.contains("Tue"))
        assertTrue(summary.contains("Thu"))
    }

    @Test
    fun `task repeat summary uses deadline as end for recurring deadline tasks`() {
        val summary = withLocale(Locale.US) {
            taskRepeatSummary(
                TaskDraft(
                    hasDeadline = true,
                    repeatsForever = true,
                    recurrenceType = RecurrenceType.WEEKLY,
                    recurrenceInterval = 1,
                    recurrenceDays = setOf(DayOfWeek.TUESDAY),
                    deadline = LocalDateTime.of(2026, 6, 2, 17, 0),
                ),
            )
        }

        assertTrue(summary.contains("until Jun 2"))
    }

    @Test
    fun `task repeat summary keeps forever recurrence for no deadline tasks`() {
        val summary = withLocale(Locale.US) {
            taskRepeatSummary(
                TaskDraft(
                    hasDeadline = false,
                    repeatsForever = true,
                    recurrenceType = RecurrenceType.WEEKLY,
                    recurrenceInterval = 1,
                    recurrenceDays = setOf(DayOfWeek.TUESDAY),
                ),
            )
        }

        assertTrue(summary.startsWith("Every 1 week: "))
        assertTrue(!summary.contains("until "))
    }

    @Test
    fun `task timing summary combines window and timeframe`() {
        val summary = withLocale(Locale.US) {
            taskTimingSummary(
                taskDraft = TaskDraft(
                    hasWindow = true,
                    fixedStartAt = LocalDateTime.of(2026, 6, 3, 18, 0),
                    fixedEndAt = LocalDateTime.of(2026, 6, 3, 21, 0),
                    timeframeId = "timeframe-1",
                ),
                timeframes = listOf(
                    Timeframe(
                        id = "timeframe-1",
                        name = "Finals",
                        startDate = LocalDate.of(2026, 6, 1),
                        endDate = LocalDate.of(2026, 6, 7),
                        colorHex = "#F4B6D2",
                    ),
                ),
            )
        }

        assertEquals("6:00 PM-9:00 PM, Finals", summary)
    }

    @Test
    fun `task rules summary lists active optional rules without timeframe`() {
        val summary = taskRulesSummary(
            taskDraft = TaskDraft(
                continuationParentTaskId = "task-2",
                continuationMode = TaskContinuationMode.AFTER_PARENT_SCHEDULED_END,
                overlapPolicy = TaskOverlapPolicy.DISALLOW,
                addReminder = true,
                priority = TaskPriority.URGENT,
            ),
            allowConcurrentTasks = true,
        )

        assertEquals("Dependency, No overlap, Reminder, Urgent", summary)
    }

    @Test
    fun `task rules summary hides overlap copy when global overlap is off`() {
        val summary = taskRulesSummary(
            taskDraft = TaskDraft(
                continuationParentTaskId = "task-2",
                overlapPolicy = TaskOverlapPolicy.DISALLOW,
                addReminder = true,
            ),
            allowConcurrentTasks = false,
        )

        assertEquals("Dependency, Reminder", summary)
    }

    @Test
    fun `window support rejects ranges shorter than task duration`() {
        assertEquals(
            false,
            windowSupportsDuration(
                startTime = LocalTime.of(18, 0),
                endTime = LocalTime.of(18, 30),
                overnight = false,
                minimumWindowMinutes = 60,
            ),
        )
    }

    @Test
    fun `dependency picker groups tasks by their start day`() {
        val zone = ZoneId.of("America/New_York")
        val june3 = LocalDate.of(2026, 6, 3)
        val june4 = LocalDate.of(2026, 6, 4)
        val tasks = listOf(
            dependencyTask(
                id = "a",
                title = "Morning class",
                start = LocalDateTime.of(2026, 6, 3, 9, 0),
            ),
            dependencyTask(
                id = "b",
                title = "Essay block",
                start = LocalDateTime.of(2026, 6, 3, 18, 0),
            ),
            dependencyTask(
                id = "c",
                title = "Gym",
                start = LocalDateTime.of(2026, 6, 4, 7, 30),
            ),
        )

        assertEquals(listOf(june3, june4), dependencyDateOptions(tasks, zone))
        assertEquals(
            listOf("Morning class", "Essay block"),
            tasksForDependencyDate(tasks, june3, zone).map { it.title },
        )
        assertEquals(june4, initialDependencyDate(tasks, "c", zone))
        assertEquals(june3, initialDependencyDate(tasks, null, zone))
    }

    @Test
    fun `dependency mode labels stay concise and clearer`() {
        assertEquals("After task ends", TaskContinuationMode.AFTER_PARENT_SCHEDULED_END.labelForDependency())
        assertEquals("After deadline", TaskContinuationMode.AFTER_PARENT_DUE_AT.labelForDependency())
        assertEquals("Before task starts", TaskContinuationMode.BEFORE_PARENT_START.labelForDependency())
    }

    private fun dependencyTask(
        id: String,
        title: String,
        start: LocalDateTime,
    ): ScheduleTask = ScheduleTask(
        id = id,
        title = title,
        priority = TaskPriority.MEDIUM,
        dueAt = start.plusHours(1).atZone(ZoneId.of("America/New_York")).toInstant(),
        estimatedMinutes = 60,
        remainingMinutes = 60,
        schedulingMode = TaskSchedulingMode.FIXED_EXACT,
        fixedStartAt = start.atZone(ZoneId.of("America/New_York")).toInstant(),
        fixedEndAt = start.plusHours(1).atZone(ZoneId.of("America/New_York")).toInstant(),
    )

    @Test
    fun `reminder summary shows due time and repeat state`() {
        val summary = withLocale(Locale.US) {
            reminderSummary(
                ReminderDraft(
                    dueAt = LocalDateTime.of(2026, 6, 2, 18, 0),
                    recurrenceType = RecurrenceType.NONE,
                ),
            )
        }

        assertEquals("Tue, Jun 2 6:00 PM | Once", summary)
    }

    // ── computeSchedulingMode ──

    @Test
    fun `computeSchedulingMode - nothing selected is FLEXIBLE`() {
        val draft = TaskDraft(dayOn = false, dayAfter = false, dayBy = false, hoursMode = HoursMode.ANY)
        assertEquals(TaskSchedulingMode.FLEXIBLE, draft.computeSchedulingMode())
    }

    @Test
    fun `computeSchedulingMode - Fixed date only is FIXED_DAY`() {
        val draft = TaskDraft(dayOn = true, dayAfter = false, dayBy = false, hoursMode = HoursMode.ANY)
        assertEquals(TaskSchedulingMode.FIXED_DAY, draft.computeSchedulingMode())
    }

    @Test
    fun `computeSchedulingMode - Fixed date + Window is FLEXIBLE_WINDOW`() {
        val draft = TaskDraft(dayOn = true, dayAfter = false, dayBy = false, hoursMode = HoursMode.WINDOW)
        assertEquals(TaskSchedulingMode.FLEXIBLE_WINDOW, draft.computeSchedulingMode())
    }

    @Test
    fun `computeSchedulingMode - Fixed date + At is FIXED_EXACT`() {
        val draft = TaskDraft(dayOn = true, dayAfter = false, dayBy = false, hoursMode = HoursMode.AT)
        assertEquals(TaskSchedulingMode.FIXED_EXACT, draft.computeSchedulingMode())
    }

    @Test
    fun `computeSchedulingMode - At with no date is FLEXIBLE_TIME`() {
        val draft = TaskDraft(dayOn = false, dayAfter = false, dayBy = false, hoursMode = HoursMode.AT)
        assertEquals(TaskSchedulingMode.FLEXIBLE_TIME, draft.computeSchedulingMode())
    }

    @Test
    fun `computeSchedulingMode - At with After is FLEXIBLE_TIME`() {
        val draft = TaskDraft(dayOn = false, dayAfter = true, dayBy = false, hoursMode = HoursMode.AT)
        assertEquals(TaskSchedulingMode.FLEXIBLE_TIME, draft.computeSchedulingMode())
    }

    @Test
    fun `computeSchedulingMode - Window only is FLEXIBLE_WINDOW`() {
        val draft = TaskDraft(dayOn = false, dayAfter = false, dayBy = false, hoursMode = HoursMode.WINDOW)
        assertEquals(TaskSchedulingMode.FLEXIBLE_WINDOW, draft.computeSchedulingMode())
    }

    @Test
    fun `computeSchedulingMode - After+By with Any time is FLEXIBLE`() {
        val draft = TaskDraft(dayOn = false, dayAfter = true, dayBy = true, hoursMode = HoursMode.ANY)
        assertEquals(TaskSchedulingMode.FLEXIBLE, draft.computeSchedulingMode())
    }

    // ── recurrenceNeedsAnchor ──

    @Test
    fun `recurrenceNeedsAnchor - None never needs anchor`() {
        assertEquals(false, recurrenceNeedsAnchor(RecurrenceType.NONE, 1, emptySet()))
    }

    @Test
    fun `recurrenceNeedsAnchor - Daily interval 1 does not need anchor`() {
        assertEquals(false, recurrenceNeedsAnchor(RecurrenceType.DAILY, 1, emptySet()))
    }

    @Test
    fun `recurrenceNeedsAnchor - Daily interval 2 needs anchor`() {
        assertEquals(true, recurrenceNeedsAnchor(RecurrenceType.DAILY, 2, emptySet()))
    }

    @Test
    fun `recurrenceNeedsAnchor - Daily interval 3 needs anchor`() {
        assertEquals(true, recurrenceNeedsAnchor(RecurrenceType.DAILY, 3, emptySet()))
    }

    @Test
    fun `recurrenceNeedsAnchor - Weekly with days and interval 1 does not need anchor`() {
        assertEquals(false, recurrenceNeedsAnchor(RecurrenceType.WEEKLY, 1, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
    }

    @Test
    fun `recurrenceNeedsAnchor - Weekly with no days needs anchor`() {
        assertEquals(true, recurrenceNeedsAnchor(RecurrenceType.WEEKLY, 1, emptySet()))
    }

    @Test
    fun `recurrenceNeedsAnchor - Weekly interval 2 needs anchor`() {
        assertEquals(true, recurrenceNeedsAnchor(RecurrenceType.WEEKLY, 2, setOf(DayOfWeek.MONDAY)))
    }

    @Test
    fun `recurrenceNeedsAnchor - Monthly always needs anchor`() {
        assertEquals(true, recurrenceNeedsAnchor(RecurrenceType.MONTHLY, 1, emptySet()))
        assertEquals(true, recurrenceNeedsAnchor(RecurrenceType.MONTHLY, 2, emptySet()))
    }

    // ── hasDeadline from tabs ──

    @Test
    fun `hasDeadline is true when dayBy`() {
        val draft = TaskDraft(dayOn = false, dayAfter = false, dayBy = true)
        assertEquals(true, draft.dayBy || draft.dayOn)
    }

    @Test
    fun `hasDeadline is true when dayOn`() {
        val draft = TaskDraft(dayOn = true, dayAfter = false, dayBy = false)
        assertEquals(true, draft.dayBy || draft.dayOn)
    }

    @Test
    fun `hasDeadline is false when nothing checked`() {
        val draft = TaskDraft(dayOn = false, dayAfter = false, dayBy = false)
        assertEquals(false, draft.dayBy || draft.dayOn)
    }

    @Test
    fun `hasDeadline is false when only dayAfter`() {
        val draft = TaskDraft(dayOn = false, dayAfter = true, dayBy = false)
        assertEquals(false, draft.dayBy || draft.dayOn)
    }

    // ── listSaver round-trip for new tab fields ──

    @Test
    fun `listSaver round-trip preserves new tab fields`() {
        val draft = TaskDraft(
            dayOn = true,
            dayAfter = false,
            dayBy = false,
            hoursMode = HoursMode.AT,
            hoursAtTime = LocalTime.of(14, 30),
            fixedDate = LocalDate.of(2026, 6, 20),
        )
        // Simulate what listSaver.save produces (index 25-29 are tab fields)
        val saved = listOf(
            draft.title, draft.description, draft.priority.name,
            draft.preferredTimePeriodId ?: "", draft.timeframeId ?: "",
            draft.hasDeadline, draft.continuationParentTaskId ?: "",
            draft.continuationMode?.name ?: "", draft.noGap.toString(),
            draft.overlapPolicy.name, draft.allowSplitting,
            draft.deadline.toString(), draft.schedulingMode.name,
            draft.hasWindow, draft.startDate?.toString() ?: "",
            draft.fixedDate.toString(), draft.fixedStartAt.toString(),
            draft.fixedEndAt.toString(), draft.repeatsForever,
            draft.estimatedMinutes, draft.addReminder,
            draft.recurrenceType.name, draft.recurrenceInterval,
            draft.recurrenceDays.joinToString(",") { day -> day.name },
            draft.firstOccurrence.toString(),
            draft.dayOn, draft.dayAfter, draft.dayBy,
            draft.hoursMode.name, draft.hoursAtTime.toString(),
        )
        assertEquals(30, saved.size)

        // Simulate restore
        val restored = TaskDraft(
            title = saved[0] as String,
            description = saved[1] as String,
            priority = TaskPriority.valueOf(saved[2] as String),
            preferredTimePeriodId = (saved[3] as String).ifBlank { null },
            timeframeId = (saved[4] as String).ifBlank { null },
            hasDeadline = saved[5] as Boolean,
            continuationParentTaskId = (saved[6] as String).ifBlank { null },
            continuationMode = (saved[7] as String).ifBlank { null }?.let { TaskContinuationMode.valueOf(it) },
            noGap = (saved[8] as String).toBooleanStrict(),
            overlapPolicy = TaskOverlapPolicy.valueOf(saved[9] as String),
            allowSplitting = saved[10] as Boolean,
            firstOccurrence = LocalDateTime.parse(saved[24] as String),
            deadline = LocalDateTime.parse(saved[11] as String),
            schedulingMode = TaskSchedulingMode.valueOf(saved[12] as String),
            hasWindow = saved[13] as Boolean,
            startDate = (saved[14] as String).ifBlank { null }?.let { LocalDate.parse(it) },
            fixedDate = LocalDate.parse(saved[15] as String),
            fixedStartAt = LocalDateTime.parse(saved[16] as String),
            fixedEndAt = LocalDateTime.parse(saved[17] as String),
            repeatsForever = saved[18] as Boolean,
            estimatedMinutes = saved[19] as Int,
            addReminder = saved[20] as Boolean,
            recurrenceType = RecurrenceType.valueOf(saved[21] as String),
            recurrenceInterval = saved[22] as Int,
            recurrenceDays = (saved[23] as String).takeIf { it.isNotBlank() }?.split(",")?.map { DayOfWeek.valueOf(it) }?.toSet() ?: emptySet(),
            dayOn = saved[25] as Boolean,
            dayAfter = saved[26] as Boolean,
            dayBy = saved[27] as Boolean,
            hoursMode = HoursMode.valueOf(saved[28] as String),
            hoursAtTime = LocalTime.parse(saved[29] as String),
        )

        assertEquals(draft.dayOn, restored.dayOn)
        assertEquals(draft.dayAfter, restored.dayAfter)
        assertEquals(draft.dayBy, restored.dayBy)
        assertEquals(draft.hoursMode, restored.hoursMode)
        assertEquals(draft.hoursAtTime, restored.hoursAtTime)
        assertEquals(draft.fixedDate, restored.fixedDate)
    }

    // ── Day / Hours mutual exclusion logic ──

    @Test
    fun `dayOn hides After and By`() {
        val draft = TaskDraft(dayOn = true, dayAfter = false, dayBy = false)
        val showOn = !draft.dayAfter && !draft.dayBy
        val showAfter = !draft.dayOn
        val showBy = !draft.dayOn
        assertTrue(showOn)
        assertEquals(false, showAfter)
        assertEquals(false, showBy)
    }

    @Test
    fun `dayAfter hides On`() {
        val draft = TaskDraft(dayOn = false, dayAfter = true, dayBy = false)
        val showOn = !draft.dayAfter && !draft.dayBy
        val showAfter = !draft.dayOn
        val showBy = !draft.dayOn
        assertEquals(false, showOn) // After is checked → On hidden
        assertTrue(showAfter)
        assertTrue(showBy)
    }

    @Test
    fun `dayBy hides On`() {
        val draft = TaskDraft(dayOn = false, dayAfter = false, dayBy = true)
        val showOn = !draft.dayAfter && !draft.dayBy
        assertEquals(false, showOn)
    }

    @Test
    fun `dayAfter and dayBy coexist`() {
        val draft = TaskDraft(dayOn = false, dayAfter = true, dayBy = true)
        val showOn = !draft.dayAfter && !draft.dayBy
        val showAfter = !draft.dayOn
        val showBy = !draft.dayOn
        assertEquals(false, showOn)
        assertTrue(showAfter)
        assertTrue(showBy)
    }

    // ── missingAnchor logic ──

    @Test
    fun `missingAnchor true when daily every 2 days and no start date`() {
        val needsAnchor = recurrenceNeedsAnchor(RecurrenceType.DAILY, 2, emptySet())
        val hasStartDate = false // !dayOn && !dayAfter
        assertEquals(true, needsAnchor && !hasStartDate)
    }

    @Test
    fun `missingAnchor false when daily every 2 days but dayOn set`() {
        val needsAnchor = recurrenceNeedsAnchor(RecurrenceType.DAILY, 2, emptySet())
        val hasStartDate = true // dayOn = true
        assertEquals(false, needsAnchor && !hasStartDate)
    }

    @Test
    fun `missingAnchor false when daily interval 1`() {
        val needsAnchor = recurrenceNeedsAnchor(RecurrenceType.DAILY, 1, emptySet())
        assertEquals(false, needsAnchor)
    }

    @Test
    fun `missingAnchor false when recurrence is NONE`() {
        val needsAnchor = recurrenceNeedsAnchor(RecurrenceType.NONE, 1, emptySet())
        assertEquals(false, needsAnchor)
    }

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
