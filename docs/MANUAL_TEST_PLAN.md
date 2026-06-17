# Manual Test Plan — Flowpath v0.3.2

Areas where automated (CLI) testing can't fully verify behavior. Run these on the emulator.

---

## 1. Database Migration 15→16 (HIGH RISK)

**Why untested**: Migration adds 3 indices. If the SQL fails silently, queries degrade at scale.

1. Install the previous release build (version 15 DB)
2. Create several tasks, timeframes, reminders, and blocks
3. Install this build over it (triggers MIGRATION_15_16)
4. **Verify**: App launches without crash
5. **Verify**: All existing data visible — tasks, blocks, reminders
6. **Verify**: Create a new task; it schedules and appears on timeline

---

## 2. Task Creation → Delete-on-Failure (HIGH RISK)

**Why untested**: Multiple code paths (fast-path, full rebuild, delete). A task can silently disappear.

1. Create a task with **due date = today, 5 minutes from now**, duration = 30 min, allow splitting = OFF
   - **Expected**: Shows unschedulable message, task NOT created
2. Create a task with **due date = today, now**, duration = 30 min  
   - **Expected**: Error message "no valid slot available", task deleted
3. Create a normal task with valid deadline and 60 min duration
   - **Expected**: Task appears on timeline immediately
4. Create a task during sleep hours (e.g., 11pm-12am with sleep at 10pm-6am)
   - **Expected**: Task placed BEFORE 10pm or AFTER 6am, not overlapping sleep

---

## 3. Sleep/Blocker Overlap (HIGH RISK)

**Why untested**: The `placeExactTask` path has independent overlap logic.

1. Create a sleep schedule (e.g., 10pm-6am, all days)
2. Create a blocker (meeting) at 11pm-11:30pm (during sleep)
   - **Expected**: Error — "blocked by another task or calendar event"
3. Create a blocker at 2pm-3pm (outside sleep)
   - **Expected**: Blocker appears at 2pm-3pm, other tasks avoid this slot
4. Create a normal task with fixed start at 11pm (during sleep)
   - **Expected**: Task rescheduled away from sleep

---

## 4. Recurring Tasks (MEDIUM RISK)

**Why untested**: Materialization creates all occurrences upfront; edge cases with counts.

1. Create a DAILY recurring task, end mode = AFTER 3 OCCURRENCES
   - **Expected**: Exactly 3 tasks created on consecutive days
2. Create a WEEKLY recurring task (Mon, Wed, Fri only), end mode = NEVER
   - **Expected**: Tasks for next ~180 days on Mon/Wed/Fri only
3. Edit a recurring task's rule from DAILY to WEEKLY
   - **Expected**: Future occurrences updated, old ones preserved
4. Complete one occurrence of a recurring series
   - **Expected**: Only that occurrence marked done; next occurrence still active
5. Create a MONTHLY task on the 31st
   - **Expected**: Feb occurrence falls on 28th (or 29th in leap year), then Mar 28th

---

## 5. noGap Behavior (MEDIUM RISK)

**Why untested**: Recently implemented; was completely ignored before.

1. Settings → break buffer = 15 min
2. Create task A (parent) at 9am, 60 min
3. Create task B (child) depending on A with `noGap = ON`
   - **Expected**: B starts exactly at 10:00 (A's end), no gap
4. Create task C depending on A with `noGap = OFF`
   - **Expected**: C starts at 10:15 (A's end + 15 min break buffer)

---

## 6. Circular Dependency Guard (MEDIUM RISK)

**Why untested**: The UI should prevent this, but if it reaches the engine, tasks used to be silently dropped.

1. Create task X (parent), then create task Y depending on X
2. Edit task X to depend on task Y
   - **Expected**: UI prevents this with error "A task cannot continue after itself"
3. Delete the parent task of a dependency chain
   - **Expected**: UI shows error "The selected parent task no longer exists"

---

## 7. Light/Dark Mode (MEDIUM RISK)

**Why untested**: Custom theme with many color slots; blocks use surfaceVariant.

1. Settings → Theme → Light
   - **Verify**: Blocks visible against white background (recently darkened)
   - **Verify**: Text readable on blocks
   - **Verify**: Timeframe chips distinguishable
2. Settings → Theme → Dark
   - **Verify**: All elements visible, no black-on-black or white-on-white
3. Switch theme while a task detail sheet is open
   - **Verify**: Sheet updates immediately, no stale colors

---

## 8. Process Death & State Restoration (MEDIUM RISK)

**Why untested**: Android kills background processes; Compose state must survive.

1. Create a task draft (fill in title, duration, due date) but DON'T save
2. Put app in background (Home button)
3. Run: `adb shell am kill dev.codex.reclaimoss`
4. Reopen app from recents
   - **Expected**: Task draft still filled in where possible; app doesn't crash
5. Open a task detail sheet, rotate device
   - **Expected**: Sheet stays open, correct task shown, no crash

---

## 9. Empty State → First Launch (LOW RISK)

**Why untested**: The seed data was recently implemented.

1. Clear app data: `adb shell pm clear dev.codex.reclaimoss`
2. Launch app
   - **Expected**: "My Tasks" default project visible
   - **Expected**: "No tasks scheduled" message on Planner
   - **Expected**: "No reminders yet" on Reminders tab
3. Create first task
   - **Expected**: App doesn't crash; task appears
4. Check Settings → onboarding was NOT auto-completed (no sleep yet)
   - **Expected**: Sleep setup banner shown

---

## 10. Time Period Actions (LOW RISK)

**Why untested**: Recently fixed — was calling destructive `clearLegacyDailyFlowData`.

1. Settings → Time Periods → Add "Morning" (9am-12pm, Productive)
2. Add "Afternoon" (1pm-5pm, Productive)
3. Delete "Morning"
   - **Expected**: Afternoon remains; no crash; tasks not corrupted
4. Check that existing tasks still display correctly

---

## 11. Overlap Policy Dropdown (LOW RISK)

**Why untested**: INHERIT was recently removed.

1. Create a task → expand "Other Rules" section
   - **Verify**: Overlap dropdown shows only "Allow" and "Disallow" (no "Inherit")
   - **Verify**: Default is "Disallow"
2. Create task with overlap = Allow at same time as another task (with Allow)
   - **Expected**: Both tasks placed at overlapping times
3. Create task with overlap = Disallow at same time as existing task
   - **Expected**: New task placed AFTER existing task ends

---

## Quick Sanity Smoke Test

Run through this in 2 minutes before every release:

1. ✓ App launches without crash
2. ✓ Create a task — appears on timeline
3. ✓ Complete a task — marked done, timeline updates
4. ✓ Create a blocker — reserves time slot
5. ✓ Switch between Tasks / Planner / Settings tabs
6. ✓ Toggle light/dark mode — nothing broken
7. ✓ Rotate device — layout adapts, no crash
8. ✓ Kill and reopen — state restored
