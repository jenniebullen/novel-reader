package com.local.novelreader

import com.local.novelreader.text.ChapterSplitter
import com.local.novelreader.text.EncodingDetector
import com.local.novelreader.text.TextCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayerTest {

    @Test
    fun recognisesCommonChapterHeadings() {
        val positives = listOf(
            "第一章 初入江湖",
            "第1章 初入江湖",
            "第 12 章",
            "第一百二十三章 大结局",
            "第四节 剑法",
            "第五卷 风起",
            "　　第三章　雨夜",
            "正文 第六章 落水",
            "CHAPTER 7 The End",
            "Chapter 12: Return",
            "楔子",
            "番外 一",
            "【第九章】夜行",
            // 标题结尾带问号/感叹号（真实书里很常见）
            "第52章 野比帝皇？",
            "第39章 9?",
            "第7章 他是谁！",
            // 不换行空格分隔
            "第52章\u00A0野比帝皇"
        )
        for (line in positives) {
            assertTrue("应识别为标题: $line", ChapterSplitter.match(line))
        }
    }

    @Test
    fun rejectsProseLines() {
        val negatives = listOf(
            "他说第一章讲的是江湖。",
            "这就是第一章的全部内容了。",
            "第一章的内容非常精彩，我看了三遍，确实写得好，值得推荐给所有人，你说是不是这样。",
            "他翻到第三章，愣住了",
            "今天天气不错",
            "第一章讲的是江湖。",
            ""
        )
        for (line in negatives) {
            assertFalse("不应识别为标题: $line", ChapterSplitter.match(line))
        }
    }

    @Test
    fun detectsUtf8AndGbk() {
        val text = "第一章 开始\n这是一段中文小说正文，用来测试编码识别。\n"
        assertEquals(Charsets.UTF_8, EncodingDetector.detect(text.toByteArray(Charsets.UTF_8)))
        val gbk = java.nio.charset.Charset.forName("GB18030")
        assertEquals(gbk, EncodingDetector.detect(text.toByteArray(gbk)))
    }

    @Test
    fun detectsUtf16WithBom() {
        val text = "第一章 开始\n正文"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(Charsets.UTF_16LE, EncodingDetector.detect(bytes))

        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
        assertEquals(Charsets.UTF_16BE, EncodingDetector.detect(be))
    }

    @Test
    fun cleanerDropsAdLinesButKeepsProse() {
        val input = "　　正文第一行，正常内容。\n笔趣阁 www.biquge.com\n　　第二行，也是正文。\n本章未完，请点击下一页继续阅读"
        val cleaned = TextCleaner.clean(input)
        assertTrue(cleaned.contains("正文第一行"))
        assertTrue(cleaned.contains("第二行"))
        assertFalse(cleaned.contains("biquge"))
        assertFalse(cleaned.contains("本章未完"))
    }

    @Test
    fun chapterTitleIsCleaned() {
        assertEquals("第九章 夜行", ChapterSplitter.titleOf("【第九章 夜行】"))
    }
}
