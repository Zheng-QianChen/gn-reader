package com.gnreader.app.ui.common

import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged

/**
 * 使用系统 EditText，避免 Compose OutlinedTextField 与中文输入法
 * 「自动匹配括号」冲突（右括号被吞、光标左移）。
 */
@Composable
fun ImeSafeEditText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: String = "",
) {
    // 仅在首次进入时写入初始值，之后完全由 EditText/IME 驱动，避免重组打断组字
    val initial = remember { value }
    AndroidView(
        factory = { ctx ->
            EditText(ctx).apply {
                setText(initial)
                setSelection(text.length)
                isSingleLine = singleLine
                maxLines = if (singleLine) 1 else 6
                hint = placeholder
                doAfterTextChanged { editable ->
                    onValueChange(editable?.toString() ?: "")
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
