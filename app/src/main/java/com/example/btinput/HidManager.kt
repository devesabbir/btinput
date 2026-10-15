package com.example.btinput

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Registers the phone as a Bluetooth HID device (keyboard + mouse combo)
 * and sends input reports to the connected host.
 *
 * Caller must hold BLUETOOTH_CONNECT before calling start().
 */
@SuppressLint("MissingPermission")
class HidManager(
    private val context: Context,
    private val onState: (String, Boolean) -> Unit
) {
    companion object {
        const val ID_KEYBOARD = 1
        const val ID_MOUSE = 2

        // Combined keyboard (report 1) + 3-button relative mouse with wheel (report 2).
        private val DESCRIPTOR = byteArrayOf(
            // ---- Keyboard ----
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01,
            0x85.toByte(), ID_KEYBOARD.toByte(),
            0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(),
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08,
            0x81.toByte(), 0x02,
            0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
            0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
            0xC0.toByte(),
            // ---- Mouse ----
            0x05, 0x01, 0x09, 0x02, 0xA1.toByte(), 0x01,
            0x85.toByte(), ID_MOUSE.toByte(),
            0x09, 0x01, 0xA1.toByte(), 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03,
            0x15, 0x00, 0x25, 0x01, 0x95.toByte(), 0x03, 0x75, 0x01,
            0x81.toByte(), 0x02,
            0x95.toByte(), 0x01, 0x75, 0x05, 0x81.toByte(), 0x01,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x03,
            0x81.toByte(), 0x06,
            0xC0.toByte(), 0xC0.toByte()
        )
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var registered = false

    val isConnected: Boolean get() = host != null

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidManager.registered = registered
            main.post {
                if (registered) onState("Ready. Pair this phone from your desktop's Bluetooth settings.", false)
                else onState("HID not registered", false)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            main.post {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        host = device
                        onState("Connected to ${device.name ?: device.address}", true)
                    }
                    BluetoothProfile.STATE_CONNECTING ->
                        onState("Connecting to ${device.name ?: device.address}…", false)
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (host?.address == device.address) host = null
                        onState("Disconnected", false)
                    }
                }
            }
        }
    }

    private val proxyListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val h = proxy as BluetoothHidDevice
            hid = h
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "BT Mouse & Keyboard",
                "Phone as keyboard and touchpad",
                "Android",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                DESCRIPTOR
            )
            h.registerApp(sdp, null, null, executor, callback)
        }

        override fun onServiceDisconnected(profile: Int) {
            hid = null
            registered = false
            host = null
            main.post { onState("Bluetooth service lost", false) }
        }
    }

    fun start() {
        val a = adapter
        if (a == null || !a.isEnabled) {
            onState("Turn Bluetooth on first", false)
            return
        }
        a.getProfileProxy(context, proxyListener, BluetoothProfile.HID_DEVICE)
    }

    fun stop() {
        try {
            hid?.unregisterApp()
        } catch (_: Exception) {
        }
        hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hid = null
        host = null
        registered = false
    }

    /** Previously bonded devices, so the user can reconnect without re-pairing. */
    fun bondedDevices(): List<BluetoothDevice> = adapter?.bondedDevices?.toList().orEmpty()

    fun connect(device: BluetoothDevice) {
        hid?.connect(device)
    }

    fun disconnect() {
        host?.let { hid?.disconnect(it) }
    }

    // ---- Reports ----

    fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int = 0) {
        val d = host ?: return
        val report = byteArrayOf(
            (buttons and 0x07).toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte()
        )
        hid?.sendReport(d, ID_MOUSE, report)
    }

    /** Sends a key press followed by a release. */
    fun tapKey(modifiers: Int, keyCode: Int) {
        val d = host ?: return
        val down = byteArrayOf(modifiers.toByte(), 0, keyCode.toByte(), 0, 0, 0, 0, 0)
        val up = ByteArray(8)
        hid?.sendReport(d, ID_KEYBOARD, down)
        hid?.sendReport(d, ID_KEYBOARD, up)
    }

    fun sendModifierOnly(modifiers: Int) {
        val d = host ?: return
        hid?.sendReport(d, ID_KEYBOARD, byteArrayOf(modifiers.toByte(), 0, 0, 0, 0, 0, 0, 0))
        hid?.sendReport(d, ID_KEYBOARD, ByteArray(8))
    }
}
