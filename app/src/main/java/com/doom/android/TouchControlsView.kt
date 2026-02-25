package com.doom.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Transparent overlay view that draws on-screen touch controls and maps
 * touch events to DOOM key presses/releases.
 *
 * Layout (landscape):
 *
 *   Left side  – D-pad  (UP / DOWN / LEFT / RIGHT)  + strafe buttons
 *   Right side – FIRE, USE, RUN, ESC, MAP buttons
 */
class TouchControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---- Painting -------------------------------------------------------

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        alpha = 180
    }

    // ---- Button descriptor ----------------------------------------------

    private data class Button(
        val label: String,
        val doomKey: Int,
        val color: Int,
        var rect: RectF = RectF(),
        var activePointers: MutableSet<Int> = mutableSetOf()
    )

    private val buttons = listOf(
        // D-pad
        Button("▲",  DoomEngine.Key.UPARROW,   Color.argb(160, 80, 80, 80)),
        Button("▼",  DoomEngine.Key.DOWNARROW,  Color.argb(160, 80, 80, 80)),
        Button("◀",  DoomEngine.Key.LEFTARROW,  Color.argb(160, 80, 80, 80)),
        Button("▶",  DoomEngine.Key.RIGHTARROW, Color.argb(160, 80, 80, 80)),
        Button("SL", DoomEngine.Key.STRAFE_L,   Color.argb(140, 60, 60, 60)),
        Button("SR", DoomEngine.Key.STRAFE_R,   Color.argb(140, 60, 60, 60)),
        // Action buttons
        Button("FIRE", DoomEngine.Key.FIRE,    Color.argb(180, 180, 30, 30)),
        Button("USE",  DoomEngine.Key.USE,     Color.argb(180, 30, 100, 180)),
        Button("RUN",  DoomEngine.Key.RSHIFT,  Color.argb(160, 30, 160, 30)),
        Button("ESC",  DoomEngine.Key.ESCAPE,  Color.argb(160, 120, 120, 120)),
        Button("MAP",  DoomEngine.Key.TAB,     Color.argb(160, 120, 120, 120))
    )

    // Indices for quick access
    private val IDX_UP    = 0
    private val IDX_DOWN  = 1
    private val IDX_LEFT  = 2
    private val IDX_RIGHT = 3
    private val IDX_SL    = 4
    private val IDX_SR    = 5
    private val IDX_FIRE  = 6
    private val IDX_USE   = 7
    private val IDX_RUN   = 8
    private val IDX_ESC   = 9
    private val IDX_MAP   = 10

    // ---- Layout ---------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutButtons(w.toFloat(), h.toFloat())
    }

    private fun layoutButtons(w: Float, h: Float) {
        val btnSize  = h * 0.18f          // base button size relative to height
        val pad      = h * 0.02f
        val leftEdge = w * 0.01f
        val rightEdge = w - btnSize - pad

        // --- D-pad (left side) ---
        val dpadCX = leftEdge + btnSize * 1.5f
        val dpadCY = h - btnSize * 1.8f

        // UP
        buttons[IDX_UP].rect.set(dpadCX - btnSize * 0.5f, dpadCY - btnSize * 1.2f,
            dpadCX + btnSize * 0.5f, dpadCY - btnSize * 0.2f)
        // DOWN
        buttons[IDX_DOWN].rect.set(dpadCX - btnSize * 0.5f, dpadCY + btnSize * 0.2f,
            dpadCX + btnSize * 0.5f, dpadCY + btnSize * 1.2f)
        // LEFT
        buttons[IDX_LEFT].rect.set(dpadCX - btnSize * 1.2f, dpadCY - btnSize * 0.5f,
            dpadCX - btnSize * 0.2f, dpadCY + btnSize * 0.5f)
        // RIGHT
        buttons[IDX_RIGHT].rect.set(dpadCX + btnSize * 0.2f, dpadCY - btnSize * 0.5f,
            dpadCX + btnSize * 1.2f, dpadCY + btnSize * 0.5f)

        // Strafe buttons (below d-pad)
        val strafeY = dpadCY + btnSize * 1.5f
        val strafeW = btnSize * 0.8f
        buttons[IDX_SL].rect.set(dpadCX - btnSize * 1.2f, strafeY,
            dpadCX - btnSize * 1.2f + strafeW, strafeY + btnSize * 0.6f)
        buttons[IDX_SR].rect.set(dpadCX + btnSize * 0.4f, strafeY,
            dpadCX + btnSize * 0.4f + strafeW, strafeY + btnSize * 0.6f)

        // --- Action buttons (right side) ---
        val ax = rightEdge
        val fireH = btnSize * 1.5f

        // FIRE (big, bottom-right)
        buttons[IDX_FIRE].rect.set(ax, h - fireH - pad, ax + btnSize, h - pad)
        // USE (above fire)
        buttons[IDX_USE].rect.set(ax - btnSize - pad, h - btnSize - pad,
            ax - pad, h - pad)
        // RUN (above USE)
        buttons[IDX_RUN].rect.set(ax - btnSize - pad, h - btnSize * 2.2f - pad,
            ax - pad, h - btnSize * 1.2f - pad)
        // ESC (top-right corner)
        buttons[IDX_ESC].rect.set(w - btnSize * 0.9f - pad, pad,
            w - pad, pad + btnSize * 0.6f)
        // MAP (top-left)
        buttons[IDX_MAP].rect.set(leftEdge, pad,
            leftEdge + btnSize * 0.9f, pad + btnSize * 0.6f)
    }

    // ---- Drawing --------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        textPaint.textSize = height * 0.055f
        strokePaint.strokeWidth = 2f

        for (btn in buttons) {
            val isPressed = btn.activePointers.isNotEmpty()
            val alpha = if (isPressed) 230 else 160

            buttonPaint.color = btn.color
            buttonPaint.alpha = alpha
            canvas.drawRoundRect(btn.rect, 12f, 12f, buttonPaint)

            strokePaint.alpha = if (isPressed) 255 else 140
            canvas.drawRoundRect(btn.rect, 12f, 12f, strokePaint)

            textPaint.alpha = if (isPressed) 255 else 210
            canvas.drawText(
                btn.label,
                btn.rect.centerX(),
                btn.rect.centerY() + textPaint.textSize * 0.35f,
                textPaint
            )
        }
    }

    // ---- Touch handling -------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action    = event.actionMasked
        val ptrIndex  = event.actionIndex
        val pointerId = event.getPointerId(ptrIndex)

        when (action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(ptrIndex)
                val y = event.getY(ptrIndex)
                handlePointerDown(pointerId, x, y)
            }

            MotionEvent.ACTION_MOVE -> {
                // Multi-touch move: re-evaluate all active pointers
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x  = event.getX(i)
                    val y  = event.getY(i)
                    handlePointerMove(id, x, y)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                val x = event.getX(ptrIndex)
                val y = event.getY(ptrIndex)
                handlePointerUp(pointerId, x, y)
            }
        }

        invalidate()
        return true
    }

    private fun handlePointerDown(id: Int, x: Float, y: Float) {
        for (btn in buttons) {
            if (btn.rect.contains(x, y)) {
                if (btn.activePointers.add(id)) {
                    DoomEngine.nativeSendKey(true, btn.doomKey)
                }
            }
        }
    }

    private fun handlePointerMove(id: Int, x: Float, y: Float) {
        for (btn in buttons) {
            val inside = btn.rect.contains(x, y)
            val had    = btn.activePointers.contains(id)
            if (inside && !had) {
                btn.activePointers.add(id)
                DoomEngine.nativeSendKey(true, btn.doomKey)
            } else if (!inside && had) {
                btn.activePointers.remove(id)
                DoomEngine.nativeSendKey(false, btn.doomKey)
            }
        }
    }

    private fun handlePointerUp(id: Int, x: Float, y: Float) {
        for (btn in buttons) {
            if (btn.activePointers.remove(id)) {
                DoomEngine.nativeSendKey(false, btn.doomKey)
            }
        }
    }
}
