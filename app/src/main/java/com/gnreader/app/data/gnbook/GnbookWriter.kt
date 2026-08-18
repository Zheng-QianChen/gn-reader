package com.gnreader.app.data.gnbook

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 写回 .gnbook / 混合 .epub：调序、删除分章（并入上一章）。
 */
object GnbookWriter {
    fun reorderChapters(path: String, orderedIds: List<String>): Boolean {
        return mutateGnbook(path) { root ->
            val chaptersObj = root.optJSONObject("chapters") ?: return@mutateGnbook false
            val oldSpine = root.optJSONArray("spine") ?: JSONArray()
            val byId = linkedMapOf<String, JSONObject>()
            for (i in 0 until oldSpine.length()) {
                val item = oldSpine.optJSONObject(i) ?: continue
                byId[item.optString("id")] = item
            }
            val newSpine = JSONArray()
            val newChapters = JSONObject()
            for (id in orderedIds) {
                val ch = chaptersObj.optJSONObject(id) ?: continue
                newChapters.put(id, ch)
                val spineItem = byId[id] ?: JSONObject()
                    .put("id", id)
                    .put("title", ch.optString("displayTitle", ch.optString("title", id)))
                    .put("kind", ch.optString("kind", "article"))
                    .put("href", "OEBPS/chapters/$id.xhtml")
                newSpine.put(spineItem)
            }
            val keys = chaptersObj.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                if (newChapters.has(id)) continue
                newChapters.put(id, chaptersObj.getJSONObject(id))
                newSpine.put(
                    byId[id] ?: JSONObject()
                        .put("id", id)
                        .put("title", id)
                        .put("kind", "article")
                        .put("href", "OEBPS/chapters/$id.xhtml"),
                )
            }
            root.put("spine", newSpine)
            root.put("chapters", newChapters)
            val stats = root.optJSONObject("stats") ?: JSONObject()
            stats.put("chapterCount", newSpine.length())
            stats.put("eggCount", countKind(newChapters, "egg"))
            root.put("stats", stats)
            MutateResult(root, emptySet(), emptyMap())
        }
    }

    /**
     * 删除 [chapterIndex] 处分章：将该章正文并入上一章，并去掉独立目录项。
     * 典型用途：把「彩蛋」章重新并回上一正文。
     * @return 并入后的上一章下标；失败返回 -1
     */
    fun mergeChapterIntoPrevious(path: String, chapterIndex: Int): Int {
        var resultIndex = -1
        val ok = mutateGnbook(path) { root ->
            val chaptersObj = root.optJSONObject("chapters") ?: return@mutateGnbook false
            val spine = root.optJSONArray("spine") ?: return@mutateGnbook false
            if (chapterIndex <= 0 || chapterIndex >= spine.length()) return@mutateGnbook false
            val prevItem = spine.optJSONObject(chapterIndex - 1) ?: return@mutateGnbook false
            val curItem = spine.optJSONObject(chapterIndex) ?: return@mutateGnbook false
            val prevId = prevItem.optString("id")
            val curId = curItem.optString("id")
            val prevCh = chaptersObj.optJSONObject(prevId) ?: return@mutateGnbook false
            val curCh = chaptersObj.optJSONObject(curId) ?: return@mutateGnbook false

            // 段落拼接：中间加空段作分隔，并标注原彩蛋标题
            val prevParas = prevCh.optJSONArray("paragraphs") ?: JSONArray()
            val curParas = curCh.optJSONArray("paragraphs") ?: JSONArray()
            val curTitle = curCh.optString(
                "displayTitle",
                curCh.optString("title", curItem.optString("title", "彩蛋")),
            )
            val sepId = "merge-sep-$curId"
            prevParas.put(
                JSONObject()
                    .put("id", sepId)
                    .put("text", "—— $curTitle ——")
                    .put("html", "<p id=\"$sepId\" class=\"egg-merge-mark\">—— ${esc(curTitle)} ——</p>")
                    .put("empty", false)
                    .put("hasImage", false),
            )
            for (i in 0 until curParas.length()) {
                prevParas.put(curParas.getJSONObject(i))
            }
            prevCh.put("paragraphs", prevParas)

            // 段评合并
            val prevPc = prevCh.optJSONObject("paragraphComments") ?: JSONObject()
            val curPc = curCh.optJSONObject("paragraphComments") ?: JSONObject()
            val ck = curPc.keys()
            while (ck.hasNext()) {
                val pid = ck.next()
                if (!prevPc.has(pid)) prevPc.put(pid, curPc.get(pid))
            }
            prevCh.put("paragraphComments", prevPc)

            // 章评：彩蛋章评并入上一章 general
            val prevCc = prevCh.optJSONObject("chapterComments") ?: JSONObject()
            val curCc = curCh.optJSONObject("chapterComments") ?: JSONObject()
            val prevGen = prevCc.optJSONArray("general") ?: JSONArray()
            fun appendComments(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) prevGen.put(arr.get(i))
            }
            appendComments(curCc.optJSONArray("hot"))
            appendComments(curCc.optJSONArray("general"))
            prevCc.put("general", prevGen)
            prevCh.put("chapterComments", prevCc)

            // 若上一章仍是 article、被并入的是 egg，保持 article
            if (prevCh.optString("kind") == "egg" && curCh.optString("kind") != "egg") {
                // keep
            } else if (curCh.optString("kind") == "egg") {
                prevCh.put("kind", prevCh.optString("kind", "article"))
            }
            chaptersObj.put(prevId, prevCh)
            chaptersObj.remove(curId)

            val newSpine = JSONArray()
            for (i in 0 until spine.length()) {
                if (i == chapterIndex) continue
                newSpine.put(spine.getJSONObject(i))
            }
            // 更新上一章 spine kind/title
            for (i in 0 until newSpine.length()) {
                val it = newSpine.getJSONObject(i)
                if (it.optString("id") == prevId) {
                    it.put("kind", prevCh.optString("kind", "article"))
                }
            }
            root.put("spine", newSpine)
            root.put("chapters", chaptersObj)
            val stats = root.optJSONObject("stats") ?: JSONObject()
            stats.put("chapterCount", newSpine.length())
            stats.put("eggCount", countKind(chaptersObj, "egg"))
            root.put("stats", stats)

            val xhtmlPath = "OEBPS/chapters/$prevId.xhtml"
            val xhtml = chapterXhtml(
                prevCh.optString("displayTitle", prevCh.optString("title", prevId)),
                prevParas,
            )
            val drop = setOf(
                "OEBPS/chapters/$curId.xhtml",
                "OEBPS/chapters/$curId.html",
            )
            resultIndex = chapterIndex - 1
            MutateResult(root, drop, mapOf(xhtmlPath to xhtml.toByteArray(Charsets.UTF_8)))
        }
        return if (ok) resultIndex else -1
    }

    private data class MutateResult(
        val root: JSONObject,
        val dropEntries: Set<String>,
        val replaceBytes: Map<String, ByteArray>,
    )

    private fun mutateGnbook(path: String, block: (JSONObject) -> Any?): Boolean {
        val file = File(path)
        if (!file.isFile) return false
        val lower = path.lowercase()
        if (!lower.endsWith(".gnbook") && !lower.endsWith(".epub")) return false

        ZipFile(file).use { zip ->
            val entry = zip.getEntry("gnbook.json") ?: return false
            val root = zip.getInputStream(entry).bufferedReader().use { JSONObject(it.readText()) }
            val out = block(root)
            if (out == false) return false
            val result = when (out) {
                is MutateResult -> out
                true -> MutateResult(root, emptySet(), emptyMap())
                else -> return false
            }
            // 刷新 nav 粗略目录
            val navBytes = buildNavXhtml(result.root).toByteArray(Charsets.UTF_8)
            val replaces = result.replaceBytes.toMutableMap()
            replaces["OEBPS/nav.xhtml"] = navBytes
            replaces["gnbook.json"] = result.root.toString(2).toByteArray(Charsets.UTF_8)

            val tmp = File(file.parentFile, file.name + ".tmp")
            ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
                val written = mutableSetOf<String>()
                zip.entries().asSequence().forEach { e ->
                    if (e.isDirectory) return@forEach
                    if (e.name in result.dropEntries) return@forEach
                    zos.putNextEntry(ZipEntry(e.name))
                    val repl = replaces[e.name]
                    if (repl != null) {
                        zos.write(repl)
                        written += e.name
                    } else {
                        zip.getInputStream(e).use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
                // 新增替换项（若原包无该文件）
                for ((name, bytes) in replaces) {
                    if (name in written) continue
                    if (name in result.dropEntries) continue
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            return true
        }
    }

    private fun countKind(chapters: JSONObject, kind: String): Int {
        var n = 0
        val keys = chapters.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            if (chapters.getJSONObject(id).optString("kind") == kind) n++
        }
        return n
    }

    private fun chapterXhtml(title: String, paragraphs: JSONArray): String {
        val body = buildString {
            for (i in 0 until paragraphs.length()) {
                val p = paragraphs.optJSONObject(i) ?: continue
                val html = p.optString("html")
                if (html.isNotBlank()) append(html).append('\n')
                else {
                    val id = p.optString("id", "p-$i")
                    val text = p.optString("text")
                    append("<p id=\"").append(esc(id)).append("\">")
                        .append(esc(text)).append("</p>\n")
                }
            }
        }.ifBlank { "<p>（无内容）</p>" }
        return """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="zh-CN" lang="zh-CN">
            |<head><meta charset="utf-8"/><title>${esc(title)}</title>
            |<link rel="stylesheet" type="text/css" href="../style/reader.css"/></head>
            |<body>
            |<h1>${esc(title)}</h1>
            |<div class="article">
            |$body
            |</div>
            |</body></html>
        """.trimMargin()
    }

    private fun buildNavXhtml(root: JSONObject): String {
        val spine = root.optJSONArray("spine") ?: JSONArray()
        val lis = buildString {
            for (i in 0 until spine.length()) {
                val it = spine.optJSONObject(i) ?: continue
                val id = it.optString("id")
                val title = it.optString("title", id)
                append("<li><a href=\"chapters/")
                    .append(esc(id))
                    .append(".xhtml\">")
                    .append(esc(title))
                    .append("</a></li>\n")
            }
        }
        return """
            |<?xml version="1.0" encoding="utf-8"?>
            |<!DOCTYPE html>
            |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN" xml:lang="zh-CN">
            |<head><meta charset="utf-8"/><title>目录</title></head>
            |<body>
            |<nav epub:type="toc"><ol>
            |$lis
            |</ol></nav>
            |</body></html>
        """.trimMargin()
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }
}
