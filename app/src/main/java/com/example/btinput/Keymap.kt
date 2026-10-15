package com.example.btinput

import android.view.KeyEvent

/** Maps Android key events / characters to USB HID keyboard usage codes. */
object Keymap {
    const val MOD_CTRL = 0x01
    const val MOD_SHIFT = 0x02
    const val MOD_ALT = 0x04
    const val MOD_GUI = 0x08

    const val KEY_ENTER = 0x28
    const val KEY_ESC = 0x29
    const val KEY_BACKSPACE = 0x2A
    const val KEY_TAB = 0x2B
    const val KEY_SPACE = 0x2C
    const val KEY_DELETE = 0x4C
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52

    /** Returns (modifiers, usage) for a typed character, or null if unsupported. */
    fun forChar(c: Char): Pair<Int, Int>? {
        return when (c) {
            in 'a'..'z' -> 0 to (0x04 + (c - 'a'))
            in 'A'..'Z' -> MOD_SHIFT to (0x04 + (c - 'A'))
            in '1'..'9' -> 0 to (0x1E + (c - '1'))
            '0' -> 0 to 0x27
            ' ' -> 0 to KEY_SPACE
            '\n' -> 0 to KEY_ENTER
            '\t' -> 0 to KEY_TAB
            '-' -> 0 to 0x2D
            '_' -> MOD_SHIFT to 0x2D
            '=' -> 0 to 0x2E
            '+' -> MOD_SHIFT to 0x2E
            '[' -> 0 to 0x2F
            '{' -> MOD_SHIFT to 0x2F
            ']' -> 0 to 0x30
            '}' -> MOD_SHIFT to 0x30
            '\\' -> 0 to 0x31
            '|' -> MOD_SHIFT to 0x31
            ';' -> 0 to 0x33
            ':' -> MOD_SHIFT to 0x33
            '\'' -> 0 to 0x34
            '"' -> MOD_SHIFT to 0x34
            '`' -> 0 to 0x35
            '~' -> MOD_SHIFT to 0x35
            ',' -> 0 to 0x36
            '<' -> MOD_SHIFT to 0x36
            '.' -> 0 to 0x37
            '>' -> MOD_SHIFT to 0x37
            '/' -> 0 to 0x38
            '?' -> MOD_SHIFT to 0x38
            '!' -> MOD_SHIFT to 0x1E
            '@' -> MOD_SHIFT to 0x1F
            '#' -> MOD_SHIFT to 0x20
            '$' -> MOD_SHIFT to 0x21
            '%' -> MOD_SHIFT to 0x22
            '^' -> MOD_SHIFT to 0x23
            '&' -> MOD_SHIFT to 0x24
            '*' -> MOD_SHIFT to 0x25
            '(' -> MOD_SHIFT to 0x26
            ')' -> MOD_SHIFT to 0x27
            else -> null
        }
    }

    /** Returns usage for a non-character Android key code (backspace, arrows, ...). */
    fun forAndroidKey(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_DEL -> KEY_BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> KEY_DELETE
        KeyEvent.KEYCODE_ENTER -> KEY_ENTER
        KeyEvent.KEYCODE_TAB -> KEY_TAB
        KeyEvent.KEYCODE_ESCAPE -> KEY_ESC
        KeyEvent.KEYCODE_DPAD_UP -> KEY_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> KEY_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> KEY_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KEY_RIGHT
        else -> null
    }
}
