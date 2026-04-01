package com.omi.baptizinglight.domain

object Configs {
    // WakeLock
    const val WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L // 10 minutes

    // Flash Config
    const val INITIAL_BRIGHTNESS = 1f

    // SOS Signal Unit (ms)
    const val SOS_UNIT_MS = 200L
    const val SOS_UNIT_MIN = 100L
    const val SOS_UNIT_MAX = 500L

    // Breathing Config
    const val BREATH_PERIOD_DEFAULT = 2000L
    const val BREATH_PERIOD_MIN = 1000L
    const val BREATH_PERIOD_MAX = 5000L

    // Sampling Rate (16ms = 60fps)
    const val SAMPLING_INTERVAL_MS = 16L
}