package com.example.habitick

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class HabitUiModel(
    val habit: Habit,
    val todayNote: String?,
    val currentStreak: Int,
    val todayTags: String?,
    val availableTags: List<Tag> // UI模型携带该习惯的可用标签
)

class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = HabitDatabase.getDatabase(application).habitDao()
    private val context = application.applicationContext

    // 1. 基础数据流
    val habits: StateFlow<List<Habit>> = dao.getAllHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allRecordsFlow = dao.getAllRecords()

    // 标签流
    private val allTagsFlow: Flow<List<Tag>> = dao.getAllTags()

    // 按 habitId 分组的标签 Map
    val tagsMap: StateFlow<Map<Int, List<Tag>>> = allTagsFlow
        .map { tags -> tags.groupBy { it.habitId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _isSorting = MutableStateFlow(false)
    val isSorting = _isSorting.asStateFlow()
    val sortingList = mutableStateListOf<HabitUiModel>()

    // 合并数据生成 UI Model
    val homeUiState: StateFlow<List<HabitUiModel>> = combine(habits, allRecordsFlow, tagsMap) { habitList, records, tagsMap ->
        val today = getTodayZero()
        habitList.map { habit ->
            val habitRecords = records.filter { it.habitId == habit.id }
            val todayRecord = habitRecords.find { it.date == today }
            val streak = calculateStreakForHabit(habitRecords, today)

            HabitUiModel(
                habit = habit,
                todayNote = todayRecord?.value,
                currentStreak = streak,
                todayTags = todayRecord?.tags,
                availableTags = tagsMap[habit.id] ?: emptyList()
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // --- 标签操作 ---

    fun addTag(habitId: Int, tagName: String) {
        viewModelScope.launch { dao.insertTag(Tag(name = tagName, habitId = habitId)) }
    }

    fun deleteTag(habitId: Int, tagName: String) {
        viewModelScope.launch { dao.deleteTag(name = tagName, habitId = habitId) }
    }

    // --- 记录操作 ---

    fun updateRecord(habit: Habit, date: Long, isCompleted: Boolean?, note: String?, tags: String? = null) {
        viewModelScope.launch {
            val isToday = (date == getTodayZero())
            val oldRecord = dao.getRecordForDate(habit.id, date)

            val newCompleted = isCompleted ?: oldRecord?.isCompleted ?: false
            val newNote = if (note !== null) note else oldRecord?.value
            val newTags = if (tags !== null) tags else oldRecord?.tags ?: ""

            if (isToday && isCompleted != null) {
                dao.updateHabit(habit.copy(isCompleted = newCompleted))
            }

            val shouldExist = newCompleted || !newNote.isNullOrBlank() || newTags.isNotEmpty()

            if (shouldExist) {
                dao.insertRecord(HabitRecord(
                    habitId = habit.id,
                    date = date,
                    value = newNote,
                    isCompleted = newCompleted,
                    tags = newTags
                ))
            } else {
                dao.deleteRecord(habit.id, date)
            }
        }
    }

    fun toggleHabit(habit: Habit, value: String? = null, tags: String? = null, dateOverride: Long? = null) {
        viewModelScope.launch {
            val targetDate = dateOverride ?: getTodayZero()
            val oldRecord = dao.getRecordForDate(habit.id, targetDate)

            val newCompleted: Boolean
            val newNote: String?
            val newTags: String?

            if (value != null || tags != null) {
                newCompleted = oldRecord?.isCompleted ?: false
                newNote = value ?: oldRecord?.value
                newTags = tags ?: oldRecord?.tags
            } else {
                newCompleted = !(oldRecord?.isCompleted ?: false)
                newNote = oldRecord?.value
                newTags = oldRecord?.tags
            }

            updateRecord(habit, targetDate, isCompleted = newCompleted, note = newNote, tags = newTags)
        }
    }

    // --- 导入导出 ---

    fun exportDataToCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allHabits = dao.getAllHabitsSync()
                val allRecords = dao.getAllRecordsSync()
                val allTags = dao.getAllTagsSync().groupBy { it.habitId }

                val sb = StringBuilder()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

                allHabits.forEach { habit ->
                    val habitRecords = allRecords.filter { it.habitId == habit.id }
                    val habitTags = allTags[habit.id]?.joinToString("|") { it.name } ?: ""

                    sb.append("[HABIT],${escapeCsv(habit.name)},${dateFormat.format(Date(habit.startDate))},${if (habit.endDate != null) dateFormat.format(Date(habit.endDate)) else ""},${habit.type.name},${escapeCsv(habit.targetValue ?: "")},${escapeCsv(habit.frequency)},${escapeCsv(habitTags)}\n")
                    sb.append("Date,IsCompleted,Note,CurrentTags\n")

                    habitRecords.sortedBy { it.date }.forEach { record ->
                        val row = listOf(
                            dateFormat.format(Date(record.date)),
                            record.isCompleted.toString(),
                            escapeCsv(record.value ?: ""),
                            escapeCsv(record.tags)
                        ).joinToString(",")
                        sb.append(row).append("\n")
                    }
                    sb.append("\n")
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(sb.toString().toByteArray())
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { Toast.makeText(context, "导出失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    fun importDataFromCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()
                if (lines.isEmpty()) throw Exception("文件为空")

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                var currentHabitId = -1
                var successCount = 0

                var i = 0
                while (i < lines.size) {
                    val line = lines[i].trim()
                    if (line.isBlank()) { i++; continue }

                    if (line.startsWith("[HABIT]")) {
                        val parts = parseCsvLine(line.substringAfter("[HABIT],"))
                        if (parts.size >= 7) {
                            val name = parts[0]
                            val startDate = dateFormat.parse(parts[1])?.time ?: System.currentTimeMillis()
                            val endDate = if (parts[2].isNotBlank()) dateFormat.parse(parts[2])?.time else null
                            val type = try { HabitType.valueOf(parts[3]) } catch (e:Exception) { HabitType.Normal }
                            val targetValue = parts[4]
                            val frequency = parts[5]
                            val habitTagsStr = if (parts.size > 6) parts[6] else ""

                            val existingHabit = dao.getHabitByName(name)
                            if (existingHabit != null) {
                                currentHabitId = existingHabit.id
                            } else {
                                val newHabit = Habit(name = name, colorValue = Color(0xFF9C27B0).value.toLong(), isCompleted = false, type = type, startDate = startDate, endDate = endDate, frequency = frequency, targetValue = if (targetValue.isBlank()) null else targetValue)
                                currentHabitId = dao.insertHabit(newHabit).toInt()
                            }

                            if (habitTagsStr.isNotBlank()) {
                                habitTagsStr.split("|").forEach { tagName ->
                                    if (tagName.isNotBlank()) dao.insertTag(Tag(name = tagName, habitId = currentHabitId))
                                }
                            }
                            i++
                        }
                    } else if (currentHabitId != -1 && !line.startsWith("Date,IsCompleted")) {
                        val tokens = parseCsvLine(line)
                        if (tokens.size >= 4) {
                            val dateStr = tokens[0]
                            val isCompleted = tokens[1].toBoolean()
                            val note = tokens[2]
                            val recordTags = tokens[3]
                            val date = dateFormat.parse(dateStr)?.time
                            if (date != null) {
                                dao.insertRecord(HabitRecord(habitId = currentHabitId, date = date, isCompleted = isCompleted, value = if (note.isBlank()) null else note, tags = recordTags))
                                successCount++
                            }
                        }
                    }
                    i++
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, "成功导入 $successCount 条记录", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { Toast.makeText(context, "导入失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun escapeCsv(value: String): String {
        var res = value.replace("\"", "\"\"")
        if (res.contains(",") || res.contains("\n")) res = "\"$res\""
        return res
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') { current.append('"'); i++ } else { inQuotes = false }
                } else current.append(c)
            } else {
                if (c == '"') inQuotes = true
                else if (c == ',') { result.add(current.toString()); current = StringBuilder() }
                else current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    // --- 排序 ---
    fun toggleSortMode() { _isSorting.value = !_isSorting.value }
    fun startSorting() { sortingList.clear(); sortingList.addAll(homeUiState.value); _isSorting.value = true }
    fun onSortSwap(from: Int, to: Int) { sortingList.apply { add(to, removeAt(from)) } }
    fun confirmSort() { viewModelScope.launch { val updates = sortingList.mapIndexed { index, model -> model.habit.copy(sortIndex = index) }; dao.updateHabits(updates); _isSorting.value = false } }
    fun cancelSort() { _isSorting.value = false }
    fun saveSortOrder(newHabits: List<Habit>) { viewModelScope.launch { val updates = newHabits.mapIndexed { index, habit -> habit.copy(sortIndex = index) }; dao.updateHabits(updates) } }

    // --- 【修复】补全缺失的 CRUD 方法 ---
    fun getHabitFlow(habitId: Int): Flow<Habit> = dao.getHabitFlow(habitId)
    fun getHabitRecords(habitId: Int): StateFlow<List<HabitRecord>> = dao.getRecordsByHabitId(habitId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun updateHabitDetails(habit: Habit) { viewModelScope.launch { dao.updateHabit(habit) } }

    // 修复：HistoryScreen 需要这些方法
    fun selectDate(date: Long) { _selectedDate.value = date }
    fun changeMonth(amount: Int) { val c = Calendar.getInstance(); c.timeInMillis = _currentViewingMonth.value; c.add(Calendar.MONTH, amount); _currentViewingMonth.value = c.timeInMillis }

    // 修复：AddHabitScreen 需要
    fun addHabit(name: String, color: Color, type: HabitType, startDate: Long, endDate: Long?, frequency: String, targetValue: String?) { viewModelScope.launch { val maxIndex = dao.getMaxSortIndex() ?: 0; dao.insertHabit(Habit(name = name, colorValue = color.value.toLong(), isCompleted = false, type = type, startDate = startDate, endDate = endDate, frequency = frequency, targetValue = targetValue, sortIndex = maxIndex + 1)) } }

    // 修复：HabitDetailScreen 和 MainScreen 需要
    fun deleteHabit(habit: Habit) { viewModelScope.launch { dao.deleteAllRecordsForHabit(habit.id); dao.deleteHabitById(habit.id) } }

    private fun calculateStreakForHabit(records: List<HabitRecord>, today: Long): Int {
        val validRecords = records.filter { it.isCompleted }
        if (validRecords.isEmpty()) return 0
        val dates = validRecords.map { it.date }.toSet()
        val yesterday = today - 24 * 3600 * 1000
        if (!dates.contains(today) && !dates.contains(yesterday)) return 0
        var streak = 0; var checkDate = if (dates.contains(today)) today else yesterday
        while (dates.contains(checkDate)) { streak++; checkDate -= 24 * 3600 * 1000 }
        return streak
    }
    private fun getTodayZero(): Long { val c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c.timeInMillis }
    private fun getStartOfCurrentMonth(): Long { val c = Calendar.getInstance(); c.set(Calendar.DAY_OF_MONTH, 1); c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); return c.timeInMillis }
}