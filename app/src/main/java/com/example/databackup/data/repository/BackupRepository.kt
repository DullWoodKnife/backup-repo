package com.example.databackup.data.repository

import android.content.Context
import android.net.Uri

interface BackupRepository {
    suspend fun backupFile(context: Context, fileUri: Uri): Result<String>
    fun getRepositoryName(): String
}
