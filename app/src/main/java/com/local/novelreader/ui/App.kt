package com.local.novelreader.ui

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.local.novelreader.data.Book
import com.local.novelreader.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AppColors = lightColorScheme(
    primary = Color(0xFF8C6A43),
    onPrimary = Color.White,
    background = Color(0xFFF7F3EA),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF2B2118),
    onSurfaceVariant = Color(0xFF7A7066)
)

/** 应用根：书架 ↔ 阅读器两个界面，外加处理外部分享进来的文件。 */
@Composable
fun NovelReaderApp(
    repo: LibraryRepository,
    incomingUri: Uri?,
    onIncomingHandled: () -> Unit
) {
    MaterialTheme(colorScheme = AppColors) {
        var openBookId by remember { mutableStateOf<Long?>(null) }
        var currentBook by remember { mutableStateOf<Book?>(null) }
        var incomingError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(incomingUri) {
            val uri = incomingUri ?: return@LaunchedEffect
            val id = runCatching {
                withContext(Dispatchers.IO) { repo.importUri(uri) }
            }.getOrElse {
                incomingError = it.message ?: "导入失败"
                null
            }
            onIncomingHandled()
            if (id != null) openBookId = id
        }

        LaunchedEffect(openBookId) {
            currentBook = openBookId?.let { id -> withContext(Dispatchers.IO) { repo.book(id) } }
        }

        val book = currentBook
        if (book != null) {
            ReaderScreen(repo = repo, book = book, onBack = {
                openBookId = null
                currentBook = null
            })
        } else {
            BookshelfScreen(repo = repo, onOpen = { openBookId = it.id })
        }

        incomingError?.let { msg ->
            AlertDialog(
                onDismissRequest = { incomingError = null },
                title = { Text("导入失败") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { incomingError = null }) { Text("好") }
                }
            )
        }
    }
}
