package com.gnreader.app.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalContext
import com.gnreader.app.data.ReaderFonts
import com.gnreader.app.data.gnbook.GnBook
import com.gnreader.app.data.reader.AndroidPageLayoutEngine
import com.gnreader.app.data.reader.BlockKind
import com.gnreader.app.data.reader.PageContent
import com.gnreader.app.data.reader.PageLayoutMetrics
import com.gnreader.app.data.reader.blocksFromChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MoonBlue = Color(0xFF1976D2)

data class ReaderThemeColors(
    val background: Color,
    val text: Color,
    val meta: Color,
    val badgeBg: Color,
    val badgeFg: Color,
)

fun readerThemeColors(
    themeId: String,
    customText: String = "",
    customBg: String = "",
): ReaderThemeColors {
    val base = when (themeId) {
        "night" -> ReaderThemeColors(
            background = Color(0xFF121212),
            text = Color(0xFFE6E6E6),
            meta = Color(0xFFAAAAAA),
            badgeBg = Color(0xFF2A3A4A),
            badgeFg = Color(0xFF9EC0E6),
        )
        "green" -> ReaderThemeColors(
            background = Color(0xFFC7EDCC),
            text = Color(0xFF1A2A1A),
            meta = Color(0xFF4A5A4A),
            badgeBg = Color(0xFFE8F5E9),
            badgeFg = Color(0xFF2E7D32),
        )
        else -> ReaderThemeColors(
            background = Color(0xFFF7F4EE),
            text = Color(0xFF222222),
            meta = Color(0xFF666666),
            badgeBg = Color(0xFFF5F8FC),
            badgeFg = Color(0xFF4A668F),
        )
    }
    return base.copy(
        background = parseHexColor(customBg) ?: base.background,
        text = parseHexColor(customText) ?: base.text,
    )
}

private fun parseHexColor(hex: String): Color? {
    val h = hex.trim().removePrefix("#")
    if (h.length != 6) return null
    return try {
        Color(android.graphics.Color.parseColor("#$h"))
    } catch (_: Exception) {
        null
    }
}

