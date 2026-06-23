package dev.codex.reclaimoss.domain.scheduling

import dev.codex.reclaimoss.domain.model.*
import java.time.*

object CombinationTests {
    var extraPass = 0
    var extraFail = 0

    fun assert(name: String, condition: Boolean, detail: String = "") {
        if (condition) { extraPass++; println("  ✓ $name") }
        else { extraFail++; println("  ✗ FAIL: $name — $detail") }
    }

    fun run(
        tz: ZoneId,
        origAssert: (String, Boolean, String) -> Unit,
        schedule: (ZoneId, List<ScheduleTask>, Boolean, Boolean, Int, Int, Instant?, List<Timeframe>) -> SchedulePlan,
        scheduleWithHours: (ZoneId, List<ScheduleTask>, WorkHoursProfile, Boolean, Boolean, Int, Int, Instant?, List<Timeframe>) -> SchedulePlan,
        sleepTask: (String, Instant, Instant) -> ScheduleTask,
        normTask: (String, Instant?, Int, Boolean, Instant?, TaskPriority) -> ScheduleTask,
        overlaps: (ScheduleBlock, ScheduleBlock) -> Boolean,
        overlapsAny: (List<ScheduleBlock>, Instant, Instant) -> Boolean,
    ) {
        fun t(iso: String) = LocalDateTime.parse(iso).atZone(tz).toInstant()

        fun tf(name: String, start: String, end: String) = Timeframe(
            id = name, name = name,
            startDate = LocalDate.parse(start), endDate = LocalDate.parse(end),
            colorHex = "#FF0000",
        )

        // ═══════════════════════════════════════════════════════════════
        println("\n" + "═".repeat(50))
        println("COMBINATIONS: BLOCKER + SLEEP + TIMEFRAME")
        println("═".repeat(50))

        // ── E1: Blocker inside sleep window ──
        println("\n── E1. BLOCKER FIXED_EXACT during sleep ──")
        val planE1 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "nightMeeting", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T23:00"), fixedEndAt = t("2026-06-11T00:00"),
                dueAt = t("2026-06-11T00:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
        ), true, true, 0, 0, null, emptyList())
        val e1Sleep = planE1.blocks.filter { it.taskId == "sleep" }
        val e1Blocker = planE1.blocks.filter { it.taskId == "nightMeeting" }
        val e1Overlap = e1Blocker.any { b -> e1Sleep.any { s -> overlaps(s, b) } }
        println("  sleep: ${e1Sleep.map { "${it.startAt}→${it.endAt}" }}")
        println("  blocker placed: ${e1Blocker.isNotEmpty()}, overlap=$e1Overlap")
        println("  issues: ${planE1.issues.map { "${it.taskId}: ${it.reason}" }}")
        assert("E1: sleep hard-blocks blocker — no overlap", !e1Overlap)
        assert("E1: blocker unscheduled when inside sleep", e1Blocker.isEmpty(),
            "blocker was placed during sleep")

