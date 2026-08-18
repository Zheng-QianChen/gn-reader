package com.gnreader.app.data.reader

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import kotlin.math.max

/**
 * Android 参考实现：量宽断行 + 按行数装箱。
 *
 * native-v3：
 * - 首行缩进写入全文「　　」，量宽与渲染用同一字符串，避免 padding 二次折行
 * - 量宽留安全边距，避免 Compose 字体略宽时吊字
 * - 右侧预留段评徽章槽位
 */
class AndroidPageLayoutEngine : PageLayoutEngine {

    override fun paginate(blocks: List<ContentBlock>, metrics: PageLayoutMetrics): List<PageContent> {
        if (metrics.contentWidthPx < 32f || metrics.contentHeightPx < metrics.bodyLineHeightPx * 3f) {
            return listOf(PageContent(emptyList(), emptySet()))
        }
        val lines = mutableListOf<PageLine>()
        for (block in blocks) {
            lines += breakBlock(block, metrics)
        }
        if (lines.isEmpty()) {
            return listOf(PageContent(emptyList(), emptySet()))
        }
        return packPagesByLineCount(lines, metrics)
    }

    private fun breakBlock(block: ContentBlock, metrics: PageLayoutMetrics): List<PageLine> {
        when (block.kind) {
            BlockKind.EMPTY -> return listOf(PageLine(block.id, "", BlockKind.EMPTY))
            BlockKind.IMAGE_PLACEHOLDER -> return listOf(
                PageLine(block.id, block.text.ifBlank { "[图片]" }, BlockKind.IMAGE_PLACEHOLDER),
                PageLine(block.id, "", BlockKind.EMPTY),
            )
            else -> Unit
        }
        val raw = block.text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (raw.isBlank()) return emptyList()

        val isTitle = block.kind == BlockKind.TITLE
        val fontPx = if (isTitle) metrics.titleFontSizePx else metrics.bodyFontSizePx
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontPx
            typeface = metrics.typeface ?: when (metrics.fontFamilyId) {
                "times", "xin_song", "wenkai", "serif", "song", "kai", "fangsong" -> Typeface.SERIF
                "sans", "hei", "smiley" -> Typeface.SANS_SERIF
                "mono" -> Typeface.MONOSPACE
                else -> Typeface.DEFAULT
            }
            isFakeBoldText = isTitle || block.kind == BlockKind.META
        }
        // 安全边距：Compose Text 度量常比 TextPaint 略宽，否则会出现行末单字掉到下一行
        val safety = max(8f, metrics.contentWidthPx * 0.03f)
        val gutter = metrics.badgeGutterPx.coerceAtLeast(0f)
        val width = max(32f, metrics.contentWidthPx - safety - gutter)
        val indentChars = if (block.kind == BlockKind.BODY && block.firstLineIndentEm > 0f) {
            "\u3000\u3000"
        } else {
            ""
        }
        val wrapped = wrapText(raw, paint, width, indentChars)
        if (wrapped.isEmpty()) {
            return listOf(PageLine(block.id, indentChars + raw, block.kind))
        }
        return wrapped.mapIndexed { i, lineText ->
            val isLast = i == wrapped.lastIndex
            PageLine(
                blockId = block.id,
                text = lineText,
                kind = block.kind,
                showBadge = isLast && block.commentCount > 0,
                badgeCount = if (isLast) block.commentCount else 0,
            )
        }
    }

    /**
     * 按像素宽度硬切；缩进字符直接拼进首行，不再用「缩小首行可用宽」。
     */
    private fun wrapText(
        text: String,
        paint: TextPaint,
        maxWidth: Float,
        firstLineIndent: String,
    ): List<String> {
        val result = ArrayList<String>()
        val paragraphs = text.split('\n')
        for ((pi, para) in paragraphs.withIndex()) {
            if (para.isEmpty()) {
                if (pi < paragraphs.lastIndex) result += ""
                continue
            }
            val full = firstLineIndent + para
            var start = 0
            while (start < full.length) {
                val avail = max(8f, maxWidth)
                var count = paint.breakText(full, start, full.length, true, avail, null)
                if (count <= 0) count = 1
                var end = (start + count).coerceAtMost(full.length)
                // 行末紧跟闭合标点时尽量并入本行
                if (end < full.length) {
                    val next = full[end]
                    if (next == '”' || next == '』' || next == '」' || next == '）' || next == ')' ||
                        next == '。' || next == '！' || next == '？' || next == '；' || next == '、'
                    ) {
                        val wider = paint.measureText(full, start, end + 1)
                        if (wider <= avail + 0.5f) end += 1
                    }
                }
                // 避免把 UTF-16 代理对拆开
                if (end < full.length && Character.isHighSurrogate(full[end - 1]) &&
                    Character.isLowSurrogate(full[end])
                ) {
                    end -= 1
                    if (end <= start) end = (start + 1).coerceAtMost(full.length)
                }
                result += full.substring(start, end)
                start = end
            }
        }
        return result
    }

    private fun packPagesByLineCount(lines: List<PageLine>, metrics: PageLayoutMetrics): List<PageContent> {
        val bodyH = metrics.bodyLineHeightPx.coerceAtLeast(1f)
        val titleH = metrics.titleLineHeightPx.coerceAtLeast(bodyH)
        // 与 NativePagePager 渲染高度对齐；正文按略紧行高装箱以吃掉浮点/字距误差造成的底部留白
        fun linePx(line: PageLine): Float = when (line.kind) {
            BlockKind.TITLE -> titleH.coerceAtLeast(bodyH * 2f)
            BlockKind.META -> bodyH
            BlockKind.IMAGE_PLACEHOLDER -> bodyH * 2f
            BlockKind.EMPTY -> bodyH * 0.45f
            else -> bodyH * 0.985f
        }

        // 允许轻微溢出（约 1/3 行），由 clipToBounds 裁切，避免整页底部大块空白
        val budget = metrics.contentHeightPx.coerceAtLeast(bodyH * 3f) + bodyH * 0.35f
        val pages = mutableListOf<PageContent>()
        var cur = mutableListOf<PageLine>()
        var used = 0f

        fun flush() {
            if (cur.isEmpty()) return
            pages += PageContent(cur.toList(), cur.mapNotNull { it.blockId }.toSet())
            cur = mutableListOf()
            used = 0f
        }

        for (line in lines) {
            val h = linePx(line)
            if (cur.isNotEmpty() && used + h > budget) {
                flush()
            }
            cur += line
            used += h
        }
        flush()
        if (pages.isEmpty()) pages += PageContent(emptyList(), emptySet())
        return pages
    }
}

