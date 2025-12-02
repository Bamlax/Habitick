package com.example.habitick

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.habitick.ui.theme.PrimaryBlue

@Composable
fun SettingsScreen(
    onNavigateToVersionHistory: () -> Unit
) {
    val context = LocalContext.current
    var showDeveloperDialog by remember { mutableStateOf(false) }

    // 【核心修改】动态获取 App 的版本名称
    val appVersionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    if (showDeveloperDialog) {
        DeveloperInfoDialog(
            onDismiss = { showDeveloperDialog = false },
            onGithubClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/YourUsername"))
                context.startActivity(intent)
            },
            onEmailClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:your.email@example.com")
                }
                context.startActivity(intent)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text(
            text = "常规",
            color = PrimaryBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 1. 关于习刻
        FlatSettingItem(
            icon = Icons.Default.Info,
            title = "关于习刻",
            // 【核心修改】这里使用动态获取的版本变量
            subtitle = "版本 $appVersionName",
            onClick = onNavigateToVersionHistory
        )

        Divider(color = Color(0xFFF5F5F5), thickness = 1.dp)

        // 2. 开发者信息
        FlatSettingItem(
            icon = Icons.Default.Person,
            title = "开发者",
            subtitle = "Bamlax",
            onClick = { showDeveloperDialog = true }
        )

        Divider(color = Color(0xFFF5F5F5), thickness = 1.dp)
    }
}

@Composable
fun FlatSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = Color.Black
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.LightGray
        )
    }
}

@Composable
fun DeveloperInfoDialog(
    onDismiss: () -> Unit,
    onGithubClick: () -> Unit,
    onEmailClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "联系开发者",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ContactItem(
                    icon = Icons.Default.Info,
                    title = "GitHub",
                    subtitle = "Bamlax",
                    onClick = onGithubClick
                )
                ContactItem(
                    icon = Icons.Default.Email,
                    title = "电子邮件",
                    subtitle = "发送邮件",
                    onClick = onEmailClick
                )

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("关闭", color = PrimaryBlue)
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Medium)
            Text(text = subtitle, fontSize = 14.sp, color = PrimaryBlue)
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = PrimaryBlue.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}