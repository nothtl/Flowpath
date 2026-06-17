package dev.codex.reclaimoss.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.codex.reclaimoss.domain.model.BlockCompletionState
import dev.codex.reclaimoss.domain.model.BlockLockState
import dev.codex.reclaimoss.domain.model.BlockSource
import dev.codex.reclaimoss.domain.model.PreferredTimeOfDay
import dev.codex.reclaimoss.domain.model.RecurrenceEndMode
import dev.codex.reclaimoss.domain.model.RecurrenceType
import dev.codex.reclaimoss.domain.model.ReminderStatus
import dev.codex.reclaimoss.domain.model.SchedulingIssueType
import dev.codex.reclaimoss.domain.model.TaskContinuationMode
import dev.codex.reclaimoss.domain.model.TaskKind
import dev.codex.reclaimoss.domain.model.TaskOverlapPolicy
import dev.codex.reclaimoss.domain.model.TaskSchedulingMode
import dev.codex.reclaimoss.domain.model.TaskPriority
import dev.codex.reclaimoss.domain.model.TaskStatus
import dev.codex.reclaimoss.domain.model.TimePeriodType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val defaultPriority: TaskPriority,
    val archived: Boolean,
)

@Entity(tableName = "tasks", indices = [Index("dueAtEpochMillis")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val recurrenceSeriesId: String?,
    val projectId: String?,
    val timeframeId: String?,
    val title: String,
    val description: String,
    val taskKind: TaskKind,
    val priority: TaskPriority,
    val preferredTimeOfDay: PreferredTimeOfDay,
    val preferredTimePeriodId: String?,
    val hasDeadline: Boolean,
    val continuationParentTaskId: String?,
    val continuationMode: TaskContinuationMode?,
    val overlapPolicy: TaskOverlapPolicy,
    val schedulingMode: TaskSchedulingMode,
    val notBeforeAtEpochMillis: Long?,
    val fixedStartAtEpochMillis: Long?,
    val fixedEndAtEpochMillis: Long?,
    val dueAtEpochMillis: Long,
    val estimatedMinutes: Int,
    val remainingMinutes: Int,
    val recurrenceType: RecurrenceType,
    val recurrenceInterval: Int,
    val recurrenceDaysCsv: String,
    val recurrenceUntilEpochMillis: Long?,
    val recurrenceEndMode: RecurrenceEndMode,
    val recurrenceOccurrenceLimit: Int?,
    val status: TaskStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "timeframes")
data class TimeframeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val colorHex: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "time_periods")
data class TimePeriodEntity(
    @PrimaryKey val id: String,
    val label: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val type: TimePeriodType,
    val sortOrder: Int,
)

@Entity(tableName = "reminders", indices = [Index("dueAtEpochMillis")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val dueAtEpochMillis: Long,
    val isAllDay: Boolean,
    val recurrenceType: RecurrenceType,
    val recurrenceInterval: Int,
    val recurrenceDaysCsv: String,
    val recurrenceUntilEpochMillis: Long?,
    val recurrenceEndMode: RecurrenceEndMode,
    val recurrenceOccurrenceLimit: Int?,
    val linkedTaskId: String?,
    val status: ReminderStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "scheduling_issues")
data class SchedulingIssueEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val type: SchedulingIssueType,
    val unscheduledMinutes: Int,
    val reason: String,
)

@Entity(tableName = "schedule_blocks", indices = [Index("taskId")])
data class ScheduleBlockEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val startAtEpochMillis: Long,
    val endAtEpochMillis: Long,
    val source: BlockSource,
    val lockState: BlockLockState,
    val completionState: BlockCompletionState,
    val externalCalendarEventId: String?,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY archived, name")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun getAll(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: ProjectEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueAtEpochMillis ASC")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("UPDATE tasks SET remainingMinutes = :remainingMinutes, updatedAtEpochMillis = :updatedAt WHERE id = :taskId")
    suspend fun updateRemainingMinutes(taskId: String, remainingMinutes: Int, updatedAt: Long)

    @Query("UPDATE tasks SET preferredTimePeriodId = NULL WHERE preferredTimePeriodId = :periodId")
    suspend fun clearPreferredTimePeriod(periodId: String)

    @Query("UPDATE tasks SET preferredTimePeriodId = NULL")
    suspend fun clearAllPreferredTimePeriods()

    @Query("UPDATE tasks SET timeframeId = NULL WHERE timeframeId = :timeframeId")
    suspend fun clearTimeframe(timeframeId: String)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)
}

