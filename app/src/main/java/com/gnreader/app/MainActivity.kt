package com.gnreader.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.gnreader.app.reader.ReaderKeyBus
import com.gnreader.app.ui.GnReaderRoot
import com.gnreader.app.ui.theme.GnReaderTheme

class MainActivity : ComponentActivity() {
    private var pendingOpenPath: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* no-op; browser will refresh */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestStorageAccess()
        pendingOpenPath = intentPath(intent)

        setContent {
            GnReaderTheme {
                GnReaderRoot(initialPath = pendingOpenPath)
            }
        }
    }

    /**
     * 优先交给阅读页按键映射 / 设置页「按下绑定」。
     * WebView 有焦点时也在此统一拦截音量等实体键。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val capture = ReaderKeyBus.captureListener
        if (capture != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            capture(event)
            return true
        }
        val interceptor = ReaderKeyBus.interceptor
        if (interceptor != null && interceptor(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenPath = intentPath(intent)
    }

    private fun intentPath(intent: Intent?): String? {
        val data = intent?.data ?: return null
        return when (data.scheme) {
            "file" -> data.path
            "content" -> {
                // 复制到缓存再打开
                try {
                    val name = data.lastPathSegment?.substringAfterLast('/') ?: "opened.bin"
                    val out = cacheDir.resolve("imports").apply { mkdirs() }.resolve(name)
                    contentResolver.openInputStream(data)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                    out.absolutePath
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val uri = Uri.parse("package:$packageName")
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                    )
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            val need = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) need += Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT <= 28 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) need += Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (need.isNotEmpty()) permissionLauncher.launch(need.toTypedArray())
        }
    }
}
