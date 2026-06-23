# Scheduler Logic

## What the Scheduler Does

The scheduler takes a set of tasks and produces a plan — deciding when each task should happen, for how long, and which tasks cannot fit. It balances deadlines, priorities, dependencies, fixed meetings, sleep hours, timeframes, and work hours to build a realistic daily schedule.

**Inputs:**
- A list of tasks, each with a duration, priority, deadline, scheduling mode, and optional constraints
- Timeframes (date ranges that tasks may be confined to)
- Existing blocks from a previous schedule run
- Calendar busy windows (external events)
- Work hours (which hours on which days are available)
- A scheduling policy (block sizes, alignment, concurrency settings)

**Output:**
- A list of scheduled blocks — each with a start time, end time, and which task it belongs to
- A list of task IDs that could not be scheduled at all
- A list of issues describing what went wrong (partial scheduling or fully unscheduled, with a reason)

---

## Task Ordering

Not all tasks are equal. The scheduler sorts them before placing anything, so sleep and meetings get priority over flexible work.

**Sort order:**

1. **Sleep first** — Sleep blocks must be locked in before any other task is considered. This ensures work cannot accidentally occupy sleep time.
2. **Blockers second** — Fixed-time meetings and blockers come next. Their reserved slots occupy time for everything that follows.
3. **Normal tasks last** — Flexible work tasks are scheduled into whatever time remains.

Within each category:
- Tasks **with deadlines** come before tasks without (backlog goes last).
- Tasks with **existing blocks** from a previous run are ordered by their anchor time — the one that was placed earliest goes first.
- Otherwise, tasks are ordered by a **score** that combines priority and deadline urgency. Higher priority and closer deadlines rank higher.
- Due date is the final tiebreaker: earlier deadline first.

### Dependencies

If a task depends on another (e.g., "review draft" must happen after "write draft"), the scheduler reorders so the parent comes before the child. This is a topological sort — parent tasks always schedule before their children.

If there's a circular dependency (A depends on B, B depends on A), the scheduler breaks the cycle: one task is freed from its dependency constraint and scheduled normally, so at least one of them gets placed.

---

## The Main Scheduling Loop

Each task is processed one at a time, in the order determined above.

### Step 1: Determine Boundaries

The scheduler computes the **earliest possible start** by taking the maximum of:
- The overall range start (typically "now" or the start of the scheduling window)
- Any fixed start time the task specifies
- The start of its timeframe, if it has one
- Any dependency boundary from its parent (e.g., parent must finish first)
- Any "not before" constraint

The **absolute latest end** is the minimum of:
- The task's own deadline
- The end of its timeframe, if it has one
- Any dependency boundary from its parent (e.g., must finish before parent starts)

### Step 2: Preserve Existing Blocks

If the task was scheduled before and still has blocks:
- Each existing block is validated against all current constraints (deadline, timeframe, work hours, occupied time).
- If **all** blocks still fit, they are kept as-is. The task's remaining work is reduced by their total duration.
- If **any** block no longer fits, all of them are discarded and the task is rescheduled from scratch.

Locked blocks (manually pinned by the user) are never moved or discarded. Completed blocks are treated as permanently occupied time.

### Step 3: Blocker Fixed-Time Reservation

If the task is a blocker with an exact fixed time (like a calendar meeting), the scheduler first tries to reserve that exact slot. The reservation succeeds only if:
- No already-placed block (sleep, another meeting, a completed task) occupies that time
- The slot falls within the task's timeframe
- It respects all dependency boundaries
- It starts after the range start

If any of these checks fail, the reservation is rejected. The entire task — meeting and any associated work — is marked unscheduled.

If the reservation succeeds, the fixed slot is placed. If the task also has work minutes beyond the meeting itself, that work is scheduled afterward using the normal placement loop.

### Step 4: Place Remaining Work

The scheduler runs a loop: find the next free slot, place a block, reduce remaining work, repeat. This continues until either all work is placed or the lookahead horizon (default 14 days) is exhausted.

