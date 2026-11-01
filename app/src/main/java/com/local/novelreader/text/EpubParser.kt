package com.local.novelreader.text

import android.text.Html
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 极简 EPUB 解析：只取章节文本，不还原原版式（图片、CSS 一律忽略）。
 * 优点是对各种排版怪异的 EPUB 容忍度高，且和 TXT 走同一套分页渲染。
 */
object EpubParser {

    data class EpubBook(val title: String, val author: String, val chapters: List<EpubChapter>)
    data class EpubChapter(val title: String, val text: String)

    fun parse(file: File, fallbackTitle: String): EpubBook {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = parseXml(zip, opfPath)

            val manifest = manifestItems(opf)
            val spineIds = spineItemIds(opf)
            val tocTitles = readTocTitles(zip, opfDir, manifest)

            val chapters = mutableListOf<EpubChapter>()
            for (id in spineIds) {
                val item = manifest[id] ?: continue
                if (!item.mediaType.contains("html", ignoreCase = true)) continue
                val entryPath = resolve(opfDir, item.href)
                val raw = zip.getEntry(entryPath)?.let { entry ->
                    zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                } ?: continue

                val text = HtmlText.toPlain(raw).trim()
                if (text.isEmpty()) continue

                val title = tocTitles[entryPath]
                    ?: tocTitles[entryPath.substringAfterLast('/')]
                    ?: HtmlText.firstHeading(raw)
                    ?: "第${chapters.size + 1}章"
                chapters.add(EpubChapter(title, text))
            }

            val meta = readMetadata(opf)
            return EpubBook(
                title = meta.first.ifBlank { fallbackTitle },
                author = meta.second,
                chapters = chapters
            )
        }
    }

    // ---------- OPF ----------

    private class ManifestItem(val href: String, val mediaType: String)

    private fun findOpfPath(zip: ZipFile): String {
        val container = zip.getEntry("META-INF/container.xml") ?: error("不是有效的 EPUB（缺少 container.xml）")
        val doc = zip.getInputStream(container).use { parseDoc(it) }
        val rootfiles = doc.getElementsByTagNameNS("*", "rootfile")
        for (i in 0 until rootfiles.length) {
            val el = rootfiles.item(i) as? Element ?: continue
            val path = el.getAttribute("full-path")
            if (path.isNotBlank()) return path
        }
        error("EPUB 中找不到 OPF 文件")
    }

    private fun manifestItems(opf: Document): Map<String, ManifestItem> {
        val out = mutableMapOf<String, ManifestItem>()
        val items = opf.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val el = items.item(i) as? Element ?: continue
            val id = el.getAttribute("id")
            val href = el.getAttribute("href")
            if (id.isBlank() || href.isBlank()) continue
            out[id] = ManifestItem(href, el.getAttribute("media-type"))
        }
        return out
    }

    private fun spineItemIds(opf: Document): List<String> {
        val out = mutableListOf<String>()
        val refs = opf.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until refs.length) {
            val el = refs.item(i) as? Element ?: continue
            val idref = el.getAttribute("idref")
            if (idref.isNotBlank()) out.add(idref)
        }
        return out
    }

    private fun readMetadata(opf: Document): Pair<String, String> {
        fun first(tag: String): String {
            val nodes = opf.getElementsByTagNameNS("*", tag)
            return if (nodes.length > 0) nodes.item(0).textContent.trim() else ""
        }
        return first("title") to first("creator")
    }

    /** 章节标题：优先 NCX，其次 EPUB3 的 nav 文档。 */
    private fun readTocTitles(zip: ZipFile, opfDir: String, manifest: Map<String, ManifestItem>): Map<String, String> {
        val titles = mutableMapOf<String, String>()
        val ncx = manifest.values.firstOrNull { it.mediaType.contains("ncx") } ?: return titles
        val path = resolve(opfDir, ncx.href)
        val bytes = zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).readBytes() } ?: return titles
        val doc = runCatching { builder().parse(bytes.inputStream()) }.getOrNull() ?: return titles

        val points = doc.getElementsByTagNameNS("*", "navPoint")
        for (i in 0 until points.length) {
            val point = points.item(i) as? Element ?: continue
            val label = point.getElementsByTagNameNS("*", "text").item(0)?.textContent?.trim().orEmpty()
            val content = point.getElementsByTagNameNS("*", "content").item(0) as? Element ?: continue
            val src = content.getAttribute("src")
            if (label.isBlank() || src.isBlank()) continue
            val full = resolve(opfDir, src)
            titles.putIfAbsent(full, label)
            titles.putIfAbsent(full.substringAfterLast('/'), label)
        }
        return titles
    }

    // ---------- 路径 ----------

    private fun resolve(baseDir: String, href: String): String {
        val decoded = try {
            URLDecoder.decode(href, "UTF-8")
        } catch (_: Exception) {
            href
        }
        val cleanPath = decoded.substringBefore('#')
        val parts = mutableListOf<String>()
        if (baseDir.isNotBlank()) parts.addAll(baseDir.split('/').filter { it.isNotEmpty() })
        for (seg in cleanPath.split('/')) {
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    // ---------- XML ----------

    private fun parseXml(zip: ZipFile, path: String): Document {
        val entry = zip.getEntry(path) ?: error("EPUB 缺少文件：$path")
        return zip.getInputStream(entry).use { parseDoc(it) }
    }

    private fun parseDoc(stream: java.io.InputStream): Document = builder().parse(stream)

    private fun builder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return factory.newDocumentBuilder()
    }

    // ---------- HTML ----------

    private object HtmlText {
        fun toPlain(html: String): String {
            var s = html
            s = s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
            s = s.replace(Regex("(?is)<(br|/p|/div|/h[1-6]|/li|/tr|/section|/article)\\s*/?>"), "\n")
            s = s.replace(Regex("(?s)<[^>]+>"), "")
            val decoded = runCatching {
                Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
            }.getOrDefault(s)
            return decoded
                .lines()
                .map { it.replace('\u00A0', ' ').trimEnd() }
                .joinToString("\n")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }

        fun firstHeading(html: String): String? {
            val m = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").find(html) ?: return null
            val text = m.groupValues[1].replace(Regex("(?s)<[^>]+>"), "").trim()
            return text.ifBlank { null }?.take(60)
        }
    }
}
