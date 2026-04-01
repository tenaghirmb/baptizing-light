package com.omi.baptizinglight.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omi.baptizinglight.domain.model.FlashlightMode
import com.omi.baptizinglight.viewmodel.FlashlightEvent
import com.omi.baptizinglight.viewmodel.FlashlightUiState
import com.omi.baptizinglight.viewmodel.MainViewModel
import com.omi.baptizinglight.ui.components.ControlPanelContent
import com.omi.baptizinglight.R
import com.omi.baptizinglight.ui.components.ScreenLightOverlay
import com.omi.baptizinglight.ui.theme.BaptizingLightTheme

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    // 1. 订阅唯一真值源 (SSOT)
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 核心监听逻辑：监听 ViewModel 转发过来的消息
    LaunchedEffect(viewModel.uiEventFlow) {
        viewModel.uiEventFlow.collect { message ->
            // 当收到消息时，立刻弹出 Snackbar
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
                withDismissAction = true
            )
        }
    }

    // 将状态与逻辑拆分，方便 Preview 渲染
    MainScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = { viewModel.handleEvent(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    uiState: FlashlightUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onEvent: (FlashlightEvent) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    // 这里开始自定义！
                    Card(
                        shape = RoundedCornerShape(16.dp), // 大圆角
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = data.visuals.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        // 强制切换主题按钮
                        IconButton(onClick = { onEvent(FlashlightEvent.ToggleTheme(!uiState.isDarkTheme)) }) {
                            Icon(
                                imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                // 只有非 OFF 模式显示设置按钮
                if (uiState.activeMode != FlashlightMode.OFF) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.settings)) },
                        icon = { Icon(Icons.Default.Tune, null) },
                        onClick = { showSheet = true }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 主开关状态显示
                    MainPowerButton(
                        isActive = uiState.activeMode != FlashlightMode.OFF,
                        onClick = {
                            val nextMode = if (uiState.activeMode == FlashlightMode.OFF)
                                FlashlightMode.MANUAL else FlashlightMode.OFF
                            onEvent(FlashlightEvent.ToggleMode(nextMode))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // 模式选择器 (Segmented Button 风格)
                    ModeSelector(
                        activeMode = uiState.activeMode,
                        onModeChange = { onEvent(FlashlightEvent.ToggleMode(it)) }
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    // 独立的屏幕补光开关
                    OutlinedButton(
                        onClick = {
                            onEvent(FlashlightEvent.SetScreenFlash(true))
                        },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.status_screen))
                    }
                }
            }

            // 底部设置抽屉
            if (showSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSheet = false },
                    sheetState = sheetState,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    ControlPanelContent(
                        uiState = uiState,
                        onEvent = onEvent
                    )
                }
            }
        }

        ScreenLightOverlay(
            isVisible = uiState.isScreenFlashActive,
            onExit = { onEvent(FlashlightEvent.SetScreenFlash(false)) }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    // 提供 Mock 数据供 Preview 预览，避免实例化 ViewModel 导致的渲染失败
    BaptizingLightTheme(darkTheme = false) {
        MainScreenContent(
            uiState = FlashlightUiState(),
            onEvent = {}
        )
    }
}

@Composable
fun MainPowerButton(isActive: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "color"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(2.dp, color),
        modifier = Modifier.size(160.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = color
            )
        }
    }
}

@Composable
fun ModeSelector(activeMode: FlashlightMode, onModeChange: (FlashlightMode) -> Unit) {
    val modes = listOf(FlashlightMode.MANUAL, FlashlightMode.SOS, FlashlightMode.BREATHING)

    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(horizontal = 16.dp)) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = activeMode == mode,
                onClick = { onModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                label = {
                    Text(when(mode) {
                        FlashlightMode.MANUAL -> stringResource(R.string.status_manual)
                        FlashlightMode.SOS -> stringResource(R.string.status_sos)
                        FlashlightMode.BREATHING -> stringResource(R.string.status_breathing )
                        else -> ""
                    })
                }
            )
        }
    }
}