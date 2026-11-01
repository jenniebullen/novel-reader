package com.local.novelreader.data

import com.local.novelreader.text.ChapterSplitter
import com.local.novelreader.text.TextCleaner
import java.io.File
import java.nio.charset.Charset

/**
 * 章节文件的读写：把整本书切成一章一个 UTF-8 文件（ch_<idx>.txt）。
 *
 * 之所以不把整本书塞进数据库：大 TXT 也只会占用单章内存，
 * 而且重建全文（重新分章）时可以直接按文件顺序拼回来。
 */
class ChapterStore(private val booksDir: File) {

    fun dirOf(folder: String): File = File(booksDir, folder)

    fun readChapter(folder: String, chapterIdx: Int): String {
        val file = File(dirOf(folder), "ch_$chapterIdx.txt")
        return if (file.exists()) file.readText(Charsets.UTF_8) else MISSING_TEXT
    }

    fun deleteFolder(folder: String) {
        dirOf(folder).deleteRecursively()
    }

    /** 按章节文件顺序拼回整本书正文，用于重新分章。 */
    fun reconstruct(folder: String): String {
        val files = dirOf(folder)
            .listFiles { f -> f.isFile && f.name.startsWith("ch_") && f.name.endsWith(".txt") }
            ?.sortedBy { it.name.removePrefix("ch_").removeSuffix(".txt").toIntOrNull() ?: Int.MAX_VALUE }
            ?: emptyList()
        val sb = StringBuilder()
        for (f in files) sb.append(f.readText(Charsets.UTF_8)).append('\n')
        return sb.toString()
    }

    /**
     * 把源文件按规则切分并写入 [dir]，返回章节列表与解析出的作者。
     * 会顺带清理空壳章（内容只有标题行的章节）。
     */
    fun splitInto(
        source: File,
        charset: Charset,
        dir: File,
        rules: List<ChapterSplitter.Rule>
    ): Pair<List<Chapter>, String> {
        val writer = ChapterWriter(dir, rules)
        writer.writeFrom(source, charset)
        return writer.finish()
    }