**Cursor:** A pointer that advances forward in time as blocks are placed. Each new block starts at or after the cursor.

**Splitting:** If a task is allowed to split and has no dependency constraints, portions that don't fit before the deadline can extend past it. This prevents tasks from being stuck as permanently partial — they can finish slightly late. Dependency-constrained tasks cannot do this; they must fit entirely within their boundary.

**Gaps:** Between blocks of the same task, there is no gap — split portions are contiguous. Between different tasks, a break buffer (configurable) is added so back-to-back work from different tasks has breathing room.

### Step 5: Report Issues

If any work minutes remain unplaced after the loop:
- If some minutes were placed: **partial** scheduling, with the remainder noted.
- If zero minutes were placed: **fully unscheduled**, with a reason (no valid slot, day unavailable, window too tight, etc.).

---

## Scheduling Modes

Each task has a scheduling mode that determines how its time is chosen.

### Flexible (default)

The task can go anywhere before its deadline, on any day within the lookahead window.

If the task specifies both a fixed start time and a fixed end time (as time-of-day values), it gains a **daily window constraint**. For example, a task with "9am to 5pm" can only be placed during those hours each day — not at midnight or 7pm. Overnight windows (e.g., "10pm to 6am") are handled by splitting the window across midnight.

### Flexible Time

The task must start at a specific time of day, but can be on any calendar date. For example, "start at 10am, any day this week." The scheduler checks each candidate day at that exact time; if it fits, the task goes there. If not, it moves to the next day.

### Flexible Window

The task must fit entirely within an absolute window — a specific start datetime and end datetime. For example, "sometime between Tuesday 3pm and Tuesday 5pm." The scheduler searches within that window for the best placement. The window does not span multiple dates; only the start date is used.

### Fixed Day

The task must happen on a specific calendar day (the day of its due date). The entire duration must fit within work hours on that single day. If it doesn't fit, it's partial or unscheduled.

### Fixed Exact

The task has an exact start and end time — a fixed slot. Typically used for meetings and blockers. The scheduler reserves this slot (if possible) and blocks other tasks from using it. If the slot conflicts with sleep or another meeting, the task is unscheduled.

For normal (non-blocker) tasks with Fixed Exact mode: the scheduler treats them as flexible, using the fixed start as a preference rather than a hard requirement. The exact slot is not guaranteed.

### Recurring Tasks

Each occurrence of a recurring task is treated as its own independent task, anchored to its due date's day. There is no special recurrence logic inside the scheduler — recurrence expansion happens outside, and the scheduler sees individual tasks.

---

## How Free Time Is Found

The scheduler scans forward day by day from the cursor.

1. **Get work windows** for the current day (e.g., "9am to 12pm, 1pm to 5pm"). If the day has no windows (like weekends), skip to the next day.
2. **Subtract busy time**: sleep blocks, meeting blocks, completed tasks, calendar events, and other tasks' blocks (if overlap isn't allowed). What remains are free segments.
3. **Find a candidate** within those free segments that fits the remaining work.

The scheduler continues scanning forward until it finds a fit or runs out of lookahead.

### Overnight Windows

Work hours can span midnight. The scheduler handles this by splitting the window at midnight and treating the two portions as belonging to different days.

### Subtracting Busy Time

When multiple busy windows overlap or touch each other, they are merged into larger occupied regions. The free segments are the gaps between these regions.

---

## How Blocks Are Sized and Aligned

### Alignment

Blocks are snapped to an alignment grid (typically 30 minutes). A block proposed to start at 9:07am would be rounded up to 9:30am. This keeps the schedule visually clean and avoids arbitrary start times.

### Minimum and Maximum Block Size

Every block must be at least the minimum block duration (default 30 minutes). If a free segment is smaller than the minimum, it's skipped.

Blocks cannot exceed the maximum block duration (default 8 hours). If a task has more remaining work than the maximum, it will be split.

