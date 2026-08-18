package com.gnreader.app.data

import android.view.KeyEvent

/**
 * 实体键可绑定的阅读动作。
 * 任意系统派发到 Activity 的按键（音量、侧键、相机键等）均可映射。
 */
enum class ReaderKeyAction(val id: String, val label: String) {
    NONE("none", "无"),
    PREV_PAGE("prev_page", "上一页"),
    NEXT_PAGE("next_page", "下一页"),
    PREV_CHAPTER("prev_chapter", "上一章"),
    NEXT_CHAPTER("next_chapter", "下一章"),
    TOGGLE_CHROME("toggle_chrome", "显示/隐藏菜单"),
    TOGGLE_TTS("toggle_tts", "朗读开/关"),
    TOGGLE_TOUCH_BLOCK("toggle_touch_block", "触控屏蔽开/关"),
    ;

    companion object {
        fun fromId(id: String?): ReaderKeyAction =
            entries.firstOrNull { it.id == id } ?: NONE
    }
}

object ReaderKeyLabels {
    fun nameOf(keyCode: Int): String {
        val sys = KeyEvent.keyCodeToString(keyCode)
        val short = sys.removePrefix("KEYCODE_")
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> "音量+"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "音量-"
            KeyEvent.KEYCODE_CAMERA -> "相机键"
            KeyEvent.KEYCODE_FOCUS -> "对焦键"
            KeyEvent.KEYCODE_HEADSETHOOK -> "耳机线控"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "播放/暂停"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "下一曲"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "上一曲"
            KeyEvent.KEYCODE_BACK -> "返回键"
            KeyEvent.KEYCODE_MENU -> "菜单键"
            KeyEvent.KEYCODE_APP_SWITCH -> "多任务"
            else -> "$short ($keyCode)"
        }
    }
}
