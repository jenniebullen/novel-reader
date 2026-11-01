package com.local.novelreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.novelreader.data.Book
import com.local.novelreader.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    repo: LibraryRepository,
    onOpen: (Book) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var actionBook by remember { mutableStateOf<Book?>(null) }
    var resplitBook by remember { mutableStateOf<Book?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(refreshKey) {
        books = withContext(Dispatchers.IO) { repo.books() }
    }

    fun runBusy(label: String, block: suspend () -> String) {
        scope.launch {
            busyLabel = label
            busy = true
            val result = runCatching { withContext(Dispatchers.IO) { block() } }
            busy = false
            message = result.getOrElse { "失败：${it.message ?: "未知错误"}" }
            refreshKey++
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busyLabel = "正在导入并分章…"
            busy = true
            var failed: String? = null
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    runCatching { repo.importUri(uri) }
                        .onFailure { failed = it.message ?: "导入失败" }
                }
            }
            busy = false
            message = failed
            refreshKey++
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runBusy("正在备份…") {
            val count = repo.exportBackupTo(uri, ReaderPrefs.exportAll(context))
            "已备份 $count 本书"
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("书架", fontWeight = FontWeight.SemiBold) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("备份书架") },
                                onClick = {
                                    menuOpen = false
                                    backupLauncher.launch(backupFileName())
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("从备份恢复") },
                                onClick = {
                                    menuOpen = false
                                    restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(arrayOf("text/plain", "application/epub+zip", "application/octet-stream", "*/*"))
            }) {
                Icon(Icons.Filled.Add, contentDescription = "导入")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                busy -> BusyOverlay(busyLabel, Modifier.align(Alignment.Center))

                books.isEmpty() -> EmptyShelf(Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onClick = { onOpen(book) },
                            onLongClick = { actionBook = book }
                        )
                    }
                }
            }
        }
    }

    actionBook?.let { book ->
        BookActionDialog(
            book = book,
            onResplit = {
                resplitBook = book
                actionBook = null
            },
            onDelete = {
                runBusy("正在删除…") {
                    repo.delete(book)
                    "已删除《${book.title}》"
                }
                actionBook = null
            },
            onDismiss = { actionBook = null }
        )
    }

    resplitBook?.let { book ->
        ResplitDialog(
            repo = repo,
            book = book,
            onDone = { count ->
                message = "已重新分为 $count 章"
                resplitBook = null
                refreshKey++
            },
            onDismiss = { resplitBook = null }
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份") },
            text = { Text("会清空当前书架，并用备份内容覆盖（包括阅读进度、书签和外观设置）。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    runBusy("正在恢复…") {
                        val result = repo.restoreBackupFrom(uri)
                        ReaderPrefs.applyAll(context, result.prefs)
                        "已恢复 ${result.bookCount} 本书"
                    }
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("取消") }
            }
        )
    }

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("好") } }
        )
    }
}

private fun backupFileName(): String =
    "novel-reader-backup-" + SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()) + ".zip"

@Composable
private fun BusyOverlay(label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(label)
    }
}

@Composable
private fun EmptyShelf(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("书架是空的", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "点右下角 + 导入 TXT / EPUB\n也支持从其他 App 分享文件过来",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
