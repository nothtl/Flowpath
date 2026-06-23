package dev.codex.reclaimoss.domain.scheduling

import dev.codex.reclaimoss.domain.model.BlockCompletionState
import dev.codex.reclaimoss.domain.model.BlockLockState
import dev.codex.reclaimoss.domain.model.BlockSource
import dev.codex.reclaimoss.domain.model.ScheduleBlock
import dev.codex.reclaimoss.domain.model.SchedulePlan
import dev.codex.reclaimoss.domain.model.ScheduleTask
import dev.codex.reclaimoss.domain.model.SchedulingIssue
import dev.codex.reclaimoss.domain.model.SchedulingIssueType
import dev.codex.reclaimoss.domain.model.SchedulingPolicy
import dev.codex.reclaimoss.domain.model.TaskContinuationMode
import dev.codex.reclaimoss.domain.model.TaskKind
import dev.codex.reclaimoss.domain.model.TaskOverlapPolicy
import dev.codex.reclaimoss.domain.model.TaskSchedulingMode
import dev.codex.reclaimoss.domain.model.TaskStatus
import dev.codex.reclaimoss.domain.model.TimePeriod
import dev.codex.reclaimoss.domain.model.Timeframe
import dev.codex.reclaimoss.domain.model.WorkHoursProfile
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

sealed interface ScheduleRebuildReason {
    data object ManualRebuild : ScheduleRebuildReason
    data class TaskMissed(val taskId: String) : ScheduleRebuildReason
    data class CalendarConflict(val taskId: String) : ScheduleRebuildReason
}

class SchedulerEngine {
    data class BusyWindow(
        val startAt: Instant,
        val endAt: Instant,
    )

    private data class ScoredBusyWindow(
        val window: BusyWindow,
        val overlapCount: Int,
        val midpointDistanceMinutes: Long,
        val startAt: Instant,
    )

    private data class DailyWindowConstraint(
        val startTime: LocalTime,
        val endTime: LocalTime,
        val overnight: Boolean,
    )

