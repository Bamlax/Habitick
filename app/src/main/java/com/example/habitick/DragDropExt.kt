package com.example.habitick

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue // 【核心修复】必须有这个
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue // 【核心修复】必须有这个
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

@Composable
fun <T : Any> DraggableLazyColumn(
    items: List<T>,
    onSwap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable LazyItemScope.(item: T, isDragging: Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState, onSwap)

    LazyColumn(
        modifier = modifier.pointerInput(dragDropState) {
            detectDragGesturesAfterLongPress(
                onDrag = { change, offset ->
                    change.consume()
                    dragDropState.onDrag(offset)
                },
                onDragStart = { offset -> dragDropState.onDragStart(offset) },
                onDragEnd = { dragDropState.onDragInterrupted() },
                onDragCancel = { dragDropState.onDragInterrupted() }
            )
        },
        state = listState
    ) {
        items(items.size) { index ->
            val item = items[index]
            val isDragging = index == dragDropState.currentIndexOfDraggedItem

            val translationY by animateFloatAsState(
                targetValue = if (isDragging) dragDropState.draggingItemOffset else 0f,
                label = "dragAnimation"
            )

            Column(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        this.translationY = translationY
                        if (isDragging) {
                            scaleX = 1.05f
                            scaleY = 1.05f
                            alpha = 0.9f
                        }
                    }
            ) {
                itemContent(item, isDragging)
            }
        }
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onSwap: (Int, Int) -> Unit
): DragDropState {
    return remember { DragDropState(lazyListState, onSwap) }
}

class DragDropState(
    private val lazyListState: LazyListState,
    private val onSwap: (Int, Int) -> Unit
) {
    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)
    var draggingItemOffset by mutableStateOf(0f)
    private var initialItemOffset = 0

    fun onDragStart(offset: androidx.compose.ui.geometry.Offset) {
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                offset.y.toInt() in item.offset..(item.offset + item.size)
            }?.also {
                currentIndexOfDraggedItem = it.index
                initialItemOffset = it.offset
                draggingItemOffset = 0f
            }
    }

    fun onDragInterrupted() {
        currentIndexOfDraggedItem = null
        draggingItemOffset = 0f
    }

    fun onDrag(offset: androidx.compose.ui.geometry.Offset) {
        val currentIndex = currentIndexOfDraggedItem ?: return
        draggingItemOffset += offset.y

        val currentItemInfo = lazyListState.layoutInfo.visibleItemsInfo
            .find { it.index == currentIndex } ?: return

        val currentEndOffset = initialItemOffset + draggingItemOffset + currentItemInfo.size
        val currentStartOffset = initialItemOffset + draggingItemOffset

        val hoveredItem = lazyListState.layoutInfo.visibleItemsInfo
            .filter { it.index != currentIndex }
            .find { item ->
                val itemCenter = item.offset + item.size / 2
                itemCenter in currentStartOffset.toInt()..currentEndOffset.toInt()
            }

        if (hoveredItem != null) {
            onSwap(currentIndex, hoveredItem.index)
            currentIndexOfDraggedItem = hoveredItem.index
        }
    }
}