/** 从章节段落构建分页输入块；合并 Lofter 空 <p> 间距。 */
fun blocksFromChapter(
    title: String,
    author: String,
    paragraphs: List<com.gnreader.app.data.gnbook.GnParagraph>,
    showParaComments: Boolean,
): List<ContentBlock> {
    val blocks = ArrayList<ContentBlock>()
    if (title.isNotBlank()) {
        blocks += ContentBlock(id = null, text = title, kind = BlockKind.TITLE)
    }
    if (author.isNotBlank()) {
        blocks += ContentBlock(id = null, text = author, kind = BlockKind.META)
    }
    var pendingGap = false
    for (p in paragraphs) {
        val blank = p.empty || p.text.isBlank()
        if (blank && !p.hasImage) {
            pendingGap = true
            continue
        }
        if (pendingGap && blocks.isNotEmpty() && blocks.last().kind == BlockKind.BODY) {
            blocks += ContentBlock(id = null, text = "", kind = BlockKind.EMPTY)
            pendingGap = false
        }
        when {
            p.hasImage && p.text.isBlank() -> blocks += ContentBlock(
                id = p.id,
                text = "[图片]",
                kind = BlockKind.IMAGE_PLACEHOLDER,
                commentCount = if (showParaComments) p.commentCount else 0,
            )
            else -> blocks += ContentBlock(
                id = p.id,
                text = p.text,
                kind = BlockKind.BODY,
                commentCount = if (showParaComments) p.commentCount else 0,
                firstLineIndentEm = 2f,
            )
        }
        pendingGap = false
    }
    return blocks
}
