package com.mike.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class AlphabetBar @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    View(ctx, attrs) {

    private val letters = listOf('#') + ('A'..'Z')
    var onLetter: ((Char) -> Unit)? = null
    var onRelease: (() -> Unit)? = null
    var accent = Color.WHITE
    var touching = false
    private var active = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        val h = height / letters.size.toFloat()
        paint.textSize = minOf(h * 0.62f, 13f * resources.displayMetrics.density)
        letters.forEachIndexed { i, l ->
            val on = i == active
            paint.color = if (on) accent else 0xAAFFFFFF.toInt()
            paint.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            c.drawText(l.toString(), width / 2f, h * i + h / 2 + paint.textSize / 3, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                touching = true
                parent.requestDisallowInterceptTouchEvent(true)
                val i = ((e.y / height) * letters.size).toInt().coerceIn(0, letters.size - 1)
                if (i != active) {
                    active = i
                    invalidate()
                    onLetter?.invoke(letters[i])
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                active = -1
                invalidate()
                onRelease?.invoke()
            }
        }
        return true
    }
}
