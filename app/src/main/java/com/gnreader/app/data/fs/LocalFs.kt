package com.gnreader.app.data.fs

import java.io.File

enum class EntryKind {
    DIR, GNBOOK, EPUB, PDF, TXT, MOBI, DOCX, PPT, XLS, IMAGE, OTHER
}

data class FileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long,
    val kind: EntryKind,
)

object FileKinds {
    fun of(name: String, isDir: Boolean): EntryKind {
        if (isDir) return EntryKind.DIR
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "gnbook" -> EntryKind.GNBOOK
            "epub" -> EntryKind.EPUB
            "pdf" -> EntryKind.PDF
            "txt", "text", "log", "md", "csv", "json", "xml", "html", "htm", "css", "js", "kt", "java", "py", "c", "cpp", "h" -> EntryKind.TXT
            "mobi", "azw", "azw3" -> EntryKind.MOBI
            "docx", "doc" -> EntryKind.DOCX
            "pptx", "ppt" -> EntryKind.PPT
            "xlsx", "xls" -> EntryKind.XLS
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg" -> EntryKind.IMAGE
            else -> EntryKind.OTHER
        }
    }

    fun label(kind: EntryKind): String = when (kind) {
        EntryKind.DIR -> "文件夹"
        EntryKind.GNBOOK -> "GN Book"
        EntryKind.EPUB -> "EPUB"
        EntryKind.PDF -> "PDF"
        EntryKind.TXT -> "文本"
        EntryKind.MOBI -> "MOBI"
        EntryKind.DOCX -> "Word"
        EntryKind.PPT -> "PPT"
        EntryKind.XLS -> "Excel"
        EntryKind.IMAGE -> "图片"
        EntryKind.OTHER -> "文件"
    }
}

object LocalFs {
    fun list(path: String, sortBy: String = "name", asc: Boolean = true): List<FileEntry> {
        val dir = File(path)
        if (!dir.isDirectory) return emptyList()
        val items = dir.listFiles()?.map { f ->
            FileEntry(
                name = f.name,
                path = f.absolutePath,
                isDir = f.isDirectory,
                size = if (f.isFile) f.length() else 0L,
                mtime = f.lastModified(),
                kind = FileKinds.of(f.name, f.isDirectory),
            )
        } ?: emptyList()
        val sorted = when (sortBy) {
            "mtime" -> items.sortedBy { it.mtime }
            "size" -> items.sortedBy { it.size }
            "type" -> items.sortedBy { it.kind.name + it.name.lowercase() }
            else -> items.sortedBy { it.name.lowercase() }
        }
        val ordered = if (asc) sorted else sorted.reversed()
        return ordered.sortedBy { !it.isDir } // dirs first
    }

    fun parentOrNull(path: String, root: String): String? {
        val p = File(path).parentFile ?: return null
        val abs = p.absolutePath
        if (!abs.startsWith(File(root).absolutePath) && abs != File(root).absolutePath) {
            // still allow navigating above if user chose a deep start? keep within storage
        }
        return abs
    }

    fun readText(path: String, charsetName: String): String {
        val bytes = File(path).readBytes()
        return String(bytes, charset(charsetName))
    }

    fun writeText(path: String, text: String, charsetName: String) {
        File(path).writeBytes(text.toByteArray(charset(charsetName)))
    }

    fun delete(path: String): Boolean {
        val f = File(path)
        return if (f.isDirectory) f.deleteRecursively() else f.delete()
    }

    fun rename(path: String, newName: String): File? {
        val f = File(path)
        val dest = File(f.parentFile, newName)
        return if (f.renameTo(dest)) dest else null
    }

    fun copy(srcPath: String, destDir: String): File? {
        val src = File(srcPath)
        if (!src.exists()) return null
        val dir = File(destDir)
        if (!dir.isDirectory) dir.mkdirs()
        var dest = File(dir, src.name)
        if (dest.exists()) {
            val base = src.nameWithoutExtension
            val ext = src.extension.let { if (it.isEmpty()) "" else ".$it" }
            var n = 2
            while (dest.exists()) {
                dest = File(dir, "$base ($n)$ext")
                n++
            }
        }
        return try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = false)
                dest
            } else {
                src.copyTo(dest, overwrite = false)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun move(srcPath: String, destDir: String): File? {
        val copied = copy(srcPath, destDir) ?: return null
        delete(srcPath)
        return copied
    }

    /** 解压 zip 到同级目录下的同名文件夹；已存在则加后缀。 */
    fun unzip(zipPath: String): File {
        val zip = File(zipPath)
        require(zip.isFile) { "不是文件" }
        val parent = zip.parentFile ?: throw IllegalStateException("无父目录")
        var dest = File(parent, zip.nameWithoutExtension)
        var n = 2
        while (dest.exists()) {
            dest = File(parent, "${zip.nameWithoutExtension} ($n)")
            n++
        }
        dest.mkdirs()
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val out = File(dest, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return dest
    }
}
