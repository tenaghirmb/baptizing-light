package com.omi.baptizinglight.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omi.baptizinglight.domain.model.FlashlightMode
import com.omi.baptizinglight.service.FlashlightForegroundService
import com.omi.baptizinglight.service.FlashlightService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MainViewModel: 圣光 App 的核心调度大脑
 * 采用 MVI 架构：UI 发送 Event -> ViewModel 处理逻辑 -> 更新 UiState -> UI 渲染
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val flashlightService = FlashlightService.getInstance(application)

    private val maxLevel = flashlightService.getMaxBrightnessLevel()

    // 定义一个 Channel，用于发送字符串消息
    private val _uiEventChannel = Channel<String>()
    // 暴露给外部一个 Flow 用于监听
    val uiEventFlow = _uiEventChannel.receiveAsFlow()

    fun showMessage(message: String) {
        viewModelScope.launch {
            _uiEventChannel.send(message)
        }
    }

    // The Single Source of Truth: The UI Subscribes Only to This State
    private val _uiState = MutableStateFlow(
        FlashlightUiState(
            maxLevel = maxLevel,
            brightness = maxLevel.toFloat()
        )
    )
    val uiState: StateFlow<FlashlightUiState> = _uiState.asStateFlow()

    init {
        // 在初始化时，启动一个协程监听硬件状态变化
        viewModelScope.launch {
            flashlightService.currentMode.collect { mode ->
                // 当 Tile 改变了硬件状态，这里会自动触发，更新 UI 状态
                _uiState.update { it.copy(activeMode = mode) }
            }
        }
    }

    /**
     * handleEvent: 统一的事件入口
     * SRE 视角：所有的系统变更都通过这个单一瓶颈进入，方便做 AOP 拦截或日志记录
     */
    fun handleEvent(event: FlashlightEvent) {
        when (event) {
            is FlashlightEvent.ToggleMode -> handleModeChange(event.mode)
            is FlashlightEvent.UpdateBrightness -> handleBrightnessChange(event.level)
            is FlashlightEvent.UpdateSosUnit -> handleSosUnitChange(event.unitMs)
            is FlashlightEvent.UpdateBreathPeriod -> handleBreathPeriodChange(event.periodMs)
            is FlashlightEvent.SetScreenFlash -> {
                _uiState.update { it.copy(isScreenFlashActive = event.active) }
            }
            is FlashlightEvent.ToggleTheme -> {
                _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
            }
        }
    }

    private fun handleModeChange(newMode: FlashlightMode) {
        _uiState.update { it.copy(activeMode = newMode) }

        // 调用 Service 执行硬件指令
        flashlightService.setMode(
            mode = newMode,
            level = _uiState.value.brightness.toInt(),
            unitMs = _uiState.value.sosUnit,
            periodMs = _uiState.value.breathPeriod
        )

        manageForegroundService(newMode)
    }

    private fun manageForegroundService(mode: FlashlightMode) {
        val context = getApplication<Application>()
        val intent = Intent(context, FlashlightForegroundService::class.java)

        when (mode) {
            FlashlightMode.SOS, FlashlightMode.BREATHING, FlashlightMode.MANUAL -> {
                // 高频闪烁模式，必须开启前台服务防止 CPU 打盹
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
            FlashlightMode.OFF -> {
                // 常亮或关闭模式，不需要高频计秒，可以停止前台服务节省电力
                // (注：如果你希望常亮也不被系统杀掉，可以将 MANUAL 也加入启动列表)
                context.stopService(intent)
            }
            else -> {}
        }
    }

    private fun handleBrightnessChange(newLevel: Float) {
        _uiState.update { it.copy(brightness = newLevel) }

        // 只有在手动模式下，滑动滑块才需要实时更新硬件亮度
        if (_uiState.value.activeMode == FlashlightMode.MANUAL) {
            flashlightService.toggle(on = true, level = newLevel.toInt())
        }

        if (_uiState.value.activeMode == FlashlightMode.BREATHING) {
            flashlightService.setMode(
                mode = FlashlightMode.BREATHING,
                level = newLevel.toInt(),
                periodMs = _uiState.value.breathPeriod
            )
        }
    }

    private fun handleSosUnitChange(newUnit: Long) {
        _uiState.update { it.copy(sosUnit = newUnit) }
        // SOS 模式下支持实时语速调节（带内部防抖）
        if (_uiState.value.activeMode == FlashlightMode.SOS) {
            flashlightService.setMode(FlashlightMode.SOS, unitMs = newUnit)
        }
    }

    private fun handleBreathPeriodChange(newPeriod: Long) {
        _uiState.update { it.copy(breathPeriod = newPeriod) }
        // 呼吸模式下支持实时周期调节
        if (_uiState.value.activeMode == FlashlightMode.BREATHING) {
            flashlightService.setMode(
                mode = FlashlightMode.BREATHING,
                level = _uiState.value.brightness.toInt(),
                periodMs = newPeriod
            )
        }
    }

    /**
     * 遵循生命周期：当 ViewModel 被销毁时，强制关闭硬件资源
     */
    override fun onCleared() {
        super.onCleared()
        flashlightService.setMode(FlashlightMode.OFF)
        //flashlightService.unregister()
    }
}