@Dao
interface TimeframeDao {
    @Query("SELECT * FROM timeframes ORDER BY startDate ASC, endDate ASC, createdAtEpochMillis ASC")
    fun observeTimeframes(): Flow<List<TimeframeEntity>>

    @Query("SELECT * FROM timeframes ORDER BY startDate ASC, endDate ASC, createdAtEpochMillis ASC")
    suspend fun getAll(): List<TimeframeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(timeframe: TimeframeEntity)

    @Query("DELETE FROM timeframes WHERE id = :timeframeId")
    suspend fun delete(timeframeId: String)
}

@Dao
interface ScheduleBlockDao {
    @Query("SELECT * FROM schedule_blocks ORDER BY startAtEpochMillis ASC")
    fun observeBlocks(): Flow<List<ScheduleBlockEntity>>

    @Query("SELECT * FROM schedule_blocks")
    suspend fun getAll(): List<ScheduleBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(blocks: List<ScheduleBlockEntity>)

    @androidx.room.Transaction
    suspend fun replaceFlexibleBlocksForTask(taskId: String, blocks: List<ScheduleBlockEntity>) {
        deleteFlexiblePendingBlocksForTask(taskId)
        upsertAll(blocks)
    }

    @Query("DELETE FROM schedule_blocks WHERE taskId = :taskId AND completionState != 'COMPLETED' AND lockState != 'LOCKED'")
    suspend fun deleteFlexiblePendingBlocksForTask(taskId: String)

    @Query("UPDATE schedule_blocks SET lockState = :lockState WHERE id = :blockId")
    suspend fun updateLockState(blockId: String, lockState: BlockLockState)

    @Query("UPDATE schedule_blocks SET completionState = :completionState WHERE id = :blockId")
    suspend fun updateCompletionState(blockId: String, completionState: BlockCompletionState)

    @Query("DELETE FROM schedule_blocks WHERE taskId = :taskId")
    suspend fun deleteAllForTask(taskId: String)

    @Query("DELETE FROM schedule_blocks WHERE taskId = :taskId AND completionState != 'COMPLETED'")
    suspend fun deletePendingForTask(taskId: String)
}

@Dao
interface TimePeriodDao {
    @Query("SELECT * FROM time_periods ORDER BY sortOrder ASC, startTime ASC")
    fun observeTimePeriods(): Flow<List<TimePeriodEntity>>

    @Query("SELECT * FROM time_periods ORDER BY sortOrder ASC, startTime ASC")
    suspend fun getAll(): List<TimePeriodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(period: TimePeriodEntity)

    @Query("DELETE FROM time_periods WHERE id = :periodId")
    suspend fun delete(periodId: String)

    @Query("DELETE FROM time_periods")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dueAtEpochMillis ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY dueAtEpochMillis ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun delete(reminderId: String)
}

@Dao
interface SchedulingIssueDao {
    @Query("SELECT * FROM scheduling_issues ORDER BY taskId ASC")
    fun observeIssues(): Flow<List<SchedulingIssueEntity>>

    @Query("SELECT * FROM scheduling_issues")
    suspend fun getAll(): List<SchedulingIssueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(issues: List<SchedulingIssueEntity>)

    @Query("DELETE FROM scheduling_issues WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)
}

class RoomConverters {
    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String = value.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority = TaskPriority.valueOf(value)

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromTaskSchedulingMode(value: TaskSchedulingMode): String = value.name

    @TypeConverter
    fun toTaskSchedulingMode(value: String): TaskSchedulingMode = TaskSchedulingMode.valueOf(value)

    @TypeConverter
    fun fromTaskKind(value: TaskKind): String = value.name

    @TypeConverter
    fun toTaskKind(value: String): TaskKind = TaskKind.valueOf(value)

    @TypeConverter
    fun fromTaskOverlapPolicy(value: TaskOverlapPolicy): String = value.name

    @TypeConverter
    fun toTaskOverlapPolicy(value: String): TaskOverlapPolicy = TaskOverlapPolicy.valueOf(value)

    @TypeConverter
    fun fromReminderStatus(value: ReminderStatus): String = value.name

    @TypeConverter
    fun toReminderStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)

    @TypeConverter
    fun fromPreferredTimeOfDay(value: PreferredTimeOfDay): String = value.name

