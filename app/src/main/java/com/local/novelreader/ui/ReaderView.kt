package com.local.novelreader.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * 阅读视图，两种模式：
 * - 翻页模式：StaticLayout 整章排版，按"每页多少行"切页，支持滑动/覆盖动画；
 * - 滚动模式：同一份排版跟随手指上下滚动，点击左右/上下区域按屏翻动。
 */
@SuppressLint("ClickableViewAccessibility", "InlinedApi")
class ReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // 外观
    var textSizePx: Float = 56f
        set(value) { if (field != value) { field = value; rebuild() } }
    var textColor: Int = 0xFF2B2118.toInt()
        set(value) { if (field != value) { field = value; invalidate() } }
    var pageColor: Int = 0xFFF6EFDF.toInt()
        set(value) { if (field != value) { field = value; invalidate() } }
    var lineSpacingMult: Float = 1.6f
        set(value) { if (field != value) { field = value; rebuild() } }
    var horizontalPadding: Float = 56f
        set(value) { if (field != value) { field = value; rebuild() } }
    var topPadding: Float = 0f
        set(value) { if (field != value) { field = value; rebuild() } }
    var bottomPadding: Float = 0f
        set(value) { if (field != value) { field = value; rebuild() } }

    /** true = 上下三分屏翻页（上/下），false = 左右三分屏 */
    var tapVertical: Boolean = false

    /** 翻页动画：0 无、1 滑动、2 覆盖（仅翻页模式生效） */
    var pageAnim: Int = 1

    /** true = 上下滚动阅读，false = 整页翻页 */
    var scrollMode: Boolean = false
        set(value) {
            if (field != value) {
                // 切模式前先记下当前位置，否则会跳回本章开头
                if (layout != null) pendingAnchor = currentOffset()
                field = value
                rebuild()
            }
        }

    // 回调
    var onPageChanged: ((page: Int, total: Int) -> Unit)? = null
    var onMenu: (() -> Unit)? = null
    var onNextChapter: (() -> Unit)? = null
    var onPrevChapter: (() -> Unit)? = null
    var onProgressChanged: ((offset: Int, fraction: Float) -> Unit)? = null

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        hinting = Paint.HINTING_ON
    }
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
    }

    private var layout: StaticLayout? = null
    private var content: String = ""
    private var anchorOffset = 0
    private var pendingAnchor = -1
    private var lineHeightPx = 0f
    private var linesPerPage = 1
    private var page = 0
    private var totalPages = 1

    // 翻页动画
    private var animFrom = -1
    private var animTo = -1
    private var animT = 0f
    private var animator: ValueAnimator? = null

    // 滚动
    private var scrollY = 0f
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var wasFlinging = false

    // 触摸
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var isDragging = false
    private val touchSlop = 24f

    private val textAreaHeight: Float        get() = (height - topPadding - bottomPadding).coerceAtLeast(1f)

    private val maxScroll: Float
        get() = max(0f, (layout?.height ?: 0).toFloat() - textAreaHeight)

    fun setContent(text: String, offset: Int) {
        content = text
        anchorOffset = if (content.isEmpty()) 0 else offset.coerceIn(0, content.length - 1)
        layout = null
        scrollY = 0f
        scroller.forceFinished(true)
        rebuild()
    }

    fun goToOffset(offset: Int) {
        val sl = layout ?: return
        if (sl.lineCount == 0) return
        val clamped = offset.coerceIn(0, (content.length - 1).coerceAtLeast(0))
        anchorOffset = clamped
        if (scrollMode) {
            scrollY = lineTopOf(sl, clamped).coerceIn(0f, maxScroll)
            notifyPage()
            invalidate()
        } else {
            setPage(sl.getLineForOffset(clamped) / linesPerPage)
        }
    }

    fun currentOffset(): Int {
        val sl = layout ?: return 0
        if (sl.lineCount == 0) return 0
        val line = if (scrollMode) {
            sl.getLineForVertical(scrollY.toInt().coerceIn(0, max(0, sl.height)))
        } else {
            (page * linesPerPage).coerceAtMost(sl.lineCount - 1)
        }
        return sl.getLineStart(line.coerceIn(0, sl.lineCount - 1))
    }

    fun fractionInChapter(): Float {
        val sl = layout ?: return 0f
        if (sl.lineCount == 0 || content.isEmpty()) return 0f
        return (currentOffset().toFloat() / content.length).coerceIn(0f, 1f)
    }

    fun turnNext() {
        if (scrollMode) {
            if (scrollY < maxScroll - 1f) scrollByScreen(1) else onNextChapter?.invoke()
        } else {
            if (page < totalPages - 1) setPage(page + 1) else onNextChapter?.invoke()
        }
    }

    fun turnPrev() {
        if (scrollMode) {
            if (scrollY > 0f) scrollByScreen(-1) else onPrevChapter?.invoke()
        } else {
            if (page > 0) setPage(page - 1) else onPrevChapter?.invoke()
        }
    }

    private fun scrollByScreen(direction: Int) {
        val target = (scrollY + direction * textAreaHeight * 0.9f).coerceIn(0f, maxScroll).toInt()
        scroller.startScroll(0, scrollY.toInt(), 0, target - scrollY.toInt(), 220)
        invalidate()
    }

    private fun lineTopOf(sl: StaticLayout, offset: Int): Float =
        sl.getLineTop(sl.getLineForOffset(offset.coerceIn(0, max(0, content.length - 1)))).toFloat()

    private fun rebuild() {
        if (width == 0 || height == 0 || content.isEmpty()) return
        // 重排（改字号/行距/切模式）前先记住当前看到的位置
        if (pendingAnchor >= 0) {
            anchorOffset = pendingAnchor
            pendingAnchor = -1
        } else if (layout != null) {
            anchorOffset = currentOffset()
        }
        val availableWidth = width - horizontalPadding * 2
        val availableHeight = textAreaHeight
        if (availableWidth <= 0 || availableHeight <= 0) return

        paint.textSize = textSizePx
        paint.color = textColor
        val built = StaticLayout.Builder
            .obtain(content, 0, content.length, paint, availableWidth.toInt())
            .setLineSpacing(0f, lineSpacingMult)
            .setIncludePad(false)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
        layout = built

        lineHeightPx = if (built.lineCount >= 2) {
            (built.getLineTop(1) - built.getLineTop(0)).toFloat()
        } else {
            max(1f, (built.getLineBottom(0) - built.getLineTop(0)).toFloat())
        }.coerceAtLeast(1f)

        linesPerPage = max(1, (availableHeight / lineHeightPx).toInt())
        totalPages = max(1, ceil(built.lineCount.toFloat() / linesPerPage).toInt())

        footerPaint.textSize = (textSizePx * 0.55f)
        footerPaint.color = (textColor and 0x00FFFFFF) or 0x66000000

        if (scrollMode) {
            scrollY = lineTopOf(built, anchorOffset).coerceIn(0f, maxScroll)
            anchorOffset = currentOffset()
        } else {
            val lineOfOffset = built.getLineForOffset(anchorOffset.coerceIn(0, content.length - 1))
            page = (lineOfOffset / linesPerPage).coerceIn(0, totalPages - 1)
            anchorOffset = currentOffset()
        }
        notifyPage()
        invalidate()
    }

    private fun setPage(newPage: Int) {
        val target = newPage.coerceIn(0, totalPages - 1)
        if (target == page) return
        val from = page
        page = target
        anchorOffset = currentOffset()
        notifyPage()
        if (pageAnim == 0) {
            invalidate()
        } else {
            startPageAnim(from, target)
        }
    }

    private fun startPageAnim(from: Int, to: Int) {
        animator?.cancel()
        animFrom = from
        animTo = to
        animT = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animT = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animFrom == from && animTo == to) {
                        animFrom = -1
                        animTo = -1
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    private fun notifyPage() {
        if (scrollMode) {
            val area = textAreaHeight
            val contentHeight = (layout?.height ?: 0).toFloat()
            val total = max(1, ceil(contentHeight / area).toInt())
            val current = ((scrollY / area).toInt() + 1).coerceIn(1, total)
            onPageChanged?.invoke(current, total)
        } else {
            onPageChanged?.invoke(page + 1, totalPages)
        }
        onProgressChanged?.invoke(currentOffset(), fractionInChapter())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.currY.toFloat()
            invalidate()
        } else if (wasFlinging) {
            wasFlinging = false
            notifyPage()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(pageColor)
        if (layout == null) return
        if (scrollMode) {
            drawScrolled(canvas)
        } else if (animFrom in 0 until totalPages && animTo in 0 until totalPages && animFrom != animTo) {
            drawTransition(canvas)
        } else {
            drawPage(canvas, page, 0f)
        }
        drawFooter(canvas)
    }

    private fun drawScrolled(canvas: Canvas) {
        val sl = layout ?: return
        canvas.save()
        canvas.clipRect(horizontalPadding, topPadding, width - horizontalPadding, height - bottomPadding)
        canvas.translate(horizontalPadding, topPadding - scrollY)
        sl.draw(canvas)
        canvas.restore()
    }

    /** 翻页过程中的两页合成：滑动 = 两页同时位移；覆盖 = 新页不动、旧页滑走。 */
    private fun drawTransition(canvas: Canvas) {
        val w = width.toFloat()
        val forward = animTo > animFrom
        val t = animT
        if (pageAnim == 2) {
            drawPage(canvas, animTo, 0f)
            drawPage(canvas, animFrom, if (forward) -t * w else t * w)
        } else {
            drawPage(canvas, animFrom, if (forward) -t * w else t * w)
            drawPage(canvas, animTo, if (forward) (1 - t) * w else -(1 - t) * w)
        }
    }

    private fun drawPage(canvas: Canvas, pageIndex: Int, dx: Float) {
        val sl = layout ?: return
        canvas.save()
        canvas.translate(dx, 0f)
        val textTop = topPadding
        val textBottom = textTop + linesPerPage * lineHeightPx
        canvas.clipRect(horizontalPadding, textTop, width - horizontalPadding, textBottom)
        canvas.translate(horizontalPadding, textTop - pageIndex * linesPerPage * lineHeightPx)
        sl.draw(canvas)
        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas) {
        val footer = if (scrollMode) {
            "${(fractionInChapter() * 100).toInt()}%"
        } else {
            "${page + 1}/$totalPages"
        }
        val fm = footerPaint.fontMetrics
        val baseline = height - bottomPadding * 0.45f - fm.descent
        canvas.drawText(footer, width - horizontalPadding - footerPaint.measureText(footer), baseline, footerPaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scrollMode) return handleScrollTouch(event)
        return handlePageTouch(event)
    }

    private fun handlePageTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
                    handleTap(event.x, event.y)
                } else if (abs(dx) > abs(dy) && abs(dx) > 80f) {
                    if (dx < 0) turnNext() else turnPrev()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleScrollTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                isDragging = false
                wasFlinging = false
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dy = event.y - lastY
                if (!isDragging && abs(event.y - downY) > touchSlop) isDragging = true
                if (isDragging) {
                    scrollY = (scrollY - dy).coerceIn(0f, maxScroll)
                    lastY = event.y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                val totalDy = event.y - downY
                val totalDx = event.x - downX
                when {
                    !isDragging && abs(totalDx) < touchSlop && abs(totalDy) < touchSlop -> handleTap(event.x, event.y)

                    // 滚到章节头尾再拉，直接进上一章/下一章
                    scrollY >= maxScroll - 1f && totalDy < -60f -> onNextChapter?.invoke()
                    scrollY <= 1f && totalDy > 60f -> onPrevChapter?.invoke()

                    else -> {
                        val tracker = velocityTracker
                        tracker?.computeCurrentVelocity(1000)
                        val vy = -(tracker?.yVelocity ?: 0f)
                        scroller.fling(0, scrollY.toInt(), 0, vy.toInt(), 0, 0, 0, maxScroll.toInt())
                        wasFlinging = true
                        notifyPage()
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        performClick()
        if (tapVertical) {
            when {
                y < height / 3f -> turnPrev()
                y > height * 2f / 3f -> turnNext()
                else -> onMenu?.invoke()
            }
        } else {
            when {
                x < width / 3f -> turnPrev()
                x > width * 2f / 3f -> turnNext()
                else -> onMenu?.invoke()
            }
        }
    }
}
