package dev.codex.reclaimoss.domain.service

import dev.codex.reclaimoss.data.calendar.NoOpGoogleCalendarGateway
import dev.codex.reclaimoss.data.repository.PlannerRepository
import dev.codex.reclaimoss.data.repository.PlannerSnapshot
import dev.codex.reclaimoss.domain.model.BlockCompletionState
import dev.codex.reclaimoss.domain.model.BlockLockState
import dev.codex.reclaimoss.domain.model.BlockSource
import dev.codex.reclaimoss.domain.model.PreferredTimeOfDay
import dev.codex.reclaimoss.domain.model.Project
import dev.codex.reclaimoss.domain.model.RecurrenceEndMode
import dev.codex.reclaimoss.domain.model.RecurrenceRule
import dev.codex.reclaimoss.domain.model.RecurrenceType
import dev.codex.reclaimoss.domain.model.Reminder
import dev.codex.reclaimoss.domain.model.ReminderStatus
import dev.codex.reclaimoss.domain.model.ScheduleBlock
import dev.codex.reclaimoss.domain.model.ScheduleTask
import dev.codex.reclaimoss.domain.model.SchedulingIssue
import dev.codex.reclaimoss.domain.model.SchedulingIssueType
import dev.codex.reclaimoss.domain.model.Timeframe
import dev.codex.reclaimoss.domain.model.TaskContinuationMode
import dev.codex.reclaimoss.domain.model.TaskOverlapPolicy
import dev.codex.reclaimoss.domain.model.TaskSchedulingMode
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.TaskStatus
import dev.codex.reclaimoss.domain.model.TimePeriod
import dev.codex.reclaimoss.domain.model.TimePeriodType
import dev.codex.reclaimoss.domain.scheduling.SchedulerEngine
import dev.codex.reclaimoss.settings.AppSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerCoordinatorTest {
    private val zone = ZoneId.of("America/New_York")
    private val clock = Clock.fixed(LocalDateTime.of(2026, 5, 21, 20, 0).atZone(zone).toInstant(), zone)

    @Test
    fun `completing a daily recurring task rolls it to the next day`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = Instant.parse("2026-05-14T21:00:00Z")
        val task = task(
            id = "daily",
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
        )
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(block(task.id, "block-daily", dueAt.minusSeconds(3600), dueAt)),
        )

        coordinator.markBlockDone("block-daily", task.id, 60)

        val updated = repository.getTasks().single()
        assertEquals(TaskStatus.ACTIVE, updated.status)
        assertEquals(updated.estimatedMinutes, updated.remainingMinutes)
        assertTrue(updated.dueAt.isAfter(now()))
        assertEquals(dueAt.atZone(zone).toLocalTime(), updated.dueAt.atZone(zone).toLocalTime())
        assertTrue(repository.getBlocks().any { it.taskId == task.id && it.completionState == BlockCompletionState.COMPLETED })
    }

    @Test
    fun `weekly recurring task advances to next selected weekday`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = Instant.parse("2026-05-14T21:00:00Z")
        val task = task(
            id = "weekly",
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKLY, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
        )
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(block(task.id, "block-weekly", dueAt.minusSeconds(3600), dueAt)),
        )

        coordinator.markBlockDone("block-weekly", task.id, 60)

        val updated = repository.getTasks().single()
        assertEquals(DayOfWeek.FRIDAY, updated.dueAt.atZone(zone).dayOfWeek)
    }

    @Test
    fun `life periods block task scheduling`() = runTest {
        val repository = FakePlannerRepository(
            periods = mutableListOf(
                TimePeriod("period-morning", "Morning", LocalTime.of(9, 0), LocalTime.of(12, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 0),
                TimePeriod("period-lunch", "Lunch", LocalTime.of(12, 0), LocalTime.of(13, 0), type = TimePeriodType.LIFE, sortOrder = 1),
                TimePeriod("period-afternoon", "Afternoon", LocalTime.of(13, 0), LocalTime.of(17, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 2),
            ),
        )
        val coordinator = coordinator(repository)
        val date = now().atZone(zone).toLocalDate()
        val dueAt = date.atTime(14, 0).atZone(zone).toInstant()
        repository.upsertTask(
            task(
                id = "lunch-safe",
                dueAt = dueAt,
                recurrenceRule = RecurrenceRule(),
                preferredTimePeriodId = "period-morning",
                estimatedMinutes = 240,
            ),
        )

        coordinator.scheduleTask("lunch-safe")

        assertTrue(repository.getBlocks().none { block ->
            val start = block.startAt.atZone(zone).toLocalTime()
            val end = block.endAt.atZone(zone).toLocalTime()
            start < LocalTime.of(13, 0) && end > LocalTime.of(12, 0)
        })
    }

    @Test
    fun `tasks still schedule when legacy productive periods are absent`() = runTest {
        val repository = FakePlannerRepository(periods = mutableListOf())
        val coordinator = coordinator(repository)
        val dueAt = now().plusSeconds(24 * 60 * 60)

        val result = coordinator.createTask(
            title = "Needs productive time",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
        )

        assertTrue(result.scheduled)
        assertTrue(repository.getBlocks().any { it.taskId == result.taskId })
        assertTrue(repository.getSchedulingIssues().none { it.taskId == result.taskId })
    }

    @Test
    fun `creating task linked reminder persists both records`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = now().plusSeconds(3600)
        val task = task("task-with-reminder", dueAt, RecurrenceRule())
        repository.upsertTask(task)

        coordinator.createReminderForTask(task.id)

        val reminder = repository.getReminders().single()
        assertEquals(task.id, reminder.linkedTaskId)
        assertEquals(task.title, reminder.title)
        assertEquals(task.dueAt, reminder.dueAt)
        assertFalse(reminder.isAllDay)
    }

    @Test
    fun `creating whole day reminder persists all day flag`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)

        coordinator.createReminder(
            title = "Holiday",
            description = "",
            dueAt = now().plusSeconds(3600),
            isAllDay = true,
        )

        val reminder = repository.getReminders().single()
        assertTrue(reminder.isAllDay)
    }

    @Test
    fun `task linked reminder uses scheduled completion time when blocks exist`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = now().plusSeconds(7200)
        val task = task("scheduled-task-reminder", dueAt, RecurrenceRule())
        repository.upsertTask(task)
        val scheduledEnd = now().plusSeconds(5400)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(block(task.id, "scheduled-block", now().plusSeconds(1800), scheduledEnd)),
        )

        coordinator.createReminderForTask(task.id)

        val reminder = repository.getReminders().single()
        assertEquals(task.id, reminder.linkedTaskId)
        assertEquals(scheduledEnd, reminder.dueAt)
    }

    @Test
    fun `create task with add reminder uses scheduled block end for reminder`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()

        val result = coordinator.createTask(
            title = "Task with auto reminder",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            preferredTimePeriodId = "period-morning",
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = true,
        )

        val block = repository.getBlocks().single { it.taskId == result.taskId }
        val reminder = repository.getReminders().single { it.linkedTaskId == result.taskId }
        assertEquals(block.endAt, reminder.dueAt)
    }

    @Test
    fun `create no deadline task with reminder uses scheduled block start for reminder`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val syntheticDueAt = now().plus(365, java.time.temporal.ChronoUnit.DAYS)

        val result = coordinator.createTask(
            title = "Backlog reminder",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = syntheticDueAt,
            preferredTimePeriodId = "period-morning",
            hasDeadline = false,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = true,
        )

        val block = repository.getBlocks().single { it.taskId == result.taskId }
        val reminder = repository.getReminders().single { it.linkedTaskId == result.taskId }
        val task = repository.getTasks().single { it.id == result.taskId }
        assertFalse(task.hasDeadline)
        assertEquals(block.startAt, reminder.dueAt)
    }

    @Test
    fun `create continuation task stores parent and dependency mode`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val parent = task(
            id = "parent-task",
            dueAt = now().plusSeconds(60L * 60L * 24L),
            recurrenceRule = RecurrenceRule(),
        )
        repository.upsertTask(parent)

        val result = coordinator.createTask(
            title = "Child task",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = now().plusSeconds(60L * 60L * 48L),
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            continuationParentTaskId = parent.id,
            continuationMode = TaskContinuationMode.AFTER_PARENT_SCHEDULED_END,
        )

        val createdTask = repository.getTasks().single { it.id == result.taskId }
        assertEquals(parent.id, createdTask.continuationParentTaskId)
        assertEquals(TaskContinuationMode.AFTER_PARENT_SCHEDULED_END, createdTask.continuationMode)
    }

    @Test
    fun `create task stores task overlap policy`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)

        val result = coordinator.createTask(
            title = "Overlap-friendly task",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = now().plusSeconds(60L * 60L * 48L),
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )

        val createdTask = repository.getTasks().single { it.id == result.taskId }
        assertEquals(TaskOverlapPolicy.ALLOW, createdTask.overlapPolicy)
    }

    @Test
    fun `saving timeframe stores it in repository`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)

        val result = coordinator.saveTimeframe(
            name = "Exam week",
            startDate = LocalDate.of(2026, 5, 27),
            endDate = LocalDate.of(2026, 5, 31),
            colorHex = "#F4B6D2",
        )

        assertTrue(result.saved)
        val stored = repository.getTimeframes().single()
        assertEquals("Exam week", stored.name)
        assertEquals(LocalDate.of(2026, 5, 27), stored.startDate)
        assertEquals(LocalDate.of(2026, 5, 31), stored.endDate)
    }

    @Test
    fun `saving sixth overlapping timeframe is rejected`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        repeat(5) { index ->
            repository.upsertTimeframe(
                Timeframe(
                    id = "tf-$index",
                    name = "Timeframe $index",
                    startDate = LocalDate.of(2026, 5, 27),
                    endDate = LocalDate.of(2026, 5, 31),
                    colorHex = "#F4B6D2",
                    createdAt = now().plusSeconds(index.toLong()),
                    updatedAt = now().plusSeconds(index.toLong()),
                ),
            )
        }

        val result = coordinator.saveTimeframe(
            name = "Too many",
            startDate = LocalDate.of(2026, 5, 29),
            endDate = LocalDate.of(2026, 5, 30),
            colorHex = "#88D1FF",
        )

        assertFalse(result.saved)
        assertEquals("You can stack up to 5 overlapping timeframes.", result.errorMessage)
        assertEquals(5, repository.getTimeframes().size)
    }

    @Test
    fun `fixed exact task outside timeframe is rejected`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val timeframe = Timeframe(
            id = "tf-exam",
            name = "Exam week",
            startDate = LocalDate.of(2026, 5, 27),
            endDate = LocalDate.of(2026, 5, 31),
            colorHex = "#F4B6D2",
            createdAt = now(),
            updatedAt = now(),
        )
        repository.upsertTimeframe(timeframe)

        val result = coordinator.createTask(
            title = "Fixed outside timeframe",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = LocalDate.of(2026, 6, 1).atTime(10, 0).atZone(zone).toInstant(),
            preferredTimePeriodId = null,
            timeframeId = timeframe.id,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = LocalDate.of(2026, 6, 1).atTime(9, 0).atZone(zone).toInstant(),
            fixedEndAt = LocalDate.of(2026, 6, 1).atTime(10, 0).atZone(zone).toInstant(),
        )

        assertFalse(result.scheduled)
        assertEquals("This fixed time falls outside the selected timeframe.", result.reason)
    }

    @Test
    fun `can create a new linked reminder after completing the previous one`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = now().plusSeconds(3600)
        val task = task("task-with-reminder", dueAt, RecurrenceRule())
        repository.upsertTask(task)

        val firstReminderId = coordinator.createReminderForTask(task.id)
        coordinator.completeReminder(firstReminderId!!)
        val secondReminderId = coordinator.createReminderForTask(task.id)

        val reminders = repository.getReminders().sortedBy { it.createdAt }
        assertEquals(2, reminders.size)
        assertEquals(ReminderStatus.COMPLETED, reminders.first().status)
        assertEquals(ReminderStatus.ACTIVE, reminders.last().status)
        assertEquals(secondReminderId, reminders.last().id)
    }

    @Test
    fun `dismissing a reminder removes it`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val reminderId = coordinator.createReminder(
            title = "Dismiss me",
            description = "Description",
            dueAt = now().plusSeconds(1800),
        )

        coordinator.dismissReminder(reminderId)

        assertTrue(repository.getReminders().none { it.id == reminderId })
    }

    @Test
    fun `creating daily recurring task materializes upcoming occurrences`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val firstDueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()

        coordinator.createTask(
            title = "Daily focus",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstDueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            estimatedMinutes = 60,
            addReminder = false,
        )

        val createdTasks = repository.getTasks().sortedBy { it.dueAt }
        assertTrue(createdTasks.size > 1)
        assertTrue(createdTasks.all { it.recurrenceRule.type == RecurrenceType.DAILY })
        val seriesId = createdTasks.first().recurrenceSeriesId
        assertTrue(seriesId != null)
        assertTrue(createdTasks.all { it.recurrenceSeriesId == seriesId })
        assertEquals(firstDueAt.atZone(zone).toLocalDate(), createdTasks.first().dueAt.atZone(zone).toLocalDate())
        assertEquals(
            createdTasks.first().dueAt.atZone(zone).toLocalDate().plusDays(1),
            createdTasks[1].dueAt.atZone(zone).toLocalDate(),
        )
        val weekendTasks = createdTasks.filter {
            val day = it.dueAt.atZone(zone).dayOfWeek
            day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
        }
        assertTrue(
            createdTasks.isNotEmpty() && createdTasks.all { task ->
                repository.getBlocks().any { block ->
                    block.taskId == task.id &&
                        block.startAt.atZone(zone).toLocalDate() == task.dueAt.atZone(zone).toLocalDate()
                }
            },
        )
        assertTrue(weekendTasks.isNotEmpty())
        assertTrue(repository.getBlocks().isNotEmpty())
    }

    @Test
    fun `creating weekly recurring task respects selected weekdays`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val startDate = now().atZone(zone).toLocalDate().plusDays(1)
        val firstDueAt = startDate.atTime(17, 0).atZone(zone).toInstant()

        coordinator.createTask(
            title = "Weekly review",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstDueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKLY, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            estimatedMinutes = 60,
            addReminder = false,
        )

        val createdTasks = repository.getTasks().sortedBy { it.dueAt }
        assertTrue(createdTasks.size > 1)
        val seriesId = createdTasks.first().recurrenceSeriesId
        assertTrue(seriesId != null)
        assertTrue(createdTasks.all { it.recurrenceSeriesId == seriesId })
        assertTrue(createdTasks.all { it.dueAt.atZone(zone).dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY) })
        val weeklyBlocks = repository.getBlocks().filter { block -> createdTasks.any { it.id == block.taskId } }
        assertTrue(weeklyBlocks.isNotEmpty())
        assertTrue(
            weeklyBlocks.all { block ->
                block.startAt.atZone(zone).dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
            },
        )
    }

    @Test
    fun `creating recurring task with an until date stops materialization at that date`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val firstDueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()
        val until = firstDueAt.plus(2, java.time.temporal.ChronoUnit.DAYS)

        coordinator.createTask(
            title = "Finite daily task",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstDueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(
                type = RecurrenceType.DAILY,
                until = until,
            ),
            estimatedMinutes = 60,
            addReminder = false,
        )

        val createdTasks = repository.getTasks().sortedBy { it.dueAt }
        assertEquals(3, createdTasks.size)
        assertEquals(firstDueAt, createdTasks.first().dueAt)
        assertEquals(until, createdTasks.last().dueAt)
        assertTrue(createdTasks.none { it.dueAt.isAfter(until) })
    }

    @Test
    fun `creating daily recurring task with interval skips intervening days`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val firstDueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()

        coordinator.createTask(
            title = "Every other day",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstDueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(
                type = RecurrenceType.DAILY,
                interval = 2,
            ),
            estimatedMinutes = 60,
            addReminder = false,
        )

        val createdTasks = repository.getTasks().sortedBy { it.dueAt }
        assertTrue(createdTasks.size > 2)
        assertEquals(
            createdTasks.first().dueAt.atZone(zone).toLocalDate().plusDays(2),
            createdTasks[1].dueAt.atZone(zone).toLocalDate(),
        )
    }

    @Test
    fun `creating monthly recurring task with occurrence limit materializes capped series`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val firstDueAt = LocalDate.of(2026, 5, 31).atTime(17, 0).atZone(zone).toInstant()

        coordinator.createTask(
            title = "Monthly close",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstDueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(
                type = RecurrenceType.MONTHLY,
                interval = 1,
                endMode = RecurrenceEndMode.AFTER_OCCURRENCES,
                occurrenceCount = 3,
            ),
            estimatedMinutes = 60,
            addReminder = false,
        )

        val createdTasks = repository.getTasks().sortedBy { it.dueAt }
        assertEquals(3, createdTasks.size)
        assertEquals(LocalDate.of(2026, 5, 31), createdTasks[0].dueAt.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026, 6, 30), createdTasks[1].dueAt.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026, 7, 31), createdTasks[2].dueAt.atZone(zone).toLocalDate())
    }

    @Test
    fun `creating weekly recurring task with interval skips off weeks`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val firstDueAt = LocalDate.of(2026, 6, 1).atTime(17, 0).atZone(zone).toInstant()

        coordinator.createTask(
            title = "Biweekly sync",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstDueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(
                type = RecurrenceType.WEEKLY,
                interval = 2,
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            ),
            estimatedMinutes = 60,
            addReminder = false,
        )

        val createdTasks = repository.getTasks().sortedBy { it.dueAt }
        assertTrue(createdTasks.size > 2)
        assertEquals(LocalDate.of(2026, 6, 1), createdTasks[0].dueAt.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026, 6, 15), createdTasks[1].dueAt.atZone(zone).toLocalDate())
        assertEquals(LocalDate.of(2026, 6, 29), createdTasks[2].dueAt.atZone(zone).toLocalDate())
    }

    @Test
    fun `weekly recurring task with interval advances by selected number of weeks when completed`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = LocalDate.of(2026, 6, 1).atTime(17, 0).atZone(zone).toInstant()
        val task = task(
            id = "biweekly",
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule(
                type = RecurrenceType.WEEKLY,
                interval = 2,
                daysOfWeek = setOf(DayOfWeek.MONDAY),
            ),
        )
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(block(task.id, "block-biweekly", dueAt.minusSeconds(3600), dueAt)),
        )

        coordinator.markBlockDone("block-biweekly", task.id, 60)

        val updated = repository.getTasks().single()
        assertEquals(LocalDate.of(2026, 6, 15), updated.dueAt.atZone(zone).toLocalDate())
    }

    @Test
    fun `creating fixed exact task creates locked manual block at exact time`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val exactStart = now().atZone(zone).toLocalDate().plusDays(1).atTime(14, 0).atZone(zone).toInstant()
        val exactEnd = now().atZone(zone).toLocalDate().plusDays(1).atTime(15, 30).atZone(zone).toInstant()

        val result = coordinator.createTask(
            title = "Pinned call",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = exactEnd,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 90,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = exactStart,
            fixedEndAt = exactEnd,
        )

        assertTrue(result.scheduled)
        val block = repository.getBlocks().single { it.taskId == result.taskId }
        assertEquals(exactStart, block.startAt)
        assertEquals(exactEnd, block.endAt)
        assertEquals(BlockSource.MANUAL, block.source)
        assertEquals(BlockLockState.LOCKED, block.lockState)
    }

    @Test
    fun `creating fixed exact task reports overlap with another fixed task`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val existingStart = now().atZone(zone).toLocalDate().plusDays(1).atTime(12, 0).atZone(zone).toInstant()
        val existingEnd = now().atZone(zone).toLocalDate().plusDays(1).atTime(13, 0).atZone(zone).toInstant()
        val existingTask = task(
            id = "existing-fixed",
            dueAt = existingEnd,
            recurrenceRule = RecurrenceRule(),
        ).copy(
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = existingStart,
            fixedEndAt = existingEnd,
        )
        repository.upsertTask(existingTask)
        repository.replaceFlexibleBlocks(
            existingTask.id,
            listOf(
                block(existingTask.id, "existing-fixed-block", existingStart, existingEnd).copy(
                    source = BlockSource.MANUAL,
                    lockState = BlockLockState.LOCKED,
                ),
            ),
        )
        val exactStart = now().atZone(zone).toLocalDate().plusDays(1).atTime(12, 15).atZone(zone).toInstant()
        val exactEnd = now().atZone(zone).toLocalDate().plusDays(1).atTime(13, 0).atZone(zone).toInstant()

        val result = coordinator.createTask(
            title = "Lunch overlap",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = exactEnd,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 45,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = exactStart,
            fixedEndAt = exactEnd,
        )

        assertFalse(result.scheduled)
        assertEquals("This fixed time is blocked by another task or calendar event.", result.reason)
        assertTrue(repository.getTasks().none { it.id == result.taskId })
        assertTrue(repository.getBlocks().none { it.taskId == result.taskId })
    }

    @Test
    fun `creating fixed exact task can schedule outside legacy productive hours`() = runTest {
        val repository = FakePlannerRepository(periods = mutableListOf())
        val coordinator = coordinator(repository)
        val exactStart = now().atZone(zone).toLocalDate().plusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val exactEnd = now().atZone(zone).toLocalDate().plusDays(1).atTime(18, 30).atZone(zone).toInstant()

        val result = coordinator.createTask(
            title = "After-hours call",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = exactEnd,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 30,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = exactStart,
            fixedEndAt = exactEnd,
        )

        assertTrue(result.scheduled)
        val block = repository.getBlocks().single { it.taskId == result.taskId }
        assertEquals(exactStart, block.startAt)
        assertEquals(exactEnd, block.endAt)
    }

    @Test
    fun `creating fixed day task only schedules on the chosen day`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val chosenDate = now().atZone(zone).toLocalDate().plusDays(2)
        val dueAt = chosenDate.atTime(17, 0).atZone(zone).toInstant()

        val result = coordinator.createTask(
            title = "Same day errand",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_DAY,
        )

        assertTrue(result.scheduled)
        assertTrue(
            repository.getBlocks()
                .filter { it.taskId == result.taskId }
                .all { it.startAt.atZone(zone).toLocalDate() == chosenDate },
        )
    }

    @Test
    fun `creating fixed day task can use the selected date without daily flow windows`() = runTest {
        val repository = FakePlannerRepository(periods = mutableListOf())
        val coordinator = coordinator(repository)
        val chosenDate = now().atZone(zone).toLocalDate().plusDays(1)
        val dueAt = chosenDate.atTime(17, 0).atZone(zone).toInstant()

        val result = coordinator.createTask(
            title = "Fixed-day impossible",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            preferredTimePeriodId = "period-tiny",
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_DAY,
        )

        assertTrue(result.scheduled)
        assertTrue(
            repository.getBlocks()
                .filter { it.taskId == result.taskId }
                .all { it.startAt.atZone(zone).toLocalDate() == chosenDate },
        )
    }

    @Test
    fun `creating a second long task keeps the original intact and uses later free time`() = runTest {
        val repository = FakePlannerRepository(
            periods = mutableListOf(
                TimePeriod("period-morning", "Morning", LocalTime.of(8, 0), LocalTime.of(12, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 0),
                TimePeriod("period-afternoon", "Afternoon", LocalTime.of(14, 0), LocalTime.of(16, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 1),
            ),
        )
        val coordinator = coordinator(repository)
        val dueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()

        val originalTaskId = coordinator.createTask(
            title = "Original four hour task",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            preferredTimePeriodId = "period-morning",
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 240,
            addReminder = false,
        ).taskId

        val originalBlocksBefore = repository.getBlocks().filter { it.taskId == originalTaskId }
        assertEquals(
            240,
            originalBlocksBefore.sumOf {
                java.time.Duration.between(it.startAt, it.endAt).toMinutes().toInt()
            },
        )

        val secondTaskResult = coordinator.createTask(
            title = "Second four hour task",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            preferredTimePeriodId = "period-morning",
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 240,
            addReminder = false,
        )

        val originalBlocksAfter = repository.getBlocks().filter { it.taskId == originalTaskId }

        assertEquals(
            240,
            originalBlocksAfter.sumOf {
                java.time.Duration.between(it.startAt, it.endAt).toMinutes().toInt()
            },
        )
        assertTrue(secondTaskResult.scheduled)
        assertTrue(repository.getBlocks().any { it.taskId == secondTaskResult.taskId })
    }

    @Test
    fun `full rebuild preserves an existing long task before placing a conflicting new task`() = runTest {
        val repository = FakePlannerRepository(
            periods = mutableListOf(
                TimePeriod("period-morning", "Morning", LocalTime.of(8, 0), LocalTime.of(12, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 0),
                TimePeriod("period-afternoon", "Afternoon", LocalTime.of(14, 0), LocalTime.of(16, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 1),
            ),
        )
        val coordinator = coordinator(repository)
        val dueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()

        val originalTask = task(
            id = "original",
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule(),
            preferredTimePeriodId = "period-morning",
            estimatedMinutes = 240,
        )
        val conflictingTask = task(
            id = "conflict",
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule(),
            preferredTimePeriodId = "period-morning",
            estimatedMinutes = 240,
        )

        repository.upsertTask(originalTask)
        repository.replaceFlexibleBlocks(
            originalTask.id,
            listOf(
                block(
                    originalTask.id,
                    "original-block",
                    now().atZone(zone).toLocalDate().plusDays(1).atTime(8, 0).atZone(zone).toInstant(),
                    now().atZone(zone).toLocalDate().plusDays(1).atTime(12, 0).atZone(zone).toInstant(),
                ),
            ),
        )
        repository.upsertTask(conflictingTask)

        coordinator.rebuildSchedule()

        val originalBlocks = repository.getBlocks().filter { it.taskId == originalTask.id }
        assertEquals(1, originalBlocks.size)
        assertEquals(240, java.time.Duration.between(originalBlocks.single().startAt, originalBlocks.single().endAt).toMinutes().toInt())
        assertEquals(LocalTime.of(8, 0), originalBlocks.single().startAt.atZone(zone).toLocalTime())
        assertTrue(repository.getBlocks().any { it.taskId == conflictingTask.id })
    }

    @Test
    fun `rescheduling clears original pending block before rebuilding`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val originalDueAt = now().plusSeconds(60L * 60L * 24L)
        val newDueAt = now().plusSeconds(60L * 60L * 48L)
        val task = task("move-me", originalDueAt, RecurrenceRule())
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(
                block(task.id, "original-block", now().plusSeconds(3600), now().plusSeconds(7200))
                    .copy(lockState = BlockLockState.LOCKED),
            ),
        )

        coordinator.rescheduleToDueDate(task.id, newDueAt)

        assertTrue(repository.getBlocks().none { it.id == "original-block" })
        assertTrue(repository.getBlocks().any { it.taskId == task.id && it.id != "original-block" })
    }

    @Test
    fun `rescheduling with task draft updates same task and linked reminder timing`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val originalStart = now().atZone(zone).toLocalDate().plusDays(1).atTime(9, 0).atZone(zone).toInstant()
        val originalEnd = now().atZone(zone).toLocalDate().plusDays(1).atTime(10, 0).atZone(zone).toInstant()
        val task = task(
            id = "reschedule-me",
            dueAt = originalEnd,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
        ).copy(
            title = "Review notes",
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = originalStart,
            fixedEndAt = originalEnd,
        )
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(
                block(task.id, "original-exact", originalStart, originalEnd).copy(
                    source = BlockSource.MANUAL,
                    lockState = BlockLockState.LOCKED,
                ),
            ),
        )
        coordinator.createReminderForTask(task.id)

        val newStart = now().atZone(zone).toLocalDate().plusDays(2).atTime(13, 0).atZone(zone).toInstant()
        val newEnd = now().atZone(zone).toLocalDate().plusDays(2).atTime(14, 0).atZone(zone).toInstant()

        val result = coordinator.rescheduleTaskWithUpdate(
            taskId = task.id,
            title = "Review notes Rescheduled",
            description = task.description,
            priority = task.priority,
            dueAt = newEnd,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = newStart,
            fixedEndAt = newEnd,
        )

        assertTrue(result.scheduled)
        val updatedTask = repository.getTasks().single { it.id == task.id }
        val updatedBlock = repository.getBlocks().single { it.taskId == task.id }
        val updatedReminder = repository.getReminders().single { it.linkedTaskId == task.id }
        assertEquals("Review notes Rescheduled", updatedTask.title)
        assertEquals(newEnd, updatedTask.dueAt)
        assertEquals(newStart, updatedTask.fixedStartAt)
        assertEquals(newEnd, updatedTask.fixedEndAt)
        assertEquals(newStart, updatedBlock.startAt)
        assertEquals(newEnd, updatedBlock.endAt)
        assertEquals(newEnd, updatedReminder.dueAt)
    }

    @Test
    fun `rescheduling with overlapping fixed exact time fails and restores original task state`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val originalStart = now().atZone(zone).toLocalDate().plusDays(1).atTime(9, 0).atZone(zone).toInstant()
        val originalEnd = now().atZone(zone).toLocalDate().plusDays(1).atTime(10, 0).atZone(zone).toInstant()
        val task = task(
            id = "reschedule-conflict",
            dueAt = originalEnd,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
        ).copy(
            title = "Review notes",
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = originalStart,
            fixedEndAt = originalEnd,
        )
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(
                block(task.id, "original-exact", originalStart, originalEnd).copy(
                    source = BlockSource.MANUAL,
                    lockState = BlockLockState.LOCKED,
                ),
            ),
        )
        coordinator.createReminderForTask(task.id)
        val conflictingAnchorStart = now().atZone(zone).toLocalDate().plusDays(2).atTime(12, 0).atZone(zone).toInstant()
        val conflictingAnchorEnd = now().atZone(zone).toLocalDate().plusDays(2).atTime(13, 0).atZone(zone).toInstant()
        val conflictingTask = task(
            id = "existing-conflict",
            dueAt = conflictingAnchorEnd,
            recurrenceRule = RecurrenceRule(),
        ).copy(
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = conflictingAnchorStart,
            fixedEndAt = conflictingAnchorEnd,
        )
        repository.upsertTask(conflictingTask)
        repository.replaceFlexibleBlocks(
            conflictingTask.id,
            listOf(
                block(conflictingTask.id, "existing-conflict-block", conflictingAnchorStart, conflictingAnchorEnd).copy(
                    source = BlockSource.MANUAL,
                    lockState = BlockLockState.LOCKED,
                ),
            ),
        )
        val conflictingStart = now().atZone(zone).toLocalDate().plusDays(2).atTime(12, 15).atZone(zone).toInstant()
        val conflictingEnd = now().atZone(zone).toLocalDate().plusDays(2).atTime(13, 0).atZone(zone).toInstant()

        val result = coordinator.rescheduleTaskWithUpdate(
            taskId = task.id,
            title = "Review notes Rescheduled",
            description = task.description,
            priority = task.priority,
            dueAt = conflictingEnd,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 45,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = conflictingStart,
            fixedEndAt = conflictingEnd,
        )

        assertFalse(result.scheduled)
        assertEquals("This fixed time is blocked by another task or calendar event.", result.reason)
        val restoredTask = repository.getTasks().single { it.id == task.id }
        val restoredBlock = repository.getBlocks().single { it.taskId == task.id }
        val restoredReminder = repository.getReminders().single { it.linkedTaskId == task.id }
        assertEquals("Review notes", restoredTask.title)
        assertEquals(originalStart, restoredTask.fixedStartAt)
        assertEquals(originalEnd, restoredTask.fixedEndAt)
        assertEquals(originalStart, restoredBlock.startAt)
        assertEquals(originalEnd, restoredBlock.endAt)
        assertEquals(originalEnd, restoredReminder.dueAt)
    }

    @Test
    fun `rescheduling fixed exact task can move outside legacy productive hours`() = runTest {
        val repository = FakePlannerRepository(periods = mutableListOf())
        val coordinator = coordinator(repository)
        val originalStart = now().atZone(zone).toLocalDate().plusDays(1).atTime(9, 0).atZone(zone).toInstant()
        val originalEnd = now().atZone(zone).toLocalDate().plusDays(1).atTime(10, 0).atZone(zone).toInstant()
        val task = task(
            id = "reschedule-after-hours",
            dueAt = originalEnd,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
        ).copy(
            title = "Reschedule after hours",
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = originalStart,
            fixedEndAt = originalEnd,
        )
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(
                block(task.id, "original-after-hours", originalStart, originalEnd).copy(
                    source = BlockSource.MANUAL,
                    lockState = BlockLockState.LOCKED,
                ),
            ),
        )
        coordinator.createReminderForTask(task.id)
        val afterHoursStart = now().atZone(zone).toLocalDate().plusDays(2).atTime(18, 0).atZone(zone).toInstant()
        val afterHoursEnd = now().atZone(zone).toLocalDate().plusDays(2).atTime(18, 30).atZone(zone).toInstant()

        val result = coordinator.rescheduleTaskWithUpdate(
            taskId = task.id,
            title = "Reschedule after hours",
            description = task.description,
            priority = task.priority,
            dueAt = afterHoursEnd,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 30,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = afterHoursStart,
            fixedEndAt = afterHoursEnd,
        )

        assertTrue(result.scheduled)
        val updatedTask = repository.getTasks().single { it.id == task.id }
        val updatedBlock = repository.getBlocks().single { it.taskId == task.id }
        val updatedReminder = repository.getReminders().single { it.linkedTaskId == task.id }
        assertEquals(afterHoursStart, updatedTask.fixedStartAt)
        assertEquals(afterHoursEnd, updatedTask.fixedEndAt)
        assertEquals(afterHoursStart, updatedBlock.startAt)
        assertEquals(afterHoursEnd, updatedBlock.endAt)
        assertEquals(afterHoursEnd, updatedReminder.dueAt)
    }

    @Test
    fun `editing one-time task updates its configuration and reminder`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val timeframe = Timeframe(
            id = "timeframe-evening",
            name = "Evening",
            startDate = now().atZone(zone).toLocalDate().plusDays(1),
            endDate = now().atZone(zone).toLocalDate().plusDays(3),
            colorHex = "#AABBCC",
        )
        repository.upsertTimeframe(timeframe)
        val dueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()
        val createdTaskId = coordinator.createTask(
            title = "Write draft",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = true,
        ).taskId

        val editedStart = now().atZone(zone).toLocalDate().plusDays(2).atTime(14, 0).atZone(zone).toInstant()
        val editedEnd = now().atZone(zone).toLocalDate().plusDays(2).atTime(15, 30).atZone(zone).toInstant()

        val result = coordinator.editTask(
            taskId = createdTaskId,
            title = "Write outline",
            description = "Trim scope",
            priority = TaskPriority.HIGH,
            dueAt = editedEnd,
            preferredTimePeriodId = null,
            timeframeId = timeframe.id,
            hasDeadline = true,
            continuationParentTaskId = null,
            continuationMode = null,
            overlapPolicy = TaskOverlapPolicy.DISALLOW,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 90,
            addReminder = true,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = editedStart,
            fixedEndAt = editedEnd,
        )

        assertTrue(result.scheduled)
        val updatedTask = repository.getTasks().single { it.id == createdTaskId }
        val updatedReminder = repository.getReminders().single { it.linkedTaskId == createdTaskId }
        val updatedBlock = repository.getBlocks().single { it.taskId == createdTaskId }
        assertEquals("Write outline", updatedTask.title)
        assertEquals("Trim scope", updatedTask.description)
        assertEquals(TaskPriority.HIGH, updatedTask.priority)
        assertEquals(timeframe.id, updatedTask.timeframeId)
        assertEquals(90, updatedTask.estimatedMinutes)
        assertEquals(editedStart, updatedTask.fixedStartAt)
        assertEquals(editedEnd, updatedTask.fixedEndAt)
        assertEquals(editedEnd, updatedTask.dueAt)
        assertEquals(editedStart, updatedBlock.startAt)
        assertEquals(editedEnd, updatedBlock.endAt)
        assertEquals(editedEnd, updatedReminder.dueAt)
    }

    @Test
    fun `editing recurring task fields applies from edited date forward`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val createdTaskId = coordinator.createTask(
            title = "Study",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(18, 0).atZone(zone).toInstant(),
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKLY, setOf(DayOfWeek.FRIDAY)),
            estimatedMinutes = 60,
            addReminder = false,
        ).taskId
        val occurrencesBeforeEdit = repository.getTasks()
            .filter { it.recurrenceSeriesId != null }
            .sortedBy { it.dueAt }
        val firstOccurrence = occurrencesBeforeEdit.first { it.id == createdTaskId }
        val editedOccurrence = occurrencesBeforeEdit[1]
        val originalSeriesId = editedOccurrence.recurrenceSeriesId

        val result = coordinator.editTask(
            taskId = editedOccurrence.id,
            title = "Study deep work",
            description = "Longer block",
            priority = TaskPriority.HIGH,
            dueAt = editedOccurrence.dueAt,
            preferredTimePeriodId = null,
            timeframeId = null,
            hasDeadline = true,
            continuationParentTaskId = null,
            continuationMode = null,
            overlapPolicy = TaskOverlapPolicy.DISALLOW,
            recurrenceRule = editedOccurrence.recurrenceRule,
            estimatedMinutes = 90,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FLEXIBLE,
        )

        assertTrue(result.scheduled)
        val occurrencesAfterEdit = repository.getTasks().sortedBy { it.dueAt }
        val preservedFirst = occurrencesAfterEdit.first { it.id == firstOccurrence.id }
        val updatedEdited = occurrencesAfterEdit.first { it.id == editedOccurrence.id }
        val futureOccurrence = occurrencesAfterEdit
            .filter { it.recurrenceSeriesId == originalSeriesId && it.dueAt.isAfter(updatedEdited.dueAt) }
            .first()
        assertEquals("Study", preservedFirst.title)
        assertEquals(60, preservedFirst.estimatedMinutes)
        assertEquals("Study deep work", updatedEdited.title)
        assertEquals(90, updatedEdited.estimatedMinutes)
        assertEquals("Study deep work", futureOccurrence.title)
        assertEquals(90, futureOccurrence.estimatedMinutes)
    }

    @Test
    fun `changing repeat rule splits recurring series from edited date`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        coordinator.createTask(
            title = "Dinner",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(19, 0).atZone(zone).toInstant(),
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKLY, setOf(DayOfWeek.FRIDAY)),
            estimatedMinutes = 30,
            addReminder = true,
        )
        val occurrencesBeforeEdit = repository.getTasks()
            .filter { it.recurrenceSeriesId != null }
            .sortedBy { it.dueAt }
        val firstOccurrence = occurrencesBeforeEdit.first()
        val editedOccurrence = occurrencesBeforeEdit[1]
        val originalSeriesId = editedOccurrence.recurrenceSeriesId

        val result = coordinator.editTask(
            taskId = editedOccurrence.id,
            title = "Dinner",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = editedOccurrence.dueAt,
            preferredTimePeriodId = null,
            timeframeId = null,
            hasDeadline = true,
            continuationParentTaskId = null,
            continuationMode = null,
            overlapPolicy = TaskOverlapPolicy.DISALLOW,
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            estimatedMinutes = 30,
            addReminder = true,
            schedulingMode = TaskSchedulingMode.FLEXIBLE,
        )

        assertTrue(result.scheduled)
        val occurrencesAfterEdit = repository.getTasks().sortedBy { it.dueAt }
        val preservedFirst = occurrencesAfterEdit.first { it.id == firstOccurrence.id }
        val updatedEdited = occurrencesAfterEdit.first { it.id == editedOccurrence.id }
        val newSeriesId = updatedEdited.recurrenceSeriesId
        val expectedNextDay = editedOccurrence.dueAt.atZone(zone).plusDays(1).toInstant()
        assertEquals(originalSeriesId, preservedFirst.recurrenceSeriesId)
        assertTrue(occurrencesAfterEdit.none { it.recurrenceSeriesId == originalSeriesId && it.dueAt.isAfter(firstOccurrence.dueAt) })
        assertTrue(newSeriesId != null && newSeriesId != originalSeriesId)
        assertEquals(RecurrenceType.DAILY, updatedEdited.recurrenceRule.type)
        assertTrue(occurrencesAfterEdit.any { it.recurrenceSeriesId == newSeriesId && it.dueAt == expectedNextDay })
        assertTrue(repository.getReminders().any { it.linkedTaskId == updatedEdited.id })
    }

    @Test
    fun `urgent reschedule preserves deadline unless a new deadline is supplied`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = now().plusSeconds(60L * 60L * 48L)
        val task = task("urgent-preserve-due", dueAt, RecurrenceRule())
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(block(task.id, "old-block", now().plusSeconds(3600), now().plusSeconds(7200))),
        )

        val success = coordinator.rescheduleUrgently(task.id)

        assertTrue(success)
        val updated = repository.getTasks().single()
        assertEquals(TaskPriority.URGENT, updated.priority)
        assertEquals(dueAt, updated.dueAt)
        assertTrue(repository.getBlocks().none { it.id == "old-block" })
        assertTrue(repository.getBlocks().any { it.taskId == task.id })
    }

    @Test
    fun `non urgent reschedule can use explicit deadline after user changes it`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val originalDueAt = now().plusSeconds(60L * 60L * 48L)
        val newDueAt = now().plusSeconds(60L * 60L * 96L)
        val task = task("nonurgent-new-due", originalDueAt, RecurrenceRule(), estimatedMinutes = 30)
            .copy(priority = TaskPriority.URGENT)
        repository.upsertTask(task)

        val success = coordinator.rescheduleNextAvailable(task.id, newDueAt)

        assertTrue(success)
        val updated = repository.getTasks().single()
        assertEquals(TaskPriority.MEDIUM, updated.priority)
        assertEquals(newDueAt, updated.dueAt)
        assertTrue(repository.getBlocks().any { it.taskId == task.id })
    }

    @Test
    fun `urgent reschedule rolls overdue deadline forward when no new deadline is supplied`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val overdueDueAt = now().minusSeconds(60L * 60L * 6L)
        val task = task("urgent-overdue", overdueDueAt, RecurrenceRule())
        repository.upsertTask(task)

        val success = coordinator.rescheduleUrgently(task.id)

        assertTrue(success)
        val updated = repository.getTasks().single()
        assertEquals(TaskPriority.URGENT, updated.priority)
        assertTrue(updated.dueAt.isAfter(now()))
        assertTrue(repository.getBlocks().any { it.taskId == task.id })
    }

    @Test
    fun `next available reschedule rolls overdue deadline forward when no new deadline is supplied`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val overdueDueAt = now().minusSeconds(60L * 60L * 30L)
        val task = task("next-available-overdue", overdueDueAt, RecurrenceRule())
            .copy(priority = TaskPriority.URGENT)
        repository.upsertTask(task)

        val success = coordinator.rescheduleNextAvailable(task.id)

        assertTrue(success)
        val updated = repository.getTasks().single()
        assertEquals(TaskPriority.MEDIUM, updated.priority)
        assertTrue(updated.dueAt.isAfter(now()))
        assertTrue(repository.getBlocks().any { it.taskId == task.id })
    }

    @Test
    fun `next available reschedule fails when no other slot exists before the deadline and keeps original block`() = runTest {
        val repository = FakePlannerRepository(
            periods = mutableListOf(
                TimePeriod("period-morning", "Morning", LocalTime.of(8, 0), LocalTime.of(12, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 0),
            ),
        )
        val coordinator = coordinator(repository)
        val day = now().atZone(zone).toLocalDate().plusDays(1)
        val dueAt = day.atTime(12, 0).atZone(zone).toInstant()
        val task = task("keep-slot", dueAt, RecurrenceRule(), preferredTimePeriodId = "period-morning", estimatedMinutes = 120)
        val originalBlock = block(
            task.id,
            "original-slot",
            day.atTime(8, 0).atZone(zone).toInstant(),
            day.atTime(10, 0).atZone(zone).toInstant(),
        )
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(task.id, listOf(originalBlock))
        repository.upsertTask(
            task("other-task", dueAt, RecurrenceRule(), preferredTimePeriodId = "period-morning", estimatedMinutes = 120),
        )
        repository.replaceFlexibleBlocks(
            "other-task",
            listOf(
                block(
                    "other-task",
                    "other-slot",
                    day.atTime(10, 0).atZone(zone).toInstant(),
                    day.atTime(12, 0).atZone(zone).toInstant(),
                ),
            ),
        )

        val success = coordinator.rescheduleNextAvailable(task.id)

        assertFalse(success)
        assertTrue(repository.getBlocks().any { it.id == "original-slot" })
    }

    @Test
    fun `urgent reschedule can move other tasks when no free alternative exists`() = runTest {
        val repository = FakePlannerRepository(
            periods = mutableListOf(
                TimePeriod("period-morning", "Morning", LocalTime.of(8, 0), LocalTime.of(12, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 0),
                TimePeriod("period-afternoon", "Afternoon", LocalTime.of(14, 0), LocalTime.of(16, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 1),
                TimePeriod("period-night", "Night", LocalTime.of(20, 0), LocalTime.of(22, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 2),
            ),
        )
        val coordinator = coordinator(repository)
        val day = now().atZone(zone).toLocalDate().plusDays(1)
        val dueAt = day.atTime(22, 0).atZone(zone).toInstant()
        val task = task("urgent-move", dueAt, RecurrenceRule(), preferredTimePeriodId = "period-morning", estimatedMinutes = 120)
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(
                block(
                    task.id,
                    "urgent-original-slot",
                    day.atTime(8, 0).atZone(zone).toInstant(),
                    day.atTime(10, 0).atZone(zone).toInstant(),
                ),
            ),
        )
        repository.upsertTask(
            task("other-fixed", dueAt, RecurrenceRule(), preferredTimePeriodId = "period-afternoon", estimatedMinutes = 120),
        )
        repository.replaceFlexibleBlocks(
            "other-fixed",
            listOf(
                block(
                    "other-fixed",
                    "other-afternoon-slot",
                    day.atTime(14, 0).atZone(zone).toInstant(),
                    day.atTime(16, 0).atZone(zone).toInstant(),
                ),
            ),
        )

        val success = coordinator.rescheduleUrgently(task.id)

        assertTrue(success)
        val movedTaskBlocks = repository.getBlocks().filter { it.taskId == task.id }
        assertTrue(movedTaskBlocks.isNotEmpty())
        assertTrue(movedTaskBlocks.none { it.id == "urgent-original-slot" })
        assertTrue(movedTaskBlocks.none { it.startAt == day.atTime(8, 0).atZone(zone).toInstant() })
    }

    @Test
    fun `marking whole task done clears pending blocks`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = now().plusSeconds(60L * 60L * 24L)
        val task = task("done-task", dueAt, RecurrenceRule())
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(block(task.id, "pending-block", now().plusSeconds(3600), now().plusSeconds(7200))),
        )

        coordinator.markTaskDone(task.id)

        assertEquals(TaskStatus.COMPLETED, repository.getTasks().single().status)
        assertTrue(repository.getBlocks().none { it.taskId == task.id })
    }

    @Test
    fun `marking recurring task done from task detail advances to next occurrence`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val firstDueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()
        val createdTaskId = coordinator.createTask(
            title = "Recurring detail",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstDueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            estimatedMinutes = 60,
            addReminder = false,
        ).taskId
        repository.replaceFlexibleBlocks(
            createdTaskId,
            listOf(block(createdTaskId, "pending-block", firstDueAt.minusSeconds(3600), firstDueAt)),
        )

        coordinator.markTaskDone(createdTaskId)

        val seriesTasks = repository.getTasks().sortedBy { it.dueAt }
        val completed = seriesTasks.first { it.id == createdTaskId }
        assertEquals(TaskStatus.COMPLETED, completed.status)
        assertEquals(0, completed.remainingMinutes)
        assertTrue(repository.getBlocks().none { it.id == "pending-block" })
        assertTrue(seriesTasks.any { it.id != createdTaskId && it.status == TaskStatus.ACTIVE })
        assertTrue(seriesTasks.size >= 2)
    }

    @Test
    fun `weekly flexible window tasks shift their window with each occurrence date`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val initialDate = LocalDate.of(2026, 5, 25)
        val firstWindowStart = ZonedDateTime.of(initialDate, LocalTime.of(18, 0), zone).toInstant()
        val firstWindowEnd = ZonedDateTime.of(initialDate, LocalTime.of(21, 0), zone).toInstant()

        coordinator.createTask(
            title = "Dinner",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstWindowEnd,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKLY, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
            estimatedMinutes = 30,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FLEXIBLE_WINDOW,
            fixedStartAt = firstWindowStart,
            fixedEndAt = firstWindowEnd,
        )

        val createdTasks = repository.getTasks()
            .filter { it.title == "Dinner" }
            .sortedBy { it.dueAt }

        assertTrue(createdTasks.size >= 2)
        val mondayTask = createdTasks.first()
        val wednesdayTask = createdTasks.first { it.dueAt.atZone(zone).toLocalDate() == initialDate.plusDays(2) }
        assertEquals(LocalTime.of(18, 0), mondayTask.fixedStartAt?.atZone(zone)?.toLocalTime())
        assertEquals(LocalTime.of(21, 0), mondayTask.fixedEndAt?.atZone(zone)?.toLocalTime())
        assertEquals(initialDate.plusDays(2), wednesdayTask.fixedStartAt?.atZone(zone)?.toLocalDate())
        assertEquals(LocalTime.of(18, 0), wednesdayTask.fixedStartAt?.atZone(zone)?.toLocalTime())
        assertEquals(LocalTime.of(21, 0), wednesdayTask.fixedEndAt?.atZone(zone)?.toLocalTime())
    }

    @Test
    fun `marking legacy recurring task done does not reschedule before its next occurrence date`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val dueAt = Instant.parse("2026-05-25T15:12:00Z")
        val task = task(
            id = "legacy-weekly",
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKLY, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            estimatedMinutes = 90,
        )
        repository.upsertTask(task)
        repository.replaceFlexibleBlocks(
            task.id,
            listOf(block(task.id, "legacy-weekly-block", Instant.parse("2026-05-22T18:00:00Z"), Instant.parse("2026-05-22T19:30:00Z"))),
        )

        coordinator.markTaskDone(task.id)

        val updated = repository.getTasks().single { it.id == task.id }
        assertEquals(Instant.parse("2026-05-28T15:12:00Z"), updated.dueAt)
        val rescheduledBlocks = repository.getBlocks().filter { it.taskId == task.id }
        assertTrue(rescheduledBlocks.isNotEmpty())
        assertTrue(
            rescheduledBlocks.all { block ->
                !block.startAt.atZone(zone).toLocalDate().isBefore(updated.dueAt.atZone(zone).toLocalDate())
            },
        )
    }

    @Test
    fun `marking all recurring tasks done completes entire series`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val firstDueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(17, 0).atZone(zone).toInstant()
        val createdTaskId = coordinator.createTask(
            title = "Recurring all",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = firstDueAt,
            preferredTimePeriodId = "period-afternoon",
            recurrenceRule = RecurrenceRule(RecurrenceType.DAILY),
            estimatedMinutes = 60,
            addReminder = false,
        ).taskId

        coordinator.markRecurringSeriesDone(createdTaskId)

        val seriesTasks = repository.getTasks()
        assertTrue(seriesTasks.isNotEmpty())
        assertTrue(seriesTasks.all { it.status == TaskStatus.COMPLETED })
        assertTrue(repository.getBlocks().none { block -> seriesTasks.any { it.id == block.taskId } })
    }

    @Test
    fun `creating a follow up task completes the original and schedules the new task`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val sourceDueAt = now().plusSeconds(60L * 60L * 24L)
        val followUpDueAt = now().plusSeconds(60L * 60L * 72L)
        val sourceTask = task(
            id = "source-task",
            dueAt = sourceDueAt,
            recurrenceRule = RecurrenceRule(),
            preferredTimePeriodId = "period-afternoon",
            estimatedMinutes = 90,
        ).copy(
            title = "Review draft",
            description = "Carry forward notes",
            priority = TaskPriority.URGENT,
        )
        repository.upsertTask(sourceTask)
        repository.replaceFlexibleBlocks(
            sourceTask.id,
            listOf(block(sourceTask.id, "source-block", now().plusSeconds(3600), now().plusSeconds(7200))),
        )

        val followUpResult = coordinator.createFollowUpTask(
            sourceTaskId = sourceTask.id,
            title = "Review draft Follow up",
            description = sourceTask.description,
            priority = sourceTask.priority,
            dueAt = followUpDueAt,
            preferredTimePeriodId = sourceTask.preferredTimePeriodId,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = sourceTask.estimatedMinutes,
            addReminder = false,
        )
        val followUpTaskId = followUpResult!!.taskId

        val tasks = repository.getTasks()
        val original = tasks.first { it.id == sourceTask.id }
        val followUp = tasks.first { it.id == followUpTaskId }
        assertEquals(TaskStatus.COMPLETED, original.status)
        assertEquals(0, original.remainingMinutes)
        assertEquals("Review draft Follow up", followUp.title)
        assertEquals(sourceTask.description, followUp.description)
        assertEquals(sourceTask.priority, followUp.priority)
        assertEquals(sourceTask.preferredTimePeriodId, followUp.preferredTimePeriodId)
        assertEquals(sourceTask.estimatedMinutes, followUp.estimatedMinutes)
        assertEquals(followUpDueAt, followUp.dueAt)
        assertTrue(repository.getBlocks().none { it.id == "source-block" })
        assertTrue(repository.getBlocks().any { it.taskId == followUpTaskId })
    }

    @Test
    fun `create task returns unscheduled when fixed tasks block all time before the deadline`() = runTest {
        val repository = FakePlannerRepository(periods = mutableListOf())
        val coordinator = coordinator(repository)
        val dueAt = now().atZone(zone).toLocalDate().plusDays(1).atTime(8, 15).atZone(zone).toInstant()
        val blockerStart = now()
        val blockerEnd = dueAt
        val blocker = task(
            id = "blocking-task",
            dueAt = blockerEnd,
            recurrenceRule = RecurrenceRule(),
        ).copy(
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = blockerStart,
            fixedEndAt = blockerEnd,
        )
        repository.upsertTask(blocker)
        repository.replaceFlexibleBlocks(
            blocker.id,
            listOf(
                block(blocker.id, "blocking-task-block", blockerStart, blockerEnd).copy(
                    source = BlockSource.MANUAL,
                    lockState = BlockLockState.LOCKED,
                ),
            ),
        )

        val result = coordinator.createTask(
            title = "Impossible task",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = dueAt,
            preferredTimePeriodId = "period-tiny",
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
        )

        assertTrue(!result.scheduled)
        assertEquals("No valid slot is available before the deadline.", result.reason)
        assertTrue(repository.getTasks().none { it.id == result.taskId })
        assertTrue(repository.getBlocks().none { it.taskId == result.taskId })
    }

    @Test
    fun `delete task removes blocks reminders and scheduling issues`() = runTest {
        val repository = FakePlannerRepository(periods = mutableListOf())
        val coordinator = coordinator(repository)
        val taskId = "test-task"
        val reminderId = "test-reminder"
        repository.upsertTask(
            task(id = taskId, dueAt = now().plusSeconds(7200), recurrenceRule = RecurrenceRule())
        )
        repository.upsertReminder(
            Reminder(id = reminderId, title = "Test", dueAt = now().plusSeconds(3600), linkedTaskId = taskId)
        )
        repository.replaceSchedulingIssuesForTask(
            taskId,
            listOf(
                SchedulingIssue(
                    taskId = taskId,
                    type = SchedulingIssueType.PARTIAL,
                    unscheduledMinutes = 30,
                    reason = "test",
                )
            )
        )
        repository.replaceFlexibleBlocks(
            taskId,
            listOf(
                block(taskId, "test-block", now(), now().plusSeconds(3600))
            )
        )

        coordinator.deleteTask(taskId)

        assertTrue(repository.getTasks().none { it.id == taskId })
        assertTrue(repository.getBlocks().none { it.taskId == taskId })
        assertTrue(repository.getReminders().none { it.linkedTaskId == taskId })
        assertTrue(repository.getSchedulingIssues().none { it.taskId == taskId })
    }

    @Test
    fun `delete task cleanup after failed creation removes issues and reminders`() = runTest {
        val repository = FakePlannerRepository(periods = mutableListOf())
        val coordinator = coordinator(repository)
        val blockerStart = now()
        val blockerEnd = now().plusSeconds(86400)
        val blocker = task(
            id = "blocking-task",
            dueAt = blockerEnd,
            recurrenceRule = RecurrenceRule(),
        ).copy(
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = blockerStart,
            fixedEndAt = blockerEnd,
        )
        repository.upsertTask(blocker)
        repository.replaceFlexibleBlocks(
            blocker.id,
            listOf(
                block(blocker.id, "blocker-block", blockerStart, blockerEnd).copy(
                    source = BlockSource.MANUAL,
                    lockState = BlockLockState.LOCKED,
                )
            )
        )

        val result = coordinator.createTask(
            title = "Will fail",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = blockerEnd,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = true,
        )

        assertTrue(!result.scheduled)
        assertTrue(repository.getTasks().none { it.id == result.taskId })
        assertTrue(repository.getBlocks().none { it.taskId == result.taskId })
        assertTrue(repository.getSchedulingIssues().none { it.taskId == result.taskId })
        assertTrue(repository.getReminders().none { it.linkedTaskId == result.taskId })
    }

    @Test
    fun `exact task with DISALLOW overlap blocks another exact task at same time`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val startAt = ZonedDateTime.of(LocalDate.of(2026, 6, 1), LocalTime.of(14, 0), zone).toInstant()
        val endAt = ZonedDateTime.of(LocalDate.of(2026, 6, 1), LocalTime.of(15, 0), zone).toInstant()
        val result1 = coordinator.createTask(
            title = "Task A",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = endAt,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = startAt,
            fixedEndAt = endAt,
            overlapPolicy = TaskOverlapPolicy.DISALLOW,
        )
        assertTrue("First exact DISALLOW task should schedule", result1.scheduled)

        val result2 = coordinator.createTask(
            title = "Task B",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = endAt,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = startAt,
            fixedEndAt = endAt,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )
        assertFalse("Second exact task should fail when first disallows overlap", result2.scheduled)
    }

    @Test
    fun `non overlapping exact tasks at different times both schedule`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val startA = ZonedDateTime.of(LocalDate.of(2026, 6, 1), LocalTime.of(9, 0), zone).toInstant()
        val endA = ZonedDateTime.of(LocalDate.of(2026, 6, 1), LocalTime.of(10, 0), zone).toInstant()
        val startB = ZonedDateTime.of(LocalDate.of(2026, 6, 1), LocalTime.of(10, 30), zone).toInstant()
        val endB = ZonedDateTime.of(LocalDate.of(2026, 6, 1), LocalTime.of(11, 30), zone).toInstant()

        val result1 = coordinator.createTask(
            title = "Task A",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = endA,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = startA,
            fixedEndAt = endA,
        )
        assertTrue(result1.scheduled)

        val result2 = coordinator.createTask(
            title = "Task B",
            description = "",
            priority = TaskPriority.MEDIUM,
            dueAt = endB,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 60,
            addReminder = false,
            schedulingMode = TaskSchedulingMode.FIXED_EXACT,
            fixedStartAt = startB,
            fixedEndAt = endB,
        )
        assertTrue(result2.scheduled)
    }

    @Test
    fun `creating flexible task triggers full rebuild so other tasks can move`() = runTest {
        val repository = FakePlannerRepository()
        val coordinator = coordinator(repository)
        val date = LocalDate.of(2026, 6, 1)
        val dueAt = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant()

        // Create a low-priority task that takes the morning
        val existing = task(
            id = "low-priority",
            dueAt = dueAt,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 240,
        )
        repository.upsertTask(existing)
        coordinator.rebuildSchedule()
        val lowBlocks = repository.getBlocks().filter { it.taskId == "low-priority" }
        assertTrue("Low priority task should have at least one block before new task is created", lowBlocks.isNotEmpty())

        // Create an urgent task — with full rebuild, it should push the low task
        val result = coordinator.createTask(
            title = "Urgent",
            description = "",
            priority = TaskPriority.URGENT,
            dueAt = dueAt,
            preferredTimePeriodId = null,
            recurrenceRule = RecurrenceRule(),
            estimatedMinutes = 120,
            addReminder = false,
        )
        assertTrue("Urgent task should schedule alongside existing task", result.scheduled)
        val urgentBlocks = repository.getBlocks().filter { it.taskId == result.taskId }
        assertTrue("Urgent task should have at least one block", urgentBlocks.isNotEmpty())
    }

    private fun coordinator(repository: PlannerRepository) = PlannerCoordinator(
        repository = repository,
        scheduler = SchedulerEngine(),
        calendarGateway = NoOpGoogleCalendarGateway(),
        clock = clock,
    )

    private fun now(): Instant = clock.instant()

    private fun task(
        id: String,
        dueAt: Instant,
        recurrenceRule: RecurrenceRule,
        preferredTimePeriodId: String? = null,
        estimatedMinutes: Int = 60,
        hasDeadline: Boolean = true,
    ) = ScheduleTask(
        id = id,
        title = id,
        description = "",
        priority = TaskPriority.MEDIUM,
        preferredTimeOfDay = PreferredTimeOfDay.ANYTIME,
        preferredTimePeriodId = preferredTimePeriodId,
        hasDeadline = hasDeadline,
        dueAt = dueAt,
        estimatedMinutes = estimatedMinutes,
        remainingMinutes = estimatedMinutes,
        recurrenceRule = recurrenceRule,
        status = TaskStatus.ACTIVE,
    )

    private fun block(taskId: String, id: String, startAt: Instant, endAt: Instant) = ScheduleBlock(
        id = id,
        taskId = taskId,
        startAt = startAt,
        endAt = endAt,
        source = BlockSource.AUTO,
        lockState = BlockLockState.FLEXIBLE,
        completionState = BlockCompletionState.PENDING,
        externalCalendarEventId = null,
    )
}

