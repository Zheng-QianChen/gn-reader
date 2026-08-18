package com.gnreader.app.ui.reader

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.ReaderKeyAction
import com.gnreader.app.data.RecentStore
import com.gnreader.app.data.gnbook.GnComment
import com.gnreader.app.data.gnbook.GnBook
import com.gnreader.app.data.gnbook.GnbookParser
import com.gnreader.app.data.gnbook.GnbookWriter
import com.gnreader.app.data.reader.PageContent
import com.gnreader.app.data.tts.TtsController
import com.gnreader.app.reader.ReaderHardwareKeys
import com.gnreader.app.reader.ReaderKeyBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MoonBlue = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GnbookReaderScreen(
    path: String,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var book by remember { mutableStateOf<GnBook?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var chromeVisible by remember { mutableStateOf(false) }
    var chapterIndex by remember { mutableIntStateOf(0) }
    var chapterCount by remember { mutableIntStateOf(1) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(1) }
    var sheetComments by remember { mutableStateOf<List<GnComment>?>(null) }
    var sheetTitle by remember { mutableStateOf("评论") }
    var showToc by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchHits by remember { mutableStateOf<List<Triple<Int, String, String>>>(emptyList()) }
    var pageMode by remember { mutableStateOf(settings.pageMode) }
    var ttsMode by remember { mutableStateOf(settings.ttsMode) }
    var showParaComments by remember { mutableStateOf(settings.showParagraphComments) }
    var showChapterComments by remember { mutableStateOf(settings.showChapterComments) }
    var themeId by remember { mutableStateOf(settings.themeId) }
    var fontSize by remember { mutableFloatStateOf(settings.fontSizeSp) }
    var fontFamily by remember { mutableStateOf(settings.fontFamily) }
    var customTextColor by remember { mutableStateOf(settings.customTextColor) }
    var customBgColor by remember { mutableStateOf(settings.customBgColor) }
    var bgImagePath by remember { mutableStateOf(settings.bgImagePath) }
    var bgImageAlpha by remember { mutableFloatStateOf(settings.bgImageAlpha) }
    var paraCommentStyle by remember { mutableStateOf(settings.paraCommentStyle) }
    var commentListStyle by remember { mutableStateOf(settings.commentListStyle) }
    var marginLeft by remember { mutableIntStateOf(settings.pageMarginLeft) }
    var marginRight by remember { mutableIntStateOf(settings.pageMarginRight) }
    var marginTop by remember { mutableIntStateOf(settings.pageMarginTop) }
    var marginBottom by remember { mutableIntStateOf(settings.pageMarginBottom) }
    var lineMult by remember { mutableFloatStateOf(settings.lineHeightMult) }
    var showStatusBar by remember { mutableStateOf(settings.showStatusBar) }
    var showNavigationBar by remember { mutableStateOf(settings.showNavigationBar) }
    var nativePages by remember { mutableStateOf<List<PageContent>>(emptyList()) }
    var jumpParagraphId by remember { mutableStateOf<String?>(null) }
    var openChapterAtEnd by remember { mutableStateOf(false) }
    var showBookInfo by remember { mutableStateOf(false) }
    val tts = remember { TtsController(context) }
    var speaking by remember { mutableStateOf(false) }
    var ttsPaused by remember { mutableStateOf(false) }
    var ttsRate by remember { mutableFloatStateOf(settings.ttsSpeechRate) }
    var ttsPitch by remember { mutableFloatStateOf(settings.ttsPitch) }
    var ttsHighlightMode by remember { mutableStateOf(settings.ttsHighlightMode) }
    var ttsHighlightColor by remember { mutableStateOf(settings.ttsHighlightColor) }
    var ttsUnits by remember { mutableStateOf<List<NativeTtsUnit>>(emptyList()) }
    var ttsIndex by remember { mutableIntStateOf(-1) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    val recentStore = remember { RecentStore(context) }
    val navBars = WindowInsets.navigationBars.asPaddingValues()
    val infoBarHeight = 26.dp
    // 暂停菜单浮层覆盖，不压缩 WebView，避免暂停时重算分页跳页
    val bottomPad = if (showNavigationBar) navBars.calculateBottomPadding() else 0.dp
    val bottomReserve = infoBarHeight + bottomPad
    val ttsActivePid = ttsUnits.getOrNull(ttsIndex)?.pid
    var touchBlocked by remember { mutableStateOf(false) }

    val pickBgImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val dest = File(context.filesDir, "reader_bg_${System.currentTimeMillis()}.img")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            bgImagePath = dest.absolutePath
            settings.bgImagePath = bgImagePath
        } catch (e: Exception) {
            Toast.makeText(context, e.message ?: "选择失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun syncViewportToWeb() {
        val wv = webView ?: return
        wv.post {
            val h = wv.height
            val w = wv.width
            if (h <= 0 || w <= 0) return@post
            wv.evaluateJavascript(
                "GnReader.setViewport($w,$h,$marginLeft,$marginTop,$lineMult,$marginLeft,$marginRight,$marginTop,$marginBottom);",
                null,
            )
        }
    }

    LaunchedEffect(Unit) {
        tts.setSpeechRate(ttsRate)
        tts.setPitch(ttsPitch)
    }

    DisposableEffect(Unit) {
        tts.onState = { sp, idx, msg ->
            speaking = sp
            ttsIndex = idx
            if (msg != null) statusMsg = msg
            when {
                msg == "已暂停" -> {
                    ttsPaused = true
                    if (pageMode != "page") {
                        webView?.evaluateJavascript(
                            "GnReader.setPaused(true); GnReader.highlightTtsIndexQuiet($idx);",
                            null,
                        )
                    }
                }
                sp -> {
                    ttsPaused = false
                    if (pageMode != "page") {
                        webView?.evaluateJavascript(
                            "GnReader.setSpeaking(true); GnReader.highlightTtsIndex($idx);",
                            null,
                        )
                    }
                }
                else -> {
                    ttsPaused = false
                    ttsIndex = -1
                    if (pageMode != "page") {
                        webView?.evaluateJavascript(
                            "GnReader.clearTtsHighlight(); GnReader.setSpeaking(false); GnReader.setPaused(false);",
                            null,
                        )
                    }
                }
            }
        }
        onDispose { tts.shutdown() }
    }

    fun startOrStopTts() {
        if (speaking) {
            // 工具栏再点 = 暂停（与中区点击一致），不直接退出
            tts.pause()
            return
        }
        if (tts.isPaused()) {
            val err = tts.resume()
            if (err != null) {
                statusMsg = err
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            }
            return
        }
        chromeVisible = false
        showSettings = false
        val mode = ttsMode
        val b = book
        if (b == null) return

        fun beginSpeak(list: List<String>, units: List<NativeTtsUnit> = emptyList()) {
            if (list.isEmpty()) {
                Toast.makeText(context, "没有可朗读的文本", Toast.LENGTH_SHORT).show()
                return
            }
            ttsUnits = units
            ttsIndex = 0
            val err = tts.speakParagraphs(list)
            if (err != null) {
                statusMsg = err
                ttsUnits = emptyList()
                ttsIndex = -1
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                chromeVisible = true
            } else {
                ttsPaused = false
                val label = when (mode) {
                    "page" -> "本页"
                    "chapter" -> "整章"
                    else -> "本页起"
                }
                statusMsg = "朗读中（$label · ${list.size}）"
                if (pageMode != "page") {
                    webView?.evaluateJavascript(
                        "GnReader.setSpeaking(true); GnReader.highlightTtsIndex(0);",
                        null,
                    )
                }
            }
        }

        if (pageMode == "page") {
            val units = nativeTtsUnits(b, chapterIndex, pageIndex, nativePages, mode)
            beginSpeak(units.map { it.text }, units)
            return
        }

        val wv = webView ?: return
        wv.evaluateJavascript(
            "(function(){try{return GnReader.prepareTts('${mode}');}catch(e){return '{}';}})()",
        ) { raw ->
            val cleaned = raw?.trim()?.removeSurrounding("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?: "{}"
            mainHandler.post {
                try {
                    val obj = JSONObject(cleaned)
                    val arr = obj.optJSONArray("texts") ?: JSONArray()
                    val list = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                    beginSpeak(list)
                } catch (e: Exception) {
                    Toast.makeText(context, "朗读准备失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun turnPrevPage() {
        val b = book ?: return
        if (pageMode == "page") {
            if (pageIndex > 0) {
                pageIndex -= 1
            } else if (chapterIndex > 0) {
                openChapterAtEnd = true
                chapterIndex -= 1
                pageIndex = Int.MAX_VALUE / 4
            }
        } else {
            webView?.evaluateJavascript("GnReader.prevPage();", null)
        }
    }

    fun turnNextPage() {
        val b = book ?: return
        if (pageMode == "page") {
            if (pageIndex < pageCount - 1) {
                pageIndex += 1
            } else if (chapterIndex < b.chapters.lastIndex) {
                openChapterAtEnd = false
                chapterIndex += 1
                pageIndex = 0
            }
        } else {
            webView?.evaluateJavascript("GnReader.nextPage();", null)
        }
    }

    fun turnPrevChapter() {
        if (pageMode == "page") {
            if (chapterIndex > 0) {
                openChapterAtEnd = true
                chapterIndex -= 1
                pageIndex = Int.MAX_VALUE / 4
            }
        } else {
            webView?.evaluateJavascript("GnReader.prevChapter();", null)
        }
    }

    fun turnNextChapter() {
        val b = book ?: return
        if (pageMode == "page") {
            if (chapterIndex < b.chapters.lastIndex) {
                openChapterAtEnd = false
                chapterIndex += 1
                pageIndex = 0
            }
        } else {
            webView?.evaluateJavascript("GnReader.nextChapter();", null)
        }
    }

    val onKeyAction = rememberUpdatedState<(ReaderKeyAction) -> Unit> { action ->
        when (action) {
            ReaderKeyAction.PREV_PAGE -> turnPrevPage()
            ReaderKeyAction.NEXT_PAGE -> turnNextPage()
            ReaderKeyAction.PREV_CHAPTER -> turnPrevChapter()
            ReaderKeyAction.NEXT_CHAPTER -> turnNextChapter()
            ReaderKeyAction.TOGGLE_CHROME -> chromeVisible = !chromeVisible
            ReaderKeyAction.TOGGLE_TTS -> startOrStopTts()
            ReaderKeyAction.TOGGLE_TOUCH_BLOCK,
            ReaderKeyAction.NONE -> Unit
        }
    }

    val hardwareKeys = remember {
        ReaderHardwareKeys(
            settings = settings,
            onAction = { action -> mainHandler.post { onKeyAction.value(action) } },
            onTouchBlockChanged = { blocked -> mainHandler.post { touchBlocked = blocked } },
            onStatus = { msg ->
                mainHandler.post {
                    statusMsg = msg
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    DisposableEffect(showSettings) {
        if (showSettings) {
            ReaderKeyBus.interceptor = null
        } else {
            ReaderKeyBus.interceptor = { ev -> hardwareKeys.handleKeyEvent(ev) }
        }
        onDispose { ReaderKeyBus.interceptor = null }
    }

    DisposableEffect(Unit) {
        onDispose { hardwareKeys.dispose() }
    }

    LaunchedEffect(path) {
        try {
            book = withContext(Dispatchers.IO) {
                GnbookParser.parseFile(path, context.cacheDir)
            }
            book?.let {
                recentStore.touch(path, it.title, if (path.endsWith(".gnbook", true)) "gnbook" else "epub")
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    fun pushBookToWeb() {
        val b = book ?: return
        val wv = webView ?: return
        val json = bookToJson(b).toString()
        val mediaBase = b.extractDir?.let {
            val media = File(it, "OEBPS/media")
            if (media.isDirectory) "file://${media.absolutePath}/" else null
        }
        val payload = JSONObject(json).apply {
            if (mediaBase != null) put("mediaBase", mediaBase)
        }
        val opts = JSONObject()
            .put("theme", themeId)
            .put("fontSize", fontSize.toInt())
            .put("pageMode", pageMode)
            .put("chapterIndex", chapterIndex)
            .put("safeTop", 0)
            .put("safeBottom", 8)
            .put("padH", marginLeft)
            .put("padV", marginTop)
            .put("padL", marginLeft)
            .put("padR", marginRight)
            .put("padT", marginTop)
            .put("padB", marginBottom)
            .put("lineMult", lineMult.toDouble())
            .put("viewportH", wv.height)
            .put("viewportW", wv.width)
            .put("showParagraphComments", showParaComments)
            .put("showChapterComments", showChapterComments)
            .put("ttsHlMode", ttsHighlightMode)
            .put("ttsHlColor", ttsHighlightColor)
        wv.evaluateJavascript("GnReader.loadBook($payload, $opts);", null)
        syncViewportToWeb()
    }

    LaunchedEffect(book, webView) {
        if (book != null && webView != null) pushBookToWeb()
    }

    LaunchedEffect(bottomReserve, marginLeft, marginRight, marginTop, marginBottom, lineMult, fontSize, pageMode) {
        syncViewportToWeb()
    }

    LaunchedEffect(ttsHighlightMode, ttsHighlightColor) {
        val colorJs = ttsHighlightColor.replace("'", "")
        webView?.evaluateJavascript(
            "GnReader.setTtsHighlight('${ttsHighlightMode}', '$colorJs');",
            null,
        )
    }

    val chTitle = book?.chapters?.getOrNull(chapterIndex)?.title.orEmpty()
    // 全书进度按「字符量」统计，避免估算页数与真实分页不一致导致乱跳
    val progressPct = remember(book, chapterIndex, pageIndex, pageCount, pageMode) {
        val b = book ?: return@remember 0f
        if (b.chapters.isEmpty()) return@remember 0f
        val weights = b.chapters.map { ch ->
            ch.paragraphs.sumOf { p ->
                p.text.length.coerceAtLeast(if (p.hasImage) 40 else 1)
            }.toLong().coerceAtLeast(1L)
        }
        val total = weights.sum().coerceAtLeast(1L)
        val before = weights.take(chapterIndex).sum()
        val curWeight = weights.getOrElse(chapterIndex) { 1L }
        // 分页：按本章页序；滚动：章内按一半估算（避免一进章就当成读完）
        val within = when {
            pageMode != "page" -> 0.35f
            pageCount <= 1 -> 0.5f
            else -> ((pageIndex + 1).toFloat() / pageCount.toFloat()).coerceIn(0f, 1f)
        }
        val cur = before + (curWeight * within).toLong().coerceIn(0L, curWeight)
        ((cur.toFloat() / total.toFloat()) * 1000f).toInt() / 10f
    }
    val pageLabel = if (pageMode == "page" && pageCount > 0) {
        "${pageIndex + 1}/$pageCount · ${chapterIndex + 1}/${chapterCount.coerceAtLeast(1)}"
    } else {
        "${chapterIndex + 1}/$chapterCount"
    }

    BackHandler(enabled = !showSettings && (chromeVisible || showSearch || showToc || sheetComments != null)) {
        when {
            sheetComments != null -> sheetComments = null
            showSearch -> showSearch = false
            showToc -> showToc = false
            chromeVisible -> chromeVisible = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F4EE)),
    ) {
        val view = LocalView.current
        DisposableEffect(showStatusBar, showNavigationBar) {
            val activity = context as? Activity
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (showStatusBar) {
                    controller.show(WindowInsetsCompat.Type.statusBars())
                } else {
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                }
                if (showNavigationBar) {
                    controller.show(WindowInsetsCompat.Type.navigationBars())
                } else {
                    controller.hide(WindowInsetsCompat.Type.navigationBars())
                }
            }
            onDispose {
                // 离开阅读页恢复系统栏
                val act = context as? Activity
                val win = act?.window
                if (win != null) {
                    val c = WindowCompat.getInsetsController(win, view)
                    c.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .then(if (showStatusBar) Modifier.statusBarsPadding() else Modifier)
                .padding(bottom = bottomReserve),
        ) {
            when {
                error != null -> Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                book == null -> CircularProgressIndicator(
                    Modifier
                        .padding(24.dp)
                        .align(Alignment.Center),
                )
                else -> {
                    if (pageMode == "page") {
                        val b = book!!
                        chapterCount = b.chapters.size.coerceAtLeast(1)
                        NativePagePager(
                            book = b,
                            chapterIndex = chapterIndex.coerceIn(0, b.chapters.lastIndex.coerceAtLeast(0)),
                            pageIndex = pageIndex,
                            fontSizeSp = fontSize,
                            lineMult = lineMult,
                            marginLeft = marginLeft,
                            marginRight = marginRight,
                            marginTop = marginTop,
                            marginBottom = marginBottom,
                            themeId = themeId,
                            showParaComments = showParaComments,
                            showChapterComments = showChapterComments,
                            fontFamily = fontFamily,
                            customTextColor = customTextColor,
                            customBgColor = customBgColor,
                            bgImagePath = bgImagePath,
                            bgImageAlpha = bgImageAlpha,
                            paraCommentStyle = paraCommentStyle,
                            jumpParagraphId = jumpParagraphId,
                            onJumpConsumed = { jumpParagraphId = null },
                            openChapterAtEnd = openChapterAtEnd,
                            onOpenChapterAtEndConsumed = { openChapterAtEnd = false },
                            ttsActivePid = if (speaking || ttsPaused) ttsActivePid else null,
                            ttsHighlightMode = ttsHighlightMode,
                            ttsHighlightColor = ttsHighlightColor,
                            onPageChange = { pIdx, pTotal ->
                                pageIndex = pIdx
                                pageCount = pTotal.coerceAtLeast(1)
                            },
                            onChapterChange = { idx, atEnd ->
                                openChapterAtEnd = atEnd
                                chapterIndex = idx.coerceIn(0, b.chapters.lastIndex.coerceAtLeast(0))
                                pageIndex = if (atEnd) Int.MAX_VALUE / 4 else 0
                            },
                            onToggleChrome = { chromeVisible = !chromeVisible },
                            onCenterTap = {
                                when {
                                    tts.isSpeaking() -> {
                                        tts.tryPauseFromTap()
                                        true
                                    }
                                    tts.isPaused() -> {
                                        val err = tts.resume()
                                        if (err != null) {
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            },
                            onOpenParagraphComments = { chIdx, pid ->
                                val ch = book?.chapters?.getOrNull(chIdx) ?: return@NativePagePager
                                sheetTitle = "段评 · $pid"
                                sheetComments = ch.paragraphComments[pid].orEmpty()
                            },
                            onOpenChapterComments = { chIdx ->
                                val ch = book?.chapters?.getOrNull(chIdx) ?: return@NativePagePager
                                sheetTitle = "本章评论"
                                sheetComments = ch.hotComments + ch.generalComments
                            },
                            onPagesReady = { nativePages = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                val webSettings = this.settings
                                webSettings.javaScriptEnabled = true
                                webSettings.domStorageEnabled = true
                                webSettings.allowFileAccess = true
                                webSettings.allowContentAccess = true
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        pushBookToWeb()
                                    }
                                }
                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun toggleChrome() {
                                        mainHandler.post { chromeVisible = !chromeVisible }
                                    }

                                    /** 中区点击：朗读中→暂停；已暂停→继续。 */
                                    @JavascriptInterface
                                    fun onReaderTap(): Boolean {
                                        if (tts.isSpeaking()) {
                                            tts.tryPauseFromTap()
                                            return true
                                        }
                                        if (tts.isPaused()) {
                                            mainHandler.post {
                                                val err = tts.resume()
                                                if (err != null) {
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            return true
                                        }
                                        return false
                                    }

                                    @JavascriptInterface
                                    fun pauseTts() {
                                        tts.tryPauseFromTap()
                                    }

                                    @JavascriptInterface
                                    fun resumeTts() {
                                        mainHandler.post {
                                            val err = tts.resume()
                                            if (err != null) {
                                                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onChapterChanged(index: Int, total: Int) {
                                        mainHandler.post {
                                            chapterIndex = index
                                            chapterCount = total
                                        }
                                    }

                                    @JavascriptInterface
                                    fun onPageChanged(pIdx: Int, pTotal: Int, chIdx: Int, chTotal: Int) {
                                        mainHandler.post {
                                            pageIndex = pIdx
                                            pageCount = pTotal.coerceAtLeast(1)
                                            chapterIndex = chIdx
                                            chapterCount = chTotal.coerceAtLeast(1)
                                        }
                                    }

                                    @JavascriptInterface
                                    fun openParagraphComments(chIdx: Int, pid: String) {
                                        mainHandler.post {
                                            val ch = book?.chapters?.getOrNull(chIdx) ?: return@post
                                            sheetTitle = "段评 · $pid"
                                            sheetComments = ch.paragraphComments[pid].orEmpty()
                                        }
                                    }

                                    @JavascriptInterface
                                    fun openChapterComments(chIdx: Int) {
                                        mainHandler.post {
                                            val ch = book?.chapters?.getOrNull(chIdx) ?: return@post
                                            sheetTitle = "本章评论"
                                            sheetComments = ch.hotComments + ch.generalComments
                                        }
                                    }
                                }, "GnReaderBridge")
                                loadUrl("file:///android_asset/reader/reader.html?v=pager-v5")
                                webView = this
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { syncViewportToWeb() },
                    )
                    }
                }
            }
        }

        // 呼出菜单：顶栏 + 工具 + 章节跳转（浮层，不挤压正文布局）
        if (chromeVisible) {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Text(
                            book?.title ?: "阅读",
                            maxLines = 1,
                            fontSize = 16.sp,
                            modifier = Modifier.clickable { showBookInfo = true },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MoonBlue,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                    windowInsets = WindowInsets.statusBars,
                    actions = {
                        IconButton(onClick = {
                            showSearch = true
                            searchQuery = ""
                            searchHits = emptyList()
                        }) {
                            Icon(Icons.Default.Search, "搜索")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.MoreVert, "更多")
                        }
                    },
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color(0xFFFFFFFF)),
            ) {
                // 底栏：上一章 | 本页进度条（分页）/ 章节条（滚动） | 下一章
                Text(
                    if (pageMode == "page") {
                        "本页 ${pageIndex + 1}/${pageCount.coerceAtLeast(1)}　全书 ${"%.1f".format(progressPct)}%"
                    } else {
                        "章节 ${chapterIndex + 1}/${chapterCount.coerceAtLeast(1)}　全书 ${"%.1f".format(progressPct)}%"
                    },
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        if (pageMode == "page") {
                            if (chapterIndex > 0) {
                                openChapterAtEnd = true
                                chapterIndex -= 1
                                pageIndex = Int.MAX_VALUE / 4
                            }
                        } else {
                            webView?.evaluateJavascript("GnReader.prevChapter();", null)
                        }
                    }) { Text("上一章") }
                    if (pageMode == "page" && pageCount > 1) {
                        Slider(
                            value = pageIndex.coerceIn(0, pageCount - 1).toFloat() / (pageCount - 1).toFloat(),
                            onValueChange = { v ->
                                val idx = (v * (pageCount - 1)).toInt().coerceIn(0, pageCount - 1)
                                pageIndex = idx
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = MoonBlue, activeTrackColor = MoonBlue),
                        )
                    } else {
                        Slider(
                            value = if (chapterCount <= 1) 0f else chapterIndex.toFloat() / (chapterCount - 1),
                            onValueChange = { v ->
                                val idx = (v * (chapterCount - 1)).toInt().coerceIn(0, chapterCount - 1)
                                openChapterAtEnd = false
                                chapterIndex = idx
                                pageIndex = 0
                                if (pageMode != "page") {
                                    webView?.evaluateJavascript("GnReader.goChapter($idx);", null)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = MoonBlue, activeTrackColor = MoonBlue),
                        )
                    }
                    TextButton(onClick = {
                        if (pageMode == "page") {
                            val last = (book?.chapters?.lastIndex ?: 0)
                            if (chapterIndex < last) {
                                openChapterAtEnd = false
                                chapterIndex += 1
                                pageIndex = 0
                            }
                        } else {
                            webView?.evaluateJavascript("GnReader.nextChapter();", null)
                        }
                    }) { Text("下一章") }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        themeId = when (themeId) {
                            "paper" -> "night"
                            "night" -> "green"
                            else -> "paper"
                        }
                        settings.themeId = themeId
                        webView?.evaluateJavascript(
                            "GnReader.setTheme('$themeId', ${fontSize.toInt()});",
                            null,
                        )
                    }) { Icon(Icons.Default.Contrast, "主题") }

                    IconButton(onClick = { startOrStopTts() }) {
                        Icon(
                            when {
                                speaking -> Icons.Default.Stop
                                ttsPaused -> Icons.Default.PlayArrow
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            "朗读",
                            tint = if (speaking || ttsPaused) MoonBlue else Color.Unspecified,
                        )
                    }

                    IconButton(onClick = { showToc = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, "目录")
                    }

                    IconButton(onClick = {
                        pageMode = if (pageMode == "page") "scroll" else "page"
                        settings.pageMode = pageMode
                        pageIndex = 0
                        if (pageMode != "page") {
                            webView?.evaluateJavascript("GnReader.setPageMode('$pageMode');", null)
                        }
                        Toast.makeText(
                            context,
                            if (pageMode == "page") "分页模式（原生）" else "滚动模式",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }) { Icon(Icons.Default.AutoStories, "翻页模式") }

                    IconButton(onClick = {
                        showSearch = true
                        searchQuery = ""
                        searchHits = emptyList()
                    }) { Icon(Icons.Default.Search, "搜索") }

                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
                statusMsg?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        color = MoonBlue,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // 底栏信息条：菜单未呼出且未暂停朗读时显示
        if (!chromeVisible && !ttsPaused && settings.showInfoBar) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(infoBarHeight)
                    .background(Color(0xDDEEEAE4))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chTitle.ifBlank { book?.title.orEmpty() },
                    fontSize = 11.sp,
                    color = Color(0xFF555555),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "($pageLabel)",
                    fontSize = 11.sp,
                    color = Color(0xFF555555),
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Text(
                    String.format(Locale.getDefault(), "%.1f%%", progressPct),
                    fontSize = 11.sp,
                    color = Color(0xFF555555),
                )
            }
        }

        // 仅「暂停后」展示朗读菜单（类 Moon+ / 晋江）
        if (ttsPaused && !chromeVisible) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(Color(0xEE2A2A2A))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text("已暂停 · 点屏幕中间可继续", color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("语速 ${"%.1f".format(ttsRate)}x", color = Color.White, fontSize = 12.sp)
                    Row {
                        IconButton(onClick = {
                            ttsRate = (ttsRate - 0.1f).coerceIn(0.5f, 2.0f)
                            settings.ttsSpeechRate = ttsRate
                            tts.setSpeechRate(ttsRate)
                        }) { Icon(Icons.Default.Remove, "减速", tint = Color.White) }
                        IconButton(onClick = {
                            ttsRate = (ttsRate + 0.1f).coerceIn(0.5f, 2.0f)
                            settings.ttsSpeechRate = ttsRate
                            tts.setSpeechRate(ttsRate)
                        }) { Icon(Icons.Default.Add, "加速", tint = Color.White) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("语调 ${"%.1f".format(ttsPitch)}", color = Color.White, fontSize = 12.sp)
                    Row {
                        IconButton(onClick = {
                            ttsPitch = (ttsPitch - 0.1f).coerceIn(0.6f, 1.8f)
                            settings.ttsPitch = ttsPitch
                            tts.setPitch(ttsPitch)
                        }) { Icon(Icons.Default.Remove, "降调", tint = Color.White) }
                        IconButton(onClick = {
                            ttsPitch = (ttsPitch + 0.1f).coerceIn(0.6f, 1.8f)
                            settings.ttsPitch = ttsPitch
                            tts.setPitch(ttsPitch)
                        }) { Icon(Icons.Default.Add, "升调", tint = Color.White) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = {
                        val err = tts.resume()
                        if (err != null) Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }) { Text("继续", color = Color.White) }
                    TextButton(onClick = {
                        tts.stop()
                        ttsPaused = false
                        ttsUnits = emptyList()
                        ttsIndex = -1
                        statusMsg = null
                        webView?.evaluateJavascript(
                            "GnReader.clearTtsHighlight(); GnReader.setSpeaking(false); GnReader.setPaused(false);",
                            null,
                        )
                    }) { Text("退出朗读", color = Color.White) }
                }
            }
        }
    }

    // 触控屏蔽层：吞掉全部触摸；设置页叠在其上仍可用
    if (touchBlocked && !showSettings) {
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
                    .padding(top = 48.dp)
                    .background(Color(0xCC333333), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    if (showSettings) {
        ReaderSettingsOverlay(
            settings = settings,
            themeId = themeId,
            fontSize = fontSize,
            fontFamily = fontFamily,
            customTextColor = customTextColor,
            customBgColor = customBgColor,
            bgImagePath = bgImagePath,
            bgImageAlpha = bgImageAlpha,
            pageMode = pageMode,
            ttsMode = ttsMode,
            ttsRate = ttsRate,
            ttsPitch = ttsPitch,
            ttsHighlightMode = ttsHighlightMode,
            ttsHighlightColor = ttsHighlightColor,
            showParaComments = showParaComments,
            showChapterComments = showChapterComments,
            paraCommentStyle = paraCommentStyle,
            commentListStyle = commentListStyle,
            marginLeft = marginLeft,
            marginRight = marginRight,
            marginTop = marginTop,
            marginBottom = marginBottom,
            lineMult = lineMult,
            showStatusBar = showStatusBar,
            showNavigationBar = showNavigationBar,
            onTheme = {
                themeId = it
                settings.themeId = it
                webView?.evaluateJavascript("GnReader.setTheme('$it', ${fontSize.toInt()});", null)
            },
            onFontSize = {
                fontSize = it
                settings.fontSizeSp = it
                webView?.evaluateJavascript("GnReader.setTheme('$themeId', ${it.toInt()});", null)
                syncViewportToWeb()
            },
            onFontFamily = {
                fontFamily = it
                settings.fontFamily = it
            },
            onCustomTextColor = {
                customTextColor = it
                settings.customTextColor = it
            },
            onCustomBgColor = {
                customBgColor = it
                settings.customBgColor = it
            },
            onBgImagePath = {
                bgImagePath = it
                settings.bgImagePath = it
            },
            onBgImageAlpha = {
                bgImageAlpha = it
                settings.bgImageAlpha = it
            },
            onPageMode = {
                pageMode = it
                settings.pageMode = it
                pageIndex = 0
                if (it != "page") {
                    webView?.evaluateJavascript("GnReader.setPageMode('$it');", null)
                }
            },
            onTtsMode = {
                ttsMode = it
                settings.ttsMode = it
            },
            onTtsRate = {
                ttsRate = it
                settings.ttsSpeechRate = it
                tts.setSpeechRate(it)
            },
            onTtsPitch = {
                ttsPitch = it
                settings.ttsPitch = it
                tts.setPitch(it)
            },
            onTtsHighlightMode = {
                ttsHighlightMode = it
                settings.ttsHighlightMode = it
            },
            onTtsHighlightColor = {
                ttsHighlightColor = it
                settings.ttsHighlightColor = it
            },
            onShowPara = {
                showParaComments = it
                settings.showParagraphComments = it
                webView?.evaluateJavascript(
                    "GnReader.setCommentDisplay($showParaComments, $showChapterComments);",
                    null,
                )
            },
            onShowChapter = {
                showChapterComments = it
                settings.showChapterComments = it
                webView?.evaluateJavascript(
                    "GnReader.setCommentDisplay($showParaComments, $showChapterComments);",
                    null,
                )
            },
            onParaCommentStyle = {
                paraCommentStyle = it
                settings.paraCommentStyle = it
            },
            onCommentListStyle = {
                commentListStyle = it
                settings.commentListStyle = it
            },
            onMarginLeft = {
                marginLeft = it
                settings.pageMarginLeft = it
                webView?.evaluateJavascript(
                    "GnReader.setLayout($marginLeft,$marginTop,$lineMult,$marginLeft,$marginRight,$marginTop,$marginBottom);",
                    null,
                )
                syncViewportToWeb()
            },
            onMarginRight = {
                marginRight = it
                settings.pageMarginRight = it
                webView?.evaluateJavascript(
                    "GnReader.setLayout($marginLeft,$marginTop,$lineMult,$marginLeft,$marginRight,$marginTop,$marginBottom);",
                    null,
                )
                syncViewportToWeb()
            },
            onMarginTop = {
                marginTop = it
                settings.pageMarginTop = it
                webView?.evaluateJavascript(
                    "GnReader.setLayout($marginLeft,$marginTop,$lineMult,$marginLeft,$marginRight,$marginTop,$marginBottom);",
                    null,
                )
                syncViewportToWeb()
            },
            onMarginBottom = {
                marginBottom = it
                settings.pageMarginBottom = it
                webView?.evaluateJavascript(
                    "GnReader.setLayout($marginLeft,$marginTop,$lineMult,$marginLeft,$marginRight,$marginTop,$marginBottom);",
                    null,
                )
                syncViewportToWeb()
            },
            onLineMult = {
                lineMult = it
                settings.lineHeightMult = it
                webView?.evaluateJavascript(
                    "GnReader.setLayout($marginLeft,$marginTop,$lineMult,$marginLeft,$marginRight,$marginTop,$marginBottom);",
                    null,
                )
                syncViewportToWeb()
            },
            onShowStatusBar = {
                showStatusBar = it
                settings.showStatusBar = it
            },
            onShowNavigationBar = {
                showNavigationBar = it
                settings.showNavigationBar = it
            },
            onStartTts = {
                showSettings = false
                startOrStopTts()
            },
            onOpenSystemTts = { TtsController.openTtsSettings(context) },
            onPickBgImage = { pickBgImage.launch("image/*") },
            onDismiss = { showSettings = false },
        )
    }

    if (showBookInfo) {
        val f = remember(path) { File(path) }
        val sizeLabel = remember(path) {
            val s = f.length()
            when {
                s < 1024 -> "$s B"
                s < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", s / 1024.0)
                else -> String.format(Locale.US, "%.2f MB", s / (1024.0 * 1024.0))
            }
        }
        AlertDialog(
            onDismissRequest = { showBookInfo = false },
            title = { Text(book?.title ?: f.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine("作者", book?.author?.ifBlank { "—" } ?: "—")
                    InfoLine("路径", path)
                    InfoLine("来源", book?.sourceUrl?.ifBlank { "—" } ?: "—")
                    InfoLine("章节", "${book?.chapters?.size ?: 0}")
                    InfoLine("大小", sizeLabel)
                    if (f.lastModified() > 0) {
                        InfoLine(
                            "修改",
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(f.lastModified())),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookInfo = false }) { Text("关闭") }
            },
        )
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("书内搜索") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        label = { Text("关键词") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        val q = searchQuery.trim()
                        if (q.isEmpty()) {
                            searchHits = emptyList()
                            return@TextButton
                        }
                        val b = book
                        if (b == null) return@TextButton
                        val list = mutableListOf<Triple<Int, String, String>>()
                        b.chapters.forEachIndexed { ci, ch ->
                            ch.paragraphs.forEach { p ->
                                if (p.text.contains(q, ignoreCase = true)) {
                                    val snippet = p.text.replace("\n", " ").let { t ->
                                        val i = t.indexOf(q, ignoreCase = true).coerceAtLeast(0)
                                        val from = (i - 12).coerceAtLeast(0)
                                        val to = (i + q.length + 24).coerceAtMost(t.length)
                                        "${ch.title} · ${t.substring(from, to)}"
                                    }
                                    list += Triple(ci, p.id, snippet)
                                }
                            }
                        }
                        searchHits = list.take(80)
                    }) { Text("搜索") }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(searchHits) { hit ->
                            val ch = hit.first
                            val pid = hit.second
                            val snip = hit.third
                            Text(
                                snip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSearch = false
                                        chromeVisible = false
                                        chapterIndex = ch
                                        pageIndex = 0
                                        if (pageMode == "page") {
                                            jumpParagraphId = pid
                                        } else {
                                            webView?.evaluateJavascript(
                                                "GnReader.goChapterPid($ch, ${JSONObject.quote(pid)});",
                                                null,
                                            )
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                fontSize = 13.sp,
                            )
                            HorizontalDivider()
                        }
                    }
                    if (searchHits.isEmpty() && searchQuery.isNotBlank()) {
                        Text("无结果", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearch = false }) { Text("关闭") }
            },
        )
    }

    if (sheetComments != null) {
        ModalBottomSheet(onDismissRequest = { sheetComments = null }) {
            Text(sheetTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                items(sheetComments.orEmpty()) { c ->
                    CommentCard(c, compact = commentListStyle == "compact")
                    HorizontalDivider()
                }
                if (sheetComments.isNullOrEmpty()) {
                    item { Text("暂无评论", modifier = Modifier.padding(16.dp)) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showToc) {
        AlertDialog(
            onDismissRequest = { showToc = false },
            title = { Text("目录（可调序 / 删分章）") },
            text = {
                val chapters = book?.chapters.orEmpty()
                val canEdit = path.lowercase().let {
                    it.endsWith(".gnbook") || it.endsWith(".epub")
                }
                LazyColumn {
                    items(chapters.size) { i ->
                        val ch = chapters[i]
                        val label = when {
                            ch.kind == "egg" && !ch.title.contains("彩蛋") -> "彩蛋 · ${ch.title}"
                            else -> ch.title
                        }
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        chapterIndex = i
                                        pageIndex = 0
                                        if (pageMode != "page") {
                                            webView?.evaluateJavascript("GnReader.goChapter($i);", null)
                                        }
                                        showToc = false
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("${i + 1}. $label", maxLines = 2) }
                                IconButton(
                                    onClick = {
                                        if (i <= 0) return@IconButton
                                        reorderChapter(book, path, i, i - 1) { newBook, newIdx ->
                                            book = newBook
                                            chapterIndex = newIdx
                                            pageIndex = 0
                                            Toast.makeText(context, "已上移并保存", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = i > 0 && canEdit,
                                ) { Icon(Icons.Default.KeyboardArrowUp, "上移") }
                                IconButton(
                                    onClick = {
                                        if (i >= chapters.lastIndex) return@IconButton
                                        reorderChapter(book, path, i, i + 1) { newBook, newIdx ->
                                            book = newBook
                                            chapterIndex = newIdx
                                            pageIndex = 0
                                            Toast.makeText(context, "已下移并保存", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = i < chapters.lastIndex && canEdit,
                                ) { Icon(Icons.Default.KeyboardArrowDown, "下移") }
                            }
                            if (i > 0 && canEdit) {
                                TextButton(
                                    onClick = {
                                        val okIdx = GnbookWriter.mergeChapterIntoPrevious(path, i)
                                        if (okIdx < 0) {
                                            Toast.makeText(context, "删除分章失败", Toast.LENGTH_SHORT).show()
                                            return@TextButton
                                        }
                                        try {
                                            val reloaded = GnbookParser.parseFile(path, context.cacheDir)
                                            book = reloaded
                                            chapterIndex = okIdx.coerceIn(0, reloaded.chapters.lastIndex.coerceAtLeast(0))
                                            pageIndex = 0
                                            Toast.makeText(
                                                context,
                                                "已删除分章，内容并入上一章",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message ?: "重载失败", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                ) {
                                    Text(
                                        if (ch.kind == "egg") "删除分章（彩蛋并入上一章）"
                                        else "删除分章（并入上一章）",
                                        fontSize = 12.sp,
                                        color = MoonBlue,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showToc = false }) { Text("关闭") }
            },
        )
    }
}

private fun reorderChapter(
    book: GnBook?,
    path: String,
    from: Int,
    to: Int,
    onDone: (GnBook, Int) -> Unit,
) {
    val b = book ?: return
    if (from !in b.chapters.indices || to !in b.chapters.indices) return
    val mutable = b.chapters.toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    val newBook = b.copy(chapters = mutable)
    // 先更新内存，再异步写回
    onDone(newBook, to)
    Thread {
        try {
            GnbookWriter.reorderChapters(path, mutable.map { it.id })
        } catch (_: Exception) {
        }
    }.start()
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp)
    }
}

@Composable
private fun CommentCard(c: GnComment, compact: Boolean = false) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val pad = if (compact) 4.dp else 10.dp
    Column(Modifier.padding(vertical = pad)) {
        Text(c.author, style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall)
        if (!compact && c.commenterId.isNotBlank()) {
            Text("ID: ${c.commenterId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        val meta = buildString {
            c.publishTime?.let {
                val ts = if (it > 10_000_000_000L) it else it * 1000
                append(sdf.format(Date(ts)))
            }
            if (c.likeCount > 0) {
                if (isNotEmpty()) append(" · ")
                append("♥ ${c.likeCount}")
            }
            if (c.ipLocation.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(c.ipLocation)
            }
        }
        if (meta.isNotEmpty() && !compact) {
            Text(meta, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        if (c.quote.isNotBlank() && !compact) {
            Text("「${c.quote}」", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Text(c.content, style = MaterialTheme.typography.bodyMedium, fontSize = if (compact) 13.sp else 14.sp)
        c.l2.takeIf { it.isNotEmpty() }?.forEach { child ->
            Text(
                "↳ ${child.author}: ${child.content}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                color = Color(0xFF555555),
            )
        }
    }
}

private fun bookToJson(book: GnBook): JSONObject {
    val chapters = JSONArray()
    book.chapters.forEach { ch ->
        val paras = JSONArray()
        ch.paragraphs.forEach { p ->
            paras.put(
                JSONObject()
                    .put("id", p.id)
                    .put("html", p.html)
                    .put("text", p.text)
                    .put("empty", p.empty)
                    .put("hasImage", p.hasImage)
                    .put("commentCount", p.commentCount),
            )
        }
        val paraComments = JSONObject()
        ch.paragraphComments.forEach { (pid, list) ->
            paraComments.put(pid, JSONObject().put("comments", commentsToJson(list)).put("count", list.size))
        }
        chapters.put(
            JSONObject()
                .put("id", ch.id)
                .put("title", ch.title)
                .put("kind", ch.kind)
                .put("paragraphs", paras)
                .put("paragraphComments", paraComments)
                .put("hotComments", commentsToJson(ch.hotComments))
                .put("generalComments", commentsToJson(ch.generalComments)),
        )
    }
    return JSONObject()
        .put("title", book.title)
        .put("author", book.author)
        .put("sourceUrl", book.sourceUrl)
        .put("chapters", chapters)
}

private fun commentsToJson(list: List<GnComment>): JSONArray {
    val arr = JSONArray()
    list.forEach { c ->
        arr.put(
            JSONObject()
                .put("id", c.id)
                .put("author", c.author)
                .put("commenterId", c.commenterId)
                .put("blogName", c.blogName)
                .put("blogId", c.blogId)
                .put("blogHome", c.blogHome)
                .put("content", c.content)
                .put("publishTime", c.publishTime)
                .put("likeCount", c.likeCount)
                .put("ipLocation", c.ipLocation)
                .put("quote", c.quote)
                .put("l2Comments", commentsToJson(c.l2)),
        )
    }
    return arr
}
