package com.example.habitick

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    val sortingList = remember { mutableStateListOf<HabitUiModel>() }
    // 【新增】获取所有标签
    val allTags by viewModel.allTags.collectAsState()

    LaunchedEffect(isSorting, habitModels) {
        if (isSorting) {
            if (sortingList.isEmpty() || sortingList.size != habitModels.size) {
                sortingList.clear()
                sortingList.addAll(habitModels)
            }
        } else {
            if (sortingList.isNotEmpty()) {
                val habitsToSave = sortingList.map { it.habit }
                viewModel.saveSortOrder(habitsToSave)
                sortingList.clear()
            }
        }
    }

    // 弹窗状态
    var showEditorDialog by remember { mutableStateOf(false) }
    var currentHabitForDialog by remember { mutableStateOf<Habit?>(null) }
    var currentNoteForDialog by remember { mutableStateOf("") }
    var currentTagsForDialog by remember { mutableStateOf("") }

    // 【修改】调用通用的 RecordEditorDialog
    if (showEditorDialog && currentHabitForDialog != null) {
        RecordEditorDialog(
            initialNote = currentNoteForDialog,
            initialTags = currentTagsForDialog,
            allTags = allTags,
            habitType = currentHabitForDialog!!.type,
            targetValue = currentHabitForDialog!!.targetValue,
            onDismiss = { showEditorDialog = false },
            onConfirm = { note, tags ->
                viewModel.toggleHabit(currentHabitForDialog!!, value = note, tags = tags)
                showEditorDialog = false
            },
            onAddTag = { viewModel.addTag(it) },
            onDeleteTag = { viewModel.deleteTag(it) }
        )
    }

    if (isSorting) {
        DraggableLazyColumn(
            items = sortingList,
            onSwap = { from, to -> viewModel.onSortSwap(from, to) }
        ) { model, isDragging ->
            val habit = model.habit
            HabitItemRow(
                habit = habit,
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
                        // 长按编辑
                        currentHabitForDialog = model.habit
                        currentNoteForDialog = model.todayNote ?: ""
                        currentTagsForDialog = model.todayTags ?: ""
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
            .height(72.dp)
            .background(Color.White)
            .combinedClickable(
                enabled = !isSorting,
                onClick = onHabitClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = habit.name,
                    fontSize = 16.sp,
                    color = if (habit.isCompleted && !isSorting) Color.Gray else habit.color,
                    textDecoration = if (habit.isCompleted && !isSorting) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )

                // 显示备注和标签
                val note = model.todayNote ?: ""
                val tags = model.todayTags ?: ""
                val display = if(tags.isNotEmpty()) "🏷️$tags $note" else note

                if (display.isNotBlank()) {
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