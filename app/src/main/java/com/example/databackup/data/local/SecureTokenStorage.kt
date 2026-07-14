package com.example.databackup.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException

/**
 * GitHub Token 加密存储
 *
 * ============================================================
 * 原因：
 * - GitHub Token 等同于账户密码，明文存储在 SharedPreferences 中可被 root 设备直接读取
 * - EncryptedSharedPreferences 使用 Android KeyStore + AES-GCM 256 位加密，
 *   即使设备被 root，加密密钥也存储在硬件安全模块（StrongBox / TEE）中，无法直接提取
 * - 用户只需输入一次 Token，后续自动从加密文件读取，不再需要重复输入
 *
 * ============================================================
 * 风险说明：
 * 1. 首次启动延迟：Android KeyStore 初始化可能需要 100-500ms（仅首次）
 * 2. Android KeyStore 不可迁移：
 *    换设备或恢复出厂设置后加密数据会丢失，用户需要重新输入 Token
 *    （这是安全设计，不是 bug）
 * 3. MasterKey 回退：
 *    Android 6.0 以下不支持 AndroidKeyStore，EncryptedSharedPreferences 会回退到
 *    不安全的加密方式；当前 minSdk=26，不存在此问题
 * 4. root + 绕过风险：
 *    理论上 root 后可通过 hook 读取运行时内存中的 Token，
 *    但无法直接从持久化文件中读取
 */
class SecureTokenStorage(context: Context) {

    companion object {
        private const val PREFS_FILE_NAME = "secure_token_storage"
        private const val KEY_GITHUB_TOKEN = "encrypted_github_token"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 保存 Token（加密写入）
     */
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    /**
     * 读取 Token（解密读取）
     * @return 保存的 Token 或空字符串（首次使用时）
     */
    fun loadToken(): String {
        return prefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
    }

    /**
     * 是否已保存过 Token
     */
    fun hasToken(): Boolean {
        return prefs.contains(KEY_GITHUB_TOKEN) &&
               prefs.getString(KEY_GITHUB_TOKEN, "")?.isNotBlank() == true
    }

    /**
     * 清除已保存的 Token
     */
    fun clearToken() {
        prefs.edit().remove(KEY_GITHUB_TOKEN).apply()
    }
}
