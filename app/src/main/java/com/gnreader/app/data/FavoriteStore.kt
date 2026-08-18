package com.gnreader.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FavoriteFolder(
    val path: String,
    val label: String,
)

/** Moon+ 风格收藏目录。 */
class FavoriteStore(context: Context) {
    private val prefs = context.getSharedPreferences("gn_reader_favorites", Context.MODE_PRIVATE)

    fun list(): List<FavoriteFolder> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                FavoriteFolder(
                    path = o.optString("path"),
                    label = o.optString("label").ifBlank { o.optString("path").substringAfterLast('/') },
                ).takeIf { it.path.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun contains(path: String): Boolean {
        val norm = path.trimEnd('/')
        return list().any { it.path.trimEnd('/') == norm }
    }

    fun add(path: String, label: String? = null) {
        val norm = path.trimEnd('/')
        val items = list().filterNot { it.path.trimEnd('/') == norm }.toMutableList()
        items.add(
            0,
            FavoriteFolder(
                path = norm,
                label = label?.ifBlank { null } ?: norm.substringAfterLast('/').ifBlank { norm },
            ),
        )
        save(items)
    }

    fun remove(path: String) {
        val norm = path.trimEnd('/')
        save(list().filterNot { it.path.trimEnd('/') == norm })
    }

    fun toggle(path: String, label: String? = null): Boolean {
        return if (contains(path)) {
            remove(path)
            false
        } else {
            add(path, label)
            true
        }
    }

    private fun save(items: List<FavoriteFolder>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("path", it.path).put("label", it.label))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "folders"
    }
}
