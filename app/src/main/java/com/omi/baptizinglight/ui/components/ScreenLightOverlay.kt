package com.omi.baptizinglight.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omi.baptizinglight.ui.theme.RadiantWhite
import com.omi.baptizinglight.R

@Composable
fun ScreenLightOverlay(
    isVisible: Boolean,
    onExit: () -> Unit
) {
    // State Management: Current Fill Light Color
    var currentColor by remember { mutableStateOf(RadiantWhite) }
    
    // Safely obtain activity; LocalActivity.current can be null in Previews.
    val activity = LocalActivity.current

    // Brightness Burst: 100% upon entry, restored upon exit
    DisposableEffect(isVisible, activity) {
        if (isVisible && activity != null) {
            val window = activity.window
            val params = window.attributes
            val oldBrightness = params.screenBrightness
            params.screenBrightness = 1.0f // Force Maximum Brightness
            window.attributes = params

            onDispose {
                params.screenBrightness = oldBrightness // Restore System Brightness
                window.attributes = params
            }
        } else {
            onDispose {}
        }
    }

    // Handling Physical Back Button to Exit
    BackHandler(enabled = isVisible) {
        onExit()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + scaleIn(initialScale = 0.9f),
        exit = fadeOut(tween(400)) + scaleOut(targetScale = 0.9f)
    ) {
        // Full-Screen Glowing Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 关键点 1：拦截所有点击事件，防止穿透到底层按钮
                .pointerInput(Unit) {
                    detectTapGestures {
                        // 如果你希望点击屏幕任何地方都能退出，就调用 onExit()
                        // 如果只想点那个特定的退出按钮，这里就留空
                        // onExit()
                    }
                }
                .background(currentColor)
        ) {
            // Bottom Color Selection Bar
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                    .padding(12.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                listOf(Color.White, Color.Yellow, Color.Red, Color.Cyan, Color.Magenta).forEach { color ->
                    ColorDot(
                        color = color,
                        isSelected = currentColor == color,
                        onClick = { currentColor = color }
                    )
                }
            }

            Text(
                text = stringResource(R.string.screen_flash_overlay_exit_message),
                style = MaterialTheme.typography.bodyLarge,
                color = if (currentColor.luminance() > 0.5f) Color.DarkGray else Color.LightGray,
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(0.5f)
            )
        }
    }
}

@Composable
fun ColorDot(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .size(if (isSelected) 44.dp else 36.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
            .clickable { onClick() }
    )
}