### Splitting

If a task is allowed to split (both globally and per-task), large tasks are broken into multiple blocks at natural break points — around meetings, across sleep, or across days. The first portion is placed at the earliest good slot, and the remainder continues from where it left off. Split blocks of the same task are always contiguous.

### Break Buffers

A configurable break buffer (default 0 minutes) is added between different tasks. When task A finishes and task B starts, the cursor advances by the break buffer before placing B. Split blocks within the same task skip the break buffer.

### Leftover Gap Handling

When splitting a task into a free segment, if the leftover free space after the block is smaller than the minimum block size, the scheduler consumes the entire free segment. This prevents tiny unusable gaps. For example, if a 60-minute task splits into a 100-minute free segment, taking 60 would leave 40 — but if the minimum block is 30 and the next smallest task also doesn't fit in 40, the scheduler just takes all 100.

---

## Overlap and Concurrency

### Hard vs Soft Occupied Time

The scheduler divides other tasks' blocks into two categories:

- **Hard occupied**: Time that this task absolutely cannot use. Includes sleep, meetings with DISALLOW overlap, completed tasks, calendar events, and any task where either party doesn't allow overlap.
- **Soft occupied**: Time from tasks where both parties allow overlap. The current task CAN be placed here, but will try to minimize how much it overlaps.

### Concurrency

Concurrency (overlapping tasks) is controlled by a global setting and a per-task overlap policy:
- If global concurrency is **off**, no tasks overlap.
- If global concurrency is **on**, each task's overlap policy decides:
  - ALLOW: the task can share time with other ALLOW tasks.
  - DISALLOW: the task cannot share time with anyone.

### Sleep Is Always Hard

Sleep tasks hard-block all other tasks unconditionally. Even if concurrency is enabled and a task has ALLOW overlap, it can never occupy sleep time. Blocker meetings are also blocked by sleep — a meeting at 11pm during sleep hours will be unscheduled.

### Concurrent Placement

When concurrency is allowed and a task has no fixed start time, the scheduler scans all free segments across all days and picks the best spot — minimizing the number of soft-occupied windows it overlaps and preferring positions near the midpoint of the free segment. This produces evenly-distributed concurrent tasks rather than all piling up at the start of the day.

Subsequent split portions of the same task use non-concurrent placement — they go into the earliest available slot.

### Blocker Reservation Check

Before a blocker's fixed time slot is reserved, the scheduler checks only the hard-occupied time — it does not consider soft-occupied windows. This means a blocker can share time with another blocker if both allow overlap, but cannot overlap sleep or a DISALLOW blocker.

---

## Dependencies

Tasks can be linked to a parent task. Three dependency modes control how the child is positioned.

### After Parent's Scheduled End

The child starts after the parent's last block finishes. This is the default.

**Break buffer:** If the child has "no gap" turned off, the scheduler adds the global break buffer between the parent's end and the child's start. If "no gap" is on, the child hugs the parent's end with zero minutes between them.

### After Parent's Deadline

The child starts after the parent's due date, regardless of when the parent was actually scheduled. Even if the parent was placed early, the child waits until after the deadline.

### Before Parent Starts

The child must finish before the parent's first block begins. The child's absolute end boundary is capped at the parent's start time. This is useful for preparation tasks that must be done before a meeting.

### Effect on Splitting

Tasks with any dependency constraint cannot extend past their deadline for splitting. They must finish entirely within their boundary. A preparation task that depends on a parent at 9am cannot have a split portion after 9am.

### Ordering

Dependencies are resolved before scheduling begins. The scheduler performs a topological sort so parents always come before children in the processing order. This ensures the parent's blocks exist when the child's dependency boundary is computed.

---

## Timeframes

A timeframe is a date range that constrains when a task can be scheduled. Any task linked to a timeframe:

- Cannot have blocks starting before the timeframe's start date (midnight of that day)
- Cannot have blocks ending after the timeframe's end date (end of that day)

