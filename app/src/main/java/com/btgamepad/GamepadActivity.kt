package com.btgamepad

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.btgamepad.hid.Btn
import com.btgamepad.hid.HidGamepad
import com.btgamepad.ui.DPadView
import com.btgamepad.ui.Haptics
import com.btgamepad.ui.JoystickView
import com.btgamepad.ui.PadButton
import kotlin.math.atan2

@SuppressLint("MissingPermission") // semua aksi Bluetooth baru jalan setelah hasPermissions() true
class GamepadActivity : Activity(), SensorEventListener {

    companion object {
        private const val REQ_PERMS = 1
        private const val REQ_ENABLE_BT = 2
        private const val REQ_DISCOVERABLE = 3
        /** Kemiringan (derajat) untuk belok penuh saat mode tilt. */
        private const val TILT_FULL_DEG = 35.0
    }

    private lateinit var txtStatus: TextView
    private lateinit var chipTilt: TextView
    private lateinit var chipHaptic: TextView
    private lateinit var stickLeft: JoystickView

    private lateinit var sensorManager: SensorManager
    private var accel: Sensor? = null
    private var tiltEnabled = false
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gamepad)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Haptics.init(this)

        txtStatus = findViewById(R.id.txtStatus)
        chipTilt = findViewById(R.id.chipTilt)
        chipHaptic = findViewById(R.id.chipHaptic)
        stickLeft = findViewById(R.id.stickLeft)

        sensorManager = getSystemService(SensorManager::class.java)!!
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        tiltEnabled = prefs.getBoolean("tilt", false)
        Haptics.enabled = prefs.getBoolean("haptic", true)
        refreshChips()

        wireControls()
        wireToolbar()

        HidGamepad.listener = HidGamepad.Listener { status, device, message ->
            val name = device?.let { it.name ?: it.address }
            val dot = when (status) {
                HidGamepad.Status.CONNECTED -> "🟢"
                HidGamepad.Status.CONNECTING, HidGamepad.Status.REGISTERING -> "🟡"
                HidGamepad.Status.READY -> "🔵"
                else -> "🔴"
            }
            txtStatus.text = if (status == HidGamepad.Status.CONNECTED && name != null)
                "$dot Terhubung ke $name" else "$dot $message"
        }

        ensureReady()
    }

    // ------------------------------------------------------------------ kontrol

    private fun wireControls() {
        // Semua tombol diatur lewat atribut app:buttonIndex di layout.
        forEachPadButton(findViewById(R.id.root)) { btn ->
            val index = btn.buttonIndex
            if (index < 0) return@forEachPadButton
            btn.onPressedChange = { pressed ->
                HidGamepad.update {
                    setButton(index, pressed)
                    // LT/RT juga dikirim sebagai sumbu analog (0 atau penuh).
                    if (index == Btn.LT) lt = if (pressed) 1f else 0f
                    if (index == Btn.RT) rt = if (pressed) 1f else 0f
                }
            }
        }

        stickLeft.onMove = { x, y -> HidGamepad.update { lx = x; ly = y } }
        findViewById<JoystickView>(R.id.stickRight).onMove = { x, y -> HidGamepad.update { rx = x; ry = y } }
        findViewById<DPadView>(R.id.dpad).onHatChange = { h -> HidGamepad.update { hat = h } }
    }

    private fun forEachPadButton(view: View, action: (PadButton) -> Unit) {
        if (view is PadButton) action(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) forEachPadButton(view.getChildAt(i), action)
    }

    private fun wireToolbar() {
        findViewById<View>(R.id.chipConnect).setOnClickListener { showConnectDialog() }
        findViewById<View>(R.id.chipVisible).setOnClickListener { makeDiscoverable() }
        chipTilt.setOnClickListener {
            if (accel == null) {
                toast("HP ini tidak punya sensor akselerometer")
                return@setOnClickListener
            }
            tiltEnabled = !tiltEnabled
            prefs.edit().putBoolean("tilt", tiltEnabled).apply()
            refreshChips()
            updateSensor()
            if (!tiltEnabled) HidGamepad.update { lx = 0f }
            else toast("Mode tilt: miringkan HP seperti setir untuk stick kiri (kiri/kanan)")
        }
        chipHaptic.setOnClickListener {
            Haptics.enabled = !Haptics.enabled
            prefs.edit().putBoolean("haptic", Haptics.enabled).apply()
            refreshChips()
        }
    }

    private fun refreshChips() {
        chipTilt.text = if (tiltEnabled) "Tilt: ON" else "Tilt: OFF"
        chipHaptic.text = if (Haptics.enabled) "Getar: ON" else "Getar: OFF"
    }

    // ------------------------------------------------------------------ Bluetooth

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        else emptyArray()

    private fun hasPermissions() = requiredPermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    /** Urutan: izin → Bluetooth nyala → daftarkan gamepad HID. */
    private fun ensureReady() {
        if (!hasPermissions()) {
            requestPermissions(requiredPermissions(), REQ_PERMS)
            return
        }
        val adapter = getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            txtStatus.text = "🔴 Perangkat ini tidak punya Bluetooth"
            return
        }
        if (!adapter.isEnabled) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT)
            return
        }
        HidGamepad.init(applicationContext)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        if (hasPermissions()) ensureReady()
        else txtStatus.text = "🔴 Izin Bluetooth ditolak. Buka Pengaturan > Aplikasi untuk mengizinkan."
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_ENABLE_BT ->
                if (resultCode == RESULT_OK) ensureReady()
                else txtStatus.text = "🔴 Bluetooth harus dinyalakan"
            REQ_DISCOVERABLE ->
                if (resultCode != RESULT_CANCELED)
                    toast("HP terlihat ${resultCode} detik. Di laptop: Bluetooth > Add device > pilih HP ini")
        }
    }

    private fun makeDiscoverable() {
        if (!hasPermissions()) { ensureReady(); return }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_DISCOVERABLE)
    }

    private fun showConnectDialog() {
        if (!hasPermissions() || !HidGamepad.isBluetoothOn()) { ensureReady(); return }

        val connected = HidGamepad.host
        if (connected != null && HidGamepad.status == HidGamepad.Status.CONNECTED) {
            AlertDialog.Builder(this)
                .setTitle("Terhubung ke ${connected.name ?: connected.address}")
                .setPositiveButton("Putuskan") { _, _ -> HidGamepad.disconnect() }
                .setNeutralButton("Ganti perangkat") { _, _ -> showDeviceList() }
                .setNegativeButton("Tutup", null)
                .show()
        } else showDeviceList()
    }

    private fun showDeviceList() {
        val devices: List<BluetoothDevice> = HidGamepad.bondedDevices()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Belum ada laptop yang di-pair")
                .setMessage(
                    "1. Tekan \"Mode Pairing\" di aplikasi ini.\n" +
                        "2. Di laptop buka Bluetooth > Add device, pilih nama HP ini.\n" +
                        "3. Setelah pairing, laptop akan langsung mengenali HP sebagai gamepad."
                )
                .setPositiveButton("Mode Pairing") { _, _ -> makeDiscoverable() }
                .setNegativeButton("Tutup", null)
                .show()
            return
        }
        val names = devices.map { "${it.name ?: "(tanpa nama)"}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Pilih laptop")
            .setItems(names) { _, which -> HidGamepad.connect(devices[which]) }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ------------------------------------------------------------------ tilt (setir)

    private fun updateSensor() {
        sensorManager.unregisterListener(this)
        if (tiltEnabled) accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!tiltEnabled || stickLeft.isTouching) return // stick sentuh tetap diprioritaskan
        val ax = event.values[0].toDouble()
        val ay = event.values[1].toDouble()
        // Sudut kemiringan setir relatif terhadap orientasi landscape saat ini.
        val angle = when (displayRotation()) {
            Surface.ROTATION_270 -> Math.toDegrees(atan2(-ay, -ax))
            else -> Math.toDegrees(atan2(ay, ax))
        }
        val steer = (angle / TILT_FULL_DEG).coerceIn(-1.0, 1.0).toFloat()
        val value = if (kotlin.math.abs(steer) < 0.05f) 0f else steer
        HidGamepad.update { lx = value }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.rotation ?: Surface.ROTATION_90
        else windowManager.defaultDisplay.rotation

    // ------------------------------------------------------------------ siklus hidup

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        updateSensor()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        // Jangan sampai ada tombol "nyangkut" saat aplikasi ditinggal.
        HidGamepad.releaseAll()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Konfirmasi dulu supaya tidak keluar tanpa sengaja saat main game.
        AlertDialog.Builder(this)
            .setTitle("Keluar dari BT Gamepad?")
            .setMessage("Koneksi gamepad ke laptop akan diputus.")
            .setPositiveButton("Keluar") { _, _ -> finish() }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        HidGamepad.listener = null
        if (isFinishing) HidGamepad.shutdown()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
