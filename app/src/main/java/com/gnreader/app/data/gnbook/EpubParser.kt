package com.gnreader.app.data.gnbook

import java.io.File
import java.util.zip.ZipFile

/**
 * 按 EPUB 标准解析：container → OPF spine 顺序 + NCX/nav 标题。
 * AO3 等：优先 TOC 列出的正文，跳过封面/目录/版权等伪章节。
 */
object EpubParser {
    private val SKIP_HREF = Regex(
        """(^|/)(cover|titlepage|title[_-]?page|toc|contents|nav|copyright|imprint|""" +
            """frontmatter|backmatter|colophon|dedication|afterword|foreword|preface|""" +
            """acknowledg|about[_-]?author|bios?|dummy|landmarks|coverpage)(\.|_|-|$)""",
        RegexOption.IGNORE_CASE,
    )
    private val SKIP_TITLE = Regex(
        """^(cover|contents?|table of contents|toc|copyright|title page|front matter|""" +
            """back matter|notes?|author.?s? notes?|end notes?|works? inspired by|translations?)$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(file: File, cacheDir: File): GnBook {
        val out = File(cacheDir, "epub_${file.nameWithoutExtension}_${file.length()}")
        if (!out.exists() || out.list().isNullOrEmpty()) {
            out.mkdirs()
            ZipFile(file).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val name = entry.name.trimStart('/')
                    val dest = File(out, name)
                    dest.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }

        // 混合包：ZIP 内含 gnbook.json 时走完整段评模型
        val embedded = File(out, "gnbook.json")
        if (embedded.isFile) {
            return try {
                GnbookParser.fromDocumentPublic(org.json.JSONObject(embedded.readText()), out)
            } catch (_: Exception) {
                // fall through to spine parse
                parseSpine(file, out)
            }
        }
        return parseSpine(file, out)
    }

    private fun parseSpine(file: File, out: File): GnBook {
        val opfPath = findOpfPath(out)
            ?: return fallbackWalk(file, out)

        val opfFile = resolveUnder(out, opfPath)
        if (!opfFile.isFile) return fallbackWalk(file, out)
        val opfDir = opfFile.parentFile ?: out
        val opfXml = opfFile.readText()

        val meta = parseOpfMeta(opfXml)
        val manifest = parseOpfManifest(opfXml) // id -> href
        val spineIds = parseOpfSpine(opfXml)

        val titleByHref = linkedMapOf<String, String>()
        findNavHref(manifest, opfXml)?.let { navHref ->
            val navFile = resolveUnder(opfDir, navHref)
            if (navFile.isFile) titleByHref.putAll(parseNavTitles(navFile.readText(), navFile.parentFile ?: opfDir, opfDir))
        }
        findNcxHref(manifest, opfXml)?.let { ncxHref ->
            val ncxFile = resolveUnder(opfDir, ncxHref)
            if (ncxFile.isFile) {
                for ((href, title) in parseNcxTitles(ncxFile.readText())) {
                    titleByHref.putIfAbsent(normalizeHref(href), title)
                }
            }
        }

        val preferToc = titleByHref.size >= 1
        val chapters = mutableListOf<GnChapter>()
        val seenHref = linkedSetOf<String>()

        fun tryAdd(idx: Int, href: String, requireToc: Boolean) {
            val norm = normalizeHref(href)
            if (!seenHref.add(norm)) return
            val lower = norm.lowercase()
            if (lower.endsWith("nav.xhtml") || lower.endsWith("nav.html")) return
            if (SKIP_HREF.containsMatchIn(lower)) return
            val inToc = titleByHref.keys.any { tocMatch(it, norm) }
            if (requireToc && !inToc) return
            val chapFile = resolveUnder(opfDir, href)
            if (!chapFile.isFile) return
            val html = chapFile.readText()
            val title = titleByHref.entries.firstOrNull { tocMatch(it.key, norm) }?.value
                ?: extractHtmlTitle(html)
                ?: chapFile.nameWithoutExtension
            if (SKIP_TITLE.matches(title.trim())) return
            val paras = GnbookParser.htmlToParagraphsPublic(html)
            if (paras.isEmpty() && html.length < 80) return
            chapters += GnChapter(
                id = "spine-$idx",
                title = title,
                kind = "article",
                paragraphs = paras,
                paragraphComments = emptyMap(),
                hotComments = emptyList(),
                generalComments = emptyList(),
            )
        }

        // 先按 TOC 过滤（AO3：避免封面/目录/notes 各成一章）
        spineIds.forEachIndexed { idx, idref ->
            val href = manifest[idref] ?: return@forEachIndexed
            tryAdd(idx, href, requireToc = preferToc)
        }
        // TOC 过严导致空目录时回退：仅跳过伪章节
        if (chapters.isEmpty()) {
            seenHref.clear()
            spineIds.forEachIndexed { idx, idref ->
                val href = manifest[idref] ?: return@forEachIndexed
                tryAdd(idx, href, requireToc = false)
            }
        }

        if (chapters.isEmpty()) return fallbackWalk(file, out)

        return GnBook(
            title = meta.title.ifBlank { file.nameWithoutExtension },
            author = meta.author,
            sourceUrl = "",
            chapters = chapters,
            extractDir = out,
        )
    }

    private fun tocMatch(tocHref: String, spineHref: String): Boolean {
        val a = normalizeHref(tocHref).lowercase()
        val b = normalizeHref(spineHref).lowercase()
        if (a == b) return true
        if (a.endsWith(b) || b.endsWith(a)) return true
        val an = a.substringAfterLast('/')
        val bn = b.substringAfterLast('/')
        return an.isNotEmpty() && an == bn
    }

    private data class Meta(val title: String, val author: String)

    private fun findOpfPath(root: File): String? {
        val container = File(root, "META-INF/container.xml")
        if (!container.isFile) {
            // 少数破包：直接找 content.opf
            return root.walkTopDown().firstOrNull { it.name.equals("content.opf", true) || it.name.endsWith(".opf", true) }
                ?.relativeTo(root)?.path?.replace('\\', '/')
        }
        val xml = container.readText()
        val m = Regex(
            """full-path\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        ).find(xml)
        return m?.groupValues?.get(1)?.trim()?.trimStart('/')
    }

    private fun parseOpfMeta(xml: String): Meta {
        val title = Regex(
            """<dc:title[^>]*>(.*?)</dc:title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(xml)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
        val author = Regex(
            """<dc:creator[^>]*>(.*?)</dc:creator>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(xml)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
        return Meta(title, author)
    }

    private fun parseOpfManifest(xml: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        val itemRe = Regex(
            """<item\b([^>]+)/?>""",
            setOf(RegexOption.IGNORE_CASE),
        )
        for (m in itemRe.findAll(xml)) {
            val attrs = m.groupValues[1]
            val id = attr(attrs, "id") ?: continue
            val href = attr(attrs, "href") ?: continue
            map[id] = href
        }
        return map
    }

    private fun parseOpfSpine(xml: String): List<String> {
        val spineBlock = Regex(
            """<spine\b[^>]*>(.*?)</spine>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(xml)?.groupValues?.get(1) ?: return emptyList()
        val ids = mutableListOf<String>()
        val refRe = Regex("""<itemref\b([^>]+)/?>""", RegexOption.IGNORE_CASE)
        for (m in refRe.findAll(spineBlock)) {
            val idref = attr(m.groupValues[1], "idref") ?: continue
            val linear = attr(m.groupValues[1], "linear")
            if (linear != null && linear.equals("no", true)) continue
            ids += idref
        }
        return ids
    }

    private fun findNavHref(manifest: Map<String, String>, opfXml: String): String? {
        // properties="nav"
        val itemRe = Regex("""<item\b([^>]+)/?>""", RegexOption.IGNORE_CASE)
        for (m in itemRe.findAll(opfXml)) {
            val attrs = m.groupValues[1]
            val props = attr(attrs, "properties").orEmpty()
            if (props.split(Regex("\\s+")).any { it.equals("nav", true) }) {
                return attr(attrs, "href")
            }
        }
        return manifest.values.firstOrNull { it.contains("nav.", ignoreCase = true) }
    }

    private fun findNcxHref(manifest: Map<String, String>, opfXml: String): String? {
        val itemRe = Regex("""<item\b([^>]+)/?>""", RegexOption.IGNORE_CASE)
        for (m in itemRe.findAll(opfXml)) {
            val attrs = m.groupValues[1]
            val media = attr(attrs, "media-type").orEmpty()
            if (media.contains("ncx", true)) return attr(attrs, "href")
        }
        return manifest.values.firstOrNull { it.endsWith(".ncx", true) }
    }

    private fun parseNavTitles(navHtml: String, navDir: File, opfDir: File): Map<String, String> {
        val map = linkedMapOf<String, String>()
        // <a href="...">title</a>
        val re = Regex(
            """<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        for (m in re.findAll(navHtml)) {
            val hrefRaw = m.groupValues[1].substringBefore('#').trim()
            if (hrefRaw.isEmpty() || hrefRaw.startsWith("http")) continue
            val title = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            if (title.isEmpty()) continue
            val abs = resolveUnder(navDir, hrefRaw)
            val rel = try {
                abs.relativeTo(opfDir).path.replace('\\', '/')
            } catch (_: Exception) {
                hrefRaw
            }
            map.putIfAbsent(normalizeHref(rel), title)
            map.putIfAbsent(normalizeHref(hrefRaw), title)
        }
        return map
    }

    private fun parseNcxTitles(ncxXml: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        // navPoint blocks
        val pointRe = Regex(
            """<navPoint\b[^>]*>(.*?)</navPoint>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        for (m in pointRe.findAll(ncxXml)) {
            val block = m.groupValues[1]
            val label = Regex(
                """<text[^>]*>(.*?)</text>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(block)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
            val src = Regex(
                """<content\b[^>]*src\s*=\s*["']([^"']+)["']""",
                RegexOption.IGNORE_CASE,
            ).find(block)?.groupValues?.get(1)?.substringBefore('#')?.trim().orEmpty()
            if (label.isNotEmpty() && src.isNotEmpty()) {
                map.putIfAbsent(normalizeHref(src), label)
            }
        }
        // flat fallback
        if (map.isEmpty()) {
            val flat = Regex(
                """<navLabel>.*?<text[^>]*>(.*?)</text>.*?</navLabel>.*?<content\b[^>]*src\s*=\s*["']([^"']+)["']""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            for (m in flat.findAll(ncxXml)) {
                val label = m.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
                val src = m.groupValues[2].substringBefore('#').trim()
                if (label.isNotEmpty() && src.isNotEmpty()) map.putIfAbsent(normalizeHref(src), label)
            }
        }
        return map
    }

    private fun extractHtmlTitle(html: String): String? {
        Regex(
            """<title[^>]*>(.*?)</title>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        Regex(
            """<h1[^>]*>(.*?)</h1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(html)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        return null
    }

    private fun fallbackWalk(file: File, out: File): GnBook {
        val chapters = out.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("xhtml", "html", "htm") }
            .filter { !it.name.contains("nav", true) && !it.path.contains("META-INF", true) }
            .sortedBy { it.path.lowercase() }
            .mapIndexed { idx, f ->
                val html = f.readText()
                GnChapter(
                    id = "ch-$idx",
                    title = extractHtmlTitle(html) ?: f.nameWithoutExtension,
                    kind = "article",
                    paragraphs = GnbookParser.htmlToParagraphsPublic(html),
                    paragraphComments = emptyMap(),
                    hotComments = emptyList(),
                    generalComments = emptyList(),
                )
            }.toList()
        return GnBook(
            title = file.nameWithoutExtension,
            author = "",
            sourceUrl = "",
            chapters = chapters.ifEmpty {
                listOf(GnChapter("article", file.nameWithoutExtension, "article", emptyList(), emptyMap(), emptyList(), emptyList()))
            },
            extractDir = out,
        )
    }

    private fun attr(attrs: String, name: String): String? {
        val re = Regex("""\b$name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        return re.find(attrs)?.groupValues?.get(1)
    }

    private fun normalizeHref(href: String): String =
        href.trim().trimStart('/').substringBefore('#').replace('\\', '/').replace(Regex("/{2,}"), "/")

    private fun resolveUnder(base: File, rel: String): File {
        val clean = rel.trim().trimStart('/').substringBefore('#')
        val f = File(base, clean)
        return if (f.exists()) f else File(base, clean.replace("%20", " "))
    }
}
