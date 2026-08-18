package com.gnreader.app.ui.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(path: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(path).name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            factory = {
                android.widget.ImageView(it).apply {
                    adjustViewBounds = true
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    setImageURI(android.net.Uri.fromFile(File(path)))
                }
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}
