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
import android.view.ViewGroup
import android.widget.*
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// Layouts
// ─────────────────────────────────────────────────────────────────────────────

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

// ─────────────────────────────────────────────────────────────────────────────
// Emoji data — todas las categorías de WhatsApp
// ─────────────────────────────────────────────────────────────────────────────

object EmojiData {

    data class Category(val icon: String, val name: String, val emojis: List<String>)

    val recent = mutableListOf<String>()   // se rellena en tiempo de ejecución

    val categories = listOf(
        Category("🕐", "Recientes", emptyList()),   // placeholder — se llena dinámicamente
        Category("😀", "Emociones", listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇",
            "🥰","😍","🤩","😘","😗","☺️","😚","😙","🥲","😋","😛","😜","🤪",
            "😝","🤑","🤗","🤭","🤫","🤔","🤐","🤨","😐","😑","😶","😏","😒",
            "🙄","😬","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮",
            "🤧","🥵","🥶","🥴","😵","💫","🤯","🤠","🥳","🥸","😎","🤓","🧐",
            "😕","😟","🙁","☹️","😮","😯","😲","😳","🥺","😦","😧","😨","😰",
            "😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡",
            "😠","🤬","😈","👿","💀","☠️","💩","🤡","👹","👺","👻","👽","👾",
            "🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾"
        )),
        Category("👋", "Gestos", listOf(
            "👋","🤚","🖐️","✋","🖖","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙",
            "👈","👉","👆","🖕","👇","☝️","👍","👎","✊","👊","🤛","🤜","👏",
            "🙌","👐","🤲","🤝","🙏","✍️","💅","🤳","💪","🦾","🦵","🦿","🦶",
            "👂","🦻","👃","🫀","🫁","🧠","🦷","🦴","👀","👁️","👅","👄","💋",
            "🩸","👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓","👴","👵"
        )),
        Category("🐶", "Animales", listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️","🐨","🐯","🦁","🐮",
            "🐷","🐽","🐸","🐵","🙈","🙉","🙊","🐒","🐔","🐧","🐦","🐤","🐣",
            "🐥","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🐛","🦋","🐌",
            "🐞","🐜","🦟","🦗","🕷️","🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑",
            "🦐","🦞","🦀","🐡","🐠","🐟","🐬","🐳","🐋","🦈","🐊","🐅","🐆",
            "🦓","🦍","🦧","🦣","🐘","🦛","🦏","🐪","🐫","🦒","🦘","🦬","🐃"
        )),
        Category("🍎", "Comida", listOf(
            "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑",
            "🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌶️","🫑","🧄",
            "🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈","🥞",
            "🧇","🥓","🥩","🍗","🍖","🦴","🌭","🍔","🍟","🍕","🫓","🥪","🥙",
            "🧆","🌮","🌯","🫔","🥗","🥘","🫕","🥫","🍝","🍜","🍲","🍛","🍣",
            "🍱","🥟","🦪","🍤","🍙","🍚","🍘","🍥","🥮","🍢","🧁","🍰","🎂"
        )),
        Category("⚽", "Deportes", listOf(
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🏓","🏸","🏒",
            "🏑","🥍","🏏","🪃","🥅","⛳","🪁","🏹","🎣","🤿","🥊","🥋","🎽",
            "🛹","🛼","🛷","⛸️","🥌","🎿","⛷️","🏂","🪂","🏋️","🤼","🤸","⛹️",
            "🤺","🏇","🧘","🏄","🏊","🤽","🚣","🧗","🚵","🚴","🏆","🥇","🥈",
            "🥉","🏅","🎖️","🏵️","🎗️","🎫","🎟️","🎪","🤹","🎭","🎨","🎬","🎤"
        )),
        Category("🚗", "Viajes", listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚","🚛",
            "🚜","🏍️","🛵","🚲","🛴","🛺","🚨","🚔","🚍","🚘","🚖","🚡","🚠",
            "🚟","🚃","🚋","🚞","🚝","🚄","🚅","🚈","🚂","🚆","🚇","🚊","🚉",
            "✈️","🛫","🛬","🛩️","💺","🛸","🚁","🛶","⛵","🚤","🛥️","🛳️","⛴️",
            "🚀","🛟","⛽","🚧","⚓","🗺️","🧭","🏔️","⛰️","🌋","🗻","🏕️","🏖️"
        )),
        Category("💡", "Objetos", listOf(
            "⌚","📱","💻","⌨️","🖥️","🖨️","🖱️","🖲️","💽","💾","💿","📀","📷",
            "📸","📹","🎥","📽️","🎞️","📞","☎️","📟","📠","📺","📻","🧭","⏱️",
            "⏲️","⏰","🕰️","⌛","⏳","📡","🔋","🪫","🔌","💡","🔦","🕯️","🪔",
            "🧯","🛢️","💸","💵","💴","💶","💷","🪙","💰","💳","💎","⚖️","🪜",
            "🧲","🔧","🪛","🔩","⚙️","🗜️","⛏️","⚒️","🛠️","🗡️","⚔️","🛡️","🪚"
        )),
        Category("❤️", "Símbolos", listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞",
            "💓","💗","💖","💘","💝","💟","☮️","✝️","☪️","🕉️","☸️","✡️","🔯",
            "🕎","☯️","☦️","🛐","⛎","♈","♉","♊","♋","♌","♍","♎","♏",
            "♐","♑","♒","♓","🆔","⚛️","🉑","☢️","☣️","📴","📳","🈶","🈚",
            "🈸","🈺","🈷️","✴️","🆚","💮","🉐","㊙️","㊗️","🈴","🈵","🈹","🈲"
        )),
        Category("🎉", "Actividades", listOf(
            "🎃","🎄","🎆","🎇","🧨","✨","🎈","🎉","🎊","🎋","🎍","🎎","🎏",
            "🎐","🎑","🧧","🎁","🎀","🎗️","🎟️","🎫","🏮","🪔","🧸","🪆","🖼️",
            "🧵","🪡","🧶","🪢","👓","🕶️","🥽","🌂","☂️","🧵","👔","👕","👖",
            "🧣","🧤","🧥","🥼","👗","👘","🥻","🩱","🩲","🩳","👙","👚","👛",
            "👜","👝","🎒","🧳","👒","🎩","🧢","⛑️","💄","💍","💼","🌂","☂️"
        ))
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Predicción swipe — diccionario mínimo en español
// ─────────────────────────────────────────────────────────────────────────────

object SwipePredictor {

