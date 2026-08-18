package com.gnreader.app.data.webdav

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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 按映射对做双向 LWW 同步；并用内容指纹识别改名/移动（WebDAV MOVE）。
 */
object FolderPairSync {
    data class Summary(
        val uploaded: MutableList<String> = mutableListOf(),
        val downloaded: MutableList<String> = mutableListOf(),
        val moved: MutableList<String> = mutableListOf(),
        val skipped: MutableList<String> = mutableListOf(),
        val conflicts: MutableList<String> = mutableListOf(),
        val errors: MutableList<String> = mutableListOf(),
        val pairs: MutableList<String> = mutableListOf(),
    )

    fun testConnection(
        baseUrl: String,
        username: String,
        password: String,
        remoteRoot: String = "/get_norvels",
    ): String {
        if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) {
            throw IllegalStateException("请填写 WebDAV 地址、账号与应用密码")
        }
        val client = DavSyncClient(baseUrl, username, password, remoteRoot)
        client.ensureDir("")
        val listed = client.listRecursive()
        val files = listed.count { !it.value.isDir }
        val dirs = listed.count { it.value.isDir }
        return "连接成功 · 云端 $remoteRoot · 文件 $files · 目录 $dirs"
    }

    fun syncAll(
        baseUrl: String,
        username: String,
        password: String,
        pairs: List<SyncPair>,
        excludeRules: List<String> = defaultExcludes(),
        onProgress: (String) -> Unit = {},
    ): Summary {
        val summary = Summary()
        val enabled = pairs.filter { it.enabled && it.local.isNotBlank() && it.remote.isNotBlank() }
        if (enabled.isEmpty()) {
            throw IllegalStateException("没有启用的文件夹映射")
        }
        if (username.isBlank() || password.isBlank() || baseUrl.isBlank()) {
            throw IllegalStateException("请先填写 WebDAV 地址、账号与应用密码")
        }
        enabled.forEachIndexed { i, pair ->
            val label = pair.name.ifBlank { pair.id }
            summary.pairs += label
            onProgress("映射 ${i + 1}/${enabled.size}: $label")
            // 先验证该映射根
            onProgress("[$label] 验证连接…")
            testConnection(baseUrl, username, password, pair.remote)
            syncOne(baseUrl, username, password, pair, excludeRules, summary, onProgress)
        }
        onProgress("完成")
        return summary
    }

    private fun syncOne(
        baseUrl: String,
        username: String,
        password: String,
        pair: SyncPair,
        rules: List<String>,
        summary: Summary,
        onProgress: (String) -> Unit,
    ) {
        val localRoot = File(pair.local)
        localRoot.mkdirs()
        val client = DavSyncClient(baseUrl, username, password, pair.remote)
        client.ensureDir("")
        onProgress("[${pair.name}] 枚举本地…")
        val local = listLocal(localRoot, rules).toMutableMap()
        onProgress("[${pair.name}] 枚举云端…")
        val remote = client.listRecursive().filterValues { !it.isDir }.toMutableMap()

        // 改名/移动：仅本地有 ↔ 仅云端有，按 size+指纹配对后 MOVE
        onProgress("[${pair.name}] 识别改名/移动…")
        detectAndApplyMoves(localRoot, client, local, remote, summary, pair.name, onProgress)

        val keys = (local.keys + remote.keys).toSortedSet()
        keys.forEachIndexed { idx, rel ->
            if (isExcluded(rel, rules)) return@forEachIndexed
            onProgress("[${pair.name}] ${idx + 1}/${keys.size} $rel")
            val L = local[rel]
            val R = remote[rel]
            try {
                when {
                    L != null && R == null -> {
                        client.upload(L.file, rel)
                        summary.uploaded += "${pair.name}:$rel"
                    }
                    R != null && L == null -> {
                        val dest = File(localRoot, rel)
                        client.download(rel, dest)
                        summary.downloaded += "${pair.name}:$rel"
                    }
                    L != null && R != null -> {
                        val lm = L.mtime
                        val rm = R.mtime
                        if (abs(lm - rm) < 2.0 && L.size == R.size) {
                            summary.skipped += "${pair.name}:$rel"
                        } else if (lm > rm) {
                            client.upload(L.file, rel)
                            summary.uploaded += "${pair.name}:$rel"
                        } else if (rm > lm) {
                            client.download(rel, L.file)
                            summary.downloaded += "${pair.name}:$rel"
                        } else {
                            val base = rel.substringBeforeLast('.', rel)
                            val ext = if (rel.contains('.')) "." + rel.substringAfterLast('.') else ""
                            val localCopy = File(localRoot, "$base.conflict.local$ext")
                            if (!localCopy.exists()) L.file.copyTo(localCopy, overwrite = false)
                            val remoteCopy = File(localRoot, "$base.conflict.remote$ext")
                            client.download(rel, remoteCopy)
                            summary.conflicts += "${pair.name}:$rel"
                        }
                    }
                }
            } catch (e: Exception) {
                summary.errors += "${pair.name}:$rel → ${e.message}"
            }
        }
    }

    /**
     * 本地改名 → 云端 MOVE 旧路径到新路径；
     * 云端改名 → 本地 rename。
     * 配对键：相同 size，且内容指纹一致（优先唯一 size 快路径）。
     */
    private fun detectAndApplyMoves(
        localRoot: File,
        client: DavSyncClient,
        local: MutableMap<String, LocalMeta>,
        remote: MutableMap<String, RemoteMeta>,
        summary: Summary,
        pairName: String,
        onProgress: (String) -> Unit,
    ) {
        val localOnly = local.keys.filter { it !in remote }.toMutableSet()
        val remoteOnly = remote.keys.filter { it !in local }.toMutableSet()
        if (localOnly.isEmpty() || remoteOnly.isEmpty()) return

        // size -> paths
        fun groupBySize(paths: Set<String>, sizeOf: (String) -> Long): Map<Long, List<String>> =
            paths.groupBy(sizeOf)

        val localBySize = groupBySize(localOnly) { local[it]!!.size }
        val remoteBySize = groupBySize(remoteOnly) { remote[it]!!.size }

        val candidateSizes = localBySize.keys.intersect(remoteBySize.keys)
        for (size in candidateSizes) {
            val lPaths = localBySize[size].orEmpty().filter { it in localOnly }
            val rPaths = remoteBySize[size].orEmpty().filter { it in remoteOnly }
            if (lPaths.isEmpty() || rPaths.isEmpty()) continue

            // 指纹配对
            val lFp = lPaths.associateWith { contentFingerprint(local[it]!!.file, size) }
            val rFp = mutableMapOf<String, String>()
            for (rp in rPaths) {
                try {
                    rFp[rp] = client.remoteFingerprint(rp, size)
                } catch (_: Exception) {
                    // Range 失败则跳过该远程文件的改名识别
                }
            }
            val usedR = mutableSetOf<String>()
            for ((lp, fp) in lFp) {
                val matches = rFp.filter { it.value == fp && it.key !in usedR }.keys.toList()
                if (matches.size != 1) continue
                val rp = matches[0]
                usedR += rp
                try {
                    // 判断方向：谁更新？两边 mtime；本地更新 → 认为本地改名，MOVE 云端
                    val lm = local[lp]!!.mtime
                    val rm = remote[rp]!!.mtime
                    if (lm >= rm - 2.0) {
                        onProgress("[$pairName] 云端移动 $rp → $lp")
                        client.move(rp, lp)
                        remote.remove(rp)
                        remote[lp] = RemoteMeta(lm, size, false)
                        summary.moved += "$pairName:$rp→$lp"
                    } else {
                        // 云端改名：本地文件挪到新相对路径
                        onProgress("[$pairName] 本地移动 $lp → $rp")
                        val src = local[lp]!!.file
                        val dest = File(localRoot, rp)
                        dest.parentFile?.mkdirs()
                        if (!src.renameTo(dest)) {
                            src.copyTo(dest, overwrite = true)
                            src.delete()
                        }
                        local.remove(lp)
                        local[rp] = LocalMeta(dest, dest.lastModified() / 1000.0, size)
                        summary.moved += "$pairName:$lp→$rp(local)"
                    }
                    localOnly.remove(lp)
                    remoteOnly.remove(rp)
                } catch (e: Exception) {
                    summary.errors += "$pairName:move $rp→$lp → ${e.message}"
                }
            }
        }
    }

    data class LocalMeta(val file: File, val mtime: Double, val size: Long)

    data class RemoteMeta(val mtime: Double, val size: Long, val isDir: Boolean)

    fun defaultExcludes(): List<String> = listOf(
        "tmp/", "*.log", "*.auth", ".lofter_auth", ".mobile_settings.json",
        "*.conflict.local*", "*.conflict.remote*", ".sync_state/",
    )

    private fun listLocal(root: File, rules: List<String>): Map<String, LocalMeta> {
        val out = linkedMapOf<String, LocalMeta>()
        root.walkTopDown().onEnter { dir ->
            val rel = dir.relativeTo(root).path.replace('\\', '/').trim('/')
            !(rel.isNotEmpty() && isExcluded("$rel/", rules))
        }.forEach { f ->
            if (!f.isFile) return@forEach
            val rel = f.relativeTo(root).path.replace('\\', '/').trim('/')
            if (rel.isEmpty() || isExcluded(rel, rules)) return@forEach
            out[rel] = LocalMeta(f, f.lastModified() / 1000.0, f.length())
        }
        return out
    }

    fun contentFingerprint(file: File, size: Long = file.length()): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(size.toString().toByteArray())
        file.inputStream().use { input ->
            if (size <= 512 * 1024) {
                md.update(input.readBytes())
            } else {
                val head = ByteArray(256 * 1024)
                val n1 = input.read(head)
                if (n1 > 0) md.update(head, 0, n1)
                // last 256KB
                val skip = size - 256 * 1024
                var left = skip - n1.coerceAtLeast(0)
                val buf = ByteArray(64 * 1024)
                while (left > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n <= 0) break
                    left -= n
                }
                val tail = input.readBytes()
                md.update(tail)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun isExcluded(relPath: String, rules: List<String>): Boolean {
        val rel = relPath.replace('\\', '/').trimStart('/')
        if (rel.isEmpty()) return false
        val name = rel.substringAfterLast('/')
        for (raw in rules) {
            val rule = raw.replace('\\', '/').trim()
            if (rule.isEmpty()) continue
            if (rule.endsWith('/')) {
                val prefix = rule.trimEnd('/')
                if (rel == prefix || rel.startsWith("$prefix/")) return true
            } else if (rule.contains('/')) {
                if (matchGlob(rel, rule)) return true
            } else {
                if (matchGlob(name, rule) || matchGlob(rel, rule)) return true
                if (rel.split('/').any { matchGlob(it, rule.trimEnd('/')) }) return true
            }
        }
        return false
    }

    private fun matchGlob(text: String, pattern: String): Boolean {
        val regex = Regex(
            "^" + Regex.escape(pattern).replace("\\*", ".*").replace("\\?", ".") + "$",
            RegexOption.IGNORE_CASE,
        )
        return regex.matches(text)
    }
}