private fun resolveFontFamily(id: String): FontFamily = when (id) {
    "times", "xin_song", "wenkai", "serif", "song", "kai", "fangsong" -> FontFamily.Serif
    "sans", "hei", "smiley" -> FontFamily.SansSerif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

data class NativeTtsUnit(
    val pid: String?,
    val text: String,
)

/**
 * Compose 原生分页阅读面（墨水屏友好：无翻页动画）。
 *
 * 每行 [PageLine] 必须以单行绘制（softWrap=false），否则会相对引擎二次折行出现吊字。
 */
@Composable
fun NativePagePager(
    book: GnBook,
    chapterIndex: Int,
    pageIndex: Int,
    fontSizeSp: Float,
    lineMult: Float,
    marginLeft: Int,
    marginRight: Int,
    marginTop: Int,
    marginBottom: Int,
    themeId: String,
    showParaComments: Boolean,
    showChapterComments: Boolean,
    fontFamily: String = "default",
    customTextColor: String = "",
    customBgColor: String = "",
    bgImagePath: String = "",
    bgImageAlpha: Float = 0.35f,
    paraCommentStyle: String = "bubble",
    jumpParagraphId: String? = null,
    onJumpConsumed: () -> Unit = {},
    /** 换章后是否打开到末页（往前翻跨章） */
    openChapterAtEnd: Boolean = false,
    onOpenChapterAtEndConsumed: () -> Unit = {},
    ttsActivePid: String? = null,
    ttsHighlightMode: String = "color",
    ttsHighlightColor: String = "#1565C0",
    onPageChange: (pageIndex: Int, pageCount: Int) -> Unit,
    /** openAtEnd=true：进入该章最后一页（上一章末） */
    onChapterChange: (chapterIndex: Int, openAtEnd: Boolean) -> Unit,
    onToggleChrome: () -> Unit,
    onCenterTap: () -> Boolean,
    onOpenParagraphComments: (chapterIndex: Int, pid: String) -> Unit,
    onOpenChapterComments: (chapterIndex: Int) -> Unit,
    onPagesReady: (List<PageContent>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = readerThemeColors(themeId, customTextColor, customBgColor)
    var bodyFont by remember { mutableStateOf(resolveFontFamily(fontFamily)) }
    var layoutTypeface by remember { mutableStateOf<Typeface?>(null) }
    var fontStatus by remember { mutableStateOf<String?>(null) }
    val engine = remember { AndroidPageLayoutEngine() }
    val density = LocalDensity.current
    var pages by remember { mutableStateOf<List<PageContent>>(emptyList()) }
    var localPage by remember { mutableIntStateOf(pageIndex) }
    var lastChapter by remember { mutableIntStateOf(chapterIndex) }
    var contentWpx by remember { mutableIntStateOf(0) }
    var contentHpx by remember { mutableIntStateOf(0) }
    val bgBitmap = remember(bgImagePath) {
        if (bgImagePath.isBlank()) null
        else runCatching { BitmapFactory.decodeFile(bgImagePath)?.asImageBitmap() }.getOrNull()
    }
    val hlColor = parseHexColor(ttsHighlightColor) ?: MoonBlue
    val markerBg = hlColor.copy(alpha = 0.45f)
    val tightStyle = remember {
        TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
    }

    LaunchedEffect(fontFamily) {
        fontStatus = if (fontFamily == "xin_song" && !ReaderFonts.isNotoReady(context)) {
            "正在准备新宋字体…"
        } else null
        val tf = withContext(Dispatchers.IO) {
            ReaderFonts.resolveTypeface(context, fontFamily) { msg -> fontStatus = msg }
        }
        layoutTypeface = tf
        bodyFont = ReaderFonts.composeFamily(tf)
        fontStatus = null
    }

    // 朗读段落换页：高亮目标不在当前页时跳转
    LaunchedEffect(ttsActivePid, pages) {
        val pid = ttsActivePid ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        val idx = pages.indexOfFirst { pid in it.blockIds }
        if (idx >= 0 && idx != localPage) {
            localPage = idx
            onPageChange(idx, pages.size.coerceAtLeast(1))
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .clipToBounds(),
    ) {
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = bgImageAlpha.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxSize(),
            )
        }
        val fontPx = with(density) { fontSizeSp.sp.toPx() }
        val titlePx = fontPx * 1.28f
        val bodyLh = fontPx * lineMult
        val titleLh = titlePx * (lineMult * 0.92f)
        val indentPx = fontPx * 2f
        // 回退：尚未测到内容区时用约束估算
        val fallbackW = with(density) {
            (maxWidth.toPx() - marginLeft.dp.toPx() - marginRight.dp.toPx()).coerceAtLeast(8f)
        }
        val fallbackH = with(density) {
            (maxHeight.toPx() - marginTop.dp.toPx() - marginBottom.dp.toPx()).coerceAtLeast(8f)
        }
        val contentW = if (contentWpx > 8) contentWpx.toFloat() else fallbackW
        val contentH = if (contentHpx > 8) contentHpx.toFloat() else fallbackH

        LaunchedEffect(
            book,
            chapterIndex,
            fontSizeSp,
            lineMult,
            marginLeft,
            marginRight,
            marginTop,
            marginBottom,
            showParaComments,
            fontFamily,
            layoutTypeface,
            contentW,
            contentH,
            openChapterAtEnd,
        ) {
            if (contentW < 32f || contentH < bodyLh * 3f) return@LaunchedEffect
            val ch = book.chapters.getOrNull(chapterIndex) ?: return@LaunchedEffect
            val blocks = blocksFromChapter(
                title = ch.title.ifBlank { book.title },
                author = book.author,
                paragraphs = ch.paragraphs,
                showParaComments = showParaComments,
            )
            val metrics = PageLayoutMetrics(
                contentWidthPx = contentW,
                contentHeightPx = contentH,
                bodyFontSizePx = fontPx,
                titleFontSizePx = titlePx,
                bodyLineHeightPx = bodyLh,
                titleLineHeightPx = titleLh,
                firstLineIndentPx = indentPx,
                badgeGutterPx = 0f,
                fontFamilyId = fontFamily,
                typeface = layoutTypeface,
            )
            val newPages = engine.paginate(blocks, metrics)
            // 同章重排时用当前页首段锚定，避免轻微改尺寸把页码打回 0
            val anchorPid = pages.getOrNull(localPage)?.blockIds?.firstOrNull()
            pages = newPages
            onPagesReady(newPages)
            if (chapterIndex != lastChapter) {
                lastChapter = chapterIndex
                localPage = if (openChapterAtEnd) {
                    (newPages.size - 1).coerceAtLeast(0)
                } else {
                    0
                }
                if (openChapterAtEnd) onOpenChapterAtEndConsumed()
            } else {
                val byAnchor = anchorPid?.let { pid ->
                    newPages.indexOfFirst { pid in it.blockIds }
                } ?: -1
                localPage = when {
                    byAnchor >= 0 -> byAnchor
                    else -> localPage.coerceIn(0, (newPages.size - 1).coerceAtLeast(0))
                }
            }
            onPageChange(localPage, newPages.size.coerceAtLeast(1))
        }

        LaunchedEffect(jumpParagraphId, pages) {
            val pid = jumpParagraphId ?: return@LaunchedEffect
            if (pages.isEmpty()) return@LaunchedEffect
            val idx = pages.indexOfFirst { pid in it.blockIds }
            if (idx >= 0) {
                localPage = idx
                onPageChange(idx, pages.size.coerceAtLeast(1))
            }
            onJumpConsumed()
        }

        LaunchedEffect(pageIndex, pages) {
            if (pages.isEmpty()) return@LaunchedEffect
            if (pageIndex >= pages.size) {
                // 跨章到末页的哨兵值
                val last = pages.lastIndex.coerceAtLeast(0)
                if (localPage != last) {
                    localPage = last
                    onPageChange(last, pages.size.coerceAtLeast(1))
                }
            } else if (pageIndex != localPage && pageIndex in pages.indices) {
                localPage = pageIndex
            }
        }

        val page = pages.getOrNull(localPage)
        val lineHeightSp = (fontSizeSp * lineMult).sp
        val lineHeightDp = with(density) { bodyLh.toDp() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(pages, localPage, chapterIndex, book.chapters.size) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val xRatio = offset.x / w
                        when {
                            xRatio < 0.28f -> {
                                if (localPage > 0) {
                                    localPage -= 1
                                    onPageChange(localPage, pages.size.coerceAtLeast(1))
                                } else if (chapterIndex > 0) {
                                    // 跨到上一章末页，而不是章首
                                    onChapterChange(chapterIndex - 1, true)
                                } else {
                                    onToggleChrome()
                                }
                            }
                            xRatio > 0.72f -> {
                                if (localPage < pages.lastIndex) {
                                    localPage += 1
                                    onPageChange(localPage, pages.size.coerceAtLeast(1))
                                } else if (chapterIndex < book.chapters.lastIndex) {
                                    onChapterChange(chapterIndex + 1, false)
                                } else {
                                    onToggleChrome()
                                }
                            }
                            else -> {
                                if (!onCenterTap()) onToggleChrome()
                            }
                        }
                    }
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = marginLeft.dp,
                        end = marginRight.dp,
                        top = marginTop.dp,
                        bottom = marginBottom.dp,
                    )
                    .onSizeChanged {
                        // 忽略 1～2px 抖动，防止重分页把阅读位置打乱
                        if (kotlin.math.abs(it.width - contentWpx) > 2) contentWpx = it.width
                        if (kotlin.math.abs(it.height - contentHpx) > 2) contentHpx = it.height
                    }
                    .clipToBounds(),
            ) {
                if (page != null) {
                    for (line in page.lines) {
                        val isTts = ttsActivePid != null && line.blockId == ttsActivePid
                        val lineFg = when {
                            isTts && ttsHighlightMode != "marker" -> hlColor
                            else -> colors.text
                        }
                        val lineBg = when {
                            isTts && ttsHighlightMode == "marker" -> markerBg
                            isTts && ttsHighlightMode != "marker" -> hlColor.copy(alpha = 0.12f)
                            else -> Color.Transparent
                        }
                        when (line.kind) {
                            BlockKind.TITLE -> {
                                Text(
                                    line.text,
                                    color = colors.text,
                                    fontSize = (fontSizeSp * 1.28f).sp,
                                    fontFamily = bodyFont,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Ellipsis,
                                    style = tightStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(lineHeightDp * 2),
                                )
                            }
                            BlockKind.META -> {
                                Text(
                                    line.text,
                                    color = colors.meta,
                                    fontSize = (fontSizeSp * 0.92f).sp,
                                    fontFamily = bodyFont,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    style = tightStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(lineHeightDp),
                                )
                            }
                            BlockKind.EMPTY -> {
                                Spacer(Modifier.height(lineHeightDp * 0.45f))
                            }
                            BlockKind.IMAGE_PLACEHOLDER -> {
                                Text(
                                    line.text,
                                    color = colors.meta,
                                    fontSize = fontSizeSp.sp,
                                    fontFamily = bodyFont,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = tightStyle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(lineHeightDp * 2),
                                )
                            }
                            BlockKind.BODY -> {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(lineHeightDp)
                                        .background(lineBg),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = line.text.ifEmpty { " " },
                                        color = lineFg,
                                        fontSize = fontSizeSp.sp,
                                        fontFamily = bodyFont,
                                        lineHeight = lineHeightSp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Clip,
                                        style = tightStyle,
                                    )
                                    if (line.showBadge && line.badgeCount > 0 && line.blockId != null) {
                                        Text(
                                            text = line.badgeCount.toString(),
                                            color = if (isTts && ttsHighlightMode != "marker") hlColor else MoonBlue,
                                            fontSize = (fontSizeSp * 0.72f).sp,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .padding(start = 1.dp)
                                                .clickable {
                                                    onOpenParagraphComments(chapterIndex, line.blockId)
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showChapterComments) {
                    val ch = book.chapters.getOrNull(chapterIndex)
                    val n = (ch?.hotComments?.size ?: 0) + (ch?.generalComments?.size ?: 0)
                    if (n > 0 && localPage >= pages.lastIndex && pages.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "本章评论 ($n)",
                            color = MoonBlue,
                            fontSize = (fontSizeSp * 0.9f).sp,
                            modifier = Modifier.clickable { onOpenChapterComments(chapterIndex) },
                        )
                    }
                }
            }
        }

        if (fontStatus != null) {
            Text(
                fontStatus!!,
                color = MoonBlue,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .background(Color(0xCCFFFFFF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** 供朗读：按模式从章节/当前页抽取文本单元（含 pid 便于高亮）。 */
fun nativeTtsUnits(
    book: GnBook,
    chapterIndex: Int,
    pageIndex: Int,
    pages: List<PageContent>,
    mode: String,
): List<NativeTtsUnit> {
    val ch = book.chapters.getOrNull(chapterIndex) ?: return emptyList()
    return when (mode) {
        "chapter" -> ch.paragraphs
            .filter { it.text.isNotBlank() }
            .map { NativeTtsUnit(it.id, it.text) }
        "page" -> {
            val ids = pages.getOrNull(pageIndex)?.blockIds.orEmpty()
            ch.paragraphs.filter { it.id in ids && it.text.isNotBlank() }
                .map { NativeTtsUnit(it.id, it.text) }
        }
        else -> {
            val ids = pages.getOrNull(pageIndex)?.blockIds.orEmpty()
            val start = ch.paragraphs.indexOfFirst { it.id in ids }.let { if (it < 0) 0 else it }
            ch.paragraphs.drop(start).filter { it.text.isNotBlank() }
                .map { NativeTtsUnit(it.id, it.text) }
        }
    }
}

fun nativeTtsTexts(
    book: GnBook,
    chapterIndex: Int,
    pageIndex: Int,
    pages: List<PageContent>,
    mode: String,
): List<String> = nativeTtsUnits(book, chapterIndex, pageIndex, pages, mode).map { it.text }
