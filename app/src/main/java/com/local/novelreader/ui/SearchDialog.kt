package com.local.novelreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.novelreader.data.LibraryRepository
import com.local.novelreader.data.Book
import com.local.novelreader.data.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 全文搜索：输入关键词，列出命中的章节与上下文，点一条跳过去。 */
@Composable
fun SearchDialog(
    repo: LibraryRepository,
    book: Book,
    onJump: (chapter: Int, offset: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(keyword) {
        if (keyword.trim().length < 1) {
            results = emptyList()
            searched = false
            return@LaunchedEffect
        }
        searching = true
        val hits = withContext(Dispatchers.IO) { repo.search(book, keyword) }
        results = hits
        searching = false
        searched = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("搜索") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    placeholder = { Text("输入要查找的内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                    when {
                        searching -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        results.isEmpty() && searched -> Text(
                            "没有找到「$keyword」",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> LazyColumn {
                            items(results, key = { it.chapter to it.offset }) { hit ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onJump(hit.chapter, hit.offset)
                                            onDismiss()
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp)
                                ) {
                                    Text(hit.chapterTitle, fontSize = 13.sp, maxLines = 1)
                                    Text(
                                        hit.snippet,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
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
