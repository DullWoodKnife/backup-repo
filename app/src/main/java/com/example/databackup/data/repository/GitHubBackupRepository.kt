package com.example.databackup.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitHubBackupRepository(
    private val owner: String,
    private val repo: String,
    private val token: String,
    private val branch: String = "main"
) : BackupRepository {

    override fun getRepositoryName(): String = "GitHub"

    override suspend fun backupFile(context: Context, fileUri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (token.isBlank()) {
                    return@withContext Result.failure(IOException("GitHub Token 为空，请配置有效的 Personal Access Token"))
                }
                val resolver = context.contentResolver
                val inputStream = resolver.openInputStream(fileUri)
                    ?: return@withContext Result.failure(IOException("无法打开文件流"))
                val fileName = getFileName(resolver, fileUri)
                val bytes = inputStream.readBytes()
                inputStream.close()
                uploadBytes(fileName, bytes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 直接上传 File 对象（用于格式化后的文件上传）
     */
    suspend fun uploadFile(file: File): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (token.isBlank()) {
                    return@withContext Result.failure(IOException("GitHub Token 为空"))
                }
                if (!file.exists()) {
                    return@withContext Result.failure(IOException("文件不存在: ${file.name}"))
                }
                val bytes = file.readBytes()
                uploadBytes(file.name, bytes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * 核心上传逻辑：将字节数组上传到 GitHub
     */
    private fun uploadBytes(fileName: String, bytes: ByteArray): Result<String> {
        if (bytes.size > 100 * 1024 * 1024) {
            return Result.failure(
                IOException("GitHub 单文件限制 100MB，当前文件 ${bytes.size / 1024 / 1024}MB")
            )
        }
        val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val path = "backups/$fileName"
        val url = URL("https://api.github.com/repos/$owner/$repo/contents/$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Authorization", "token $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 60000

        // 自动生成详细的 commit message
        val sizeKB = bytes.size / 1024
        val sizeStr = if (sizeKB < 1024) "${sizeKB}KB" else "${sizeKB / 1024}MB"
        val timestamp = createTimestamp()
        val commitMessage = buildString {
            append("[DataBackup] Upload $fileName")
            append("\n\n")
            append("- File: $fileName")
            append("\n")
            append("- Size: $sizeStr")
            append("\n")
            append("- Time: $timestamp")
            append("\n")
            append("- Device: Android via DataBackup App")
        }

        val jsonBody = JSONObject().apply {
            put("message", commitMessage)
            put("content", base64Content)
            put("branch", branch)
        }
        connection.outputStream.use { os ->
            os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
        }
        val responseCode = connection.responseCode
        val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return if (responseCode in 200..299) {
            val json = JSONObject(responseMessage)
            val htmlUrl = json.optJSONObject("content")?.optString("html_url", "")
            Result.success("[$fileName] 已上传至 GitHub\n路径: $path\n链接: $htmlUrl")
        } else {
            Result.failure(IOException("GitHub API 错误 [$responseCode]: $responseMessage"))
        }
    }

    private fun getFileName(resolver: android.content.ContentResolver, uri: Uri): String {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment ?: "unknown_file"
    }

    private fun createTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
}
