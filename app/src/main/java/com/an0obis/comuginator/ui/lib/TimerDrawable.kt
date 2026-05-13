package com.an0obis.comuginator.ui.lib

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.graphics.toColorInt
import kotlin.math.min

class TimerDrawable : Drawable() {

    var progress: Float = 1f
        set(value) {
            field = value
            invalidateSelf()
        }

    var text: String = ""
        set(value) {
            field = value
            invalidateSelf()
        }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#DADADA".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#1976D2".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#1976D2".toColorInt()
        textAlign = Paint.Align.CENTER
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun draw(canvas: Canvas) {
        val b = bounds

        val size = min(b.width(), b.height()).toFloat()
        val padding = 22f

        val rect = RectF(
            padding,
            padding,
            size - padding,
            size - padding
        )

        canvas.drawArc(rect, 0f, 360f, false, backgroundPaint)

        canvas.drawArc(
            rect,
            -90f,
            progress * 360f,
            false,
            progressPaint
        )

        val x = b.exactCenterX()
        val y = b.exactCenterY() - ((textPaint.descent() + textPaint.ascent()) / 2)

        canvas.drawText(text, x, y, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        progressPaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        progressPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Deprecated("?")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}