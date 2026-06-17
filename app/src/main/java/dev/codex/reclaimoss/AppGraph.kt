package dev.codex.reclaimoss

import android.content.Context
import androidx.room.Room
import dev.codex.reclaimoss.data.calendar.NoOpGoogleCalendarGateway
import dev.codex.reclaimoss.data.local.OpenReclaimDatabase
import dev.codex.reclaimoss.data.repository.PlannerRepository
import dev.codex.reclaimoss.data.repository.PlannerRepositoryImpl
import dev.codex.reclaimoss.domain.scheduling.SchedulerEngine
import dev.codex.reclaimoss.domain.service.PlannerCoordinator
import dev.codex.reclaimoss.notifications.ReminderNotificationScheduler
import dev.codex.reclaimoss.settings.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppGraph(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        OpenReclaimDatabase::class.java,
        "open-reclaim.db",
    ).addMigrations(
        OpenReclaimDatabase.MIGRATION_5_6,
        OpenReclaimDatabase.MIGRATION_6_7,
        OpenReclaimDatabase.MIGRATION_7_8,
        OpenReclaimDatabase.MIGRATION_8_9,
        OpenReclaimDatabase.MIGRATION_9_10,
        OpenReclaimDatabase.MIGRATION_10_11,
        OpenReclaimDatabase.MIGRATION_11_12,
        OpenReclaimDatabase.MIGRATION_12_13,
        OpenReclaimDatabase.MIGRATION_13_14,
        OpenReclaimDatabase.MIGRATION_14_15,
        OpenReclaimDatabase.MIGRATION_15_16,
    ).fallbackToDestructiveMigration().fallbackToDestructiveMigrationOnDowngrade().build()

    private val calendarGateway = NoOpGoogleCalendarGateway()
    private val schedulerEngine = SchedulerEngine()
    private val reminderNotificationScheduler = ReminderNotificationScheduler(context)
    val appSettingsRepository = AppSettingsRepository(context)

    val plannerRepository: PlannerRepository = PlannerRepositoryImpl(
        db = database,
        projectDao = database.projectDao(),
        timeframeDao = database.timeframeDao(),
        taskDao = database.taskDao(),
        scheduleBlockDao = database.scheduleBlockDao(),
        timePeriodDao = database.timePeriodDao(),
        reminderDao = database.reminderDao(),
        schedulingIssueDao = database.schedulingIssueDao(),
    )

    val plannerCoordinator = PlannerCoordinator(
        repository = plannerRepository,
        scheduler = schedulerEngine,
        calendarGateway = calendarGateway,
        getSettings = { appSettingsRepository.current() },
    )

    fun startBackgroundObservers(scope: CoroutineScope) {
        reminderNotificationScheduler.ensureChannel()
        scope.launch {
            plannerCoordinator.snapshot
                .map { it.reminders }
                .distinctUntilChanged()
                .collect { reminders ->
                    reminderNotificationScheduler.sync(reminders)
                }
        }
    }
}
