package com.local.novelreader

import com.local.novelreader.text.EpubParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpubParserTest {

    @Test
    fun parsesChaptersAndTitlesFromNcx() {
        val epub = createEpub(
            ncx = true,
            chapters = listOf(
                "ch1.xhtml" to "<h1>第一章 开端</h1><p>这是第一章的正文。</p><p>第二段。</p>",
                "ch2.xhtml" to "<h2>第二章 转折</h2><div>第二章的正文内容。</div>"
            )
        )

        val book = EpubParser.parse(epub, "测试书")

        assertEquals(2, book.chapters.size)
        assertEquals("第一章 开端", book.chapters[0].title)
        assertEquals("第二章 转折", book.chapters[1].title)
        assertTrue(book.chapters[0].text.contains("这是第一章的正文"))
        assertTrue(book.chapters[1].text.contains("第二章的正文内容"))
    }

    @Test
    fun usesHeadingWhenNoNcx() {
        val epub = createEpub(
            ncx = false,
            chapters = listOf(
                "a.xhtml" to "<h1>Alpha</h1><p>Some text.</p>",
                "b.xhtml" to "<p>no heading here</p>"
            )
        )

        val book = EpubParser.parse(epub, "无名")
        assertEquals(2, book.chapters.size)
        assertEquals("Alpha", book.chapters[0].title)
        assertEquals("第2章", book.chapters[1].title)
    }

    private fun createEpub(ncx: Boolean, chapters: List<Pair<String, String>>): File {
        val file = File.createTempFile("testbook", ".epub")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
                <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>"""
            )

            val manifestItems = chapters.mapIndexed { i, (name, _) ->
                """<item id="c$i" href="$name" media-type="application/xhtml+xml"/>"""
            }.joinToString("\n")
            val spineItems = chapters.indices.joinToString("\n") { i -> """<itemref idref="c$i"/>""" }
            val ncxItem = if (ncx) """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""" else ""
            val spineAttrs = if (ncx) """ toc="ncx"""" else ""

            put(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>测试书</dc:title>
                    <dc:creator>某作者</dc:creator>
                  </metadata>
                  <manifest>
                    $manifestItems
                    $ncxItem
                  </manifest>
                  <spine$spineAttrs>
                    $spineItems
                  </spine>
                </package>"""
            )

            chapters.forEachIndexed { i, (name, html) ->
                put(
                    "OEBPS/$name",
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml"><head><title>t$i</title></head><body>$html</body></html>"""
                )
            }

            if (ncx) {
                val navPoints = chapters.mapIndexed { i, (name, html) ->
                    val title = Regex("""<h[12][^>]*>(.*?)</h[12]>""").find(html)?.groupValues?.get(1) ?: "第${i + 1}章"
                    """<navPoint id="n$i" playOrder="${i + 1}"><navLabel><text>$title</text></navLabel><content src="$name"/></navPoint>"""
                }.joinToString("\n")
                put(
                    "OEBPS/toc.ncx",
                    """<?xml version="1.0" encoding="UTF-8"?>
                    <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                      <navMap>$navPoints</navMap>
                    </ncx>"""
                )
            }
        }
        return file
    }
}
