package com.gnreader.app.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.ReaderFonts

private val MoonBlue = Color(0xFF1976D2)

private enum class SettingsPage {
    Root, Layout, Appearance, PageTurn, Keys, SystemBars, Tts, Comments, About
}

/**
 * 分层设置（类 Moon+）：可滚动；系统返回键回到上一层设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsOverlay(
    @Suppress("UNUSED_PARAMETER") settings: AppSettings,
    themeId: String,
    fontSize: Float,
    fontFamily: String,
    customTextColor: String,
    customBgColor: String,
    bgImagePath: String,
    bgImageAlpha: Float,
    pageMode: String,
    ttsMode: String,
    ttsRate: Float,
    ttsPitch: Float,
    ttsHighlightMode: String,
    ttsHighlightColor: String,
    showParaComments: Boolean,
    showChapterComments: Boolean,
    paraCommentStyle: String,
    commentListStyle: String,
    marginLeft: Int,
    marginRight: Int,
    marginTop: Int,
    marginBottom: Int,
    lineMult: Float,
    showStatusBar: Boolean,
    showNavigationBar: Boolean,
    onTheme: (String) -> Unit,
    onFontSize: (Float) -> Unit,
    onFontFamily: (String) -> Unit,
    onCustomTextColor: (String) -> Unit,
    onCustomBgColor: (String) -> Unit,
    onBgImagePath: (String) -> Unit,
    onBgImageAlpha: (Float) -> Unit,
    onPageMode: (String) -> Unit,
    onTtsMode: (String) -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsPitch: (Float) -> Unit,
    onTtsHighlightMode: (String) -> Unit,
    onTtsHighlightColor: (String) -> Unit,
    onShowPara: (Boolean) -> Unit,
    onShowChapter: (Boolean) -> Unit,
    onParaCommentStyle: (String) -> Unit,
    onCommentListStyle: (String) -> Unit,
    onMarginLeft: (Int) -> Unit,
    onMarginRight: (Int) -> Unit,
    onMarginTop: (Int) -> Unit,
    onMarginBottom: (Int) -> Unit,
    onLineMult: (Float) -> Unit,
    onShowStatusBar: (Boolean) -> Unit,
    onShowNavigationBar: (Boolean) -> Unit,
    onStartTts: () -> Unit,
    onOpenSystemTts: () -> Unit,
    onPickBgImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.Root) }
    val title = when (page) {
        SettingsPage.Root -> "设置"
        SettingsPage.Layout -> "阅读排版"
        SettingsPage.Appearance -> "外观"
        SettingsPage.PageTurn -> "翻页与滚动"
        SettingsPage.Keys -> "实体键 / 触控屏蔽"
        SettingsPage.SystemBars -> "全屏 / 系统栏"
        SettingsPage.Tts -> "朗读"
        SettingsPage.Comments -> "评论显示"
        SettingsPage.About -> "关于"
    }

    BackHandler {
        if (page == SettingsPage.Root) onDismiss()
        else page = SettingsPage.Root
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (page == SettingsPage.Root) onDismiss()
                        else page = SettingsPage.Root
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MoonBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (page) {
                    SettingsPage.Root -> {
                        SettingsNavRow("阅读排版", "字号、边距、行距") { page = SettingsPage.Layout }
                        SettingsNavRow("外观", "主题、字体、颜色、背景") { page = SettingsPage.Appearance }
                        SettingsNavRow("翻页与滚动", if (pageMode == "page") "当前：分页" else "当前：滚动") {
                            page = SettingsPage.PageTurn
                        }
                        SettingsNavRow("实体键 / 触控屏蔽", "音量键翻页、侧键映射、防误触") {
                            page = SettingsPage.Keys
                        }
                        SettingsNavRow(
                            "全屏 / 系统栏",
                            buildString {
                                append(if (showStatusBar) "通知栏开" else "通知栏关")
                                append(" · ")
                                append(if (showNavigationBar) "导航栏开" else "导航栏关")
                            },
                        ) { page = SettingsPage.SystemBars }
                        SettingsNavRow("朗读", "范围、语速、高亮样式") { page = SettingsPage.Tts }
                        SettingsNavRow("评论显示", "段评样式 / 隐藏章评") { page = SettingsPage.Comments }
                        SettingsNavRow("关于", "GN Reader") { page = SettingsPage.About }
                    }
                    SettingsPage.Layout -> {
                        StepperRow(
                            label = "字号",
                            valueText = "${fontSize.toInt()}",
                            onMinus = { onFontSize((fontSize - 1f).coerceIn(12f, 32f)) },
                            onPlus = { onFontSize((fontSize + 1f).coerceIn(12f, 32f)) },
                        )
                        Text("页边距（分别设置）", style = MaterialTheme.typography.titleSmall)
                        StepperRow(
                            label = "左边距",
                            valueText = "$marginLeft",
                            onMinus = { onMarginLeft((marginLeft - 2).coerceIn(4, 56)) },
                            onPlus = { onMarginLeft((marginLeft + 2).coerceIn(4, 56)) },
                        )
                        StepperRow(
                            label = "右边距",
                            valueText = "$marginRight",
                            onMinus = { onMarginRight((marginRight - 2).coerceIn(4, 56)) },
                            onPlus = { onMarginRight((marginRight + 2).coerceIn(4, 56)) },
                        )
                        StepperRow(
                            label = "上边距",
                            valueText = "$marginTop",
                            onMinus = { onMarginTop((marginTop - 2).coerceIn(0, 48)) },
                            onPlus = { onMarginTop((marginTop + 2).coerceIn(0, 48)) },
                        )
                        StepperRow(
                            label = "下边距",
                            valueText = "$marginBottom",
                            onMinus = { onMarginBottom((marginBottom - 2).coerceIn(0, 48)) },
                            onPlus = { onMarginBottom((marginBottom + 2).coerceIn(0, 48)) },
                        )
                        StepperRow(
                            label = "行距",
                            valueText = String.format("%.2f", lineMult),
                            onMinus = { onLineMult((lineMult - 0.05f).coerceIn(1.4f, 2.4f)) },
                            onPlus = { onLineMult((lineMult + 0.05f).coerceIn(1.4f, 2.4f)) },
                        )
                    }
                    SettingsPage.Appearance -> {
                        var textHex by remember(customTextColor) { mutableStateOf(customTextColor.removePrefix("#")) }
                        var bgHex by remember(customBgColor) { mutableStateOf(customBgColor.removePrefix("#")) }
                        var alphaText by remember(bgImageAlpha) {
                            mutableStateOf(String.format("%.0f", bgImageAlpha * 100))
                        }
                        val zhFonts = ReaderFonts.options().map { it.id to it.label }
                        Text("主题", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("paper" to "纸感", "night" to "夜间", "green" to "护眼").forEach { (id, label) ->
                                FilterChip(selected = themeId == id, onClick = { onTheme(id) }, label = { Text(label, fontSize = 12.sp) })
                            }
                        }
                        Text("中文字体", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "点选一行即可切换。内置霞鹜文楷 / 得意黑 / Liberation；新宋首次会下载 Noto Serif SC",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                        // 不用只读 TextField+Dropdown：TextField 会吞掉点击，菜单打不开
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F5F5), MaterialTheme.shapes.small)
                                .padding(vertical = 4.dp),
                        ) {
                            zhFonts.forEach { (id, label) ->
                                val selected = fontFamily == id
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onFontFamily(id) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { onFontFamily(id) },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        label,
                                        color = if (selected) MoonBlue else Color.Unspecified,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = textHex,
                                onValueChange = {
                                    textHex = it.filter { c -> c.isDigit() || c in "abcdefABCDEF" }.take(6)
                                    if (textHex.length == 6) onCustomTextColor("#$textHex")
                                    if (textHex.isEmpty()) onCustomTextColor("")
                                },
                                label = { Text("文字色 #RRGGBB") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = bgHex,
                                onValueChange = {
                                    bgHex = it.filter { c -> c.isDigit() || c in "abcdefABCDEF" }.take(6)
                                    if (bgHex.length == 6) onCustomBgColor("#$bgHex")
                                    if (bgHex.isEmpty()) onCustomBgColor("")
                                },
                                label = { Text("背景色 #RRGGBB") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (bgImagePath.isBlank()) "背景图：无" else bgImagePath.substringAfterLast('/'),
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                            TextButton(onClick = onPickBgImage) { Text("选图") }
                            if (bgImagePath.isNotBlank()) {
                                TextButton(onClick = { onBgImagePath("") }) { Text("清") }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = alphaText,
                                onValueChange = {
                                    alphaText = it.filter { c -> c.isDigit() }.take(3)
                                    val n = alphaText.toIntOrNull()?.coerceIn(0, 100)
                                    if (n != null) onBgImageAlpha(n / 100f)
                                },
                                label = { Text("背景透明度 0-100") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { /* preview is live below */ }) { Text("浏览") }
                        }
                        // 预览：底色 + 可选图透明度感 + 文字
                        val previewBg = try {
                            if (customBgColor.length >= 7) Color(android.graphics.Color.parseColor(customBgColor))
                            else when (themeId) {
                                "night" -> Color(0xFF121212)
                                "green" -> Color(0xFFC7EDCC)
                                else -> Color(0xFFF7F4EE)
                            }
                        } catch (_: Exception) { Color(0xFFF7F4EE) }
                        val previewFg = try {
                            if (customTextColor.length >= 7) Color(android.graphics.Color.parseColor(customTextColor))
                            else when (themeId) {
                                "night" -> Color(0xFFE6E6E6)
                                else -> Color(0xFF222222)
                            }
                        } catch (_: Exception) { Color(0xFF222222) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .background(previewBg)
                                .padding(12.dp),
                        ) {
                            Text(
                                "预览 Aa 汉字 段评1",
                                color = previewFg,
                                fontSize = 16.sp,
                            )
                            Text(
                                "透明度 ${alphaText.ifBlank { "0" }}%",
                                color = previewFg.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }
                    }
                    SettingsPage.PageTurn -> {
                        Text("阅读模式", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = pageMode == "scroll",
                                onClick = { onPageMode("scroll") },
                                label = { Text("滚动") },
                            )
                            FilterChip(
                                selected = pageMode == "page",
                                onClick = { onPageMode("page") },
                                label = { Text("分页") },
                            )
                        }
                        Text(
                            "分页：原生排版（墨水屏友好）。滚动仍用 WebView。实体键翻页见「实体键 / 触控屏蔽」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsPage.Keys -> {
                        HardwareKeysSettingsPanel(settings = settings)
                    }
                    SettingsPage.SystemBars -> {
                        Text("沉浸阅读", style = MaterialTheme.typography.titleSmall)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("显示通知栏")
                                Text("状态栏时间/电量等", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = showStatusBar, onCheckedChange = onShowStatusBar)
                        }
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("显示导航栏")
                                Text("底部手势条/三键", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = showNavigationBar, onCheckedChange = onShowNavigationBar)
                        }
                        HorizontalDivider()
                        var infoBar by remember { mutableStateOf(settings.showInfoBar) }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("显示底部信息条")
                                Text("未呼出菜单时显示章节/页码/全书进度", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = infoBar,
                                onCheckedChange = {
                                    infoBar = it
                                    settings.showInfoBar = it
                                },
                            )
                        }
                    }
                    SettingsPage.Tts -> {
                        Text("朗读范围", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "paragraph" to "逐段",
                                "page" to "本页",
                                "chapter" to "整章",
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = ttsMode == id,
                                    onClick = { onTtsMode(id) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        StepperRow(
                            label = "语速",
                            valueText = String.format("%.1fx", ttsRate),
                            onMinus = { onTtsRate((ttsRate - 0.1f).coerceIn(0.5f, 2.0f)) },
                            onPlus = { onTtsRate((ttsRate + 0.1f).coerceIn(0.5f, 2.0f)) },
                        )
                        StepperRow(
                            label = "语调",
                            valueText = String.format("%.1f", ttsPitch),
                            onMinus = { onTtsPitch((ttsPitch - 0.1f).coerceIn(0.6f, 1.8f)) },
                            onPlus = { onTtsPitch((ttsPitch + 0.1f).coerceIn(0.6f, 1.8f)) },
                        )
                        TextButton(onClick = onStartTts) { Text("开始朗读") }
                        TextButton(onClick = onOpenSystemTts) { Text("打开系统 TTS 设置") }
                        Text(
                            "朗读只用正文段落，不会读章末评论。关闭「显示章末评论」可彻底隐藏入口。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        Text("高亮表现（边角）", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "朗读中当前段落的视觉样式",
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = ttsHighlightMode == "color",
                                onClick = { onTtsHighlightMode("color") },
                                label = { Text("文字变色") },
                            )
                            FilterChip(
                                selected = ttsHighlightMode == "marker",
                                onClick = { onTtsHighlightMode("marker") },
                                label = { Text("荧光笔") },
                            )
                        }
                        var hlHex by remember(ttsHighlightColor) {
                            mutableStateOf(ttsHighlightColor.removePrefix("#"))
                        }
                        OutlinedTextField(
                            value = hlHex,
                            onValueChange = {
                                hlHex = it.filter { c -> c.isDigit() || c in "abcdefABCDEF" }.take(6)
                                if (hlHex.length == 6) onTtsHighlightColor("#$hlHex")
                            },
                            label = { Text("高亮颜色 #RRGGBB") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ColorPresetRow(
                            current = ttsHighlightColor,
                            presets = listOf(
                                "#1565C0" to "蓝",
                                "#C62828" to "红",
                                "#2E7D32" to "绿",
                                "#F9A825" to "黄",
                                "#6A1B9A" to "紫",
                            ),
                            onPick = {
                                onTtsHighlightColor(it)
                                hlHex = it.removePrefix("#")
                            },
                        )
                    }
                    SettingsPage.Comments -> {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("显示段评")
                                Text("段落末尾气泡", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = showParaComments, onCheckedChange = onShowPara)
                        }
                        Text("段评气泡样式", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "bubble" to "气泡",
                                "number" to "数字",
                                "inline" to "角标",
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = paraCommentStyle == id,
                                    onClick = { onParaCommentStyle(id) },
                                    label = { Text(label) },
                                )
                            }
                        }
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("隐藏章节整体评论")
                                Text("关掉后不显示章评入口，避免误点；朗读本就不读章评", fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = !showChapterComments,
                                onCheckedChange = { onShowChapter(!it) },
                            )
                        }
                        Text("评论列表密度", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("comfortable" to "舒适", "compact" to "紧凑").forEach { (id, label) ->
                                FilterChip(
                                    selected = commentListStyle == id,
                                    onClick = { onCommentListStyle(id) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    SettingsPage.About -> {
                        Text("GN Reader", style = MaterialTheme.typography.titleMedium)
                        Text("仿 Moon+ 的 .gnbook / EPUB 阅读器", fontSize = 13.sp)
                        Text("引擎 native-v3 · 段评行内气泡", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) { Text("完成") }
        }
    }
}

@Composable
private fun ColorPresetRow(
    current: String,
    presets: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        presets.forEach { (hex, label) ->
            FilterChip(
                selected = current.equals(hex, ignoreCase = true),
                onClick = { onPick(hex) },
                label = { Text(label, fontSize = 12.sp) },
            )
        }
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, fontSize = 12.sp) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(Icons.Default.Remove, "减小") }
        Text(valueText, modifier = Modifier.widthIn(min = 48.dp), fontSize = 16.sp)
        IconButton(onClick = onPlus) { Icon(Icons.Default.Add, "增大") }
    }
}
