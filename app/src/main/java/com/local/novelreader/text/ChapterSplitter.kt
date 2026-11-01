package com.local.novelreader.text

/** 章节识别规则。规则越"像正文标题"越好：短、以章节词开头、不含句末标点。 */
object ChapterSplitter {

    data class Rule(val name: String, val regex: Regex)

    // 章节标题里常见的分隔符：半角/全角空格、冒号、顿号、句点
    private const val SEP = "[\\s\\u3000:：、.．\\-]+"
    private const val NUM = "[0-9０-９零〇一二三四五六七八九十百千万两]"

    /** 自动分章时用验证过的规则 */
    val defaultRules: List<Rule> = listOf(
        Rule(
            "第X章",
            Regex("""^\s*(第\s*${NUM}+\s*[章回节卷集部篇幕话])(?:$SEP(.*))?$""")
        ),
        Rule(
            "正文 第X章",
            Regex("""^\s*(正文\s*第\s*${NUM}+\s*[章回节卷集部篇话])(?:$SEP(.*))?$""")
        ),
        Rule(
            "Chapter N",
            Regex("""^\s*(chapter\s+\d+)(?:[\s.:：\-]+(.*))?$""", RegexOption.IGNORE_CASE)
        ),
        Rule(
            "楔子/序/番外",
            Regex("""^\s*(楔子|序章|序言|序|引子|尾声|后记|完本感言|番外(?:\s*\d+)?)(?:$SEP(.*))?$""")
        ),
        Rule(
            "【第X章】",
            Regex("""^\s*[【\[（(]\s*(第\s*${NUM}+\s*[章回节卷集部篇话])\s*[】\]）)](?:[\s\u3000:：、.．\-]*(.*))?$""")
        )
    )

    /** 手动"重新分章"时可额外选用的规则（精度低一些，适合特殊排版的书） */
    val extraRules: List<Rule> = listOf(
        Rule(
            "第X章无空格",
            Regex("""^\s*(第\s*${NUM}+\s*[章回节卷集部篇话])([^\s。！？，、；：""]{1,24})$""")
        ),
        Rule(
            "卷/部/集",
            Regex("""^\s*(第\s*${NUM}+\s*[卷部集篇])(?:$SEP(.*))?$""")
        )
    )

    val allRules: List<Rule> get() = defaultRules + extraRules

    fun match(line: String, rules: List<Rule> = defaultRules): Boolean {
        val t = normalize(line)
        if (t.isEmpty() || t.length > 60) return false
        // 正文段落以句号/右引号收尾；标题不会。问号、感叹号、逗号都可能出现在真实标题里
        // （例如"第52章 野比帝皇？"、"第49章 出门在外，身份是自己给的"），不能一刀切。
        if (t.endsWith("。") || t.endsWith("”") || t.endsWith("\"")) return false
        return rules.any { it.regex.matches(t) }
    }

    fun titleOf(line: String): String {
        val sb = StringBuilder(line.length)
        for (ch in normalize(line)) sb.append(if (ch in "【】[]（）()") ' ' else ch)
        return sb.toString().replace(Regex("[\\s\\u3000]+"), " ").trim()
    }

    /** 把全角空格、不换行空格、零宽字符统一掉，否则正则和 trim 都会失灵。 */
    private fun normalize(s: String): String = s
        .replace('\u00A0', ' ')
        .replace('\u3000', ' ')
        .replace("\u200B", "")
        .replace("\uFEFF", "")
        .replace('\u2002', ' ')
        .replace('\u2003', ' ')
        .trim()
}
