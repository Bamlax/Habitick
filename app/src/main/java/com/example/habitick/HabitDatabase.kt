package com.example.habitick

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class HabitType { Normal, Numeric, Timer, TimePoint }

class Converters {
    @TypeConverter fun fromHabitType(value: HabitType): String = value.name
    @TypeConverter fun toHabitType(value: String): HabitType = HabitType.valueOf(value)
}

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val colorValue: Long,
    val isCompleted: Boolean,
    val type: HabitType,
    val startDate: Long,
    val endDate: Long?,
    val frequency: String,
    val targetValue: String?,
    val sortIndex: Int = 0
) {
    val color: Color get() = Color(colorValue.toULong())
}

@Entity(
    tableName = "habit_records",
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitRecord(
    @PrimaryKey(autoGenerate = true) val recordId: Int = 0,
    val habitId: Int,
    val date: Long,
    val value: String? = null,
    val isCompleted: Boolean = true
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortIndex ASC")
    fun getAllHabits(): Flow<List<Habit>>

    @Query("SELECT MAX(sortIndex) FROM habits")
    suspend fun getMaxSortIndex(): Int?

    // 【新增】实时监听单个习惯的变化 (用于详情页刷新)
    @Query("SELECT * FROM habits WHERE id = :id")
    fun getHabitFlow(id: Int): Flow<Habit>

    @Insert suspend fun insertHabit(habit: Habit)
    @Update suspend fun updateHabits(habits: List<Habit>)
    @Update suspend fun updateHabit(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :habitId")
    suspend fun deleteHabitById(habitId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HabitRecord)

    @Query("DELETE FROM habit_records WHERE habitId = :habitId AND date = :date")
    suspend fun deleteRecord(habitId: Int, date: Long)

    @Query("DELETE FROM habit_records WHERE habitId = :habitId")
    suspend fun deleteAllRecordsForHabit(habitId: Int)

    @Query("UPDATE habit_records SET value = :newValue WHERE habitId = :habitId AND date = :date")
    suspend fun updateRecordValue(habitId: Int, date: Long, newValue: String?)

    @Query("SELECT * FROM habit_records WHERE habitId = :habitId AND date = :date")
    suspend fun getRecordForDate(habitId: Int, date: Long): HabitRecord?

    @Query("""
        SELECT r.date, COUNT(*) as count 
        FROM habit_records r 
        INNER JOIN habits h ON r.habitId = h.id 
        WHERE r.isCompleted = 1
        GROUP BY r.date
    """)
    fun getHeatmapData(): Flow<List<HeatmapEntry>>

    @Query("SELECT * FROM habit_records")
    fun getAllRecords(): Flow<List<HabitRecord>>

    @Query("SELECT habits.* FROM habits INNER JOIN habit_records ON habits.id = habit_records.habitId WHERE habit_records.date = :date AND habit_records.isCompleted = 1")
    fun getCompletedHabitsForDate(date: Long): Flow<List<Habit>>

    @Query("SELECT * FROM habit_records WHERE date = :date")
    fun getRecordsForDateFlow(date: Long): Flow<List<HabitRecord>>

    @Query("SELECT * FROM habit_records WHERE habitId = :habitId ORDER BY date ASC")
    fun getRecordsByHabitId(habitId: Int): Flow<List<HabitRecord>>
}

data class HeatmapEntry(val date: Long, val count: Int)

@Database(entities = [Habit::class, HabitRecord::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile private var Instance: HabitDatabase? = null
        fun getDatabase(context: Context): HabitDatabase {
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, HabitDatabase::class.java, "habit_database")
                    .fallbackToDestructiveMigration()
                    .build().also { Instance = it }
            }
        }
    }
}