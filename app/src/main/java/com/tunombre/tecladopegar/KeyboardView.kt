package com.tunombre.tecladopegar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

object SpanishLayout {
    val row1 = "qwertyuiop".toCharArray()
    val row1Numbers = "1234567890".toCharArray()
    val row2 = "asdfghjklñ".toCharArray()
    val row3 = "zxcvbnm".toCharArray()
}

object NumbersLayout {
    val row1 = "1234567890".toCharArray()
    val row2 = "@#\$_&-+()/".toCharArray()
    val row3 = "*\"':;!?".toCharArray()
}

object SymbolsLayout {
    val row1 = "~`|•√π÷×§∆".toCharArray()
    val row2 = "£€¢¤^°={}\\".toCharArray()
    val row3 = "%©®™✓[]".toCharArray()
}

object EmojiData {
    // 24 emojis: 3 filas x 8 columnas
    val emojis = listOf(
        "😀", "😂", "😍", "😊", "😉", "😢", "😡", "😱",
        "👍", "👎", "👏", "🙌", "🙏", "💪", "❤️", "🔥",
        "🎉", "🎂", "⚽", "🚗", "🍕", "🌞", "🌙", "⭐"
    )
}

enum class KeyboardMode { LETTERS, NUMBERS, SYMBOLS, EMOJI }
enum class ShiftState { OFF, SINGLE, CAPS_LOCK }

sealed class KeyType {
    data class Letter(val char: Char, val number: Char? = null) : KeyType()
    data class Symbol(val char: Char) : KeyType()
    data class Emoji(val emoji: String) : KeyType()
    object Shift : KeyType()
    object Backspace : KeyType()
    object Numbers123 : KeyType()      // Letras -> Números
    object BackToLetters : KeyType()   // Números/Símbolos/Emoji -> Letras (ABC)
    object ModeSwitchLeft : KeyType()  // "=\<" (Números->Símbolos) / "?123" (Símbolos->Números)
    object NumSymToggle : KeyType()    // botón "12/34": alterna Números <-> Símbolos
    object CommaEmoji : KeyType()      // toque = coma, mantener presionado = abre emojis
    object LessThan : KeyType()
    object GreaterThan : KeyType()
    object Space : KeyType()
    object Period : KeyType()
    object Enter : KeyType()
}

data class KeyInfo(val type: KeyType, val rect: RectF)

class KeyboardView(context: Context) : View(context) {

    private val keys = mutableListOf<KeyInfo>()
    private var shiftState = ShiftState.OFF
    private val isUpperCase: Boolean get() = shiftState != ShiftState.OFF
    private var mode = KeyboardMode.LETTERS