private class FakePlannerRepository(
    periods: MutableList<TimePeriod>? = null,
) : PlannerRepository {
    private val projects = mutableListOf<Project>()
    private val timeframes = mutableListOf<Timeframe>()
    private val tasks = mutableListOf<ScheduleTask>()
    private val blocks = mutableListOf<ScheduleBlock>()
    private val reminders = mutableListOf<Reminder>()
    private val schedulingIssues = mutableListOf<SchedulingIssue>()
    private val timePeriods = periods ?: mutableListOf(
        TimePeriod("period-morning", "Morning", LocalTime.of(9, 0), LocalTime.of(12, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 0),
        TimePeriod("period-afternoon", "Afternoon", LocalTime.of(13, 0), LocalTime.of(17, 0), type = TimePeriodType.PRODUCTIVE, sortOrder = 1),
    )
    private val snapshotFlow = MutableStateFlow(
        PlannerSnapshot(
            projects = emptyList(),
            timeframes = emptyList(),
            tasks = emptyList(),
            blocks = emptyList(),
            timePeriods = emptyList(),
        ),
    )

    override fun observeSnapshot(): Flow<PlannerSnapshot> = snapshotFlow

    override suspend fun upsertProject(project: Project) {
        projects.removeAll { it.id == project.id }
        projects += project
        publish()
    }

    override suspend fun upsertTimeframe(timeframe: Timeframe) {
        timeframes.removeAll { it.id == timeframe.id }
        timeframes += timeframe
        publish()
    }

    override suspend fun getTimeframes(): List<Timeframe> = timeframes.sortedBy { it.startDate }

    override suspend fun deleteTimeframe(timeframeId: String) {
        timeframes.removeAll { it.id == timeframeId }
        tasks.replaceAll { task ->
            if (task.timeframeId == timeframeId) task.copy(timeframeId = null) else task
        }
        publish()
    }

    override suspend fun upsertTask(task: ScheduleTask) {
        tasks.removeAll { it.id == task.id }
        tasks += task
        publish()
    }

    override suspend fun getTasks(): List<ScheduleTask> = tasks.sortedBy { it.id }

    override suspend fun updateTaskDueDate(taskId: String, dueAt: Instant) {
        val task = tasks.first { it.id == taskId }
        upsertTask(task.copy(dueAt = dueAt))
    }

    override suspend fun updateTaskPriority(taskId: String, priority: TaskPriority) {
        val task = tasks.first { it.id == taskId }
        upsertTask(task.copy(priority = priority))
    }

    override suspend fun updateTaskEstimatedMinutes(taskId: String, estimatedMinutes: Int) {
        val task = tasks.first { it.id == taskId }
        upsertTask(task.copy(estimatedMinutes = estimatedMinutes, remainingMinutes = estimatedMinutes))
    }

    override suspend fun getBlocks(): List<ScheduleBlock> = blocks.sortedBy { it.startAt }

    override suspend fun replaceFlexibleBlocks(taskId: String, blocks: List<ScheduleBlock>) {
        this.blocks.removeAll { it.taskId == taskId && it.lockState != BlockLockState.LOCKED && it.completionState != BlockCompletionState.COMPLETED }
        this.blocks += blocks
        publish()
    }

    override suspend fun updateBlockLock(blockId: String, lockState: BlockLockState) {
        replaceBlock(blockId) { copy(lockState = lockState) }
    }

    override suspend fun updateBlockCompletion(blockId: String, completionState: BlockCompletionState) {
        replaceBlock(blockId) { copy(completionState = completionState) }
    }

    override suspend fun updateTaskRemaining(taskId: String, remainingMinutes: Int) {
        val task = tasks.first { it.id == taskId }
        upsertTask(task.copy(remainingMinutes = remainingMinutes))
    }

    override suspend fun deleteTask(taskId: String) {
        tasks.removeAll { it.id == taskId }
        blocks.removeAll { it.taskId == taskId }
        publish()
    }

    override suspend fun clearAllPendingBlocks(taskId: String) {
        blocks.removeAll { it.taskId == taskId && it.completionState != BlockCompletionState.COMPLETED }
        publish()
    }

    override suspend fun getTimePeriods(): List<TimePeriod> = timePeriods.sortedBy { it.sortOrder }

    override suspend fun upsertTimePeriod(period: TimePeriod) {
        timePeriods.removeAll { it.id == period.id }
        timePeriods += period
        publish()
    }

    override suspend fun deleteTimePeriod(periodId: String) {
        timePeriods.removeAll { it.id == periodId }
        tasks.replaceAll { task ->
            if (task.preferredTimePeriodId == periodId) task.copy(preferredTimePeriodId = null) else task
        }
        publish()
    }

    override suspend fun upsertReminder(reminder: Reminder) {
        reminders.removeAll { it.id == reminder.id }
        reminders += reminder
        publish()
    }

    override suspend fun deleteReminder(reminderId: String) {
        reminders.removeAll { it.id == reminderId }
        publish()
    }

    override suspend fun getReminders(): List<Reminder> = reminders.sortedBy { it.dueAt }

    override fun observeReminders(): Flow<List<Reminder>> = snapshotFlow.map { it.reminders }

    override suspend fun getSchedulingIssues(): List<SchedulingIssue> = schedulingIssues.sortedBy { it.taskId }

    override suspend fun replaceSchedulingIssuesForTask(taskId: String, issues: List<SchedulingIssue>) {
        schedulingIssues.removeAll { it.taskId == taskId }
        schedulingIssues += issues
        publish()
    }

    override suspend fun seedDemoDataIfEmpty() = Unit

    override suspend fun clearLegacyDailyFlowData() {
        timePeriods.clear()
        tasks.replaceAll { it.copy(preferredTimePeriodId = null) }
        publish()
    }

    private suspend fun replaceBlock(blockId: String, transform: ScheduleBlock.() -> ScheduleBlock) {
        val block = blocks.first { it.id == blockId }
        blocks.removeAll { it.id == blockId }
        blocks += block.transform()
        publish()
    }

    private fun publish() {
        snapshotFlow.value = PlannerSnapshot(
            projects = projects.toList(),
            timeframes = timeframes.sortedWith(compareBy<Timeframe> { it.startDate }.thenBy { it.createdAt }),
            tasks = tasks.sortedBy { it.dueAt },
            blocks = blocks.sortedBy { it.startAt },
            timePeriods = timePeriods.sortedBy { it.sortOrder },
            reminders = reminders.sortedBy { it.dueAt },
            schedulingIssues = schedulingIssues.sortedBy { it.taskId },
        )
    }
}
