package com.example.databackup.data.local

import android.os.Environment
import java.io.File

class LocalDocumentManager {

    companion object {
        const val BACKUP_DIR_NAME = "backuptool"
        val WORD_EXTENSIONS = arrayOf("docx", "doc")
    }

    fun getBackupDirectory(): File {
        val sdcard = Environment.getExternalStorageDirectory()
        return File(sdcard, BACKUP_DIR_NAME)
    }

    fun ensureBackupDirectoryExists(): Boolean {
        val dir = getBackupDirectory()
        return if (dir.exists()) dir.isDirectory else dir.mkdirs()
    }

    fun scanWordFiles(): List<File> {
        val dir = getBackupDirectory()
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles { file ->
            file.isFile && WORD_EXTENSIONS.any { ext ->
                file.name.lowercase().endsWith(".$ext")
            }
        }?.toList() ?: emptyList()
    }

    fun getBackupDirPath(): String = getBackupDirectory().absolutePath
}
