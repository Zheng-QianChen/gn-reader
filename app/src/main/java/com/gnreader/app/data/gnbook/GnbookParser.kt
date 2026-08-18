package com.gnreader.app.data.gnbook

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

data class GnComment(
    val id: String?,
    val author: String,
    val commenterId: String,
    val blogName: String,
    val blogId: String,
    val blogHome: String,
    val content: String,
    val publishTime: Long?,
    val likeCount: Int,
    val ipLocation: String,
    val quote: String,
    val l2: List<GnComment>,
)

data class GnParagraph(
    val id: String,
    val html: String,
    val text: String,
    val empty: Boolean,
    val hasImage: Boolean,
    val commentCount: Int,
)

data class GnChapter(
    val id: String,
    val title: String,
    val kind: String,
    val paragraphs: List<GnParagraph>,
    val paragraphComments: Map<String, List<GnComment>>,
    val hotComments: List<GnComment>,
    val generalComments: List<GnComment>,
)

data class GnBook(
    val title: String,
    val author: String,
    val sourceUrl: String,
    val chapters: List<GnChapter>,
    val extractDir: File?,
)

object GnbookParser {
    fun parseFile(path: String, cacheDir: File): GnBook {
        val file = File(path)
        return when {
            path.endsWith(".gnbook", true) -> parseGnbookZip(file, cacheDir)
            path.endsWith("reader_bundle.json", true) -> parseBundle(file)
            path.endsWith(".epub", true) -> {
                // 混合包优先：.epub 内嵌 gnbook.json 时启用段评
                if (zipHasEntry(file, "gnbook.json")) parseGnbookZip(file, cacheDir)
                else EpubParser.parse(file, cacheDir)
            }
            else -> error("不支持的格式: $path")
        }
    }

