package com.gnreader.app.ui.reader

import android.graphics.Color as AndroidColor
import android.text.SpannableString
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.AutoToc
import com.gnreader.app.data.FileEncodingStore
import com.gnreader.app.data.ManualTocEntry
import com.gnreader.app.data.ManualTocStore
import com.gnreader.app.data.ReaderKeyAction
import com.gnreader.app.data.RecentStore
import com.gnreader.app.data.fs.LocalFs
import com.gnreader.app.data.tts.TtsController
import com.gnreader.app.reader.ReaderHardwareKeys
import com.gnreader.app.reader.ReaderKeyBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipFile

private const val MENU_ADD_TOC = 0x671

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReaderScreen(
    path: String,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tocStore = remember { ManualTocStore(context) }
    val encStore = remember { FileEncodingStore(context) }
    var encoding by remember {
        mutableStateOf(encStore.get(path, settings.defaultEncoding))
    }
    var encodings by remember { mutableStateOf(settings.allEncodings()) }
    var text by remember { mutableStateOf("") }
    var editable by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showEnc by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var manualToc by remember { mutableStateOf(tocStore.listFor(path)) }
    var autoToc by remember { mutableStateOf<List<ManualTocEntry>>(emptyList()) }
    var textViewRef by remember { mutableStateOf<TextView?>(null) }
    val tts = remember { TtsController(context) }
    var speaking by remember { mutableStateOf(false) }
    var ttsIndex by remember { mutableIntStateOf(-1) }
    var ttsUnits by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val fileName = remember(path) { File(path).name.lowercase() }
    val isOfficeOrMobi = fileName.endsWith(".docx") || fileName.endsWith(".pptx") ||
        fileName.endsWith(".xlsx") || fileName.endsWith(".doc") ||
        fileName.endsWith(".ppt") || fileName.endsWith(".xls") ||
        fileName.endsWith(".mobi") || fileName.endsWith(".azw") || fileName.endsWith(".azw3")
    val dark = isSystemInDarkTheme()
    val hlMode = settings.ttsHighlightMode
    val hlColor = remember(settings.ttsHighlightColor) {
        try {
            AndroidColor.parseColor(settings.ttsHighlightColor)
        } catch (_: Exception) {
            AndroidColor.parseColor("#1565C0")
        }
    }

    fun applyTtsHighlight(tv: TextView?, body: String, index: Int, units: List<Pair<Int, Int>>) {
        if (tv == null) return
        if (index < 0 || index >= units.size) {
            tv.text = body
            return
        }
        val (start, end) = units[index]
        val ss = SpannableString(body)
        val s = start.coerceIn(0, body.length)
        val e = end.coerceIn(s, body.length)
        if (e > s) {
            if (hlMode == "marker") {
                val bg = AndroidColor.argb(0x99, AndroidColor.red(hlColor), AndroidColor.green(hlColor), AndroidColor.blue(hlColor))
                ss.setSpan(BackgroundColorSpan(bg), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                ss.setSpan(ForegroundColorSpan(hlColor), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val bg = AndroidColor.argb(0x33, AndroidColor.red(hlColor), AndroidColor.green(hlColor), AndroidColor.blue(hlColor))
                ss.setSpan(BackgroundColorSpan(bg), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        tv.text = ss
        tv.post {
            try {
                val layout = tv.layout ?: return@post
                val line = layout.getLineForOffset(s.coerceAtMost((body.length - 1).coerceAtLeast(0)))
                val y = layout.getLineTop(line)
                tv.scrollTo(0, (y - tv.height / 3).coerceAtLeast(0))
            } catch (_: Exception) {
            }
        }
    }

    DisposableEffect(Unit) {
        tts.onState = { sp, idx, msg ->
            speaking = sp
            if (msg != null) statusMsg = msg
            when {
                msg == "已暂停" -> {
                    ttsIndex = idx
                    applyTtsHighlight(textViewRef, text, idx, ttsUnits)
                }
                sp -> {
                    ttsIndex = idx
                    applyTtsHighlight(textViewRef, text, idx, ttsUnits)
                }
                else -> {
                    ttsIndex = -1
                    applyTtsHighlight(textViewRef, text, -1, ttsUnits)
                    if (msg == null) statusMsg = null
                }
            }
        }
        onDispose { tts.shutdown() }
    }

    LaunchedEffect(path) {
        RecentStore(context).touch(path, File(path).name, "txt")
    }

    fun reloadToc() {
        manualToc = tocStore.listFor(path)
        autoToc = AutoToc.detect(text)
    }

    fun load() {
        scope.launch {
            try {
                error = null
                text = withContext(Dispatchers.IO) {
                    when {
                        fileName.endsWith(".docx") -> extractDocxText(path)
                        fileName.endsWith(".pptx") || fileName.endsWith(".xlsx") ->
                            extractZipXmlText(path)
                        fileName.endsWith(".mobi") || fileName.endsWith(".azw") || fileName.endsWith(".azw3") ->
                            extractMobiRoughText(path, encoding)
                        else -> LocalFs.readText(path, encoding)
                    }
                }
                editable = !isOfficeOrMobi
                reloadToc()
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    LaunchedEffect(path, encoding) { load() }

    fun jumpTo(offset: Int) {
        val tv = textViewRef ?: return
        val len = tv.text?.length ?: 0
        val safe = offset.coerceIn(0, len)
        tv.post {
            try {
                val layout = tv.layout ?: return@post
                val line = layout.getLineForOffset(safe.coerceAtMost((len - 1).coerceAtLeast(0)))
                val y = layout.getLineTop(line)
                tv.scrollTo(0, y)
            } catch (_: Exception) {
            }
        }
    }

    fun scrollByPage(direction: Int) {
        val tv = textViewRef ?: return
        val delta = (tv.height * 0.9f).toInt().coerceAtLeast(80) * direction
        tv.post {
            val max = (tv.layout?.height ?: 0) - tv.height
            val next = (tv.scrollY + delta).coerceIn(0, max.coerceAtLeast(0))
            tv.scrollTo(0, next)
        }
    }

    var touchBlocked by remember { mutableStateOf(false) }
    var showKeySettings by remember { mutableStateOf(false) }

    fun buildTtsUnits(body: String): Pair<List<String>, List<Pair<Int, Int>>> {
        val texts = mutableListOf<String>()
        val ranges = mutableListOf<Pair<Int, Int>>()
        val regex = Regex("\\n{2,}|[。！？.!?…]")
        var start = 0
        for (m in regex.findAll(body)) {
            val end = m.range.last + 1
            val raw = body.substring(start, end)
            val chunk = raw.trim()
            if (chunk.isNotEmpty()) {
                val lead = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trail = raw.indexOfLast { !it.isWhitespace() }
                texts += chunk
                ranges += (start + lead) to (if (trail >= 0) start + trail + 1 else end)
            }
            start = end
        }
        if (start < body.length) {
            val raw = body.substring(start)
            val chunk = raw.trim()
            if (chunk.isNotEmpty()) {
                val lead = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trail = raw.indexOfLast { !it.isWhitespace() }
                texts += chunk
                ranges += (start + lead) to (if (trail >= 0) start + trail + 1 else body.length)
            }
        }
        return texts to ranges
    }

    fun startTts() {
        if (speaking) {
            tts.stop()
            ttsIndex = -1
            applyTtsHighlight(textViewRef, text, -1, emptyList())
            return
        }
        val body = text
        val (parts, ranges) = buildTtsUnits(body)
        ttsUnits = ranges
        ttsIndex = 0
        val err = tts.speakParagraphs(parts)
        if (err != null) {
            statusMsg = err
            ttsUnits = emptyList()
            ttsIndex = -1
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        } else {
            statusMsg = "朗读中…"
            applyTtsHighlight(textViewRef, body, 0, ranges)
        }
    }

    val onKeyAction = rememberUpdatedState<(ReaderKeyAction) -> Unit> { action ->
        when (action) {
            ReaderKeyAction.PREV_PAGE, ReaderKeyAction.PREV_CHAPTER -> scrollByPage(-1)
            ReaderKeyAction.NEXT_PAGE, ReaderKeyAction.NEXT_CHAPTER -> scrollByPage(1)
            ReaderKeyAction.TOGGLE_TTS -> startTts()
            ReaderKeyAction.TOGGLE_CHROME -> showKeySettings = !showKeySettings
            ReaderKeyAction.TOGGLE_TOUCH_BLOCK, ReaderKeyAction.NONE -> Unit
        }
    }

    val hardwareKeys = remember {
        ReaderHardwareKeys(
            settings = settings,
            onAction = { action -> onKeyAction.value(action) },
            onTouchBlockChanged = { touchBlocked = it },
            onStatus = { msg ->
                statusMsg = msg
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
        )
    }

    DisposableEffect(showKeySettings, showToc, showEnc) {
        if (showKeySettings || showToc || showEnc) {
            ReaderKeyBus.interceptor = null
        } else {
            ReaderKeyBus.interceptor = { hardwareKeys.handleKeyEvent(it) }
        }
        onDispose { ReaderKeyBus.interceptor = null }
    }

    DisposableEffect(Unit) {
        onDispose { hardwareKeys.dispose() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(path).name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showEnc = true }) { Text(encoding) }
                    IconButton(onClick = {
                        reloadToc()
                        showToc = true
                    }) { Icon(Icons.AutoMirrored.Filled.List, "目录") }
                    IconButton(onClick = { startTts() }) {
                        Icon(if (speaking) Icons.Default.Stop else Icons.Default.VolumeUp, "朗读")
                    }
                    TextButton(onClick = { showKeySettings = true }) { Text("按键") }
                    if (editable) {
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val latest = textViewRef?.text?.toString() ?: text
                                    withContext(Dispatchers.IO) {
                                        LocalFs.writeText(path, latest, encoding)
                                    }
                                    text = latest
                                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    error = e.message
                                }
                            }
                        }) { Icon(Icons.Default.Save, "保存") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
            statusMsg?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            if (isOfficeOrMobi) {
                Text(
                    "当前为抽取预览。选中标题后可用系统选择菜单「加入目录」。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            } else {
                Text(
                    "选中章节名 → 长按菜单「加入目录」。朗读中会高亮当前句。音量键可翻页。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        setTextIsSelectable(true)
                        textSize = settings.fontSizeSp
                        setPadding(36, 24, 36, 48)
                        setLineSpacing(0f, 1.25f)
                        movementMethod = ScrollingMovementMethod.getInstance()
                        setTextColor(if (dark) AndroidColor.WHITE else AndroidColor.BLACK)
                        setBackgroundColor(if (dark) AndroidColor.BLACK else AndroidColor.parseColor("#F7F4EE"))
                        customSelectionActionModeCallback = object : ActionMode.Callback {
                            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                menu.add(0, MENU_ADD_TOC, 0, "加入目录")
                                return true
                            }

                            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = true

                            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                                if (item.itemId != MENU_ADD_TOC) return false
                                val start = selectionStart.coerceAtMost(selectionEnd)
                                val end = selectionStart.coerceAtLeast(selectionEnd)
                                if (start < 0 || end <= start) {
                                    Toast.makeText(ctx, "请先选中目录标题", Toast.LENGTH_SHORT).show()
                                    return true
                                }
                                val title = text.subSequence(start, end).toString().trim()
                                tocStore.add(path, title, start, end - start)
                                manualToc = tocStore.listFor(path)
                                Toast.makeText(ctx, "已加入目录：$title", Toast.LENGTH_SHORT).show()
                                mode.finish()
                                return true
                            }

                            override fun onDestroyActionMode(mode: ActionMode) {}
                        }
                        textViewRef = this
                    }
                },
                update = { tv ->
                    if (!speaking) {
                        if (tv.text?.toString() != text) {
                            tv.text = text
                        }
                    } else {
                        applyTtsHighlight(tv, text, ttsIndex, ttsUnits)
                    }
                    tv.textSize = settings.fontSizeSp
                    textViewRef = tv
                },
                modifier = Modifier.fillMaxSize().weight(1f),
            )
            }
            if (touchBlocked && !showKeySettings) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                        .background(Color(0x22000000)),
                ) {
                    Text(
                        "触控已屏蔽",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 24.dp)
                            .background(Color(0xCC333333))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    if (showKeySettings) {
        AlertDialog(
            onDismissRequest = { showKeySettings = false },
            title = { Text("实体键 / 触控屏蔽") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp)) {
                    HardwareKeysSettingsPanel(settings = settings)
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeySettings = false }) { Text("关闭") }
            },
        )
    }

    if (showToc) {
        AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text("目录") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        Text("手动目录", style = MaterialTheme.typography.titleSmall)
                    }
                    if (manualToc.isEmpty()) {
                        item { Text("暂无。选中标题后用「加入目录」。", style = MaterialTheme.typography.bodySmall) }
                    }
                    items(manualToc, key = { it.id }) { e ->
                        ListItem(
                            headlineContent = { Text(e.title) },
                            supportingContent = { Text("位置 ${offsetLabel(e.offset)}") },
                            trailingContent = {
                                TextButton(onClick = {
                                    tocStore.remove(path, e.id)
                                    manualToc = tocStore.listFor(path)
                                }) { Text("删") }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        )
                        TextButton(onClick = {
                            showToc = false
                            jumpTo(e.offset)
                        }) { Text("跳转到：${e.title}") }
                    }
                    item {
                        Spacer(Modifier.height(12.dp))
                        Text("自动识别", style = MaterialTheme.typography.titleSmall)
                    }
                    if (autoToc.isEmpty()) {
                        item { Text("未识别到常见章节标题。", style = MaterialTheme.typography.bodySmall) }
                    }
                    items(autoToc.take(100), key = { it.id }) { e ->
                        TextButton(onClick = {
                            showToc = false
                            jumpTo(e.offset)
                        }) { Text(e.title, maxLines = 1) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showToc = false }) { Text("关闭") }
            },
        )
    }

    if (showEnc) {
        AlertDialog(
            onDismissRequest = { showEnc = false },
            title = { Text("选择编码") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    encodings.forEach { enc ->
                        TextButton(onClick = {
                            encoding = enc
                            encStore.set(path, enc)
                            showEnc = false
                        }) { Text(enc) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEnc = false }) { Text("关闭") }
            },
        )
    }
}

private fun offsetLabel(offset: Int): String = offset.toString()

private fun extractDocxText(path: String): String {
    ZipFile(path).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: return "无法读取 docx"
        zip.getInputStream(entry).use { input ->
            val xml = input.bufferedReader(Charsets.UTF_8).readText()
            return xml
                .replace(Regex("</w:p>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim()
        }
    }
}

private fun extractZipXmlText(path: String): String {
    val sb = StringBuilder()
    ZipFile(path).use { zip ->
        zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".xml") }
            .take(30)
            .forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val xml = input.bufferedReader(Charsets.UTF_8).readText()
                    val plain = xml.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                    if (plain.length > 20) sb.appendLine(plain).appendLine()
                }
            }
    }
    return sb.toString().ifBlank { "未能抽取文本" }
}

private fun extractMobiRoughText(path: String, encoding: String): String {
    val bytes = File(path).readBytes()
    val charset = try {
        Charset.forName(encoding)
    } catch (_: Exception) {
        Charsets.UTF_8
    }
    val decoded = String(bytes, charset)
    val cleaned = decoded.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), " ")
        .replace(Regex(" {2,}"), " ")
    return cleaned.take(200_000).ifBlank { "未能抽取 MOBI 文本" }
}