    private val keyPaint = Paint().apply {
        color = Color.parseColor("#F4F4F6")
        style = Paint.Style.FILL
    }
    private val specialKeyPaint = Paint().apply {
        color = Color.parseColor("#ADB1BC")
        style = Paint.Style.FILL
    }
    private val shiftSinglePaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }
    private val shiftLockPaint = Paint().apply {
        color = Color.parseColor("#1C1C1E")
        style = Paint.Style.FILL
    }
    private val lockIndicatorPaint = Paint().apply {
        color = Color.WHITE
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
    private val symbolTextPaint = Paint().apply {
        color = Color.parseColor("#1C1C1E")
        textSize = 38f
        textAlign = Paint.Align.CENTER
    }
    private val emojiTextPaint = Paint().apply {
        textSize = 40f
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
    private val smallLabelTextPaint = Paint().apply {
        color = Color.parseColor("#1C1C1E")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private var pressedKey: KeyInfo? = null

    // Callbacks hacia MyKeyboardService
    var onLetterTyped: ((Char) -> Unit)? = null
    var onCharTyped: ((Char) -> Unit)? = null
    var onEmojiTyped: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onShiftToggle: ((Boolean) -> Unit)? = null

    private val actionHandler = Handler(Looper.getMainLooper())

    // Long press (coma -> emoji)
    private var longPressTriggered = false
    private var longPressRunnable: Runnable? = null

    // Doble clic de Shift
    private var lastShiftTapTime = 0L
    private var pendingShiftRunnable: Runnable? = null
    private val doubleTapThresholdMs = 280L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildKeyLayout(w, h)
    }

    private fun buildKeyLayout(width: Int, height: Int) {
        keys.clear()
        if (width == 0 || height == 0) return

        when (mode) {
            KeyboardMode.LETTERS -> buildLettersLayout(width, height)
            KeyboardMode.NUMBERS -> buildNumbersOrSymbolsLayout(width, height, isNumbers = true)
            KeyboardMode.SYMBOLS -> buildNumbersOrSymbolsLayout(width, height, isNumbers = false)
            KeyboardMode.EMOJI -> buildEmojiLayout(width, height)
        }
    }

    private fun buildLettersLayout(width: Int, height: Int) {
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

        keys.add(KeyInfo(KeyType.Shift, RectF(0f, rowHeight * 2, sideKeyWidth, rowHeight * 3)))
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

        // Fila 4: ?123 | coma/emoji | espacio | . | enter
        val numbersWidth = width * 0.16f
        val emojiWidth = width * 0.12f
        val periodWidth = width * 0.12f
        val enterWidth = width * 0.20f
        val spaceWidth = width - numbersWidth - emojiWidth - periodWidth - enterWidth

        var cursor = 0f
        keys.add(KeyInfo(KeyType.Numbers123, RectF(cursor, rowHeight * 3, cursor + numbersWidth, height.toFloat())))
        cursor += numbersWidth
        keys.add(KeyInfo(KeyType.CommaEmoji, RectF(cursor, rowHeight * 3, cursor + emojiWidth, height.toFloat())))
        cursor += emojiWidth
        keys.add(KeyInfo(KeyType.Space, RectF(cursor, rowHeight * 3, cursor + spaceWidth, height.toFloat())))
        cursor += spaceWidth
        keys.add(KeyInfo(KeyType.Period, RectF(cursor, rowHeight * 3, cursor + periodWidth, height.toFloat())))
        cursor += periodWidth
        keys.add(KeyInfo(KeyType.Enter, RectF(cursor, rowHeight * 3, width.toFloat(), height.toFloat())))
    }

    private fun buildNumbersOrSymbolsLayout(width: Int, height: Int, isNumbers: Boolean) {
        val rowHeight = height / 4f
        val row1Chars = if (isNumbers) NumbersLayout.row1 else SymbolsLayout.row1
        val row2Chars = if (isNumbers) NumbersLayout.row2 else SymbolsLayout.row2
        val row3Chars = if (isNumbers) NumbersLayout.row3 else SymbolsLayout.row3

        val row1Width = width / row1Chars.size.toFloat()
        row1Chars.forEachIndexed { i, c ->
            val left = i * row1Width
            keys.add(KeyInfo(KeyType.Symbol(c), RectF(left, 0f, left + row1Width, rowHeight)))
        }

        val row2Width = width / row2Chars.size.toFloat()
        row2Chars.forEachIndexed { i, c ->
            val left = i * row2Width
            keys.add(KeyInfo(KeyType.Symbol(c), RectF(left, rowHeight, left + row2Width, rowHeight * 2)))
        }

        // Fila 3: ModeSwitchLeft | 7 símbolos | Backspace
        val middleCount = row3Chars.size
        val sideKeyWidth = width * 0.13f
        val middleWidth = (width - sideKeyWidth * 2) / middleCount.toFloat()

        keys.add(KeyInfo(KeyType.ModeSwitchLeft, RectF(0f, rowHeight * 2, sideKeyWidth, rowHeight * 3)))
        row3Chars.forEachIndexed { i, c ->
            val left = sideKeyWidth + i * middleWidth
            keys.add(KeyInfo(KeyType.Symbol(c), RectF(left, rowHeight * 2, left + middleWidth, rowHeight * 3)))
        }
        keys.add(
            KeyInfo(
                KeyType.Backspace,
                RectF(width - sideKeyWidth, rowHeight * 2, width.toFloat(), rowHeight * 3)
            )
        )

        // Fila 4: ABC | coma(o <) | 12/34 | espacio | .(o >) | enter
        val abcWidth = width * 0.16f
        val sideSmallWidth = width * 0.12f
        val toggleWidth = width * 0.12f
        val enterWidth = width * 0.20f
        val spaceWidth = width - abcWidth - sideSmallWidth - toggleWidth - enterWidth - sideSmallWidth

        var cursor = 0f
        keys.add(KeyInfo(KeyType.BackToLetters, RectF(cursor, rowHeight * 3, cursor + abcWidth, height.toFloat())))
        cursor += abcWidth
        val leftBottomType = if (isNumbers) KeyType.CommaEmoji else KeyType.LessThan
        keys.add(KeyInfo(leftBottomType, RectF(cursor, rowHeight * 3, cursor + sideSmallWidth, height.toFloat())))
        cursor += sideSmallWidth
        keys.add(KeyInfo(KeyType.NumSymToggle, RectF(cursor, rowHeight * 3, cursor + toggleWidth, height.toFloat())))
        cursor += toggleWidth
        keys.add(KeyInfo(KeyType.Space, RectF(cursor, rowHeight * 3, cursor + spaceWidth, height.toFloat())))
        cursor += spaceWidth
        val rightBottomType = if (isNumbers) KeyType.Period else KeyType.GreaterThan
        keys.add(KeyInfo(rightBottomType, RectF(cursor, rowHeight * 3, cursor + sideSmallWidth, height.toFloat())))
        cursor += sideSmallWidth
        keys.add(KeyInfo(KeyType.Enter, RectF(cursor, rowHeight * 3, width.toFloat(), height.toFloat())))
    }

    private fun buildEmojiLayout(width: Int, height: Int) {
        val cols = 8
        val rows = 3
        val gridHeight = height * 0.75f
        val rowHeight = gridHeight / rows
        val colWidth = width / cols.toFloat()

        EmojiData.emojis.take(rows * cols).forEachIndexed { i, emoji ->
            val r = i / cols
            val c = i % cols
            val left = c * colWidth
            val top = r * rowHeight
            keys.add(KeyInfo(KeyType.Emoji(emoji), RectF(left, top, left + colWidth, top + rowHeight)))
        }

        // Fila inferior: ABC | espacio | borrar | enter
        val bottomTop = gridHeight
        val abcWidth = width * 0.22f
        val backspaceWidth = width * 0.18f
        val enterWidth = width * 0.20f
        val spaceWidth = width - abcWidth - backspaceWidth - enterWidth

        var cursor = 0f
        keys.add(KeyInfo(KeyType.BackToLetters, RectF(cursor, bottomTop, cursor + abcWidth, height.toFloat())))
        cursor += abcWidth
        keys.add(KeyInfo(KeyType.Space, RectF(cursor, bottomTop, cursor + spaceWidth, height.toFloat())))
        cursor += spaceWidth
        keys.add(KeyInfo(KeyType.Backspace, RectF(cursor, bottomTop, cursor + backspaceWidth, height.toFloat())))
        cursor += backspaceWidth
        keys.add(KeyInfo(KeyType.Enter, RectF(cursor, bottomTop, width.toFloat(), height.toFloat())))
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
                key == pressedKey && key.type !is KeyType.Shift -> pressedPaint
                key.type is KeyType.Enter -> enterPaint
                key.type is KeyType.Shift -> when (shiftState) {
                    ShiftState.OFF -> specialKeyPaint
                    ShiftState.SINGLE -> shiftSinglePaint
                    ShiftState.CAPS_LOCK -> shiftLockPaint
                }
                key.type is KeyType.Backspace || key.type is KeyType.Numbers123 ||
                    key.type is KeyType.BackToLetters || key.type is KeyType.ModeSwitchLeft ||
                    key.type is KeyType.NumSymToggle -> specialKeyPaint
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
            is KeyType.Symbol -> canvas.drawText(type.char.toString(), cx, cy + 13f, symbolTextPaint)
            is KeyType.Emoji -> canvas.drawText(type.emoji, cx, cy + 14f, emojiTextPaint)
            KeyType.Shift -> {
                val arrowPaint = if (shiftState == ShiftState.CAPS_LOCK) whiteTextPaint else textPaint
                canvas.drawText("⇧", cx, cy + 8f, arrowPaint)
                if (shiftState == ShiftState.CAPS_LOCK) {
                    canvas.drawRoundRect(
                        RectF(cx - 14f, key.rect.bottom - 16f, cx + 14f, key.rect.bottom - 9f),
                        2f, 2f, lockIndicatorPaint
                    )
                }
            }
            KeyType.Backspace -> canvas.drawText("⌫", cx, cy + 15f, textPaint)
            KeyType.Numbers123 -> canvas.drawText("?123", cx, cy + 10f, labelTextPaint)
            KeyType.BackToLetters -> canvas.drawText("ABC", cx, cy + 10f, labelTextPaint)
            KeyType.ModeSwitchLeft -> {
                val label = if (mode == KeyboardMode.NUMBERS) "=\\<" else "?123"
                canvas.drawText(label, cx, cy + 10f, labelTextPaint)
            }
            KeyType.NumSymToggle -> {
                canvas.drawText("12", cx, cy - 2f, smallLabelTextPaint)
                canvas.drawText("34", cx, cy + 24f, smallLabelTextPaint)
            }
            KeyType.CommaEmoji -> {
                canvas.drawText("☺", cx, cy + 5f, textPaint)
                canvas.drawText(",", cx, cy + 32f, labelTextPaint)
            }
            KeyType.LessThan -> canvas.drawText("<", cx, cy + 15f, textPaint)
            KeyType.GreaterThan -> canvas.drawText(">", cx, cy + 15f, textPaint)
            KeyType.Space -> canvas.drawText("español", cx, cy + 10f, labelTextPaint)
            KeyType.Period -> canvas.drawText(".", cx, cy + 15f, textPaint)
            KeyType.Enter -> canvas.drawText("↵", cx, cy + 15f, whiteTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = keys.firstOrNull { it.rect.contains(event.x, event.y) }
                pressedKey = key
                invalidate()

                if (key?.type is KeyType.CommaEmoji) {
                    longPressTriggered = false
                    val runnable = Runnable {
                        longPressTriggered = true
                        mode = KeyboardMode.EMOJI
                        buildKeyLayout(width, height)
                        invalidate()
                    }
                    longPressRunnable = runnable
                    actionHandler.postDelayed(runnable, 350L)
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { actionHandler.removeCallbacks(it) }

                pressedKey?.let { key ->
                    when (val type = key.type) {
                        is KeyType.Letter -> {
                            val letter = if (isUpperCase) type.char.uppercaseChar() else type.char
                            onLetterTyped?.invoke(letter)
                            if (shiftState == ShiftState.SINGLE) {
                                shiftState = ShiftState.OFF
                                onShiftToggle?.invoke(false)
                            }
                        }
                        is KeyType.Symbol -> onCharTyped?.invoke(type.char)
                        is KeyType.Emoji -> onEmojiTyped?.invoke(type.emoji)
                        KeyType.Shift -> handleShiftTap()
                        KeyType.Backspace -> onBackspace?.invoke()
                        KeyType.Numbers123 -> {
                            mode = KeyboardMode.NUMBERS
                            buildKeyLayout(width, height)
                        }
                        KeyType.BackToLetters -> {
                            mode = KeyboardMode.LETTERS
                            buildKeyLayout(width, height)
                        }
                        KeyType.ModeSwitchLeft, KeyType.NumSymToggle -> {
                            mode = if (mode == KeyboardMode.NUMBERS) KeyboardMode.SYMBOLS else KeyboardMode.NUMBERS
                            buildKeyLayout(width, height)
                        }
                        KeyType.CommaEmoji -> {
                            if (!longPressTriggered) onCharTyped?.invoke(',')
                        }
                        KeyType.LessThan -> onCharTyped?.invoke('<')
                        KeyType.GreaterThan -> onCharTyped?.invoke('>')
                        KeyType.Space -> onSpace?.invoke()
                        KeyType.Period -> onCharTyped?.invoke('.')
                        KeyType.Enter -> onEnter?.invoke()
                    }
                    Unit
                }
                pressedKey = null
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { actionHandler.removeCallbacks(it) }
                pressedKey = null
                invalidate()
            }
        }
        return true
    }

    /**
     * Un clic alterna OFF <-> SINGLE (mayúscula solo en la próxima letra).
     * Doble clic rápido activa CAPS_LOCK (mayúscula sostenida) sin pasar por SINGLE.
     */
    private fun handleShiftTap() {
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastShiftTapTime) < doubleTapThresholdMs

        if (isDoubleTap) {
            pendingShiftRunnable?.let { actionHandler.removeCallbacks(it) }
            pendingShiftRunnable = null
            lastShiftTapTime = 0L
            shiftState = ShiftState.CAPS_LOCK
            onShiftToggle?.invoke(true)
            invalidate()
        } else {
            lastShiftTapTime = now
            val runnable = Runnable {
                shiftState = when (shiftState) {
                    ShiftState.OFF -> ShiftState.SINGLE
                    ShiftState.SINGLE -> ShiftState.OFF
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
                onShiftToggle?.invoke(isUpperCase)
                invalidate()
            }
            pendingShiftRunnable = runnable
            actionHandler.postDelayed(runnable, doubleTapThresholdMs)
        }
    }

    fun keyAt(x: Float, y: Float): Char? {
        val key = keys.firstOrNull { it.rect.contains(x, y) }
        return (key?.type as? KeyType.Letter)?.char
    }
}
