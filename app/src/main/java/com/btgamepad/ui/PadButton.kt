package com.btgamepad.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.btgamepad.R

/**
 * Tombol gamepad (A/B/X/Y, LB/RB, LT/RT, Start, Select, dll).
 * Mendukung multi-touch: setiap tombol menerima sentuhan jarinya sendiri.
 */
class PadButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Dipanggil saat tombol ditekan (true) / dilepas (false). */
    var onPressedChange: ((Boolean) -> Unit)? = null

    var label: String = ""
    var buttonIndex: Int = -1
    private val round: Boolean
    private val accent: Int

    private var down = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PadButton)
        label = a.getString(R.styleable.PadButton_label) ?: ""
        buttonIndex = a.getInt(R.styleable.PadButton_buttonIndex, -1)
        round = a.getBoolean(R.styleable.PadButton_round, true)
        accent = a.getColor(R.styleable.PadButton_accentColor, Color.WHITE)
        a.recycle()
        stroke.strokeWidth = resources.displayMetrics.density * 2f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> setDown(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setDown(false)
        }
        return true
    }

    private fun setDown(value: Boolean) {
        if (down == value) return
        down = value
        if (value) Haptics.tick()
        onPressedChange?.invoke(value)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val pad = stroke.strokeWidth
        rect.set(pad, pad, width - pad, height - pad)
        fill.color = if (down) accent else Color.argb(60, Color.red(accent), Color.green(accent), Color.blue(accent))
        stroke.color = accent
        val radius = if (round) minOf(rect.width(), rect.height()) / 2f else rect.height() * 0.3f
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, stroke)

        text.color = if (down) Color.BLACK else accent
        text.textSize = minOf(width, height) * if (label.length <= 2) 0.42f else 0.28f
        val y = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, width / 2f, y, text)
    }
}
