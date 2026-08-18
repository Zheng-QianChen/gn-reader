package com.gnreader.app.data

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gn_reader_settings", Context.MODE_PRIVATE)

    var libraryRoot: String
        get() = prefs.getString(KEY_LIBRARY, defaultLibrary()) ?: defaultLibrary()
        set(v) = prefs.edit().putString(KEY_LIBRARY, v).apply()

    /** 文件浏览器上次停留的本地目录，用于返回时恢复。 */
    var lastBrowsePath: String
        get() = prefs.getString(KEY_LAST_BROWSE, libraryRoot) ?: libraryRoot
        set(v) = prefs.edit().putString(KEY_LAST_BROWSE, v).apply()

    var customEncodings: List<String>
        get() {
            val raw = prefs.getString(KEY_ENCODINGS, "[]") ?: "[]"
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(it) }
            prefs.edit().putString(KEY_ENCODINGS, arr.toString()).apply()
        }

    var defaultEncoding: String
        get() = prefs.getString(KEY_DEFAULT_ENC, "UTF-8") ?: "UTF-8"
        set(v) = prefs.edit().putString(KEY_DEFAULT_ENC, v).apply()

    var pageMode: String
        get() = prefs.getString(KEY_PAGE_MODE, "page") ?: "page" // scroll | page
        set(v) = prefs.edit().putString(KEY_PAGE_MODE, v).apply()

    /** 朗读范围：chapter 整章 | paragraph 逐段 | page 当前页 */
    var ttsMode: String
        get() = prefs.getString(KEY_TTS_MODE, "paragraph") ?: "paragraph"
        set(v) = prefs.edit().putString(KEY_TTS_MODE, v).apply()

    /** 是否在阅读器中显示段评徽章（格式只提供数据，显示由阅读器决定） */
    var showParagraphComments: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PARA_COMMENTS, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_PARA_COMMENTS, v).apply()

    /** 是否显示章末评论入口 */
    var showChapterComments: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CH_COMMENTS, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_CH_COMMENTS, v).apply()

    var fontSizeSp: Float
        get() = prefs.getFloat(KEY_FONT, 18f)
        set(v) = prefs.edit().putFloat(KEY_FONT, v).apply()

    var themeId: String
        get() = prefs.getString(KEY_THEME, "paper") ?: "paper"
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    /** TTS 语速 0.4~2.5，默认 1.0 */
    var ttsSpeechRate: Float
        get() = prefs.getFloat(KEY_TTS_RATE, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_TTS_RATE, v).apply()

    /** TTS 语调 0.5~2.0，默认 1.0 */
    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_TTS_PITCH, v).apply()

    /** 兼容旧键：曾用左右/上下成对边距 */
    var pageMarginH: Int
        get() = pageMarginLeft
        set(v) {
            pageMarginLeft = v
            pageMarginRight = v
        }

    var pageMarginV: Int
        get() = pageMarginTop
        set(v) {
            pageMarginTop = v
            pageMarginBottom = v
        }

    /** 左边距（dp） */
    var pageMarginLeft: Int
        get() = prefs.getInt(KEY_MARGIN_L, prefs.getInt(KEY_MARGIN_H, 18))
        set(v) = prefs.edit().putInt(KEY_MARGIN_L, v.coerceIn(4, 56)).apply()

    /** 右边距（dp） */
    var pageMarginRight: Int
        get() = prefs.getInt(KEY_MARGIN_R, prefs.getInt(KEY_MARGIN_H, 18))
        set(v) = prefs.edit().putInt(KEY_MARGIN_R, v.coerceIn(4, 56)).apply()

    /** 上边距（dp） */
    var pageMarginTop: Int
        get() = prefs.getInt(KEY_MARGIN_T, prefs.getInt(KEY_MARGIN_V, 10))
        set(v) = prefs.edit().putInt(KEY_MARGIN_T, v.coerceIn(0, 48)).apply()

    /** 下边距（dp） */
    var pageMarginBottom: Int
        get() = prefs.getInt(KEY_MARGIN_B, prefs.getInt(KEY_MARGIN_V, 10))
        set(v) = prefs.edit().putInt(KEY_MARGIN_B, v.coerceIn(0, 48)).apply()

    /** 朗读高亮：color 变色 | marker 荧光笔 */
    var ttsHighlightMode: String
        get() = prefs.getString(KEY_TTS_HL_MODE, "color") ?: "color"
        set(v) = prefs.edit().putString(KEY_TTS_HL_MODE, v).apply()

    /** 朗读高亮颜色 #RRGGBB */
    var ttsHighlightColor: String
        get() = prefs.getString(KEY_TTS_HL_COLOR, "#1565C0") ?: "#1565C0"
        set(v) = prefs.edit().putString(KEY_TTS_HL_COLOR, v).apply()

    /** 行距倍数 1.4~2.4 */
    var lineHeightMult: Float
        get() = prefs.getFloat(KEY_LINE_H, 1.85f)
        set(v) = prefs.edit().putFloat(KEY_LINE_H, v.coerceIn(1.4f, 2.4f)).apply()

    /** 阅读时是否显示系统通知栏 */
    var showStatusBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_STATUS, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_STATUS, v).apply()

    /** 阅读时是否显示系统导航栏（三键/手势条） */
    var showNavigationBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NAV, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_NAV, v).apply()

    /** 正文字体：default | serif | sans | mono */
    var fontFamily: String
        get() = prefs.getString(KEY_FONT_FAMILY, "default") ?: "default"
        set(v) = prefs.edit().putString(KEY_FONT_FAMILY, v).apply()

    /** 自定义文字色 #RRGGBB，空=跟随主题 */
    var customTextColor: String
        get() = prefs.getString(KEY_TEXT_COLOR, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TEXT_COLOR, v).apply()

    /** 自定义背景色 #RRGGBB，空=跟随主题 */
    var customBgColor: String
        get() = prefs.getString(KEY_BG_COLOR, "") ?: ""
        set(v) = prefs.edit().putString(KEY_BG_COLOR, v).apply()

    /** 背景图绝对路径，空=无 */
    var bgImagePath: String
        get() = prefs.getString(KEY_BG_IMAGE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_BG_IMAGE, v).apply()

    /** 背景图不透明度 0~1（底色仍可见） */
    var bgImageAlpha: Float
        get() = prefs.getFloat(KEY_BG_ALPHA, 0.35f)
        set(v) = prefs.edit().putFloat(KEY_BG_ALPHA, v.coerceIn(0f, 1f)).apply()

    /** 段评样式：inline | bubble | number */
    var paraCommentStyle: String
        get() = prefs.getString(KEY_PARA_STYLE, "bubble") ?: "bubble"
        set(v) = prefs.edit().putString(KEY_PARA_STYLE, v).apply()

    /** 评论列表样式：comfortable | compact */
    var commentListStyle: String
        get() = prefs.getString(KEY_COMMENT_STYLE, "comfortable") ?: "comfortable"
        set(v) = prefs.edit().putString(KEY_COMMENT_STYLE, v).apply()

    /** 未呼出菜单时是否显示底部信息条 */
    var showInfoBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_INFO_BAR, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_INFO_BAR, v).apply()

    /** 文件浏览器排序：name | mtime | size | type */
    var browseSortBy: String
        get() = prefs.getString(KEY_BROWSE_SORT, "name") ?: "name"
        set(v) = prefs.edit().putString(KEY_BROWSE_SORT, v).apply()

    var browseSortAsc: Boolean
        get() = prefs.getBoolean(KEY_BROWSE_ASC, true)
        set(v) = prefs.edit().putBoolean(KEY_BROWSE_ASC, v).apply()

    /** 外文字体配置 id（fonts.json latin） */
    var latinFontFamily: String
        get() = prefs.getString(KEY_LATIN_FONT, "latin-default") ?: "latin-default"
        set(v) = prefs.edit().putString(KEY_LATIN_FONT, v).apply()

    var webdavUrl: String
        get() = prefs.getString(KEY_DAV_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DAV_URL, v).apply()

    var webdavUser: String
        get() = prefs.getString(KEY_DAV_USER, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DAV_USER, v).apply()

    var webdavPass: String
        get() = prefs.getString(KEY_DAV_PASS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DAV_PASS, v).apply()

    /** 本地↔云端文件夹映射 JSON 数组 */
    var syncPairsJson: String
        get() = prefs.getString(KEY_SYNC_PAIRS, "[]") ?: "[]"
        set(v) = prefs.edit().putString(KEY_SYNC_PAIRS, v).apply()

    var syncExcludeRules: String
        get() = prefs.getString(KEY_SYNC_EXCLUDE, "tmp/\n*.log\n*.auth\n.lofter_auth") ?: "tmp/\n*.log\n*.auth\n.lofter_auth"
        set(v) = prefs.edit().putString(KEY_SYNC_EXCLUDE, v).apply()

    /** 是否启用阅读页实体键映射 */
    var hardwareKeysEnabled: Boolean
        get() = prefs.getBoolean(KEY_HW_KEYS, true)
        set(v) = prefs.edit().putBoolean(KEY_HW_KEYS, v).apply()

    /**
     * 按键映射 JSON：`{"24":"prev_page","25":"next_page"}`（keyCode → ReaderKeyAction.id）
     * 缺省：音量+上一页、音量-下一页。
     */
    var keyMappingsJson: String
        get() {
            val raw = prefs.getString(KEY_KEY_MAP, null)
            if (raw.isNullOrBlank()) return defaultKeyMappingsJson()
            return raw
        }
        set(v) = prefs.edit().putString(KEY_KEY_MAP, v).apply()

    /** 短时连按切换触控屏蔽 */
    var touchBlockGestureEnabled: Boolean
        get() = prefs.getBoolean(KEY_TB_GESTURE, true)
        set(v) = prefs.edit().putBoolean(KEY_TB_GESTURE, v).apply()

    /** 触控屏蔽手势键 keyCode，默认音量- */
    var touchBlockGestureKeyCode: Int
        get() = prefs.getInt(KEY_TB_KEY, KeyEvent.KEYCODE_VOLUME_DOWN)
        set(v) = prefs.edit().putInt(KEY_TB_KEY, v).apply()

    /** 连按次数 2~8，默认 3 */
    var touchBlockGestureCount: Int
        get() = prefs.getInt(KEY_TB_COUNT, 3).coerceIn(2, 8)
        set(v) = prefs.edit().putInt(KEY_TB_COUNT, v.coerceIn(2, 8)).apply()

    /** 连按判定窗口毫秒 */
    var touchBlockGestureWindowMs: Int
        get() = prefs.getInt(KEY_TB_WINDOW, 900).coerceIn(300, 3000)
        set(v) = prefs.edit().putInt(KEY_TB_WINDOW, v.coerceIn(300, 3000)).apply()

    /** 最近一次在阅读器/捕获中检测到的实体键（便于发现侧键 keyCode） */
    var lastDetectedKeyCode: Int
        get() = prefs.getInt(KEY_LAST_KEY, -1)
        set(v) = prefs.edit().putInt(KEY_LAST_KEY, v).apply()

    fun keyMappings(): Map<Int, ReaderKeyAction> {
        return try {
            val obj = JSONObject(keyMappingsJson)
            val out = linkedMapOf<Int, ReaderKeyAction>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val code = k.toIntOrNull() ?: continue
                out[code] = ReaderKeyAction.fromId(obj.optString(k))
            }
            out
        } catch (_: Exception) {
            defaultKeyMappings()
        }
    }

    fun setKeyMappings(map: Map<Int, ReaderKeyAction>) {
        val obj = JSONObject()
        map.entries.sortedBy { it.key }.forEach { (code, action) ->
            if (action != ReaderKeyAction.NONE) {
                obj.put(code.toString(), action.id)
            }
        }
        keyMappingsJson = obj.toString()
    }

    fun keyActionFor(keyCode: Int): ReaderKeyAction =
        keyMappings()[keyCode] ?: ReaderKeyAction.NONE

    fun setKeyAction(keyCode: Int, action: ReaderKeyAction) {
        val map = keyMappings().toMutableMap()
        if (action == ReaderKeyAction.NONE) map.remove(keyCode) else map[keyCode] = action
        setKeyMappings(map)
    }

    fun rememberLastKeyCode(keyCode: Int) {
        if (keyCode > 0) lastDetectedKeyCode = keyCode
    }

    fun syncPairs(): List<com.gnreader.app.data.webdav.SyncPair> {
        return try {
            val arr = org.json.JSONArray(syncPairsJson)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val local = o.optString("local").trim()
                val remote = o.optString("remote").trim()
                if (local.isEmpty() || remote.isEmpty()) return@mapNotNull null
                com.gnreader.app.data.webdav.SyncPair(
                    id = o.optString("id").ifBlank { "pair_${i + 1}" },
                    name = o.optString("name").ifBlank { java.io.File(local).name },
                    local = local,
                    remote = if (remote.startsWith("/")) remote else "/$remote",
                    enabled = o.optBoolean("enabled", true),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setSyncPairs(pairs: List<com.gnreader.app.data.webdav.SyncPair>) {
        val arr = org.json.JSONArray()
        pairs.forEach { p ->
            arr.put(
                org.json.JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("local", p.local)
                    .put("remote", p.remote)
                    .put("enabled", p.enabled),
            )
        }
        syncPairsJson = arr.toString()
    }

    /** 无映射时回退：书库根 ↔ /get_norvels */
    fun effectiveSyncPairs(): List<com.gnreader.app.data.webdav.SyncPair> {
        val configured = syncPairs().filter { it.enabled }
        if (configured.isNotEmpty()) return configured
        return listOf(
            com.gnreader.app.data.webdav.SyncPair(
                id = "default",
                name = "书库",
                local = libraryRoot,
                remote = "/get_norvels",
                enabled = true,
            ),
        )
    }

    fun allEncodings(): List<String> = EncodingCatalog.merge(customEncodings)

    fun addEncoding(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        customEncodings = (customEncodings + n).distinct()
    }

    private fun defaultLibrary(): String {
        val docs = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOCUMENTS
        )
        return java.io.File(docs, "get_norvels").absolutePath
    }

    companion object {
        private const val KEY_LIBRARY = "libraryRoot"
        private const val KEY_LAST_BROWSE = "lastBrowsePath"
        private const val KEY_ENCODINGS = "customEncodings"
        private const val KEY_DEFAULT_ENC = "defaultEncoding"
        private const val KEY_PAGE_MODE = "pageMode"
        private const val KEY_TTS_MODE = "ttsMode"
        private const val KEY_SHOW_PARA_COMMENTS = "showParagraphComments"
        private const val KEY_SHOW_CH_COMMENTS = "showChapterComments"
        private const val KEY_FONT = "fontSizeSp"
        private const val KEY_THEME = "themeId"
        private const val KEY_TTS_RATE = "ttsSpeechRate"
        private const val KEY_TTS_PITCH = "ttsPitch"
        private const val KEY_MARGIN_H = "pageMarginH"
        private const val KEY_MARGIN_V = "pageMarginV"
        private const val KEY_MARGIN_L = "pageMarginLeft"
        private const val KEY_MARGIN_R = "pageMarginRight"
        private const val KEY_MARGIN_T = "pageMarginTop"
        private const val KEY_MARGIN_B = "pageMarginBottom"
        private const val KEY_TTS_HL_MODE = "ttsHighlightMode"
        private const val KEY_TTS_HL_COLOR = "ttsHighlightColor"
        private const val KEY_LINE_H = "lineHeightMult"
        private const val KEY_SHOW_STATUS = "showStatusBar"
        private const val KEY_SHOW_NAV = "showNavigationBar"
        private const val KEY_FONT_FAMILY = "fontFamily"
        private const val KEY_TEXT_COLOR = "customTextColor"
        private const val KEY_BG_COLOR = "customBgColor"
        private const val KEY_BG_IMAGE = "bgImagePath"
        private const val KEY_BG_ALPHA = "bgImageAlpha"
        private const val KEY_PARA_STYLE = "paraCommentStyle"
        private const val KEY_COMMENT_STYLE = "commentListStyle"
        private const val KEY_SHOW_INFO_BAR = "showInfoBar"
        private const val KEY_BROWSE_SORT = "browseSortBy"
        private const val KEY_BROWSE_ASC = "browseSortAsc"
        private const val KEY_LATIN_FONT = "latinFontFamily"
        private const val KEY_DAV_URL = "webdavUrl"
        private const val KEY_DAV_USER = "webdavUser"
        private const val KEY_DAV_PASS = "webdavPass"
        private const val KEY_SYNC_PAIRS = "syncPairsJson"
        private const val KEY_SYNC_EXCLUDE = "syncExcludeRules"
        private const val KEY_HW_KEYS = "hardwareKeysEnabled"
        private const val KEY_KEY_MAP = "keyMappingsJson"
        private const val KEY_TB_GESTURE = "touchBlockGestureEnabled"
        private const val KEY_TB_KEY = "touchBlockGestureKeyCode"
        private const val KEY_TB_COUNT = "touchBlockGestureCount"
        private const val KEY_TB_WINDOW = "touchBlockGestureWindowMs"
        private const val KEY_LAST_KEY = "lastDetectedKeyCode"

        fun defaultKeyMappings(): Map<Int, ReaderKeyAction> = linkedMapOf(
            KeyEvent.KEYCODE_VOLUME_UP to ReaderKeyAction.PREV_PAGE,
            KeyEvent.KEYCODE_VOLUME_DOWN to ReaderKeyAction.NEXT_PAGE,
        )

        fun defaultKeyMappingsJson(): String {
            val obj = JSONObject()
            defaultKeyMappings().forEach { (code, action) ->
                obj.put(code.toString(), action.id)
            }
            return obj.toString()
        }
    }
}
