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

    // Diccionario embebido de ~500 palabras frecuentes en español
    private val dictionary = """
        hola como estas bien mal todo nada mas menos algo poco mucho
        que quien cuando donde como porque para por con sin sobre bajo
        gracias perdón disculpa favor espera voy vamos viene viene
        casa trabajo escuela universidad ciudad país mundo vida tiempo
        día noche mañana tarde hora minuto segundo año mes semana
        agua comida dinero amor amigo familia padre madre hijo hija
        hermano hermana abuelo abuela primo prima esposo esposa novio
        novia persona gente hombre mujer niño niña chico chica
        bueno malo bonito feo grande pequeño alto bajo rápido lento
        nuevo viejo joven fuerte débil feliz triste cansado enfermo
        quiero puedo debo voy tengo hay hace fue ser estar tener
        hacer decir saber poder querer llegar salir volver quedar
        llama mensaje correo número teléfono internet redes foto
        música película serie libro película canción juego deporte
        fútbol básquet tenis golf natación correr caminar bailar
        comer beber dormir despertar trabajar estudiar aprender enseñar
        comprar vender pagar recibir enviar buscar encontrar perder
        ganar cambiar abrir cerrar entrar salir subir bajar
        sí no tal vez quizás siempre nunca antes después ahora
        aquí allá acá lejos cerca arriba abajo adentro afuera
        muy bastante demasiado suficiente poco nada todo algo
        uno dos tres cuatro cinco seis siete ocho nueve diez
        primero segundo tercero último siguiente anterior nuevo
        este esta estos estas ese esa esos esas aquel aquella
        yo tú él ella nosotros vosotros ellos ellas me te le nos
        mi tu su nuestro vuestro sus mis tus sus
        ciudad país pueblo barrio calle avenida parque plaza iglesia
        tienda banco hospital escuela parque playa montaña río lago
        carro moto bicicleta autobús metro tren avión barco taxi
        rojo azul verde amarillo negro blanco naranja rosa morado
        grande pequeño mediano largo corto ancho estrecho grueso
        caliente frío dulce salado amargo picante suave duro
        lunes martes miércoles jueves viernes sábado domingo
        enero febrero marzo abril mayo junio julio agosto
        septiembre octubre noviembre diciembre
    """.trimIndent().split(Regex("\\s+")).filter { it.length > 1 }

    /**
     * Dada una secuencia de letras que el dedo pasó, devuelve las N mejores palabras.
     */
    fun predict(swipedLetters: List<Char>, topN: Int = 3): List<String> {
        if (swipedLetters.size < 2) return emptyList()
        val swipeStr = swipedLetters.joinToString("")
        return dictionary
            .map { word -> word to swipeScore(swipeStr, word) }
            .sortedBy { it.second }
            .take(topN)
            .map { it.first }
    }

    /**
     * Puntuación: combina Levenshtein contra la clave de cada palabra
     * (primera + última + letras intermedias) con penalización por diferencia de longitud.
     */
    private fun swipeScore(swipe: String, word: String): Float {
        if (word.isEmpty()) return Float.MAX_VALUE
        // "clave" de la palabra: primera letra + intermedias sin duplicados + última letra
        val key = buildKey(word)
        val lev = levenshtein(swipe, key).toFloat()
        val lenDiff = abs(word.length - swipe.length) * 0.5f
        return lev + lenDiff
    }

    private fun buildKey(word: String): String {
        if (word.length <= 2) return word
        val mid = word.substring(1, word.length - 1)
            .toList()
            .distinct()
            .joinToString("")
        return "${word.first()}$mid${word.last()}"
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Key model
// ─────────────────────────────────────────────────────────────────────────────

enum class KeyboardMode { LETTERS, NUMBERS, SYMBOLS, EMOJI }
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
    object CommaEmoji : KeyType()
    object LessThan : KeyType()
    object GreaterThan : KeyType()
    object Space : KeyType()
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
    /** Para el servicio: lista de sugerencias swipe actualizadas */
    var onSwipeSuggestions: ((List<String>) -> Unit)? = null

    private val keysCanvas = KeysCanvasView(context)
    private val emojiPanel = EmojiPanelView(context)

    private var mode = KeyboardMode.LETTERS
        set(v) {
            field = v
            emojiPanel.visibility = if (v == KeyboardMode.EMOJI) VISIBLE else GONE
            keysCanvas.visibility = if (v == KeyboardMode.EMOJI) GONE else VISIBLE
        }

    init {
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
        keysCanvas.onSwipeSuggestions = { onSwipeSuggestions?.invoke(it) }

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
    var onSwipeSuggestions: ((List<String>) -> Unit)? = null

    private val keys = mutableListOf<KeyInfo>()
    private var shiftState = ShiftState.OFF
    private val isUpperCase: Boolean get() = shiftState != ShiftState.OFF
    private var mode = KeyboardMode.LETTERS

    // Swipe state
    private var isSwiping = false
    private val swipePath = mutableListOf<Char>()   // letras por las que pasó el dedo
    private val swipePoints = mutableListOf<Pair<Float, Float>>()
    private var swipeStartKey: KeyInfo? = null
    private var lastSwipeKey: KeyInfo? = null

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
                        key.type is KeyType.NumSymToggle -> specialKeyPaint
                else -> keyPaint
            }
            canvas.drawRoundRect(r, 8f, 8f, paint)
            drawKeyContent(canvas, key)
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
            KeyType.Shift -> {
                val arrowPaint = if (shiftState == ShiftState.CAPS_LOCK) whiteTextPaint else textPaint
                canvas.drawText("⇧", cx, cy + 8f, arrowPaint)
                if (shiftState == ShiftState.CAPS_LOCK) {
                    canvas.drawRoundRect(
                        RectF(cx - 14f, key.rect.bottom - 16f, cx + 14f, key.rect.bottom - 9f),
                        2f, 2f, lockIndicatorPaint)
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
        swipePath.clear()
        swipePoints.clear()

        val key = keys.firstOrNull { it.rect.contains(event.x, event.y) }
        pressedKey = key
        swipeStartKey = key
        lastSwipeKey = key

        // Registrar punto inicial
        key?.let {
            if (it.type is KeyType.Letter) {
                swipePath.add(it.type.char)
                swipePoints.add(event.x to event.y)
            }
        }

        invalidate()

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
                // Cancelar long-press si se mueve
                longPressRunnable?.let { actionHandler.removeCallbacks(it) }
            }
        }

        if (isSwiping) {
            swipePoints.add(event.x to event.y)

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

        if (isSwiping && swipePath.size >= 2) {
            // Convertir trayectoria en sugerencias
            val suggestions = SwipePredictor.predict(swipePath)
            if (suggestions.isNotEmpty()) {
                // Insertar la mejor candidata directamente; las otras van a la barra
                onLetterTyped?.invoke(0.toChar()) // flush (sin efecto real)
                // Emitir la primera como texto
                commitSwipeWord(suggestions.first())
                onSwipeSuggestions?.invoke(suggestions)
            } else {
                // No hay predicción: escribir las letras del path tal cual
                swipePath.forEach { c ->
                    val out = if (isUpperCase) c.uppercaseChar() else c
                    onLetterTyped?.invoke(out)
                }
            }
            isSwiping = false
            swipePath.clear()
            swipePoints.clear()
            pressedKey = null
            invalidate()
            return
        }

        // Toque normal (sin swipe)
        isSwiping = false
        swipePath.clear()
        swipePoints.clear()

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
                KeyType.Shift -> handleShiftTap()
                KeyType.Backspace -> onBackspace?.invoke()
                KeyType.Numbers123 -> onModeChange?.invoke(KeyboardMode.NUMBERS)
                KeyType.BackToLetters -> onModeChange?.invoke(KeyboardMode.LETTERS)
                KeyType.ModeSwitchLeft, KeyType.NumSymToggle -> {
                    val next = if (mode == KeyboardMode.NUMBERS) KeyboardMode.SYMBOLS else KeyboardMode.NUMBERS
                    onModeChange?.invoke(next)
                }
                KeyType.CommaEmoji -> {
                    if (!longPressTriggered) onCharTyped?.invoke(',') else Unit
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

    private fun handleCancel() {
        longPressRunnable?.let { actionHandler.removeCallbacks(it) }
        isSwiping = false
        swipePath.clear()
        swipePoints.clear()
        pressedKey = null
        invalidate()
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
}
