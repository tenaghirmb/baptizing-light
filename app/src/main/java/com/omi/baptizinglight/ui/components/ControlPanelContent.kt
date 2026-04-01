package com.omi.baptizinglight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omi.baptizinglight.R
import com.omi.baptizinglight.domain.Configs.BREATH_PERIOD_MAX
import com.omi.baptizinglight.domain.Configs.BREATH_PERIOD_MIN
import com.omi.baptizinglight.domain.Configs.SOS_UNIT_MAX
import com.omi.baptizinglight.domain.Configs.SOS_UNIT_MIN
import com.omi.baptizinglight.domain.model.FlashlightMode
import com.omi.baptizinglight.viewmodel.FlashlightEvent
import com.omi.baptizinglight.viewmodel.FlashlightUiState

@Composable
fun ControlPanelContent(
    uiState: FlashlightUiState,
    onEvent: (FlashlightEvent) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .navigationBarsPadding()
    ) {
        Text(stringResource(R.string.settingsPrompt), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))

        // 1. 亮度滑块 (SOS 模式下锁定)
        val isSos = uiState.activeMode == FlashlightMode.SOS
        BrightnessControl(
            currentLevel = uiState.brightness,
            maxLevel = uiState.maxLevel,
            locked = isSos,
            onValueChange = { onEvent(FlashlightEvent.UpdateBrightness(it)) },
            onLockedClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        )

        // 2. 动态显示特定模式的参数
        AnimatedVisibility(visible = uiState.activeMode == FlashlightMode.BREATHING) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AvTimer, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.breathing_label) + ": ${uiState.breathPeriod.toInt()}ms")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.breathPeriod.toFloat(),
                    onValueChange = { onEvent(FlashlightEvent.UpdateBreathPeriod(it.toLong())) },
                    valueRange = BREATH_PERIOD_MIN.toFloat()..BREATH_PERIOD_MAX.toFloat()
                )
            }
        }

        AnimatedVisibility(visible = uiState.activeMode == FlashlightMode.SOS) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sos, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sos_unit) + ": ${uiState.sosUnit}ms")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.sosUnit.toFloat(),
                    onValueChange = { onEvent(FlashlightEvent.UpdateSosUnit(it.toLong())) },
                    valueRange = SOS_UNIT_MIN.toFloat()..SOS_UNIT_MAX.toFloat()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun BrightnessControl(
    currentLevel: Float,
    maxLevel: Int,
    locked: Boolean,
    onValueChange: (Float) -> Unit,
    onLockedClick: () -> Unit
) {
    val displayValue = if (locked) maxLevel.toFloat() else currentLevel
    val alpha by animateFloatAsState(if (locked) 0.5f else 1f, label = "alpha")

    Column(modifier = Modifier.alpha(alpha)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (locked) Icons.Default.Lock else Icons.Default.Brightness5, null)
            Spacer(Modifier.width(8.dp))
            Text(if (locked) stringResource(R.string.sos_lock_label) else stringResource(R.string.brightness_label) + ": ${currentLevel.toInt()}")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = displayValue,
            onValueChange = { if (!locked) onValueChange(it) else onLockedClick() },
            valueRange = 1f..maxLevel.toFloat(),
            enabled = !locked,
            colors = SliderDefaults.colors(
                disabledThumbColor = MaterialTheme.colorScheme.error,
                disabledActiveTrackColor = MaterialTheme.colorScheme.error
            )
        )
    }
}