    fun rebuildSchedule(
        tasks: List<ScheduleTask>,
        timeframes: List<Timeframe> = emptyList(),
        existingBlocks: List<ScheduleBlock>,
        busyWindows: List<BusyWindow>,
        workHours: WorkHoursProfile,
        timePeriods: List<TimePeriod> = emptyList(),
        policy: SchedulingPolicy,
        rangeStart: Instant,
        reason: ScheduleRebuildReason,
        preserveExistingPendingBlocks: Boolean = true,
    ): SchedulePlan {
        val zoneId = ZoneId.of(workHours.timezone)
        val existingPendingByTaskId = if (preserveExistingPendingBlocks) {
            existingBlocks
                .filter { it.completionState == BlockCompletionState.PENDING && it.lockState != BlockLockState.LOCKED }
                .groupBy { it.taskId }
        } else {
            emptyMap()
        }
        val existingAnchorByTaskId = existingPendingByTaskId
            .mapValues { (_, blocks) -> blocks.minOf { it.startAt } }
        val baseTasksToSchedule = tasks
            .filter { it.status == TaskStatus.ACTIVE && (it.remainingMinutes > 0 || it.taskKind == TaskKind.BLOCKER) }
            .sortedWith(
                compareBy<ScheduleTask> {
                    when (it.taskKind) {
                        TaskKind.SLEEP -> 0
                        TaskKind.BLOCKER -> 1
                        TaskKind.NORMAL -> 2
                    }
                }
                    .thenBy { !it.hasDeadline }
                    .thenBy { existingAnchorByTaskId[it.id] == null }
                    .thenBy { existingAnchorByTaskId[it.id] ?: Instant.MAX }
                    .thenByDescending { taskScore(it, rangeStart, policy) }
                    .thenBy { it.dueAt },
            )
        val activeTasksById = baseTasksToSchedule.associateBy { it.id }
        val allTasksById = tasks.associateBy { it.id }
        val timeframesById = timeframes.associateBy { it.id }
        val tasksToSchedule = orderTasksWithDependencies(baseTasksToSchedule, activeTasksById)
        val completedBlocks = existingBlocks.filter {
            it.completionState == BlockCompletionState.COMPLETED
        }
        val lockedPendingBlocks = existingBlocks.filter {
            it.completionState == BlockCompletionState.PENDING &&
                it.lockState == BlockLockState.LOCKED
        }
        val hardBusyWindows = (
            busyWindows +
                completedBlocks.map { BusyWindow(it.startAt, it.endAt) }
            )
            .sortedBy { it.startAt }
        val pendingBlocks = if (preserveExistingPendingBlocks) {
            existingBlocks.filter { it.completionState == BlockCompletionState.PENDING }
        } else {
            emptyList()
        }
        val pendingBlocksPool = pendingBlocks.toMutableList()
        val results = (completedBlocks + lockedPendingBlocks).sortedBy { it.startAt }.toMutableList()
        val unscheduled = mutableListOf<String>()
        val issues = mutableListOf<SchedulingIssue>()

        for (task in tasksToSchedule) {
            val timeframe = task.timeframeId?.let { timeframesById[it] }
            val existingTaskBlocks = existingPendingByTaskId[task.id].orEmpty().sortedBy { it.startAt }
            var remaining = task.remainingMinutes
            val startingRemaining = remaining
            val dependencyStartBoundary = dependencyStartBoundary(
                task = task,
                allTasksById = allTasksById,
                existingBlocks = existingBlocks,
                scheduledBlocks = results,
            )
            val dependencyEndBoundary = dependencyEndBoundary(
                task = task,
                allTasksById = allTasksById,
                existingBlocks = existingBlocks,
                scheduledBlocks = results,
            )
            val timeframeStart = timeframe?.startDate?.atStartOfDay(zoneId)?.toInstant()
            val timeframeEnd = timeframe?.endDate?.plusDays(1)?.atStartOfDay(zoneId)?.toInstant()?.minusSeconds(1)
            val taskUpperBound = taskSchedulingUpperBound(task, zoneId)
            val effectiveTaskDueAt = listOfNotNull(taskUpperBound, timeframeEnd, dependencyEndBoundary).minOrNull() ?: taskUpperBound
            val firstBlockStart = when {
                task.schedulingMode == TaskSchedulingMode.FIXED_DAY -> {
                    val occurrenceStart = task.dueAt.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
                    maxInstant(
                        maxInstant(
                            maxInstant(
                                maxInstant(rangeStart, occurrenceStart),
                                dependencyStartBoundary ?: Instant.MIN,
                            ),
                            timeframeStart ?: Instant.MIN,
                        ),
                        task.notBeforeAt ?: Instant.MIN,
                    )
                }
                task.schedulingMode == TaskSchedulingMode.FLEXIBLE_WINDOW && task.fixedStartAt != null -> {
                    maxInstant(
                        maxInstant(
                            maxInstant(
                                maxInstant(rangeStart, task.fixedStartAt),
                                dependencyStartBoundary ?: Instant.MIN,
                            ),
                            timeframeStart ?: Instant.MIN,
                        ),
                        task.notBeforeAt ?: Instant.MIN,
                    )
                }
                task.schedulingMode == TaskSchedulingMode.FLEXIBLE && task.fixedStartAt != null -> {
                    maxInstant(
                        maxInstant(
                            maxInstant(
                                maxInstant(rangeStart, task.fixedStartAt),
                                dependencyStartBoundary ?: Instant.MIN,
                            ),
                            timeframeStart ?: Instant.MIN,
                        ),
                        task.notBeforeAt ?: Instant.MIN,
                    )
                }
                task.schedulingMode == TaskSchedulingMode.FLEXIBLE_TIME && task.fixedStartAt != null -> {
                    maxInstant(
                        maxInstant(
                            maxInstant(
                                maxInstant(rangeStart, task.fixedStartAt),
                                dependencyStartBoundary ?: Instant.MIN,
                            ),
                            timeframeStart ?: Instant.MIN,
                        ),
                        task.notBeforeAt ?: Instant.MIN,
                    )
                }
                task.recurrenceRule.type != dev.codex.reclaimoss.domain.model.RecurrenceType.NONE -> {
                    val occurrenceStart = task.dueAt.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant()
                    maxInstant(
                        maxInstant(
                            maxInstant(
                                maxInstant(rangeStart, occurrenceStart),
                                dependencyStartBoundary ?: Instant.MIN,
                            ),
                            timeframeStart ?: Instant.MIN,
                        ),
                        task.notBeforeAt ?: Instant.MIN,
                    )
                }
                else -> maxInstant(
                    maxInstant(
                        maxInstant(
                            maxInstant(
                                maxInstant(rangeStart, Instant.now().minus(3650, ChronoUnit.DAYS)),
                                dependencyStartBoundary ?: Instant.MIN,
                            ),
                            timeframeStart ?: Instant.MIN,
                        ),
                        task.notBeforeAt ?: Instant.MIN,
                    ),
                    Instant.MIN,
                )
            }
            var cursor = firstBlockStart

            // noGap: when false (default), add a break-buffer gap between the
            // parent's end and this task's start. When true, hug the parent.
            if (task.continuationParentTaskId != null && !task.noGap &&
                dependencyStartBoundary != null &&
                (task.continuationMode == null || task.continuationMode == TaskContinuationMode.AFTER_PARENT_SCHEDULED_END)
            ) {
                cursor = cursor.plus(policy.breakBetweenBlocksMinutes.toLong(), ChronoUnit.MINUTES)
            }

            if (existingTaskBlocks.isNotEmpty()) {
                val occupiedWithoutSelf = partitionBusyWindowsForTask(
                    task = task,
                    taskBlocks = pendingBlocksPool.filter { it.taskId != task.id },
                    hardBusyWindows = hardBusyWindows,
                    tasksById = allTasksById,
                    allowConcurrentTasks = policy.allowConcurrentTasks,
                ).first
                val canKeepExisting = existingTaskBlocks.all { existingBlock ->
                    blockCanStayScheduled(
                        block = existingBlock,
                        task = task,
                        occupiedWithoutSelf = occupiedWithoutSelf,
                        zoneId = zoneId,
                        workHours = workHours,
                        timePeriods = timePeriods,
                        rangeStart = firstBlockStart,
                        dependencyStartBoundary = dependencyStartBoundary,
                        dependencyEndBoundary = dependencyEndBoundary,
                        timeframeStart = timeframeStart,
                        timeframeEnd = timeframeEnd,
                        effectiveDueAt = effectiveTaskDueAt,
                    )
                }
                if (canKeepExisting) {
                    results += existingTaskBlocks
                    val keptMinutes = existingTaskBlocks.sumOf { Duration.between(it.startAt, it.endAt).toMinutes().toInt() }
                    remaining = (remaining - keptMinutes).coerceAtLeast(0)
                    if (remaining == 0) {
                        continue
                    }
                    cursor = existingTaskBlocks.maxOf { it.endAt }
                        .plus(policy.breakBetweenBlocksMinutes.toLong(), ChronoUnit.MINUTES)
                } else {
                    existingTaskBlocks.forEach { existingBlock ->
                        pendingBlocksPool.removeAll { it.id == existingBlock.id }
                    }
                }
            }

            if (cursor == firstBlockStart) {
                cursor = existingAnchorByTaskId[task.id]?.let { maxInstant(firstBlockStart, it) } ?: firstBlockStart
            }

            // Blocker FIXED_EXACT tasks reserve their time slot regardless of
            // remaining work minutes. The fixedStartAt→fixedEndAt window is the
            // blocked time; any remaining > 0 represents associated work that
            // will be scheduled around the blocker after the reservation is made.
            if (task.taskKind == TaskKind.BLOCKER &&
                task.schedulingMode == TaskSchedulingMode.FIXED_EXACT &&
                task.fixedStartAt != null && task.fixedEndAt != null
            ) {
                val (occupied, _) = partitionBusyWindowsForTask(
                    task = task,
                    taskBlocks = pendingBlocksPool.filter { it.taskId != task.id },
                    hardBusyWindows = hardBusyWindows,
                    tasksById = allTasksById,
                    allowConcurrentTasks = policy.allowConcurrentTasks,
                )
                val conflictsWithOccupied = occupied.any { it.startAt < task.fixedEndAt && it.endAt > task.fixedStartAt }
                val withinTimeframe = timeframeStart == null || !task.fixedStartAt.isBefore(timeframeStart)
                val beforeTimeframeEnd = timeframeEnd == null || !task.fixedEndAt.isAfter(timeframeEnd)
                val afterRangeStart = !task.fixedStartAt.isBefore(rangeStart)
                val respectsDependency = dependencyStartBoundary == null || !task.fixedStartAt.isBefore(dependencyStartBoundary)
                val respectsEndBoundary = dependencyEndBoundary == null || !task.fixedEndAt.isAfter(dependencyEndBoundary)

                if (!conflictsWithOccupied && withinTimeframe && beforeTimeframeEnd && afterRangeStart && respectsDependency && respectsEndBoundary) {
                    val block = ScheduleBlock(
                        id = "block-${task.id}-${results.count { it.taskId == task.id } + 1}",
                        taskId = task.id,
                        startAt = task.fixedStartAt,
                        endAt = task.fixedEndAt,
                        source = BlockSource.AUTO,
                        lockState = BlockLockState.FLEXIBLE,
                        completionState = BlockCompletionState.PENDING,
                        externalCalendarEventId = null,
                    )
                    results += block
                    pendingBlocksPool += block
                    // If there's associated work (remaining > 0), the fixed-exact
                    // block doesn't count toward it — the work is separate.
                    if (remaining == 0) {
                        continue
                    }
                    // remaining > 0: fall through to normal scheduling for the work.
                    // Cursor starts after the reserved block so work is placed
                    // after the meeting.
                    cursor = task.fixedEndAt.plus(
                        policy.breakBetweenBlocksMinutes.toLong(), ChronoUnit.MINUTES)
                } else {
                    unscheduled += task.id
                    issues += SchedulingIssue(
                        taskId = task.id,
                        type = SchedulingIssueType.UNSCHEDULED,
                        unscheduledMinutes = remaining,
                        reason = "Blocker conflicts with occupied time or constraints.",
                    )
                    continue
                }
            }

            val effectiveAllowSplitting = policy.allowTaskSplitting && task.allowSplitting
            // Splittable tasks can extend past the deadline so they can
            // be split around sleep/blockers into future days. The extension
            // applies from the first iteration so the while-loop gate allows
            // past-deadline blocks. Dependency-constrained tasks cannot extend.
            val hasDependencyConstraint = dependencyEndBoundary != null || dependencyStartBoundary != null
            val splitLoopEnd = if (effectiveAllowSplitting && !hasDependencyConstraint) {
                maxInstant(effectiveTaskDueAt, cursor.plus(policy.lookAheadDays.toLong(), ChronoUnit.DAYS))
            } else {
                effectiveTaskDueAt
            }
            while (remaining > 0 && cursor <= splitLoopEnd) {
                val (occupied, softOccupied) = partitionBusyWindowsForTask(
                    task = task,
                    taskBlocks = pendingBlocksPool.filter { it.taskId != task.id },
                    hardBusyWindows = hardBusyWindows,
                    tasksById = allTasksById,
                    allowConcurrentTasks = policy.allowConcurrentTasks,
                )
                val candidate = nextCandidate(
                    task = task,
                    cursor = cursor,
                    // When splitting, let remaining portions extend past the deadline
                    // so the task can be split around sleep into the next day
                    dueAt = if (effectiveAllowSplitting && remaining < startingRemaining) {
                        maxInstant(effectiveTaskDueAt, cursor.plus(policy.lookAheadDays.toLong(), ChronoUnit.DAYS))
                    } else {
                        effectiveTaskDueAt
                    },
                    timeframeEnd = timeframeEnd,
                    zoneId = zoneId,
                    workHours = workHours,
                    occupied = occupied,
                    softOccupied = softOccupied,
                    timePeriods = timePeriods,
                    policy = policy,
                    // For fixed-start tasks: always anchor at cursor (non-concurrent).
                    // For flexible tasks: first block uses concurrent (best placement),
                    // split portions use non-concurrent (earliest slot, no gaps).
                    allowConcurrentForTask = taskAllowsOverlap(task, policy.allowConcurrentTasks) &&
                        task.fixedStartAt == null &&
                        remaining == startingRemaining,
                    remainingMinutes = remaining,
                    preferredTimePeriodId = task.preferredTimePeriodId,
                ) ?: break

                val minutes = Duration.between(candidate.startAt, candidate.endAt).toMinutes().toInt()
                val block = ScheduleBlock(
                    id = "block-${task.id}-${results.count { it.taskId == task.id } + 1}",
                    taskId = task.id,
                    startAt = candidate.startAt,
                    endAt = candidate.endAt,
                    source = BlockSource.AUTO,
                    lockState = BlockLockState.FLEXIBLE,
                    completionState = BlockCompletionState.PENDING,
                    externalCalendarEventId = null,
                )
                results += block
                pendingBlocksPool += block
                remaining -= minutes
                // Same-task split blocks should be contiguous; only add break
                // buffer when transitioning to a different task.
                cursor = if (remaining > 0) block.endAt
                    else block.endAt.plus(policy.breakBetweenBlocksMinutes.toLong(), ChronoUnit.MINUTES)
            }

            if (remaining > 0) {
                unscheduled += task.id
                val scheduledMinutes = startingRemaining - remaining
                issues += SchedulingIssue(
                    taskId = task.id,
                    type = if (scheduledMinutes > 0) SchedulingIssueType.PARTIAL else SchedulingIssueType.UNSCHEDULED,
                    unscheduledMinutes = remaining,
                    reason = if (scheduledMinutes > 0) {
                        if (task.schedulingMode == TaskSchedulingMode.FIXED_DAY) {
                            "Only part of this task fits on the selected date."
                        } else if (taskHasWindowConstraint(task, zoneId)) {
                            "Only part of this task fits inside its time window."
                        } else {
                            "Only part of this task fits before its deadline."
                        }
                    } else {
                        if (task.schedulingMode == TaskSchedulingMode.FIXED_DAY) {
                            "No valid time is available on the selected date."
                        } else if (taskHasWindowConstraint(task, zoneId)) {
                            "No valid time is available inside this time window."
                        } else {
                            "No valid slot is available before the deadline."
                        }
                    },
                )
            }
        }

        // Preserve pending blocks for tasks that weren't in the scheduling loop
        // (e.g. tasks with remaining=0 that still have active blocks occupying time).
        val pendingBlockIdsInResults = results.map { it.id }.toSet()
        val orphanedPending = pendingBlocksPool.filter { it.id !in pendingBlockIdsInResults }
        val allBlocks = results + orphanedPending

        return SchedulePlan(
            blocks = coalesceAdjacentTaskBlocks(allBlocks).sortedBy { it.startAt },
            unscheduledTaskIds = unscheduled.distinct(),
            issues = issues,
        )
    }

