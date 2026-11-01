package com.local.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 阅读设置：背景、字号、行距、点击区域、翻页动画、自动翻页。改动即时生效并由调用方持久化。 */
@Composable
fun ReaderSettingsDialog(
    themeIndex: Int,
    fontSp: Float,
    lineSpacing: Float,
    tapVertical: Boolean,
    pageAnim: Int,
    autoTurnSeconds: Int,
    scrollMode: Boolean,
    onThemeChange: (Int) -> Unit,
    onFontChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onTapModeChange: (Boolean) -> Unit,
    onPageAnimChange: (Int) -> Unit,
    onAutoTurnChange: (Int) -> Unit,
    onScrollModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读设置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("阅读模式", fontSize = 13.sp)
                Row {
                    TextButton(onClick = { onScrollModeChange(false) }) {
                        Text(if (scrollMode) "整页翻页" else "整页翻页 ✓")
                    }
                    TextButton(onClick = { onScrollModeChange(true) }) {
                        Text(if (scrollMode) "上下滚动 ✓" else "上下滚动")
                    }
                }
                Text("背景", fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ReaderThemes.all.forEachIndexed { index, theme ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(theme.background), CircleShape)
                                .border(
                                    width = if (index == themeIndex) 3.dp else 1.dp,
                                    color = if (index == themeIndex) Color(theme.text) else Color(0x33000000),
                                    shape = CircleShape
                                )
                                .clickable { onThemeChange(index) }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))

                Text("字号 ${fontSp.toInt()}", fontSize = 13.sp)
                Row {
                    TextButton(onClick = { onFontChange((fontSp - 1f).coerceAtLeast(12f)) }) { Text("A-") }
                    TextButton(onClick = { onFontChange((fontSp + 1f).coerceAtMost(36f)) }) { Text("A+") }
                }

                Text("行距 ${"%.2f".format(lineSpacing)}", fontSize = 13.sp)
                Row {
                    TextButton(onClick = { onLineSpacingChange((lineSpacing - 0.1f).coerceAtLeast(1.2f)) }) { Text("紧") }
                    TextButton(onClick = { onLineSpacingChange((lineSpacing + 0.1f).coerceAtMost(2.4f)) }) { Text("疏") }
                }

                Spacer(Modifier.height(6.dp))
                Text("翻页动画", fontSize = 13.sp)
                Row {
                    val labels = listOf("无", "滑动", "覆盖")
                    labels.forEachIndexed { index, label ->
                        TextButton(onClick = { onPageAnimChange(index) }) {
                            Text(if (pageAnim == index) "$label ✓" else label)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("点击区域", fontSize = 13.sp)
                Row {
                    TextButton(onClick = { onTapModeChange(false) }) {
                        Text(if (tapVertical) "左右翻页" else "左右翻页 ✓")
                    }
                    TextButton(onClick = { onTapModeChange(true) }) {
                        Text(if (tapVertical) "上下翻页 ✓" else "上下翻页")
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("自动翻页", fontSize = 13.sp)
                Row {
                    listOf(0, 10, 20, 30).forEach { seconds ->
                        TextButton(onClick = { onAutoTurnChange(seconds) }) {
                            val label = if (seconds == 0) "关" else "${seconds}秒"
                            Text(if (autoTurnSeconds == seconds) "$label ✓" else label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
