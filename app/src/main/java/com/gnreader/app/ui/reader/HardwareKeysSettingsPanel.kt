package com.gnreader.app.ui.reader

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.ReaderKeyAction
import com.gnreader.app.data.ReaderKeyLabels
import com.gnreader.app.reader.ReaderKeyBus

/**
 * 实体键映射 + 触控屏蔽手势设置。
 * 「按下以绑定」会临时接管 [ReaderKeyBus.captureListener]，捕获手机全部实体键。
 */
@Composable
fun HardwareKeysSettingsPanel(settings: AppSettings) {
    var enabled by remember { mutableStateOf(settings.hardwareKeysEnabled) }
    var mappings by remember { mutableStateOf(settings.keyMappings()) }
    var gestureOn by remember { mutableStateOf(settings.touchBlockGestureEnabled) }
    var gestureKey by remember { mutableIntStateOf(settings.touchBlockGestureKeyCode) }
    var gestureCount by remember { mutableIntStateOf(settings.touchBlockGestureCount) }
    var gestureWindow by remember { mutableIntStateOf(settings.touchBlockGestureWindowMs) }
    var lastKey by remember { mutableIntStateOf(settings.lastDetectedKeyCode) }
    var capturingFor by remember { mutableStateOf<CaptureTarget?>(null) }
    var editKey by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(capturingFor) {
        val target = capturingFor
        if (target == null) {
            ReaderKeyBus.captureListener = null
        } else {
            ReaderKeyBus.captureListener = capture@{ ev ->
                if (ev.keyCode == KeyEvent.KEYCODE_BACK) {
                    capturingFor = null
                    return@capture
                }
                settings.rememberLastKeyCode(ev.keyCode)
                lastKey = ev.keyCode
                when (target) {
                    CaptureTarget.BindMapping -> {
                        val map = mappings.toMutableMap()
                        if (!map.containsKey(ev.keyCode)) {
                            map[ev.keyCode] = ReaderKeyAction.NEXT_PAGE
                            mappings = map
                            settings.setKeyMappings(map)
                        }
                        editKey = ev.keyCode
                    }
                    CaptureTarget.GestureKey -> {
                        gestureKey = ev.keyCode
                        settings.touchBlockGestureKeyCode = ev.keyCode
                    }
                }
                capturingFor = null
            }
        }
        onDispose { ReaderKeyBus.captureListener = null }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("启用实体键映射")
            Text("音量键、侧键等均可绑定", fontSize = 12.sp, color = Color.Gray)
        }
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                settings.hardwareKeysEnabled = it
            },
        )
    }

    Text(
        "默认：音量+ = 上一页，音量- = 下一页。可绑定手机上能传到 App 的任意实体键（含额外侧键）。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (lastKey > 0) {
        Text(
            "最近检测到：${ReaderKeyLabels.nameOf(lastKey)}",
            fontSize = 12.sp,
            color = Color(0xFF1976D2),
        )
    }

    OutlinedButton(
        onClick = { capturingFor = CaptureTarget.BindMapping },
        enabled = enabled && capturingFor == null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (capturingFor == CaptureTarget.BindMapping) "请按下要绑定的实体键…" else "按下实体键以添加/编辑映射")
    }

    if (capturingFor != null) {
        Text(
            "正在监听按键（返回键取消）。请按下手机音量键或侧键。",
            color = Color(0xFFC62828),
            fontSize = 13.sp,
        )
    }

    HorizontalDivider()
    Text("当前映射", style = MaterialTheme.typography.titleSmall)

    if (mappings.isEmpty()) {
        Text("暂无映射", fontSize = 13.sp, color = Color.Gray)
    } else {
        mappings.entries.sortedBy { it.key }.forEach { (code, action) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { editKey = code }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ReaderKeyLabels.nameOf(code))
                    Text("keyCode=$code", fontSize = 11.sp, color = Color.Gray)
                }
                Text(action.label, color = Color(0xFF1976D2))
            }
            HorizontalDivider()
        }
    }

    TextButton(
        onClick = {
            mappings = AppSettings.defaultKeyMappings()
            settings.setKeyMappings(mappings)
        },
        enabled = enabled,
    ) { Text("恢复默认（音量±翻页）") }

    Spacer(Modifier.height(8.dp))
    Text("触控屏蔽", style = MaterialTheme.typography.titleSmall)
    Text(
        "开启后阅读页忽略手指触摸（防误触）。可用下方连按手势或映射「触控屏蔽开/关」切换。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("短时连按开关")
            Text("同一实体键快速连按切换屏蔽", fontSize = 12.sp, color = Color.Gray)
        }
        Switch(
            checked = gestureOn,
            onCheckedChange = {
                gestureOn = it
                settings.touchBlockGestureEnabled = it
            },
        )
    }

    OutlinedButton(
        onClick = { capturingFor = CaptureTarget.GestureKey },
        enabled = gestureOn && capturingFor == null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("手势键：${ReaderKeyLabels.nameOf(gestureKey)}（点击改绑）")
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("连按次数：$gestureCount")
        Row {
            TextButton(
                onClick = {
                    gestureCount = (gestureCount - 1).coerceIn(2, 8)
                    settings.touchBlockGestureCount = gestureCount
                },
            ) { Text("-") }
            TextButton(
                onClick = {
                    gestureCount = (gestureCount + 1).coerceIn(2, 8)
                    settings.touchBlockGestureCount = gestureCount
                },
            ) { Text("+") }
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("判定窗口：${gestureWindow}ms")
        Row {
            TextButton(
                onClick = {
                    gestureWindow = (gestureWindow - 100).coerceIn(300, 3000)
                    settings.touchBlockGestureWindowMs = gestureWindow
                },
            ) { Text("-") }
            TextButton(
                onClick = {
                    gestureWindow = (gestureWindow + 100).coerceIn(300, 3000)
                    settings.touchBlockGestureWindowMs = gestureWindow
                },
            ) { Text("+") }
        }
    }

    Text(
        "提示：手势键第 1 次立即翻页；窗口内继续连按只计数，达到次数即切换触控屏蔽（不会连翻多页）。也可把侧键映射为「触控屏蔽开/关」。",
        fontSize = 11.sp,
        color = Color.Gray,
    )

    val editing = editKey
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editKey = null },
            title = { Text("映射 · ${ReaderKeyLabels.nameOf(editing)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("keyCode=$editing", fontSize = 12.sp, color = Color.Gray)
                    ReaderKeyAction.entries.forEach { act ->
                        FilterChip(
                            selected = mappings[editing] == act ||
                                (act == ReaderKeyAction.NONE && mappings[editing] == null),
                            onClick = {
                                val map = mappings.toMutableMap()
                                if (act == ReaderKeyAction.NONE) map.remove(editing)
                                else map[editing] = act
                                mappings = map
                                settings.setKeyMappings(map)
                                editKey = null
                            },
                            label = { Text(act.label) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editKey = null }) { Text("关闭") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val map = mappings.toMutableMap()
                        map.remove(editing)
                        mappings = map
                        settings.setKeyMappings(map)
                        editKey = null
                    },
                ) { Text("删除映射", color = Color(0xFFC62828)) }
            },
        )
    }
}

private enum class CaptureTarget {
    BindMapping,
    GestureKey,
}
