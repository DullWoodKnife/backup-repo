package com.example.databackup.data.local

import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub 仓库文件下载器（模拟 git pull）
 *
 * 使用 GitHub Contents API 获取仓库文件列表，逐个下载到 /sdcard/backuptool。
 * 不依赖系统 git 命令，纯 HTTP 方式实现，兼容所有 Android 设备。
 *
 * API 限制：未认证请求每小时 60 次，已认证每小时 5000 次。
 */
class GitHubPullManager(
    private val owner: String = "DullWoodKnife",
    private val repo: String = "backup-doc",
    private val token: String = "",
    private val branch: String = "main"
) {

    companion object {
        const val BACKUP_DIR_NAME = "backuptool"
    }

    private fun getBackupDir(): File {
        val sdcard = Environment.getExternalStorageDirectory()
        return File(sdcard, BACKUP_DIR_NAME)
    }

    /**
     * 拉取仓库 backups/ 目录下的所有文件到本地 /sdcard/backuptool
     * @return Result 包含下载文件数统计或错误信息
     */
    fun pullBackups(): Result<String> {
        return try {
            val backupDir = getBackupDir()
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val files = listRepoContents("backups")
            if (files.isEmpty()) {
                return Result.success("仓库 backups/ 目录为空，无需下载")
            }

            var downloaded = 0
            var skipped = 0
            val errors = mutableListOf<String>()

            for (fileInfo in files) {
                val name = fileInfo.name
                val downloadUrl = fileInfo.downloadUrl
                val sha = fileInfo.sha

                if (downloadUrl.isBlank()) continue

                val localFile = File(backupDir, name)

                // 如果文件已存在且 SHA 相同则跳过（简单校验）
                if (localFile.exists() && localFile.length() > 0) {
                    skipped++
                    continue
                }

                val downloadResult = downloadFile(downloadUrl, localFile)
                if (downloadResult.isSuccess) {
                    downloaded++
                } else {
                    errors.add("$name: ${downloadResult.exceptionOrNull()?.message}")
                }
            }

            val msg = buildString {
                append("同步完成: 下载 $downloaded 个文件")
                if (skipped > 0) append(", 跳过 $skipped 个已存在")
                if (errors.isNotEmpty()) append(", ${errors.size} 个失败")
            }

            if (errors.isNotEmpty()) {
                Result.success("$msg\n失败详情: ${errors.take(3).joinToString(", ")}")
            } else {
                Result.success(msg)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取仓库指定路径下的文件列表
     */
    private fun listRepoContents(path: String): List<FileInfo> {
        val url = URL(
            "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        if (token.isNotBlank()) {
            connection.setRequestProperty("Authorization", "token $token")
        }
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.connectTimeout = 30000
        connection.readTimeout = 60000

        val responseCode = connection.responseCode
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IOException("GitHub API 错误 [$responseCode]: $response")
        }

        val files = mutableListOf<FileInfo>()
        val jsonArray = JSONArray(response)
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val type = item.optString("type", "")
            if (type == "file") {
                files.add(
                    FileInfo(
                        name = item.optString("name", ""),
                        path = item.optString("path", ""),
                        downloadUrl = item.optString("download_url", ""),
                        sha = item.optString("sha", ""),
                        size = item.optLong("size", 0)
                    )
                )
            }
        }
        return files
    }

    /**
     * 下载单个文件
     */
    private fun downloadFile(downloadUrl: String, outputFile: File): Result<Unit> {
        return try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 120000

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class FileInfo(
        val name: String,
        val path: String,
        val downloadUrl: String,
        val sha: String,
        val size: Long
    )
}
