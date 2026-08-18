package com.gnreader.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecentBook(
    val path: String,
    val title: String,
    val kind: String,
    val lastReadAt: Long,
    val progress: Float = 0f,
)

class RecentStore(context: Context) {
    private val prefs = context.getSharedPreferences("gn_reader_recent", Context.MODE_PRIVATE)

    fun list(limit: Int = 20): List<RecentBook> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                RecentBook(
                    path = o.optString("path"),
                    title = o.optString("title"),
                    kind = o.optString("kind"),
                    lastReadAt = o.optLong("lastReadAt"),
                    progress = o.optDouble("progress").toFloat(),
                ).takeIf { it.path.isNotBlank() }
            }.sortedByDescending { it.lastReadAt }.take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun touch(path: String, title: String, kind: String, progress: Float = 0f) {
        val items = list(50).filterNot { it.path == path }.toMutableList()
        items.add(
            0,
            RecentBook(path, title.ifBlank { path.substringAfterLast('/') }, kind, System.currentTimeMillis(), progress),
        )
        val arr = JSONArray()
        items.take(40).forEach {
            arr.put(
                JSONObject()
                    .put("path", it.path)
                    .put("title", it.title)
                    .put("kind", it.kind)
                    .put("lastReadAt", it.lastReadAt)
                    .put("progress", it.progress.toDouble()),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "items"
    }
}
