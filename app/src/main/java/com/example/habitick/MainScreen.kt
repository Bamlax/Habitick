package com.example.habitick

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.habitick.ui.theme.PrimaryBlue

sealed class OverlayState {
    object None : OverlayState()
    object AddHabit : OverlayState()
    data class HabitDetail(val habit: Habit) : OverlayState()
    object VersionHistory : OverlayState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: HabitViewModel = viewModel()
) {
    var selectedItem by remember { mutableStateOf(0) }
    var currentOverlay by remember { mutableStateOf<OverlayState>(OverlayState.None) }

    val isSorting by viewModel.isSorting.collectAsState()

    BackHandler(enabled = currentOverlay != OverlayState.None || isSorting) {
        if (isSorting) {
            viewModel.cancelSort()
        } else {
            currentOverlay = OverlayState.None
        }
    }

    val habitUiModels by viewModel.homeUiState.collectAsState()
    val items = listOf("今天", "历史", "设置")
    val icons = listOf(Icons.Filled.CheckCircle, Icons.Filled.DateRange, Icons.Filled.Settings)

    LaunchedEffect(selectedItem) {
        if (selectedItem != 2 && currentOverlay is OverlayState.VersionHistory) {
            currentOverlay = OverlayState.None
        }
    }

    AnimatedContent(
        targetState = currentOverlay,
        label = "PageTransition",
        transitionSpec = {
            if (targetState == OverlayState.None) {
                slideInHorizontally { width -> -width } togetherWith slideOutHorizontally { width -> width }
            } else {
                slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
            }
        }
    ) { targetState ->
        when (targetState) {
            is OverlayState.AddHabit -> {
                AddHabitScreen(
                    onBack = { currentOverlay = OverlayState.None },
                    onSave = { name, color, type, start, end, freq, target ->
                        viewModel.addHabit(name, color, type, start, end, freq, target)
                        currentOverlay = OverlayState.None
                    }
                )
            }
            is OverlayState.HabitDetail -> {
                HabitDetailScreen(
                    habit = targetState.habit,
                    onBack = { currentOverlay = OverlayState.None },
                    onDelete = {
                        viewModel.deleteHabit(targetState.habit)
                        currentOverlay = OverlayState.None
                    },
                    viewModel = viewModel
                )
            }
            is OverlayState.VersionHistory -> {
                VersionHistoryScreen(
                    onBack = { currentOverlay = OverlayState.None }
                )
            }
            is OverlayState.None -> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if(isSorting) "拖动排序" else "习刻 Habitick") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = PrimaryBlue,
                                titleContentColor = Color.White
                            ),
                            actions = {
                                if (selectedItem == 0) {
                                    if (isSorting) {
                                        IconButton(onClick = { viewModel.confirmSort() }) {
                                            Icon(Icons.Filled.Check, contentDescription = "完成", tint = Color.White)
                                        }
                                    } else {
                                        // 【修改】点击直接进入排序模式
                                        IconButton(onClick = { viewModel.startSorting() }) {
                                            Icon(Icons.Filled.List, contentDescription = "排序", tint = Color.White)
                                        }
                                        IconButton(onClick = { currentOverlay = OverlayState.AddHabit }) {
                                            Icon(Icons.Filled.Add, contentDescription = "添加", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        )
                    },
                    bottomBar = {
                        if (!isSorting) {
                            NavigationBar(containerColor = Color.White) {
                                items.forEachIndexed { index, item ->
                                    NavigationBarItem(
                                        icon = { Icon(icons[index], contentDescription = item) },
                                        label = { Text(item) },
                                        selected = selectedItem == index,
                                        onClick = { selectedItem = index },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = PrimaryBlue,
                                            selectedTextColor = PrimaryBlue,
                                            indicatorColor = PrimaryBlue.copy(alpha = 0.2f),
                                            unselectedIconColor = Color.Gray,
                                            unselectedTextColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedItem) {
                            0 -> TodayScreen(
                                habitModels = habitUiModels,
                                viewModel = viewModel,
                                onHabitClick = { habit -> currentOverlay = OverlayState.HabitDetail(habit) }
                            )
                            1 -> HistoryScreen(viewModel = viewModel)
                            2 -> SettingsScreen(
                                onNavigateToVersionHistory = {
                                    currentOverlay = OverlayState.VersionHistory
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}