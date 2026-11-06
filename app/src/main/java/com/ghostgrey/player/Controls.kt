package com.ghostgrey.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/** A touch button. Circle if square, pill if wide. */
class PadButton(
    ctx: Context,
    private val label: String,
    private val onChange: (Boolean) -> Unit,
) : View(ctx) {

    var active = false
        set(v) { field = v; invalidate() }
    private var down = false

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()

    private fun set(v: Boolean) {
        if (v == down) return
        down = v
        onChange(v)
        if (v) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> set(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> set(false)
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        fill.color = if (down || active) Color.parseColor("#888888") else Color.parseColor("#3A3A3A")
        rect.set(2f, 2f, width - 2f, height - 2f)
        val r = minOf(width, height) / 2f
        c.drawRoundRect(rect, r, r, fill)
        text.textSize = minOf(width, height) * 0.38f
        c.drawText(label, width / 2f, height / 2f - (text.descent() + text.ascent()) / 2f, text)
    }
}

/** 8-way D-pad driven by a single finger. */
class DPad(
    ctx: Context,
    private val onDir: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit,
) : View(ctx) {

    private var up = false
    private var dn = false
    private var lf = false
    private var rt = false

    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#262626") }
    private val on = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#888888") }
    private val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3A3A3A") }
    private val rect = RectF()

    private fun update(u: Boolean, d: Boolean, l: Boolean, r: Boolean) {
        if (u == up && d == dn && l == lf && r == rt) return
        up = u; dn = d; lf = l; rt = r
        onDir(u, d, l, r)
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = e.x - width / 2f
                val dy = e.y - height / 2f
                val dead = width * 0.12f
                update(dy < -dead, dy > dead, dx < -dead, dx > dead)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> update(false, false, false, false)
        }
        return true
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawCircle(w / 2, h / 2, minOf(w, h) / 2, bg)
        val s = w * 0.26f
        fun cell(cx: Float, cy: Float, p: Paint) {
            rect.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
            c.drawRoundRect(rect, s * 0.25f, s * 0.25f, p)
        }
        cell(w / 2, h * 0.18f, if (up) on else off)
        cell(w / 2, h * 0.82f, if (dn) on else off)
        cell(w * 0.18f, h / 2, if (lf) on else off)
        cell(w * 0.82f, h / 2, if (rt) on else off)
        cell(w / 2, h / 2, off)
    }
}
