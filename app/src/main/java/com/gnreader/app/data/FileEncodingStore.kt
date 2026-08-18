package com.gnreader.app.data

import android.content.Context
import java.security.MessageDigest

/**
 * 文本编码按「文件路径」持久化（每个 TXT 可不同），
 * 全局 [AppSettings.defaultEncoding] 仅作首次打开默认值。
 */
class FileEncodingStore(context: Context) {
    private val prefs = context.getSharedPreferences("gn_reader_file_encoding", Context.MODE_PRIVATE)

    fun get(filePath: String, fallback: String = "UTF-8"): String {
        return prefs.getString(key(filePath), null)?.takeIf { it.isNotBlank() } ?: fallback
    }

    fun set(filePath: String, encoding: String) {
        val enc = encoding.trim().ifBlank { return }
        prefs.edit().putString(key(filePath), enc).apply()
    }

    private fun key(filePath: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val dig = md.digest(filePath.toByteArray(Charsets.UTF_8))
        return "enc_" + dig.joinToString("") { "%02x".format(it) }
    }
}
