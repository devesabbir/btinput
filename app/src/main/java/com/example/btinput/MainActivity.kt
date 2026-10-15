package com.example.btinput

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.app.AlertDialog
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Invisible view that receives soft-keyboard input and forwards it as HID key presses. */
private class KeyCaptureView(context: Context, private val hid: HidManager) : View(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                for (c in text) Keymap.forChar(c)?.let { (mod, key) -> hid.tapKey(mod, key) }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(maxOf(beforeLength, 1)) { hid.tapKey(0, Keymap.KEY_BACKSPACE) }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) handleKey(event)
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (handleKey(event)) true else super.onKeyDown(keyCode, event)
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val special = Keymap.forAndroidKey(event.keyCode)
        if (special != null) {
            hid.tapKey(0, special)
            return true
        }
        val ch = event.unicodeChar
        if (ch != 0) {
            Keymap.forChar(ch.toChar())?.let { (mod, key) ->
                hid.tapKey(mod, key)
                return true
            }
        }
        return false
    }
}

class MainActivity : Activity() {

    private lateinit var hid: HidManager
    private lateinit var status: TextView
    private lateinit var keyCapture: KeyCaptureView
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hid = HidManager(this) { text, connected -> status.text = text; status.setTextColor(if (connected) Color.rgb(90, 200, 120) else Color.LTGRAY) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20, 21, 25))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        status = TextView(this).apply {
            text = "Starting…"
            setTextColor(Color.LTGRAY)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(10))
        }
        root.addView(status)

        val touchpad = TouchpadView(this, hid)
        root.addView(touchpad, LinearLayout.LayoutParams(-1, 0, 1f))

        // Special-key row
        val keys = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun keyButton(label: String, mod: Int, key: Int) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { hid.tapKey(mod, key) }
        }
        listOf(
            keyButton("Esc", 0, Keymap.KEY_ESC),
            keyButton("Tab", 0, Keymap.KEY_TAB),
            keyButton("←", 0, Keymap.KEY_LEFT),
            keyButton("↑", 0, Keymap.KEY_UP),
            keyButton("↓", 0, Keymap.KEY_DOWN),
            keyButton("→", 0, Keymap.KEY_RIGHT)
        ).forEach { keys.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(keys, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // Action row
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val kbBtn = Button(this).apply {
            text = "Keyboard"
            isAllCaps = false
            setOnClickListener { showKeyboard() }
        }
        val devBtn = Button(this).apply {
            text = "Devices"
            isAllCaps = false
            setOnClickListener { showDevices() }
        }
        val discBtn = Button(this).apply {
            text = "Disconnect"
            isAllCaps = false
            setOnClickListener { hid.disconnect() }
        }
        actions.addView(kbBtn, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(devBtn, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(discBtn, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)

        keyCapture = KeyCaptureView(this, hid)
        root.addView(keyCapture, LinearLayout.LayoutParams(1, 1))

        setContentView(root)
        ensurePermissionsThenStart()
    }

    private fun showKeyboard() {
        keyCapture.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.restartInput(keyCapture)
        imm.showSoftInput(keyCapture, InputMethodManager.SHOW_FORCED)
    }

    @SuppressLint("MissingPermission")
    private fun showDevices() {
        val devices: List<BluetoothDevice> = hid.bondedDevices()
        val names = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Connect to a paired computer")
            .setItems(names) { _, i -> hid.connect(devices[i]) }
            .setNeutralButton("Bluetooth settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ---- Permissions ----

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else emptyArray()

    private fun ensurePermissionsThenStart() {
        val missing = neededPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startHid() else requestPermissions(missing.toTypedArray(), 1)
    }

    private fun startHid() {
        if (started) return
        started = true
        hid.start()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            startHid()
        } else {
            status.text = "Bluetooth permission is required"
        }
    }

    override fun onDestroy() {
        hid.stop()
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