/** 带 remoteRoot 的同步用 WebDAV 客户端。 */
private class DavSyncClient(
    baseUrl: String,
    username: String,
    password: String,
    remoteRoot: String,
) {
    private val base = baseUrl.trimEnd('/') + "/"
    private val root = "/" + remoteRoot.trim().trim('/') + "/"
    private val auth = Credentials.basic(username, password)
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun url(rel: String): String {
        val clean = rel.replace('\\', '/').trim('/')
        val path = root.trimStart('/') + clean
        val parts = path.split('/').filter { it.isNotEmpty() }.joinToString("/") {
            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val trailing = if (rel.endsWith('/') || clean.isEmpty()) "/" else ""
        return base + parts + trailing
    }

    fun ensureDir(relDir: String) {
        val clean = relDir.replace('\\', '/').trim('/')
        if (clean.isEmpty()) {
            mkcol("")
            return
        }
        val acc = mutableListOf<String>()
        for (part in clean.split('/')) {
            acc += part
            mkcol(acc.joinToString("/"))
        }
    }

    private fun mkcol(rel: String) {
        val req = Request.Builder()
            .url(url(if (rel.isEmpty()) "" else "$rel/"))
            .header("Authorization", auth)
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        http.newCall(req).execute().use { /* 201/405 ok */ }
    }

    fun move(fromRel: String, toRel: String) {
        val parent = toRel.replace('\\', '/').substringBeforeLast('/', "")
        if (parent.isNotEmpty()) ensureDir(parent) else ensureDir("")
        val destUrl = url(toRel)
        val req = Request.Builder()
            .url(url(fromRel))
            .header("Authorization", auth)
            .header("Destination", destUrl)
            .header("Overwrite", "T")
            .method("MOVE", null)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 201 && resp.code != 204) {
                error("MOVE ${resp.code}")
            }
        }
    }

    fun remoteFingerprint(rel: String, size: Long): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(size.toString().toByteArray())
        if (size <= 512 * 1024) {
            md.update(getBytes(rel, null))
        } else {
            md.update(getBytes(rel, "bytes=0-${256 * 1024 - 1}"))
            val start = size - 256 * 1024
            md.update(getBytes(rel, "bytes=$start-${size - 1}"))
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getBytes(rel: String, range: String?): ByteArray {
        val builder = Request.Builder().url(url(rel)).header("Authorization", auth).get()
        if (range != null) builder.header("Range", range)
        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) error("GET ${resp.code}")
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    fun listRecursive(): Map<String, FolderPairSync.RemoteMeta> {
        val out = linkedMapOf<String, FolderPairSync.RemoteMeta>()
        walk("", out)
        return out
    }

    private fun walk(rel: String, out: MutableMap<String, FolderPairSync.RemoteMeta>) {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:">
  <d:prop><d:getlastmodified/><d:getcontentlength/><d:resourcetype/></d:prop>
</d:propfind>""".trimIndent().toRequestBody("application/xml".toMediaType())
        val req = Request.Builder()
            .url(url(if (rel.isEmpty()) "" else "$rel/"))
            .header("Authorization", auth)
            .header("Depth", "1")
            .method("PROPFIND", body)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("PROPFIND ${resp.code}")
            val xml = resp.body?.string().orEmpty()
            parsePropfind(xml, rel, out)
        }
    }

    private fun parsePropfind(
        xml: String,
        currentRel: String,
        out: MutableMap<String, FolderPairSync.RemoteMeta>,
    ) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))
        var event = parser.eventType
        var href = ""
        var length = 0L
        var mtime = 0.0
        var isCollection = false
        var inResponse = false
        val remotePrefix = root.trimEnd('/')

        fun flush() {
            if (!inResponse || href.isEmpty()) return
            val pathOnly = try {
                java.net.URI(href).path ?: href
            } catch (_: Exception) {
                href.substringBefore('?')
            }
            val decoded = java.net.URLDecoder.decode(pathOnly, "UTF-8").trimEnd('/')
            val itemRel = when {
                decoded.startsWith(remotePrefix) -> decoded.removePrefix(remotePrefix).trim('/')
                currentRel.isNotEmpty() && decoded.endsWith(currentRel) -> currentRel
                else -> {
                    val name = decoded.substringAfterLast('/')
                    if (currentRel.isEmpty()) name else "$currentRel/$name".trim('/')
                }
            }
            if (itemRel.isEmpty()) return
            if (itemRel == currentRel) return
            out[itemRel] = FolderPairSync.RemoteMeta(mtime, length, isCollection)
            if (isCollection) {
                walk(itemRel, out)
            }
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                    "response" -> {
                        inResponse = true
                        href = ""; length = 0L; mtime = 0.0; isCollection = false
                    }
                    "href" -> href = parser.nextText()
                    "getcontentlength" -> length = parser.nextText().toLongOrNull() ?: 0L
                    "getlastmodified" -> mtime = parseHttpDate(parser.nextText())
                    "collection" -> isCollection = true
                }
                XmlPullParser.END_TAG -> if (parser.name.equals("response", true)) {
                    flush()
                    inResponse = false
                }
            }
            event = parser.next()
        }
    }

    fun download(rel: String, dest: File) {
        val req = Request.Builder().url(url(rel)).header("Authorization", auth).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("GET ${resp.code}")
            dest.parentFile?.mkdirs()
            resp.body?.byteStream()?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: error("empty body")
            resp.header("Last-Modified")?.let { lm ->
                val ts = (parseHttpDate(lm) * 1000).toLong()
                if (ts > 0) dest.setLastModified(ts)
            }
        }
    }

    fun upload(local: File, rel: String) {
        val parent = rel.replace('\\', '/').substringBeforeLast('/', "")
        if (parent.isNotEmpty()) ensureDir(parent) else ensureDir("")
        val req = Request.Builder()
            .url(url(rel))
            .header("Authorization", auth)
            .put(local.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("PUT ${resp.code}")
        }
    }

    private fun parseHttpDate(s: String?): Double {
        if (s.isNullOrBlank()) return 0.0
        return try {
            val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("GMT")
            (fmt.parse(s)?.time ?: 0L) / 1000.0
        } catch (_: Exception) {
            0.0
        }
    }
}