    private fun coalesceAdjacentTaskBlocks(blocks: List<ScheduleBlock>): List<ScheduleBlock> {
        if (blocks.size < 2) return blocks
        val merged = mutableListOf<ScheduleBlock>()
        blocks.sortedWith(compareBy<ScheduleBlock> { it.taskId }.thenBy { it.startAt }).forEach { block ->
            val previous = merged.lastOrNull()
            if (previous != null &&
                previous.taskId == block.taskId &&
                previous.endAt == block.startAt &&
                previous.source == BlockSource.AUTO &&
                block.source == BlockSource.AUTO &&
                previous.lockState == BlockLockState.FLEXIBLE &&
                block.lockState == BlockLockState.FLEXIBLE &&
                previous.completionState == BlockCompletionState.PENDING &&
                block.completionState == BlockCompletionState.PENDING
            ) {
                merged[merged.lastIndex] = previous.copy(endAt = block.endAt)
            } else {
                merged += block
            }
        }
        return merged
    }

    private fun blockCanStayScheduled(
        block: ScheduleBlock,
        task: ScheduleTask,
        occupiedWithoutSelf: List<BusyWindow>,
        zoneId: ZoneId,
        workHours: WorkHoursProfile,
        timePeriods: List<TimePeriod>,
        rangeStart: Instant,
        dependencyStartBoundary: Instant?,
        dependencyEndBoundary: Instant?,
        timeframeStart: Instant?,
        timeframeEnd: Instant?,
        effectiveDueAt: Instant,
    ): Boolean {
        if (block.endAt > effectiveDueAt) return false
        if (block.startAt < rangeStart) return false
        if (dependencyStartBoundary != null && block.startAt < dependencyStartBoundary) return false
        if (dependencyEndBoundary != null && block.endAt > dependencyEndBoundary) return false
        if (task.notBeforeAt != null && block.startAt < task.notBeforeAt) return false
        if (timeframeStart != null && block.startAt < timeframeStart) return false
        if (timeframeEnd != null && block.endAt > timeframeEnd) return false
        if (task.schedulingMode == TaskSchedulingMode.FLEXIBLE && task.fixedStartAt != null && !taskHasDailyWindowConstraint(task, zoneId) && block.startAt < task.fixedStartAt) return false
        if (task.schedulingMode == TaskSchedulingMode.FLEXIBLE_TIME && task.fixedStartAt != null) {
            val blockTime = block.startAt.atZone(zoneId).toLocalTime()
            val anchorTime = task.fixedStartAt.atZone(zoneId).toLocalTime()
            if (blockTime != anchorTime) return false
        }
        if (task.schedulingMode == TaskSchedulingMode.FLEXIBLE_WINDOW) {
            val windowStart = task.fixedStartAt ?: return false
            val windowEnd = task.fixedEndAt ?: task.dueAt
            if (block.startAt < windowStart || block.endAt > windowEnd) return false
        }
        if (taskHasDailyWindowConstraint(task, zoneId)) {
            val blockDate = block.startAt.atZone(zoneId).toLocalDate()
            val fitsWindow = windowSegmentsForDate(task, blockDate, zoneId).any { segment ->
                block.startAt >= segment.startAt && block.endAt <= segment.endAt
            }
            if (!fitsWindow) return false
        }
        if (occupiedWithoutSelf.any { it.startAt < block.endAt && it.endAt > block.startAt }) return false
        if (!isInsideWorkingWindow(block, zoneId, workHours)) return false
        return true
    }

