package com.example.m3u8player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.m3u8player.ui.navigation.AppNavigation
import com.example.m3u8player.ui.theme.M3U8PlayerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var showNotificationDialog by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showNotificationDialog = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            M3U8PlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
                if (showNotificationDialog) {
                    AlertDialog(
                        onDismissRequest = { showNotificationDialog = false },
                        title = { Text("开启通知权限") },
                        text = { Text("通知用于显示数据更新和下载进度。拒绝后将无法在后台看到任务状态。") },
                        confirmButton = {
                            TextButton(onClick = {
                                showNotificationDialog = false
                                openNotificationSettings()
                            }) {
                                Text("去设置")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showNotificationDialog = false }) {
                                Text("暂不")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)) {
            showNotificationDialog = true
        } else {
            prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            appDetailsSettingsIntent()
        }
        runCatching { startActivity(intent) }.onFailure {
            startActivity(appDetailsSettingsIntent())
        }
    }

    private fun appDetailsSettingsIntent() = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )

    private companion object {
        const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
    }
}
