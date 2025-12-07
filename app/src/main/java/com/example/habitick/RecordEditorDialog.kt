package com.example.habitick

import androidx.compose.foundation.ExperimentalFoundationApi // 【修复】补上这个关键导包
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.habitick.ui.theme.PrimaryBlue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordEditorDialog(
    initialNote: String,
    initialTags: String,
    allTags: List<Tag>,
    habitType: HabitType,
    targetValue: String?,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onAddTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit
) {
    var note by remember { mutableStateOf(initialNote) }
    val selectedTags = remember { mutableStateListOf<String>().apply {
        if (initialTags.isNotBlank()) addAll(initialTags.split(","))
    } }

    var showAddTagDialog by remember { mutableStateOf(false) }

    val keyboardType = if (habitType == HabitType.Numeric) KeyboardType.Number else KeyboardType.Text
    val label = if (habitType == HabitType.Numeric) "数值 (目标: $targetValue)" else "备注"

    if (showAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("新建标签") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("标签名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTagName.isNotBlank()) {
                        onAddTag(newTagName)
                        showAddTagDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("取消") }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("编辑记录", style = MaterialTheme.typography.titleLarge, color = Color.Black)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("标签", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showAddTagDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "添加标签", tint = PrimaryBlue)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allTags) { tag ->
                        val isSelected = selectedTags.contains(tag.name)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedTags.remove(tag.name)
                                else selectedTags.add(tag.name)
                            },
                            label = { Text(tag.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFF5F5F5)
                            ),
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    if (isSelected) selectedTags.remove(tag.name)
                                    else selectedTags.add(tag.name)
                                },
                                onLongClick = {
                                    onDeleteTag(tag.name)
                                    selectedTags.remove(tag.name)
                                }
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(note, selectedTags.joinToString(","))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}