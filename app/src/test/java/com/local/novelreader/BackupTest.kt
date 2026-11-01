package com.local.novelreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.local.novelreader.data.AppDb
import com.local.novelreader.data.BackupManager
import com.local.novelreader.data.LibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupTest {

    private val sample = buildString {
        appendLine("第一章 开始")
        repeat(30) { appendLine("第一章的正文内容，用来占位并保证长度。") }
        appendLine("第二章 风波")
        repeat(30) { appendLine("第二章的正文内容，用来占位并保证长度。") }
        appendLine("第三章 终局")
        repeat(30) { appendLine("第三章的正文内容，故事结束。") }
    }

    @Test
    fun backupAndRestoreKeepsLibraryProgressAndBookmarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))

        val file = File.createTempFile("backupbook", ".txt")
        file.writeBytes(sample.toByteArray(Charsets.UTF_8))
        val bookId = repo.importFile(file, "备份之书.txt")
        val book = repo.book(bookId)!!
        repo.saveProgress(bookId, 1, 42, 0.33f)
        repo.addBookmark(book, 0, 5, "重要的书签")

        val manager = BackupManager(AppDb(context), File(context.filesDir, "books"))
        val zip = File.createTempFile("backup", ".zip")
        zip.outputStream().use { manager.export(it, mapOf("fontSp" to "22.0", "theme" to "2")) }

        // 清空后再恢复
        repo.delete(repo.book(bookId)!!)
        assertEquals(0, repo.books().size)

        val result = manager.restore(zip)

        assertEquals(1, result.bookCount)
        assertEquals("22.0", result.prefs["fontSp"])
        assertEquals("2", result.prefs["theme"])

        val restored = repo.book(bookId)!!
        assertEquals("备份之书", restored.title)
        assertEquals(3, restored.chapterCount)
        assertEquals(1, restored.progressChapter)
        assertEquals(42, restored.progressOffset)
        assertTrue(restored.progressPct > 0.3f)

        assertEquals(1, repo.bookmarks(bookId).size)
        assertEquals("重要的书签", repo.bookmarks(bookId)[0].snippet)
        assertTrue(repo.readChapter(restored, 1).contains("第二章的正文"))
    }
}