    // Diccionario vacío — las predicciones vienen íntegramente del PersonalDictionary.
    // Se mantiene el objeto para que el resto del código compile sin cambios.
    private val dictionary: List<String> = emptyList()

    /**
     * Dada una secuencia de letras que el dedo pasó, devuelve las N mejores palabras
     * del diccionario estático (vacío por diseño — usar PersonalDictionary desde el servicio).
     */
    fun predict(swipedLetters: List<Char>, topN: Int = 3): List<String> {
        if (swipedLetters.size < 2 || dictionary.isEmpty()) return emptyList()
        val swipeStr = swipedLetters.joinToString("")
        return dictionary
            .map { word -> word to swipeScore(swipeStr, word) }
            .sortedBy { it.second }
            .take(topN)
            .map { it.first }
    }

    /** Puntuación: Levenshtein sobre la clave + penalización por diferencia de longitud. */
    private fun swipeScore(swipe: String, word: String): Float {
        if (word.isEmpty()) return Float.MAX_VALUE
        val key = buildKey(word)
        val lev = levenshtein(swipe, key).toFloat()
        val lenDiff = abs(word.length - swipe.length) * 0.5f
        return lev + lenDiff
    }

    private fun buildKey(word: String): String {
        if (word.length <= 2) return word
        val mid = word.substring(1, word.length - 1).toList().distinct().joinToString("")
        return "${word.first()}$mid${word.last()}"
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Key model
// ─────────────────────────────────────────────────────────────────────────────

enum class KeyboardMode { LETTERS, NUMBERS, SYMBOLS, CALCULATOR, EMOJI }
enum class ShiftState { OFF, SINGLE, CAPS_LOCK }

sealed class KeyType {
    data class Letter(val char: Char, val number: Char? = null) : KeyType()
    data class Symbol(val char: Char) : KeyType()
    object Shift : KeyType()
    object Backspace : KeyType()
    object Numbers123 : KeyType()
    object BackToLetters : KeyType()
    object ModeSwitchLeft : KeyType()
    object NumSymToggle : KeyType()
    data class Operator(val char: Char) : KeyType()
    object Percent : KeyType()
    object Equals : KeyType()
    object CommaEmoji : KeyType()
    object LessThan : KeyType()
    object GreaterThan : KeyType()
    object Space : KeyType()
    object SpaceCompact : KeyType()
    object SymbolsPageToggle : KeyType()
    object Period : KeyType()
    object Enter : KeyType()
}

data class KeyInfo(val type: KeyType, val rect: RectF)

// ─────────────────────────────────────────────────────────────────────────────
// Panel de emojis scrolleable (View nativa con Canvas)
// ─────────────────────────────────────────────────────────────────────────────

class EmojiPanelView(context: Context) : View(context) {

    var onEmojiSelected: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onBackToLetters: (() -> Unit)? = null

    private val paint = Paint()
    private val textPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        textSize = 44f
    }
    private val labelPaint = Paint().apply {
        color = Color.parseColor("#1C1C1E")
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    private val tabPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        textSize = 38f
    }
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#F0F0F3")
        style = Paint.Style.FILL
    }
    private val selectedTabPaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }
    private val dividerPaint = Paint().apply {
        color = Color.parseColor("#D1D1D6")
        strokeWidth = 1f
    }
    private val bottomBarPaint = Paint().apply {
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
    private val whiteTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 44f
        textAlign = Paint.Align.CENTER
    }
    private val smallLabelPaint = Paint().apply {
        color = Color.parseColor("#1C1C1E")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var selectedCat = 0
    private var scrollY = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownScrollY = 0f

    private val tabH get() = height * 0.14f
    private val bottomBarH get() = height * 0.20f
    private val gridTop get() = tabH
    private val gridBottom get() = height - bottomBarH
    private val gridH get() = gridBottom - gridTop

    private val cols = 8
    private val cellSize get() = width / cols.toFloat()

    private fun currentEmojis(): List<String> {
        val cat = EmojiData.categories[selectedCat]
        return if (selectedCat == 0) EmojiData.recent.toList() else cat.emojis
    }

    private fun totalGridHeight(): Float {
        val n = currentEmojis().size
        val rows = ceil(n / cols.toFloat()).toInt()
        return rows * cellSize
    }

    private fun maxScroll() = maxOf(0f, totalGridHeight() - gridH)

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        drawTabs(canvas)
        drawGrid(canvas)
        drawBottomBar(canvas)
    }

    private fun drawTabs(canvas: Canvas) {
        val tabW = width / EmojiData.categories.size.toFloat()
        EmojiData.categories.forEachIndexed { i, cat ->
            val left = i * tabW
            if (i == selectedCat) {
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(left + 2f, 2f, left + tabW - 2f, tabH - 2f), 8f, 8f, paint)
            }
            canvas.drawText(cat.icon, left + tabW / 2f, tabH * 0.72f, tabPaint)
        }
        canvas.drawLine(0f, tabH, width.toFloat(), tabH, dividerPaint)
    }

    private fun drawGrid(canvas: Canvas) {
        val emojis = currentEmojis()
        val save = canvas.save()
        canvas.clipRect(0f, gridTop, width.toFloat(), gridBottom)
        canvas.translate(0f, gridTop - scrollY)

        emojis.forEachIndexed { i, emoji ->
            val col = i % cols
            val row = i / cols
            val cx = col * cellSize + cellSize / 2f
            val cy = row * cellSize + cellSize / 2f
            canvas.drawText(emoji, cx, cy + 14f, textPaint)
        }

        if (emojis.isEmpty()) {
            canvas.drawText("Sin recientes", width / 2f, gridH / 2f, labelPaint)
        }

        canvas.restoreToCount(save)
    }

    private fun drawBottomBar(canvas: Canvas) {
        val top = gridBottom
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), bottomBarPaint)
        canvas.drawLine(0f, top, width.toFloat(), top, dividerPaint)

        val margin = 6f
        val abcW = width * 0.22f
        val backW = width * 0.18f
        val enterW = width * 0.20f

        // ABC
        paint.color = Color.parseColor("#ADB1BC")
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(margin, top + margin, abcW - margin, height - margin), 8f, 8f, paint)
        canvas.drawText("ABC", abcW / 2f, top + (height - top) / 2f + 9f, smallLabelPaint)

        // espacio
        paint.color = Color.parseColor("#F4F4F6")
        val spaceLeft = abcW
        val spaceRight = width - backW - enterW
        canvas.drawRoundRect(RectF(spaceLeft + margin, top + margin, spaceRight - margin, height - margin), 8f, 8f, paint)

        // borrar
        paint.color = Color.parseColor("#ADB1BC")
        val backLeft = width - backW - enterW
        canvas.drawRoundRect(RectF(backLeft + margin, top + margin, width - enterW - margin, height - margin), 8f, 8f, paint)
        canvas.drawText("⌫", backLeft + backW / 2f, top + (height - top) / 2f + 15f, textPaint)

        // enter
        paint.color = Color.parseColor("#1668E3")
        canvas.drawRoundRect(RectF(width - enterW + margin, top + margin, width.toFloat() - margin, height - margin), 8f, 8f, paint)
        canvas.drawText("↵", width - enterW / 2f, top + (height - top) / 2f + 15f, whiteTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                lastTouchY = event.y
                touchDownScrollY = scrollY
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = touchDownY - event.y
                if (!isDragging && abs(dy) > 8f) isDragging = true
                if (isDragging) {
                    scrollY = (touchDownScrollY + dy).coerceIn(0f, maxScroll())
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) handleTap(event.x, event.y)
                isDragging = false
            }
        }
        return true
    }

    private fun handleTap(x: Float, y: Float) {
        // Tab?
        if (y < tabH) {
            val tabW = width / EmojiData.categories.size.toFloat()
            val i = (x / tabW).toInt().coerceIn(0, EmojiData.categories.size - 1)
            selectedCat = i
            scrollY = 0f
            invalidate()
            return
        }
        // Bottom bar?
        val top = gridBottom
        if (y > top) {
            val abcW = width * 0.22f
            val backW = width * 0.18f
            val enterW = width * 0.20f
            when {
                x < abcW -> onBackToLetters?.invoke()
                x > width - enterW - backW && x < width - enterW -> onBackspace?.invoke()
                // espacio — no hace nada especial aquí; lo maneja el servicio
            }
            return
        }
        // Emoji grid
        if (y < gridTop) return
        val adjustedY = y - gridTop + scrollY
        val col = (x / cellSize).toInt().coerceIn(0, cols - 1)
        val row = (adjustedY / cellSize).toInt()
        val idx = row * cols + col
        val emojis = currentEmojis()
        if (idx in emojis.indices) {
            val emoji = emojis[idx]
            // Agregar a recientes
            EmojiData.recent.remove(emoji)
            EmojiData.recent.add(0, emoji)
            if (EmojiData.recent.size > 32) EmojiData.recent.removeLast()
            onEmojiSelected?.invoke(emoji)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KeyboardView principal
// ─────────────────────────────────────────────────────────────────────────────

class KeyboardView(context: Context) : ViewGroup(context) {

    // Callbacks
    var onLetterTyped: ((Char) -> Unit)? = null
    var onCharTyped: ((Char) -> Unit)? = null
    var onEmojiTyped: ((String) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onShiftToggle: ((Boolean) -> Unit)? = null
    /** Para el servicio: sugerencias, letras del path, longitud en px, duración ms, índices de pausa */
    var onSwipeSuggestions: ((List<String>, List<Char>, Float, Long, List<Int>) -> Unit)? = null

    private val keysCanvas = KeysCanvasView(context)
    private val emojiPanel = EmojiPanelView(context)

    private var mode = KeyboardMode.LETTERS
        set(v) {
            field = v
            emojiPanel.visibility = if (v == KeyboardMode.EMOJI) VISIBLE else GONE
            keysCanvas.visibility = if (v == KeyboardMode.EMOJI) GONE else VISIBLE
        }

    init {
        // Permite que KeysCanvasView dibuje el popup de acentos fuera de sus propios límites
        // (necesario cuando la tecla está en row1 y el popup aparece por encima del teclado)
        clipChildren = false
        clipToPadding = false
        addView(keysCanvas)
        addView(emojiPanel)

        emojiPanel.onEmojiSelected = { emoji -> onEmojiTyped?.invoke(emoji) }
        emojiPanel.onBackspace = { onBackspace?.invoke() }
        emojiPanel.onBackToLetters = {
            mode = KeyboardMode.LETTERS
            keysCanvas.syncMode(mode)
        }

        keysCanvas.onModeChange = { newMode ->
            mode = newMode
            keysCanvas.syncMode(newMode)
        }
        keysCanvas.onLetterTyped = { onLetterTyped?.invoke(it) }
        keysCanvas.onCharTyped = { onCharTyped?.invoke(it) }
        keysCanvas.onBackspace = { onBackspace?.invoke() }
        keysCanvas.onSpace = { onSpace?.invoke() }
        keysCanvas.onEnter = { onEnter?.invoke() }
        keysCanvas.onShiftToggle = { onShiftToggle?.invoke(it) }
        keysCanvas.onSwipeSuggestions = { s, p, l, d, pi -> onSwipeSuggestions?.invoke(s, p, l, d, pi) }

        mode = KeyboardMode.LETTERS
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        keysCanvas.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
        emojiPanel.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        keysCanvas.layout(0, 0, r - l, b - t)
        emojiPanel.layout(0, 0, r - l, b - t)
    }

    /** Activa mayúscula de inicio de oración (tras punto, enter o inicio de campo). */
    fun resetToSentenceCase() = keysCanvas.resetToSentenceCase()

    /**
     * Aplica el estado de shift actual a [word] y resetea SINGLE → OFF igual que al clickear.
     * El servicio llama esto antes de commitear la palabra predicha por swipe.
     */
    fun applyShiftToWord(word: String): String = keysCanvas.applyShiftToWord(word)
}

// ─────────────────────────────────────────────────────────────────────────────
// Vista con Canvas que dibuja las teclas (LETTERS / NUMBERS / SYMBOLS)
// ─────────────────────────────────────────────────────────────────────────────

class KeysCanvasView(context: Context) : View(context) {

    // Callbacks
    var onModeChange: ((KeyboardMode) -> Unit)? = null
    var onLetterTyped: ((Char) -> Unit)? = null
    var onCharTyped: ((Char) -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onShiftToggle: ((Boolean) -> Unit)? = null
    // suggestions, pathChars, totalLenPx, durationMs, pauseIndices
    var onSwipeSuggestions: ((List<String>, List<Char>, Float, Long, List<Int>) -> Unit)? = null

    private val keys = mutableListOf<KeyInfo>()
    private var shiftState = ShiftState.SINGLE   // arranca en mayúscula inicial
    private val isUpperCase: Boolean get() = shiftState != ShiftState.OFF
    private var mode = KeyboardMode.LETTERS

    // Swipe state
    private var isSwiping = false
    private val swipePath = mutableListOf<Char>()   // letras por las que pasó el dedo
    private val swipePoints = mutableListOf<Pair<Float, Float>>()
    private val swipePointTimesMs = mutableListOf<Long>()  // timestamp de cada punto del path
    private var swipeStartKey: KeyInfo? = null
    private var lastSwipeKey: KeyInfo? = null
    private var swipeStartTimeMs = 0L
    // Umbral de pausa: si el dedo se detiene más de este tiempo sin levantar, se considera pausa
    private val swipePauseThresholdMs = 180L

    // Paints
    private val keyPaint = Paint().apply { color = Color.parseColor("#F4F4F6"); style = Paint.Style.FILL }
    private val specialKeyPaint = Paint().apply { color = Color.parseColor("#ADB1BC"); style = Paint.Style.FILL }
    private val shiftSinglePaint = Paint().apply { color = Color.parseColor("#FFFFFF"); style = Paint.Style.FILL }
    private val shiftLockPaint = Paint().apply { color = Color.parseColor("#1C1C1E"); style = Paint.Style.FILL }
    private val lockIndicatorPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val enterPaint = Paint().apply { color = Color.parseColor("#1668E3"); style = Paint.Style.FILL }
    private val pressedPaint = Paint().apply { color = Color.parseColor("#C8CAD2"); style = Paint.Style.FILL }
    private val swipePathPaint = Paint().apply {
        color = Color.parseColor("#4488DD")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val swipeKeyHighlightPaint = Paint().apply {
        color = Color.parseColor("#BBDDFF")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply { color = Color.parseColor("#1C1C1E"); textSize = 44f; textAlign = Paint.Align.CENTER }
    private val symbolTextPaint = Paint().apply { color = Color.parseColor("#1C1C1E"); textSize = 38f; textAlign = Paint.Align.CENTER }
    private val numberTextPaint = Paint().apply { color = Color.parseColor("#6E6E73"); textSize = 22f; textAlign = Paint.Align.CENTER }
    private val whiteTextPaint = Paint().apply { color = Color.WHITE; textSize = 44f; textAlign = Paint.Align.CENTER }
    private val labelTextPaint = Paint().apply { color = Color.parseColor("#1C1C1E"); textSize = 28f; textAlign = Paint.Align.CENTER }
    private val smallLabelTextPaint = Paint().apply { color = Color.parseColor("#1C1C1E"); textSize = 22f; textAlign = Paint.Align.CENTER }

    private var pressedKey: KeyInfo? = null

    // Long press
    private val actionHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private var longPressRunnable: Runnable? = null
    // Runnable exclusivo para acentos — no se cancela por micro-movimiento de swipe
    private var accentLongPressRunnable: Runnable? = null

    // Borrado continuo (backspace mantenido)
    private var backspaceRepeatRunnable: Runnable? = null
    private val backspaceInitialDelayMs = 400L   // esperar antes de empezar a repetir
    private val backspaceRepeatIntervalMs = 50L  // intervalo entre borrados

    // Popup de acentos
    private val accentMap = mapOf(
        'a' to listOf("á", "à", "â", "ä", "ã", "å", "æ"),
        'e' to listOf("é", "è", "ê", "ë"),
        'i' to listOf("í", "ì", "î", "ï"),
        'o' to listOf("ó", "ò", "ô", "ö", "õ", "ø"),
        'u' to listOf("ú", "ù", "û", "ü"),
        'n' to listOf("ñ"),
        'c' to listOf("ç"),
        's' to listOf("ß")
    )
    private var accentPopupKey: KeyInfo? = null   // tecla sobre la que se muestra el popup
    private var accentOptions: List<String> = emptyList()
    private var accentRects: List<RectF> = emptyList()
    private var showingAccentPopup = false

    private val accentKeyPaint = Paint().apply { color = Color.parseColor("#FFFFFF"); style = Paint.Style.FILL }
    private val accentSelectedPaint = Paint().apply { color = Color.parseColor("#1668E3"); style = Paint.Style.FILL }
    private val accentTextPaint = Paint().apply { color = Color.parseColor("#1C1C1E"); textSize = 40f; textAlign = Paint.Align.CENTER }
    private val accentSelectedTextPaint = Paint().apply { color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER }
    private val accentBgPaint = Paint().apply { color = Color.parseColor("#E8E8EE"); style = Paint.Style.FILL }
    private var hoveredAccentIdx = -1

    // Doble-clic shift
    private var lastShiftTapTime = 0L
    private var pendingShiftRunnable: Runnable? = null
    private val doubleTapThresholdMs = 280L

    // ── Swipe: umbral mínimo de movimiento antes de considerar que es swipe
    private val swipeThresholdPx = 18f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchMoved = false

    fun syncMode(newMode: KeyboardMode) {
        mode = newMode
        buildKeyLayout(width, height)
        invalidate()
    }

    /** Activa mayúscula de primera letra de oración (se llama tras punto, enter o inicio de campo). */
    fun resetToSentenceCase() {
        if (shiftState == ShiftState.OFF) {
            shiftState = ShiftState.SINGLE
            onShiftToggle?.invoke(true)
            invalidate()
        }
    }

    /**
     * Aplica el estado de shift actual a [word] (mayúscula inicial si SINGLE o CAPS_LOCK,
     * todo mayúscula si CAPS_LOCK, primera mayúscula si SINGLE) y luego resetea SINGLE → OFF.
     * Equivalente a lo que hace commitSwipeWord internamente, pero accesible desde el servicio.
     */
    fun applyShiftToWord(word: String): String {
        val out = when (shiftState) {
            ShiftState.CAPS_LOCK -> word.uppercase()
            ShiftState.SINGLE   -> word.replaceFirstChar { it.uppercaseChar() }
            ShiftState.OFF      -> word
        }
        if (shiftState == ShiftState.SINGLE) {
            shiftState = ShiftState.OFF
            onShiftToggle?.invoke(false)
            invalidate()
        }
        return out
    }

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
            KeyboardMode.CALCULATOR -> buildCalculatorLayout(width, height)
            KeyboardMode.EMOJI -> { /* handled by EmojiPanelView */ }
        }
    }

    private fun buildLettersLayout(width: Int, height: Int) {
        val rowHeight = height / 4f

        val row1Width = width / SpanishLayout.row1.size.toFloat()
        SpanishLayout.row1.forEachIndexed { i, letter ->
            val left = i * row1Width
            keys.add(KeyInfo(KeyType.Letter(letter, SpanishLayout.row1Numbers[i]),
                RectF(left, 0f, left + row1Width, rowHeight)))
        }

        val row2Width = width / SpanishLayout.row2.size.toFloat()
        SpanishLayout.row2.forEachIndexed { i, letter ->
            val left = i * row2Width
            keys.add(KeyInfo(KeyType.Letter(letter),
                RectF(left, rowHeight, left + row2Width, rowHeight * 2)))
        }

        val middleCount = SpanishLayout.row3.size
        val sideKeyWidth = width * 0.13f
        val middleWidth = (width - sideKeyWidth * 2) / middleCount.toFloat()

        keys.add(KeyInfo(KeyType.Shift, RectF(0f, rowHeight * 2, sideKeyWidth, rowHeight * 3)))
        SpanishLayout.row3.forEachIndexed { i, letter ->
            val left = sideKeyWidth + i * middleWidth
            keys.add(KeyInfo(KeyType.Letter(letter),
                RectF(left, rowHeight * 2, left + middleWidth, rowHeight * 3)))
        }
        keys.add(KeyInfo(KeyType.Backspace,
            RectF(width - sideKeyWidth, rowHeight * 2, width.toFloat(), rowHeight * 3)))

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

    /**
     * Layout tipo calculadora para el modo NUMBERS (activado con el botón "?123").
     * Columna izquierda gris con +,-,*,/ (4 celdas iguales que cubren la altura de las
     * 3 filas numéricas). Centro: dígitos 1-9 en 3 columnas blancas. Columna derecha
     * gris con %, espacio compacto (⎵) y borrar. Fila inferior: ABC, coma, !?#, 0, =, ., Enter.
     */
    private fun buildCalculatorLayout(width: Int, height: Int) {
        val rowHeight = height / 4f
        val gridHeight = rowHeight * 3f   // alto total de las filas 1-9

        val sideColWidth = width * 0.15f
        val midColWidth = (width - sideColWidth * 2f) / 3f

        // Columna izquierda: operadores (4 celdas iguales dentro del alto de las 3 filas numéricas)
        val operators = charArrayOf('+', '-', '*', '/')
        val opCellHeight = gridHeight / operators.size
        operators.forEachIndexed { i, op ->
            val top = i * opCellHeight
            keys.add(KeyInfo(KeyType.Operator(op), RectF(0f, top, sideColWidth, top + opCellHeight)))
        }

        // Filas 1-3: dígitos 1-9 en las 3 columnas centrales
        val digitRows = listOf(
            charArrayOf('1', '2', '3'),
            charArrayOf('4', '5', '6'),
            charArrayOf('7', '8', '9')
        )
        digitRows.forEachIndexed { r, rowDigits ->
            val top = r * rowHeight
            rowDigits.forEachIndexed { c, d ->
                val left = sideColWidth + c * midColWidth
                keys.add(KeyInfo(KeyType.Symbol(d), RectF(left, top, left + midColWidth, top + rowHeight)))
            }
        }

        // Columna derecha: % / espacio compacto / borrar
        keys.add(KeyInfo(KeyType.Percent,
            RectF(width - sideColWidth, 0f, width.toFloat(), rowHeight)))
        keys.add(KeyInfo(KeyType.SpaceCompact,
            RectF(width - sideColWidth, rowHeight, width.toFloat(), rowHeight * 2)))
        keys.add(KeyInfo(KeyType.Backspace,
            RectF(width - sideColWidth, rowHeight * 2, width.toFloat(), rowHeight * 3)))

        // Fila inferior: ABC | , | !?# | 0 | = | . | Enter
        val bottomTop = rowHeight * 3
        val abcWidth = sideColWidth          // alineado bajo la columna de operadores
        val commaWidth = width * 0.09f
        val hashWidth = width * 0.12f
        val zeroWidth = midColWidth          // alineado bajo la columna del 2, 5, 8
        val equalsWidth = width * 0.13f
        val periodWidth = width * 0.09f

        var cursor = 0f
        keys.add(KeyInfo(KeyType.BackToLetters, RectF(cursor, bottomTop, cursor + abcWidth, height.toFloat())))
        cursor += abcWidth
        keys.add(KeyInfo(KeyType.Symbol(','), RectF(cursor, bottomTop, cursor + commaWidth, height.toFloat())))
        cursor += commaWidth
        keys.add(KeyInfo(KeyType.SymbolsPageToggle, RectF(cursor, bottomTop, cursor + hashWidth, height.toFloat())))
        cursor += hashWidth
        keys.add(KeyInfo(KeyType.Symbol('0'), RectF(cursor, bottomTop, cursor + zeroWidth, height.toFloat())))
        cursor += zeroWidth
        keys.add(KeyInfo(KeyType.Equals, RectF(cursor, bottomTop, cursor + equalsWidth, height.toFloat())))
        cursor += equalsWidth
        keys.add(KeyInfo(KeyType.Symbol('.'), RectF(cursor, bottomTop, cursor + periodWidth, height.toFloat())))
        cursor += periodWidth
        // Enter toma el resto — queda alineado bajo la columna del % / borrar
        keys.add(KeyInfo(KeyType.Enter, RectF(cursor, bottomTop, width.toFloat(), height.toFloat())))
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

        val middleCount = row3Chars.size
        val sideKeyWidth = width * 0.13f
        val middleWidth = (width - sideKeyWidth * 2) / middleCount.toFloat()

        keys.add(KeyInfo(KeyType.ModeSwitchLeft, RectF(0f, rowHeight * 2, sideKeyWidth, rowHeight * 3)))
        row3Chars.forEachIndexed { i, c ->
            val left = sideKeyWidth + i * middleWidth
            keys.add(KeyInfo(KeyType.Symbol(c), RectF(left, rowHeight * 2, left + middleWidth, rowHeight * 3)))
        }
        keys.add(KeyInfo(KeyType.Backspace,
            RectF(width - sideKeyWidth, rowHeight * 2, width.toFloat(), rowHeight * 3)))

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

    // ─────────────────────────────────────────────────────────────────────────
    // Draw
    // ─────────────────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val margin = 4f

        keys.forEach { key ->
            val r = RectF(key.rect.left + margin, key.rect.top + margin,
                key.rect.right - margin, key.rect.bottom - margin)

            val paint = when {
                isSwiping && key.type is KeyType.Letter && swipePath.contains((key.type).char) ->
                    swipeKeyHighlightPaint
                key == pressedKey && key.type !is KeyType.Shift -> pressedPaint
                key.type is KeyType.Enter -> enterPaint
                key.type is KeyType.Shift -> when (shiftState) {
                    ShiftState.OFF -> specialKeyPaint
                    ShiftState.SINGLE -> shiftSinglePaint
                    ShiftState.CAPS_LOCK -> shiftLockPaint
                }
                key.type is KeyType.Backspace || key.type is KeyType.Numbers123 ||
                        key.type is KeyType.BackToLetters || key.type is KeyType.ModeSwitchLeft ||
                        key.type is KeyType.NumSymToggle || key.type is KeyType.SpaceCompact ||
                        key.type is KeyType.SymbolsPageToggle -> specialKeyPaint
                key.type is KeyType.Operator || key.type is KeyType.Percent -> specialKeyPaint
                key.type is KeyType.Symbol &&
                        (key.type as KeyType.Symbol).char in charArrayOf('+', '-', '*', '/', '%', ',', '.') ->
                    specialKeyPaint
                else -> keyPaint
            }
            canvas.drawRoundRect(r, 8f, 8f, paint)
            drawKeyContent(canvas, key)
        }

        // Popup de acentos
        if (showingAccentPopup && accentRects.isNotEmpty()) {
            // Fondo del popup
            val first = accentRects.first()
            val last = accentRects.last()
            val bgRect = RectF(first.left - 6f, first.top - 6f, last.right + 6f, last.bottom + 6f)
            canvas.drawRoundRect(bgRect, 12f, 12f, accentBgPaint)
            accentRects.forEachIndexed { i, r ->
                val bg = if (i == hoveredAccentIdx) accentSelectedPaint else accentKeyPaint
                canvas.drawRoundRect(r, 8f, 8f, bg)
                val tp = if (i == hoveredAccentIdx) accentSelectedTextPaint else accentTextPaint
                canvas.drawText(accentOptions[i], r.centerX(), r.centerY() + 14f, tp)
            }
        }

        // Trazar el camino del swipe encima de las teclas
        if (isSwiping && swipePoints.size >= 2) {
            val path = android.graphics.Path()
            path.moveTo(swipePoints[0].first, swipePoints[0].second)
            swipePoints.drop(1).forEach { (x, y) -> path.lineTo(x, y) }
            canvas.drawPath(path, swipePathPaint)
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
            KeyType.Shift -> drawShiftIcon(canvas, key)

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
            KeyType.SpaceCompact -> canvas.drawText("⎵", cx, cy + 15f, textPaint)
            KeyType.SymbolsPageToggle -> canvas.drawText("!?#", cx, cy + 10f, labelTextPaint)
            KeyType.Period -> canvas.drawText(".", cx, cy + 15f, textPaint)
            is KeyType.Operator -> canvas.drawText(type.char.toString(), cx, cy + 15f, textPaint)
            KeyType.Percent -> canvas.drawText("%", cx, cy + 15f, textPaint)
            KeyType.Equals -> canvas.drawText("=", cx, cy + 15f, textPaint)
            KeyType.Enter -> drawEnterIcon(canvas, key)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Touch / Swipe
    // ─────────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        touchDownX = event.x
        touchDownY = event.y
        touchMoved = false
        isSwiping = false
        longPressTriggered = false
        swipePath.clear()
        swipePoints.clear()
        swipePointTimesMs.clear()
        if (showingAccentPopup) { hideAccentPopup(); invalidate() }

        val key = keys.firstOrNull { it.rect.contains(event.x, event.y) }
        pressedKey = key
        swipeStartKey = key
        lastSwipeKey = key

        // Registrar punto inicial
        key?.let {
            if (it.type is KeyType.Letter) {
                swipePath.add(it.type.char)
                swipePoints.add(event.x to event.y)
                swipeStartTimeMs = System.currentTimeMillis()
            }
        }

        invalidate()

        // Borrado continuo al mantener presionado Backspace
        if (key?.type is KeyType.Backspace) {
            val repeatRunnable = object : Runnable {
                override fun run() {
                    onBackspace?.invoke()
                    actionHandler.postDelayed(this, backspaceRepeatIntervalMs)
                }
            }
            backspaceRepeatRunnable = repeatRunnable
            actionHandler.postDelayed(repeatRunnable, backspaceInitialDelayMs)
        }

        // Long-press para CommaEmoji -> EMOJI
        if (key?.type is KeyType.CommaEmoji) {
            longPressTriggered = false
            val runnable = Runnable {
                longPressTriggered = true
                onModeChange?.invoke(KeyboardMode.EMOJI)
            }
            longPressRunnable = runnable
            actionHandler.postDelayed(runnable, 350L)
        }

        // Long-press para: acentos + número real de la tecla (renglón qwertyuiop),
        // y exponente de los dígitos cuando estamos en modo NUMBERS.
        // Usa runnable propio para que micro-movimientos de swipe no lo cancelen.
        key?.let { k ->
            val variants = longPressVariantsFor(k)
            if (variants.isNotEmpty()) {
                accentLongPressRunnable?.let { actionHandler.removeCallbacks(it) }
                val runnable = Runnable {
                    accentLongPressRunnable = null
                    longPressTriggered = true
                    isSwiping = false
                    swipePath.clear()
                    swipePoints.clear()
                    invalidate()
                    showAccentPopup(k, variants)
                }
                accentLongPressRunnable = runnable
                actionHandler.postDelayed(runnable, 380L)
            }
        }
    }

    /**
     * Variantes que se muestran al mantener presionada una tecla:
     * - Letras: sus acentos definidos en accentMap (si tiene) + su número real
     *   (el mismo que ya se ve pequeño en la esquina, para TODO el renglón qwertyuiop,
     *   tengan o no acentos disponibles).
     * - Dígitos en modo NUMBERS: su versión en superíndice/exponente (p. ej. "2" -> "²").
     */
    private fun longPressVariantsFor(key: KeyInfo): List<String> {
        return when (val type = key.type) {
            is KeyType.Letter -> {
                val baseAccents = accentMap[type.char] ?: emptyList()
                val numberVariant = type.number?.let { listOf(it.toString()) } ?: emptyList()
                baseAccents + numberVariant
            }
            is KeyType.Symbol -> {
                if ((mode == KeyboardMode.NUMBERS || mode == KeyboardMode.CALCULATOR) && type.char.isDigit()) {
                    listOf(superscriptDigit(type.char).toString())
                } else emptyList()
            }
            else -> emptyList()
        }
    }

    private fun superscriptDigit(c: Char): Char = when (c) {
        '0' -> '⁰'; '1' -> '¹'; '2' -> '²'; '3' -> '³'; '4' -> '⁴'
        '5' -> '⁵'; '6' -> '⁶'; '7' -> '⁷'; '8' -> '⁸'; '9' -> '⁹'
        else -> c
    }

    private fun handleMove(event: MotionEvent) {
        val dx = event.x - touchDownX
        val dy = event.y - touchDownY
        val dist = sqrt(dx * dx + dy * dy)

        if (!touchMoved && dist > swipeThresholdPx) {
            // Solo arrancamos swipe si el toque inicial fue en una tecla de letra
            if (swipeStartKey?.type is KeyType.Letter) {
                touchMoved = true
                isSwiping = true
                // Cancelar AMBOS long-press al detectar swipe
                longPressRunnable?.let { actionHandler.removeCallbacks(it) }
                accentLongPressRunnable?.let { actionHandler.removeCallbacks(it) }
                accentLongPressRunnable = null
            }
        }

        // Si hay popup de acentos, rastrear hover
        if (showingAccentPopup) {
            hoveredAccentIdx = accentRects.indexOfFirst { it.contains(event.x, event.y) }
            invalidate()
            return
        }

        if (isSwiping) {
            swipePoints.add(event.x to event.y)
            swipePointTimesMs.add(System.currentTimeMillis())

            val key = keys.firstOrNull { it.rect.contains(event.x, event.y) }
            if (key != null && key != lastSwipeKey && key.type is KeyType.Letter) {
                val ch = key.type.char
                // Evitar duplicados consecutivos (el dedo tiembla)
                if (swipePath.isEmpty() || swipePath.last() != ch) {
                    swipePath.add(ch)
                }
                lastSwipeKey = key
            }
            invalidate()
        }
    }

    private fun handleUp(event: MotionEvent) {
        longPressRunnable?.let { actionHandler.removeCallbacks(it) }
        accentLongPressRunnable?.let { actionHandler.removeCallbacks(it) }
        accentLongPressRunnable = null
        backspaceRepeatRunnable?.let { actionHandler.removeCallbacks(it) }
        backspaceRepeatRunnable = null

        // Selección de acento
        if (showingAccentPopup) {
            val idx = accentRects.indexOfFirst { it.contains(event.x, event.y) }
            if (idx >= 0) {
                val accent = if (isUpperCase) accentOptions[idx].uppercase() else accentOptions[idx]
                onLetterTyped?.invoke(accent[0])
                if (accent.length > 1) accent.drop(1).forEach { onLetterTyped?.invoke(it) }
                if (shiftState == ShiftState.SINGLE) {
                    shiftState = ShiftState.OFF
                    onShiftToggle?.invoke(false)
                }
            }
            hideAccentPopup()
            pressedKey = null
            invalidate()
            return
        }

        if (isSwiping && swipePath.size >= 2) {
            // Calcular métricas del gesto
            val durationMs = System.currentTimeMillis() - swipeStartTimeMs
            var totalLenPx = 0f
            for (i in 1 until swipePoints.size) {
                val dx = swipePoints[i].first - swipePoints[i-1].first
                val dy = swipePoints[i].second - swipePoints[i-1].second
                totalLenPx += sqrt(dx*dx + dy*dy)
            }
            val pathSnapshot = swipePath.toList()

            // Detectar índices de pausa: posiciones en swipePointTimesMs donde el delta
            // entre puntos consecutivos supera el umbral. Una pausa indica separación de palabras.
            val pauseIndices = mutableListOf<Int>()
            for (i in 1 until swipePointTimesMs.size) {
                if (swipePointTimesMs[i] - swipePointTimesMs[i - 1] >= swipePauseThresholdMs) {
                    pauseIndices.add(i)
                }
            }

            // Delegar predicción al servicio vía callback — el servicio consulta PersonalDictionary
            // que tiene las trayectorias SWIPE precalculadas y suggestForSwipe() completo.
            // SwipePredictor queda inactivo (diccionario vacío por diseño).
            onSwipeSuggestions?.invoke(emptyList(), pathSnapshot, totalLenPx, durationMs, pauseIndices)
            isSwiping = false
            swipePath.clear()
            swipePoints.clear()
            swipePointTimesMs.clear()
            pressedKey = null
            invalidate()
            return
        }

        // Toque normal (sin swipe)
        isSwiping = false
        swipePath.clear()
        swipePoints.clear()
        swipePointTimesMs.clear()

        pressedKey?.let { key ->
            when (val type = key.type) {
                is KeyType.Letter -> {
                    if (!longPressTriggered) {
                        val letter = if (isUpperCase) type.char.uppercaseChar() else type.char
                        onLetterTyped?.invoke(letter)
                        if (shiftState == ShiftState.SINGLE) {
                            shiftState = ShiftState.OFF
                            onShiftToggle?.invoke(false)
                        }
                    }
                }
                is KeyType.Symbol -> onCharTyped?.invoke(type.char)
                KeyType.Shift -> handleShiftTap()
                KeyType.Backspace -> onBackspace?.invoke()
                KeyType.Numbers123 -> onModeChange?.invoke(KeyboardMode.NUMBERS)
                KeyType.BackToLetters -> onModeChange?.invoke(KeyboardMode.LETTERS)
                KeyType.ModeSwitchLeft -> {
                    val next = if (mode == KeyboardMode.NUMBERS) KeyboardMode.SYMBOLS else KeyboardMode.NUMBERS
                    onModeChange?.invoke(next)
                }
                KeyType.NumSymToggle -> onModeChange?.invoke(KeyboardMode.CALCULATOR)
                KeyType.CommaEmoji -> {
                    if (!longPressTriggered) onCharTyped?.invoke(',') else Unit
                }
                KeyType.LessThan -> onCharTyped?.invoke('<')
                KeyType.GreaterThan -> onCharTyped?.invoke('>')
                KeyType.Space -> onSpace?.invoke()
                KeyType.SpaceCompact -> onSpace?.invoke()
                KeyType.SymbolsPageToggle -> onModeChange?.invoke(KeyboardMode.SYMBOLS)
                KeyType.Period -> onCharTyped?.invoke('.')
                is KeyType.Operator -> onCharTyped?.invoke(type.char)
                KeyType.Percent -> onCharTyped?.invoke('%')
                KeyType.Equals -> onCharTyped?.invoke('=')
                KeyType.Enter -> onEnter?.invoke()
            }
            Unit
        }

        pressedKey = null
        invalidate()
    }

    private fun handleCancel() {
        longPressRunnable?.let { actionHandler.removeCallbacks(it) }
        accentLongPressRunnable?.let { actionHandler.removeCallbacks(it) }
        accentLongPressRunnable = null
        backspaceRepeatRunnable?.let { actionHandler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
        isSwiping = false
        swipePath.clear()
        swipePoints.clear()
        swipePointTimesMs.clear()
        pressedKey = null
        hideAccentPopup()
        invalidate()
    }

    private fun showAccentPopup(key: KeyInfo, variants: List<String>) {
    accentPopupKey = key
    accentOptions = variants

    val density = resources.displayMetrics.density
    val cellW = key.rect.width().coerceAtLeast(80f)

    // Separación entre la tecla y el popup, y margen de seguridad arriba/abajo del popup.
    val gap = 8f

    // Para teclas del renglón superior (qwertyuiop) no hay espacio libre por encima
    // dentro de esta misma vista. Usamos como zona de desborde permitida la altura de
    // la fila de botones superior (Copiar/Pegar/Teclados/Diccionario, 48dp), sobre la
    // que esta vista puede dibujar porque los contenedores padres tienen
    // clipChildren = false.
    val overflowAllowancePx = 48f * density
    val availableAbove = key.rect.top + overflowAllowancePx

    val desiredCellH = key.rect.height().coerceAtLeast(60f)
    val cellH = desiredCellH.coerceAtMost((availableAbove - gap * 2).coerceAtLeast(32f))

    var popupY = key.rect.top - gap - cellH
    popupY = popupY.coerceAtLeast(-overflowAllowancePx + gap)

    var startX = key.rect.centerX() - (variants.size * cellW) / 2f
    startX = startX.coerceIn(4f, (width - variants.size * cellW - 4f).coerceAtLeast(4f))

    accentRects = variants.mapIndexed { i, _ ->
        RectF(startX + i * cellW, popupY, startX + (i + 1) * cellW, popupY + cellH)
    }

    hoveredAccentIdx = -1
    showingAccentPopup = true
    invalidate()
}

    private fun hideAccentPopup() {
        showingAccentPopup = false
        accentPopupKey = null
        accentOptions = emptyList()
        accentRects = emptyList()
        hoveredAccentIdx = -1
    }

    private fun commitSwipeWord(word: String) {
        val out = if (isUpperCase) word.replaceFirstChar { it.uppercaseChar() } else word
        // Escribir letra a letra para respetar el InputConnection
        out.forEach { c -> onLetterTyped?.invoke(c) }
        // Auto-espacio tras swipe
        onSpace?.invoke()
        if (shiftState == ShiftState.SINGLE) {
            shiftState = ShiftState.OFF
            onShiftToggle?.invoke(false)
        }
    }

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

    // ─────────────────────────────────────────────────────────────────────────
    // Iconos dibujados con Path
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Shift: flecha hueca (OFF) / rellena oscura (SINGLE) / rellena blanca + barra (CAPS_LOCK).
     * Dibujada con Path para un look limpio independiente del soporte de glifos del dispositivo.
     *
     * Geometría: pentágono-flecha apuntando arriba + tronco cuadrado.
     *   - Ancho total de la flecha: W
     *   - Altura total: H  (60% flecha, 40% tronco)
     *   - El tronco ocupa el tercio central del ancho.
     */
    private fun drawShiftIcon(canvas: Canvas, key: KeyInfo) {
        val cx = key.rect.centerX()
        // Subir el icono un poco para dejar margen visual abajo
        val cy = key.rect.centerY() - key.rect.height() * 0.04f

        val W = key.rect.width() * 0.44f   // mitad del ancho de la flecha
        val H = key.rect.height() * 0.52f  // altura total del icono
        val arrowH = H * 0.60f             // porción triangular
        val trunkH = H * 0.40f            // porción rectangular (tronco)
        val trunkW = W * 0.46f             // semiancho del tronco

        val top = cy - H / 2f
        val mid = top + arrowH             // unión flecha-tronco
        val bot = top + H

        // Path de la flecha completa (flecha + tronco unidos)
        val path = android.graphics.Path().apply {
            moveTo(cx, top)                      // punta superior
            lineTo(cx + W, mid)                  // extremo derecho del triángulo
            lineTo(cx + trunkW, mid)             // esquina interior derecha
            lineTo(cx + trunkW, bot)             // base derecha del tronco
            lineTo(cx - trunkW, bot)             // base izquierda del tronco
            lineTo(cx - trunkW, mid)             // esquina interior izquierda
            lineTo(cx - W, mid)                  // extremo izquierdo del triángulo
            close()
        }

        when (shiftState) {
            ShiftState.OFF -> {
                // Contorno hueco
                val p = Paint().apply {
                    color = Color.parseColor("#1C1C1E")
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f
                    isAntiAlias = true
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(path, p)
            }
            ShiftState.SINGLE -> {
                // Rellena oscura
                val pFill = Paint().apply {
                    color = Color.parseColor("#1C1C1E")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawPath(path, pFill)
            }
            ShiftState.CAPS_LOCK -> {
                // Rellena blanca
                val pFill = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawPath(path, pFill)
                // Barra indicadora debajo del icono
                val barY = bot + 7f
                val barPaint = Paint().apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRoundRect(
                    RectF(cx - trunkW * 1.6f, barY, cx + trunkW * 1.6f, barY + 5f),
                    2f, 2f, barPaint
                )
            }
        }
    }

    /**
     * Enter: flecha L invertida (↵) dibujada con Path.
     * Línea horizontal a la derecha que baja y gira a la izquierda con punta de flecha.
     */
    private fun drawEnterIcon(canvas: Canvas, key: KeyInfo) {
        val cx = key.rect.centerX()
        val cy = key.rect.centerY()

        val W = key.rect.width() * 0.38f   // semiancho de la figura
        val H = key.rect.height() * 0.30f  // semiAltura

        // Punto de inicio (extremo derecho, arriba)
        val x0 = cx + W
        val y0 = cy - H * 0.30f
        // Esquina de giro
        val x1 = cx + W
        val y1 = cy + H * 0.30f
        // Extremo izquierdo de la línea horizontal
        val x2 = cx - W
        val y2 = y1
        // Punta de flecha
        val arrowSize = H * 0.70f

        val p = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        // Línea en L
        val path = android.graphics.Path().apply {
            moveTo(x0, y0)
            lineTo(x1, y1)
            lineTo(x2, y2)
        }
        canvas.drawPath(path, p)

        // Cabeza de flecha (triángulo relleno apuntando izquierda)
        val arrowPath = android.graphics.Path().apply {
            moveTo(x2, y2)                             // punta
            lineTo(x2 + arrowSize, y2 - arrowSize * 0.55f)  // esquina superior
            lineTo(x2 + arrowSize, y2 + arrowSize * 0.55f)  // esquina inferior
            close()
        }
        val arrowFill = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawPath(arrowPath, arrowFill)
    }
}