    private fun isInsideWorkingWindow(
        block: ScheduleBlock,
        zoneId: ZoneId,
        workHours: WorkHoursProfile,
    ): Boolean = isInsideWorkingWindow(BusyWindow(block.startAt, block.endAt), zoneId, workHours)

    private fun isInsideWorkingWindow(
        window: BusyWindow,
        zoneId: ZoneId,
        workHours: WorkHoursProfile,
    ): Boolean {
        var cursor = window.startAt
        while (cursor < window.endAt) {
            val cursorZoned = cursor.atZone(zoneId)
            val dayDate = cursorZoned.toLocalDate()
            val nextMidnight = dayDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            val segmentEnd = minInstant(window.endAt, nextMidnight)
            val day = workHours.days[cursorZoned.dayOfWeek] ?: return false
            val fitsDayWindow = day.windows.any { dayWindow ->
                val dayWindowStart = ZonedDateTime.of(dayDate, dayWindow.start, zoneId).toInstant()
                val dayWindowEnd = windowEndInstant(dayDate, dayWindow.end, zoneId)
                cursor >= dayWindowStart && segmentEnd <= dayWindowEnd
            }
            if (!fitsDayWindow) return false
            cursor = segmentEnd
        }
        return true
    }

    private fun nextCandidate(
        task: ScheduleTask,
        cursor: Instant,
        dueAt: Instant,
        timeframeEnd: Instant?,
        zoneId: ZoneId,
        workHours: WorkHoursProfile,
        occupied: List<BusyWindow>,
        softOccupied: List<BusyWindow>,
        timePeriods: List<TimePeriod>,
        policy: SchedulingPolicy,
        allowConcurrentForTask: Boolean,
        remainingMinutes: Int,
        preferredTimePeriodId: String?,
    ): BusyWindow? {
        val fixedDayEnd = if (task.schedulingMode == TaskSchedulingMode.FIXED_DAY) {
            taskSchedulingUpperBound(task, zoneId)
        } else {
            dueAt
        }
        val effectiveDueAt = timeframeEnd?.let { minInstant(fixedDayEnd, it) } ?: fixedDayEnd
        val hasWindowConstraint = taskHasWindowConstraint(task, zoneId)
        val lookAheadEnd = minInstant(
            effectiveDueAt,
            cursor.plus(policy.lookAheadDays.toLong(), ChronoUnit.DAYS),
        )
        var date = cursor.atZone(zoneId).toLocalDate()
        val endDate = lookAheadEnd.atZone(zoneId).toLocalDate()
        var earliestFallback: BusyWindow? = null
        var bestConcurrentCandidate: ScoredBusyWindow? = null

        while (!date.isAfter(endDate)) {
            val preferredSegments = mutableListOf<BusyWindow>()
            val fallbackSegments = mutableListOf<BusyWindow>()
            val candidateSegments = if (hasWindowConstraint) {
                windowSegmentsForDate(task, date, zoneId)
            } else {
                workHours.days[date.dayOfWeek]?.windows.orEmpty().map { window ->
                    BusyWindow(
                        startAt = ZonedDateTime.of(date, window.start, zoneId).toInstant(),
                        endAt = windowEndInstant(date, window.end, zoneId),
                    )
                }
            }
            for (candidateSegment in candidateSegments) {
                val segmentStart = maxInstant(candidateSegment.startAt, cursor)
                val segmentEnd = minInstant(candidateSegment.endAt, effectiveDueAt)
                if (segmentEnd <= segmentStart) continue
                val freeSegments = subtractBusy(
                    start = segmentStart,
                    end = segmentEnd,
                    occupied = occupied,
                )
                for (segment in freeSegments) {
                    val capacity = Duration.between(segment.startAt, segment.endAt).toMinutes().toInt()
                    if (capacity < policy.minBlockMinutes) continue
                    preferredSegments += segment
                }
            }
            // Fixed-start tasks must anchor at cursor — treat splitting as always allowed
            // so even non-splittable tasks can place a partial block at their fixed time.
            val effectiveAllowSplitting = if (task.fixedStartAt != null) {
                true
            } else {
                policy.allowTaskSplitting && task.allowSplitting
            }
            if (allowConcurrentForTask) {
                val preferredCandidate = preferredSegments.bestConcurrentBlock(
                    remainingMinutes = remainingMinutes,
                    maxBlockMinutes = policy.maxBlockMinutes,
                    minBlockMinutes = policy.minBlockMinutes,
                    zoneId = zoneId,
                    alignmentMinutes = policy.alignmentMinutes,
                    allowTaskSplitting = effectiveAllowSplitting,
                    softOccupied = softOccupied,
                    workHours = if (hasWindowConstraint) workHours else null,
                )
                val fallbackCandidate = if (!policy.strictPreferredPeriod && preferredCandidate == null) {
                    fallbackSegments.bestConcurrentBlock(
                        remainingMinutes = remainingMinutes,
                        maxBlockMinutes = policy.maxBlockMinutes,
                        minBlockMinutes = policy.minBlockMinutes,
                        zoneId = zoneId,
                        alignmentMinutes = policy.alignmentMinutes,
                        allowTaskSplitting = effectiveAllowSplitting,
                        softOccupied = softOccupied,
                        workHours = if (hasWindowConstraint) workHours else null,
                    )
                } else {
                    null
                }
                bestConcurrentCandidate = preferredCandidate
                    ?.let { candidate -> minByNullable(bestConcurrentCandidate, candidate) }
                    ?: fallbackCandidate?.let { candidate -> minByNullable(bestConcurrentCandidate, candidate) }
                    ?: bestConcurrentCandidate
            } else {
                val preferredCandidate = if (hasWindowConstraint) {
                    preferredSegments.bestWindowedBlock(
                        remainingMinutes = remainingMinutes,
                        maxBlockMinutes = policy.maxBlockMinutes,
                        minBlockMinutes = policy.minBlockMinutes,
                        zoneId = zoneId,
                        alignmentMinutes = policy.alignmentMinutes,
                        allowTaskSplitting = effectiveAllowSplitting,
                        workHours = workHours,
                    )
                } else if (task.schedulingMode == TaskSchedulingMode.FLEXIBLE_TIME && task.fixedStartAt != null) {
                    // FLEXIBLE_TIME: pin to exact time-of-day on a flexible date.
                    // For each day, compute the anchored start at the fixed time-of-day
                    // and check if the full duration fits in a free segment.
                    val anchorTime = task.fixedStartAt.atZone(zoneId).toLocalTime()
                    val pinnedStart = ZonedDateTime.of(date, anchorTime, zoneId).toInstant()
                    val pinnedEnd = pinnedStart.plus(task.estimatedMinutes.toLong(), ChronoUnit.MINUTES)
                    val fits = preferredSegments.any { seg ->
                        pinnedStart >= seg.startAt && pinnedEnd <= seg.endAt
                    }
                    if (fits) {
                        BusyWindow(startAt = pinnedStart, endAt = pinnedEnd)
                    } else {
                        null
                    }
                } else {
                    // For fixed-start tasks: force anchor at cursor by only
                    // considering the first (earliest) segment. This ensures
                    // the block starts at the fixed time, splitting if needed.
                    val segmentsToUse = if (task.fixedStartAt != null) {
                        preferredSegments.take(1)
                    } else {
                        preferredSegments
                    }
                    segmentsToUse.firstBlock(
                        remainingMinutes = remainingMinutes,
                        maxBlockMinutes = policy.maxBlockMinutes,
                        minBlockMinutes = policy.minBlockMinutes,
                        zoneId = zoneId,
                        alignmentMinutes = policy.alignmentMinutes,
                        allowTaskSplitting = effectiveAllowSplitting,
                        breakBetweenBlocksMinutes = policy.breakBetweenBlocksMinutes,
                    )
                }
                preferredCandidate?.let { return it }
            }
            if (!policy.strictPreferredPeriod && earliestFallback == null) {
                earliestFallback = if (hasWindowConstraint) {
                    fallbackSegments.bestWindowedBlock(
                        remainingMinutes = remainingMinutes,
                        maxBlockMinutes = policy.maxBlockMinutes,
                        minBlockMinutes = policy.minBlockMinutes,
                        zoneId = zoneId,
                        alignmentMinutes = policy.alignmentMinutes,
                        allowTaskSplitting = effectiveAllowSplitting,
                        workHours = workHours,
                    )
                } else {
                    fallbackSegments.firstBlock(
                        remainingMinutes = remainingMinutes,
                        maxBlockMinutes = policy.maxBlockMinutes,
                        minBlockMinutes = policy.minBlockMinutes,
                        zoneId = zoneId,
                        alignmentMinutes = policy.alignmentMinutes,
                        allowTaskSplitting = effectiveAllowSplitting,
                        breakBetweenBlocksMinutes = policy.breakBetweenBlocksMinutes,
                    )
                }
            }

            date = date.plusDays(1)
        }
        if (allowConcurrentForTask) return bestConcurrentCandidate?.window
        return earliestFallback
    }

