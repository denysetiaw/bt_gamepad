package com.btgamepad.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors

/**
 * Menjadikan HP sebagai perangkat Bluetooth HID (gamepad) memakai API BluetoothHidDevice.
 * Laptop melihat HP ini persis seperti gamepad Bluetooth fisik.
 */
@SuppressLint("MissingPermission") // izin dicek di GamepadActivity sebelum init()
object HidGamepad {

    private const val TAG = "HidGamepad"
    /** Jeda minimal antar report (~125 Hz). */
    private const val MIN_INTERVAL_MS = 8L

    enum class Status { OFF, REGISTERING, READY, CONNECTING, CONNECTED, ERROR }

    fun interface Listener {
        fun onStatus(status: Status, device: BluetoothDevice?, message: String)
    }

    var listener: Listener? = null
        set(value) {
            field = value
            notifyStatus()
        }

    var status = Status.OFF
        private set
    var host: BluetoothDevice? = null
        private set
    private var message = ""

    private var adapter: BluetoothAdapter? = null
    private var hid: BluetoothHidDevice? = null
    private var registered = false
    private var initialized = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    // --- pengiriman report di thread sendiri, dengan throttle ---
    private val state = GamepadState()
    private val sendThread = HandlerThread("hid-send").apply { start() }
    private val sendHandler = Handler(sendThread.looper)
    private var sendPending = false
    @Volatile private var lastSendAt = 0L
    private var lastReport: ByteArray? = null

    private val sendRunnable = Runnable {
        val report: ByteArray
        synchronized(state) {
            sendPending = false
            report = state.toReport()
        }
        lastSendAt = SystemClock.uptimeMillis()
        lastReport = report
        val h = hid
        val d = host
        if (h != null && d != null && status == Status.CONNECTED) {
            try {
                h.sendReport(d, HidDescriptor.REPORT_ID, report)
            } catch (e: Exception) {
                Log.w(TAG, "sendReport gagal", e)
            }
        }
    }

    /** Ubah kondisi gamepad lalu kirim ke laptop. Aman dipanggil dari thread mana pun. */
    fun update(block: GamepadState.() -> Unit) {
        synchronized(state) {
            state.block()
            if (sendPending) return
            sendPending = true
        }
        val wait = (lastSendAt + MIN_INTERVAL_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        sendHandler.postDelayed(sendRunnable, wait)
    }

    /** Lepas semua tombol & stick (dipakai saat aplikasi ke background). */
    fun releaseAll() = update {
        buttons = 0; hat = Hat.CENTER
        lx = 0f; ly = 0f; rx = 0f; ry = 0f; lt = 0f; rt = 0f
    }

    // ------------------------------------------------------------------

    fun init(context: Context) {
        if (initialized) {
            if (!registered) registerApp()
            return
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
        adapter = manager?.adapter
        val a = adapter
        if (a == null) {
            setStatus(Status.ERROR, "Perangkat ini tidak punya Bluetooth")
            return
        }
        if (!a.isEnabled) {
            setStatus(Status.OFF, "Bluetooth mati")
            return
        }
        setStatus(Status.REGISTERING, "Menyiapkan profil HID…")
        val ok = a.getProfileProxy(context.applicationContext, profileListener, BluetoothProfile.HID_DEVICE)
        if (!ok) {
            setStatus(Status.ERROR, "HP ini tidak mendukung Bluetooth HID Device")
        } else {
            initialized = true
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = proxy as BluetoothHidDevice
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hid = null
            registered = false
            initialized = false
            host = null
            setStatus(Status.OFF, "Layanan HID terputus")
        }
    }

    private fun registerApp() {
        val h = hid ?: return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "BT Gamepad",
            "Android Bluetooth Gamepad",
            "BtGamepad",
            BluetoothHidDevice.SUBCLASS2_GAMEPAD,
            HidDescriptor.DESCRIPTOR
        )
        val ok = h.registerApp(sdp, null, null, callbackExecutor, hidCallback)
        if (!ok) setStatus(Status.ERROR, "Gagal mendaftarkan gamepad HID")
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidGamepad.registered = registered
            if (registered) {
                setStatus(Status.READY, "Siap. Pair/hubungkan dari laptop")
                // Sambung ulang otomatis ke laptop terakhir bila sistem memberi tahu.
                if (pluggedDevice != null) connect(pluggedDevice)
            } else {
                host = null
                setStatus(Status.OFF, "Gamepad tidak aktif")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    setStatus(Status.CONNECTED, "Terhubung")
                    // Kirim kondisi awal (semua netral).
                    update { }
                }
                BluetoothProfile.STATE_CONNECTING -> setStatus(Status.CONNECTING, "Menghubungkan…", device)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == null || host == device) {
                        host = null
                        setStatus(if (registered) Status.READY else Status.OFF, "Terputus")
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val h = hid ?: return
            if (type == BluetoothHidDevice.REPORT_TYPE_INPUT) {
                val report = synchronized(state) { state.toReport() }
                h.replyReport(device, type, HidDescriptor.REPORT_ID.toByte(), report)
            } else {
                h.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    fun connect(device: BluetoothDevice) {
        val h = hid
        if (h == null || !registered) {
            setStatus(status, "Gamepad belum siap, coba lagi sebentar")
            return
        }
        host?.let { if (it != device) h.disconnect(it) }
        setStatus(Status.CONNECTING, "Menghubungkan…", device)
        if (!h.connect(device)) setStatus(Status.READY, "Gagal menghubungkan. Pastikan laptop sudah di-pair")
    }

    fun disconnect() {
        val h = hid ?: return
        host?.let { h.disconnect(it) }
    }

    fun bondedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices?.toList()?.sortedBy { it.name ?: it.address } ?: emptyList()

    fun isBluetoothOn(): Boolean = adapter?.isEnabled == true

    /** Matikan gamepad dan lepas profil (dipanggil saat aplikasi ditutup). */
    fun shutdown() {
        releaseAll()
        val h = hid
        if (h != null) {
            host?.let { h.disconnect(it) }
            if (registered) h.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, h)
        }
        hid = null
        host = null
        registered = false
        initialized = false
        setStatus(Status.OFF, "Gamepad dimatikan")
    }

    private fun setStatus(s: Status, msg: String, device: BluetoothDevice? = host) {
        status = s
        message = msg
        mainHandler.post { listener?.onStatus(status, device, message) }
    }

    private fun notifyStatus() {
        mainHandler.post { listener?.onStatus(status, host, message) }
    }
}
