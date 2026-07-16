package com.example.databackup.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.databackup.data.local.GitHubPullManager
import com.example.databackup.data.local.SecureTokenStorage
import com.example.databackup.data.repository.BackupRepository
import com.example.databackup.data.repository.GitHubBackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri

    private val _selectedFileName = MutableStateFlow("")
    val selectedFileName: StateFlow<String> = _selectedFileName

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

    // GitHub Token 状态（可由 UI 输入更新）
    private val _githubToken = MutableStateFlow("")
    val githubToken: StateFlow<String> = _githubToken

    // 加密存储
    private val tokenStorage = SecureTokenStorage(getApplication())

    init {
        val savedToken = tokenStorage.loadToken()
        if (savedToken.isNotBlank()) {
            _githubToken.value = savedToken
        }
    }

    /**
     * 更新 GitHub Token（用户从界面输入），同时加密保存到本地
     */
    fun updateGitHubToken(token: String) {
        _githubToken.value = token
        if (token.isNotBlank()) {
            tokenStorage.saveToken(token)
        }
    }

    private fun getRepositories(): Map<BackupTarget, BackupRepository> {
        return mapOf<BackupTarget, BackupRepository>(
            BackupTarget.GITHUB to GitHubBackupRepository(
                owner = "DullWoodKnife",
                repo = "backup-doc",
                token = _githubToken.value
            )
        )
    }

    fun onFileSelected(uri: Uri, displayName: String = "") {
        _selectedFileUri.value = uri
        _selectedFileName.value = displayName
        _backupStatus.value = BackupStatus.FileSelected(displayName)
    }

    /**
     * 执行备份流程：
     * 1. git pull：先下载仓库 backups/ 目录内容到 /sdcard/backuptool
     * 2. 上传：将选中的文件上传到 GitHub
     */
    fun performBackup(target: BackupTarget) {
        val uri = _selectedFileUri.value
        if (uri == null) {
            _backupStatus.value = BackupStatus.Error("请先选择文件")
            return
        }
        if (_githubToken.value.isBlank()) {
            _backupStatus.value = BackupStatus.Error("GitHub Token 为空，请在下方输入")
            return
        }
        val repository = getRepositories()[target]
            ?: run {
                _backupStatus.value = BackupStatus.Error("未找到对应备份实现")
                return
            }

        _backupStatus.value = BackupStatus.Loading("${repository.getRepositoryName()} · 同步中")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()

                // ===== 第 1 步：git pull（下载仓库文件到本地） =====
                val pullManager = GitHubPullManager(
                    owner = "DullWoodKnife",
                    repo = "backup-doc",
                    token = _githubToken.value
                )
                val pullResult = pullManager.pullBackups()
                val pullMsg = if (pullResult.isSuccess) pullResult.getOrNull() ?: "同步完成" else "同步失败: ${pullResult.exceptionOrNull()?.message}"

                // ===== 第 2 步：上传文件 =====
                val uploadResult = repository.backupFile(context, uri)

                // 合并 pull 和 upload 的结果
                if (uploadResult.isSuccess) {
                    val uploadMsg = uploadResult.getOrNull() ?: "上传完成"
                    Result.success("$uploadMsg\n\n[本地同步] $pullMsg")
                } else {
                    Result.failure<String>(
                        Exception("上传失败: ${uploadResult.exceptionOrNull()?.message}\n同步结果: $pullMsg")
                    )
                }
            }
            _backupStatus.value = if (result.isSuccess) {
                BackupStatus.Success(result.getOrNull() ?: "备份完成")
            } else {
                BackupStatus.Error(result.exceptionOrNull()?.message ?: "未知错误")
            }
        }
    }

    fun clearStatus() {
        _backupStatus.value = BackupStatus.Idle
    }

    fun needsAllFilesPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()
    }

    fun getManageStorageIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    fun getLocalBackupPath(): String {
        val sdcard = Environment.getExternalStorageDirectory()
        return File(sdcard, "backuptool").absolutePath
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return uri.lastPathSegment?.substringAfterLast('/') ?: "unknown_file"
    }
}

enum class BackupTarget {
    GITHUB
}

sealed class BackupStatus {
    object Idle : BackupStatus()
    data class FileSelected(val uriString: String) : BackupStatus()
    data class Loading(val targetName: String) : BackupStatus()
    data class Success(val message: String) : BackupStatus()
    data class Error(val message: String) : BackupStatus()
}
