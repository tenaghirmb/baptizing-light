package com.omi.baptizinglight.viewmodel

import com.omi.baptizinglight.domain.model.FlashlightMode

sealed class FlashlightEvent {
    data class ToggleMode(val mode: FlashlightMode) : FlashlightEvent()
    data class UpdateBrightness(val level: Float) : FlashlightEvent()
    data class UpdateSosUnit(val unitMs: Long) : FlashlightEvent()
    data class UpdateBreathPeriod(val periodMs: Long) : FlashlightEvent()
    data class SetScreenFlash(val active: Boolean) : FlashlightEvent()
    data class ToggleTheme(val isDarkTheme: Boolean) : FlashlightEvent()
}