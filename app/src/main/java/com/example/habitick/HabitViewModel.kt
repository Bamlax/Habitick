package com.example.habitick

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class HabitUiModel(
    val habit: Habit,
    val todayNote: String?,
    val currentStreak: Int
)

class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = HabitDatabase.getDatabase(application).habitDao()

    // 1. 基础数据流
    val habits: StateFlow<List<Habit>> = dao.getAllHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allRecordsFlow = dao.getAllRecords()

    // 【核心修复】补全排序状态
    private val _isSorting = MutableStateFlow(false)
    val isSorting = _isSorting.asStateFlow()

    // 【核心修复】补全排序列表
    val sortingList = mutableStateListOf<HabitUiModel>()

    // 2. 主页 UI 状态流
    val homeUiState: StateFlow<List<HabitUiModel>> = combine(habits, allRecordsFlow) { habitList, records ->
        val today = getTodayZero()
        habitList.map { habit ->
            val habitRecords = records.filter { it.habitId == habit.id }
            val todayRecord = habitRecords.find { it.date == today }
            val streak = calculateStreakForHabit(habitRecords, today)

            HabitUiModel(
                habit = habit,
                todayNote = todayRecord?.value,
                currentStreak = streak
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- 排序相关操作 (补全缺失的方法) ---

    fun toggleSortMode() {
        _isSorting.value = !_isSorting.value
    }

    fun startSorting() {
        sortingList.clear()
        sortingList.addAll(homeUiState.value)
        _isSorting.value = true
    }

    fun onSortSwap(from: Int, to: Int) {
        sortingList.apply { add(to, removeAt(from)) }
    }

    fun confirmSort() {
        viewModelScope.launch {
            val updates = sortingList.mapIndexed { index, model ->
                model.habit.copy(sortIndex = index)
            }
            dao.updateHabits(updates)
            _isSorting.value = false
        }
    }

    fun cancelSort() {
        _isSorting.value = false
    }

    // 【核心修复】补全 saveSortOrder (MainScreen/TodayScreen 可能会调用)
    fun saveSortOrder(newHabits: List<Habit>) {
        viewModelScope.launch {
            val updates = newHabits.mapIndexed { index, habit ->
                habit.copy(sortIndex = index)
            }
            dao.updateHabits(updates)
        }
    }

    // --- 其他数据流 ---

    val heatmapData: StateFlow<Map<Long, Int>> = dao.getHeatmapData()
        .map { list -> list.associate { it.date to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _selectedDate = MutableStateFlow(getTodayZero())
    val selectedDate = _selectedDate.asStateFlow()

    private val _currentViewingMonth = MutableStateFlow(getStartOfCurrentMonth())
    val currentViewingMonth = _currentViewingMonth.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDateHabits: StateFlow<List<Habit>> = _selectedDate
        .flatMapLatest { date -> dao.getCompletedHabitsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedDateRecords: StateFlow<List<HabitRecord>> = _selectedDate
        .flatMapLatest { date -> dao.getRecordsForDateFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- 核心操作 ---

    fun getHabitFlow(habitId: Int): Flow<Habit> = dao.getHabitFlow(habitId)

    fun updateHabitDetails(habit: Habit) {
        viewModelScope.launch { dao.updateHabit(habit) }
    }

    fun updateRecord(habit: Habit, date: Long, isCompleted: Boolean?, note: String?) {
        viewModelScope.launch {
            val isToday = (date == getTodayZero())
            val oldRecord = dao.getRecordForDate(habit.id, date)

            val newCompleted = isCompleted ?: oldRecord?.isCompleted ?: false
            val newNote = if (note !== null) note else oldRecord?.value

            if (isToday && isCompleted != null) {
                dao.updateHabit(habit.copy(isCompleted = newCompleted))
            }

            val shouldExist = newCompleted || !newNote.isNullOrBlank()

            if (shouldExist) {
                dao.insertRecord(HabitRecord(
                    habitId = habit.id,
                    date = date,
                    value = newNote,
                    isCompleted = newCompleted
                ))
            } else {
                dao.deleteRecord(habit.id, date)
            }
        }
    }

    fun toggleHabit(habit: Habit, value: String? = null, dateOverride: Long? = null) {
        viewModelScope.launch {
            val targetDate = dateOverride ?: getTodayZero()
            val oldRecord = dao.getRecordForDate(habit.id, targetDate)

            val newCompleted: Boolean
            val newNote: String?

            if (value != null) {
                newCompleted = oldRecord?.isCompleted ?: false
                newNote = value
            } else {
                newCompleted = !(oldRecord?.isCompleted ?: false)
                newNote = oldRecord?.value
            }

            updateRecord(habit, targetDate, isCompleted = newCompleted, note = newNote)
        }
    }

    fun getHabitRecords(habitId: Int): StateFlow<List<HabitRecord>> {
        return dao.getRecordsByHabitId(habitId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun selectDate(date: Long) { _selectedDate.value = date }

    fun changeMonth(amount: Int) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = _currentViewingMonth.value
        calendar.add(Calendar.MONTH, amount)
        _currentViewingMonth.value = calendar.timeInMillis
    }

    fun addHabit(name: String, color: Color, type: HabitType, startDate: Long, endDate: Long?, frequency: String, targetValue: String?) {
        viewModelScope.launch {
            val maxIndex = dao.getMaxSortIndex() ?: 0
            dao.insertHabit(Habit(
                name = name,
                colorValue = color.value.toLong(),
                isCompleted = false,
                type = type,
                startDate = startDate,
                endDate = endDate,
                frequency = frequency,
                targetValue = targetValue,
                sortIndex = maxIndex + 1
            ))
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            dao.deleteAllRecordsForHabit(habit.id)
            dao.deleteHabitById(habit.id)
        }
    }

    private fun calculateStreakForHabit(records: List<HabitRecord>, today: Long): Int {
        val validRecords = records.filter { it.isCompleted }
        if (validRecords.isEmpty()) return 0
        val dates = validRecords.map { it.date }.toSet()
        val yesterday = today - 24 * 3600 * 1000

        if (!dates.contains(today) && !dates.contains(yesterday)) return 0

        var streak = 0
        var checkDate = if (dates.contains(today)) today else yesterday
        while (dates.contains(checkDate)) {
            streak++
            checkDate -= 24 * 3600 * 1000
        }
        return streak
    }

    private fun getTodayZero(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun getStartOfCurrentMonth(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}