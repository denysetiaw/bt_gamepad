package com.btgamepad.hid

import kotlin.math.roundToInt

/** Nomor tombol HID (0-based bit). Urutan mengikuti layout umum gamepad DirectInput/SDL. */
object Btn {
    const val A = 0
    const val B = 1
    const val X = 2
    const val Y = 3
    const val LB = 4
    const val RB = 5
    const val SELECT = 6
    const val START = 7
    const val L3 = 8
    const val R3 = 9
    const val HOME = 10
    const val LT = 11   // versi digital dari trigger (untuk game yang membaca trigger sebagai tombol)
    const val RT = 12
}

object Hat {
    const val UP = 0
    const val UP_RIGHT = 1
    const val RIGHT = 2
    const val DOWN_RIGHT = 3
    const val DOWN = 4
    const val DOWN_LEFT = 5
    const val LEFT = 6
    const val UP_LEFT = 7
    const val CENTER = 8
}

/** Menyimpan kondisi gamepad saat ini dan mengubahnya menjadi HID report. */
class GamepadState {
    var buttons: Int = 0
    var hat: Int = Hat.CENTER
    var lx = 0f; var ly = 0f      // -1..1
    var rx = 0f; var ry = 0f      // -1..1
    var lt = 0f; var rt = 0f      //  0..1

    fun setButton(index: Int, pressed: Boolean) {
        buttons = if (pressed) buttons or (1 shl index) else buttons and (1 shl index).inv()
    }

    fun toReport(): ByteArray {
        val r = ByteArray(HidDescriptor.REPORT_SIZE)
        r[0] = (buttons and 0xFF).toByte()
        r[1] = ((buttons shr 8) and 0xFF).toByte()
        r[2] = (hat and 0x0F).toByte()
        r[3] = axis(lx)
        r[4] = axis(ly)
        r[5] = axis(rx)
        r[6] = axis(ry)
        r[7] = trigger(lt)
        r[8] = trigger(rt)
        return r
    }

    private fun axis(v: Float): Byte = (v.coerceIn(-1f, 1f) * 127f).roundToInt().toByte()
    private fun trigger(v: Float): Byte = (v.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
}
