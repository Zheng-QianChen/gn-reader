package com.gnreader.app.reader

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.ReaderKeyAction
import com.gnreader.app.data.ReaderKeyLabels

/**
 * 实体键映射 + 短时连按切换触控屏蔽。
 *
 * 手势键策略：第 1 次立即执行映射动作；窗口内第 2..N-1 次只计数不翻页；
 * 达到 N 次则切换触控屏蔽（避免连按解锁时连翻多页）。
 */
class ReaderHardwareKeys(
    private val settings: AppSettings,
    private val onAction: (ReaderKeyAction) -> Unit,
    private val onTouchBlockChanged: (Boolean) -> Unit,
    private val onStatus: (String) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private val gestureTimes = ArrayDeque<Long>()
    private var clearGestureBuffer: Runnable? = null
    private var touchBlocked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            onTouchBlockChanged(value)
            onStatus(
                if (value) {
                    "触控已屏蔽 · ${settings.touchBlockGestureCount} 连按「${
                        ReaderKeyLabels.nameOf(settings.touchBlockGestureKeyCode)
                    }」解除"
                } else {
                    "触控已恢复"
                },
            )
        }

    fun isTouchBlocked(): Boolean = touchBlocked

    fun applyTouchBlocked(blocked: Boolean) {
        touchBlocked = blocked
    }

    fun toggleTouchBlock() {
        touchBlocked = !touchBlocked
    }

    /** @return true 表示已消费，不再交给系统（避免调音量等） */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!settings.hardwareKeysEnabled) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return false

        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_BACK) return false

        settings.rememberLastKeyCode(keyCode)

        val gestureKey = settings.touchBlockGestureKeyCode
        val gestureOn = settings.touchBlockGestureEnabled && gestureKey > 0

        if (gestureOn && keyCode == gestureKey) {
            return handleGestureKey(keyCode)
        }

        return dispatchMapped(keyCode)
    }

    private fun handleGestureKey(keyCode: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        val window = settings.touchBlockGestureWindowMs.toLong().coerceIn(300L, 3000L)
        val need = settings.touchBlockGestureCount.coerceIn(2, 8)

        while (gestureTimes.isNotEmpty() && now - gestureTimes.first() > window) {
            gestureTimes.removeFirst()
        }
        gestureTimes.addLast(now)

        clearGestureBuffer?.let { main.removeCallbacks(it) }
        val clear = Runnable {
            gestureTimes.clear()
            clearGestureBuffer = null
        }
        clearGestureBuffer = clear
        main.postDelayed(clear, window)

        if (gestureTimes.size >= need) {
            gestureTimes.clear()
            clearGestureBuffer?.let { main.removeCallbacks(it) }
            clearGestureBuffer = null
            toggleTouchBlock()
            return true
        }

        // 仅第一次立即执行映射；窗口内后续按键只用于凑连按
        if (gestureTimes.size == 1) {
            fireMappedAction(keyCode)
        }
        return true
    }

    private fun fireMappedAction(keyCode: Int) {
        val action = settings.keyActionFor(keyCode)
        when (action) {
            ReaderKeyAction.NONE -> Unit
            ReaderKeyAction.TOGGLE_TOUCH_BLOCK -> toggleTouchBlock()
            else -> onAction(action)
        }
    }

    private fun dispatchMapped(keyCode: Int): Boolean {
        val action = settings.keyActionFor(keyCode)
        if (action == ReaderKeyAction.NONE) return false
        if (action == ReaderKeyAction.TOGGLE_TOUCH_BLOCK) {
            toggleTouchBlock()
            return true
        }
        onAction(action)
        return true
    }

    fun dispose() {
        clearGestureBuffer?.let { main.removeCallbacks(it) }
        clearGestureBuffer = null
        gestureTimes.clear()
    }
}
