package com.example.habitick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    // 修改这里：回调函数现在返回 名字(String) 和 颜色(Color)
    onConfirm: (String, Color) -> Unit
) {
    var text by remember { mutableStateOf("") }
    // 默认选中第一个颜色（紫色）
    var selectedColor by remember { mutableStateOf(Color(0xFF9C27B0)) }

    // 预设的颜色选项 (紫, 蓝, 深灰, 橙, 青)
    val colorOptions = listOf(
        Color(0xFF9C27B0),
        Color(0xFF03A9F4),
        Color(0xFF424242),
        Color(0xFFFF5722),
        Color(0xFF009688)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加新习惯") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("习惯名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("选择颜色", modifier = Modifier.padding(bottom = 8.dp))

                // 颜色选择行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colorOptions.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp) // 圆圈大小
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (color == selectedColor) 3.dp else 0.dp, // 选中时加个边框
                                    color = Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color } // 点击切换颜色
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        // 把名字和颜色一起传出去
                        onConfirm(text, selectedColor)
                    }
                }
            ) {
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