    private fun partitionBusyWindowsForTask(
        task: ScheduleTask,
        taskBlocks: List<ScheduleBlock>,
        hardBusyWindows: List<BusyWindow>,
        tasksById: Map<String, ScheduleTask>,
        allowConcurrentTasks: Boolean,
    ): Pair<List<BusyWindow>, List<BusyWindow>> {
        val blockingTaskWindows = mutableListOf<BusyWindow>()
        val softTaskWindows = mutableListOf<BusyWindow>()
        taskBlocks.forEach { block ->
            val window = BusyWindow(block.startAt, block.endAt)
            val otherTask = tasksById[block.taskId]
            if (otherTask != null && otherTask.taskKind == TaskKind.SLEEP && task.taskKind != TaskKind.SLEEP) {
                blockingTaskWindows += window
            } else {
                val blockerAndSleep = otherTask != null &&
                    task.taskKind == TaskKind.SLEEP && otherTask.taskKind == TaskKind.BLOCKER
                if (blockerAndSleep || (otherTask != null && tasksCanOverlap(task, otherTask, allowConcurrentTasks))) {
                    softTaskWindows += window
                } else {
                    blockingTaskWindows += window
                }
            }
        }
        return Pair(
            (hardBusyWindows + blockingTaskWindows).sortedBy { it.startAt },
            softTaskWindows.sortedBy { it.startAt },
        )
    }

