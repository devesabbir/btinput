package com.example.btinput

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Touchpad surface.
 *  - One finger drag: move the pointer
 *  - One finger tap: left click
 *  - Two finger tap: right click
 *  - Two finger drag: scroll
 */
class TouchpadView(context: Context, private val hid: HidManager) : View(context) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getTapTimeout().toLong() + 100

    private val bg = Paint().apply { color = Color.rgb(32, 34, 40) }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(120, 125, 140)
        textSize = 15f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    var sensitivity = 1.8f

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var moved = false
    private var maxPointers = 0
    private var scrollAccum = 0f
    private var remX = 0f
    private var remY = 0f

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        canvas.drawText("Touchpad", width / 2f, height / 2f - hint.textSize, hint)
        canvas.drawText("tap = click · two-finger tap = right click", width / 2f, height / 2f + hint.textSize * 0.4f, hint)
        canvas.drawText("two-finger drag = scroll", width / 2f, height / 2f + hint.textSize * 1.8f, hint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y
                lastX = e.x; lastY = e.y
                downTime = e.eventTime
                moved = false
                maxPointers = 1
                scrollAccum = 0f
                remX = 0f; remY = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                lastX = avgX(e); lastY = avgY(e)
            }

            MotionEvent.ACTION_MOVE -> {
                val x = avgX(e)
                val y = avgY(e)
                val dx = x - lastX
                val dy = y - lastY
                if (abs(x - downX) > slop || abs(y - downY) > slop) moved = true

                if (e.pointerCount >= 2) {
                    // Scroll: accumulate until we have a whole wheel step.
                    scrollAccum += dy / 18f
                    val steps = scrollAccum.toInt()
                    if (steps != 0) {
                        hid.sendMouse(0, 0, 0, -steps)
                        scrollAccum -= steps
                    }
                } else if (moved) {
                    val mx = dx * sensitivity + remX
                    val my = dy * sensitivity + remY
                    val ix = mx.toInt()
                    val iy = my.toInt()
                    remX = mx - ix
                    remY = my - iy
                    if (ix != 0 || iy != 0) hid.sendMouse(0, ix, iy)
                }
                lastX = x; lastY = y
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Re-anchor on the remaining fingers so the pointer doesn't jump.
                val skip = e.actionIndex
                var sx = 0f; var sy = 0f; var n = 0
                for (i in 0 until e.pointerCount) {
                    if (i == skip) continue
                    sx += e.getX(i); sy += e.getY(i); n++
                }
                if (n > 0) { lastX = sx / n; lastY = sy / n }
            }

            MotionEvent.ACTION_UP -> {
                val quick = e.eventTime - downTime < tapTimeout
                if (!moved && quick) {
                    if (maxPointers >= 2) click(2) else click(1)
                }
            }
        }
        return true
    }

    private fun click(button: Int) {
        hid.sendMouse(button, 0, 0)
        hid.sendMouse(0, 0, 0)
    }

    private fun avgX(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getX(i)
        return s / e.pointerCount
    }

    private fun avgY(e: MotionEvent): Float {
        var s = 0f
        for (i in 0 until e.pointerCount) s += e.getY(i)
        return s / e.pointerCount
    }
}
