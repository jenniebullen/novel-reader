package com.local.novelreader.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 备份 / 恢复。
 *
 * 备份是一个 zip：
 * - `backup.json`：版本号、阅读设置、书架/章节/书签元数据
 * - `books/<folder>/ch_N.txt`：章节正文
 *
 * 恢复会先清空当前书库，再按备份重建（id 保持不变，书签才能对上）。
 */
class BackupManager(private val db: AppDb, private val booksDir: File) {

    data class RestoreResult(val bookCount: Int, val prefs: Map<String, String>)

    fun export(out: OutputStream, prefs: Map<String, String>): Int {
        val root = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("prefs", JSONObject(prefs as Map<*, *>))
        }
        val books = JSONArray()
        for (book in db.books()) {
            val o = JSONObject().apply {
                put("id", book.id)
                put("title", book.title)
                put("author", book.author)
                put("folder", book.folder)
                put("format", book.format)
                put("encoding", book.encoding)
                put("sizeBytes", book.sizeBytes)
                put("addedAt", book.addedAt)
                put("lastReadAt", book.lastReadAt)
                put("progressChapter", book.progressChapter)
                put("progressOffset", book.progressOffset)
                put("progressPct", book.progressPct.toDouble())
                put("groupName", book.groupName)
            }
            o.put("chapters", JSONArray().apply {
                for (c in db.chapters(book.id)) {
                    put(JSONObject().apply {
                        put("idx", c.idx)
                        put("title", c.title)
                        put("charCount", c.charCount)
                    })
                }
            })
            o.put("bookmarks", JSONArray().apply {
                for (m in db.bookmarks(book.id)) {
                    put(JSONObject().apply {
                        put("chapter", m.chapter)
                        put("offset", m.offset)
                        put("snippet", m.snippet)
                        put("createdAt", m.createdAt)
                    })
                }
            })
            books.put(o)
        }
        root.put("books", books)

        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for (book in db.books()) {
                val dir = File(booksDir, book.folder)
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".txt")) {
                        zip.putNextEntry(ZipEntry("books/${book.folder}/${file.name}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        return books.length()
    }

    fun restore(zipFile: File): RestoreResult {
        ZipFile(zipFile).use { zip ->
            val metaEntry = zip.getEntry(META_ENTRY) ?: error("不是有效的备份文件")
            val root = JSONObject(zip.getInputStream(metaEntry).use(InputStream::readBytes).toString(Charsets.UTF_8))
            if (root.optInt("version", 0) > FORMAT_VERSION) throw IllegalStateException("备份文件版本过新")

            val prefs = root.optJSONObject("prefs")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.optString(it) }
            } ?: emptyMap()

            db.clearAll()
            booksDir.deleteRecursively()
            booksDir.mkdirs()

            // 先把章节正文解出来（结构固定：books/<folder>/<file>）
            for (entry in zip.entries()) {
                val name = entry.name
                if (!name.startsWith("books/") || name.endsWith("/")) continue
                val target = File(booksDir, name.removePrefix("books/"))
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }

            val booksArray = root.getJSONArray("books")
            for (i in 0 until booksArray.length()) {
                val o = booksArray.getJSONObject(i)
                val bookId = o.getLong("id")
                val chapters = mutableListOf<Chapter>()
                val chs = o.optJSONArray("chapters") ?: JSONArray()
                for (j in 0 until chs.length()) {
                    val c = chs.getJSONObject(j)
                    chapters.add(Chapter(c.getInt("idx"), c.getString("title"), c.optInt("charCount")))
                }
                db.insertBook(
                    Book(
                        id = bookId,
                        title = o.getString("title"),
                        author = o.optString("author"),
                        folder = o.getString("folder"),
                        format = o.optString("format", "txt"),
                        encoding = o.optString("encoding", "UTF-8"),
                        sizeBytes = o.optLong("sizeBytes"),
                        chapterCount = chapters.size,
                        addedAt = o.optLong("addedAt"),
                        lastReadAt = o.optLong("lastReadAt"),
                        progressChapter = o.optInt("progressChapter"),
                        progressOffset = o.optInt("progressOffset"),
                        progressPct = o.optDouble("progressPct", 0.0).toFloat(),
                        groupName = o.optString("groupName")
                    ),
                    forcedId = bookId
                )
                db.insertChapters(bookId, chapters)

                val marks = o.optJSONArray("bookmarks") ?: JSONArray()
                for (j in 0 until marks.length()) {
                    val m = marks.getJSONObject(j)
                    db.insertBookmark(bookId, m.optInt("chapter"), m.optInt("offset"), m.optString("snippet"))
                }
            }
            return RestoreResult(booksArray.length(), prefs)
        }
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val META_ENTRY = "backup.json"
    }
}
