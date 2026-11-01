package com.local.novelreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.local.novelreader.data.AppDb
import com.local.novelreader.data.LibraryRepository
import com.local.novelreader.text.ChapterSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.Charset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportPipelineTest {

    private val sample = buildString {
        appendLine("《测试之书》")
        appendLine("作者：某某")
        appendLine()
        appendLine("第一章 开端")
        repeat(30) { appendLine("这是第一章的正文内容，用来凑够一些字数并验证分章逻辑是否可靠。") }
        appendLine("第二章 风波")
        repeat(30) { appendLine("第二章的正文，继续凑字数，确保每一章都被正确切分出来。") }
        appendLine()
        appendLine("　　第三章　终局")
        repeat(30) { appendLine("第三章的正文，故事到此结束，全文完。") }
    }

    @Test
    fun importsGbkTxtSplitsChaptersAndReadsBack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))

        val file = File.createTempFile("testbook", ".txt")
        file.writeBytes(sample.toByteArray(Charset.forName("GB18030")))

        val id = repo.importFile(file, "测试之书.txt")
        val book = repo.book(id)!!

        assertEquals("测试之书", book.title)
        assertEquals("某某", book.author)
        val chapters = repo.chapters(id)
        assertEquals(
            "chapterCount=${book.chapterCount} titles=${chapters.map { it.title }}",
            3, book.chapterCount
        )
        assertEquals("GB18030", book.encoding)

        assertEquals(3, chapters.size)
        assertTrue(chapters[0].title.contains("第一章"))
        assertTrue(chapters[1].title.contains("第二章"))
        assertTrue(chapters[2].title.contains("第三章"))

        val text = repo.readChapter(book, 1)
        assertTrue(text.contains("第二章的正文"))

        repo.saveProgress(id, 1, 42, 0.5f)
        val updated = repo.book(id)!!
        assertEquals(1, updated.progressChapter)
        assertEquals(42, updated.progressOffset)
        assertEquals(0.5f, updated.progressPct, 0.001f)

        repo.delete(book)
        assertEquals(0, repo.books().size)
    }

    @Test
    fun dropsTableOfContentsAtHead() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))

        val body = buildString {
            // 书首目录：每行只有一个章节标题
            appendLine("第一章 开始")
            appendLine("第二章 风波")
            appendLine("第三章 终局")
            appendLine("第一章 开始")
            repeat(30) { appendLine("这是第一章的正文内容，比较长，用来确保不会被当成目录行删掉。") }
            repeat(1) { appendLine("第二章 风波") }
            repeat(30) { appendLine("第二章正文内容，也同样足够长，确保分章正确。") }
            repeat(1) { appendLine("第三章 终局") }
            repeat(30) { appendLine("第三章正文内容，故事结束。") }
        }
        val file = File.createTempFile("tocbook", ".txt")
        file.writeBytes(body.toByteArray(Charsets.UTF_8))

        val id = repo.importFile(file, "带目录.txt")
        val book = repo.book(id)!!
        val titles = repo.chapters(id).map { it.title }
        assertEquals("目录行应被清理，实际：$titles", 3, book.chapterCount)
        assertTrue("首章标题不对：$titles", titles[0].contains("第一章"))
    }

    @Test
    fun resplitWithSpecificRuleRebuildsChapters() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))

        // 章节标题没有空格分隔，自动规则可能识别不出，需要手动指定规则
        val body = buildString {
            repeat(20) { appendLine("第一章少年游") }
            repeat(40) { appendLine("第一章的正文内容，写得很长很长，用于测试重新分章。") }
            appendLine("第二章风波起")
            repeat(40) { appendLine("第二章的正文内容，同样很长，确保能够被正确切分。") }
            appendLine("第三章终局")
            repeat(40) { appendLine("第三章的正文内容，故事到此结束。") }
        }
        val file = File.createTempFile("nospace", ".txt")
        file.writeBytes(body.toByteArray(Charsets.UTF_8))

        val id = repo.importFile(file, "无空格标题.txt")
        val book = repo.book(id)!!

        val noSpaceRule = ChapterSplitter.extraRules.first { it.name == "第X章无空格" }
        val count = repo.resplit(book, noSpaceRule)
        val reloaded = repo.book(id)!!
        assertEquals(count, reloaded.chapterCount)
        assertTrue("无空格规则应分出多章，实际 $count", count >= 3)
        assertTrue(repo.readChapter(reloaded, 1).contains("第二章的正文"))
    }

    @Test
    fun splitsHeadingEndingWithQuestionMark() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))

        // 复刻真实书源：上一章以（本章完）结尾，下一章标题以问号收尾且重复出现一行
        val body = buildString {
            appendLine("第51章 天父与救世主")
            repeat(25) { appendLine("第五十一章的正文内容，足够长，用来占位。") }
            appendLine("（本章完）")
            appendLine()
            appendLine("第52章 野比帝皇？")
            appendLine("　　第52章 野比帝皇？")
            repeat(25) { appendLine("第五十二章的正文内容，足够长，用来占位。") }
            appendLine()
            appendLine("第53章 天父与救世主")
            repeat(25) { appendLine("第五十三章的正文内容，足够长，用来占位。") }
        }
        val file = File.createTempFile("qmark", ".txt")
        file.writeBytes(body.toByteArray(Charsets.UTF_8))

        val id = repo.importFile(file, "问号标题.txt")
        val book = repo.book(id)!!
        val titles = repo.chapters(id).map { it.title }
        assertEquals("问号结尾的标题必须能切开，实际：$titles", 3, book.chapterCount)
        assertTrue(titles[1].contains("野比帝皇"))
        assertTrue(repo.readChapter(book, 1).contains("第五十二章的正文"))
        // 第51章不能把第52章的标题吞进去
        assertTrue(!repo.readChapter(book, 0).contains("野比帝皇"))
    }

    @Test
    fun duplicateHeadingsDoNotCreateShellChapters() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))

        // 真实书源常见：目录行 + 真实标题行相邻出现，标题内容略有差异
        val body = buildString {
            appendLine("第48章 署名：奥尔良烤翅")
            appendLine("第48章 署名：奥尔良烤翅")
            repeat(25) { appendLine("第四十八章的正文，讲的是署名这件事，内容足够长。") }
            appendLine("第49章 出门在外，身份是自己给的")
            appendLine("第49章 出门在外，身份是自己给的")
            repeat(25) { appendLine("第四十九章的正文，出门在外身份是自己给的，内容足够长。") }
            appendLine("第50章 名刀电光丸和超人手套")
            appendLine("第50章 名刀.电光丸和超人手套")
            repeat(25) { appendLine("第五十章的正文，名刀电光丸和超人手套，内容足够长。") }
        }
        val file = File.createTempFile("duphead", ".txt")
        file.writeBytes(body.toByteArray(Charsets.UTF_8))

        val id = repo.importFile(file, "重复标题.txt")
        val book = repo.book(id)!!
        val titles = repo.chapters(id).map { it.title }
        assertEquals("空壳章应被清掉，实际：$titles", 3, book.chapterCount)
        assertTrue(titles[0].contains("第48章"))
        assertTrue(titles[1].contains("第49章"))
        assertTrue(titles[2].contains("第50章"))
        assertTrue(repo.readChapter(book, 0).contains("第四十八章的正文"))
    }

    @Test
    fun searchFindsHitsAcrossChapters() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))
        val file = File.createTempFile("searchbook", ".txt")
        file.writeBytes(sample.toByteArray(Charset.forName("GB18030")))
        val id = repo.importFile(file, "搜索测试.txt")
        val book = repo.book(id)!!

        val hits = repo.search(book, "第二章的正文")
        assertTrue("应命中第二章，实际 ${hits.size} 条", hits.isNotEmpty())
        assertEquals(1, hits.first().chapter)
        assertTrue(hits.first().snippet.contains("第二章的正文"))

        assertTrue("空关键词不应有结果", repo.search(book, "   ").isEmpty())
        assertTrue("找不到的词应返回空", repo.search(book, "绝对不存在的词").isEmpty())
    }

    @Test
    fun bookmarksCanBeAddedListedAndDeleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))
        val file = File.createTempFile("markbook", ".txt")
        file.writeBytes(sample.toByteArray(Charsets.UTF_8))
        val id = repo.importFile(file, "书签测试.txt")
        val book = repo.book(id)!!

        val markId = repo.addBookmark(book, 1, 20, "这是一段书签")
        val marks = repo.bookmarks(id)
        assertEquals(1, marks.size)
        assertEquals(1, marks[0].chapter)
        assertEquals(20, marks[0].offset)
        assertTrue(marks[0].snippet.contains("书签"))

        repo.deleteBookmark(markId)
        assertTrue(repo.bookmarks(id).isEmpty())
    }

    @Test
    fun fallsBackToChunkingWhenNoChapterHeadings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = LibraryRepository(context, AppDb(context))

        val file = File.createTempFile("nochapters", ".txt")
        val body = "这是一段没有章节标题的长文本。".repeat(800)
        file.writeBytes(body.toByteArray(Charsets.UTF_8))

        val id = repo.importFile(file, "无章节.txt")
        val book = repo.book(id)!!
        assertTrue("应退化为按字数切分，实际 ${book.chapterCount} 章", book.chapterCount > 1)
    }
}