    /** 章节已经切好（EPUB）时直接落盘。标题会作为首行写进正文。 */
    fun writePreSplitChapters(dir: File, items: List<Pair<String, String>>): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        for ((title, body) in items) {
            val cleaned = TextCleaner.clean(body)
            if (cleaned.isBlank()) continue
            val content = if (cleaned.startsWith(title)) cleaned else "$title\n\n$cleaned"
            File(dir, "ch_${chapters.size}.txt").writeText(content, Charsets.UTF_8)
            chapters.add(Chapter(chapters.size, title, content.length))
        }
        if (chapters.isEmpty()) {
            File(dir, "ch_0.txt").writeText("（空文件）", Charsets.UTF_8)
            chapters.add(Chapter(0, "开始", 5))
        }
        return chapters
    }

    private class ChapterWriter(
        private val dir: File,
        private val rules: List<ChapterSplitter.Rule>
    ) {
        private val chapters = mutableListOf<Chapter>()
        private val buf = StringBuilder()
        private var index = 0
        private var currentTitle = ""
        private var author = ""
        private var hasChapters = false

        fun writeFrom(file: File, charset: Charset) {
            hasChapters = fileHasChapterLines(file, charset)
            file.bufferedReader(charset).use { reader ->
                var lineNo = 0
                var line = reader.readLine()
                while (line != null) {
                    if (lineNo < 30 && author.isEmpty()) {
                        AUTHOR_REGEX.find(line)?.let { author = it.groupValues[1].trim().take(40) }
                    }
                    when {
                        hasChapters && ChapterSplitter.match(line, rules) -> onHeading(line)
                        hasChapters -> buf.append(line).append('\n')
                        else -> onPlainLine(line)
                    }
                    line = reader.readLine()
                    lineNo++
                }
            }
            if (buf.isNotBlank()) flush()
            if (chapters.isEmpty()) {
                File(dir, "ch_0.txt").writeText(EMPTY_BOOK_TEXT, Charsets.UTF_8)
                chapters.add(Chapter(0, "开始", EMPTY_BOOK_TEXT.length))
            }
        }

        fun finish(): Pair<List<Chapter>, String> {
            val cleaned = cleanChapters()
            return cleaned to author
        }

        private fun onHeading(line: String) {
            if (buf.isNotBlank()) {
                // 书首的书名/作者/来源等短行不单独成章
                if (chapters.isEmpty() && buf.toString().trim().length < 200) {
                    buf.setLength(0)
                    currentTitle = ""
                } else {
                    flush()
                }
            }
            currentTitle = ChapterSplitter.titleOf(line)
            buf.append(line.trim()).append('\n')
        }

        private fun onPlainLine(line: String) {
            buf.append(line).append('\n')
            // 没有章节标题的书按字数切；超长单行也要能切开
            while (buf.length >= CHUNK_SIZE) {
                writeChapter(buf.substring(0, CHUNK_SIZE), "第${index + 1}节")
                buf.delete(0, CHUNK_SIZE)
            }
        }

        private fun flush() {
            if (buf.isBlank()) {
                buf.setLength(0)
                return
            }
            val title = when {
                currentTitle.isNotBlank() -> currentTitle
                chapters.isEmpty() -> "开始"
                else -> "第${index + 1}节"
            }
            writeChapter(buf.toString(), title)
            buf.setLength(0)
            currentTitle = ""
        }

        private fun writeChapter(body: String, title: String) {
            val clean = TextCleaner.clean(body).trim('\n', '\r').trim()
            if (clean.isBlank()) return
            File(dir, "ch_$index.txt").writeText(clean, Charsets.UTF_8)
            val titleOnly = hasChapters && clean.lineSequence().count { it.isNotBlank() } <= 1
            chapters.add(Chapter(index, title, clean.length, titleOnly = titleOnly))
            index++
        }

        /**
         * 丢掉空壳章（内容只有标题行，书源重复标题时会成对出现），
         * 再丢掉书首的书名/作者短前置，最后重排章节文件编号。
         */
        private fun cleanChapters(): List<Chapter> {
            if (chapters.size <= 1) return chapters
            var kept = chapters.filter { !it.titleOnly }
            if (kept.isEmpty()) kept = chapters.take(1)
            var drop = 0
            while (drop < kept.size - 1 && kept[drop].charCount < 60) drop++
            if (drop > 0) kept = kept.drop(drop)
            if (kept.size == chapters.size) return chapters

            for (c in kept) {
                File(dir, "ch_${c.idx}.txt").renameTo(File(dir, "ch_${c.idx}.tmp"))
            }
            dir.listFiles()?.forEach { if (it.name.endsWith(".txt")) it.delete() }
            for ((newIdx, c) in kept.withIndex()) {
                File(dir, "ch_${c.idx}.tmp").renameTo(File(dir, "ch_$newIdx.txt"))
            }
            return kept.mapIndexed { i, c -> c.copy(idx = i) }
        }

        private fun fileHasChapterLines(file: File, charset: Charset): Boolean {
            var found = 0
            var lines = 0
            file.bufferedReader(charset).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    lines++
                    if (ChapterSplitter.match(line, rules)) {
                        found++
                        if (found >= 2) break
                    }
                    line = reader.readLine()
                }
            }
            // 至少两处命中才算"分章过的书"；只有一处且文件很短时也认
            return found >= 2 || (found == 1 && lines < 2000)
        }
    }

    companion object {
        private const val CHUNK_SIZE = 4000
        private const val MISSING_TEXT = "（本章内容缺失）"
        private const val EMPTY_BOOK_TEXT = "（空文件）"
        private val AUTHOR_REGEX = Regex("""^\s*(?:作\s*者|著\s*者|作者名)\s*[:：]\s*(\S.{0,40})$""")
    }
}
