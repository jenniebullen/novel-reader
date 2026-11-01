package com.local.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.novelreader.data.Book
import com.local.novelreader.data.LibraryRepository
import com.local.novelreader.data.SplitPreview
import com.local.novelreader.text.ChapterSplitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 长按书籍后的操作菜单：重新分章 / 删除。 */
@Composable
fun BookActionDialog(
    book: Book,
    onResplit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title, maxLines = 2) },
        text = { Text("长按书籍可以管理这本小说。") },
        confirmButton = {
            TextButton(onClick = onResplit) { Text("重新分章") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 重新分章：选规则、预览、应用。规则见 [ChapterSplitter.allRules]。 */
@Composable
fun ResplitDialog(
    repo: LibraryRepository,
    book: Book,
    onDone: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var ruleIndex by remember { mutableIntStateOf(-1) }
    var preview by remember { mutableStateOf<SplitPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun selectedRule(): ChapterSplitter.Rule? =
        if (ruleIndex < 0) null else ChapterSplitter.allRules[ruleIndex]

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("重新分章") },
        text = {
            Column {
                Text("选择用于识别章节标题的规则，先预览再应用。", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                RuleRow("自动识别（推荐）", ruleIndex == -1) {
                    ruleIndex = -1
                    preview = null
                }
                ChapterSplitter.allRules.forEachIndexed { index, rule ->
                    RuleRow(rule.name, ruleIndex == index) {
                        ruleIndex = index
                        preview = null
                    }
                }
                Spacer(Modifier.height(10.dp))
                preview?.let { p ->
                    Text("共 ${p.chapterCount} 章", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    p.titles.take(6).forEach { Text("· $it", fontSize = 12.sp, maxLines = 1) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    Text("处理中…", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching {
                                withContext(Dispatchers.IO) { repo.previewSplit(book, selectedRule()) }
                            }.onSuccess { preview = it }
                                .onFailure { error = it.message ?: "预览失败" }
                            busy = false
                        }
                    }
                ) { Text("预览") }

                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching {
                                withContext(Dispatchers.IO) { repo.resplit(book, selectedRule()) }
                            }.onSuccess { onDone(it) }
                                .onFailure {
                                    error = it.message ?: "重新分章失败"
                                    busy = false
                                }
                        }
                    }
                ) { Text("应用") }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun RuleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                )
        )
        Spacer(Modifier.size(10.dp))
        Text(label, fontSize = 14.sp)
    }
}
