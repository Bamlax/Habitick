package com.example.habitick.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// 1. 定义暗黑模式下的颜色方案
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,      // 使用我们在 Color.kt 定义的蓝色
    secondary = PrimaryDarkBlue,
    tertiary = LightBlueContext
)

// 2. 定义亮色模式下的颜色方案
private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,      // 使用蓝色
    secondary = PrimaryDarkBlue,
    tertiary = LightBlueContext

    /* 如果需要覆盖默认背景色，可以在这里解开注释
    background = BackgroundWhite,
    surface = Color.White,
    */
)

@Composable
fun HabitickTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 注意：这里我把 dynamicColor 改成了 false。
    // 这样可以强制 App 使用你的蓝色设计，而不是跟随用户手机壁纸变色（Material You）。
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}