package com.gnreader.app.data

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class FontOption(
    val id: String,
    val label: String,
    val kind: String, // asset | download | system
)

/**
 * 内置免费字体（OFL / Liberation）：
 * - 霞鹜文楷 Lite、得意黑、Liberation Serif
 * 首次使用可下载 Noto Serif SC（新宋，OFL）。
 */
object ReaderFonts {
    const val NOTO_SC_URL =
        "https://github.com/googlefonts/noto-cjk/raw/main/Serif/Variable/TTF/Subset/NotoSerifSC-VF.ttf"
    const val NOTO_SC_FILE = "NotoSerifSC-VF.ttf"
    const val LIBERATION_ASSET = "fonts/LiberationSerif-Regular.ttf"
    const val WENKAI_ASSET = "fonts/LXGWWenKaiLite-Regular.ttf"
    const val SMILEY_ASSET = "fonts/SmileySans-Oblique.ttf"

    fun options(): List<FontOption> = listOf(
        FontOption("default", "系统默认", "system"),
        FontOption("wenkai", "霞鹜文楷（内置）", "asset"),
        FontOption("xin_song", "新宋（公文风格）", "download"),
        FontOption("times", "Times / Liberation", "asset"),
        FontOption("smiley", "得意黑（斜体）", "asset"),
        FontOption("serif", "系统衬线", "system"),
        FontOption("sans", "系统无衬线", "system"),
        FontOption("mono", "等宽", "system"),
    )

    fun notoFile(context: Context): File = File(context.filesDir, "fonts/$NOTO_SC_FILE")

    fun isNotoReady(context: Context): Boolean {
        val f = notoFile(context)
        return f.isFile && f.length() > 1_000_000
    }

    suspend fun ensureNotoSc(context: Context, onProgress: (String) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            val dest = notoFile(context)
            if (dest.isFile && dest.length() > 1_000_000) return@withContext dest
            dest.parentFile?.mkdirs()
            onProgress("正在下载新宋字体…")
            val tmp = File(dest.absolutePath + ".part")
            val conn = (URL(NOTO_SC_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 180_000
                instanceFollowRedirects = true
            }
            try {
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                onProgress("字体已就绪")
                dest
            } catch (e: Exception) {
                tmp.delete()
                throw e
            } finally {
                conn.disconnect()
            }
        }

    private fun typefaceFromAsset(context: Context, asset: String): Typeface? =
        try {
            Typeface.createFromAsset(context.assets, asset)
        } catch (_: Exception) {
            null
        }

    /** 解析量宽/绘制用 Typeface；xin_song 未下载时先拉取。 */
    suspend fun resolveTypeface(
        context: Context,
        id: String,
        onProgress: (String) -> Unit = {},
    ): Typeface = withContext(Dispatchers.IO) {
        when (id) {
            "times" -> typefaceFromAsset(context, LIBERATION_ASSET) ?: Typeface.SERIF
            "wenkai", "kai" -> typefaceFromAsset(context, WENKAI_ASSET) ?: Typeface.SERIF
            "smiley" -> typefaceFromAsset(context, SMILEY_ASSET) ?: Typeface.SANS_SERIF
            "xin_song", "song" -> {
                try {
                    val f = ensureNotoSc(context, onProgress)
                    Typeface.createFromFile(f)
                } catch (_: Exception) {
                    Typeface.SERIF
                }
            }
            "serif", "fangsong" -> Typeface.SERIF
            "sans", "hei" -> Typeface.SANS_SERIF
            "mono" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
    }

    fun composeFamily(typeface: Typeface): FontFamily = FontFamily(typeface)

    fun paintTypeface(context: Context, id: String): Typeface {
        return when (id) {
            "times" -> typefaceFromAsset(context, LIBERATION_ASSET) ?: Typeface.SERIF
            "wenkai", "kai" -> typefaceFromAsset(context, WENKAI_ASSET) ?: Typeface.SERIF
            "smiley" -> typefaceFromAsset(context, SMILEY_ASSET) ?: Typeface.SANS_SERIF
            "xin_song", "song" -> {
                val f = notoFile(context)
                if (f.isFile && f.length() > 1_000_000) Typeface.createFromFile(f) else Typeface.SERIF
            }
            "serif", "fangsong" -> Typeface.SERIF
            "sans", "hei" -> Typeface.SANS_SERIF
            "mono" -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
    }
}
