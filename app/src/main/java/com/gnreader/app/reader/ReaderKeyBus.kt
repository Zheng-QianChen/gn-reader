package com.gnreader.app.reader

import android.view.KeyEvent

/**
 * Activity ↔ 阅读页按键总线。
 * [captureListener] 用于设置页「按下以绑定」；[interceptor] 用于阅读中消费按键。
 */
object ReaderKeyBus {
    @Volatile
    var interceptor: ((KeyEvent) -> Boolean)? = null

    @Volatile
    var captureListener: ((KeyEvent) -> Unit)? = null
}
