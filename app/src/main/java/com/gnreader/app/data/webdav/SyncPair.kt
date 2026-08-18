package com.gnreader.app.data.webdav

/**
 * 本地文件夹 ↔ 云端文件夹映射。
 * 例：local=/storage/emulated/0/BOOKS  remote=/BOOKS
 */
data class SyncPair(
    val id: String,
    val name: String,
    val local: String,
    val remote: String,
    val enabled: Boolean = true,
)
