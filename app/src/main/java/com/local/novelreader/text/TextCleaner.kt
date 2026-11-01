package com.local.novelreader.text

/**
 * 导入时的正文净化：去掉书源塞进来的广告行、网址行。
 * 只删短行（<=60 字），避免误伤正文段落。
 */
object TextCleaner {

    private val AD_KEYWORDS = listOf(
        "本章未完", "请点击下一页", "请收藏本站", "请记住本站", "最新网址", "最新域名",
        "笔趣阁", "顶点小说", "无弹窗", "手机阅读", "手机版阅读", "加入书签",
        "投推荐票", "求月票", "天才一秒记住", "更新最快", "全文阅读", "小说网",
        "首发", "记住网址", "百度搜索"
    )

    fun clean(text: String): String = text
        .lineSequence()
        .map { it.replace('\u00A0', ' ').trimEnd() }
        .filterNot { isAdLine(it) }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun isAdLine(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty() || t.length > 60) return false
        if (t.contains("http://") || t.contains("https://") || t.contains("www.")) return true
        return AD_KEYWORDS.any { t.contains(it) }
    }
}