    @TypeConverter
    fun toPreferredTimeOfDay(value: String): PreferredTimeOfDay = PreferredTimeOfDay.valueOf(value)

    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType): String = value.name

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType = RecurrenceType.valueOf(value)

    @TypeConverter
    fun fromRecurrenceEndMode(value: RecurrenceEndMode): String = value.name

    @TypeConverter
    fun toRecurrenceEndMode(value: String): RecurrenceEndMode = RecurrenceEndMode.valueOf(value)

    @TypeConverter
    fun fromTimePeriodType(value: TimePeriodType): String = value.name

    @TypeConverter
    fun toTimePeriodType(value: String): TimePeriodType = TimePeriodType.valueOf(value)

    @TypeConverter
    fun fromSchedulingIssueType(value: SchedulingIssueType): String = value.name

    @TypeConverter
    fun toSchedulingIssueType(value: String): SchedulingIssueType = SchedulingIssueType.valueOf(value)

    @TypeConverter
    fun fromLocalTime(value: LocalTime): String = value.toString()

    @TypeConverter
    fun toLocalTime(value: String): LocalTime = LocalTime.parse(value)

    @TypeConverter
    fun fromLocalDate(value: LocalDate): String = value.toString()

    @TypeConverter
    fun toLocalDate(value: String): LocalDate = LocalDate.parse(value)

    @TypeConverter
    fun fromDayOfWeekSet(value: Set<DayOfWeek>): String =
        value.joinToString(",") { it.name }

    @TypeConverter
    fun toDayOfWeekSet(value: String): Set<DayOfWeek> =
        value.split(',').filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSet()

    @TypeConverter
    fun fromBlockSource(value: BlockSource): String = value.name

    @TypeConverter
    fun toBlockSource(value: String): BlockSource = BlockSource.valueOf(value)

    @TypeConverter
    fun fromBlockLockState(value: BlockLockState): String = value.name

    @TypeConverter
    fun toBlockLockState(value: String): BlockLockState = BlockLockState.valueOf(value)

    @TypeConverter
    fun fromBlockCompletionState(value: BlockCompletionState): String = value.name

    @TypeConverter
    fun toBlockCompletionState(value: String): BlockCompletionState = BlockCompletionState.valueOf(value)
}

@Database(
    entities = [
        ProjectEntity::class,
        TimeframeEntity::class,
        TaskEntity::class,
        ScheduleBlockEntity::class,
        TimePeriodEntity::class,
        ReminderEntity::class,
        SchedulingIssueEntity::class,
    ],
    version = 16,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class OpenReclaimDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun timeframeDao(): TimeframeDao
    abstract fun taskDao(): TaskDao
    abstract fun scheduleBlockDao(): ScheduleBlockDao
    abstract fun timePeriodDao(): TimePeriodDao
    abstract fun reminderDao(): ReminderDao
    abstract fun schedulingIssueDao(): SchedulingIssueDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) = Unit
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceUntilEpochMillis INTEGER")
                database.execSQL("ALTER TABLE reminders ADD COLUMN recurrenceUntilEpochMillis INTEGER")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN schedulingMode TEXT NOT NULL DEFAULT 'FLEXIBLE'")
                database.execSQL("ALTER TABLE tasks ADD COLUMN fixedStartAtEpochMillis INTEGER")
                database.execSQL("ALTER TABLE tasks ADD COLUMN fixedEndAtEpochMillis INTEGER")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceEndMode TEXT NOT NULL DEFAULT 'NEVER'")
                database.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceOccurrenceLimit INTEGER")
                database.execSQL("ALTER TABLE reminders ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE reminders ADD COLUMN recurrenceEndMode TEXT NOT NULL DEFAULT 'NEVER'")
                database.execSQL("ALTER TABLE reminders ADD COLUMN recurrenceOccurrenceLimit INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN hasDeadline INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN continuationParentTaskId TEXT")
                database.execSQL("ALTER TABLE tasks ADD COLUMN continuationMode TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN overlapPolicy TEXT NOT NULL DEFAULT 'DISALLOW'")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN timeframeId TEXT")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS timeframes (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL,
                        colorHex TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN isAllDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN taskKind TEXT NOT NULL DEFAULT 'NORMAL'")
                database.execSQL("ALTER TABLE tasks ADD COLUMN notBeforeAtEpochMillis INTEGER")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_dueAt ON tasks(dueAtEpochMillis)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_reminders_dueAt ON reminders(dueAtEpochMillis)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_blocks_taskId ON schedule_blocks(taskId)")
            }
        }
    }
}
