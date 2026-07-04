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
    val row1Numbers = "1234567890".toCharArray()
    val row2 = "asdfghjklñ".toCharArray()
    val row3 = "zxcvbnm".toCharArray()
}

sealed class KeyType {
    data class Letter(val char: Char, val number: Char? = null) : KeyType()
    object Shift : KeyType()
    object Backspace : KeyType()
    object Numbers123 : KeyType()
    object Emoji : KeyType()
    object Space : KeyType()
    object Period : KeyType()
    object Enter : KeyType()
}

data class KeyInfo(val type: KeyType, val rect: RectF)

class KeyboardView(context: Context) : View(context) {

    private val keys = mutableListOf<KeyInfo>()
    private var isUpperCase = false

    private val keyPaint = Paint().apply {
        color = Color.parseColor("#F4F4F6")
        style = Paint.Style.FILL
    }
    private val specialKeyPaint = Paint().apply {
        color = Color.parseColor("#ADB1BC")
        style = Paint.Style.FILL
    }
    private val enterPaint = Paint().apply {
        color = Color.parseColor("#1668E3")
        style = Paint.Style.FILL
    }
    private val pressedPaint = Paint().apply {
        color = Color.parseColor("#C8CAD2")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.parseColor("#1C1C1E")
        textSize = 44f
        textAlign = Paint.Align.CENTER
    }
    private val numberTextPaint = Paint().apply {
        color = Color.parseColor("#6E6E73")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }
    private val whiteTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 44f
        textAlign = Paint.Align.CENTER
    }
    private val labelTextPaint = Paint().apply {
        color = Color.parseColor("#1C1C1E")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var pressedKey: KeyInfo? = null

    var onLetterTyped: ((Char) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onPeriod: (() -> Unit)? = null
    var onShiftToggle: ((Boolean) -> Unit)? = null
    var onNumbers123: (() -> Unit)? = null
    var onEmoji: (() -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildKeyLayout(w, h)
    }

    private fun buildKeyLayout(width: Int, height: Int) {
        keys.clear()
        val rowHeight = height / 4f

        val row1Width = width / SpanishLayout.row1.size.toFloat()
        SpanishLayout.row1.forEachIndexed { i, letter ->
            val left = i * row1Width
            keys.add(
                KeyInfo(
                    KeyType.Letter(letter, SpanishLayout.row1Numbers[i]),
                    RectF(left, 0f, left + row1Width, rowHeight)
                )
            )
        }

        val row2Width = width / SpanishLayout.row2.size.toFloat()
        SpanishLayout.row2.forEachIndexed { i, letter ->
            val left = i * row2Width
            keys.add(
                KeyInfo(
                    KeyType.Letter(letter),
                    RectF(left, rowHeight, left + row2Width, rowHeight * 2)
                )
            )
        }

        val middleCount = SpanishLayout.row3.size
        val sideKeyWidth = width * 0.13f
        val middleWidth = (width - sideKeyWidth * 2) / middleCount.toFloat()

        keys.add(
            KeyInfo(KeyType.Shift, RectF(0f, rowHeight * 2, sideKeyWidth, rowHeight * 3))
        )
        SpanishLayout.row3.forEachIndexed { i, letter ->
            val left = sideKeyWidth + i * middleWidth
            keys.add(
                KeyInfo(
                    KeyType.Letter(letter),
                    RectF(left, rowHeight * 2, left + middleWidth, rowHeight * 3)
                )
            )
        }
        keys.add(
            KeyInfo(
                KeyType.Backspace,
                RectF(width - sideKeyWidth, rowHeight * 2, width.toFloat(), rowHeight * 3)
            )
        )

        val numbersWidth = width * 0.16f
        val emojiWidth = width * 0.12f
        val periodWidth = width * 0.12f
        val enterWidth = width * 0.20f
        val spaceWidth = width - numbersWidth - emojiWidth - periodWidth - enterWidth

        var cursor = 0f
        keys.add(
            KeyInfo(KeyType.Numbers123, RectF(cursor, rowHeight * 3, cursor + numbersWidth, height.toFloat()))
        )
        cursor += numbersWidth
        keys.add(
            KeyInfo(KeyType.Emoji, RectF(cursor, rowHeight * 3, cursor + emojiWidth, height.toFloat()))
        )
        cursor += emojiWidth
        keys.add(
            KeyInfo(KeyType.Space, RectF(cursor, rowHeight * 3, cursor + spaceWidth, height.toFloat()))
        )
        cursor += spaceWidth
        keys.add(
            KeyInfo(KeyType.Period, RectF(cursor, rowHeight * 3, cursor + periodWidth, height.toFloat()))
        )
        cursor += periodWidth
        keys.add(
            KeyInfo(KeyType.Enter, RectF(cursor, rowHeight * 3, width.toFloat(), height.toFloat()))
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val margin = 4f

        keys.forEach { key ->
            val r = RectF(
                key.rect.left + margin,
                key.rect.top + margin,
                key.rect.right - margin,
                key.rect.bottom - margin
            )

            val paint = when {
                key == pressedKey -> pressedPaint
                key.type is KeyType.Enter -> enterPaint
                key.type is KeyType.Shift || key.type is KeyType.Backspace ||
                    key.type is KeyType.Numbers123 || key.type is KeyType.Emoji -> specialKeyPaint
                else -> keyPaint
            }

            canvas.drawRoundRect(r, 8f, 8f, paint)
            drawKeyContent(canvas, key)
        }
    }

    private fun drawKeyContent(canvas: Canvas, key: KeyInfo) {
        val cx = key.rect.centerX()
        val cy = key.rect.centerY()

        when (val type = key.type) {
            is KeyType.Letter -> {
                val display = if (isUpperCase) type.char.uppercaseChar() else type.char
                canvas.drawText(display.toString(), cx, cy + 15f, textPaint)
                type.number?.let {
                    canvas.drawText(it.toString(), cx, key.rect.top + 24f, numberTextPaint)
                }
            }
            KeyType.Shift -> canvas.drawText("⇧", cx, cy + 15f, textPaint)
            KeyType.Backspace -> canvas.drawText("⌫", cx, cy + 15f, textPaint)
            KeyType.Numbers123 -> canvas.drawText("?123", cx, cy + 10f, labelTextPaint)
            KeyType.Emoji -> canvas.drawText("☺", cx, cy + 15f, textPaint)
            KeyType.Space -> canvas.drawText("español", cx, cy + 10f, labelTextPaint)
            KeyType.Period -> canvas.drawText(".", cx, cy + 15f, textPaint)
            KeyType.Enter -> canvas.drawText("↵", cx, cy + 15f, whiteTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedKey = keys.firstOrNull { it.rect.contains(event.x, event.y) }
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                pressedKey?.let { key ->
                    when (val type = key.type) {
                        is KeyType.Letter -> {
                            val letter = if (isUpperCase) type.char.uppercaseChar() else type.char
                            onLetterTyped?.invoke(letter)
                        }
                        KeyType.Shift -> {
                            isUpperCase = !isUpperCase
                            onShiftToggle?.invoke(isUpperCase)
                        }
                        KeyType.Backspace -> onBackspace?.invoke()
                        KeyType.Numbers123 -> onNumbers123?.invoke()
                        KeyType.Emoji -> onEmoji?.invoke()
                        KeyType.Space -> onSpace?.invoke()
                        KeyType.Period -> onPeriod?.invoke()
                        KeyType.Enter -> onEnter?.invoke()
                    }
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

    fun keyAt(x: Float, y: Float): Char? {
        val key = keys.firstOrNull { it.rect.contains(x, y) }
        return (key?.type as? KeyType.Letter)?.char
    }
}
