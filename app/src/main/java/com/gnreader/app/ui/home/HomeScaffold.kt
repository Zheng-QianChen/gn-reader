package com.gnreader.app.ui.home

import android.app.Activity
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.FavoriteStore
import com.gnreader.app.data.RecentStore
import com.gnreader.app.data.fs.EntryKind
import com.gnreader.app.data.fs.FileKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val MoonBlue = Color(0xFF1976D2)

enum class HomeDest { Home, Favorites }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    settings: AppSettings,
    onOpenFile: (path: String, kind: EntryKind) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFiles: (path: String?) -> Unit,
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var dest by remember { mutableStateOf(HomeDest.Home) }
    val recent = remember { RecentStore(context) }
    val favorites = remember { FavoriteStore(context) }
    var recentItems by remember { mutableStateOf(recent.list()) }
    var favItems by remember { mutableStateOf(favorites.list()) }

    fun refreshHome() {
        recentItems = recent.list()
        favItems = favorites.list()
    }

    fun openParagraphSample() {
        scope.launch {
            try {
                val samplePath = withContext(Dispatchers.IO) {
                    val dir = File(settings.libraryRoot)
                    dir.mkdirs()
                    val out = File(dir, "paragraph-comments-sample.gnbook")
                    if (!out.exists() || out.length() < 100) {
                        context.assets.open("samples/paragraph-comments-sample.gnbook").use { input ->
                            out.outputStream().use { input.copyTo(it) }
                        }
                    }
                    out.absolutePath
                }
                onOpenFile(samplePath, EntryKind.GNBOOK)
            } catch (e: Exception) {
                Toast.makeText(context, "导出样例失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(dest) {
        if (dest == HomeDest.Home || dest == HomeDest.Favorites) refreshHome()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF1976D2))),
                        ),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("GN Reader", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Text("仿静读天下 · 支持 gnbook 段评", color = Color(0xFFBBDEFB), fontSize = 13.sp)
                    }
                }
                DrawerItem(Icons.Default.History, "最近阅读") {
                    dest = HomeDest.Home
                    scope.launch { drawerState.close() }
                }
                DrawerItem(Icons.AutoMirrored.Filled.MenuBook, "我的书架") {
                    dest = HomeDest.Home
                    scope.launch { drawerState.close() }
                }
                DrawerItem(Icons.Default.Folder, "我的文件") {
                    scope.launch { drawerState.close() }
                    onOpenFiles(null)
                }
                DrawerItem(Icons.Default.Star, "收藏目录") {
                    dest = HomeDest.Favorites
                    scope.launch { drawerState.close() }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DrawerItem(Icons.Default.Settings, "设置选项") {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = {
                        scope.launch { drawerState.close() }
                        onOpenSettings()
                    }) { Text("设置") }
                    TextButton(onClick = {
                        (context as? Activity)?.finishAffinity()
                    }) { Text("退出") }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (dest == HomeDest.Favorites) "收藏目录" else "GN Reader") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "菜单")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MoonBlue,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                    windowInsets = WindowInsets.statusBars,
                    actions = {
                        IconButton(onClick = { onOpenFiles(null) }) {
                            Icon(Icons.Default.Folder, "文件", tint = Color.White)
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        recentItems.firstOrNull()?.let {
                            onOpenFile(it.path, FileKinds.of(File(it.path).name, false))
                        } ?: onOpenFiles(null)
                    },
                    containerColor = MoonBlue,
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, "继续阅读", tint = Color.White)
                }
            },
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFFEEEEEE)),
            ) {
                if (dest == HomeDest.Home) {
                    HomeSection("最近阅读", onMore = { }) {
                        if (recentItems.isEmpty()) {
                            Text("暂无最近阅读，去「我的文件」打开书籍，或点下方「段评测试样例」", Modifier.padding(12.dp), color = Color.Gray)
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(recentItems) { book ->
                                    RecentCard(book.title) {
                                        onOpenFile(book.path, FileKinds.of(File(book.path).name, false))
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = { openParagraphSample() },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) { Text("打开段评测试样例 .gnbook") }
                    }
                    HomeSection("我的珍藏", onMore = { dest = HomeDest.Favorites }) {
                        if (favItems.isEmpty()) {
                            Text("在文件浏览器中点星标收藏目录", Modifier.padding(12.dp), color = Color.Gray)
                        } else {
                            Row(
                                Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                favItems.take(12).forEach { fav ->
                                    FolderChip(fav.label, Icons.Default.Star) {
                                        settings.libraryRoot = fav.path
                                        onOpenFiles(fav.path)
                                    }
                                }
                            }
                        }
                    }
                    HomeSection("我的文件", onMore = { onOpenFiles(null) }) {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            val sd = Environment.getExternalStorageDirectory().absolutePath
                            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                            FolderChip("sdcard", Icons.Default.SdCard) {
                                settings.libraryRoot = sd
                                onOpenFiles(sd)
                            }
                            FolderChip("Books", Icons.Default.Folder) {
                                settings.libraryRoot = "$sd/Books"
                                File("$sd/Books").mkdirs()
                                onOpenFiles("$sd/Books")
                            }
                            FolderChip("Documents", Icons.Default.Description) {
                                settings.libraryRoot = docs
                                onOpenFiles(docs)
                            }
                            FolderChip("get_norvels", Icons.AutoMirrored.Filled.MenuBook) {
                                settings.libraryRoot = "$docs/get_norvels"
                                File("$docs/get_norvels").mkdirs()
                                onOpenFiles("$docs/get_norvels")
                            }
                            FolderChip("WebDAV", Icons.Default.Cloud) {
                                onOpenFiles(null)
                            }
                        }
                    }
                } else {
                    favItems.forEach { fav ->
                        ListItem(
                            headlineContent = { Text(fav.label) },
                            supportingContent = { Text(fav.path) },
                            leadingContent = { Icon(Icons.Default.Star, null) },
                            modifier = Modifier
                                .clickable {
                                    settings.libraryRoot = fav.path
                                    onOpenFiles(fav.path)
                                }
                                .background(Color.White),
                        )
                        HorizontalDivider()
                    }
                    if (favItems.isEmpty()) {
                        Text("暂无收藏", Modifier.padding(16.dp))
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun HomeSection(title: String, onMore: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MoonBlue)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onMore) { Text("更多") }
            }
            content()
        }
    }
}

@Composable
private fun RecentCard(title: String, onClick: () -> Unit) {
    Box(
        Modifier
            .width(110.dp)
            .height(150.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF90CAF9))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = Color.Black,
            fontSize = 12.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(8.dp)
                .background(Color(0xCCFFFFFF), RoundedCornerShape(4.dp))
                .padding(6.dp),
        )
    }
}

@Composable
private fun FolderChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFFE3F2FD)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MoonBlue)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}
