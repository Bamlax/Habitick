package com.example.habitick

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habitick.ui.theme.PrimaryBlue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    habitModels: List<HabitUiModel>,
    viewModel: HabitViewModel,
    onHabitClick: (Habit) -> Unit
) {
    val isSorting by viewModel.isSorting.collectAsState()
    // 【核心】监听分组后的标签
    val tagsMap by viewModel.tagsMap.collectAsState()

    var showEditorDialog by remember { mutableStateOf(false) }
    var currentHabitUiModel by remember { mutableStateOf<HabitUiModel?>(null) }

    if (showEditorDialog && currentHabitUiModel != null) {
        val habit = currentHabitUiModel!!.habit
        // 【核心】只传当前习惯的标签
        val currentHabitTags = tagsMap[habit.id] ?: emptyList()

        RecordEditorDialog(
            initialNote = currentHabitUiModel!!.todayNote ?: "",
            initialTags = currentHabitUiModel!!.todayTags ?: "",
            allTags = currentHabitTags,
            habitType = habit.type,
            targetValue = habit.targetValue,
            onDismiss = { showEditorDialog = false },
            onConfirm = { note, tags ->
                viewModel.toggleHabit(habit, value = note, tags = tags)
                showEditorDialog = false
            },
            onAddTag = { tagName -> viewModel.addTag(habit.id, tagName) },
            onDeleteTag = { tagName -> viewModel.deleteTag(habit.id, tagName) }
        )
    }

    if (isSorting) {
        DraggableLazyColumn(
            items = viewModel.sortingList,
            onSwap = { from, to -> viewModel.onSortSwap(from, to) },
            modifier = Modifier.fillMaxSize().background(Color.White)
        ) { model, isDragging ->
            HabitItemRow(
                habit = model.habit,
                model = model,
                isSorting = true,
                isDragging = isDragging,
                onHabitClick = {},
                onLongClick = {},
                onCheck = {}
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.White),
            contentPadding = PaddingValues(0.dp)
        ) {
            items(habitModels.size) { index ->
                val model = habitModels[index]
                HabitItemRow(
                    habit = model.habit,
                    model = model,
                    isSorting = false,
                    isDragging = false,
                    onHabitClick = { onHabitClick(model.habit) },
                    onLongClick = {
                        currentHabitUiModel = model
                        showEditorDialog = true
                    },
                    onCheck = { viewModel.toggleHabit(model.habit) }
                )
                if (index < habitModels.size) {
                    Divider(color = Color(0xFFEEEEEE), thickness = 1.dp, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitItemRow(
    habit: Habit,
    model: HabitUiModel,
    isSorting: Boolean,
    isDragging: Boolean,
    onHabitClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheck: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .defaultMinSize(minHeight = 72.dp)
            .background(Color.White)
            .combinedClickable(
                enabled = !isSorting,
                onClick = onHabitClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = habit.name,
                    fontSize = 18.sp,
                    color = if (habit.isCompleted && !isSorting) Color.Gray else habit.color,
                    textDecoration = if (habit.isCompleted && !isSorting) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                val note = model.todayNote ?: ""
                if (note.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = note,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }

            val tags = model.todayTags ?: ""
            if (tags.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.split(",").forEach { tag ->
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isSorting && model.currentStreak >= 2) {
                StreakTag(streak = model.currentStreak)
                Spacer(modifier = Modifier.width(12.dp))
            }

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (isSorting) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "拖拽",
                        tint = Color.LightGray
                    )
                } else {
                    Checkbox(
                        checked = habit.isCompleted,
                        onCheckedChange = { onCheck() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PrimaryBlue,
                            uncheckedColor = Color.LightGray
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun StreakTag(streak: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFF0E0))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "🔥 $streak 天",
            fontSize = 10.sp,
            color = Color(0xFFFF6D00),
            fontWeight = FontWeight.Bold
        )
    }
}