package com.btgamepad.hid

/**
 * HID Report Descriptor gamepad standar (Generic Desktop / Gamepad).
 *
 * Isi report (Report ID 1, 9 byte data):
 *  byte 0-1 : 16 tombol (bit 0 = tombol 1 ... bit 15 = tombol 16)
 *  byte 2   : Hat switch / D-Pad (4 bit bawah, 0..7 = arah, 8 = netral) + 4 bit padding
 *  byte 3   : Left stick X   (-127..127)
 *  byte 4   : Left stick Y   (-127..127)
 *  byte 5   : Right stick X  (Z,  -127..127)
 *  byte 6   : Right stick Y  (Rz, -127..127)
 *  byte 7   : Left trigger   (Rx, 0..255)
 *  byte 8   : Right trigger  (Ry, 0..255)
 *
 * Laptop (Windows / Linux / macOS) akan mengenalinya sebagai "HID-compliant game controller"
 * tanpa driver tambahan.
 */
object HidDescriptor {

    const val REPORT_ID = 1
    const val REPORT_SIZE = 9

    val DESCRIPTOR: ByteArray = byteArrayOf(
        0x05, 0x01,                    // Usage Page (Generic Desktop)
        0x09, 0x05,                    // Usage (Game Pad)
        0xA1.toByte(), 0x01,           // Collection (Application)
        0x85.toByte(), REPORT_ID.toByte(), //   Report ID (1)

        // ---- 16 tombol ----
        0x05, 0x09,                    //   Usage Page (Button)
        0x19, 0x01,                    //   Usage Minimum (Button 1)
        0x29, 0x10,                    //   Usage Maximum (Button 16)
        0x15, 0x00,                    //   Logical Minimum (0)
        0x25, 0x01,                    //   Logical Maximum (1)
        0x75, 0x01,                    //   Report Size (1)
        0x95.toByte(), 0x10,           //   Report Count (16)
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)

        // ---- Hat switch (D-Pad) ----
        0x05, 0x01,                    //   Usage Page (Generic Desktop)
        0x09, 0x39,                    //   Usage (Hat switch)
        0x15, 0x00,                    //   Logical Minimum (0)
        0x25, 0x07,                    //   Logical Maximum (7)
        0x35, 0x00,                    //   Physical Minimum (0)
        0x46, 0x3B, 0x01,              //   Physical Maximum (315)
        0x65, 0x14,                    //   Unit (Degrees)
        0x75, 0x04,                    //   Report Size (4)
        0x95.toByte(), 0x01,           //   Report Count (1)
        0x81.toByte(), 0x42,           //   Input (Data, Var, Abs, Null State)
        0x65, 0x00,                    //   Unit (None)
        0x35, 0x00,                    //   Physical Minimum (0)
        0x45, 0x00,                    //   Physical Maximum (0)
        0x75, 0x04,                    //   Report Size (4)  -> padding
        0x95.toByte(), 0x01,           //   Report Count (1)
        0x81.toByte(), 0x03,           //   Input (Const, Var, Abs)

        // ---- 2 analog stick: X, Y, Z, Rz ----
        0x09, 0x30,                    //   Usage (X)
        0x09, 0x31,                    //   Usage (Y)
        0x09, 0x32,                    //   Usage (Z)
        0x09, 0x35,                    //   Usage (Rz)
        0x15, 0x81.toByte(),           //   Logical Minimum (-127)
        0x25, 0x7F,                    //   Logical Maximum (127)
        0x75, 0x08,                    //   Report Size (8)
        0x95.toByte(), 0x04,           //   Report Count (4)
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)

        // ---- 2 trigger analog: Rx, Ry ----
        0x09, 0x33,                    //   Usage (Rx)
        0x09, 0x34,                    //   Usage (Ry)
        0x15, 0x00,                    //   Logical Minimum (0)
        0x26, 0xFF.toByte(), 0x00,     //   Logical Maximum (255)
        0x75, 0x08,                    //   Report Size (8)
        0x95.toByte(), 0x02,           //   Report Count (2)
        0x81.toByte(), 0x02,           //   Input (Data, Var, Abs)

        0xC0.toByte()                  // End Collection
    )
}
