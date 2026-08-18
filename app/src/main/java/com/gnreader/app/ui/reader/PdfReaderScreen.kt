package com.gnreader.app.ui.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(path: String, onBack: () -> Unit) {
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        try {
            pages = withContext(Dispatchers.IO) {
                val file = File(path)
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val list = mutableListOf<Bitmap>()
                val limit = minOf(renderer.pageCount, 40) // 首版限制页数防 OOM
                for (i in 0 until limit) {
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(
                            page.width * 2,
                            page.height * 2,
                            Bitmap.Config.ARGB_8888,
                        )
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        list += bmp
                    }
                }
                renderer.close()
                pfd.close()
                list
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(path).name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            error != null -> Text(error!!, Modifier.padding(padding).padding(16.dp), color = MaterialTheme.colorScheme.error)
            pages.isEmpty() -> CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(pages.size) { i ->
                    Image(
                        bitmap = pages[i].asImageBitmap(),
                        contentDescription = "第 ${i + 1} 页",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}
