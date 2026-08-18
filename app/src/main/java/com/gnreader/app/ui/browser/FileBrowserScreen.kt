package com.gnreader.app.ui.browser

import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.FavoriteFolder
import com.gnreader.app.data.FavoriteStore
import com.gnreader.app.data.fs.EntryKind
import com.gnreader.app.data.fs.FileEntry
import com.gnreader.app.data.fs.FileKinds
import com.gnreader.app.data.fs.LocalFs
import com.gnreader.app.data.webdav.WebDavClient
import com.gnreader.app.data.webdav.FolderPairSync
import com.gnreader.app.ui.common.ImeSafeEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MoonBlue = Color(0xFF1976D2)
private val FolderAmber = Color(0xFFE8A317)

private data class PathShortcut(val label: String, val path: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    settings: AppSettings,
    initialDirPath: String?,
    onOpenFile: (path: String, kind: EntryKind) -> Unit,
    onOpenSettings: () -> Unit,
    onBackToHome: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favorites = remember { FavoriteStore(context) }
    val sdRoot = remember { Environment.getExternalStorageDirectory().absolutePath }

    var currentPath by rememberSaveable {
        mutableStateOf(settings.lastBrowsePath.ifBlank { settings.libraryRoot })
    }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var favItems by remember { mutableStateOf(favorites.list()) }
    var sortBy by rememberSaveable { mutableStateOf(settings.browseSortBy) }
    var sortAsc by rememberSaveable { mutableStateOf(settings.browseSortAsc) }
    var selectMoreOpen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf("name") } // name | fulltext
    var searchFormats by remember { mutableStateOf(setOf("txt","epub","gnbook","pdf","mobi")) }
    var searchHits by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pathMenuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var clipboard by remember { mutableStateOf<List<String>>(emptyList()) }
    var clipboardCut by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<FileEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var syncing by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showWebDav by remember { mutableStateOf(false) }
    var davPath by remember { mutableStateOf("") }
    var itemMenu by remember { mutableStateOf<FileEntry?>(null) }

    // 每次进入/回到本页：以 lastBrowsePath 为准，避免跳到收藏列表第一项
    LaunchedEffect(Unit) {
        val saved = settings.lastBrowsePath.ifBlank { settings.libraryRoot }
        if (File(saved).isDirectory && saved != currentPath) {
            currentPath = saved
        }
    }

    // 外部明确要求跳转到某目录（首页快捷入口会先写入 lastBrowsePath）
    LaunchedEffect(initialDirPath) {
        val p = initialDirPath?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (File(p).isDirectory) {
            currentPath = p
            settings.lastBrowsePath = p
        }
    }

    val titleLabel = remember(currentPath, favItems) {
        favItems.firstOrNull { it.path.trimEnd('/') == currentPath.trimEnd('/') }?.label
            ?: when {
                currentPath.trimEnd('/') == sdRoot.trimEnd('/') -> "本地文件"
                else -> currentPath.substringAfterLast('/').ifBlank { "本地文件" }
            }
    }

    fun refreshFavorites() {
        favItems = favorites.list()
    }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                if (showWebDav) {
                    entries = withContext(Dispatchers.IO) {
                        WebDavClient(settings.webdavUrl, settings.webdavUser, settings.webdavPass)
                            .list(davPath)
                    }
                } else {
                    entries = withContext(Dispatchers.IO) {
                        val dir = File(currentPath)
                        if (!dir.exists()) dir.mkdirs()
                        LocalFs.list(currentPath, sortBy, sortAsc)
                    }
                    settings.lastBrowsePath = currentPath
                }
                refreshFavorites()
            } catch (e: Exception) {
                error = e.message
                entries = emptyList()
            } finally {
                loading = false
            }
        }
    }

    fun goUpOrHome(): Boolean {
        if (selecting) {
            selecting = false
            selected = emptySet()
            return true
        }
        if (showWebDav) {
            val trimmed = davPath.trim('/')
            if (trimmed.isNotEmpty()) {
                davPath = if (trimmed.contains('/')) trimmed.substringBeforeLast('/') else ""
                return true
            }
            showWebDav = false
            return true
        }
        val parent = File(currentPath).parentFile?.absolutePath
        return if (parent != null && parent != currentPath) {
            currentPath = parent
            true
        } else {
            onBackToHome()
            true
        }
    }

    fun enterSelect(seed: FileEntry? = null) {
        selecting = true
        selected = seed?.let { setOf(it.path) } ?: emptySet()
    }

    fun toggleSelect(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    fun doDeleteSelected() {
        scope.launch {
            withContext(Dispatchers.IO) {
                selected.forEach { LocalFs.delete(it) }
            }
            selecting = false
            selected = emptySet()
            refresh()
        }
    }

    fun doPaste() {
        if (clipboard.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) {
                clipboard.forEach { src ->
                    if (clipboardCut) LocalFs.move(src, currentPath) else LocalFs.copy(src, currentPath)
                }
            }
            if (clipboardCut) clipboard = emptyList()
            clipboardCut = false
            Toast.makeText(context, "已粘贴", Toast.LENGTH_SHORT).show()
            refresh()
        }
    }

    LaunchedEffect(currentPath, davPath, sortBy, sortAsc, showWebDav) { refresh() }
    LaunchedEffect(sortBy, sortAsc) {
        settings.browseSortBy = sortBy
        settings.browseSortAsc = sortAsc
    }

    BackHandler { goUpOrHome() }

    val crumbs = remember(currentPath) { buildCrumbs(currentPath, sdRoot) }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("已选 ${selected.size}") },
                    navigationIcon = {
                        IconButton(onClick = {
                            selecting = false
                            selected = emptySet()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "取消") }
                    },
                    actions = {
                        IconButton(onClick = {
                            clipboard = selected.toList()
                            clipboardCut = false
                            Toast.makeText(context, "已复制 ${clipboard.size} 项", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "复制") }
                        IconButton(onClick = {
                            clipboard = selected.toList()
                            clipboardCut = true
                            Toast.makeText(context, "已剪切 ${clipboard.size} 项", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCut, "剪切") }
                        IconButton(
                            onClick = { doDeleteSelected() },
                            enabled = selected.isNotEmpty(),
                        ) { Icon(Icons.Default.Delete, "删除") }
                        Box {
                            IconButton(onClick = { selectMoreOpen = true }) {
                                Icon(Icons.Default.MoreVert, "更多")
                            }
                            DropdownMenu(expanded = selectMoreOpen, onDismissRequest = { selectMoreOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("分享到其他应用") },
                                    onClick = {
                                        selectMoreOpen = false
                                        shareMultiple(context, selected.toList())
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("用其他应用打开（单项）") },
                                    onClick = {
                                        selectMoreOpen = false
                                        val one = selected.firstOrNull()
                                        if (one != null && File(one).isFile) openWithOtherApp(context, one)
                                        else Toast.makeText(context, "请选择一个文件", Toast.LENGTH_SHORT).show()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("收藏所选目录") },
                                    onClick = {
                                        selectMoreOpen = false
                                        selected.forEach { p ->
                                            if (File(p).isDirectory) favorites.toggle(p)
                                        }
                                        refreshFavorites()
                                        Toast.makeText(context, "已更新收藏", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF455A64),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                    windowInsets = WindowInsets.statusBars,
                )
            } else {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                Modifier.combinedClickable(onClick = { pathMenuOpen = true }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (showWebDav) "WebDAV" else titleLabel, maxLines = 1)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = pathMenuOpen, onDismissRequest = { pathMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("本地文件") },
                                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
                                    onClick = {
                                        pathMenuOpen = false
                                        showWebDav = false
                                        currentPath = sdRoot
                                    },
                                )
                                if (favItems.isNotEmpty()) {
                                    HorizontalDivider()
                                    favItems.forEach { fav ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(fav.label)
                                                    Text(fav.path, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                                }
                                            },
                                            leadingIcon = { Icon(Icons.Default.Star, null, tint = FolderAmber) },
                                            onClick = {
                                                pathMenuOpen = false
                                                showWebDav = false
                                                currentPath = fav.path
                                                settings.lastBrowsePath = fav.path
                                            },
                                        )
                                    }
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("WebDAV") },
                                    leadingIcon = { Icon(Icons.Default.Cloud, null) },
                                    onClick = {
                                        pathMenuOpen = false
                                        showWebDav = true
                                    },
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackToHome) {
                            Icon(Icons.Default.Menu, "首页菜单")
                        }
                    },
                    actions = {
                        IconButton(onClick = { goUpOrHome() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                        IconButton(onClick = { refresh() }) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
                        Box {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Default.MoreVert, "更多")
                            }
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("多选") },
                                    onClick = { overflowOpen = false; enterSelect() },
                                )
                                DropdownMenuItem(
                                    text = { Text("新建文件夹") },
                                    onClick = { overflowOpen = false; showNewFolder = true },
                                )
                                if (clipboard.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(if (clipboardCut) "粘贴（移动）" else "粘贴（复制）") },
                                        onClick = { overflowOpen = false; doPaste() },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("按名称排序") },
                                    onClick = {
                                        overflowOpen = false
                                        if (sortBy == "name") sortAsc = !sortAsc else {
                                            sortBy = "name"; sortAsc = true
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("按时间排序") },
                                    onClick = {
                                        overflowOpen = false
                                        if (sortBy == "mtime") sortAsc = !sortAsc else {
                                            sortBy = "mtime"; sortAsc = false
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("搜索") },
                                    onClick = {
                                        overflowOpen = false
                                        showSearch = true
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (syncing) "同步中…" else "立即同步") },
                                    enabled = !syncing,
                                    onClick = {
                                        overflowOpen = false
                                        syncing = true
                                        scope.launch {
                                            try {
                                                val result = withContext(Dispatchers.IO) {
                                                    FolderPairSync.syncAll(
                                                        baseUrl = settings.webdavUrl,
                                                        username = settings.webdavUser,
                                                        password = settings.webdavPass,
                                                        pairs = settings.effectiveSyncPairs(),
                                                        excludeRules = settings.syncExcludeRules
                                                            .lines().map { it.trim() }.filter { it.isNotEmpty() },
                                                    )
                                                }
                                                Toast.makeText(
                                                    context,
                                                    "同步完成 ↑${result.uploaded.size} ↓${result.downloaded.size} " +
                                                        "移${result.moved.size} 跳过${result.skipped.size}",
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, e.message ?: "同步失败", Toast.LENGTH_LONG).show()
                                            } finally {
                                                syncing = false
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("测试 WebDAV 连接") },
                                    onClick = {
                                        overflowOpen = false
                                        scope.launch {
                                            try {
                                                val msg = withContext(Dispatchers.IO) {
                                                    FolderPairSync.testConnection(
                                                        settings.webdavUrl,
                                                        settings.webdavUser,
                                                        settings.webdavPass,
                                                        settings.effectiveSyncPairs().firstOrNull()?.remote ?: "/get_norvels",
                                                    )
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("在其他应用中打开此文件夹") },
                                    onClick = {
                                        overflowOpen = false
                                        openFolderInOtherApp(context, currentPath)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("设置") },
                                    onClick = { overflowOpen = false; onOpenSettings() },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MoonBlue,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                    windowInsets = WindowInsets.statusBars,
                )
            }
        },
        floatingActionButton = {
            if (!selecting && clipboard.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { doPaste() },
                    containerColor = MoonBlue,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.ContentPaste, null) },
                    text = { Text(if (clipboardCut) "粘贴到此" else "复制到此") },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF7F7F7)),
        ) {
            // Moon+ 风格路径面包屑
            if (!showWebDav) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Color(0xFFECEFF1))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    crumbs.forEachIndexed { i, crumb ->
                        Surface(
                            onClick = { currentPath = crumb.path },
                            shape = RoundedCornerShape(4.dp),
                            color = if (i == crumbs.lastIndex) Color(0xFFCFD8DC) else Color(0xFFE0E0E0),
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(
                                crumb.label,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        if (i != crumbs.lastIndex) {
                            Text("›", color = Color.Gray, modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            } else {
                Text(
                    "WebDAV:/${davPath.trim('/')}",
                    Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }

            val dirs = entries.filter { it.isDir }
            val files = entries.filter { !it.isDir }

            LazyColumn(Modifier.fillMaxSize()) {
                if (dirs.isNotEmpty()) {
                    item {
                        Text(
                            "文件夹",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    items(dirs, key = { it.path }) { item ->
                        FileRow(
                            item = item,
                            starred = favorites.contains(item.path),
                            selected = item.path in selected,
                            selecting = selecting,
                            onClick = {
                                if (selecting) toggleSelect(item.path)
                                else if (showWebDav) davPath = item.path.trim('/')
                                else currentPath = item.path
                            },
                            onLongClick = {
                                if (!selecting) enterSelect(item) else toggleSelect(item.path)
                            },
                            onStar = {
                                favorites.toggle(item.path, item.name)
                                refreshFavorites()
                            },
                            onMore = { itemMenu = item },
                        )
                    }
                }
                if (files.isNotEmpty()) {
                    item {
                        Text(
                            "文件",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    items(files, key = { it.path }) { item ->
                        FileRow(
                            item = item,
                            starred = false,
                            selected = item.path in selected,
                            selecting = selecting,
                            onClick = {
                                if (selecting) toggleSelect(item.path)
                                else if (showWebDav) {
                                    scope.launch {
                                        try {
                                            loading = true
                                            val local = withContext(Dispatchers.IO) {
                                                val client = WebDavClient(
                                                    settings.webdavUrl, settings.webdavUser, settings.webdavPass,
                                                )
                                                val dest = File(context.cacheDir, "dav/${item.name}")
                                                client.download(item.path.trimStart('/'), dest)
                                                dest.absolutePath
                                            }
                                            onOpenFile(local, item.kind)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                        } finally {
                                            loading = false
                                        }
                                    }
                                } else onOpenFile(item.path, item.kind)
                            },
                            onLongClick = {
                                if (!selecting) enterSelect(item) else toggleSelect(item.path)
                            },
                            onStar = null,
                            onMore = { itemMenu = item },
                        )
                    }
                }
                if (entries.isEmpty() && !loading) {
                    item {
                        Text("空目录", Modifier.padding(24.dp), color = Color.Gray)
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }

    // 单项菜单（扁平大字，对标系统文件操作）
    itemMenu?.let { target ->
        val isZip = !target.isDir && target.name.lowercase().endsWith(".zip")
        AlertDialog(
            onDismissRequest = { itemMenu = null },
            title = {
                Text(target.name, maxLines = 2, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    FlatMenuItem("打开") {
                        itemMenu = null
                        if (target.isDir) currentPath = target.path
                        else onOpenFile(target.path, target.kind)
                    }
                    if (!target.isDir) {
                        FlatMenuItem("用其他软件打开") {
                            itemMenu = null
                            openWithOtherApp(context, target.path)
                        }
                    }
                    FlatMenuItem("分享") {
                        itemMenu = null
                        shareFile(context, target.path)
                    }
                    if (isZip && !showWebDav) {
                        FlatMenuItem("解压") {
                            itemMenu = null
                            scope.launch {
                                try {
                                    val dest = withContext(Dispatchers.IO) { LocalFs.unzip(target.path) }
                                    Toast.makeText(context, "已解压到 ${dest.name}", Toast.LENGTH_SHORT).show()
                                    refresh()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    if (!showWebDav && settings.webdavUrl.isNotBlank() && !target.isDir) {
                        FlatMenuItem("上传到 WebDAV") {
                            itemMenu = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        WebDavClient(settings.webdavUrl, settings.webdavUser, settings.webdavPass)
                                            .upload(File(target.path), target.name)
                                    }
                                    Toast.makeText(context, "已上传", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    FlatMenuItem("选择") {
                        itemMenu = null
                        enterSelect(target)
                    }
                    if (target.isDir && !showWebDav) {
                        val starred = favorites.contains(target.path)
                        FlatMenuItem(if (starred) "取消收藏" else "收藏此目录") {
                            favorites.toggle(target.path, target.name)
                            refreshFavorites()
                            itemMenu = null
                        }
                    }
                    if (!showWebDav) {
                        FlatMenuItem("重命名") {
                            editTarget = target
                            renameText = target.name
                            itemMenu = null
                        }
                        FlatMenuItem("复制") {
                            clipboard = listOf(target.path)
                            clipboardCut = false
                            itemMenu = null
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }
                        FlatMenuItem("剪切") {
                            clipboard = listOf(target.path)
                            clipboardCut = true
                            itemMenu = null
                            Toast.makeText(context, "已剪切", Toast.LENGTH_SHORT).show()
                        }
                        FlatMenuItem("删除", danger = true) {
                            LocalFs.delete(target.path)
                            itemMenu = null
                            refresh()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { itemMenu = null }) { Text("关闭", fontSize = 16.sp) }
            },
        )
    }


    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("搜索") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("模式", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = searchMode == "name", onClick = { searchMode = "name" }, label = { Text("文件名") })
                        FilterChip(selected = searchMode == "fulltext", onClick = { searchMode = "fulltext" }, label = { Text("全文") })
                    }
                    Text("格式", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("txt","epub","gnbook","pdf","mobi").forEach { ext ->
                            FilterChip(
                                selected = ext in searchFormats,
                                onClick = {
                                    searchFormats = if (ext in searchFormats) searchFormats - ext else searchFormats + ext
                                },
                                label = { Text(ext, fontSize = 12.sp) },
                            )
                        }
                    }
                    if (searchHits.isNotEmpty()) {
                        Text("命中 ${searchHits.size}", fontSize = 12.sp, color = Color.Gray)
                        searchHits.take(30).forEach { hit ->
                            Text(
                                hit.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSearch = false
                                        if (hit.isDir) currentPath = hit.path
                                        else onOpenFile(hit.path, hit.kind)
                                    }
                                    .padding(vertical = 6.dp),
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        searchHits = withContext(Dispatchers.IO) {
                            searchFiles(currentPath, searchQuery, searchMode, searchFormats)
                        }
                    }
                }) { Text("搜索") }
            },
            dismissButton = { TextButton(onClick = { showSearch = false }) { Text("关闭") } },
        )
    }

    if (editTarget != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("重命名") },
            text = {
                // 原生 EditText：避免输入法自动补全括号被 Compose 吃掉
                ImeSafeEditText(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    placeholder = "新文件名",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalFs.rename(editTarget!!.path, renameText.trim())
                    editTarget = null
                    refresh()
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("取消") } },
        )
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("新建文件夹") },
            text = {
                ImeSafeEditText(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    placeholder = "文件夹名",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    File(currentPath, newFolderName.trim()).mkdirs()
                    showNewFolder = false
                    newFolderName = ""
                    refresh()
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: FileEntry,
    starred: Boolean,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStar: (() -> Unit)?,
    onMore: () -> Unit,
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val bg = when {
        selected -> Color(0xFFBBDEFB)
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeIcon(item)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                buildString {
                    if (item.mtime > 0) append(sdf.format(Date(item.mtime)))
                    if (!item.isDir) {
                        if (isNotEmpty()) append(" · ")
                        append(formatSize(item.size))
                        append(" · ")
                        append(FileKinds.label(item.kind))
                    }
                },
                fontSize = 12.sp,
                color = Color(0xFF8A8A8A),
                maxLines = 1,
            )
        }
        if (onStar != null && !selecting) {
            IconButton(onClick = onStar) {
                Icon(
                    if (starred) Icons.Default.Star else Icons.Default.StarBorder,
                    "收藏",
                    tint = if (starred) FolderAmber else Color(0xFF9E9E9E),
                )
            }
        }
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else {
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, "菜单", tint = Color(0xFF757575))
            }
        }
    }
    HorizontalDivider(color = Color(0xFFE8E8E8), thickness = 0.5.dp)
}

@Composable
private fun FlatMenuItem(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 17.sp,
        color = if (danger) MaterialTheme.colorScheme.error else Color(0xFF111111),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

private fun shareFile(context: android.content.Context, path: String) {
    try {
        val file = File(path)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri) ?: "*/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享"))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "无法分享", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun FileTypeIcon(item: FileEntry) {
    val icon: ImageVector
    val tint: Color
    when {
        item.isDir -> {
            icon = Icons.Filled.Folder
            tint = FolderAmber
        }
        item.kind == EntryKind.GNBOOK -> {
            icon = Icons.AutoMirrored.Filled.MenuBook
            tint = MoonBlue
        }
        item.kind == EntryKind.EPUB -> {
            icon = Icons.Filled.MenuBook
            tint = Color(0xFF1565C0)
        }
        item.kind == EntryKind.PDF -> {
            icon = Icons.Filled.PictureAsPdf
            tint = Color(0xFFC62828)
        }
        item.kind == EntryKind.TXT -> {
            icon = Icons.Filled.Description
            tint = Color(0xFF546E7A)
        }
        item.kind == EntryKind.IMAGE -> {
            icon = Icons.Filled.Image
            tint = Color(0xFF6A1B9A)
        }
        else -> {
            icon = Icons.AutoMirrored.Filled.InsertDriveFile
            tint = Color(0xFF78909C)
        }
    }
    Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
}

private fun buildCrumbs(path: String, sdRoot: String): List<PathShortcut> {
    val out = mutableListOf<PathShortcut>()
    var f: File? = File(path)
    val chain = mutableListOf<File>()
    while (f != null) {
        chain.add(f)
        if (f.absolutePath.trimEnd('/') == sdRoot.trimEnd('/')) break
        if (f.parentFile == null || f.parentFile?.absolutePath == f.absolutePath) break
        f = f.parentFile
    }
    chain.asReversed().forEachIndexed { i, file ->
        val label = when {
            i == 0 && file.absolutePath.trimEnd('/') == sdRoot.trimEnd('/') -> "sdcard"
            else -> file.name.ifBlank { "/" }
        }
        out += PathShortcut(label, file.absolutePath)
    }
    if (out.isEmpty()) out += PathShortcut("sdcard", sdRoot)
    return out
}


private fun searchFiles(
    root: String,
    query: String,
    mode: String,
    formats: Set<String>,
): List<FileEntry> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val out = mutableListOf<FileEntry>()
    fun walk(dir: File, depth: Int) {
        if (depth > 8 || out.size >= 200) return
        val kids = dir.listFiles() ?: return
        for (f in kids) {
            if (f.isDirectory) {
                if (mode == "name" && f.name.lowercase().contains(q)) {
                    out += FileEntry(f.name, f.absolutePath, true, 0, f.lastModified(), EntryKind.DIR)
                }
                walk(f, depth + 1)
            } else {
                val ext = f.extension.lowercase()
                if (formats.isNotEmpty() && ext !in formats) continue
                val nameHit = f.name.lowercase().contains(q)
                val textHit = if (mode == "fulltext" && ext in setOf("txt","md","json","html","htm","xml","css","js")) {
                    try {
                        f.bufferedReader().use { it.readText().lowercase().contains(q) }
                    } catch (_: Exception) { false }
                } else false
                if (nameHit || textHit) {
                    out += FileEntry(f.name, f.absolutePath, false, f.length(), f.lastModified(), FileKinds.of(f.name, false))
                }
            }
            if (out.size >= 200) return
        }
    }
    walk(File(root), 0)
    return out
}

private fun shareMultiple(context: android.content.Context, paths: List<String>) {
    val files = paths.map { File(it) }.filter { it.isFile }
    if (files.isEmpty()) {
        Toast.makeText(context, "请选择文件（文件夹请先压缩）", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uris = ArrayList<android.net.Uri>()
        files.forEach { f ->
            uris += androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", f,
            )
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, uris)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "分享"))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "无法分享", Toast.LENGTH_SHORT).show()
    }
}

private fun openWithOtherApp(context: android.content.Context, path: String) {
    try {
        val file = File(path)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "用其他软件打开"))
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "无法打开", Toast.LENGTH_SHORT).show()
    }
}

private fun openFolderInOtherApp(context: android.content.Context, path: String) {
    try {
        val uri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:" +
            path.removePrefix("/storage/emulated/0/").trimStart('/'))
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // fallback: send as text path for apps like get_norvels via share
        try {
            context.startActivity(android.content.Intent.createChooser(intent, "打开文件夹"))
        } catch (_: Exception) {
            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, path)
            }
            context.startActivity(android.content.Intent.createChooser(share, "分享文件夹路径"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: "无法打开文件夹", Toast.LENGTH_SHORT).show()
    }
}

private fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
