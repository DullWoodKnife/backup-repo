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
import com.example.databackup.data.local.DocConverter
import com.example.databackup.data.local.SCIDocumentFormatter
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

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus

    // GitHub Token 状态（可由 UI 输入更新）
    private val _githubToken = MutableStateFlow("")
    val githubToken: StateFlow<String> = _githubToken

    // 加密存储
    private val tokenStorage = SecureTokenStorage(getApplication())

    init {
        // 启动时从加密存储读取 Token
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
        _backupStatus.value = BackupStatus.Loading("${repository.getRepositoryName()} · 预处理")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val resolver = context.contentResolver
                val fileName = getFileNameFromUri(uri)

                // 判断是否为 Word 文件，是的话先格式化再上传
                val isWordFile = fileName.lowercase().endsWith(".docx") ||
                                 fileName.lowercase().endsWith(".doc")

                if (isWordFile) {
                    try {
                        // 将 URI 文件复制到应用缓存目录
                        val inputStream = resolver.openInputStream(uri)
                            ?: return@withContext Result.failure<String>(Exception("无法打开文件流"))
                        val cacheDir = context.cacheDir
                        val tempFile = File(cacheDir, "sci_format_${System.currentTimeMillis()}_$fileName")
                        inputStream.use { input ->
                            tempFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        // 根据文件格式选择格式化策略
                        val fileToUpload: File
                        val formatMsg: String

                        if (fileName.lowercase().endsWith(".doc")) {
                            // .doc 旧格式：先转换为 .docx，再格式化
                            val converter = DocConverter()
                            val convertResult = converter.convertAndFormat(tempFile, _formatConfig.value)
                            if (convertResult.isFailure) {
                                tempFile.delete()
                                return@withContext Result.failure<String>(
                                    Exception(".doc 格式化失败: ${convertResult.exceptionOrNull()?.message}")
                                )
                            }
                            fileToUpload = convertResult.getOrNull()!!
                            tempFile.delete() // 删除原始 .doc 临时文件
                            formatMsg = ".doc 已转换为 .docx 并按 SCI 标准排版"
                        } else {
                            // .docx 格式：直接格式化
                            sciFormatter.setConfig(_formatConfig.value)
                            val formatResult = sciFormatter.formatDocument(tempFile)
                            if (formatResult.isFailure) {
                                tempFile.delete()
                                return@withContext Result.failure<String>(
                                    Exception("SCI 格式化失败: ${formatResult.exceptionOrNull()?.message}")
                                )
                            }
                            fileToUpload = tempFile
                            formatMsg = formatResult.getOrNull() ?: "格式化完成"
                        }

                        // 上传格式化后的文件
                        if (repository is GitHubBackupRepository) {
                            val uploadResult = repository.uploadFile(fileToUpload)
                            // 清理缓存文件
                            fileToUpload.delete()
                            if (uploadResult.isSuccess) {
                                val uploadMsg = uploadResult.getOrNull() ?: "上传完成"
                                Result.success("$uploadMsg\n\n[SCI 格式化] $formatMsg")
                            } else {
                                uploadResult
                            }
                        } else {
                            val uploadResult = repository.backupFile(context, Uri.fromFile(fileToUpload))
                            fileToUpload.delete()
                            uploadResult
                        }
                    } catch (e: Exception) {
                        Result.failure<String>(e)
                    }
                } else {
                    // 非 Word 文件，直接上传
                    repository.backupFile(context, uri)
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
