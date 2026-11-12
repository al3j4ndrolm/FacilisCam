package io.github.al3j4ndrolm.faciliscam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Dims the preview around a square window with white corners, and outlines a code once it's read. */
internal class ViewfinderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val window = RectF()
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) }
    private val corners = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val found = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(76, 217, 100)
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
    }
    private val path = Path()
    private var code: RectF? = null

    /** Outlines the code at [box] (in this view's coordinates) to show it was read. */
    fun showCode(box: Rect?) {
        code = box?.let { RectF(it).apply { inset(-6 * density, -6 * density) } } ?: RectF(window)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val size = minOf(w, h) * 0.7f
        val left = (w - size) / 2
        val top = (h - size) / 2.4f
        window.set(left, top, left + size, top + size)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = 20 * density
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRoundRect(window, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, dim)

        val arm = window.width() * 0.12f
        with(window) {
            // Four corner brackets.
            canvas.drawLine(left, top + arm, left, top + radius, corners)
            canvas.drawArc(left, top, left + 2 * radius, top + 2 * radius, 180f, 90f, false, corners)
            canvas.drawLine(left + radius, top, left + arm, top, corners)

            canvas.drawLine(right - arm, top, right - radius, top, corners)
            canvas.drawArc(right - 2 * radius, top, right, top + 2 * radius, 270f, 90f, false, corners)
            canvas.drawLine(right, top + radius, right, top + arm, corners)

            canvas.drawLine(right, bottom - arm, right, bottom - radius, corners)
            canvas.drawArc(right - 2 * radius, bottom - 2 * radius, right, bottom, 0f, 90f, false, corners)
            canvas.drawLine(right - radius, bottom, right - arm, bottom, corners)

            canvas.drawLine(left + arm, bottom, left + radius, bottom, corners)
            canvas.drawArc(left, bottom - 2 * radius, left + 2 * radius, bottom, 90f, 90f, false, corners)
            canvas.drawLine(left, bottom - radius, left, bottom - arm, corners)
        }

        code?.let { canvas.drawRoundRect(it, 12 * density, 12 * density, found) }
    }
}