If a task's deadline falls after the timeframe ends, the timeframe end wins — the task must be done by then. The effective deadline becomes the earlier of the real deadline and the timeframe end.

Timeframes do not affect task ordering or priority. They are purely a boundary constraint.

---

## Work Hours

Work hours define which hours are available on which days. Each day of the week has a list of time windows (e.g., Monday: 9am-12pm, 1pm-5pm). Days with no windows (e.g., Saturday and Sunday) are off — no tasks can be placed there.

A block is valid only if its entire duration falls within one or more of the day's windows. Blocks that cross midnight are split into per-day segments, and each segment is validated separately.

Work hours are used as candidate segments during the day-by-day scan. The scheduler only considers time that falls within a work window.

### Preferred Time Periods

Each task can have a preferred time period (e.g., "Morning" or "Deep Work"). Currently, this preference is stored but not enforced by the scheduler. The task will be placed at any available time regardless of period preference.

---

## Existing Blocks and Rebuilds

When the scheduler runs a rebuild (not the first run), it may have blocks from a previous schedule. These are handled as follows:

**Completed blocks:** Locked in permanently. They occupy time and cannot be overlapped by any new block. They are always included in the output.

**Locked pending blocks:** Blocks the user has manually pinned. They are always kept and never moved. They occupy time for subsequent tasks.

**Flexible pending blocks:** Blocks the scheduler created previously. They are validated against current constraints. If the task's parameters haven't changed (same duration, same deadline, same timeframe) and the blocks still fit, they're preserved. If anything changed, they're discarded and the task is rescheduled.

Blocks from tasks not in the current scheduling loop (e.g., tasks with zero remaining that still hold active blocks) are carried forward as orphaned blocks.

---

## Post-Processing

### Coalescing

After all tasks are scheduled, blocks that belong to the same task and touch each other (one ends exactly where the next starts) are merged into a single continuous block. No need for three separate 30-minute blocks that form one 90-minute stretch — they become one block.

Only auto-scheduled, flexible, pending blocks are coalesced. Manual blocks and completed blocks are left as-is.

### Orphaned Blocks

Any pending block that belongs to a task not in the scheduling loop (e.g., a task with zero remaining minutes whose block is still occupying time) is preserved and included in the output.

---

## Task Scoring

The scheduler scores each task to determine ordering within equal-priority groups. The score has three components:

**Kind boost:** Sleep tasks get the highest boost (ensuring they sort first). Blockers get a medium boost. Normal tasks get none.

**Deadline urgency:** The closer the deadline, the higher the urgency. The formula is inversely proportional to minutes remaining until the deadline. A task due in 30 minutes scores much higher than one due in 7 days.

**Priority:** Each priority level has a numeric weight. URGENT scores higher than HIGH, which scores higher than MEDIUM, which scores higher than LOW.

The score is used only for ordering — it does not affect which slot a task gets, only in what order tasks are considered.

---

## Known Limitations

**Preferred time periods are not enforced.** The `strictPreferredPeriod` policy exists but the scheduler does not filter free segments by time period. A task preferring "Morning" may still be placed in the afternoon.

**Fixed Exact mode for normal tasks is treated as flexible.** Only blocker tasks get guaranteed exact-time placement. Normal tasks with Fixed Exact mode are scheduled flexibly — the fixed time acts as a preference, not a guarantee.

**Flexible Window only uses the start date.** An overnight window spanning multiple dates (like "Monday 10pm to Tuesday 7am") is evaluated on the start date only. The window does not extend across dates.

**Dependency-constrained tasks cannot extend past their deadline.** If a task depends on a parent, its split portions must all finish before the deadline. Without dependencies, split portions can extend past the deadline to avoid permanent partiality.

**Dropped blockers have no explicit warning for the user.** When two DISALLOW blockers conflict at the same time, the second one is silently unscheduled. There is no special "meeting conflict" issue type — it uses the generic "conflicts with occupied time" reason.
