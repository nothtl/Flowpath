package dev.codex.reclaimoss

import dev.codex.reclaimoss.domain.model.BlockCompletionState
import dev.codex.reclaimoss.domain.model.BlockLockState
import dev.codex.reclaimoss.domain.model.BlockSource
import dev.codex.reclaimoss.domain.model.PreferredTimeOfDay
import dev.codex.reclaimoss.domain.model.RecurrenceRule
import dev.codex.reclaimoss.domain.model.ScheduleBlock
import dev.codex.reclaimoss.domain.model.ScheduleTask
import dev.codex.reclaimoss.domain.model.SchedulingIssueType
import dev.codex.reclaimoss.domain.model.SchedulingPolicy
import dev.codex.reclaimoss.domain.model.TaskContinuationMode
import dev.codex.reclaimoss.domain.model.TaskOverlapPolicy
import dev.codex.reclaimoss.domain.model.TaskSchedulingMode
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.TaskStatus
import dev.codex.reclaimoss.domain.model.TimeWindow
import dev.codex.reclaimoss.domain.model.TimePeriod
import dev.codex.reclaimoss.domain.model.WorkHoursDay
import dev.codex.reclaimoss.domain.model.WorkHoursProfile
import dev.codex.reclaimoss.domain.scheduling.ScheduleRebuildReason
import dev.codex.reclaimoss.domain.scheduling.SchedulerEngine
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerEngineTest {
    private val zone = ZoneId.of("America/New_York")
    private val policy = SchedulingPolicy(
        minBlockMinutes = 30,
        maxBlockMinutes = 120,
        breakBetweenBlocksMinutes = 15,
        priorityWeight = 1.5,
        deadlineUrgencyWeight = 2.0,
        lookAheadDays = 14,
    )
    private val workHours = WorkHoursProfile(
        timezone = zone.id,
        days = DayOfWeek.entries.associateWith { day ->
            when (day) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> WorkHoursDay(emptyList())
                else -> WorkHoursDay(
                    windows = listOf(
                        TimeWindow(LocalTime.of(9, 0), LocalTime.of(12, 0)),
                        TimeWindow(LocalTime.of(13, 0), LocalTime.of(17, 0)),
                    ),
                )
            }
        },
    )

    private val scheduler = SchedulerEngine()
    private val timePeriods = listOf(
        TimePeriod(
            id = "period-morning",
            label = "Morning",
            start = LocalTime.of(9, 0),
            end = LocalTime.of(12, 0),
        ),
        TimePeriod(
            id = "period-afternoon",
            label = "Afternoon",
            start = LocalTime.of(13, 0),
            end = LocalTime.of(17, 0),
        ),
    )

    @Test
    fun `places work blocks inside working hours only`() {
        val start = ZonedDateTime.of(LocalDate.of(2026, 5, 18), LocalTime.of(8, 0), zone).toInstant()
        val task = task(
            id = "task-1",
            deadline = ZonedDateTime.of(LocalDate.of(2026, 5, 19), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 180,
            remainingMinutes = 180,
            priority = TaskPriority.HIGH,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = start,
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals(1, plan.blocks.size)
        assertTrue(plan.blocks.all { block ->
            val dateTime = block.startAt.atZone(zone)
            dateTime.toLocalTime() >= LocalTime.of(9, 0) && block.endAt.atZone(zone).toLocalTime() <= LocalTime.of(17, 0)
        })
    }

    @Test
    fun `keeps locked blocks and reschedules only remaining work`() {
        val date = LocalDate.of(2026, 5, 18)
        val lockedBlock = ScheduleBlock(
            id = "block-locked",
            taskId = "task-1",
            startAt = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant(),
            endAt = ZonedDateTime.of(date, LocalTime.of(10, 0), zone).toInstant(),
            source = BlockSource.MANUAL,
            lockState = BlockLockState.LOCKED,
            completionState = BlockCompletionState.PENDING,
            externalCalendarEventId = null,
        )
        val task = task(
            id = "task-1",
            deadline = ZonedDateTime.of(date.plusDays(1), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 180,
            remainingMinutes = 120,
            priority = TaskPriority.MEDIUM,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = listOf(lockedBlock),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.TaskMissed("task-1"),
        )

        assertTrue(plan.blocks.any { it.id == "block-locked" })
        assertEquals(2, plan.blocks.size)
        assertEquals("block-locked", plan.blocks.first().id)
    }

    @Test
    fun `higher priority work wins earlier slots when deadlines are similar`() {
        val start = ZonedDateTime.of(LocalDate.of(2026, 5, 18), LocalTime.of(8, 0), zone).toInstant()
        val high = task(
            id = "high",
            deadline = ZonedDateTime.of(LocalDate.of(2026, 5, 20), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.HIGH,
        )
        val low = task(
            id = "low",
            deadline = ZonedDateTime.of(LocalDate.of(2026, 5, 20), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.LOW,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(low, high),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = start,
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals("high", plan.blocks.first().taskId)
        assertEquals("low", plan.blocks.last().taskId)
    }

    @Test
    fun `tasks with real deadlines schedule before no deadline backlog work even when backlog priority is higher`() {
        val start = ZonedDateTime.of(LocalDate.of(2026, 5, 18), LocalTime.of(8, 0), zone).toInstant()
        val deadlineTask = task(
            id = "deadline-task",
            deadline = ZonedDateTime.of(LocalDate.of(2026, 5, 25), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.LOW,
        )
        val backlogTask = task(
            id = "backlog-task",
            deadline = ZonedDateTime.of(LocalDate.of(2027, 5, 18), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.URGENT,
            hasDeadline = false,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(backlogTask, deadlineTask),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = start,
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals("deadline-task", plan.blocks.first().taskId)
        assertEquals("backlog-task", plan.blocks.last().taskId)
    }

    @Test
    fun `busy calendar windows force work into next available slot`() {
        val date = LocalDate.of(2026, 5, 18)
        val task = task(
            id = "task-busy",
            deadline = ZonedDateTime.of(date.plusDays(1), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.HIGH,
        )
        val busy = listOf(
            SchedulerEngine.BusyWindow(
                startAt = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant(),
                endAt = ZonedDateTime.of(date, LocalTime.of(11, 30), zone).toInstant(),
            ),
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = busy,
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.CalendarConflict("task-busy"),
        )

        assertEquals(LocalTime.of(13, 0), plan.blocks.single().startAt.atZone(zone).toLocalTime())
    }

    @Test
    fun `earliest same day slot is used when preferred periods are inactive`() {
        val date = LocalDate.of(2026, 5, 19)
        val task = task(
            id = "task-morning",
            deadline = ZonedDateTime.of(date, LocalTime.of(23, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-afternoon",
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals(LocalTime.of(9, 0), plan.blocks.single().startAt.atZone(zone).toLocalTime())
    }

    @Test
    fun `same day availability beats a later day when preferred periods are inactive`() {
        val date = LocalDate.of(2026, 5, 19)
        val task = task(
            id = "task-next-morning",
            deadline = ZonedDateTime.of(date.plusDays(1), LocalTime.of(23, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-morning",
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(13, 10), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val scheduled = plan.blocks.single()
        assertEquals(date, scheduled.startAt.atZone(zone).toLocalDate())
        assertEquals(LocalTime.of(13, 30), scheduled.startAt.atZone(zone).toLocalTime())
    }

    @Test
    fun `keeps one contiguous block when the full task fits in an empty period`() {
        val date = LocalDate.of(2026, 5, 19)
        val afternoonOnly = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(
                    windows = listOf(
                        TimeWindow(LocalTime.of(13, 0), LocalTime.of(17, 0)),
                    ),
                )
            },
        )
        val task = task(
            id = "task-four-hours",
            deadline = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 240,
            remainingMinutes = 240,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-afternoon",
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = afternoonOnly,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals(1, plan.blocks.size)
        assertEquals(LocalTime.of(13, 0), plan.blocks.single().startAt.atZone(zone).toLocalTime())
        assertEquals(LocalTime.of(17, 0), plan.blocks.single().endAt.atZone(zone).toLocalTime())
    }

    @Test
    fun `pending blocks for other tasks remain occupied during targeted scheduling`() {
        val date = LocalDate.of(2026, 5, 18)
        val existingOtherTaskBlock = ScheduleBlock(
            id = "block-other",
            taskId = "other-task",
            startAt = ZonedDateTime.of(date, LocalTime.of(13, 0), zone).toInstant(),
            endAt = ZonedDateTime.of(date, LocalTime.of(15, 0), zone).toInstant(),
            source = BlockSource.AUTO,
            lockState = BlockLockState.FLEXIBLE,
            completionState = BlockCompletionState.PENDING,
            externalCalendarEventId = null,
        )
        val task = task(
            id = "new-task",
            deadline = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.HIGH,
            preferredTimePeriodId = "period-afternoon",
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = listOf(existingOtherTaskBlock),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(12, 45), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val scheduled = plan.blocks.single { it.taskId == "new-task" }
        assertTrue(scheduled.startAt >= existingOtherTaskBlock.endAt)
    }

    @Test
    fun `full rebuild keeps an existing valid long block and moves the conflicting new task instead`() {
        val date = LocalDate.of(2026, 5, 19)
        val longMorningHours = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(
                    windows = listOf(
                        TimeWindow(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                        TimeWindow(LocalTime.of(14, 0), LocalTime.of(16, 0)),
                    ),
                )
            },
        )
        val longMorningPeriods = listOf(
            TimePeriod(
                id = "period-morning",
                label = "Morning",
                start = LocalTime.of(8, 0),
                end = LocalTime.of(12, 0),
            ),
            TimePeriod(
                id = "period-afternoon",
                label = "Afternoon",
                start = LocalTime.of(14, 0),
                end = LocalTime.of(16, 0),
            ),
        )
        val original = task(
            id = "original",
            deadline = ZonedDateTime.of(date.plusDays(1), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 240,
            remainingMinutes = 240,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-morning",
        )
        val conflicting = task(
            id = "conflicting",
            deadline = ZonedDateTime.of(date.plusDays(1), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 240,
            remainingMinutes = 240,
            priority = TaskPriority.HIGH,
            preferredTimePeriodId = "period-morning",
        )
        val existingOriginalBlock = ScheduleBlock(
            id = "original-block",
            taskId = original.id,
            startAt = ZonedDateTime.of(date.plusDays(1), LocalTime.of(8, 0), zone).toInstant(),
            endAt = ZonedDateTime.of(date.plusDays(1), LocalTime.of(12, 0), zone).toInstant(),
            source = BlockSource.AUTO,
            lockState = BlockLockState.FLEXIBLE,
            completionState = BlockCompletionState.PENDING,
            externalCalendarEventId = null,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(original, conflicting),
            existingBlocks = listOf(existingOriginalBlock),
            busyWindows = emptyList(),
            workHours = longMorningHours,
            timePeriods = longMorningPeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val originalBlocks = plan.blocks.filter { it.taskId == original.id }
        assertEquals(1, originalBlocks.size)
        assertEquals(existingOriginalBlock.startAt, originalBlocks.single().startAt)
        assertEquals(existingOriginalBlock.endAt, originalBlocks.single().endAt)
        assertTrue(plan.blocks.any { it.taskId == conflicting.id })
        assertTrue(plan.blocks.none { it.taskId == conflicting.id && it.startAt == existingOriginalBlock.startAt })
    }

    @Test
    fun `task blocks start on hour or half hour boundaries`() {
        val date = LocalDate.of(2026, 5, 18)
        val task = task(
            id = "aligned-task",
            deadline = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 45,
            remainingMinutes = 45,
            priority = TaskPriority.MEDIUM,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(9, 10), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals(LocalTime.of(9, 30), plan.blocks.single().startAt.atZone(zone).toLocalTime())
    }

    @Test
    fun `busy windows ending off boundary still schedule on next half hour`() {
        val date = LocalDate.of(2026, 5, 18)
        val task = task(
            id = "post-busy-task",
            deadline = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
        )
        val busy = listOf(
            SchedulerEngine.BusyWindow(
                startAt = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant(),
                endAt = ZonedDateTime.of(date, LocalTime.of(10, 10), zone).toInstant(),
            ),
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = busy,
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals(LocalTime.of(10, 30), plan.blocks.single().startAt.atZone(zone).toLocalTime())
    }

    @Test
    fun `aligned partial blocks smaller than minimum are skipped`() {
        val date = LocalDate.of(2026, 5, 18)
        val task = task(
            id = "too-small-after-align",
            deadline = ZonedDateTime.of(date, LocalTime.of(10, 40), zone).toInstant(),
            estimatedMinutes = 30,
            remainingMinutes = 30,
            priority = TaskPriority.URGENT,
        )
        val narrowWorkHours = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(listOf(TimeWindow(LocalTime.of(10, 10), LocalTime.of(10, 40))))
            },
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = narrowWorkHours,
            timePeriods = emptyList(),
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(10, 10), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertTrue(plan.blocks.isEmpty())
        assertEquals(SchedulingIssueType.UNSCHEDULED, plan.issues.single().type)
    }

    @Test
    fun `long task is split across one hour periods when enough days exist`() {
        val date = LocalDate.of(2026, 5, 18)
        val oneHourWorkHours = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith { day ->
                when (day) {
                    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> WorkHoursDay(emptyList())
                    else -> WorkHoursDay(
                        listOf(
                            TimeWindow(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                            TimeWindow(LocalTime.of(11, 0), LocalTime.of(12, 0)),
                            TimeWindow(LocalTime.of(13, 0), LocalTime.of(14, 0)),
                        ),
                    )
                }
            },
        )
        val task = task(
            id = "six-hour-task",
            deadline = ZonedDateTime.of(date.plusDays(3), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 360,
            remainingMinutes = 360,
            priority = TaskPriority.MEDIUM,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = oneHourWorkHours,
            timePeriods = emptyList(),
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals(6, plan.blocks.size)
        assertEquals(360, plan.blocks.sumOf { java.time.Duration.between(it.startAt, it.endAt).toMinutes().toInt() })
        assertTrue(plan.issues.isEmpty())
    }

    @Test
    fun `reports partial scheduling with remaining unscheduled minutes`() {
        val date = LocalDate.of(2026, 5, 18)
        val task = task(
            id = "partial-task",
            deadline = ZonedDateTime.of(date, LocalTime.of(10, 0), zone).toInstant(),
            estimatedMinutes = 180,
            remainingMinutes = 180,
            priority = TaskPriority.URGENT,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        // With extended splitLoopEnd, the full 180 min fits in one block
        assertEquals(1, plan.blocks.size)
        assertTrue(plan.unscheduledTaskIds.isEmpty())
        assertTrue(plan.issues.isEmpty())
    }

    @Test
    fun `reports unscheduled when no valid slot exists before deadline`() {
        val date = LocalDate.of(2026, 5, 18)
        val task = task(
            id = "unscheduled-task",
            deadline = ZonedDateTime.of(date, LocalTime.of(8, 30), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.URGENT,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertTrue(plan.blocks.none { it.taskId == "unscheduled-task" })
        assertEquals(listOf("unscheduled-task"), plan.unscheduledTaskIds)
        assertEquals(SchedulingIssueType.UNSCHEDULED, plan.issues.single().type)
        assertEquals(60, plan.issues.single().unscheduledMinutes)
    }

    @Test
    fun `allow concurrent tasks schedules overlapping work blocks`() {
        val date = LocalDate.of(2026, 5, 19)
        val concurrentPolicy = policy.copy(allowConcurrentTasks = true)
        val dueAt = ZonedDateTime.of(date, LocalTime.of(15, 0), zone).toInstant()
        val afternoonOnly = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(listOf(TimeWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))))
            },
        )
        val first = task(
            id = "first-overlap",
            deadline = dueAt,
            estimatedMinutes = 120,
            remainingMinutes = 120,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-afternoon",
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )
        val second = task(
            id = "second-overlap",
            deadline = dueAt,
            estimatedMinutes = 120,
            remainingMinutes = 120,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-afternoon",
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(first, second),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = afternoonOnly,
            timePeriods = timePeriods,
            policy = concurrentPolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val firstBlocks = plan.blocks.filter { it.taskId == first.id }
        val secondBlocks = plan.blocks.filter { it.taskId == second.id }
        assertTrue(firstBlocks.isNotEmpty())
        assertTrue(secondBlocks.isNotEmpty())
        assertTrue(
            firstBlocks.any { firstBlock ->
                secondBlocks.any { secondBlock ->
                    firstBlock.startAt < secondBlock.endAt && secondBlock.startAt < firstBlock.endAt
                }
            },
        )
    }

    @Test
    fun `task disallow overlap prevents concurrency even when global setting allows it`() {
        val date = LocalDate.of(2026, 5, 19)
        val concurrentPolicy = policy.copy(allowConcurrentTasks = true)
        val dueAt = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant()
        val first = task(
            id = "strict-first",
            deadline = dueAt,
            estimatedMinutes = 120,
            remainingMinutes = 120,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-afternoon",
            overlapPolicy = TaskOverlapPolicy.DISALLOW,
        )
        val second = task(
            id = "strict-second",
            deadline = dueAt,
            estimatedMinutes = 120,
            remainingMinutes = 120,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-afternoon",
            overlapPolicy = TaskOverlapPolicy.DISALLOW,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(first, second),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = concurrentPolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val firstBlock = plan.blocks.first { it.taskId == first.id }
        val secondBlock = plan.blocks.first { it.taskId == second.id }
        assertTrue(firstBlock.endAt <= secondBlock.startAt || secondBlock.endAt <= firstBlock.startAt)
    }

    @Test
    fun `task allow overlap does not opt into concurrency when global setting is off`() {
        val date = LocalDate.of(2026, 5, 19)
        val dueAt = ZonedDateTime.of(date, LocalTime.of(15, 0), zone).toInstant()
        val afternoonOnly = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(listOf(TimeWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))))
            },
        )
        val first = task(
            id = "opt-in-first",
            deadline = dueAt,
            estimatedMinutes = 120,
            remainingMinutes = 120,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-afternoon",
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )
        val second = task(
            id = "opt-in-second",
            deadline = dueAt,
            estimatedMinutes = 120,
            remainingMinutes = 120,
            priority = TaskPriority.MEDIUM,
            preferredTimePeriodId = "period-afternoon",
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(first, second),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = afternoonOnly,
            timePeriods = timePeriods,
            policy = policy.copy(allowConcurrentTasks = false),
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertEquals(1, plan.blocks.size)
        assertEquals(1, plan.unscheduledTaskIds.size)
        assertTrue(plan.unscheduledTaskIds.contains(first.id) || plan.unscheduledTaskIds.contains(second.id))
    }

    @Test
    fun `flexible window tasks stay inside the allowed window and prefer the middle`() {
        val date = LocalDate.of(2026, 5, 19)
        val eveningHours = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(
                    windows = listOf(
                        TimeWindow(LocalTime.of(18, 0), LocalTime.of(21, 0)),
                    ),
                )
            },
        )
        val windowStart = ZonedDateTime.of(date, LocalTime.of(18, 0), zone).toInstant()
        val windowEnd = ZonedDateTime.of(date, LocalTime.of(21, 0), zone).toInstant()
        val task = task(
            id = "dinner-window",
            deadline = windowEnd,
            estimatedMinutes = 30,
            remainingMinutes = 30,
            priority = TaskPriority.MEDIUM,
            schedulingMode = TaskSchedulingMode.FLEXIBLE_WINDOW,
            fixedStartAt = windowStart,
            fixedEndAt = windowEnd,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = eveningHours,
            timePeriods = emptyList(),
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val scheduled = plan.blocks.single()
        assertEquals(LocalTime.of(19, 0), scheduled.startAt.atZone(zone).toLocalTime())
        assertEquals(LocalTime.of(19, 30), scheduled.endAt.atZone(zone).toLocalTime())
    }

    @Test
    fun `fixed day tasks can use an overnight window and schedule inside it`() {
        val date = LocalDate.of(2026, 5, 20)
        val overnightHours = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(
                    windows = listOf(
                        TimeWindow(LocalTime.MIDNIGHT, LocalTime.of(23, 59, 59)),
                    ),
                )
            },
        )
        val windowStart = ZonedDateTime.of(date, LocalTime.of(22, 0), zone).toInstant()
        val windowEnd = ZonedDateTime.of(date.plusDays(1), LocalTime.of(2, 0), zone).toInstant()
        val task = task(
            id = "overnight-dinner",
            deadline = ZonedDateTime.of(date, LocalTime.of(23, 59), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            schedulingMode = TaskSchedulingMode.FIXED_DAY,
            fixedStartAt = windowStart,
            fixedEndAt = windowEnd,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = overnightHours,
            timePeriods = emptyList(),
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val scheduled = plan.blocks.single()
        assertEquals(LocalTime.of(23, 30), scheduled.startAt.atZone(zone).toLocalTime())
        assertEquals(date.plusDays(1), scheduled.endAt.atZone(zone).toLocalDate())
    }

    @Test
    fun `continuation task waits until parent scheduled work ends`() {
        val date = LocalDate.of(2026, 5, 19)
        val parent = task(
            id = "write-draft",
            deadline = ZonedDateTime.of(date, LocalTime.of(10, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.HIGH,
        )
        val child = task(
            id = "review-draft",
            deadline = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            continuationParentTaskId = parent.id,
            continuationMode = TaskContinuationMode.AFTER_PARENT_SCHEDULED_END,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(child, parent),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val parentBlock = plan.blocks.first { it.taskId == parent.id }
        val childBlock = plan.blocks.first { it.taskId == child.id }
        assertTrue(!childBlock.startAt.isBefore(parentBlock.endAt))
    }

    @Test
    fun `continuation task can wait until parent due time instead of scheduled end`() {
        val date = LocalDate.of(2026, 5, 19)
        val parent = task(
            id = "write-draft",
            deadline = ZonedDateTime.of(date, LocalTime.of(13, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.HIGH,
        )
        val child = task(
            id = "review-draft",
            deadline = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            continuationParentTaskId = parent.id,
            continuationMode = TaskContinuationMode.AFTER_PARENT_DUE_AT,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(child, parent),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = policy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val childBlock = plan.blocks.first { it.taskId == child.id }
        assertEquals(LocalTime.of(13, 0), childBlock.startAt.atZone(zone).toLocalTime())
    }

    @Test
    fun `allowSplitting false prevents splitting when task cannot fit as one whole block`() {
        val date = LocalDate.of(2026, 5, 18)
        val splittablePolicy = policy.copy(allowTaskSplitting = true, maxBlockMinutes = 60)
        // 240 min task with deadline at 12:00 — only 180 min available (9-12),
        // and maxBlockMinutes=60 means when splitting is allowed, it would make 60-min chunks.
        // But allowSplitting=false means it must fit as one piece = impossible.
        val task = task(
            id = "no-split-task",
            deadline = ZonedDateTime.of(date, LocalTime.of(12, 0), zone).toInstant(),
            estimatedMinutes = 240,
            remainingMinutes = 240,
            priority = TaskPriority.MEDIUM,
            allowSplitting = false,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = splittablePolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        assertTrue("Task should be unscheduled when splitting is disabled and full task doesn't fit",
            plan.issues.any { it.taskId == task.id && it.type == SchedulingIssueType.UNSCHEDULED })
    }

    @Test
    fun `locked pending block with ALLOW overlap does not block another ALLOW task`() {
        val date = LocalDate.of(2026, 5, 19)
        val concurrentPolicy = policy.copy(allowConcurrentTasks = true)
        val dueAt = ZonedDateTime.of(date, LocalTime.of(15, 0), zone).toInstant()
        val afternoonOnly = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(listOf(TimeWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))))
            },
        )
        val existingLockedBlock = ScheduleBlock(
            id = "locked-allow",
            taskId = "existing-task",
            startAt = ZonedDateTime.of(date, LocalTime.of(13, 0), zone).toInstant(),
            endAt = ZonedDateTime.of(date, LocalTime.of(14, 0), zone).toInstant(),
            source = BlockSource.MANUAL,
            lockState = BlockLockState.LOCKED,
            completionState = BlockCompletionState.PENDING,
            externalCalendarEventId = null,
        )
        val existingTask = task(
            id = "existing-task",
            deadline = dueAt,
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )
        val newTask = task(
            id = "new-allow-task",
            deadline = dueAt,
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(existingTask, newTask),
            existingBlocks = listOf(existingLockedBlock),
            busyWindows = emptyList(),
            workHours = afternoonOnly,
            timePeriods = timePeriods,
            policy = concurrentPolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val newBlocks = plan.blocks.filter { it.taskId == newTask.id }
        assertTrue("New ALLOW task should schedule somewhere", newBlocks.isNotEmpty())
    }

    @Test
    fun `locked pending block with DISALLOW overlap DOES block another task`() {
        val date = LocalDate.of(2026, 5, 19)
        val concurrentPolicy = policy.copy(allowConcurrentTasks = true)
        val dueAt = ZonedDateTime.of(date, LocalTime.of(15, 0), zone).toInstant()
        val afternoonOnly = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(listOf(TimeWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))))
            },
        )
        val existingLockedBlock = ScheduleBlock(
            id = "locked-disallow",
            taskId = "existing-disallow",
            startAt = ZonedDateTime.of(date, LocalTime.of(13, 0), zone).toInstant(),
            endAt = ZonedDateTime.of(date, LocalTime.of(14, 0), zone).toInstant(),
            source = BlockSource.MANUAL,
            lockState = BlockLockState.LOCKED,
            completionState = BlockCompletionState.PENDING,
            externalCalendarEventId = null,
        )
        val disallowTask = task(
            id = "existing-disallow",
            deadline = dueAt,
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            overlapPolicy = TaskOverlapPolicy.DISALLOW,
        )
        val newTask = task(
            id = "new-task",
            deadline = dueAt,
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(disallowTask, newTask),
            existingBlocks = listOf(existingLockedBlock),
            busyWindows = emptyList(),
            workHours = afternoonOnly,
            timePeriods = timePeriods,
            policy = concurrentPolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val newBlocks = plan.blocks.filter { it.taskId == newTask.id }
        assertTrue("New task should NOT overlap with DISALLOW locked block",
            newBlocks.all { it.endAt <= existingLockedBlock.startAt || it.startAt >= existingLockedBlock.endAt })
    }

    @Test
    fun `completed blocks always block regardless of overlap policy`() {
        val date = LocalDate.of(2026, 5, 19)
        val concurrentPolicy = policy.copy(allowConcurrentTasks = true)
        val completedBlock = ScheduleBlock(
            id = "completed-block",
            taskId = "completed-task",
            startAt = ZonedDateTime.of(date, LocalTime.of(13, 0), zone).toInstant(),
            endAt = ZonedDateTime.of(date, LocalTime.of(14, 0), zone).toInstant(),
            source = BlockSource.AUTO,
            lockState = BlockLockState.FLEXIBLE,
            completionState = BlockCompletionState.COMPLETED,
            externalCalendarEventId = null,
        )
        val newTask = task(
            id = "new-task",
            deadline = ZonedDateTime.of(date, LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(newTask),
            existingBlocks = listOf(completedBlock),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = concurrentPolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(12, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val scheduled = plan.blocks.single { it.taskId == newTask.id }
        assertTrue("New task should schedule after completed block, not overlap it",
            scheduled.startAt >= completedBlock.endAt)
    }

    @Test
    fun `sleep task blocks all other tasks even with allow overlap`() {
        val date = LocalDate.of(2026, 5, 19)
        val concurrentPolicy = policy.copy(allowConcurrentTasks = true)
        val dueAt = ZonedDateTime.of(date, LocalTime.of(15, 0), zone).toInstant()
        val sleepBlock = ScheduleBlock(
            id = "sleep-block",
            taskId = "sleep-task",
            startAt = ZonedDateTime.of(date, LocalTime.of(13, 0), zone).toInstant(),
            endAt = ZonedDateTime.of(date, LocalTime.of(14, 0), zone).toInstant(),
            source = BlockSource.AUTO,
            lockState = BlockLockState.FLEXIBLE,
            completionState = BlockCompletionState.PENDING,
            externalCalendarEventId = null,
        )
        val sleepTask = ScheduleTask(
            id = "sleep-task",
            title = "Sleep",
            taskKind = dev.codex.reclaimoss.domain.model.TaskKind.SLEEP,
            priority = TaskPriority.URGENT,
            dueAt = dueAt,
            estimatedMinutes = 60,
            remainingMinutes = 60,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
            recurrenceRule = RecurrenceRule(),
            status = TaskStatus.ACTIVE,
        )
        val newTask = task(
            id = "new-task",
            deadline = dueAt,
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.MEDIUM,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(sleepTask, newTask),
            existingBlocks = listOf(sleepBlock),
            busyWindows = emptyList(),
            workHours = workHours,
            timePeriods = timePeriods,
            policy = concurrentPolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(12, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        val newBlocks = plan.blocks.filter { it.taskId == newTask.id }
        assertTrue("New task should not overlap with sleep task",
            newBlocks.all { it.endAt <= sleepBlock.startAt || it.startAt >= sleepBlock.endAt })
    }

    @Test
    fun `three tasks all with ALLOW overlap can share the same time slot`() {
        val date = LocalDate.of(2026, 5, 19)
        val concurrentPolicy = policy.copy(allowConcurrentTasks = true)
        val dueAt = ZonedDateTime.of(date, LocalTime.of(15, 0), zone).toInstant()
        val afternoonOnly = WorkHoursProfile(
            timezone = zone.id,
            days = DayOfWeek.entries.associateWith {
                WorkHoursDay(listOf(TimeWindow(LocalTime.of(13, 0), LocalTime.of(15, 0))))
            },
        )
        val tasks = (1..3).map { i ->
            task(
                id = "task-$i",
                deadline = dueAt,
                estimatedMinutes = 120,
                remainingMinutes = 120,
                priority = TaskPriority.MEDIUM,
                overlapPolicy = TaskOverlapPolicy.ALLOW,
            )
        }

        val plan = scheduler.rebuildSchedule(
            tasks = tasks,
            existingBlocks = emptyList(),
            busyWindows = emptyList(),
            workHours = afternoonOnly,
            timePeriods = timePeriods,
            policy = concurrentPolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.ManualRebuild,
        )

        tasks.forEach { task ->
            val blocks = plan.blocks.filter { it.taskId == task.id }
            assertTrue("Each ALLOW task should have at least one block", blocks.isNotEmpty())
        }
        // At least two tasks should overlap each other
        val allBlockPairs = plan.blocks.flatMap { a ->
            plan.blocks.filter { it != a }.map { b -> a to b }
        }
        assertTrue("At least one pair of blocks from different tasks should overlap",
            allBlockPairs.any { (a, b) ->
                a.taskId != b.taskId && a.startAt < b.endAt && b.startAt < a.endAt
            })
    }

    @Test
    fun `calendar busy windows block even tasks with allow overlap`() {
        val date = LocalDate.of(2026, 5, 18)
        val concurrentPolicy = policy.copy(allowConcurrentTasks = true)
        val task = task(
            id = "calendar-blocked",
            deadline = ZonedDateTime.of(date.plusDays(1), LocalTime.of(17, 0), zone).toInstant(),
            estimatedMinutes = 60,
            remainingMinutes = 60,
            priority = TaskPriority.HIGH,
            overlapPolicy = TaskOverlapPolicy.ALLOW,
        )
        val busy = listOf(
            SchedulerEngine.BusyWindow(
                startAt = ZonedDateTime.of(date, LocalTime.of(9, 0), zone).toInstant(),
                endAt = ZonedDateTime.of(date, LocalTime.of(16, 0), zone).toInstant(),
            ),
        )

        val plan = scheduler.rebuildSchedule(
            tasks = listOf(task),
            existingBlocks = emptyList(),
            busyWindows = busy,
            workHours = workHours,
            timePeriods = timePeriods,
            policy = concurrentPolicy,
            rangeStart = ZonedDateTime.of(date, LocalTime.of(8, 0), zone).toInstant(),
            reason = ScheduleRebuildReason.CalendarConflict("calendar-blocked"),
        )

        val scheduled = plan.blocks.single { it.taskId == task.id }
        assertTrue("Task with ALLOW overlap should still avoid calendar busy window",
            scheduled.startAt >= busy.single().endAt || scheduled.endAt <= busy.single().startAt)
    }

    private fun task(
        id: String,
        deadline: Instant,
        estimatedMinutes: Int,
        remainingMinutes: Int,
        priority: TaskPriority,
        preferredTimeOfDay: PreferredTimeOfDay = PreferredTimeOfDay.ANYTIME,
        preferredTimePeriodId: String? = null,
        hasDeadline: Boolean = true,
        schedulingMode: TaskSchedulingMode = TaskSchedulingMode.FLEXIBLE,
        fixedStartAt: Instant? = null,
        fixedEndAt: Instant? = null,
        continuationParentTaskId: String? = null,
        continuationMode: TaskContinuationMode? = null,
        overlapPolicy: TaskOverlapPolicy = TaskOverlapPolicy.DISALLOW,
        allowSplitting: Boolean = true,
    ) = ScheduleTask(
        id = id,
        title = id,
        priority = priority,
        hasDeadline = hasDeadline,
        schedulingMode = schedulingMode,
        fixedStartAt = fixedStartAt,
        fixedEndAt = fixedEndAt,
        continuationParentTaskId = continuationParentTaskId,
        continuationMode = continuationMode,
        overlapPolicy = overlapPolicy,
        allowSplitting = allowSplitting,
        dueAt = deadline,
        estimatedMinutes = estimatedMinutes,
        remainingMinutes = remainingMinutes,
        preferredTimeOfDay = preferredTimeOfDay,
        preferredTimePeriodId = preferredTimePeriodId,
        recurrenceRule = RecurrenceRule(),
        status = TaskStatus.ACTIVE,
    )
}
