package com.omi.baptizinglight.viewmodel

import com.omi.baptizinglight.domain.Configs
import com.omi.baptizinglight.domain.model.FlashlightMode

data class FlashlightUiState(
    val activeMode: FlashlightMode = FlashlightMode.OFF,
    val brightness: Float = Configs.INITIAL_BRIGHTNESS,
    val sosUnit: Long = Configs.SOS_UNIT_MS,
    val breathPeriod: Long = Configs.BREATH_PERIOD_DEFAULT,
    val maxLevel: Int = 1,
    val isScreenFlashActive: Boolean = false,
    val isDarkTheme: Boolean = true
)
