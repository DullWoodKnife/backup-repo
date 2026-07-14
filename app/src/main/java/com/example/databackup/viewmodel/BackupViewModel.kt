package com.example.databackup.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.databackup.data.local.LocalDocumentManager
import com.example.databackup.BuildConfig
import com.example.databackup.data.local.SCIDocumentFormatter
import com.example.databackup.data.repository.BackupRepository
import com.example.databackup.data.repository.GitHubBackupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

    // GitHub Token 状态（可由 UI 输入更新）
    private val _githubToken = MutableStateFlow(BuildConfig.GITHUB_TOKEN)
    val githubToken: StateFlow<String> = _githubToken

    /**
     * 更新 GitHub Token（用户从界面输入）
     */
    fun updateGitHubToken(token: String) {
        _githubToken.value = token
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

    fun onFileSelected(uri: Uri) {
        _selectedFileUri.value = uri
        _backupStatus.value = BackupStatus.FileSelected(uri.toString())
    }

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
        _backupStatus.value = BackupStatus.Loading(repository.getRepositoryName())
        viewModelScope.launch {
            val result = repository.backupFile(getApplication(), uri)
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

    private val localDocumentManager = LocalDocumentManager()
    private val sciFormatter = SCIDocumentFormatter()

    private val _formatConfig = MutableStateFlow(SCIDocumentFormatter.FormatConfig())
    val formatConfig: StateFlow<SCIDocumentFormatter.FormatConfig> = _formatConfig

    fun updateFormatConfig(config: SCIDocumentFormatter.FormatConfig) {
        _formatConfig.value = config
        sciFormatter.setConfig(config)
    }

    fun needsAllFilesPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()
    }

    fun getManageStorageIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    fun initAndFormatLocalDocuments() {
        viewModelScope.launch {
            _backupStatus.value = BackupStatus.Loading("SCI论文格式化")
            sciFormatter.setConfig(_formatConfig.value)
            val result = withContext(Dispatchers.IO) {
                try {
                    val dirCreated = localDocumentManager.ensureBackupDirectoryExists()
                    if (!dirCreated) {
                        return@withContext Result.failure<String>(
                            Exception("无法创建目录 ${localDocumentManager.getBackupDirPath()}，请检查存储权限")
                        )
                    }
                    val wordFiles = localDocumentManager.scanWordFiles()
                    if (wordFiles.isEmpty()) {
                        return@withContext Result.success(
                            "目录已准备就绪：${localDocumentManager.getBackupDirPath()}\n" +
                            "未找到 Word 文件（.docx / .doc），请将文件放入该目录后重试"
                        )
                    }
                    val results = mutableListOf<String>()
                    results.add("找到 ${wordFiles.size} 个 Word 文件，开始 SCI 格式化...")
                    for (file in wordFiles) {
                        val formatResult = sciFormatter.formatDocument(file)
                        results.add(
                            if (formatResult.isSuccess) formatResult.getOrNull()!!
                            else "❌ ${file.name}: ${formatResult.exceptionOrNull()?.message}"
                        )
                    }
                    Result.success(results.joinToString("\n"))
                } catch (e: Exception) {
                    Result.failure<String>(e)
                }
            }
            _backupStatus.value = if (result.isSuccess) {
                BackupStatus.Success(result.getOrNull() ?: "格式化完成")
            } else {
                BackupStatus.Error(result.exceptionOrNull()?.message ?: "格式化失败")
            }
        }
    }

    fun getLocalBackupPath(): String = localDocumentManager.getBackupDirPath()
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
