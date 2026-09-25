package com.btgamepad.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.btgamepad.hid.Hat
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * D-Pad 8 arah. Menggeser jari di atas D-Pad langsung mengubah arah
 * (termasuk diagonal), sama seperti D-Pad fisik.
 */
class DPadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onHatChange: ((Int) -> Unit)? = null

    private var hat = Hat.CENTER
    private var pointerId = -1

    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val path = Path()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerId == -1) {
                    val i = e.actionIndex
                    pointerId = e.getPointerId(i)
                    update(e.getX(i), e.getY(i))
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) update(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == pointerId) { pointerId = -1; setHat(Hat.CENTER) }
            }
            MotionEvent.ACTION_CANCEL -> { pointerId = -1; setHat(Hat.CENTER) }
        }
        return true
    }

    private fun update(x: Float, y: Float) {
        val dx = x - width / 2f
        val dy = y - height / 2f
        if (hypot(dx, dy) < min(width, height) * 0.12f) { setHat(Hat.CENTER); return }
        // sudut kompas: 0° = atas, searah jarum jam
        val deg = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
        setHat(((deg + 22.5) / 45.0).toInt() % 8)
    }

    private fun setHat(value: Int) {
        if (value == hat) return
        if (value != Hat.CENTER) Haptics.tick()
        hat = value
        onHatChange?.invoke(value)
        invalidate()
    }

    private fun active(dir: Int): Boolean = when (dir) {
        Hat.UP -> hat == Hat.UP || hat == Hat.UP_LEFT || hat == Hat.UP_RIGHT
        Hat.DOWN -> hat == Hat.DOWN || hat == Hat.DOWN_LEFT || hat == Hat.DOWN_RIGHT
        Hat.LEFT -> hat == Hat.LEFT || hat == Hat.UP_LEFT || hat == Hat.DOWN_LEFT
        Hat.RIGHT -> hat == Hat.RIGHT || hat == Hat.UP_RIGHT || hat == Hat.DOWN_RIGHT
        else -> false
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        val t = s / 3f  // lebar lengan D-Pad
        val r = t * 0.2f

        fun arm(l: Float, tp: Float, dir: Int) {
            armPaint.color = if (active(dir)) Color.argb(230, 0, 200, 255) else Color.argb(90, 255, 255, 255)
            canvas.drawRoundRect(ox + l, oy + tp, ox + l + t, oy + tp + t, r, r, armPaint)
        }
        arm(t, 0f, Hat.UP)
        arm(t, 2 * t, Hat.DOWN)
        arm(0f, t, Hat.LEFT)
        arm(2 * t, t, Hat.RIGHT)
        armPaint.color = Color.argb(90, 255, 255, 255)
        canvas.drawRect(ox + t, oy + t, ox + 2 * t, oy + 2 * t, armPaint)

        // panah kecil di tiap lengan
        val c = t / 2f
        val a = t * 0.18f
        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            path.reset(); path.moveTo(ox + x1, oy + y1); path.lineTo(ox + x2, oy + y2); path.lineTo(ox + x3, oy + y3); path.close()
            canvas.drawPath(path, arrowPaint)
        }
        tri(t + c, c - a, t + c - a, c + a, t + c + a, c + a)                         // atas
        tri(t + c, 2 * t + c + a, t + c - a, 2 * t + c - a, t + c + a, 2 * t + c - a) // bawah
        tri(c - a, t + c, c + a, t + c - a, c + a, t + c + a)                         // kiri
        tri(2 * t + c + a, t + c, 2 * t + c - a, t + c - a, 2 * t + c - a, t + c + a) // kanan
    }
}
