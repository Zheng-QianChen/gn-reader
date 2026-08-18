package com.gnreader.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.webdav.FolderPairSync
import com.gnreader.app.data.webdav.SyncPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf(settings.libraryRoot) }
    var davUrl by remember { mutableStateOf(settings.webdavUrl) }
    var davUser by remember { mutableStateOf(settings.webdavUser) }
    var davPass by remember { mutableStateOf(settings.webdavPass) }
    var defaultEnc by remember { mutableStateOf(settings.defaultEncoding) }
    var newEnc by remember { mutableStateOf("") }
    var pageMode by remember { mutableStateOf(settings.pageMode) }
    var showPara by remember { mutableStateOf(settings.showParagraphComments) }
    var showCh by remember { mutableStateOf(settings.showChapterComments) }
    var encodings by remember { mutableStateOf(settings.allEncodings()) }
    var pairs by remember { mutableStateOf(settings.syncPairs().toMutableList()) }
    var excludeText by remember { mutableStateOf(settings.syncExcludeRules) }
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("书库根目录（对齐 getter 的 get_norvels）", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = library, onValueChange = { library = it }, modifier = Modifier.fillMaxWidth())

            Text("阅读", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = pageMode == "scroll",
                    onClick = { pageMode = "scroll" },
                    label = { Text("滚动") },
                )
                FilterChip(
                    selected = pageMode == "page",
                    onClick = { pageMode = "page" },
                    label = { Text("分页") },
                )
            }
            Text("评论显示", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showPara,
                    onClick = { showPara = !showPara },
                    label = { Text("段评") },
                )
                FilterChip(
                    selected = showCh,
                    onClick = { showCh = !showCh },
                    label = { Text("章评") },
                )
            }
            Text("段评/章评数据来自 gnbook；是否展示由本开关控制。", style = MaterialTheme.typography.bodySmall)

            Text("文本编码", style = MaterialTheme.typography.titleSmall)
            Text(
                "此处仅为「新打开文本」的默认编码；每个文件的编码保存在该文件自己的阅读记录里，打开 TXT 时点顶栏编码可单独切换并记住。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = defaultEnc,
                onValueChange = { defaultEnc = it },
                label = { Text("默认编码（仅新文件）") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("可用编码（VS Code 常用集 + 自定义）", style = MaterialTheme.typography.bodySmall)
            Text(encodings.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newEnc,
                    onValueChange = { newEnc = it },
                    label = { Text("新增编码名，如 IBM866") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(onClick = {
                    settings.addEncoding(newEnc)
                    encodings = settings.allEncodings()
                    newEnc = ""
                }) { Text("添加") }
            }

            Text("WebDAV（坚果云等）", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = davUrl, onValueChange = { davUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = davUser, onValueChange = { davUser = it }, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = davPass, onValueChange = { davPass = it }, label = { Text("应用密码") }, modifier = Modifier.fillMaxWidth())

            Text("文件夹映射同步", style = MaterialTheme.typography.titleSmall)
            Text(
                "本地目录 ↔ 云端目录。例：本地 /storage/emulated/0/BOOKS 映射云端 /BOOKS。未配置时回退为书库根 ↔ /get_norvels。",
                style = MaterialTheme.typography.bodySmall,
            )
            pairs.forEachIndexed { idx, pair ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = pair.enabled,
                                onCheckedChange = { on ->
                                    pairs = pairs.toMutableList().also {
                                        it[idx] = pair.copy(enabled = on)
                                    }
                                },
                            )
                            Text("启用", modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                pairs = pairs.toMutableList().also { it.removeAt(idx) }
                            }) { Icon(Icons.Default.Delete, "删除") }
                        }
                        OutlinedTextField(
                            value = pair.name,
                            onValueChange = { v ->
                                pairs = pairs.toMutableList().also { it[idx] = pair.copy(name = v) }
                            },
                            label = { Text("名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = pair.local,
                            onValueChange = { v ->
                                pairs = pairs.toMutableList().also { it[idx] = pair.copy(local = v) }
                            },
                            label = { Text("本地路径") },
                            placeholder = { Text("/storage/emulated/0/BOOKS") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = pair.remote,
                            onValueChange = { v ->
                                pairs = pairs.toMutableList().also { it[idx] = pair.copy(remote = v) }
                            },
                            label = { Text("云端路径") },
                            placeholder = { Text("/BOOKS") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    val next = SyncPair(
                        id = "pair_${System.currentTimeMillis()}",
                        name = if (pairs.isEmpty()) "书库" else "映射${pairs.size + 1}",
                        local = if (pairs.isEmpty()) library.ifBlank { settings.libraryRoot } else "/storage/emulated/0/BOOKS",
                        remote = if (pairs.isEmpty()) "/get_norvels" else "/BOOKS",
                        enabled = true,
                    )
                    pairs = pairs.toMutableList().also { it.add(next) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("添加映射")
            }
            OutlinedTextField(
                value = excludeText,
                onValueChange = { excludeText = it },
                label = { Text("排除规则（每行一条）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = {
                    // 先落盘再同步
                    settings.libraryRoot = library.trim()
                    settings.webdavUrl = davUrl.trim()
                    settings.webdavUser = davUser.trim()
                    settings.webdavPass = davPass
                    settings.setSyncPairs(pairs)
                    settings.syncExcludeRules = excludeText
                    syncing = true
                    syncMsg = "同步中…"
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                FolderPairSync.syncAll(
                                    baseUrl = settings.webdavUrl,
                                    username = settings.webdavUser,
                                    password = settings.webdavPass,
                                    pairs = settings.effectiveSyncPairs(),
                                    excludeRules = excludeText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                                ) { msg -> syncMsg = msg }
                            }
                            syncMsg =
                                "完成：↑${result.uploaded.size} ↓${result.downloaded.size} " +
                                    "移${result.moved.size} 跳过${result.skipped.size} 冲突${result.conflicts.size} 错${result.errors.size}"
                            Toast.makeText(context, syncMsg, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            syncMsg = e.message
                            Toast.makeText(context, e.message ?: "同步失败", Toast.LENGTH_LONG).show()
                        } finally {
                            syncing = false
                        }
                    }
                },
                enabled = !syncing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (syncing) "同步中…" else "立即同步映射") }
            OutlinedButton(
                onClick = {
                    settings.webdavUrl = davUrl.trim()
                    settings.webdavUser = davUser.trim()
                    settings.webdavPass = davPass
                    settings.setSyncPairs(pairs)
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
                            syncMsg = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            syncMsg = "连接失败: ${e.message}"
                            Toast.makeText(context, syncMsg, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("测试 WebDAV 连接") }
            if (syncMsg != null) {
                Text(syncMsg!!, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    settings.libraryRoot = library.trim()
                    settings.webdavUrl = davUrl.trim()
                    settings.webdavUser = davUser.trim()
                    settings.webdavPass = davPass
                    settings.setSyncPairs(pairs)
                    settings.syncExcludeRules = excludeText
                    settings.defaultEncoding = defaultEnc.trim().ifBlank { "UTF-8" }
                    settings.pageMode = pageMode
                    settings.showParagraphComments = showPara
                    settings.showChapterComments = showCh
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存") }
        }
    }
}
