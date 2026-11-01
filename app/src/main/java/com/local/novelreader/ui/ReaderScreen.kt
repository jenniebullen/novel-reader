package com.local.novelreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider as MdSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.local.novelreader.data.Book
import com.local.novelreader.data.Chapter
import com.local.novelreader.data.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 渲染请求：章节序号 + 正文 + 起始偏移。视图只在它变化时重建排版。 */
private data class RenderReq(val chapter: Int, val text: String, val offset: Int)

@Composable
fun ReaderScreen(
    repo: LibraryRepository,
    book: Book,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var chapterIdx by remember {
        mutableIntStateOf(book.progressChapter.coerceIn(0, (book.chapterCount - 1).coerceAtLeast(0)))
    }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var req by remember { mutableStateOf<RenderReq?>(null) }
    var viewRef by remember { mutableStateOf<ReaderView?>(null) }

    var menuVisible by remember { mutableStateOf(false) }
    var tocVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var bookmarksVisible by remember { mutableStateOf(false) }

    var pageInfo by remember { mutableStateOf(1 to 1) }
    var chapterFraction by remember { mutableFloatStateOf(0f) }
    var pendingOffset by remember { mutableIntStateOf(book.progressOffset) }
    var pendingFraction by remember { mutableFloatStateOf(0f) }

    var themeIndex by remember { mutableIntStateOf(ReaderPrefs.themeIndex(context)) }
    var fontSp by remember { mutableFloatStateOf(ReaderPrefs.fontSp(context)) }
    var lineSpacing by remember { mutableFloatStateOf(ReaderPrefs.lineSpacing(context)) }
    var tapVertical by remember { mutableStateOf(ReaderPrefs.tapVertical(context)) }
    var pageAnim by remember { mutableIntStateOf(ReaderPrefs.pageAnim(context)) }
    var scrollMode by remember { mutableStateOf(ReaderPrefs.scrollMode(context)) }
    var autoSeconds by remember { mutableIntStateOf(ReaderPrefs.autoTurnSeconds(context)) }
    var autoPlaying by remember { mutableStateOf(false) }

    var sliderValue by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    val chapterState = rememberUpdatedState(chapterIdx)
    val theme = ReaderThemes.of(themeIndex)
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val globalFraction = if (book.chapterCount <= 0) 0f
    else ((chapterIdx + chapterFraction) / book.chapterCount).coerceIn(0f, 1f)

    LaunchedEffect(Unit) {
        chapters = withContext(Dispatchers.IO) { repo.chapters(book.id) }
    }

    LaunchedEffect(chapterIdx) {
        val text = withContext(Dispatchers.IO) { repo.readChapter(book, chapterIdx) }
        val offset = if (pendingFraction > 0f) (pendingFraction * text.length).toInt() else pendingOffset
        pendingOffset = 0
        pendingFraction = 0f
        req = RenderReq(chapterIdx, text, offset)
    }

    // 自动翻页：到点就翻，翻到底自动进下一章（turnNext 内部会触发）
    LaunchedEffect(autoPlaying, autoSeconds, chapterIdx) {
        if (!autoPlaying || autoSeconds <= 0) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(autoSeconds * 1000L)
            viewRef?.turnNext()
        }
    }

    fun openChapter(idx: Int, atEnd: Boolean = false) {
        if (idx < 0 || idx >= book.chapterCount) {
            autoPlaying = false // 到头了，自动翻页停住
            return
        }
        pendingOffset = if (atEnd) Int.MAX_VALUE else 0
        pendingFraction = 0f
        chapterIdx = idx
        tocVisible = false
        menuVisible = false
    }

    /** 跳到某章内的具体字符位置（搜索命中、书签）。 */
    fun openChapterAt(idx: Int, offset: Int) {
        if (idx < 0 || idx >= book.chapterCount) return
        if (idx == chapterIdx) {
            viewRef?.goToOffset(offset)
        } else {
            pendingOffset = offset
            pendingFraction = 0f
            chapterIdx = idx
        }
        tocVisible = false
        searchVisible = false
        bookmarksVisible = false
        menuVisible = false
    }

    fun addBookmarkHere() {
        val text = req?.text ?: return
        val offset = viewRef?.currentOffset() ?: 0
        if (offset !in text.indices) return
        val end = (offset + 40).coerceAtMost(text.length)
        val snippet = text.substring(offset, end).replace('\n', ' ').trim()
        scope.launch(Dispatchers.IO) {
            repo.addBookmark(book, chapterIdx, offset, snippet)
        }
    }

    /** 按全书百分比跳转：落在哪一章就算出章内偏移。 */
    fun jumpToPercent(pct: Float) {
        if (book.chapterCount <= 0) return
        val target = pct.coerceIn(0f, 1f) * book.chapterCount
        val targetChapter = target.toInt().coerceIn(0, book.chapterCount - 1)
        val fraction = (target - targetChapter).coerceIn(0f, 1f)
        if (targetChapter == chapterIdx) {
            val len = req?.text?.length ?: 0
            if (len > 0) viewRef?.goToOffset((fraction * len).toInt())
        } else {
            pendingFraction = fraction
            pendingOffset = 0
            chapterIdx = targetChapter
        }
    }

    fun saveProgress(offset: Int, fraction: Float) {
        chapterFraction = fraction
        val len = req?.text?.length ?: 0
        val pct = repo.progressPercent(book, chapterState.value, offset, len)
        scope.launch(Dispatchers.IO) {
            repo.saveProgress(book.id, chapterState.value, offset, pct)
        }
    }

    BackHandler {
        when {
            searchVisible -> searchVisible = false
            bookmarksVisible -> bookmarksVisible = false
            settingsVisible -> settingsVisible = false
            tocVisible -> tocVisible = false
            menuVisible -> menuVisible = false
            else -> onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.backgroundCompose)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ReaderView(ctx).apply {
                    onMenu = { menuVisible = !menuVisible }
                    onNextChapter = { openChapter(chapterState.value + 1) }
                    onPrevChapter = { openChapter(chapterState.value - 1, atEnd = true) }
                    onPageChanged = { page, total -> pageInfo = page to total }
                    onProgressChanged = { offset, fraction -> saveProgress(offset, fraction) }
                    viewRef = this
                }
            },
            update = { view ->
                view.textSizePx = with(density) { fontSp.sp.toPx() }
                view.pageColor = theme.background
                view.textColor = theme.text
                view.lineSpacingMult = lineSpacing
                view.tapVertical = tapVertical
                view.pageAnim = pageAnim
                view.scrollMode = scrollMode
                view.horizontalPadding = with(density) { 22.dp.toPx() }
                view.topPadding = with(density) { statusBarPadding.toPx() + 12.dp.toPx() }
                view.bottomPadding = with(density) { navBarPadding.toPx() + 30.dp.toPx() }
                val current = req
                if (current != null && view.tag != current) {
                    view.tag = current
                    view.setContent(current.text, current.offset)
                }
            }
        )

        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            ReaderTopBar(
                visible = menuVisible,
                title = chapters.firstOrNull { it.idx == chapterIdx }?.title ?: "第 ${chapterIdx + 1} 章",
                pageInfo = pageInfo,
                percent = globalFraction,
                theme = theme,
                statusBarPadding = statusBarPadding,
                onBack = onBack,
                onSearch = { searchVisible = true },
                onBookmarks = { bookmarksVisible = true },
                onToc = { tocVisible = true }
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            ReaderBottomBar(
                visible = menuVisible,
                theme = theme,
                navBarPadding = navBarPadding,
                globalFraction = if (dragging) sliderValue else globalFraction,
                onSliderChange = { sliderValue = it; dragging = true },
                onSliderFinished = {
                    jumpToPercent(sliderValue)
                    dragging = false
                },
                onPrevChapter = { openChapter(chapterIdx - 1, atEnd = true) },
                onNextChapter = { openChapter(chapterIdx + 1) },
                onToc = { tocVisible = true },
                autoPlaying = autoPlaying,
                onAutoToggle = {
                    if (autoSeconds == 0) settingsVisible = true else autoPlaying = !autoPlaying
                },
                onSettings = { settingsVisible = true }
            )
        }
    }

    if (settingsVisible) {
        ReaderSettingsDialog(
            themeIndex = themeIndex,
            fontSp = fontSp,
            lineSpacing = lineSpacing,
            tapVertical = tapVertical,
            pageAnim = pageAnim,
            autoTurnSeconds = autoSeconds,
            scrollMode = scrollMode,
            onThemeChange = { themeIndex = it; ReaderPrefs.setThemeIndex(context, it) },
            onFontChange = { fontSp = it; ReaderPrefs.setFontSp(context, it) },
            onLineSpacingChange = { lineSpacing = it; ReaderPrefs.setLineSpacing(context, it) },
            onTapModeChange = { tapVertical = it; ReaderPrefs.setTapVertical(context, it) },
            onPageAnimChange = { pageAnim = it; ReaderPrefs.setPageAnim(context, it) },
            onAutoTurnChange = {
                autoSeconds = it
                ReaderPrefs.setAutoTurnSeconds(context, it)
                if (it == 0) autoPlaying = false
            },
            onScrollModeChange = {
                scrollMode = it
                ReaderPrefs.setScrollMode(context, it)
            },
            onDismiss = { settingsVisible = false }
        )
    }

    if (tocVisible) {
        ChapterListDialog(
            chapters = chapters,
            currentChapter = chapterIdx,
            onSelect = { openChapter(it) },
            onDismiss = { tocVisible = false }
        )
    }

    if (searchVisible) {
        SearchDialog(
            repo = repo,
            book = book,
            onJump = { chapter, offset -> openChapterAt(chapter, offset) },
            onDismiss = { searchVisible = false }
        )
    }

    if (bookmarksVisible) {
        BookmarksDialog(
            repo = repo,
            bookId = book.id,
            onAddCurrent = { addBookmarkHere() },
            onJump = { chapter, offset -> openChapterAt(chapter, offset) },
            onDismiss = { bookmarksVisible = false }
        )
    }
}

