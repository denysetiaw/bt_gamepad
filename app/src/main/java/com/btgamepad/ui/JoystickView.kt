package com.btgamepad.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Analog stick virtual. Menghasilkan nilai x,y dalam rentang -1..1
 * (y positif = ke bawah, sama seperti konvensi HID).
 */
class JoystickView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onMove: ((x: Float, y: Float) -> Unit)? = null
    var deadZone = 0.08f

    /** True selama jari masih menyentuh stick. */
    var isTouching = false
        private set

    private var knobX = 0f   // -1..1
    private var knobY = 0f
    private var pointerId = -1

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 255, 255, 255) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(140, 255, 255, 255)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        ringPaint.strokeWidth = resources.displayMetrics.density * 2f
    }

    private val knobRadius get() = min(width, height) * 0.2f
    private val travel get() = min(width, height) / 2f - knobRadius

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerId == -1) {
                    val i = e.actionIndex
                    pointerId = e.getPointerId(i)
                    isTouching = true
                    move(e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) move(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == pointerId) reset()
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun move(px: Float, py: Float) {
        var dx = (px - width / 2f) / travel
        var dy = (py - height / 2f) / travel
        val len = hypot(dx, dy)
        if (len > 1f) { dx /= len; dy /= len }
        knobX = dx; knobY = dy
        val out = hypot(dx, dy)
        if (out < deadZone) emit(0f, 0f)
        else {
            // skala ulang agar setelah dead zone nilainya mulus dari 0
            val scale = (out - deadZone) / (1f - deadZone) / out
            emit(dx * scale, dy * scale)
        }
        invalidate()
    }

    private fun reset() {
        pointerId = -1
        isTouching = false
        knobX = 0f; knobY = 0f
        emit(0f, 0f)
        invalidate()
    }

    private fun emit(x: Float, y: Float) = onMove?.invoke(x, y)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - ringPaint.strokeWidth
        canvas.drawCircle(cx, cy, r, basePaint)
        canvas.drawCircle(cx, cy, r, ringPaint)
        knobPaint.color = if (isTouching) Color.argb(230, 0, 200, 255) else Color.argb(180, 220, 220, 220)
        canvas.drawCircle(cx + knobX * travel, cy + knobY * travel, knobRadius, knobPaint)
    }
}