        // ── E2: BLOCKER with work minutes, fixed slot during sleep ──
        println("\n── E2. BLOCKER+work during sleep (reserved slot+work) ──")
        val planE2 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "meetingWork", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = true,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T23:00"), fixedEndAt = t("2026-06-11T00:00"),
                dueAt = t("2026-06-11T17:00"), estimatedMinutes = 120, remainingMinutes = 120,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, null, emptyList())
        val e2Blocks = planE2.blocks.filter { it.taskId == "meetingWork" }
        val e2SleepBlocks = planE2.blocks.filter { it.taskId == "sleep" }
        val e2Overlap = e2Blocks.any { b -> e2SleepBlocks.any { s -> overlaps(s, b) } }
        val e2Issue = planE2.issues.firstOrNull { it.taskId == "meetingWork" }
        println("  meeting blocks: ${e2Blocks.size}, placed at: ${e2Blocks.map { "${it.startAt}→${it.endAt}" }}")
        println("  issues: ${planE2.issues.map { "${it.taskId}: ${it.reason}" }}")
        assert("E2: no blocker+work during sleep", !e2Overlap,
            "work blocks overlap sleep")

        // ── E3: Blocker + sleep + timeframe triple combo ──
        println("\n── E3. Blocker + sleep + timeframe: work must respect all three ──")
        val planE3 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "meeting", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T14:00"), fixedEndAt = t("2026-06-10T15:00"),
                dueAt = t("2026-06-10T15:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "work", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = false, allowSplitting = true,
                schedulingMode = TaskSchedulingMode.FLEXIBLE,
                timeframeId = "Sprint3",
                fixedStartAt = t("2026-06-10T09:00"),
                dueAt = t("2026-06-15T17:00"), estimatedMinutes = 420, remainingMinutes = 420,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, null, listOf(
            tf("Sprint3", "2026-06-09", "2026-06-12"),
        ))
        val e3Blocks = planE3.blocks.filter { it.taskId == "work" }.sortedBy { it.startAt }
        val e3Total = e3Blocks.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }
        println("  work blocks: ${e3Blocks.size}, total=${e3Total}min")
        println("  blocks: ${e3Blocks.map { "${it.startAt.atZone(tz).toLocalDateTime()}→${it.endAt.atZone(tz).toLocalDateTime()}" }}")
        assert("Triple combo: work avoids blocker 2-3pm",
            !overlapsAny(e3Blocks, t("2026-06-10T14:00"), t("2026-06-10T15:00")))
        val e3InTimeframe = e3Blocks.all {
            val d = it.endAt.atZone(tz).toLocalDate()
            !d.isAfter(LocalDate.parse("2026-06-12"))
        }
        assert("Triple combo: all blocks within timeframe", e3InTimeframe,
            "end dates: ${e3Blocks.map { it.endAt.atZone(tz).toLocalDate() }}")
        println("  ** UNCERTAIN: how many blocks? exact placement? **")

        // ── E4: Two blockers overlapping each other (ALLOW vs DISALLOW) ──
        println("\n── E4. Two blockers at same time (ALLOW+ALLOW vs DISALLOW+DISALLOW) ──")
        val planE4a = schedule(tz, listOf(
            ScheduleTask(id = "bA", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T10:00"), fixedEndAt = t("2026-06-10T11:00"),
                dueAt = t("2026-06-10T11:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "bB", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T10:00"), fixedEndAt = t("2026-06-10T11:00"),
                dueAt = t("2026-06-10T11:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
        ), true, true, 0, 0, null, emptyList())
        val e4aB1 = planE4a.blocks.filter { it.taskId == "bA" }
        val e4aB2 = planE4a.blocks.filter { it.taskId == "bB" }
        println("  [ALLOW+ALLOW] bA: ${e4aB1.size}, bB: ${e4aB2.size}")
        val e4aOverlap = e4aB1.any { a -> e4aB2.any { b -> overlaps(a, b) } }
        println("  [ALLOW+ALLOW] overlap: $e4aOverlap")

        val planE4b = schedule(tz, listOf(
            ScheduleTask(id = "bA2", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T10:00"), fixedEndAt = t("2026-06-10T11:00"),
                dueAt = t("2026-06-10T11:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "bB2", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T10:00"), fixedEndAt = t("2026-06-10T11:00"),
                dueAt = t("2026-06-10T11:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
        ), true, true, 0, 0, null, emptyList())
        val e4bB1 = planE4b.blocks.filter { it.taskId == "bA2" }
        val e4bB2 = planE4b.blocks.filter { it.taskId == "bB2" }
        val e4bBoth = e4bB1.isNotEmpty() && e4bB2.isNotEmpty()
        val e4bOverlap = e4bB1.any { a -> e4bB2.any { b -> overlaps(a, b) } }
        println("  [DISALLOW+DISALLOW] bA: ${e4bB1.size}, bB: ${e4bB2.size}, both=$e4bBoth, overlap=$e4bOverlap")
        println("  ** UNCERTAIN: two same-time DISALLOW blockers — both placed? one dropped? **")

        // ── E5: Dependency chain: parent blocked by blocker → child delayed ──
        println("\n── E5. Dependency chain through blocker ──")
        val planE5 = schedule(tz, listOf(
            ScheduleTask(id = "meeting", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T10:00"), fixedEndAt = t("2026-06-10T11:00"),
                dueAt = t("2026-06-10T11:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "parent", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = false, allowSplitting = true,
                schedulingMode = TaskSchedulingMode.FLEXIBLE,
                fixedStartAt = t("2026-06-10T09:00"),
                dueAt = t("2026-06-10T17:00"), estimatedMinutes = 120, remainingMinutes = 120,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "child", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FLEXIBLE,
                continuationParentTaskId = "parent",
                continuationMode = TaskContinuationMode.AFTER_PARENT_SCHEDULED_END,
                dueAt = t("2026-06-10T17:00"), estimatedMinutes = 30, remainingMinutes = 30,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, t("2026-06-10T08:00"), emptyList())
        val e5Parent = planE5.blocks.filter { it.taskId == "parent" }.sortedBy { it.startAt }
        val e5Child = planE5.blocks.firstOrNull { it.taskId == "child" }
        val e5ParentEnd = e5Parent.maxOfOrNull { it.endAt } ?: Instant.MIN
        println("  parent blocks: ${e5Parent.map { "${it.startAt}→${it.endAt}" }}")
        println("  child: ${e5Child?.startAt}→${e5Child?.endAt}")
        assert("Chain-blocker: child after parent ends", e5Child != null &&
            e5Child.startAt >= e5ParentEnd,
            "child starts=${e5Child?.startAt} parent ends=$e5ParentEnd")
        println("  ** UNCERTAIN: parent split into 2 blocks? child at 12pm? **")

        // ── E6: Sleep + timeframe for work, blocker inside timeframe ──
        println("\n── E6. Work in timeframe with sleep + blocker on different days ──")
        val planE6 = schedule(tz, listOf(
            sleepTask("sleep-d1", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "meeting", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T14:00"), fixedEndAt = t("2026-06-10T15:00"),
                dueAt = t("2026-06-10T15:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "work", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = false, allowSplitting = true,
                schedulingMode = TaskSchedulingMode.FLEXIBLE,
                timeframeId = "TF",
                fixedStartAt = t("2026-06-10T09:00"),
                dueAt = t("2026-06-15T17:00"), estimatedMinutes = 240, remainingMinutes = 240,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, null, listOf(
            tf("TF", "2026-06-10", "2026-06-11"),
        ))
        val e6Blocks = planE6.blocks.filter { it.taskId == "work" }.sortedBy { it.startAt }
        val e6Total = e6Blocks.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }
        println("  work: ${e6Blocks.size} blocks, ${e6Total}min total")
        println("  blocks: ${e6Blocks.map { "${it.startAt.atZone(tz).toLocalDateTime()}→${it.endAt.atZone(tz).toLocalDateTime()}" }}")
        assert("E6: full 240min scheduled", e6Total.toInt() == 240, "got $e6Total")
        assert("E6: no overlap with blocker", !overlapsAny(e6Blocks, t("2026-06-10T14:00"), t("2026-06-10T15:00")))
        assert("E6: all in timeframe", e6Blocks.all {
            val d = it.endAt.atZone(tz).toLocalDate()
            d <= LocalDate.parse("2026-06-11")
        })
        println("  ** UNCERTAIN: work splits into 2+ blocks across 2 days? **")

        // ── E7: FIXED_DAY with blocker + sleep same day ──
        println("\n── E7. FIXED_DAY with sleep+blocker: 60min on tight day ──")
        val planE7 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "allday", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T09:00"), fixedEndAt = t("2026-06-10T17:00"),
                dueAt = t("2026-06-10T17:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "mustToday", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = true, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_DAY,
                dueAt = t("2026-06-10T23:59"), estimatedMinutes = 60, remainingMinutes = 60,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, t("2026-06-10T00:00"), emptyList())
        val e7Blocks = planE7.blocks.filter { it.taskId == "mustToday" }
        val e7Issue = planE7.issues.firstOrNull { it.taskId == "mustToday" }
        val e7InDay = e7Blocks.all {
            val d = it.startAt.atZone(tz).toLocalDate()
            d == LocalDate.parse("2026-06-10")
        }
        println("  blocks: ${e7Blocks.size}, in day: $e7InDay, issue: ${e7Issue?.type}: ${e7Issue?.reason}")
        println("  ** UNCERTAIN: FIXED_DAY task — unscheduled or pushes past blocker? **")

        // ── E8: FLEXIBLE_WINDOW task overlapping sleep ──
        println("\n── E8. FLEXIBLE_WINDOW (10pm-12am) overlapping sleep (10pm-6am) ──")
        val planE8 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "window", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = true, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FLEXIBLE_WINDOW,
                fixedStartAt = t("2026-06-10T22:00"), fixedEndAt = t("2026-06-11T00:00"),
                dueAt = t("2026-06-11T00:00"), estimatedMinutes = 60, remainingMinutes = 60,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, t("2026-06-10T20:00"), emptyList())
        val e8Block = planE8.blocks.firstOrNull { it.taskId == "window" }
        val e8Issue = planE8.issues.firstOrNull { it.taskId == "window" }
        println("  scheduled: ${e8Block != null}, issue: ${e8Issue?.type}: ${e8Issue?.reason}")
        val e8Overlaps = e8Block != null && planE8.blocks.filter { it.taskId == "sleep" }
            .any { s -> overlaps(s, e8Block) }
        println("  overlap with sleep: $e8Overlaps")
        println("  ** UNCERTAIN: window task entirely inside sleep — unscheduled? **")

        // ── E9: Dependency BEFORE_PARENT + blocker + sleep ──
        println("\n── E9. Before-parent task blocked by sleep + blocker ──")
        val planE9 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-09T22:00"), t("2026-06-10T06:00")),
            ScheduleTask(id = "meeting", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-09T14:00"), fixedEndAt = t("2026-06-09T15:00"),
                dueAt = t("2026-06-09T15:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
            normTask("parent", t("2026-06-10T08:00"), 60, false, t("2026-06-10T17:00")),
            ScheduleTask(id = "prep", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = false, allowSplitting = true,
                schedulingMode = TaskSchedulingMode.FLEXIBLE,
                continuationParentTaskId = "parent",
                continuationMode = TaskContinuationMode.BEFORE_PARENT_START,
                fixedStartAt = t("2026-06-09T12:00"),
                dueAt = t("2026-06-10T17:00"), estimatedMinutes = 180, remainingMinutes = 180,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, t("2026-06-09T00:00"), emptyList())
        val e9Prep = planE9.blocks.filter { it.taskId == "prep" }
        val e9Parent = planE9.blocks.firstOrNull { it.taskId == "parent" }
        val e9PrepTotal = e9Prep.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }
        val e9AllBefore = e9Parent?.let { p ->
            e9Prep.all { it.endAt <= p.startAt }
        } ?: true
        println("  prep: ${e9Prep.size} blocks, ${e9PrepTotal}min, all before parent: $e9AllBefore")
        println("  prep blocks: ${e9Prep.map { "${it.startAt.atZone(tz).toLocalDateTime()}→${it.endAt.atZone(tz).toLocalDateTime()}" }}")
        assert("Before-child: full 180min scheduled", e9PrepTotal.toInt() == 180, "got $e9PrepTotal")
        assert("Before-child: all before parent", e9AllBefore,
            "prep ends=${e9Prep.maxOfOrNull { it.endAt }} parent starts=${e9Parent?.startAt}")
        println("  ** UNCERTAIN: prep splits around blocker? single block? **")

        // ── E10: Timeframe ending during sleep ──
        println("\n── E10. Timeframe ends at midnight during sleep ──")
        val planE10 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "work", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = false, allowSplitting = true,
                schedulingMode = TaskSchedulingMode.FLEXIBLE,
                timeframeId = "Short",
                fixedStartAt = t("2026-06-10T20:00"),
                dueAt = t("2026-06-15T17:00"), estimatedMinutes = 180, remainingMinutes = 180,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, t("2026-06-10T20:00"), listOf(
            tf("Short", "2026-06-09", "2026-06-10"),
        ))
        val e10Blocks = planE10.blocks.filter { it.taskId == "work" }
        val e10Total = e10Blocks.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }
        val e10InTimeframe = e10Blocks.all {
            !it.endAt.atZone(tz).toLocalDate().isAfter(LocalDate.parse("2026-06-10"))
        }
        println("  work: ${e10Blocks.size} blocks, ${e10Total}min, in timeframe: $e10InTimeframe")
        println("  blocks: ${e10Blocks.map { "${it.startAt.atZone(tz).toLocalDateTime()}→${it.endAt.atZone(tz).toLocalDateTime()}" }}")
        println("  ** UNCERTAIN: work truncated at timeframe end (midnight) or sleep start (10pm)? **")

        // ── E11: BLOCKER with timeframe constraint overlapping sleep ──
        println("\n── E11. BLOCKER in timeframe, fixed slot during sleep ──")
        val planE11 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "lateMeeting", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                timeframeId = "SprintE11",
                fixedStartAt = t("2026-06-10T23:00"), fixedEndAt = t("2026-06-11T00:00"),
                dueAt = t("2026-06-11T00:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
        ), true, true, 0, 0, null, listOf(
            tf("SprintE11", "2026-06-09", "2026-06-12"),
        ))
        val e11Blocker = planE11.blocks.filter { it.taskId == "lateMeeting" }
        val e11Sleep = planE11.blocks.filter { it.taskId == "sleep" }
        val e11Overlap = e11Blocker.any { b -> e11Sleep.any { s -> overlaps(s, b) } }
        println("  blocker placed: ${e11Blocker.isNotEmpty()}, overlap=$e11Overlap")
        println("  issues: ${planE11.issues.map { "${it.taskId}: ${it.reason}" }}")
        assert("E11: blocker in timeframe during sleep — no overlap", !e11Overlap,
            "blocker overlaps sleep in timeframe")

        // ── E12: Sleep + 0-minute blocker at exact sleep boundary ──
        println("\n── E12. Zero-min blocker at sleep edge (10pm exact) ──")
        val planE12 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "edgeBlocker", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T22:00"), fixedEndAt = t("2026-06-10T22:00"),
                dueAt = t("2026-06-10T22:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
        ), true, true, 0, 0, null, emptyList())
        val e12Blocker = planE12.blocks.filter { it.taskId == "edgeBlocker" }
        val e12Sleep = planE12.blocks.filter { it.taskId == "sleep" }
        println("  blocker placed: ${e12Blocker.isNotEmpty()}")
        println("  blocker: ${e12Blocker.map { "${it.startAt}→${it.endAt}" }}")
        println("  sleep: ${e12Sleep.map { "${it.startAt}→${it.endAt}" }}")
        val e12Overlap = e12Blocker.any { b -> e12Sleep.any { s -> overlaps(s, b) } }
        println("  overlap: $e12Overlap")
        println("  ** UNCERTAIN: 0-min blocker at sleep start — conflict? placed? **")

        // ── E13: All constraints simultaneously ──
        println("\n── E13. All constraints: sleep + blocker + timeframe + dependency ──")
        val planE13 = schedule(tz, listOf(
            sleepTask("sleep", t("2026-06-10T22:00"), t("2026-06-11T06:00")),
            ScheduleTask(id = "meeting", title = "", taskKind = TaskKind.BLOCKER,
                priority = TaskPriority.HIGH, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FIXED_EXACT,
                fixedStartAt = t("2026-06-10T14:00"), fixedEndAt = t("2026-06-10T15:00"),
                dueAt = t("2026-06-10T15:00"), estimatedMinutes = 0, remainingMinutes = 0,
                overlapPolicy = TaskOverlapPolicy.DISALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "parent", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = false, allowSplitting = false,
                schedulingMode = TaskSchedulingMode.FLEXIBLE,
                fixedStartAt = t("2026-06-10T09:00"),
                dueAt = t("2026-06-10T17:00"), estimatedMinutes = 60, remainingMinutes = 60,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
            ScheduleTask(id = "child", title = "", taskKind = TaskKind.NORMAL,
                priority = TaskPriority.MEDIUM, hasDeadline = false, allowSplitting = true,
                schedulingMode = TaskSchedulingMode.FLEXIBLE,
                timeframeId = "TFE13",
                continuationParentTaskId = "parent",
                continuationMode = TaskContinuationMode.AFTER_PARENT_SCHEDULED_END,
                dueAt = t("2026-06-15T17:00"), estimatedMinutes = 300, remainingMinutes = 300,
                overlapPolicy = TaskOverlapPolicy.ALLOW, status = TaskStatus.ACTIVE),
        ), false, true, 0, 0, t("2026-06-10T08:00"), listOf(
            tf("TFE13", "2026-06-10", "2026-06-11"),
        ))
        val e13Child = planE13.blocks.filter { it.taskId == "child" }.sortedBy { it.startAt }
        val e13Parent = planE13.blocks.firstOrNull { it.taskId == "parent" }
        val e13Total = e13Child.sumOf { Duration.between(it.startAt, it.endAt).toMinutes() }
        println("  child: ${e13Child.size} blocks, ${e13Total}min")
        println("  child blocks: ${e13Child.map { "${it.startAt.atZone(tz).toLocalDateTime()}→${it.endAt.atZone(tz).toLocalDateTime()}" }}")
        assert("E13: full 300min scheduled", e13Total.toInt() == 300, "got $e13Total")
        assert("E13: child after parent", e13Parent?.let { p ->
            e13Child.all { it.startAt >= p.endAt }
        } ?: false, "child starts=${e13Child.firstOrNull()?.startAt} parent ends=${e13Parent?.endAt}")
        assert("E13: no overlap with blocker",
            !overlapsAny(e13Child, t("2026-06-10T14:00"), t("2026-06-10T15:00")))
        assert("E13: all in timeframe", e13Child.all {
            !it.endAt.atZone(tz).toLocalDate().isAfter(LocalDate.parse("2026-06-11"))
        })
        println("  ** UNCERTAIN: child split — 2 blocks? exact placement? **")
    }
}