    private fun zipHasEntry(file: File, name: String): Boolean {
        return try {
            ZipFile(file).use { zip -> zip.getEntry(name) != null }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseGnbookZip(file: File, cacheDir: File): GnBook {
        val out = File(cacheDir, "gnbook_${file.nameWithoutExtension}_${file.length()}")
        if (!out.exists()) {
            out.mkdirs()
            ZipFile(file).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val dest = File(out, entry.name)
                    dest.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
        val jsonFile = File(out, "gnbook.json")
        require(jsonFile.isFile) { "缺少 gnbook.json" }
        val root = JSONObject(jsonFile.readText())
        return fromDocument(root, out)
    }

    private fun parseBundle(file: File): GnBook {
        val root = JSONObject(file.readText())
        // 最小转换
        val title = root.optString("title", "未命名")
        val author = root.optString("author", "")
        val contentHtml = root.optString("contentHtml", "")
        val paragraphs = htmlToParagraphs(contentHtml)
        val paraMap = mutableMapOf<String, List<GnComment>>()
        val pc = root.optJSONObject("paragraphComments")
        if (pc != null) {
            val keys = pc.keys()
            while (keys.hasNext()) {
                val pid = keys.next()
                val block = pc.getJSONObject(pid)
                paraMap[pid] = parseComments(block.optJSONArray("comments"))
            }
        }
        val chapter = GnChapter(
            id = "article",
            title = title,
            kind = "article",
            paragraphs = paragraphs.map { p ->
                p.copy(commentCount = paraMap[p.id]?.size ?: 0)
            },
            paragraphComments = paraMap,
            hotComments = parseComments(root.optJSONArray("hotComments")),
            generalComments = parseComments(root.optJSONArray("generalComments")),
        )
        return GnBook(title, author, root.optString("sourceUrl"), listOf(chapter), null)
    }

    private fun parseEpubFallback(file: File, cacheDir: File): GnBook {
        return EpubParser.parse(file, cacheDir)
    }

    /** 供 EpubParser 复用。 */
    fun htmlToParagraphsPublic(html: String): List<GnParagraph> = htmlToParagraphs(html)

    fun fromDocumentPublic(root: JSONObject, extractDir: File?): GnBook =
        fromDocument(root, extractDir ?: File(""))

    private fun fromDocument(root: JSONObject, extractDir: File): GnBook {
        val spine = root.optJSONArray("spine") ?: JSONArray()
        val chaptersObj = root.optJSONObject("chapters") ?: JSONObject()
        val chapters = mutableListOf<GnChapter>()
        for (i in 0 until spine.length()) {
            val item = spine.getJSONObject(i)
            val id = item.getString("id")
            val ch = chaptersObj.optJSONObject(id) ?: continue
            chapters += parseChapter(id, ch, item.optString("title"))
        }
        if (chapters.isEmpty()) {
            val keys = chaptersObj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                chapters += parseChapter(id, chaptersObj.getJSONObject(id), null)
            }
        }
        return GnBook(
            title = root.optString("title", "未命名"),
            author = root.optString("author", ""),
            sourceUrl = root.optString("sourceUrl", ""),
            chapters = chapters,
            extractDir = extractDir,
        )
    }

    private fun parseChapter(id: String, ch: JSONObject, spineTitle: String?): GnChapter {
        val parasArr = ch.optJSONArray("paragraphs") ?: JSONArray()
        val paraCommentsObj = ch.optJSONObject("paragraphComments") ?: JSONObject()
        val paraMap = mutableMapOf<String, List<GnComment>>()
        val keys = paraCommentsObj.keys()
        while (keys.hasNext()) {
            val pid = keys.next()
            val block = paraCommentsObj.getJSONObject(pid)
            paraMap[pid] = parseComments(block.optJSONArray("comments"))
        }
        val paragraphs = mutableListOf<GnParagraph>()
        for (i in 0 until parasArr.length()) {
            val p = parasArr.getJSONObject(i)
            val pid = p.optString("id", "p-$i")
            paragraphs += GnParagraph(
                id = pid,
                html = p.optString("html", ""),
                text = p.optString("text", ""),
                empty = p.optBoolean("empty", false),
                hasImage = p.optBoolean("hasImage", false),
                commentCount = paraMap[pid]?.size
                    ?: paraCommentsObj.optJSONObject(pid)?.optInt("count", 0)
                    ?: 0,
            )
        }
        // 仅当「整章只剩 1 段且过长」时按句号拆开（旧包/坏包）；
        // 已有多段的正文绝不再按逗号切碎，否则分页会像「一逗号一章」。
        val normalized = normalizeParagraphs(paragraphs, paraMap)
        val chapterComments = ch.optJSONObject("chapterComments") ?: JSONObject()
        return GnChapter(
            id = id,
            title = spineTitle ?: ch.optString("displayTitle", ch.optString("title", id)),
            kind = ch.optString("kind", "article"),
            paragraphs = normalized,
            paragraphComments = paraMap,
            hotComments = parseComments(chapterComments.optJSONArray("hot")),
            generalComments = parseComments(chapterComments.optJSONArray("general")),
        )
    }

    /**
     * 只修复「整章被塞进单一长段」的坏包。
     * 正常多段正文保持原样，交给分页引擎断行，绝不按逗号拆段。
     */
    private fun normalizeParagraphs(
        paragraphs: List<GnParagraph>,
        @Suppress("UNUSED_PARAMETER") paraMap: MutableMap<String, List<GnComment>>,
    ): List<GnParagraph> {
        if (paragraphs.size != 1) return paragraphs
        val only = paragraphs[0]
        if (only.hasImage || only.text.length <= 400) return paragraphs
        val parts = splitBySentences(only.text)
        if (parts.size <= 1) return paragraphs
        return parts.mapIndexed { idx, c ->
            val id = if (idx == 0) only.id else "${only.id}-$idx"
            GnParagraph(
                id = id,
                html = "<p id=\"$id\">$c</p>",
                text = c,
                empty = false,
                hasImage = false,
                commentCount = if (idx == 0) only.commentCount else 0,
            )
        }
    }

    private fun parseComments(arr: JSONArray?): List<GnComment> {
        if (arr == null) return emptyList()
        val out = mutableListOf<GnComment>()
        for (i in 0 until arr.length()) {
            val c = arr.optJSONObject(i) ?: continue
            out += parseComment(c)
        }
        return out
    }

    private fun parseComment(c: JSONObject): GnComment {
        val l2arr = c.optJSONArray("l2Comments")
        val l2 = mutableListOf<GnComment>()
        if (l2arr != null) {
            for (i in 0 until l2arr.length()) {
                l2arr.optJSONObject(i)?.let { l2 += parseComment(it) }
            }
        }
        val blogName = c.optString("blogName", "")
        val blogId = c.opt("blogId")?.toString()?.takeIf { it != "null" } ?: ""
        val commenterId = c.optString("commenterId").ifBlank {
            blogName.ifBlank { blogId }
        }
        return GnComment(
            id = c.opt("id")?.toString(),
            author = c.optString("author", "匿名"),
            commenterId = commenterId,
            blogName = blogName,
            blogId = blogId,
            blogHome = c.optString("blogHome", ""),
            content = c.optString("content", ""),
            publishTime = c.optLong("publishTime").takeIf { c.has("publishTime") && !c.isNull("publishTime") },
            likeCount = c.optInt("likeCount", 0),
            ipLocation = c.optString("ipLocation", ""),
            quote = c.optString("quote", ""),
            l2 = l2,
        )
    }

    private fun htmlToParagraphs(html: String): List<GnParagraph> {
        // 去掉 head/script/style；优先抽叶子块，避免外层 <div> 把整章吞成一段
        var body = html
            .replace(Regex("(?is)<script\\b[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style\\b[^>]*>.*?</style>"), "")
            .replace(Regex("(?is)<head\\b[^>]*>.*?</head>"), "")
        Regex("(?is)<body\\b[^>]*>(.*?)</body>").find(body)?.let {
            body = it.groupValues[1]
        }
        val list = mutableListOf<GnParagraph>()
        var autoId = 0
        fun nextId(preferred: String?): String =
            preferred?.takeIf { it.isNotBlank() } ?: "auto-${++autoId}"

        fun addPara(id: String, innerHtml: String, text: String, hasImg: Boolean) {
            if (text.isEmpty() && !hasImg) return
            list += GnParagraph(
                id = id,
                html = "<p id=\"$id\">$innerHtml</p>",
                text = text,
                empty = text.isEmpty() && !hasImg,
                hasImage = hasImg,
                commentCount = 0,
            )
        }

        fun plain(s: String): String =
            s.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

        // 1) 叶子块：p / 标题 / li（不吃外层容器）
        val leafRe = Regex("(?is)<(p|h[1-6]|li)\\b([^>]*)>(.*?)</\\1>")
        for (m in leafRe.findAll(body)) {
            val attrs = m.groupValues[2]
            val inner = m.groupValues[3]
            // 跳过内部仍嵌套同级块的畸形标签
            if (Regex("(?is)<(p|h[1-6]|li)\\b").containsMatchIn(inner)) continue
            val text = plain(inner)
            val hasImg = inner.contains("<img", true)
            val idMatch = Regex("""id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)
            addPara(nextId(idMatch?.groupValues?.get(1)), inner, text, hasImg)
        }

        // 2) 无 <p> 时：取不含嵌套块的简单 div/section/article
        if (list.isEmpty()) {
            val simpleBlockRe =
                Regex("(?is)<(div|section|article)\\b([^>]*)>((?:(?!</?(?:div|section|article|p|h[1-6]|li)\\b).)*)</\\1>")
            for (m in simpleBlockRe.findAll(body)) {
                val attrs = m.groupValues[2]
                var inner = m.groupValues[3]
                // 双 <br> 再切段
                val brChunks = inner.split(Regex("(?is)<br\\s*/?>\\s*<br\\s*/?>"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (brChunks.size > 1) {
                    brChunks.forEach { chunk ->
                        val text = plain(chunk)
                        addPara(nextId(null), chunk, text, chunk.contains("<img", true))
                    }
                } else {
                    val text = plain(inner)
                    val idMatch = Regex("""id\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)
                    addPara(nextId(idMatch?.groupValues?.get(1)), inner, text, inner.contains("<img", true))
                }
            }
        }

        // 3) 仍空：按 br / 换行 / 句号切开，绝不整章一段
        if (list.isEmpty() && body.isNotBlank()) {
            var text = plain(body.replace(Regex("(?is)<br\\s*/?>"), "\n"))
            val chunks = when {
                text.contains('\n') ->
                    text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
                else ->
                    splitBySentences(text)
            }
            if (chunks.size > 1) {
                chunks.forEachIndexed { idx, c ->
                    addPara("p-$idx", c, c, false)
                }
            } else if (text.isNotEmpty()) {
                // 超长单段再按句切
                val finer = splitBySentences(text)
                if (finer.size > 1) {
                    finer.forEachIndexed { idx, c -> addPara("p-$idx", c, c, false) }
                } else {
                    addPara(
                        "p-1",
                        body,
                        text,
                        body.contains("<img", true),
                    )
                }
            }
        }

        // 4) 若只解析出 1 段且过长，再按句号拆开（常见于被包进单个 <p> 的整章）
        if (list.size == 1 && list[0].text.length > 120 && !list[0].hasImage) {
            val finer = splitBySentences(list[0].text)
            if (finer.size > 1) {
                list.clear()
                finer.forEachIndexed { idx, c ->
                    addPara("p-$idx", c, c, false)
                }
            }
        }
        return list
    }

    /**
     * 按中文句末标点切段，供坏包兜底。
     * 注意：不要用逗号/顿号作为段边界，否则正文会被切成「一逗号一段」。
     */
    private fun splitBySentences(text: String, softMax: Int = 240): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val byPunct = trimmed.split(Regex("(?<=[。！？!?；;\\n])"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (byPunct.size > 1) return byPunct
        if (trimmed.length <= softMax) return listOf(trimmed)
        // 仍无句号：按长度硬切，优先在空白处断开（不用逗号）
        val out = mutableListOf<String>()
        var i = 0
        while (i < trimmed.length) {
            var end = (i + softMax).coerceAtMost(trimmed.length)
            if (end < trimmed.length) {
                val slice = trimmed.substring(i, end)
                val cut = slice.indexOfLast { it == ' ' || it == '\u3000' || it == '\n' }
                if (cut > softMax / 3) end = i + cut + 1
            }
            val part = trimmed.substring(i, end).trim()
            if (part.isNotEmpty()) out += part
            i = end
        }
        return out.ifEmpty { listOf(trimmed) }
    }
}