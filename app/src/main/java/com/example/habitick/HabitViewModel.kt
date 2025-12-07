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
    val todayTags: String?
)

class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = HabitDatabase.getDatabase(application).habitDao()
    private val context = application.applicationContext

    // 1. 基础数据流
    val habits: StateFlow<List<Habit>> = dao.getAllHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allRecordsFlow = dao.getAllRecords()

    val allTags: StateFlow<List<Tag>> = dao.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSorting = MutableStateFlow(false)
    val isSorting = _isSorting.asStateFlow()
    val sortingList = mutableStateListOf<HabitUiModel>()

    val homeUiState: StateFlow<List<HabitUiModel>> = combine(habits, allRecordsFlow) { habitList, records ->
        val today = getTodayZero()
        habitList.map { habit ->
            val habitRecords = records.filter { it.habitId == habit.id }
            val todayRecord = habitRecords.find { it.date == today }
            val streak = calculateStreakForHabit(habitRecords, today)

            HabitUiModel(
                habit = habit,
                todayNote = todayRecord?.value,
                currentStreak = streak,
                todayTags = todayRecord?.tags
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

    // --- 导入导出功能 ---

    fun exportDataToCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allHabits = dao.getAllHabitsSync()
                val allRecords = dao.getAllRecordsSync()

                // 拼接 CSV 内容
                // Header: 习惯名称,日期,开始日期,结束日期,类型,目标,重复策略,是否完成,备注,标签
                val sb = StringBuilder()
                sb.append("习惯名称,日期,开始日期,结束日期,类型,目标,重复策略,是否完成,备注,标签\n")

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

                // 遍历所有记录导出
                // 注意：这里只导出了“有记录”的数据。
                // 如果需要导出所有习惯（即使没有记录），逻辑会更复杂，这里优先满足“数据备份”需求，即导出所有打卡历史。
                allRecords.forEach { record ->
                    val habit = allHabits.find { it.id == record.habitId }
                    if (habit != null) {
                        val row = listOf(
                            escapeCsv(habit.name),
                            dateFormat.format(Date(record.date)),
                            dateFormat.format(Date(habit.startDate)),
                            if (habit.endDate != null) dateFormat.format(Date(habit.endDate)) else "",
                            habit.type.name,
                            escapeCsv(habit.targetValue ?: ""),
                            escapeCsv(habit.frequency),
                            record.isCompleted.toString(),
                            escapeCsv(record.value ?: ""),
                            escapeCsv(record.tags)
                        ).joinToString(",")
                        sb.append(row).append("\n")
                    }
                }

                // 写入文件
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(sb.toString().toByteArray())
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导出失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun importDataFromCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val lines = reader.readLines()

                // 检查 Header
                if (lines.isEmpty()) throw Exception("文件为空")
                val header = lines[0]
                if (!header.contains("习惯名称") || !header.contains("日期") || !header.contains("类型")) {
                    throw Exception("CSV 格式不符合规范，请检查表头")
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                var successCount = 0

                // 从第二行开始解析
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    if (line.isBlank()) continue

                    // 简单的 CSV 解析 (处理引号)
                    val tokens = parseCsvLine(line)
                    if (tokens.size < 10) continue // 数据残缺

                    // 解析字段
                    val name = tokens[0]
                    val dateStr = tokens[1]
                    val startDateStr = tokens[2]
                    val endDateStr = tokens[3]
                    val typeStr = tokens[4]
                    val targetValue = tokens[5]
                    val frequency = tokens[6]
                    val isCompleted = tokens[7].toBoolean()
                    val note = tokens[8]
                    val tags = tokens[9]

                    val date = dateFormat.parse(dateStr)?.time ?: continue
                    val startDate = dateFormat.parse(startDateStr)?.time ?: System.currentTimeMillis()
                    val endDate = if (endDateStr.isNotBlank()) dateFormat.parse(endDateStr)?.time else null
                    val type = try { HabitType.valueOf(typeStr) } catch (e:Exception) { HabitType.Normal }

                    // 1. 查找或创建 Habit
                    var habitId: Int
                    val existingHabit = dao.getHabitByName(name)
                    if (existingHabit != null) {
                        habitId = existingHabit.id
                    } else {
                        // 创建新习惯
                        val newHabit = Habit(
                            name = name,
                            colorValue = Color(0xFF9C27B0).value.toLong(), // 默认紫色
                            isCompleted = false,
                            type = type,
                            startDate = startDate,
                            endDate = endDate,
                            frequency = frequency,
                            targetValue = if (targetValue.isBlank()) null else targetValue
                        )
                        habitId = dao.insertHabit(newHabit).toInt()
                    }

                    // 2. 插入 Record
                    val record = HabitRecord(
                        habitId = habitId,
                        date = date,
                        isCompleted = isCompleted,
                        value = if (note.isBlank()) null else note,
                        tags = tags
                    )
                    dao.insertRecord(record)

                    // 3. 恢复 Tags 表
                    if (tags.isNotBlank()) {
                        tags.split(",").forEach { tagName ->
                            if (tagName.isNotBlank()) {
                                dao.insertTag(Tag(tagName.trim()))
                            }
                        }
                    }
                    successCount++
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "成功导入 $successCount 条记录", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "导入失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun escapeCsv(value: String): String {
        var res = value.replace("\"", "\"\"") // 双引号转义
        if (res.contains(",") || res.contains("\n")) {
            res = "\"$res\"" // 有逗号或换行则加引号
        }
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
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(c)
                }
            } else {
                if (c == '"') {
                    inQuotes = true
                } else if (c == ',') {
                    result.add(current.toString())
                    current = StringBuilder()
                } else {
                    current.append(c)
                }
            }
            i++
        }
        result.add(current.toString())
        return result
    }


    // --- 原有逻辑 ---

    fun addTag(tagName: String) {
        viewModelScope.launch { dao.insertTag(Tag(tagName)) }
    }

    fun deleteTag(tagName: String) {
        viewModelScope.launch { dao.deleteTag(tagName) }
    }

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

    fun toggleSortMode() { _isSorting.value = !_isSorting.value }
    fun startSorting() {
        sortingList.clear()
        sortingList.addAll(homeUiState.value)
        _isSorting.value = true
    }
    fun onSortSwap(from: Int, to: Int) { sortingList.apply { add(to, removeAt(from)) } }
    fun confirmSort() {
        viewModelScope.launch {
            val updates = sortingList.mapIndexed { index, model -> model.habit.copy(sortIndex = index) }
            dao.updateHabits(updates)
            _isSorting.value = false
        }
    }
    fun cancelSort() { _isSorting.value = false }
    fun saveSortOrder(newHabits: List<Habit>) {
        viewModelScope.launch {
            val updates = newHabits.mapIndexed { index, habit -> habit.copy(sortIndex = index) }
            dao.updateHabits(updates)
        }
    }

    fun getHabitFlow(habitId: Int): Flow<Habit> = dao.getHabitFlow(habitId)
    fun getHabitRecords(habitId: Int): StateFlow<List<HabitRecord>> = dao.getRecordsByHabitId(habitId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun updateHabitDetails(habit: Habit) { viewModelScope.launch { dao.updateHabit(habit) } }
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
            dao.insertHabit(Habit(name = name, colorValue = color.value.toLong(), isCompleted = false, type = type, startDate = startDate, endDate = endDate, frequency = frequency, targetValue = targetValue, sortIndex = maxIndex + 1))
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