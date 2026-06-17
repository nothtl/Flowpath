package dev.codex.reclaimoss.ui

import java.time.DayOfWeek

data class SleepOnboardingEntryDraft(
    val weekdays: Set<DayOfWeek> = emptySet(),
    val windowStart: java.time.LocalTime = java.time.LocalTime.of(22, 0),
    val windowEnd: java.time.LocalTime = java.time.LocalTime.of(8, 0),
    val endsNextDay: Boolean = true,
    val durationMinutes: Int = 8 * 60,
)

fun coveredSleepWeekdays(entries: List<SleepOnboardingEntryDraft>): Set<DayOfWeek> =
    entries.flatMapTo(linkedSetOf()) { it.weekdays }

fun missingSleepWeekdays(entries: List<SleepOnboardingEntryDraft>): Set<DayOfWeek> =
    DayOfWeek.entries.filterNotTo(linkedSetOf()) { it in coveredSleepWeekdays(entries) }

fun unavailableSleepWeekdays(
    entries: List<SleepOnboardingEntryDraft>,
    selectedDays: Set<DayOfWeek>,
    editingIndex: Int,
): Set<DayOfWeek> {
    val otherEntries = entries.filterIndexed { index, _ -> index != editingIndex }
    return otherEntries.flatMapTo(linkedSetOf()) { it.weekdays }
}