@Composable
private fun ReaderTopBar(
    visible: Boolean,
    title: String,
    pageInfo: Pair<Int, Int>,
    percent: Float,
    theme: ReaderTheme,
    statusBarPadding: Dp,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onToc: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Surface(color = theme.backgroundCompose.copy(alpha = 0.97f), tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = statusBarPadding)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = theme.textCompose)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = theme.textCompose, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(
                        "第 ${pageInfo.first}/${pageInfo.second} 页 · 全书 ${(percent * 100).toInt()}%",
                        color = theme.textCompose.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索", tint = theme.textCompose)
                }
                IconButton(onClick = onBookmarks) {
                    Icon(Icons.Filled.Star, contentDescription = "书签", tint = theme.textCompose)
                }
                IconButton(onClick = onToc) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "目录", tint = theme.textCompose)
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    visible: Boolean,
    theme: ReaderTheme,
    navBarPadding: Dp,
    globalFraction: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onToc: () -> Unit,
    autoPlaying: Boolean,
    onAutoToggle: () -> Unit,
    onSettings: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Surface(color = theme.backgroundCompose.copy(alpha = 0.97f), tonalElevation = 3.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = navBarPadding)) {
                HorizontalDivider(color = theme.textCompose.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MdSlider(
                        value = globalFraction.coerceIn(0f, 1f),
                        onValueChange = onSliderChange,
                        onValueChangeFinished = onSliderFinished,
                        modifier = Modifier.weight(1f),
                        valueRange = 0f..1f
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onPrevChapter) { Text("上一章", color = theme.textCompose) }
                    TextButton(onClick = onToc) { Text("目录", color = theme.textCompose) }
                    TextButton(onClick = onNextChapter) { Text("下一章", color = theme.textCompose) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onAutoToggle) {
                        Text(if (autoPlaying) "暂停" else "自动", color = theme.textCompose)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置", tint = theme.textCompose)
                    }
                }
            }
        }
    }
}
