package com.example.databackup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.databackup.ui.screens.BackupHomeScreen
import com.example.databackup.ui.theme.DataBackupTheme
import com.example.databackup.viewmodel.BackupViewModel

/**
 * 主入口 Activity
 *
 * 文件选择器说明：
 * - 使用 Android Storage Access Framework (SAF) 的 OpenDocument 契约
 * - 原因：Android 10+ 引入了 Scoped Storage，直接访问外部存储受限，SAF 是官方推荐方案
 * - 优势：无需申请 READ_EXTERNAL_STORAGE 权限，由用户主动授权，更安全且兼容性最好
 * - 风险：返回的 URI 权限可能随进程重启失效，因此调用 takePersistableUriPermission 持久化权限
 *
 * ============================================================
 * AndroidManifest.xml 中需要补充的权限声明：
 * ============================================================
 * <!-- Android 10 及以下：传统存储读写权限 -->
 * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
 *     android:maxSdkVersion="29" />
 * <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
 *     android:maxSdkVersion="29" />
 *
 * <!-- Android 11+：所有文件访问权限（仅限文件管理器类应用，Google Play 审核严格） -->
 * <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
 *     tools:ignore="ScopedStorage" />
 *
 * 原因：
 * - /sdcard/backuptool 位于外部存储根目录，Android 11+ 默认无法访问
 * - MANAGE_EXTERNAL_STORAGE 是唯一能让应用自由读写 /sdcard/ 任意路径的权限
 * - 风险：Google Play 通常仅批准文件管理器、备份工具等类型应用使用此权限
 */
class MainActivity : ComponentActivity() {

    private val viewModel: BackupViewModel by viewModels()

    /**
     * 文件选择器启动器
     * 支持选择任意类型文件（*/*），实际项目中可限制为文档类型如 pdf、doc、txt 等
     */
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // 持久化 URI 读取权限，防止后台上传或进程重启后权限丢失
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.onFileSelected(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DataBackupTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BackupHomeScreen(
                        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                        viewModel = viewModel,
                        onRequestManageStorage = { openManageStorageSettings() }
                    )
                }
            }
        }
    }

    /**
     * 跳转到系统设置页，引导用户开启 MANAGE_EXTERNAL_STORAGE 权限
     *
     * 原因：
     * - Android 11+ 不允许应用通过常规权限弹窗申请 MANAGE_EXTERNAL_STORAGE
     * - 必须跳转到系统设置页由用户手动开启
     * - 开启后返回应用，界面会自动刷新权限状态
     */
    private fun openManageStorageSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
        } else {
            // Android 10 及以下不需要此权限，但保留跳转逻辑作为兜底
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        }
        startActivity(intent)
    }

    /**
     * 从设置页返回后重新检查权限状态
     * 用户可能已手动开启/关闭 MANAGE_EXTERNAL_STORAGE，需要刷新 UI
     */
    override fun onResume() {
        super.onResume()
        // 从设置页返回后，Compose 会自动重组，权限状态会通过 viewModel.needsAllFilesPermission() 重新计算
        // 无需额外操作，但可在此处添加日志或埋点
    }
}
