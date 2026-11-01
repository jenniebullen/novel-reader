package com.local.novelreader.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.local.novelreader.text.ChapterSplitter
import com.local.novelreader.text.EncodingDetector
import com.local.novelreader.text.EpubParser
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID

data class SplitPreview(val chapterCount: Int, val titles: List<String>)

/**
 * 书架的业务入口：导入 / 重新分章 / 进度 / 删除。
 * 章节文件的读写交给 [ChapterStore]，元数据交给 [AppDb]。
 */
class LibraryRepository(private val context: Context, private val db: AppDb) {

    private val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    private val store = ChapterStore(booksDir)

    fun books(): List<Book> = db.books()
    fun book(id: Long): Book? = db.book(id)
    fun chapters(bookId: Long): List<Chapter> = db.chapters(bookId)

    fun importUri(uri: Uri): Long {
        val name = queryDisplayName(uri) ?: "未命名.txt"
        val tmp = File.createTempFile("import_", ".tmp", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取该文件")
            return importFile(tmp, name)
        } finally {
            tmp.delete()
        }
    }

    fun importFile(file: File, displayName: String): Long =
        if (displayName.lowercase().endsWith(".epub")) importEpub(file, displayName) else importTxt(file, displayName)

    private fun importTxt(file: File, displayName: String): Long {
        val charset = EncodingDetector.detect(file)
        val folder = UUID.randomUUID().toString()
        val dir = File(booksDir, folder).apply { mkdirs() }
        try {
            val (chapters, author) = store.splitInto(file, charset, dir, ChapterSplitter.defaultRules)
            return createBook(
                title = displayName.substringBeforeLast('.').trim().ifEmpty { "未命名" },
                author = author,
                folder = folder,
                format = "txt",
                encoding = charset.name(),
                sizeBytes = file.length(),
                chapters = chapters
            )
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun importEpub(file: File, displayName: String): Long {
        val fallbackTitle = displayName.substringBeforeLast('.').trim().ifEmpty { "未命名" }
        val parsed = EpubParser.parse(file, fallbackTitle)
        if (parsed.chapters.isEmpty()) throw IOException("EPUB 里没有可解析的正文")
        val folder = UUID.randomUUID().toString()
        val dir = File(booksDir, folder).apply { mkdirs() }
        try {
            val chapters = store.writePreSplitChapters(dir, parsed.chapters.map { it.title to it.text })
            return createBook(
                title = parsed.title.ifBlank { fallbackTitle },
                author = parsed.author,
                folder = folder,
                format = "epub",
                encoding = "UTF-8",
                sizeBytes = file.length(),
                chapters = chapters
            )
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    private fun createBook(
        title: String,
        author: String,
        folder: String,
        format: String,
        encoding: String,
        sizeBytes: Long,
        chapters: List<Chapter>
    ): Long {
        val bookId = db.insertBook(
            Book(
                id = 0,
                title = title,
                author = author,
                folder = folder,
                format = format,
                encoding = encoding,
                sizeBytes = sizeBytes,
                chapterCount = chapters.size,
                addedAt = System.currentTimeMillis(),
                lastReadAt = 0,
                progressChapter = 0,
                progressOffset = 0,
                progressPct = 0f,
                groupName = ""
            )
        )
        db.insertChapters(bookId, chapters)
        return bookId
    }

    fun readChapter(book: Book, chapterIdx: Int): String = store.readChapter(book.folder, chapterIdx)

    /** 全书搜索：逐章扫文本，命中即停（默认最多 50 条）。 */
    fun search(book: Book, query: String, limit: Int = 50): List<SearchHit> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        val regex = runCatching { Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE) }.getOrNull() ?: return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (chapter in db.chapters(book.id)) {
            val text = store.readChapter(book.folder, chapter.idx)
            var from = 0
            while (hits.size < limit) {
                val match = regex.find(text, from) ?: break
                val start = match.range.first
                val end = match.range.last + 1
                hits.add(SearchHit(chapter.idx, chapter.title, start, snippetOf(text, start, end)))
                from = end
            }
            if (hits.size >= limit) break
        }
        return hits
    }

    private fun snippetOf(text: String, start: Int, end: Int): String {
        val from = (start - 24).coerceAtLeast(0)
        val to = (end + 24).coerceAtMost(text.length)
        val prefix = if (from > 0) "…" else ""
        val suffix = if (to < text.length) "…" else ""
        return prefix + text.substring(from, to).replace('\n', ' ') + suffix
    }

    fun addBookmark(book: Book, chapter: Int, offset: Int, snippet: String): Long =
        db.insertBookmark(book.id, chapter, offset, snippet.take(60))

    fun bookmarks(bookId: Long): List<Bookmark> = db.bookmarks(bookId)

    fun deleteBookmark(id: Long) = db.deleteBookmark(id)

    // ---------- 备份 / 恢复 ----------

    fun exportBackupTo(uri: Uri, prefs: Map<String, String>): Int {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            return BackupManager(db, booksDir).export(out, prefs)
        }
        throw IOException("无法写入备份文件")
    }

    fun restoreBackupFrom(uri: Uri): BackupManager.RestoreResult {
        val tmp = File.createTempFile("restore", ".zip", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取备份文件")
            return BackupManager(db, booksDir).restore(tmp)
        } finally {
            tmp.delete()
        }
    }

    fun saveProgress(bookId: Long, chapter: Int, offset: Int, pct: Float) =
        db.updateProgress(bookId, chapter, offset, pct)

    fun progressPercent(book: Book, chapterIdx: Int, offset: Int, chapterLength: Int): Float {
        if (book.chapterCount <= 0) return 0f
        val within = if (chapterLength <= 0) 0f else offset.toFloat() / chapterLength
        return ((chapterIdx + within) / book.chapterCount).coerceIn(0f, 1f)
    }

    fun delete(book: Book) {
        db.deleteBook(book.id)
        store.deleteFolder(book.folder)
    }

    /** 预览某种规则切出来的结果，不动原书。 */
    fun previewSplit(book: Book, rule: ChapterSplitter.Rule?): SplitPreview {
        val text = store.reconstruct(book.folder)
        val tmpDir = newTempDir()
        val tmpFile = File.createTempFile("preview", ".txt", context.cacheDir)
        try {
            tmpFile.writeText(text, Charsets.UTF_8)
            val (chapters, _) = store.splitInto(tmpFile, Charsets.UTF_8, tmpDir, rulesOf(rule))
            return SplitPreview(chapters.size, chapters.take(15).map { it.title })
        } finally {
            tmpFile.delete()
            tmpDir.deleteRecursively()
        }
    }

    /** 用指定规则重新分章，阅读进度按百分比重定位。 */
    fun resplit(book: Book, rule: ChapterSplitter.Rule?): Int {
        val dir = store.dirOf(book.folder)
        val text = store.reconstruct(book.folder)
        dir.listFiles()?.forEach { it.delete() }
        val tmpFile = File.createTempFile("resplit", ".txt", context.cacheDir)
        try {
            tmpFile.writeText(text, Charsets.UTF_8)
            val (chapters, _) = store.splitInto(tmpFile, Charsets.UTF_8, dir, rulesOf(rule))
            db.replaceChapters(book.id, chapters)
            db.updateChapterCount(book.id, chapters.size)
            val pct = book.progressPct.coerceIn(0f, 1f)
            val newChapter = (pct * chapters.size).toInt().coerceIn(0, (chapters.size - 1).coerceAtLeast(0))
            db.updateProgress(book.id, newChapter, 0, pct)
            return chapters.size
        } finally {
            tmpFile.delete()
        }
    }

    private fun rulesOf(rule: ChapterSplitter.Rule?): List<ChapterSplitter.Rule> =
        if (rule == null) ChapterSplitter.defaultRules else listOf(rule)

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return uri.lastPathSegment
    }

    private fun newTempDir(): File = File(context.cacheDir, "tmp_" + UUID.randomUUID()).apply { mkdirs() }
}
