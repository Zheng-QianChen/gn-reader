package com.gnreader.app.data.webdav

import com.gnreader.app.data.fs.EntryKind
import com.gnreader.app.data.fs.FileEntry
import com.gnreader.app.data.fs.FileKinds
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * 简易 WebDAV 客户端（坚果云等）。支持 PROPFIND / GET / PUT / DELETE / MKCOL。
 */
class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun authHeader(): String = Credentials.basic(username, password)

    private fun join(path: String): String {
        val root = baseUrl.trimEnd('/')
        val rel = path.trim().trimStart('/')
        return if (rel.isEmpty()) "$root/" else "$root/$rel"
    }

    fun list(path: String): List<FileEntry> {
        val url = join(path)
        val body = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:">
  <d:prop>
    <d:displayname/>
    <d:getcontentlength/>
    <d:getlastmodified/>
    <d:resourcetype/>
  </d:prop>
</d:propfind>""".trimIndent().toRequestBody("application/xml".toMediaType())

        val req = Request.Builder()
            .url(url)
            .header("Authorization", authHeader())
            .header("Depth", "1")
            .method("PROPFIND", body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("WebDAV list failed: HTTP ${resp.code}")
            val xml = resp.body?.string().orEmpty()
            return parsePropfind(xml, path)
        }
    }

    fun download(remotePath: String, localFile: File) {
        val req = Request.Builder()
            .url(join(remotePath))
            .header("Authorization", authHeader())
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("WebDAV GET failed: HTTP ${resp.code}")
            localFile.parentFile?.mkdirs()
            resp.body?.byteStream()?.use { input ->
                localFile.outputStream().use { input.copyTo(it) }
            } ?: error("empty body")
        }
    }

    fun upload(localFile: File, remotePath: String) {
        val req = Request.Builder()
            .url(join(remotePath))
            .header("Authorization", authHeader())
            .put(localFile.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("WebDAV PUT failed: HTTP ${resp.code}")
        }
    }

    fun delete(remotePath: String) {
        val req = Request.Builder()
            .url(join(remotePath))
            .header("Authorization", authHeader())
            .delete()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("WebDAV DELETE failed: HTTP ${resp.code}")
        }
    }

    fun mkcol(remotePath: String) {
        val req = Request.Builder()
            .url(join(remotePath))
            .header("Authorization", authHeader())
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 405) {
                error("WebDAV MKCOL failed: HTTP ${resp.code}")
            }
        }
    }

    private fun parsePropfind(xml: String, currentPath: String): List<FileEntry> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        val out = mutableListOf<FileEntry>()
        var event = parser.eventType
        var href = ""
        var displayName = ""
        var length = 0L
        var mtime = 0L
        var isCollection = false
        var inResponse = false

        fun flush() {
            if (!inResponse || href.isEmpty()) return
            val name = displayName.ifBlank {
                href.trimEnd('/').substringAfterLast('/')
            }
            if (name.isEmpty()) return
            // skip self
            val selfName = currentPath.trim('/').substringAfterLast('/')
            val normalizedHref = href.trimEnd('/')
            val currentUrl = join(currentPath).trimEnd('/')
            if (normalizedHref == currentUrl || name == selfName && !href.contains(name + "/")) {
                // still may be self entry; compare path segments
            }
            val remoteRel = if (currentPath.isBlank()) name else currentPath.trimEnd('/') + "/" + name
            // Heuristic: first response often self — skip when href ends with current path only
            val kind = if (isCollection) EntryKind.DIR else FileKinds.of(name, false)
            out += FileEntry(
                name = name,
                path = if (isCollection) remoteRel.trimEnd('/') + "/" else remoteRel,
                isDir = isCollection,
                size = length,
                mtime = mtime,
                kind = kind,
            )
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "response" -> {
                            inResponse = true
                            href = ""; displayName = ""; length = 0L; mtime = 0L; isCollection = false
                        }
                        "href" -> href = parser.nextText()
                        "displayname" -> displayName = parser.nextText()
                        "getcontentlength" -> length = parser.nextText().toLongOrNull() ?: 0L
                        "getlastmodified" -> mtime = 0L // keep simple
                        "collection" -> isCollection = true
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("response", true)) {
                        flush()
                        inResponse = false
                    }
                }
            }
            event = parser.next()
        }

        // Drop first entry if it looks like self directory
        return out.dropWhile { it.isDir && (it.name.isEmpty() || it.path.trim('/') == currentPath.trim('/')) }
            .distinctBy { it.path }
            .sortedWith(compareBy<FileEntry> { !it.isDir }.thenBy { it.name.lowercase() })
    }
}
