package com.gnreader.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class ManualTocEntry(
    val id: String,
    val title: String,
    val offset: Int,
    val length: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * 手动目录（按文件路径持久化）。
 * TXT：选中标题 → 长按菜单「加入目录」。
 */
class ManualTocStore(context: Context) {
    private val prefs = context.getSharedPreferences("gn_reader_manual_toc", Context.MODE_PRIVATE)

    fun listFor(filePath: String): List<ManualTocEntry> {
        val raw = prefs.getString(key(filePath), "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ManualTocEntry(
                    id = o.optString("id").ifBlank { "e-$i" },
                    title = o.optString("title"),
                    offset = o.optInt("offset"),
                    length = o.optInt("length"),
                    createdAt = o.optLong("createdAt"),
                )
            }.sortedBy { it.offset }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(filePath: String, title: String, offset: Int, length: Int = 0): ManualTocEntry {
        val entry = ManualTocEntry(
            id = "m-${System.currentTimeMillis()}-${offset}",
            title = title.trim().ifBlank { "书签@$offset" },
            offset = offset.coerceAtLeast(0),
            length = length.coerceAtLeast(0),
        )
        val items = listFor(filePath).filterNot {
            it.offset == entry.offset && it.title == entry.title
        }.toMutableList()
        items += entry
        save(filePath, items)
        return entry
    }

    fun remove(filePath: String, id: String) {
        save(filePath, listFor(filePath).filterNot { it.id == id })
    }

    private fun save(filePath: String, items: List<ManualTocEntry>) {
        val arr = JSONArray()
        items.sortedBy { it.offset }.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("offset", e.offset)
                    .put("length", e.length)
                    .put("createdAt", e.createdAt),
            )
        }
        prefs.edit().putString(key(filePath), arr.toString()).apply()
    }

    private fun key(filePath: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val dig = md.digest(filePath.toByteArray(Charsets.UTF_8))
        return "toc_" + dig.joinToString("") { "%02x".format(it) }
    }
}

object AutoToc {
    private val patterns = listOf(
        Regex("""(?m)^(第[零一二三四五六七八九十百千0-9]+[章节回卷部集].{0,40})$"""),
        Regex("""(?m)^(Chapter\s+\d+[^\n]{0,40})$""", RegexOption.IGNORE_CASE),
        Regex("""(?m)^(CHAPTER\s+[IVXLC\d]+[^\n]{0,40})$"""),
        Regex("""(?m)^([0-9]{1,3}[\.、\s].{2,40})$"""),
        Regex("""(?m)^(【.+】)\s*$"""),
    )

    fun detect(text: String, maxEntries: Int = 200): List<ManualTocEntry> {
        val found = linkedMapOf<Int, ManualTocEntry>()
        for (re in patterns) {
            for (m in re.findAll(text)) {
                val title = m.groupValues.getOrNull(1)?.trim().orEmpty()
                if (title.length < 2) continue
                val offset = m.range.first
                if (offset in found) continue
                found[offset] = ManualTocEntry(
                    id = "auto-$offset",
                    title = title.take(80),
                    offset = offset,
                    length = title.length,
                )
                if (found.size >= maxEntries) break
            }
            if (found.size >= maxEntries) break
        }
        return found.values.sortedBy { it.offset }
    }
}
