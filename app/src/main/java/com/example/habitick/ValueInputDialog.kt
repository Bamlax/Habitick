package com.example.habitick

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ValueInputDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    // 根据类型决定提示语和键盘类型
    val (label, keyboardType) = when (habit.type) {
        HabitType.Numeric -> "输入数值 (目标: ${habit.targetValue})" to KeyboardType.Number
        HabitType.Timer -> "输入时长 (目标: ${habit.targetValue})" to KeyboardType.Text // 暂时用文本，如"30min"
        HabitType.TimePoint -> "打卡备注" to KeyboardType.Text
        HabitType.Normal -> "打卡备注" to KeyboardType.Text
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打卡记录") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}