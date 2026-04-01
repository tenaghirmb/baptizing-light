package com.omi.baptizinglight.service

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.omi.baptizinglight.domain.Configs.BREATH_PERIOD_DEFAULT
import com.omi.baptizinglight.domain.Configs.SAMPLING_INTERVAL_MS
import com.omi.baptizinglight.domain.Configs.SOS_UNIT_MS
import com.omi.baptizinglight.domain.model.FlashlightMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.core.content.edit

/**
 * FlashlightService: 硬件抽象层 (HAL)
 * 职责：负责底层 Camera2 API 的直接调用、协程任务管理及硬件状态同步
 */
class FlashlightService private constructor(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val prefs = context.getSharedPreferences("flashlight_prefs", Context.MODE_PRIVATE)
    private var cameraId: String? = prefs.getString("cached_id", null)

    // 调度器核心：使用 SupervisorJob 确保单个任务失败不会导致整个 Service 崩溃
    private var serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val hardwareMutex = Mutex()
    private var activeJob: Job? = null

    companion object {
        @Volatile
        private var INSTANCE: FlashlightService? = null

        fun getInstance(context: Context): FlashlightService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FlashlightService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // 增加一个 Flow 供外部订阅状态
    private val _currentMode = MutableStateFlow(FlashlightMode.OFF)
    val currentMode = _currentMode.asStateFlow()

    init {
        if (cameraId != null) {
            // 1. 如果有缓存，优先保证响应速度
            android.util.Log.d("SRE", "Using cached CameraID: $cameraId")
        }

        // 2. 无论有没有缓存，都在后台偷偷验证/更新一次
        serviceScope.launch(Dispatchers.Default) {
            val latestId = fetchCameraIdFromSystem() // 真正的 Camera2 遍历
            if (latestId != cameraId) {
                cameraId = latestId
                prefs.edit { putString("cached_id", latestId) }
                android.util.Log.d("SRE", "CameraID Cache Updated")
            }
        }
    }

    private fun fetchCameraIdFromSystem(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    /**
     * 获取硬件支持的最大亮度等级 (API 33+)
     */
    fun getMaxBrightnessLevel(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                cameraId?.let {
                    cameraManager.getCameraCharacteristics(it)
                        .get(CameraCharacteristics.FLASH_TORCH_STRENGTH_MAX_LEVEL) ?: 1
                } ?: 1
            } catch (e: Exception) { 1 }
        } else { 1 }
    }

    /**
     * 核心调度：模式切换
     * 采用“先终止、后启动”的原子策略，防止协程竞争硬件资源
     */
    fun setMode(
        mode: FlashlightMode,
        level: Int = 1,
        unitMs: Long = SOS_UNIT_MS,
        periodMs: Long = BREATH_PERIOD_DEFAULT
    ) {
        if (!serviceScope.isActive) {
            serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        }
        _currentMode.value = mode
        serviceScope.launch {
            hardwareMutex.withLock {
                activeJob?.cancelAndJoin() // 等待旧任务彻底结束

                toggle(false)

                activeJob = when (mode) {
                    FlashlightMode.MANUAL -> {
                        toggle(true, level)
                        null // 常亮模式不需要循环 Job
                    }

                    FlashlightMode.SOS -> launchSosJob(unitMs)
                    FlashlightMode.BREATHING -> launchBreathingJob(periodMs, level)
                    FlashlightMode.OFF -> null
                }
            }
        }
    }

    /**
     * 基础开关与亮度调节
     */
    fun toggle(on: Boolean, level: Int = 1) {
        val id = cameraId ?: return
        try {
            if (on) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && getMaxBrightnessLevel() > 1) {
                    val safeLevel = level.coerceIn(1, getMaxBrightnessLevel())
                    cameraManager.turnOnTorchWithStrengthLevel(id, safeLevel)
                } else {
                    cameraManager.setTorchMode(id, true)
                }
            } else {
                cameraManager.setTorchMode(id, false)
            }
        } catch (e: Exception) {
            // SRE 实践：硬件占用异常不抛出，仅记录，防止 App Crash
            android.util.Log.e("SRE", "Hardware error: ${e.message}")
        }
    }

    // --- 内部私有任务实现 ---

    private fun launchSosJob(unitMs: Long) = serviceScope.launch {
        val maxLevel = getMaxBrightnessLevel()
        while (isActive) {
            // SOS: ... --- ...
            repeat(3) { sendSignal(unitMs, unitMs, maxLevel) } // S
            delay(unitMs * 2)
            repeat(3) { sendSignal(unitMs * 3, unitMs, maxLevel) } // O
            delay(unitMs * 2)
            repeat(3) { sendSignal(unitMs, unitMs, maxLevel) } // S
            delay(unitMs * 7) // Word Gap
        }
    }

    private fun launchBreathingJob(periodMs: Long, targetLevel: Int) = serviceScope.launch {
        val id = cameraId ?: return@launch
        val maxLevel = getMaxBrightnessLevel()
        val frequency = 2 * Math.PI / periodMs
        val startTime = System.currentTimeMillis()
        var lastLevel = -1

        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = (Math.sin(frequency * elapsed - Math.PI / 2) + 1) / 2

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxLevel > 1) {
                val currentLevel = (progress * (targetLevel - 1)).toInt() + 1
                if (currentLevel != lastLevel) {
                    try {
                        cameraManager.turnOnTorchWithStrengthLevel(id, currentLevel)
                        lastLevel = currentLevel
                    } catch (e: Exception) { yield() }
                }
                delay(SAMPLING_INTERVAL_MS)
            } else {
                // Legacy PWM 模拟 (针对不支持亮度调节的设备)
                cameraManager.setTorchMode(id, true)
                delay((progress * 30).toLong().coerceAtLeast(1L))
                cameraManager.setTorchMode(id, false)
                delay((30 - progress * 30).toLong().coerceAtLeast(1L))
            }
        }
    }

    private suspend fun sendSignal(duration: Long, gap: Long, level: Int) {
        toggle(true, level)
        delay(duration)
        toggle(false)
        delay(gap)
    }

    /**
     * 资源释放：防止内存泄漏和摄像头常驻
     */
    fun unregister() {
        activeJob?.cancel()
        serviceScope.cancel()
        toggle(false)
    }
}