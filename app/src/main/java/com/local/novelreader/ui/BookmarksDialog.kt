package com.local.novelreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.novelreader.data.Bookmark
import com.local.novelreader.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 书签：添加当前位置、跳转、删除。 */
@Composable
fun BookmarksDialog(
    repo: LibraryRepository,
    bookId: Long,
    onAddCurrent: () -> Unit,
    onJump: (chapter: Int, offset: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        bookmarks = withContext(Dispatchers.IO) { repo.bookmarks(bookId) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("书签") },
        text = {
            Column {
                TextButton(onClick = {
                    onAddCurrent()
                    refreshKey++
                }) { Text("＋ 添加当前页书签") }

                Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                    if (bookmarks.isEmpty()) {
                        Text(
                            "还没有书签",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn {
                            items(bookmarks, key = { it.id }) { mark ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJump(mark.chapter, mark.offset)
                                            onDismiss()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("第 ${mark.chapter + 1} 章", fontSize = 13.sp)
                                        if (mark.snippet.isNotBlank()) {
                                            Text(
                                                mark.snippet,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { repo.deleteBookmark(mark.id) }
                                            refreshKey++
                                        }
                                    }) { Text("删除") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
