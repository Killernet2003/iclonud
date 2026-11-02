package com.iclonud.bridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF0A84FF)
private val BlueDark = Color(0xFF0060DF)

private val Light = lightColorScheme(
    primary = Blue,
    secondary = BlueDark,
)
private val Dark = darkColorScheme(
    primary = Blue,
    secondary = Color(0xFF5AC8FA),
)

@Composable
fun IclonudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
