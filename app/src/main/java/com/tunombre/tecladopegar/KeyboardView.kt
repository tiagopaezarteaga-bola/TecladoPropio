package com.tunombre.tecladopegar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

object SpanishLayout {
    val row1 = "qwertyuiop".toCharArray()
    val row2 = "asdfghjklñ".toCharArray()
    val row3 = "zxcvbnm".toCharArray()
}

data class KeyInfo(val label: Char, val rect: RectF)

class KeyboardView(context: Context) : View(context) {

    private val keys = mutableListOf<KeyInfo>()
    private var isUpperCase = false

    private val keyPaint = Paint().apply {
        color = Color.parseColor("#3A3A3A")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }
    private val pressedPaint = Paint().apply {
        color = Color.parseColor("#565656")
        style = Paint.Style.FILL
    }

    private var pressedKey: KeyInfo? = null

    var onLetterTyped: ((Char) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onShiftToggle: ((Boolean) -> Unit)? = null

    private lateinit var backspaceRect: RectF
    private lateinit var spaceRect: RectF
    private lateinit var enterRect: RectF

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildKeyLayout(w, h)
    }

    private fun buildKeyLayout(width: Int, height: Int) {
        keys.clear()
        val rows = listOf(SpanishLayout.row1, SpanishLayout.row2, SpanishLayout.row3)
        val rowHeight = height / 4f // 3 filas de letras + 1 fila de espacio/enter/borrar

        rows.forEachIndexed { rowIndex, row ->
            val keyWidth = width / row.size.toFloat()
            row.forEachIndexed { colIndex, letter ->
                val left = colIndex * keyWidth
                val top = rowIndex * rowHeight
                keys.add(
                    KeyInfo(
                        label = letter,
                        rect = RectF(left, top, left + keyWidth, top + rowHeight)
                    )
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        keys.forEach { key ->
            val paint = if (key == pressedKey) pressedPaint else keyPaint
            canvas.drawRect(key.rect, paint)

            val displayChar = if (isUpperCase) key.label.uppercaseChar() else key.label
            canvas.drawText(
                displayChar.toString(),
                key.rect.centerX(),
                key.rect.centerY() + 14f,
                textPaint
            )
        }

        drawBottomRow(canvas)
    }

    private fun drawBottomRow(canvas: Canvas) {
        val bottomTop = height * 0.75f
        val bottomHeight = height * 0.25f

        backspaceRect = RectF(0f, bottomTop, width * 0.2f, bottomTop + bottomHeight)
        spaceRect = RectF(width * 0.2f, bottomTop, width * 0.7f, bottomTop + bottomHeight)
        enterRect = RectF(width * 0.7f, bottomTop, width.toFloat(), bottomTop + bottomHeight)

        canvas.drawRect(backspaceRect, keyPaint)
        canvas.drawRect(spaceRect, keyPaint)
        canvas.drawRect(enterRect, keyPaint)

        canvas.drawText("⌫", backspaceRect.centerX(), backspaceRect.centerY() + 14f, textPaint)
        canvas.drawText("espacio", spaceRect.centerX(), spaceRect.centerY() + 14f, textPaint)
        canvas.drawText("↵", enterRect.centerX(), enterRect.centerY() + 14f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = keys.firstOrNull { it.rect.contains(event.x, event.y) }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pressedKey?.let { key ->
                    val letter = if (isUpperCase) key.label.uppercaseChar() else key.label
                    onLetterTyped?.invoke(letter)
                }

                if (::backspaceRect.isInitialized && backspaceRect.contains(event.x, event.y)) {
                    onBackspace?.invoke()
                }
                if (::spaceRect.isInitialized && spaceRect.contains(event.x, event.y)) {
                    onSpace?.invoke()
                }
                if (::enterRect.isInitialized && enterRect.contains(event.x, event.y)) {
                    onEnter?.invoke()
                }

                pressedKey = null
                invalidate()
            }
        }
        return true
    }

    fun setUpperCase(upper: Boolean) {
        isUpperCase = upper
        invalidate()
    }

    // Expuesto para el futuro reconocedor de gestos (swipe)
    fun keyAt(x: Float, y: Float): Char? {
        return keys.firstOrNull { it.rect.contains(x, y) }?.label
    }
}
