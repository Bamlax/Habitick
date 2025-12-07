package com.example.habitick

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habitick.ui.theme.PrimaryBlue
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HabitViewModel = viewModel()
) {
    val heatmapData by viewModel.heatmapData.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedRecords by viewModel.selectedDateRecords.collectAsState()
    val currentViewingMonth by viewModel.currentViewingMonth.collectAsState()
    val allHabits by viewModel.habits.collectAsState()
    // 【新增】获取所有标签，供弹窗选择
    val allTags by viewModel.allTags.collectAsState()

    val monthDays = remember(currentViewingMonth) { generateDaysForMonthHistory(currentViewingMonth) }
    val oneYearAgoStart = remember { getOneYearAgoStartHistory() }

    // 弹窗状态
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var editingRecordValue by remember { mutableStateOf("") }
    var editingRecordTags by remember { mutableStateOf("") }

    // 【核心修改】使用通用的 RecordEditorDialog (带标签功能)
    if (showEditorDialog && editingHabit != null) {
        RecordEditorDialog(
            initialNote = editingRecordValue,
            initialTags = editingRecordTags,
            allTags = allTags,
            habitType = editingHabit!!.type,
            targetValue = editingHabit!!.targetValue,
            onDismiss = { showEditorDialog = false },
            onConfirm = { note, tags ->
                // 保存备注和标签
                viewModel.updateRecord(editingHabit!!, selectedDate, isCompleted = null, note = note, tags = tags)
                showEditorDialog = false
            },
            onAddTag = { viewModel.addTag(it) },
            onDeleteTag = { viewModel.deleteTag(it) }
        )
    }

    val visibleHabits = remember(allHabits, selectedDate) {
        allHabits.filter { habit ->
            val habitStartDay = getStartOfDayHistory(habit.startDate)
            val currentDay = getStartOfDayHistory(selectedDate)
            val isStarted = currentDay >= habitStartDay
            val isNotEnded = habit.endDate == null || currentDay <= getStartOfDayHistory(habit.endDate)
            isStarted && isNotEnded
        }
    }

    val recordMap = remember(selectedRecords) { selectedRecords.associateBy { it.habitId } }

    val today = remember { getStartOfDayHistory(System.currentTimeMillis()) }
    val isFutureDate = selectedDate > today

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // 1. 年度热力图
            Text("年度回顾", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(52) { weekIndex ->
                    Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        repeat(7) { dayIndex ->
                            val date = calculateDateForYearGridHistory(oneYearAgoStart, weekIndex, dayIndex)
                            val count = heatmapData[date] ?: 0
                            Box(modifier = Modifier.size(5.dp).background(calculateColorHistory(count), RoundedCornerShape(1.dp)))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. 月度日历
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("本月详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.changeMonth(-1) }) { Icon(Icons.Filled.KeyboardArrowLeft, null) }
                    Text(formatMonthHistory(currentViewingMonth), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                    IconButton(onClick = { viewModel.changeMonth(1) }) { Icon(Icons.Filled.KeyboardArrowRight, null) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            MonthGridHistory(
                days = monthDays,
                heatmapData = heatmapData,
                selectedDate = selectedDate,
                onDateClick = { viewModel.selectDate(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color(0xFFEEEEEE))
            Spacer(modifier = Modifier.height(16.dp))

            // 3. 历史修改列表
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("历史修改", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(formatDateHistory(selectedDate), fontSize = 12.sp, color = Color.Gray)
            }

            if (isFutureDate) {
                Text("未来日期不可修改", fontSize = 12.sp, color = Color.Red.copy(alpha = 0.6f))
            } else {
                Text("点击条目切换打卡，长按条目修改备注/标签", fontSize = 12.sp, color = Color.LightGray)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (visibleHabits.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("该日期没有任何有效任务", fontSize = 14.sp, color = Color.Gray)
            } else {
                visibleHabits.forEach { habit ->
                    val record = recordMap[habit.id]
                    val isCompleted = record?.isCompleted == true
                    val note = record?.value
                    val tags = record?.tags

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .alpha(if (isFutureDate) 0.5f else 1f)
                            .combinedClickable(
                                enabled = !isFutureDate,
                                onClick = {
                                    // 点击：切换打卡状态 (保留原有的备注和标签)
                                    viewModel.toggleHabit(habit, dateOverride = selectedDate)
                                },
                                onLongClick = {
                                    // 长按：编辑备注和标签
                                    editingHabit = habit
                                    editingRecordValue = note ?: ""
                                    editingRecordTags = tags ?: ""
                                    showEditorDialog = true
                                }
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 打卡状态圆圈
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(if (isCompleted) PrimaryBlue else Color.Transparent, CircleShape)
                                .border(1.dp, if (isCompleted) PrimaryBlue else Color.LightGray, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        // 习惯名称及备注标签
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = habit.name,
                                fontSize = 16.sp,
                                color = if (isCompleted) Color.Black else Color.Gray
                            )

                            // 【核心修改】显示标签和备注
                            val display = if(!tags.isNullOrEmpty()) "🏷️$tags ${note ?: ""}" else note
                            if (!display.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = display,
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        if (isCompleted) {
                            Text("已完成", fontSize = 12.sp, color = PrimaryBlue)
                        }
                    }
                    Divider(color = Color(0xFFF5F5F5))
                }
            }
        }
    }
}

// --- 辅助函数 (保持不变) ---

@Composable
private fun MonthGridHistory(days: List<Long>, heatmapData: Map<Long, Int>, selectedDate: Long, onDateClick: (Long) -> Unit) {
    val columns = 7
    val rows = (days.size + columns - 1) / columns
    Column {
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until columns) {
                    val index = r * columns + c
                    if (index < days.size) {
                        val date = days[index]
                        val count = heatmapData[date] ?: 0
                        val isSelected = selectedDate == date
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(calculateColorHistory(count), RoundedCornerShape(4.dp))
                                .border(if (isSelected) 2.dp else 0.dp, if (isSelected) Color.Black else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable { onDateClick(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(getDayOfMonthHistory(date), fontSize = 12.sp, color = if (count > 3) Color.White else Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

private fun getStartOfDayHistory(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun calculateColorHistory(count: Int): Color {
    return if (count == 0) Color(0xFFF0F0F0)
    else PrimaryBlue.copy(alpha = (0.2f + count * 0.15f).coerceAtMost(1.0f))
}

private fun formatDateHistory(timestamp: Long): String = SimpleDateFormat("MM月dd日", Locale.CHINA).format(Date(timestamp))
private fun formatMonthHistory(timestamp: Long): String = SimpleDateFormat("yyyy年MM月", Locale.CHINA).format(Date(timestamp))

private fun getDayOfMonthHistory(timestamp: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    return cal.get(Calendar.DAY_OF_MONTH).toString()
}

private fun calculateDateForYearGridHistory(start: Long, weekIndex: Int, dayIndex: Int): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = start
    cal.add(Calendar.DAY_OF_YEAR, (weekIndex * 7) + dayIndex)
    return cal.timeInMillis
}

private fun getOneYearAgoStartHistory(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.WEEK_OF_YEAR, -52)
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    return cal.timeInMillis
}

private fun generateDaysForMonthHistory(monthStart: Long): List<Long> {
    val list = mutableListOf<Long>()
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = monthStart

    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (i in 1..maxDay) {
        list.add(calendar.timeInMillis)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return list
}