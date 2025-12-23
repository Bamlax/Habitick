package com.example.habitick

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habitick.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.math.abs

// ==================== 1. 核心定义 ====================

enum class ChartPeriod { Week, Month, Quarter, Year }

val LightBlueBar = Color(0xFFE3F2FD)
val ContentBackground = Color(0xFFFAFAFA)

// 【修改 1】在数据类中增加 medianValue
data class HabitStats(
    val daysSinceStart: Long,
    val totalCheckIns: Int,
    val checkInRate: Int,
    val totalValue: String,
    val avgValue: String,
    val medianValue: String, // 新增：中位数
    val minValue: String,
    val maxValue: String
)

data class StreakInfo(
    val startDate: Long,
    val endDate: Long,
    val days: Int
)

// ==================== 2. 辅助函数 (全部置顶) ====================

private fun showDatePickerDetail(context: Context, initialDate: Long, onDateSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = initialDate
    android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(year, month, dayOfMonth, 0, 0, 0)
            onDateSelected(calendar.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun getStartOfDayDetail(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun getTodayZeroDetail(): Long = getStartOfDayDetail(System.currentTimeMillis())

private fun getStartOfMonthDetail(ts: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun formatDateFullDetail(ts: Long) = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(Date(ts))
private fun formatDateShortMMDDDetail(ts: Long) = SimpleDateFormat("MM.dd", Locale.CHINA).format(Date(ts))
private fun formatDateShortDetail(ts: Long) = SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(Date(ts))
private fun formatDateCN(ts: Long) = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(ts))

private fun formatMinutesToTimeDetail(minutes: Float): String {
    val total = minutes.toInt()
    val h = total / 60
    val m = total % 60
    return String.format("%02d:%02d", h, m)
}

private fun formatYLabel(value: Float, type: HabitType): String {
    return if (type == HabitType.TimePoint) {
        formatMinutesToTimeDetail(value)
    } else {
        String.format("%.0f", value)
    }
}

private fun extractValueDetail(str: String?, type: HabitType): Float? {
    if (str.isNullOrBlank()) return null
    return if (type == HabitType.TimePoint) {
        val parts = str.split("[:：]".toRegex())
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            (h * 60 + m).toFloat()
        } else null
    } else {
        val matcher = Pattern.compile("(\\d+(\\.\\d+)?)").matcher(str)
        if (matcher.find()) matcher.group(1)?.toFloatOrNull() else null
    }
}

private fun parseFrequencyDetail(freqStr: String): Set<Int> {
    if (freqStr.isBlank()) return setOf(1, 2, 3, 4, 5, 6, 7)
    return try {
        freqStr.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    } catch (e: Exception) {
        setOf(1, 2, 3, 4, 5, 6, 7)
    }
}

private fun formatFrequencyDetail(freq: String): String {
    if (freq.length >= 13) return "每天"
    val days = freq.split(",").mapNotNull { it.toIntOrNull() }
    val sb = StringBuilder()
    days.forEach { sb.append(when(it) { 1->"一";2->"二";3->"三";4->"四";5->"五";6->"六";7->"日";else->"" }); sb.append(",") }
    return if (sb.isNotEmpty()) sb.deleteCharAt(sb.length-1).toString() else "无"
}

private fun getTypeNameDetail(type: HabitType) = when (type) {
    HabitType.Normal -> "普通"
    HabitType.Numeric -> "数值"
    HabitType.Timer -> "计时"
    HabitType.TimePoint -> "时刻"
}

private fun isSameMonthDetail(d1: Long, d2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = d1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = d2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.MONTH) == c2.get(Calendar.MONTH)
}

private fun getDayStrDetail(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return c.get(Calendar.DAY_OF_MONTH).toString()
}

// --- 统计逻辑 ---

private fun processRecordsDetail(input: List<HabitRecord>): List<HabitRecord> {
    return input.sortedBy { it.date }.distinctBy { it.date }
}

private fun isConsecutiveDetail(prevTs: Long, currTs: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = prevTs }
    val c2 = Calendar.getInstance().apply { timeInMillis = currTs }
    c1.add(Calendar.DAY_OF_YEAR, 1)
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun calculateAllStreaksDetail(records: List<HabitRecord>): List<StreakInfo> {
    val validRecords = records.filter { it.isCompleted }.sortedBy { it.date }
    if (validRecords.isEmpty()) return emptyList()

    val streaks = mutableListOf<StreakInfo>()
    var start = validRecords[0].date
    var end = validRecords[0].date
    var count = 1

    for (i in 1 until validRecords.size) {
        val prev = validRecords[i-1].date
        val curr = validRecords[i].date
        if (isConsecutiveDetail(prev, curr)) {
            end = curr
            count++
        } else {
            streaks.add(StreakInfo(start, end, count))
            start = curr
            end = curr
            count = 1
        }
    }
    streaks.add(StreakInfo(start, end, count))
    return streaks.sortedByDescending { it.startDate }
}

private fun calculateStatsDetail(habit: Habit, records: List<HabitRecord>): HabitStats {
    val today = System.currentTimeMillis()
    val start = habit.startDate
    val daysSinceStart = if (today < start) 0 else (today - start) / (24 * 3600 * 1000) + 1

    val completedRecords = records.filter { it.isCompleted }
    val totalCheckIns = completedRecords.size
    val rate = if (daysSinceStart == 0L) 0 else ((totalCheckIns.toFloat() / daysSinceStart) * 100).toInt()

    // 【修改 2】重写数值统计逻辑，收集所有数值以计算中位数
    val validValues = mutableListOf<Float>()
    var totalV = 0f

    records.forEach {
        val v = extractValueDetail(it.value, habit.type) ?: 0f
        if (v > 0) {
            validValues.add(v)
            totalV += v
        }
    }

    val countV = validValues.size
    val minV = if (countV > 0) validValues.minOrNull() ?: 0f else 0f
    val maxV = if (countV > 0) validValues.maxOrNull() ?: 0f else 0f

    // 1. 总计显示
    val displayTotal = if (countV == 0) "-" else if (habit.type == HabitType.TimePoint) "-" else String.format("%.0f", totalV)

    // 2. 平均值计算
    val avg = if (countV == 0) 0f else totalV / countV
    val displayAvg = if (countV == 0) "-" else {
        if (habit.type == HabitType.TimePoint) formatMinutesToTimeDetail(avg)
        else String.format("%.2f", avg)
    }

    // 3. 中位数计算
    validValues.sort()
    val median = if (countV == 0) 0f else {
        if (countV % 2 == 1) {
            validValues[countV / 2]
        } else {
            (validValues[countV / 2 - 1] + validValues[countV / 2]) / 2f
        }
    }
    val displayMedian = if (countV == 0) "-" else {
        if (habit.type == HabitType.TimePoint) formatMinutesToTimeDetail(median)
        else String.format("%.2f", median)
    }

    // 4. 最大最小值显示
    val displayMin = if (countV == 0) "-" else {
        if (habit.type == HabitType.TimePoint) formatMinutesToTimeDetail(minV)
        else String.format("%.1f", minV)
    }

    val displayMax = if (countV == 0) "-" else {
        if (habit.type == HabitType.TimePoint) formatMinutesToTimeDetail(maxV)
        else String.format("%.1f", maxV)
    }

    return HabitStats(
        daysSinceStart,
        totalCheckIns,
        rate,
        displayTotal,
        displayAvg,
        displayMedian, // 传入中位数
        displayMin,
        displayMax
    )
}

private fun generateCalendarGridDetail(monthTime: Long): List<Long> {
    val days = mutableListOf<Long>()
    val cal = Calendar.getInstance()
    cal.timeInMillis = monthTime
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val prevDays = firstDayOfWeek - 1
    cal.add(Calendar.DAY_OF_YEAR, -prevDays)
    repeat(42) {
        days.add(cal.timeInMillis)
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return days
}

private fun generateCheckInStatsDetail(records: List<HabitRecord>, period: ChartPeriod): List<Pair<String, Int>> {
    val cal = Calendar.getInstance()
    val result = mutableListOf<Pair<String, Int>>()
    val recordDates = records.filter { it.isCompleted }.map { it.date }.toSet()
    when(period) {
        ChartPeriod.Week -> {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -6)
            repeat(7) {
                val label = SimpleDateFormat("E", Locale.CHINA).format(cal.time)
                val count = if (recordDates.contains(cal.timeInMillis)) 1 else 0
                result.add(label to count)
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        ChartPeriod.Month -> {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_YEAR, -29)
            repeat(30) {
                val label = SimpleDateFormat("d", Locale.CHINA).format(cal.time)
                val count = if (recordDates.contains(cal.timeInMillis)) 1 else 0
                result.add(label to count)
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        ChartPeriod.Quarter -> return generateCheckInStatsDetail(records, ChartPeriod.Month).takeLast(12)
        ChartPeriod.Year -> {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.MONTH, -11)
            repeat(12) {
                val monthStart = cal.timeInMillis
                val monthLabel = (cal.get(Calendar.MONTH) + 1).toString() + "月"
                cal.add(Calendar.MONTH, 1)
                val monthEnd = cal.timeInMillis
                val count = records.count { it.date in monthStart until monthEnd && it.isCompleted }
                result.add(monthLabel to count)
            }
        }
    }
    return result
}

// ==================== 3. UI 子组件 (全部前置) ====================

@Composable
fun StatItem(modifier: Modifier, label: String, value: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun PeriodSelector(selected: ChartPeriod, onSelect: (ChartPeriod) -> Unit) {
    Row(modifier = Modifier.background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp)).padding(2.dp)) {
        ChartPeriod.values().forEach { period ->
            val isSelected = selected == period
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = when(period){
                        ChartPeriod.Week -> "周"
                        ChartPeriod.Month -> "月"
                        ChartPeriod.Quarter -> "季"
                        ChartPeriod.Year -> "年"
                    },
                    fontSize = 12.sp,
                    color = if (isSelected) PrimaryBlue else Color.Gray,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun ChartSectionContainer(title: String, action: @Composable (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(modifier = Modifier.background(Color.White).padding(16.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            action?.invoke()
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun CheckInBarChart(records: List<HabitRecord>, period: ChartPeriod, graphColor: Color) {
    val dataPoints = remember(records, period) { generateCheckInStatsDetail(records, period) }
    if (dataPoints.isEmpty()) { Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Text("暂无打卡数据", color = Color.LightGray) }; return }
    val maxValue = dataPoints.maxOf { it.second }.coerceAtLeast(1)
    Row(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        Column(modifier = Modifier.width(30.dp).fillMaxHeight().padding(bottom = 20.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Text("$maxValue", fontSize = 10.sp, color = Color.Gray)
            Text("${maxValue/2}", fontSize = 10.sp, color = Color.Gray)
            Text("0", fontSize = 10.sp, color = Color.Gray)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val barWidth = size.width / (dataPoints.size * 1.5f)
            val stepX = size.width / dataPoints.size
            val chartHeight = size.height - 40f
            drawLine(Color(0xFFEEEEEE), Offset(0f, 0f), Offset(size.width, 0f))
            drawLine(Color(0xFFEEEEEE), Offset(0f, chartHeight), Offset(size.width, chartHeight))
            dataPoints.forEachIndexed { index, (label, count) ->
                val barHeight = (count.toFloat() / maxValue) * chartHeight
                val x = index * stepX + (stepX - barWidth) / 2
                val y = chartHeight - barHeight
                drawRect(color = graphColor, topLeft = Offset(x, y), size = Size(barWidth, barHeight))
                if (dataPoints.size < 15 || index % (dataPoints.size/5) == 0) {
                    drawContext.canvas.nativeCanvas.apply { val paint = android.graphics.Paint().apply { this.color = android.graphics.Color.GRAY; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER }; drawText(label, x + barWidth/2, size.height - 10f, paint) }
                }
            }
        }
    }
}

@Composable
fun ValueLineChartV2(records: List<HabitRecord>, graphColor: Color, habitType: HabitType) {
    var selectedIndex by remember { mutableStateOf(-1) }

    val dataPoints = remember(records, habitType) {
        records.mapNotNull { record ->
            val value = extractValueDetail(record.value, habitType)
            if (value != null && value > 0) Pair(record.date, value) else null
        }.sortedBy { it.first }.takeLast(15)
    }

    if (dataPoints.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("暂无数值记录", color = Color.LightGray)
        }
        return
    }

    val yValues = dataPoints.map { it.second }
    val maxY = yValues.maxOrNull() ?: 10f
    val minY = yValues.minOrNull() ?: 0f
    val yRange = (maxY - minY).coerceAtLeast(1f)

    Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        Column(modifier = Modifier.width(40.dp).fillMaxHeight().padding(vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            Text(formatYLabel(maxY, habitType), fontSize = 10.sp, color = Color.Gray)
            Text(formatYLabel((maxY+minY)/2, habitType), fontSize = 10.sp, color = Color.Gray)
            Text(formatYLabel(minY, habitType), fontSize = 10.sp, color = Color.Gray)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val w = size.width
                            if (dataPoints.size > 1) {
                                val stepX = w / (dataPoints.size - 1)
                                val index = (offset.x / stepX).let { Math.round(it).toInt() }
                                if (index in dataPoints.indices) {
                                    val pointX = index * stepX
                                    if (abs(pointX - offset.x) < 40f) {
                                        selectedIndex = index
                                    } else {
                                        selectedIndex = -1
                                    }
                                }
                            } else {
                                selectedIndex = if (dataPoints.isNotEmpty()) 0 else -1
                            }
                        }
                    )
                }
        ) {
            val w = size.width; val h = size.height; val paddingBottom = 40f
            drawLine(Color(0xFFEEEEEE), Offset(0f, 0f), Offset(w, 0f))
            drawLine(Color(0xFFEEEEEE), Offset(0f, (h-paddingBottom)/2), Offset(w, (h-paddingBottom)/2))
            drawLine(Color(0xFFEEEEEE), Offset(0f, h-paddingBottom), Offset(w, h-paddingBottom))

            if (dataPoints.size == 1) {
                val cx = w / 2; val cy = (h - paddingBottom) / 2
                drawCircle(color = graphColor, radius = 4.dp.toPx(), center = Offset(cx, cy))
                drawContext.canvas.nativeCanvas.apply { val paint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER }; drawText(formatDateShortMMDDDetail(dataPoints[0].first), cx, h, paint) }
            } else {
                val path = Path()
                dataPoints.forEachIndexed { index, (timestamp, value) ->
                    val x = index * (w / (dataPoints.size - 1))
                    val yRatio = (value - minY) / yRange
                    val y = (h - paddingBottom) - (yRatio * (h - paddingBottom) * 0.8f) - ((h - paddingBottom) * 0.1f)

                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(color = graphColor, radius = 3.dp.toPx(), center = Offset(x, y))

                    val shouldDrawLabel = when { dataPoints.size <= 5 -> true; index == 0 || index == dataPoints.size - 1 -> true; index % (dataPoints.size / 3) == 0 -> true; else -> false }
                    if (shouldDrawLabel) { drawContext.canvas.nativeCanvas.apply { val paint = android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER }; drawText(formatDateShortMMDDDetail(timestamp), x, h - 10f, paint) } }

                    if (index == selectedIndex) {
                        drawCircle(graphColor.copy(alpha=0.2f), radius=8.dp.toPx(), center=Offset(x, y))
                        drawCircle(graphColor, radius=4.dp.toPx(), center=Offset(x, y))
                        val text = formatYLabel(value, habitType)
                        drawContext.canvas.nativeCanvas.apply { val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 30f; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }; drawText(text, x, y - 20f, paint) } }
                }
                drawPath(path, color = graphColor, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

// ==================== 5. 其他组件 ====================

@Composable
fun TagPieChart(records: List<HabitRecord>) {
    val tagCounts = remember(records) {
        val map = mutableMapOf<String, Int>()
        records.forEach { record ->
            if (record.tags.isNotBlank()) {
                record.tags.split(",").forEach { tag ->
                    if (tag.isNotBlank()) {
                        map[tag] = map.getOrDefault(tag, 0) + 1
                    }
                }
            }
        }
        map
    }

    if (tagCounts.isEmpty()) return

    val total = tagCounts.values.sum()
    val tags = tagCounts.keys.toList()
    val counts = tagCounts.values.toList()

    val colors = remember(tags) {
        List(tags.size) { index ->
            val alpha = 1.0f - (index * 0.8f / tags.size.coerceAtLeast(1))
            PrimaryBlue.copy(alpha = alpha.coerceAtLeast(0.2f))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text("标签分布", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    counts.forEachIndexed { index, count ->
                        val sweepAngle = (count.toFloat() / total) * 360f
                        drawArc(
                            color = colors[index],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                        startAngle += sweepAngle
                    }
                }
            }
            Spacer(modifier = Modifier.width(24.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(tags.size) { index ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(colors[index], CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${tags[index]} (${counts[index]})",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun DayEditorSection(
    selectedDate: Long,
    habit: Habit,
    records: List<HabitRecord>,
    allTags: List<Tag>,
    viewModel: HabitViewModel
) {
    val currentRecord = records.find { it.date == selectedDate }
    val isCompleted = currentRecord?.isCompleted ?: false
    val currentNote = currentRecord?.value ?: ""
    val currentTags = currentRecord?.tags ?: ""

    val today = getTodayZeroDetail()
    val startDate = getStartOfDayDetail(habit.startDate)
    val isValidDate = selectedDate >= startDate && selectedDate <= today

    var showEditorDialog by remember { mutableStateOf(false) }

    if (showEditorDialog) {
        RecordEditorDialog(
            initialNote = currentNote,
            initialTags = currentTags,
            allTags = allTags,
            habitType = habit.type,
            targetValue = habit.targetValue,
            onDismiss = { showEditorDialog = false },
            onConfirm = { note, tags ->
                viewModel.updateRecord(habit, selectedDate, isCompleted = null, note = note, tags = tags)
                showEditorDialog = false
            },
            onAddTag = { viewModel.addTag(habit.id, it) },
            onDeleteTag = { viewModel.deleteTag(habit.id, it) }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = isCompleted,
                enabled = isValidDate,
                onCheckedChange = { checked ->
                    viewModel.updateRecord(habit, selectedDate, isCompleted = checked, note = null, tags = null)
                },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 42.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable(enabled = isValidDate) { showEditorDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (!isValidDate) {
                    Text("无法在此时段记录", color = Color.LightGray, fontSize = 14.sp)
                } else {
                    if (currentNote.isEmpty() && currentTags.isEmpty()) {
                        Text("添加备注/标签", color = Color.Gray, fontSize = 14.sp)
                    } else {
                        Column {
                            if (currentNote.isNotBlank()) {
                                Text(currentNote, color = Color.Black, fontSize = 14.sp)
                            }
                            if (currentTags.isNotBlank()) {
                                if (currentNote.isNotBlank()) Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    currentTags.split(",").forEach { tag ->
                                        if (tag.isNotBlank()) {
                                            Surface(
                                                color = PrimaryBlue.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(4.dp),
                                            ) {
                                                Text(
                                                    text = tag,
                                                    color = PrimaryBlue,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreakListSection(streaks: List<StreakInfo>) {
    if (streaks.isEmpty()) return

    val bestStreak = streaks.sortedWith(
        compareByDescending<StreakInfo> { it.days }
            .thenBy { it.startDate }
    ).firstOrNull() ?: return

    val otherStreaks = streaks.sortedByDescending { it.startDate }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text("最佳连续完成次数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                otherStreaks.forEachIndexed { index, streak ->
                    val isBest = (streak == bestStreak)

                    val bgColor = if (isBest) PrimaryBlue else Color(0xFFEEEEEE)
                    val contentColor = if (isBest) Color.White else Color.Gray
                    val dateColor = if (isBest) Color.Black else Color.LightGray

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(formatDateCN(streak.startDate), color = dateColor, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .width(if (isBest) 60.dp else 40.dp)
                                .height(if (isBest) 30.dp else 24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${streak.days}",
                                color = contentColor,
                                fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(formatDateCN(streak.endDate), color = dateColor, fontSize = 12.sp)
                    }

                    if (index < otherStreaks.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .background(Color(0xFFEEEEEE))
                        )
                    }
                }
            }
        }
    }
}

// 【修改 3】支持点击切换平均/中位数
@Composable
fun StatsGridSection(habit: Habit, stats: HabitStats, showMedian: Boolean, onToggleMedian: () -> Unit) {
    Column(modifier = Modifier.background(Color.White).padding(16.dp)) {
        Row(Modifier.fillMaxWidth()) {
            StatItem(Modifier.weight(1f), "开始时间", formatDateShortDetail(habit.startDate))
            StatItem(Modifier.weight(1f), "重复", formatFrequencyDetail(habit.frequency))
            StatItem(Modifier.weight(1f), "结束时间", if (habit.endDate != null) formatDateShortDetail(habit.endDate) else "-")
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            StatItem(Modifier.weight(1f), "类型", getTypeNameDetail(habit.type))
            StatItem(Modifier.weight(1f), "目标", habit.targetValue ?: "-")
            StatItem(Modifier.weight(1f), "已开始", "${stats.daysSinceStart}天")
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            StatItem(Modifier.weight(1f), "打卡", "${stats.totalCheckIns}天/${stats.checkInRate}%")
            if (habit.type != HabitType.Normal) {
                // 【修改点】点击切换逻辑
                StatItem(
                    modifier = Modifier.weight(1f).clickable { onToggleMedian() },
                    label = if (showMedian) "中位数" else "平均",
                    value = if (showMedian) stats.medianValue else stats.avgValue
                )
                StatItem(Modifier.weight(1f), "总共", stats.totalValue)
            } else {
                Spacer(Modifier.weight(2f))
            }
        }
        if (habit.type != HabitType.Normal) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                StatItem(Modifier.weight(1f), "最小", stats.minValue)
                StatItem(Modifier.weight(1f), "最大", stats.maxValue)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHabitDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(habit.name) }
    var selectedColor by remember { mutableStateOf(habit.color) }
    var selectedType by remember { mutableStateOf(habit.type) }
    var startDate by remember { mutableStateOf(habit.startDate) }
    var endDate by remember { mutableStateOf(habit.endDate) }
    var frequency by remember { mutableStateOf(parseFrequencyDetail(habit.frequency)) }
    var targetValue by remember { mutableStateOf(habit.targetValue ?: "") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = Color.White,
            topBar = {
                TopAppBar(
                    title = { Text("修改习惯", color = Color.White) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White) } },
                    actions = {
                        TextButton(onClick = {
                            if (name.isBlank()) {
                                Toast.makeText(context, "请输入习惯名称", Toast.LENGTH_SHORT).show()
                            } else {
                                val updatedHabit = habit.copy(
                                    name = name,
                                    colorValue = selectedColor.value.toLong(),
                                    type = selectedType,
                                    startDate = startDate,
                                    endDate = endDate,
                                    frequency = frequency.joinToString(","),
                                    targetValue = if (selectedType == HabitType.Normal) null else targetValue
                                )
                                onSave(updatedHabit)
                            }
                        }) { Text("保存", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("习惯名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(modifier = Modifier.height(24.dp)); Text("标记颜色", style = MaterialTheme.typography.titleSmall); Spacer(modifier = Modifier.height(8.dp)); EditColorSelector(selectedColor) { selectedColor = it }
                Spacer(modifier = Modifier.height(24.dp)); Text("任务类型", style = MaterialTheme.typography.titleSmall); Spacer(modifier = Modifier.height(8.dp)); EditTypeSelector(selectedType) { selectedType = it }
                if (selectedType != HabitType.Normal) { Spacer(modifier = Modifier.height(16.dp)); OutlinedTextField(value = targetValue, onValueChange = { targetValue = it }, label = { Text(when(selectedType) { HabitType.Numeric -> "目标数值 (如: 50)"; HabitType.Timer -> "目标时长 (如: 30分钟)"; HabitType.TimePoint -> "目标时刻 (如: 08:00)"; else -> "" }) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                Spacer(modifier = Modifier.height(24.dp)); Text("重复时间", style = MaterialTheme.typography.titleSmall); Spacer(modifier = Modifier.height(8.dp)); EditFrequencySelector(frequency) { newSet -> frequency = newSet }
                Spacer(modifier = Modifier.height(24.dp)); Text("有效日期", style = MaterialTheme.typography.titleSmall); Spacer(modifier = Modifier.height(8.dp)); EditDateRow("开始时间", startDate) { newDate -> if (newDate != null) startDate = newDate }; Spacer(modifier = Modifier.height(12.dp)); EditDateRow("结束时间", endDate, isNullable = true) { newDate -> endDate = newDate }
            }
        }
    }
}

@Composable private fun EditColorSelector(selected: Color, onSelect: (Color) -> Unit) { val colors = listOf(Color(0xFF9C27B0), Color(0xFF03A9F4), Color(0xFF424242), Color(0xFFFF5722), Color(0xFF009688), Color(0xFFE91E63)); Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { colors.forEach { color -> Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color).border(width = if (color == selected) 3.dp else 0.dp, color = Color.LightGray, shape = CircleShape).clickable { onSelect(color) }) } } }
@OptIn(ExperimentalMaterial3Api::class) @Composable private fun EditTypeSelector(selected: HabitType, onSelect: (HabitType) -> Unit) { val types = listOf(HabitType.Normal to "普通", HabitType.Numeric to "数值", HabitType.Timer to "计时", HabitType.TimePoint to "时刻"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { types.forEach { (type, label) -> FilterChip(selected = (type == selected), onClick = { onSelect(type) }, label = { Text(label) }, leadingIcon = if (type == selected) { { Icon(Icons.Filled.Check, null) } } else null, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlue, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White)) } } }
@Composable private fun EditFrequencySelector(selectedDays: Set<Int>, onChange: (Set<Int>) -> Unit) { val days = listOf("一", "二", "三", "四", "五", "六", "日"); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { days.forEachIndexed { index, label -> val dayNum = index + 1; val isSelected = selectedDays.contains(dayNum); Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (isSelected) PrimaryBlue else Color(0xFFEEEEEE)).clickable { val newSet = selectedDays.toMutableSet(); if (isSelected) newSet.remove(dayNum) else newSet.add(dayNum); if (newSet.isNotEmpty()) onChange(newSet) }, contentAlignment = Alignment.Center) { Text(label, color = if (isSelected) Color.White else Color.Gray) } } } }
@Composable private fun EditDateRow(label: String, date: Long?, isNullable: Boolean = false, onDateChange: (Long?) -> Unit) { val context = LocalContext.current; val displayDate = if (date != null) SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(date)) else "永久"; Row(modifier = Modifier.fillMaxWidth().clickable { showDatePickerDetail(context, date ?: System.currentTimeMillis()) { onDateChange(it) } }.background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, fontSize = 16.sp); Row(verticalAlignment = Alignment.CenterVertically) { Text(displayDate, color = if (date == null) Color.Gray else PrimaryBlue); Spacer(modifier = Modifier.width(8.dp)); Icon(Icons.Filled.DateRange, null, tint = Color.Gray, modifier = Modifier.size(20.dp)) } } }

@Composable
fun CompactCalendarSection(
    currentMonth: Long,
    selectedDate: Long,
    records: List<HabitRecord>,
    habit: Habit,
    onMonthChange: (Int) -> Unit,
    onDateClick: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = currentMonth }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val recordMap = remember(records) { records.associateBy { it.date } }
    val today = getTodayZeroDetail()
    val startDate = getStartOfDayDetail(habit.startDate)

    Column(modifier = Modifier.background(Color.White).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(-1) }) { Icon(Icons.Filled.KeyboardArrowLeft, null) }
            Text("$year 年 $month 月", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            IconButton(onClick = { onMonthChange(1) }) { Icon(Icons.Filled.KeyboardArrowRight, null) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column {
            val days = remember(currentMonth) { generateCalendarGridDetail(currentMonth) }
            for (r in 0 until 6) {
                Row(Modifier.fillMaxWidth().height(48.dp)) {
                    for (c in 0 until 7) {
                        val index = r * 7 + c
                        if (index < days.size) {
                            val date = days[index]
                            val isCurrentMonth = isSameMonthDetail(date, currentMonth)
                            val record = recordMap[date]
                            val isSelected = (date == selectedDate)
                            val isValidDate = date >= startDate && date <= today

                            Box(
                                modifier = Modifier
                                    .weight(1f).fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) LightBlueBar else Color.Transparent)
                                    .border(if (isSelected) 1.dp else 0.dp, if (isSelected) PrimaryBlue else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable(enabled = isValidDate) { onDateClick(date) },
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                                    Text(
                                        text = getDayStrDetail(date),
                                        color = if (!isValidDate) Color.LightGray.copy(alpha = 0.5f)
                                        else if (isCurrentMonth) Color.Black else Color.LightGray,
                                        fontSize = 14.sp,
                                        fontWeight = if (record?.isCompleted == true) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (record != null) {
                                        if (!record.value.isNullOrEmpty()) {
                                            Spacer(Modifier.height(0.dp))
                                            val displayText = if (habit.type == HabitType.TimePoint) record.value else record.value.take(4)
                                            Text(
                                                text = displayText,
                                                fontSize = 7.sp,
                                                color = PrimaryBlue,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 8.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        } else if (record.isCompleted) {
                                            Spacer(Modifier.height(4.dp))
                                            Box(modifier = Modifier.size(4.dp).background(PrimaryBlue, CircleShape))
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ==================== 5. 主界面函数 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    habit: Habit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    viewModel: HabitViewModel = viewModel()
) {
    val habitState by viewModel.getHabitFlow(habit.id).collectAsState(initial = habit)
    val currentHabit = habitState ?: habit

    val recordsFlow = remember(currentHabit.id) { viewModel.getHabitRecords(currentHabit.id) }
    val rawRecordsState = recordsFlow.collectAsState()
    val rawRecords = rawRecordsState.value

    val records: List<HabitRecord> = remember(rawRecords) { processRecordsDetail(rawRecords) }
    val stats: HabitStats = remember(records, currentHabit) { calculateStatsDetail(currentHabit, records) }
    val allStreaks = remember(records) { calculateAllStreaksDetail(records) }
    // 监听 tagsMap
    val tagsMap by viewModel.tagsMap.collectAsState()
    val currentTags = tagsMap[currentHabit.id] ?: emptyList()

    var selectedDate: Long by remember { mutableStateOf(getTodayZeroDetail()) }
    var currentCalendarMonth: Long by remember { mutableStateOf(getStartOfMonthDetail(System.currentTimeMillis())) }
    var checkInChartPeriod: ChartPeriod by remember { mutableStateOf(ChartPeriod.Month) }

    // 【修改 4】状态变量控制显示平均还是中位数
    var showMedian by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除习惯") },
            text = { Text("确定要删除 \"${currentHabit.name}\" 及其所有历史记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("删除", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }

    if (showEditDialog) {
        EditHabitDialog(
            habit = currentHabit,
            onDismiss = { showEditDialog = false },
            onSave = { newHabit ->
                viewModel.updateHabitDetails(newHabit)
                showEditDialog = false
            }
        )
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text(currentHabit.name, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = Color.Black)
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Filled.Edit, "修改", tint = PrimaryBlue)
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, "删除", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBlueBar)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .background(ContentBackground)
        ) {
            // 【修改 5】传递状态和回调
            StatsGridSection(
                habit = currentHabit,
                stats = stats,
                showMedian = showMedian,
                onToggleMedian = { showMedian = !showMedian }
            )
            Spacer(modifier = Modifier.height(12.dp))

            CompactCalendarSection(
                currentMonth = currentCalendarMonth,
                selectedDate = selectedDate,
                records = records,
                habit = currentHabit,
                onMonthChange = { diff ->
                    val cal = Calendar.getInstance().apply { timeInMillis = currentCalendarMonth; add(Calendar.MONTH, diff) }
                    currentCalendarMonth = cal.timeInMillis
                },
                onDateClick = { date -> selectedDate = date }
            )

            DayEditorSection(
                selectedDate = selectedDate,
                habit = currentHabit,
                records = records,
                allTags = currentTags,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(16.dp))

            TagPieChart(records)

            ChartSectionContainer(
                title = "打卡统计",
                action = { PeriodSelector(checkInChartPeriod) { period -> checkInChartPeriod = period } }
            ) {
                CheckInBarChart(records, checkInChartPeriod, graphColor = PrimaryBlue)
            }

            Spacer(modifier = Modifier.height(16.dp))

            StreakListSection(allStreaks)

            Spacer(modifier = Modifier.height(16.dp))

            if (currentHabit.type != HabitType.Normal) {
                ChartSectionContainer(title = "数值统计") {
                    ValueLineChartV2(records, graphColor = PrimaryBlue, habitType = currentHabit.type)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}