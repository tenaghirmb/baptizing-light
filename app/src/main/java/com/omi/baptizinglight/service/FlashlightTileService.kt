package com.omi.baptizinglight.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.omi.baptizinglight.domain.model.FlashlightMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlashlightTileService : TileService() {

    // 延迟初始化硬件服务
    private val flashlightService by lazy { FlashlightService.getInstance(this) }
    private val tileScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var serviceJob: Job? = null

    // 当用户添加或看到 Tile 时触发
    override fun onStartListening() {
        super.onStartListening()

        serviceJob?.cancel()

        refreshTile(flashlightService.currentMode.value)

        // 订阅全局状态，实时同步 Tile 图标状态
        serviceJob = tileScope.launch {
            flashlightService.currentMode.collect { mode ->
                refreshTile(mode)
            }
        }
    }

    private fun refreshTile(mode: FlashlightMode) {
        // 实时获取最新的 qsTile，防止引用过期
        val tile = qsTile ?: return
        val isActive = mode != FlashlightMode.OFF

        val newState = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        // 性能优化：只有状态真的变了才去 update，减少系统负担
        if (tile.state != newState) {
            tile.state = newState
            tile.updateTile()
        }
    }

    override fun onClick() {
        val currentMode = flashlightService.currentMode.value
        val nextMode = if (currentMode == FlashlightMode.OFF) FlashlightMode.MANUAL else FlashlightMode.OFF
        val maxLevel = flashlightService.getMaxBrightnessLevel()

        // 1. 乐观更新：点击即更新 UI，不等 Flow 同步
        val tile = qsTile ?: return
        tile.state = if (nextMode != FlashlightMode.OFF) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()

        // 2. 发送指令
        flashlightService.setMode(nextMode, level = maxLevel)
    }

    override fun onStopListening() {
        serviceJob?.cancel()
        super.onStopListening()
    }
}