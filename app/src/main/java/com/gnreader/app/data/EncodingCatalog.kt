package com.gnreader.app.data

/** VS Code 风格首批编码 + 用户可扩展。 */
object EncodingCatalog {
    val builtins: List<String> = listOf(
        "UTF-8",
        "UTF-16LE",
        "UTF-16BE",
        "UTF-32LE",
        "UTF-32BE",
        "GBK",
        "GB18030",
        "GB2312",
        "Big5",
        "Shift_JIS",
        "EUC-JP",
        "EUC-KR",
        "ISO-8859-1",
        "ISO-8859-2",
        "ISO-8859-5",
        "ISO-8859-6",
        "ISO-8859-7",
        "ISO-8859-8",
        "ISO-8859-9",
        "windows-1250",
        "windows-1251",
        "windows-1252",
        "windows-1253",
        "windows-1254",
        "windows-1255",
        "windows-1256",
        "windows-1257",
        "windows-1258",
        "KOI8-R",
        "KOI8-U",
        "IBM437",
        "IBM850",
        "macintosh",
        "x-mac-cyrillic",
        "US-ASCII",
    )

    fun merge(custom: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        (builtins + custom.map { it.trim() }.filter { it.isNotEmpty() }).forEach { seen += it }
        return seen.toList()
    }
}
