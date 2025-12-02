package com.example.habitick

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habitick.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionHistoryScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("版本历史", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // 使用 VersionData.kt 中的 versionHistory 变量
            items(versionHistory) { version ->
                VersionItem(version)
                Divider(
                    color = Color.Gray.copy(alpha = 0.1f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            // 底部留白
            item {
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}

@Composable
fun VersionItem(version: VersionInfo) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 版本号
            Text(
                text = "v${version.version}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
            // 日期
            Text(
                text = version.date,
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 更新条目
        version.changes.forEach { change ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                // 【修复】Row 的纵向对齐参数是 verticalAlignment，不是 crossAxisAlignment
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = change,
                    fontSize = 15.sp,
                    color = Color.Black.copy(alpha = 0.8f),
                    lineHeight = 22.sp
                )
            }
        }
    }
}