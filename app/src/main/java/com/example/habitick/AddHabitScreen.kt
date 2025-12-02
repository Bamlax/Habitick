package com.example.habitick

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habitick.ui.theme.PrimaryBlue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitScreen(
    onBack: () -> Unit,
    onSave: (String, Color, HabitType, Long, Long?, String, String?) -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color(0xFF9C27B0)) }
    var selectedType by remember { mutableStateOf(HabitType.Normal) }
    var startDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var frequency by remember { mutableStateOf(setOf(1,2,3,4,5,6,7)) }
    var targetValue by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("新建习惯", color = Color.White) }, // 文字改白
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White) // 图标改白
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (name.isBlank()) {
                            Toast.makeText(context, "请输入习惯名称", Toast.LENGTH_SHORT).show()
                        } else {
                            onSave(
                                name,
                                selectedColor,
                                selectedType,
                                startDate,
                                endDate,
                                frequency.joinToString(","),
                                if (selectedType == HabitType.Normal) null else targetValue
                            )
                        }
                    }) {
                        Text("保存", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) // 按钮改白
                    }
                },
                // 【核心修改】顶栏背景设为蓝色
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("习惯名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))

            Text("标记颜色", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            ColorSelector(selectedColor) { selectedColor = it }

            Spacer(modifier = Modifier.height(24.dp))

            Text("任务类型", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            TypeSelector(selectedType) { selectedType = it }

            if (selectedType != HabitType.Normal) {
                Spacer(modifier = Modifier.height(16.dp))
                val label = when(selectedType) {
                    HabitType.Numeric -> "目标数值 (如: 50)"
                    HabitType.Timer -> "目标时长 (如: 30分钟)"
                    HabitType.TimePoint -> "目标时刻 (如: 08:00)"
                    else -> ""
                }
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("重复时间", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            FrequencySelector(frequency) { newSet -> frequency = newSet }

            Spacer(modifier = Modifier.height(24.dp))

            Text("有效日期", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            DateRow("开始时间", startDate) { newDate ->
                if (newDate != null) startDate = newDate
            }
            Spacer(modifier = Modifier.height(12.dp))
            DateRow("结束时间", endDate, isNullable = true) { newDate -> endDate = newDate }
        }
    }
}

// --- 子组件 ---

@Composable
fun ColorSelector(selected: Color, onSelect: (Color) -> Unit) {
    val colors = listOf(
        Color(0xFF9C27B0), Color(0xFF03A9F4), Color(0xFF424242),
        Color(0xFFFF5722), Color(0xFF009688), Color(0xFFE91E63)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (color == selected) 3.dp else 0.dp,
                        color = Color.LightGray,
                        shape = CircleShape
                    )
                    .clickable { onSelect(color) }
            )
        }
    }
}

@Composable
fun TypeSelector(selected: HabitType, onSelect: (HabitType) -> Unit) {
    val types = listOf(
        HabitType.Normal to "普通",
        HabitType.Numeric to "数值",
        HabitType.Timer to "计时",
        HabitType.TimePoint to "时刻"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { (type, label) ->
            FilterChip(
                selected = (type == selected),
                onClick = { onSelect(type) },
                label = { Text(label) },
                leadingIcon = if (type == selected) {
                    { Icon(Icons.Filled.Check, null) }
                } else null,
                // 【核心修改】选中状态强制为蓝色背景+白字
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlue,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                )
            )
        }
    }
}

@Composable
fun FrequencySelector(selectedDays: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { index, label ->
            val dayNum = index + 1
            val isSelected = selectedDays.contains(dayNum)

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) PrimaryBlue else Color(0xFFEEEEEE))
                    .clickable {
                        val newSet = selectedDays.toMutableSet()
                        if (isSelected) newSet.remove(dayNum) else newSet.add(dayNum)
                        if (newSet.isNotEmpty()) onChange(newSet)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (isSelected) Color.White else Color.Gray)
            }
        }
    }
}

@Composable
fun DateRow(label: String, date: Long?, isNullable: Boolean = false, onDateChange: (Long?) -> Unit) {
    val context = LocalContext.current
    val displayDate = if (date != null) {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(date))
    } else "永久"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDatePicker(context, date ?: System.currentTimeMillis()) {
                onDateChange(it)
            } }
            .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(displayDate, color = if (date == null) Color.Gray else PrimaryBlue)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.DateRange, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

fun showDatePicker(context: Context, initialDate: Long, onDateSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = initialDate
    DatePickerDialog(
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