package dev.codex.reclaimoss.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TaskPriority(val score: Int) {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    URGENT(4),
}

enum class TaskStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

enum class ReminderStatus {
    ACTIVE,
    COMPLETED,
    SNOOZED,
}

enum class PreferredTimeOfDay {
    ANYTIME,
    MORNING,
    NOON,
    AFTERNOON,
    NIGHT,
}

enum class RecurrenceType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
}

enum class RecurrenceEndMode {
    NEVER,
    ON_DATE,
    AFTER_OCCURRENCES,
}

enum class BlockSource {
    AUTO,
    MANUAL,
}

enum class BlockLockState {
    FLEXIBLE,
    LOCKED,
}

enum class BlockCompletionState {
    PENDING,
    COMPLETED,
    MISSED,
}

enum class TimePeriodType {
    PRODUCTIVE,
    LIFE,
}

enum class SchedulingIssueType {
    PARTIAL,
    UNSCHEDULED,
}

enum class TaskSchedulingMode {
    FLEXIBLE,
    FLEXIBLE_TIME,
    FLEXIBLE_WINDOW,
    FIXED_DAY,
    FIXED_EXACT,
}

enum class TaskKind {
    NORMAL,
    SLEEP,
    BLOCKER,
}

enum class TaskContinuationMode {
    AFTER_PARENT_SCHEDULED_END,
    AFTER_PARENT_DUE_AT,
    BEFORE_PARENT_START,
}

enum class TaskOverlapPolicy {
    ALLOW,
    DISALLOW,
}

data class TimeWindow(
    val start: LocalTime,
    val end: LocalTime,
)

data class WorkHoursDay(
    val windows: List<TimeWindow>,
)

data class WorkHoursProfile(
    val timezone: String,
    val days: Map<DayOfWeek, WorkHoursDay>,
)

data class TimePeriod(
    val id: String,
    val label: String,
    val start: LocalTime,
    val end: LocalTime,
    val type: TimePeriodType = TimePeriodType.PRODUCTIVE,
    val sortOrder: Int = 0,
)

data class SchedulingPolicy(
    val minBlockMinutes: Int,
    val maxBlockMinutes: Int,
    val breakBetweenBlocksMinutes: Int,
    val priorityWeight: Double,
    val deadlineUrgencyWeight: Double,
    val lookAheadDays: Int,
    val alignmentMinutes: Int = 30,
    val allowTaskSplitting: Boolean = true,
    val strictPreferredPeriod: Boolean = false,
    val allowConcurrentTasks: Boolean = false,
)

data class Project(
    val id: String,
    val name: String,
    val colorHex: String,
    val defaultPriority: TaskPriority,
    val archived: Boolean = false,
)

data class Timeframe(
    val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val colorHex: String,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class ReminderPolicy(
    val remindBeforeMinutes: Int,
    val remindOverdueMinutes: Int,
    val remindMissedMinutes: Int,
)

data class RecurrenceRule(
    val type: RecurrenceType = RecurrenceType.NONE,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val until: Instant? = null,
    val endMode: RecurrenceEndMode = RecurrenceEndMode.NEVER,
    val occurrenceCount: Int? = null,
) {
    constructor(type: RecurrenceType) : this(
        type = type,
        interval = 1,
        daysOfWeek = emptySet(),
        until = null,
        endMode = RecurrenceEndMode.NEVER,
        occurrenceCount = null,
    )

    constructor(
        type: RecurrenceType,
        daysOfWeek: Set<DayOfWeek>,
        until: Instant? = null,
    ) : this(
        type = type,
        interval = 1,
        daysOfWeek = daysOfWeek,
        until = until,
        endMode = if (until != null) RecurrenceEndMode.ON_DATE else RecurrenceEndMode.NEVER,
        occurrenceCount = null,
    )
}

data class ScheduleTask(
    val id: String,
    val recurrenceSeriesId: String? = null,
    val title: String,
    val description: String = "",
    val projectId: String? = null,
    val timeframeId: String? = null,
    val taskKind: TaskKind = TaskKind.NORMAL,
    val priority: TaskPriority,
    val preferredTimeOfDay: PreferredTimeOfDay = PreferredTimeOfDay.ANYTIME,
    val preferredTimePeriodId: String? = null,
    val hasDeadline: Boolean = true,
    val continuationParentTaskId: String? = null,
    val continuationMode: TaskContinuationMode? = null,
    val noGap: Boolean = false,
    val overlapPolicy: TaskOverlapPolicy = TaskOverlapPolicy.ALLOW,
    val allowSplitting: Boolean = true,
    val schedulingMode: TaskSchedulingMode = TaskSchedulingMode.FLEXIBLE,
    val notBeforeAt: Instant? = null,
    val fixedStartAt: Instant? = null,
    val fixedEndAt: Instant? = null,
    val dueAt: Instant,
    val estimatedMinutes: Int,
    val remainingMinutes: Int,
    val recurrenceRule: RecurrenceRule = RecurrenceRule(),
    val reminderPolicy: ReminderPolicy = ReminderPolicy(10, 5, 0),
    val status: TaskStatus = TaskStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class Reminder(
    val id: String,
    val title: String,
    val description: String = "",
    val dueAt: Instant,
    val isAllDay: Boolean = false,
    val recurrenceRule: RecurrenceRule = RecurrenceRule(),
    val linkedTaskId: String? = null,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

data class ScheduleBlock(
    val id: String,
    val taskId: String,
    val startAt: Instant,
    val endAt: Instant,
    val source: BlockSource,
    val lockState: BlockLockState,
    val completionState: BlockCompletionState,
    val externalCalendarEventId: String?,
)

data class SchedulePlan(
    val blocks: List<ScheduleBlock>,
    val unscheduledTaskIds: List<String>,
    val issues: List<SchedulingIssue> = emptyList(),
)

data class SchedulingIssue(
    val taskId: String,
    val type: SchedulingIssueType,
    val unscheduledMinutes: Int,
    val reason: String,
)
