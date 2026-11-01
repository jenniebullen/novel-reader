package com.local.novelreader.text

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/** 简单可靠的中文 TXT 编码识别：BOM > 严格 UTF-8 校验 > GB18030 兜底。 */
object EncodingDetector {

    fun detect(file: File): Charset {
        val head = ByteArray(minOf(file.length(), 256 * 1024L).toInt())
        file.inputStream().use { it.read(head) }
        return detect(head)
    }

    fun detect(bytes: ByteArray): Charset {
        // BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE
        }
        if (looksLikeUtf16WithoutBom(bytes)) {
            return Charsets.UTF_16LE
        }
        if (bytes.isEmpty()) return Charsets.UTF_8

        // 严格 UTF-8 解码；截断在末尾的多字节字符不算错
        val decoder: CharsetDecoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val probe = trimIncompleteTail(bytes)
        try {
            decoder.decode(ByteBuffer.wrap(probe))
            return Charsets.UTF_8
        } catch (_: CharacterCodingException) {
            // 继续
        }
        return try {
            Charset.forName("GB18030")
        } catch (_: Exception) {
            Charset.forName("GBK")
        }
    }

    private fun looksLikeUtf16WithoutBom(bytes: ByteArray): Boolean {
        if (bytes.size < 64) return false
        var zeroAtOdd = 0
        var zeroAtEven = 0
        val n = minOf(bytes.size, 4096)
        for (i in 0 until n) {
            if (bytes[i] == 0.toByte()) {
                if (i % 2 == 0) zeroAtEven++ else zeroAtOdd++
            }
        }
        val pairs = n / 2
        return zeroAtOdd > pairs * 0.3 || zeroAtEven > pairs * 0.3
    }

    private fun trimIncompleteTail(bytes: ByteArray): ByteArray {
        var end = bytes.size
        var i = end - 1
        var continuation = 0
        while (i >= 0 && bytes[i].toInt() and 0xC0 == 0x80) {
            continuation++
            i--
            if (continuation > 3) return bytes
        }
        if (i < 0) return bytes
        val lead = bytes[i].toInt() and 0xFF
        val expected = when {
            lead and 0x80 == 0x00 -> 1
            lead and 0xE0 == 0xC0 -> 2
            lead and 0xF0 == 0xE0 -> 3
            lead and 0xF8 == 0xF0 -> 4
            else -> 1
        }
        if (continuation < expected) end = i
        return bytes.copyOf(end)
    }
}