    private fun List<BusyWindow>.firstBlock(
        remainingMinutes: Int,
        maxBlockMinutes: Int,
        minBlockMinutes: Int,
        zoneId: ZoneId,
        alignmentMinutes: Int,
        allowTaskSplitting: Boolean,
        breakBetweenBlocksMinutes: Int,
    ): BusyWindow? {
        firstOrNull { segment ->
            val alignedStart = alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes)
            Duration.between(alignedStart, segment.endAt).toMinutes().toInt() >= remainingMinutes
        }?.let { segment ->
            val alignedStart = alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes)
            return BusyWindow(
                startAt = alignedStart,
                endAt = alignedStart.plus(remainingMinutes.toLong(), ChronoUnit.MINUTES),
            )
        }
        if (!allowTaskSplitting) return null
        val preferredBlockMinutes = min(remainingMinutes, maxBlockMinutes)
        firstOrNull { segment ->
            val alignedStart = alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes)
            Duration.between(alignedStart, segment.endAt).toMinutes().toInt() >= preferredBlockMinutes
        }?.let { segment ->
            val alignedStart = alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes)
            val available = Duration.between(alignedStart, segment.endAt).toMinutes().toInt()
            val leftover = available - preferredBlockMinutes - breakBetweenBlocksMinutes
            val effectiveMinutes = if (leftover > 0 && leftover < minBlockMinutes) {
                available.coerceAtMost(remainingMinutes)
            } else {
                preferredBlockMinutes
            }
            return BusyWindow(
                startAt = alignedStart,
                endAt = alignedStart.plus(effectiveMinutes.toLong(), ChronoUnit.MINUTES),
            )
        }
        firstOrNull { segment ->
            val alignedStart = alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes)
            Duration.between(alignedStart, segment.endAt).toMinutes().toInt() >= minBlockMinutes
        }?.let { segment ->
            val alignedStart = alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes)
            val partialBlockMinutes = min(
                min(
                    Duration.between(alignedStart, segment.endAt).toMinutes().toInt(),
                    remainingMinutes,
                ),
                maxBlockMinutes,
            )
            return BusyWindow(
                startAt = alignedStart,
                endAt = alignedStart.plus(partialBlockMinutes.toLong(), ChronoUnit.MINUTES),
            )
        }
        return null
    }

    private fun List<BusyWindow>.bestConcurrentBlock(
        remainingMinutes: Int,
        maxBlockMinutes: Int,
        minBlockMinutes: Int,
        zoneId: ZoneId,
        alignmentMinutes: Int,
        allowTaskSplitting: Boolean,
        softOccupied: List<BusyWindow>,
        workHours: WorkHoursProfile? = null,
    ): ScoredBusyWindow? {
        var best: ScoredBusyWindow? = null
        for (segment in this) {
            val preferredBlockMinutes = when {
                Duration.between(segment.startAt, segment.endAt).toMinutes().toInt() >= remainingMinutes -> remainingMinutes
                !allowTaskSplitting -> continue
                Duration.between(segment.startAt, segment.endAt).toMinutes().toInt() >= min(remainingMinutes, maxBlockMinutes) -> min(remainingMinutes, maxBlockMinutes)
                else -> min(
                    min(Duration.between(alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes), segment.endAt).toMinutes().toInt(), remainingMinutes),
                    maxBlockMinutes,
                )
            }
            if (preferredBlockMinutes < minBlockMinutes) continue
            val alignedStart = alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes)
            var candidateStart = alignedStart
            val windowMidpoint = Duration.between(Instant.EPOCH, segment.startAt).toMinutes() +
                Duration.between(segment.startAt, segment.endAt).toMinutes() / 2
            val step = alignmentMinutes.coerceAtLeast(1)
            while (!candidateStart.plus(preferredBlockMinutes.toLong(), ChronoUnit.MINUTES).isAfter(segment.endAt)) {
                val candidate = BusyWindow(
                    startAt = candidateStart,
                    endAt = candidateStart.plus(preferredBlockMinutes.toLong(), ChronoUnit.MINUTES),
                )
                if (workHours != null && !isInsideWorkingWindow(candidate, zoneId, workHours)) {
                    candidateStart = candidateStart.plus(step.toLong(), ChronoUnit.MINUTES)
                    continue
                }
                val overlapCount = softOccupied.count { it.startAt < candidate.endAt && it.endAt > candidate.startAt }
                val candidateMidpoint = Duration.between(Instant.EPOCH, candidate.startAt).toMinutes() + preferredBlockMinutes / 2
                val score = ScoredBusyWindow(
                    window = candidate,
                    overlapCount = overlapCount,
                    midpointDistanceMinutes = kotlin.math.abs(candidateMidpoint - windowMidpoint),
                    startAt = candidate.startAt,
                )
                best = minByNullable(best, score)
                candidateStart = candidateStart.plus(step.toLong(), ChronoUnit.MINUTES)
            }
        }
        return best
    }

    private fun List<BusyWindow>.bestWindowedBlock(
        remainingMinutes: Int,
        maxBlockMinutes: Int,
        minBlockMinutes: Int,
        zoneId: ZoneId,
        alignmentMinutes: Int,
        allowTaskSplitting: Boolean,
        workHours: WorkHoursProfile,
    ): BusyWindow? {
        var best: ScoredBusyWindow? = null
        for (segment in this) {
            val preferredBlockMinutes = when {
                Duration.between(segment.startAt, segment.endAt).toMinutes().toInt() >= remainingMinutes -> remainingMinutes
                !allowTaskSplitting -> continue
                Duration.between(segment.startAt, segment.endAt).toMinutes().toInt() >= min(remainingMinutes, maxBlockMinutes) -> min(remainingMinutes, maxBlockMinutes)
                else -> min(
                    min(Duration.between(alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes), segment.endAt).toMinutes().toInt(), remainingMinutes),
                    maxBlockMinutes,
                )
            }
            if (preferredBlockMinutes < minBlockMinutes) continue
            val alignedStart = alignToNextAlignment(segment.startAt, zoneId, alignmentMinutes)
            var candidateStart = alignedStart
            val windowMidpoint = Duration.between(Instant.EPOCH, segment.startAt).toMinutes() +
                Duration.between(segment.startAt, segment.endAt).toMinutes() / 2
            val winStep = alignmentMinutes.coerceAtLeast(1)
            while (!candidateStart.plus(preferredBlockMinutes.toLong(), ChronoUnit.MINUTES).isAfter(segment.endAt)) {
                val candidate = BusyWindow(
                    startAt = candidateStart,
                    endAt = candidateStart.plus(preferredBlockMinutes.toLong(), ChronoUnit.MINUTES),
                )
                if (!isInsideWorkingWindow(candidate, zoneId, workHours)) {
                    candidateStart = candidateStart.plus(winStep.toLong(), ChronoUnit.MINUTES)
                    continue
                }
                val candidateMidpoint = Duration.between(Instant.EPOCH, candidate.startAt).toMinutes() + preferredBlockMinutes / 2
                val score = ScoredBusyWindow(
                    window = candidate,
                    overlapCount = 0,
                    midpointDistanceMinutes = kotlin.math.abs(candidateMidpoint - windowMidpoint),
                    startAt = candidate.startAt,
                )
                best = minByNullable(best, score)
                candidateStart = candidateStart.plus(winStep.toLong(), ChronoUnit.MINUTES)
            }
        }
        return best?.window
    }

    private fun minByNullable(current: ScoredBusyWindow?, candidate: ScoredBusyWindow): ScoredBusyWindow =
        when {
            current == null -> candidate
            candidate.overlapCount < current.overlapCount -> candidate
            candidate.overlapCount > current.overlapCount -> current
            candidate.midpointDistanceMinutes < current.midpointDistanceMinutes -> candidate
            candidate.midpointDistanceMinutes > current.midpointDistanceMinutes -> current
            candidate.startAt < current.startAt -> candidate
            else -> current
        }


    private fun subtractBusy(
        start: Instant,
        end: Instant,
        occupied: List<BusyWindow>,
    ): List<BusyWindow> {
        if (end <= start) return emptyList()
        val free = mutableListOf<BusyWindow>()
        var cursor = start
        occupied
            .filter { it.endAt > start && it.startAt < end }
            .sortedBy { it.startAt }
            .forEach { busy ->
                if (busy.startAt > cursor) {
                    free += BusyWindow(cursor, minInstant(busy.startAt, end))
                }
                if (busy.endAt > cursor) {
                    cursor = maxInstant(cursor, busy.endAt)
                }
            }
        if (cursor < end) {
            free += BusyWindow(cursor, end)
        }
        return free.filter { it.endAt > it.startAt }
    }

    private fun orderTasksWithDependencies(
        baseTasks: List<ScheduleTask>,
        tasksById: Map<String, ScheduleTask>,
    ): List<ScheduleTask> {
        val ordered = mutableListOf<ScheduleTask>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(task: ScheduleTask) {
            if (task.id in visited) return
            if (!visiting.add(task.id)) {
                // Cycle detected: break it by scheduling this task without its
                // dependency parent. The cycle participant is still added to
                // ordered so at least one task survives the cycle.
                visited += task.id
                ordered += task
                return
            }
            val parentId = task.continuationParentTaskId
            if (parentId != null) {
                tasksById[parentId]?.let(::visit)
            }
            visiting.remove(task.id)
            visited += task.id
            ordered += task
        }

        baseTasks.forEach(::visit)
        return ordered
    }

    private fun dependencyStartBoundary(
        task: ScheduleTask,
        allTasksById: Map<String, ScheduleTask>,
        existingBlocks: List<ScheduleBlock>,
        scheduledBlocks: List<ScheduleBlock>,
    ): Instant? {
        val parentId = task.continuationParentTaskId ?: return null
        val parentTask = allTasksById[parentId]
        return when (task.continuationMode ?: TaskContinuationMode.AFTER_PARENT_SCHEDULED_END) {
            TaskContinuationMode.AFTER_PARENT_SCHEDULED_END -> {
                val latestParentEnd = (existingBlocks.asSequence() + scheduledBlocks.asSequence())
                    .filter { it.taskId == parentId }
                    .maxOfOrNull { it.endAt }
                // If parent has no blocks (was unscheduled or in a broken dependency
                // cycle), return null — don't constrain child to parent's deadline.
                // Returning parentTask?.dueAt would create an impossible constraint
                // when parent and child share the same deadline.
                latestParentEnd
            }
            TaskContinuationMode.AFTER_PARENT_DUE_AT -> parentTask?.dueAt
            TaskContinuationMode.BEFORE_PARENT_START -> null
        }
    }

    private fun dependencyEndBoundary(
        task: ScheduleTask,
        allTasksById: Map<String, ScheduleTask>,
        existingBlocks: List<ScheduleBlock>,
        scheduledBlocks: List<ScheduleBlock>,
    ): Instant? {
        val parentId = task.continuationParentTaskId ?: return null
        val parentTask = allTasksById[parentId]
        return when (task.continuationMode ?: TaskContinuationMode.AFTER_PARENT_SCHEDULED_END) {
            TaskContinuationMode.BEFORE_PARENT_START -> {
                val earliestParentStart = (existingBlocks.asSequence() + scheduledBlocks.asSequence())
                    .filter { it.taskId == parentId }
                    .minOfOrNull { it.startAt }
                earliestParentStart ?: parentTask?.fixedStartAt ?: parentTask?.dueAt
            }
            TaskContinuationMode.AFTER_PARENT_SCHEDULED_END,
            TaskContinuationMode.AFTER_PARENT_DUE_AT -> null
        }
    }

    private fun taskSchedulingUpperBound(
        task: ScheduleTask,
        zoneId: ZoneId,
    ): Instant = when {
        task.schedulingMode == TaskSchedulingMode.FIXED_DAY && taskHasDailyWindowConstraint(task, zoneId) ->
            task.fixedEndAt ?: task.dueAt
        task.schedulingMode == TaskSchedulingMode.FLEXIBLE_WINDOW ->
            task.fixedEndAt ?: task.dueAt
        else -> task.dueAt
    }

    private fun taskHasWindowConstraint(
        task: ScheduleTask,
        zoneId: ZoneId,
    ): Boolean = task.schedulingMode == TaskSchedulingMode.FLEXIBLE_WINDOW || taskHasDailyWindowConstraint(task, zoneId)

    private fun taskHasDailyWindowConstraint(
        task: ScheduleTask,
        zoneId: ZoneId,
    ): Boolean = task.dailyWindowConstraint(zoneId) != null

    private fun ScheduleTask.dailyWindowConstraint(zoneId: ZoneId): DailyWindowConstraint? {
        if (schedulingMode == TaskSchedulingMode.FIXED_EXACT || schedulingMode == TaskSchedulingMode.FLEXIBLE_WINDOW || schedulingMode == TaskSchedulingMode.FLEXIBLE_TIME) return null
        val start = fixedStartAt ?: return null
        val end = fixedEndAt ?: return null
        val startLocal = start.atZone(zoneId)
        val endLocal = end.atZone(zoneId)
        return DailyWindowConstraint(
            startTime = startLocal.toLocalTime(),
            endTime = endLocal.toLocalTime(),
            overnight = endLocal.toLocalDate().isAfter(startLocal.toLocalDate()) || !endLocal.toLocalTime().isAfter(startLocal.toLocalTime()),
        )
    }

    private fun windowSegmentsForDate(
        task: ScheduleTask,
        date: LocalDate,
        zoneId: ZoneId,
    ): List<BusyWindow> {
        if (task.schedulingMode == TaskSchedulingMode.FLEXIBLE_WINDOW) {
            val startAt = task.fixedStartAt ?: return emptyList()
            val endAt = task.fixedEndAt ?: task.dueAt
            // Only return the window for the date containing its start.
            // This prevents overnight windows (e.g. 10 PM – 7 AM) from being
            // split at midnight into two partial segments.
            val windowStartDate = startAt.atZone(zoneId).toLocalDate()
            if (date != windowStartDate) return emptyList()
            return listOf(BusyWindow(startAt, endAt))
        }
        val constraint = task.dailyWindowConstraint(zoneId) ?: return emptyList()
        val anchorDate = task.dueAt.atZone(zoneId).toLocalDate()
        return when {
            !constraint.overnight && task.schedulingMode == TaskSchedulingMode.FIXED_DAY && date != anchorDate -> emptyList()
            !constraint.overnight -> listOf(
                BusyWindow(
                    startAt = ZonedDateTime.of(date, constraint.startTime, zoneId).toInstant(),
                    endAt = ZonedDateTime.of(date, constraint.endTime, zoneId).toInstant(),
                ),
            )
            task.schedulingMode == TaskSchedulingMode.FIXED_DAY && date == anchorDate -> listOf(
                BusyWindow(
                    startAt = ZonedDateTime.of(date, constraint.startTime, zoneId).toInstant(),
                    endAt = ZonedDateTime.of(date.plusDays(1), constraint.endTime, zoneId).toInstant(),
                ),
            )
            task.schedulingMode == TaskSchedulingMode.FIXED_DAY && date == anchorDate.plusDays(1) -> listOf(
                BusyWindow(
                    startAt = date.atStartOfDay(zoneId).toInstant(),
                    endAt = ZonedDateTime.of(date, constraint.endTime, zoneId).toInstant(),
                ),
            )
            task.schedulingMode == TaskSchedulingMode.FIXED_DAY -> emptyList()
            else -> listOf(
                BusyWindow(
                    startAt = ZonedDateTime.of(date, constraint.startTime, zoneId).toInstant(),
                    endAt = ZonedDateTime.of(date.plusDays(1), constraint.endTime, zoneId).toInstant(),
                ),
                BusyWindow(
                    startAt = date.atStartOfDay(zoneId).toInstant(),
                    endAt = ZonedDateTime.of(date, constraint.endTime, zoneId).toInstant(),
                ),
            )
        }
    }

    private fun windowEndInstant(
        date: LocalDate,
        localEnd: LocalTime,
        zoneId: ZoneId,
    ): Instant = if (localEnd == LocalTime.of(23, 59, 59)) {
        date.plusDays(1).atStartOfDay(zoneId).toInstant()
    } else {
        ZonedDateTime.of(date, localEnd, zoneId).toInstant()
    }

    private fun taskAllowsOverlap(
        task: ScheduleTask,
        allowConcurrentTasks: Boolean,
    ): Boolean {
        if (task.taskKind == TaskKind.SLEEP) return false
        if (!allowConcurrentTasks) return false
        return when (task.overlapPolicy) {
            TaskOverlapPolicy.ALLOW -> true
            TaskOverlapPolicy.DISALLOW -> false
        }
    }

    private fun tasksCanOverlap(
        first: ScheduleTask,
        second: ScheduleTask,
        allowConcurrentTasks: Boolean,
    ): Boolean = taskAllowsOverlap(first, allowConcurrentTasks) && taskAllowsOverlap(second, allowConcurrentTasks)

    private fun taskScore(
        task: ScheduleTask,
        rangeStart: Instant,
        policy: SchedulingPolicy,
    ): Double {
        val urgency = if (task.hasDeadline) {
            val minutesToDeadline = max(1, Duration.between(rangeStart, task.dueAt).toMinutes().toInt())
            policy.deadlineUrgencyWeight * (1440.0 / minutesToDeadline.toDouble())
        } else {
            0.0
        }
        val priority = policy.priorityWeight * task.priority.score.toDouble()
        val taskKindBoost = when (task.taskKind) {
            TaskKind.SLEEP -> 1000.0
            TaskKind.BLOCKER -> 500.0
            TaskKind.NORMAL -> 0.0
        }
        return taskKindBoost + urgency + priority
    }

    private fun minInstant(a: Instant, b: Instant): Instant = if (a <= b) a else b

    private fun maxInstant(a: Instant, b: Instant): Instant = if (a >= b) a else b

    private fun alignToNextAlignment(instant: Instant, zoneId: ZoneId, alignmentMinutes: Int): Instant {
        if (alignmentMinutes == 0) return instant
        val local = instant.atZone(zoneId).truncatedTo(ChronoUnit.MINUTES)
        val minute = local.minute
        val effectiveAlignment = alignmentMinutes.coerceAtLeast(1)
        val remainder = minute % effectiveAlignment
        val minutesToAdd = if (remainder == 0) 0 else effectiveAlignment - remainder
        return local
            .plusMinutes(minutesToAdd.toLong())
            .toInstant()
    }
}
