package com.local.novelreader.ui

import androidx.compose.ui.graphics.Color

/** 阅读器配色方案：背景 + 文字。 */
data class ReaderTheme(
    val name: String,
    val background: Int,
    val text: Int
) {
    val backgroundCompose: Color get() = Color(background)
    val textCompose: Color get() = Color(text)
}

object ReaderThemes {
    val all: List<ReaderTheme> = listOf(
        ReaderTheme("羊皮纸", 0xFFF6EFDF.toInt(), 0xFF2B2118.toInt()),
        ReaderTheme("护眼绿", 0xFFE9F0E1.toInt(), 0xFF23301F.toInt()),
        ReaderTheme("夜间黑", 0xFF121212.toInt(), 0xFFB9B9B9.toInt()),
        ReaderTheme("纯白", 0xFFFFFFFF.toInt(), 0xFF1A1A1A.toInt())
    )

    fun of(index: Int): ReaderTheme = all.getOrElse(index) { all[0] }
}
