package com.local.novelreader.data

data class Book(
    val id: Long,
    val title: String,
    val author: String,
    val folder: String,
    val format: String,
    val encoding: String,
    val sizeBytes: Long,
    val chapterCount: Int,
    val addedAt: Long,
    val lastReadAt: Long,
    val progressChapter: Int,
    val progressOffset: Int,
    val progressPct: Float,
    val groupName: String
)

data class Chapter(
    val idx: Int,
    val title: String,
    val charCount: Int,
    /** 章节内容只有标题行、没有正文（书源重复标题行时会产生这种空壳章） */
    val titleOnly: Boolean = false
)

data class Bookmark(
    val id: Long,
    val chapter: Int,
    val offset: Int,
    val snippet: String,
    val createdAt: Long
)

data class SearchHit(
    val chapter: Int,
    val chapterTitle: String,
    val offset: Int,
    val snippet: String
)
