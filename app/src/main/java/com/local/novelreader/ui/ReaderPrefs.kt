package com.local.novelreader.ui

import android.content.Context

/** 阅读外观设置（SharedPreferences 持久化）。配色见 [ReaderThemes]。 */
object ReaderPrefs {
    private const val NAME = "reader_prefs"
    private const val DEFAULT_FONT_SP = 19f
    private const val DEFAULT_LINE_SPACING = 1.55f

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun fontSp(ctx: Context): Float = sp(ctx).getFloat("fontSp", DEFAULT_FONT_SP)
    fun setFontSp(ctx: Context, v: Float) = sp(ctx).edit().putFloat("fontSp", v.coerceIn(12f, 36f)).apply()

    fun lineSpacing(ctx: Context): Float = sp(ctx).getFloat("lineSpacing", DEFAULT_LINE_SPACING)
    fun setLineSpacing(ctx: Context, v: Float) = sp(ctx).edit().putFloat("lineSpacing", v.coerceIn(1.0f, 2.4f)).apply()

    fun themeIndex(ctx: Context): Int = sp(ctx).getInt("theme", 0)
    fun setThemeIndex(ctx: Context, v: Int) = sp(ctx).edit().putInt("theme", v).apply()

    /** true = 上下三分屏翻页 */
    fun tapVertical(ctx: Context): Boolean = sp(ctx).getBoolean("tapVertical", false)
    fun setTapVertical(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("tapVertical", v).apply()

    /** 翻页动画：0 无、1 滑动、2 覆盖 */
    fun pageAnim(ctx: Context): Int = sp(ctx).getInt("pageAnim", 1)
    fun setPageAnim(ctx: Context, v: Int) = sp(ctx).edit().putInt("pageAnim", v.coerceIn(0, 2)).apply()

    /** 自动翻页间隔（秒），0 = 关闭 */
    fun autoTurnSeconds(ctx: Context): Int = sp(ctx).getInt("autoTurn", 0)
    fun setAutoTurnSeconds(ctx: Context, v: Int) = sp(ctx).edit().putInt("autoTurn", v).apply()

    /** true = 上下滚动阅读，false = 整页翻页 */
    fun scrollMode(ctx: Context): Boolean = sp(ctx).getBoolean("scrollMode", false)
    fun setScrollMode(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("scrollMode", v).apply()

    /** 备份用：把外观设置导成键值对 */
    fun exportAll(ctx: Context): Map<String, String> = mapOf(
        "fontSp" to fontSp(ctx).toString(),
        "lineSpacing" to lineSpacing(ctx).toString(),
        "theme" to themeIndex(ctx).toString(),
        "tapVertical" to tapVertical(ctx).toString(),
        "pageAnim" to pageAnim(ctx).toString(),
        "autoTurn" to autoTurnSeconds(ctx).toString(),
        "scrollMode" to scrollMode(ctx).toString()
    )

    /** 恢复用：把备份里的设置写回去，非法值直接忽略 */
    fun applyAll(ctx: Context, values: Map<String, String>) {
        values["fontSp"]?.toFloatOrNull()?.let { setFontSp(ctx, it) }
        values["lineSpacing"]?.toFloatOrNull()?.let { setLineSpacing(ctx, it) }
        values["theme"]?.toIntOrNull()?.let { setThemeIndex(ctx, it) }
        values["tapVertical"]?.toBooleanStrictOrNull()?.let { setTapVertical(ctx, it) }
        values["pageAnim"]?.toIntOrNull()?.let { setPageAnim(ctx, it) }
        values["autoTurn"]?.toIntOrNull()?.let { setAutoTurnSeconds(ctx, it) }
        values["scrollMode"]?.toBooleanStrictOrNull()?.let { setScrollMode(ctx, it) }
    }
}
