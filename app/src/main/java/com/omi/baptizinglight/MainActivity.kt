package com.omi.baptizinglight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.omi.baptizinglight.ui.screens.MainScreen
import com.omi.baptizinglight.viewmodel.MainViewModel
import com.omi.baptizinglight.ui.theme.BaptizingLightTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 成功逻辑
        } else {
            // 既然拿不到 Scaffold，我们就通过 ViewModel 转发！
            // 这里的消息会自动流向 MainScreen 的 LaunchedEffect
            mainViewModel.showMessage(this.getString(R.string.notification_permission_toast))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. 启动页安装 (必须在 super.onCreate 之前)
        // 这一步利用了我们在 themes.xml 中定义的 postSplashScreenTheme 自动切换主题
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // 2. 沉浸式状态栏/导航栏支持 (Android 15+ 默认开启，但手动开启更稳健)
        enableEdgeToEdge()

        checkNotificationPermission()

        setContent {
            // 3. 关键点：调用 viewModel() 获取单例。
            // 如果此时报错，请检查 build.gradle 是否引入了 lifecycle-viewmodel-compose 依赖。
            // val mainViewModel: MainViewModel = viewModel()
            val state by mainViewModel.uiState.collectAsState()

            // 4. 应用你的自定义主题（确保 ui/theme 文件夹下的 Theme.kt 已定义）
            BaptizingLightTheme (darkTheme = state.isDarkTheme){

                // 5. 将逻辑大脑注入给表现层
                MainScreen(viewModel = mainViewModel)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            )
            if (status != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}