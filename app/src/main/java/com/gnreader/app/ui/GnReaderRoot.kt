package com.gnreader.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gnreader.app.data.AppSettings
import com.gnreader.app.data.fs.EntryKind
import com.gnreader.app.ui.browser.FileBrowserScreen
import com.gnreader.app.ui.home.HomeScaffold
import com.gnreader.app.ui.reader.GnbookReaderScreen
import com.gnreader.app.ui.reader.ImageViewerScreen
import com.gnreader.app.ui.reader.PdfReaderScreen
import com.gnreader.app.ui.reader.TextReaderScreen
import com.gnreader.app.ui.settings.SettingsScreen
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val Home = "home"
    const val Files = "files"
    const val Settings = "settings"
    const val Gnbook = "gnbook/{path}"
    const val Text = "text/{path}"
    const val Pdf = "pdf/{path}"
    const val Image = "image/{path}"

    fun enc(path: String): String =
        URLEncoder.encode(path, StandardCharsets.UTF_8.toString())

    fun dec(path: String): String =
        URLDecoder.decode(path, StandardCharsets.UTF_8.toString())
}

@Composable
fun GnReaderRoot(initialPath: String?) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }

    fun openFile(path: String, kind: EntryKind) {
        // 打开文件前记住当前浏览目录（文件所在父目录）
        File(path).parentFile?.absolutePath?.let { parent ->
            if (File(parent).isDirectory) settings.lastBrowsePath = parent
        }
        when (kind) {
            EntryKind.GNBOOK, EntryKind.EPUB -> nav.navigate("gnbook/${Routes.enc(path)}")
            EntryKind.PDF -> nav.navigate("pdf/${Routes.enc(path)}")
            EntryKind.TXT, EntryKind.DOCX, EntryKind.PPT, EntryKind.XLS, EntryKind.MOBI ->
                nav.navigate("text/${Routes.enc(path)}")
            EntryKind.IMAGE -> nav.navigate("image/${Routes.enc(path)}")
            else -> nav.navigate("text/${Routes.enc(path)}")
        }
    }

    fun openFiles(path: String? = null) {
        if (path != null && path.isNotBlank()) {
            settings.lastBrowsePath = path
        }
        nav.navigate(Routes.Files) {
            launchSingleTop = true
        }
    }

    LaunchedEffect(initialPath) {
        val p = initialPath?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val f = File(p)
        if (f.isFile) {
            openFile(p, com.gnreader.app.data.fs.FileKinds.of(f.name, false))
        } else if (f.isDirectory) {
            openFiles(p)
        }
    }

    NavHost(navController = nav, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScaffold(
                settings = settings,
                onOpenFile = { path, kind -> openFile(path, kind) },
                onOpenSettings = { nav.navigate(Routes.Settings) },
                onOpenFiles = { path -> openFiles(path) },
            )
        }
        composable(Routes.Files) {
            FileBrowserScreen(
                settings = settings,
                initialDirPath = null,
                onOpenFile = { path, kind -> openFile(path, kind) },
                onOpenSettings = { nav.navigate(Routes.Settings) },
                onBackToHome = {
                    nav.popBackStack(Routes.Home, inclusive = false)
                },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(settings = settings, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.Gnbook,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = Routes.dec(entry.arguments?.getString("path") ?: "")
            GnbookReaderScreen(path = path, settings = settings, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.Text,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = Routes.dec(entry.arguments?.getString("path") ?: "")
            TextReaderScreen(path = path, settings = settings, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.Pdf,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = Routes.dec(entry.arguments?.getString("path") ?: "")
            PdfReaderScreen(path = path, onBack = { nav.popBackStack() })
        }
        composable(
            Routes.Image,
            arguments = listOf(navArgument("path") { type = NavType.StringType }),
        ) { entry ->
            val path = Routes.dec(entry.arguments?.getString("path") ?: "")
            ImageViewerScreen(path = path, onBack = { nav.popBackStack() })
        }
    }
}
