package com.tunombre.tecladopegar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MyKeyboardService : InputMethodService() {

    private var suggestionBar: LinearLayout? = null
    private lateinit var personalDict: PersonalDictionary
    private var activeKeyboardView: KeyboardView? = null
    private var rootContainer: LinearLayout? = null   // contenedor raíz para inyectar overlays

    // Última palabra completada (para bigramas)
    private var lastCompletedWord: String? = null

    // Última palabra efectivamente APRENDIDA (word+bigrama+ngramas ya sumados al
    // diccionario) y la palabra de contexto que se usó como "anterior" en ese
    // aprendizaje. Sirve para detectar cuando el usuario borra justo el separador
    // tras esa palabra para corregirla: en ese caso se deshace el aprendizaje
    // como "comienza_comience" cuando en realidad es una sola palabra corregida.
    private var lastLearnedWord: String? = null
    private var lastLearnedWordPrev: String? = null

    // Estado de sugerencias de swipe (para reemplazar si el usuario elige otra)
    private var lastSwipeSuggestions: List<String> = emptyList()
    private var swipeInsertedLength: Int = 0

    // Sugerencias actualmente visibles en la barra (raw, sin capitalizar) y su modo,
    // para poder re-renderizarlas cuando cambia el estado de shift.
    private enum class SuggestionMode { NONE, TYPING, BIGRAM, SWIPE }
    private var currentSuggestions: List<String> = emptyList()
    private var currentSuggestionMode: SuggestionMode = SuggestionMode.NONE
    private var currentPartial: String = ""   // solo relevante en modo TYPING

    // Para detección de corrección: guardamos la última palabra commitida por swipe
    // y el path que la produjo. Si el usuario borra esa palabra y escribe otra, reportamos error.
    private var lastSwipeCommittedWord: String? = null
    private var lastSwipePath: List<Char> = emptyList()
    // ── Flujo de confirmación diferida del swipe ────────────────────────────
    // Tras swipear, la palabra se inserta SIN espacio y queda "pendiente de
    // confirmación": el espacio solo se agrega cuando el usuario confirma
    // (tocando una sugerencia) o cuando llega el siguiente swipe.
    private var pendingSwipeWord: String? = null
    // Snapshot de la decisión de mayúscula tomada en el momento del swipe,
    // ANTES de que applyShiftToWord consuma el shift. Se reutiliza para pintar
    // las alternativas de la barra con la MISMA capitalización que la palabra
    // realmente commiteada (si no se captura antes, queda obsoleta).
    private var currentSwipeShouldCap: Boolean = false
    private var currentSwipeCapsLock: Boolean = false

    override fun onCreateInputView(): View {
        personalDict = PersonalDictionary(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        rootContainer = container

        // ── Fila superior ─────────────────────────────────────────────────────
        // Construimos la fila de botones programáticamente para no depender de IDs
        // que aún no existen en el layout XML (evita el error de compilación de btnDictionary).
        val dp = resources.displayMetrics.density
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#D1D4DB"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * dp).toInt()
            )
        }
        fun makeTopButton(label: String, weight: Float = 1f, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                textSize = 13f
                setPadding(0, 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
                setOnClickListener { onClick() }
            }
        topRow.addView(makeTopButton("📄 Copiar") { copySelectedText() })
        topRow.addView(makeTopButton("📋 Pegar") { pasteFullClipboard() })
        // Botón cambiar de teclado: solo el icono del planeta, sin texto.
        topRow.addView(makeTopButton("🌐", weight = 0.6f) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        // Botón lupa: justo a la derecha del planeta. Solo visible cuando el campo
        // de texto es una barra de búsqueda (navegadores, redes sociales, etc.).
        // Envía la acción IME_ACTION_SEARCH al campo activo (equivale a pulsar "Buscar").
        val searchBtn = makeTopButton("🔍", weight = 0.6f) {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
        }
        searchBtn.visibility = View.GONE   // oculto por defecto; onStartInputView lo muestra si aplica
        searchButton = searchBtn
        topRow.addView(searchBtn)
        topRow.addView(makeTopButton("📖 Diccionario") { showDictMenu() })

        // ── Barra de sugerencias ──────────────────────────────────────────────
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#F4F4F6"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (40 * dp).toInt()
            )
            visibility = View.GONE
        }
        suggestionBar = bar

        // ── Teclado principal ─────────────────────────────────────────────────
        val keyboardView = KeyboardView(this)
        activeKeyboardView = keyboardView
        keyboardView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (280 * resources.displayMetrics.density).toInt()
        )

        // Cuando el estado de shift cambia, refrescar los textos de la barra
        // para que reflejen la capitalización correcta sin reescribir la palabra.
        keyboardView.onShiftToggle = { _ ->
            refreshSuggestionBarDisplay()
        }

        keyboardView.onLetterTyped = { letter ->
            // Si había un swipe pendiente de confirmar, escribir letra a letra
            // implica que el usuario rechazó la predicción: limpiar todo el estado
            // del swipe para que no se confirme accidentalmente en el siguiente disparador.
            if (pendingSwipeWord != null) {
                pendingSwipeWord = null
                lastSwipeCommittedWord = null
                lastSwipePath = emptyList()
            }
            currentInputConnection?.commitText(letter.toString(), 1)
            updateTypingSuggestions()
        }
        keyboardView.onCharTyped = { c ->
            val ic = currentInputConnection
            if (ic != null && isPunctuation(c)) {
                // Aprender la palabra que precede al signo ANTES de commitear.
                if (pendingSwipeWord != null) {
                    confirmPendingSwipeIfAny()   // swipe pendiente → registrar bigrama/trayectoria
                } else {
                    learnCurrentWord()           // palabra escrita letra a letra
                }
                // Si hay un espacio justo antes, borrarlo para evitar "palabra ."
                val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                if (before == " ") ic.deleteSurroundingText(1, 0)
            }
            pendingSwipeWord = null
            ic?.commitText(c.toString(), 1)
            // Tras punto/signo de cierre → mayúscula inicial de oración
            if (c == '.' || c == '!' || c == '?') {
                keyboardView.resetToSentenceCase()
            }
        }
        keyboardView.onEmojiTyped = { emoji ->
            currentInputConnection?.commitText(emoji, 1)
        }
        keyboardView.onBackspace = {
            val ic = currentInputConnection
            if (ic != null) {
                if (pendingSwipeWord != null) {
                    // El backspace sobre una palabra swipeada pendiente = rechazo total.
                    // Borrar la palabra completa que insertó el swipe (no solo un carácter),
                    // porque el swipe la insertó de golpe y el usuario quiere empezar de cero.
                    val wordLen = swipeInsertedLength
                    if (wordLen > 0) {
                        ic.deleteSurroundingText(wordLen, 0)
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    }
                    pendingSwipeWord = null
                    lastSwipeCommittedWord = null
                    lastSwipePath = emptyList()
                    swipeInsertedLength = 0
                } else {
                    // Backspace normal: solo borra un carácter.
                    val selected = ic.getSelectedText(0)
                    if (!selected.isNullOrEmpty()) {
                        ic.commitText("", 0)
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    }
                }
                // Restaurar mayúscula de oración si corresponde
                val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                val trimmed = before.trimEnd(' ')
                val shouldCapitalize = trimmed.isEmpty() ||
                        trimmed.last() == '.' || trimmed.last() == '!' ||
                        trimmed.last() == '?' || trimmed.last() == '\n'
                if (shouldCapitalize) {
                    keyboardView.resetToSentenceCase()
                }
            }
            updateTypingSuggestions()
        }
        keyboardView.onSpace = {
            if (pendingSwipeWord != null) {
                confirmPendingSwipeIfAny()   // swipe pendiente → registrar bigrama/trayectoria
            } else {
                learnCurrentWord()           // palabra escrita letra a letra
            }
            currentInputConnection?.commitText(" ", 1)
            updateTypingSuggestions()
        }
        keyboardView.onEnter = {
            if (pendingSwipeWord != null) {
                confirmPendingSwipeIfAny()
            } else {
                learnCurrentWord()
            }
            currentInputConnection?.commitText("\n", 1)
            hideSuggestions()
            keyboardView.resetToSentenceCase()
        }
        keyboardView.onSwipeSuggestions = { _, pathChars, totalLenPx, durationMs, pauseIndices ->
            val ic = currentInputConnection

            // El siguiente swipe confirma implícitamente la palabra pendiente anterior.
            // Es un disparador legítimo: registrar bigrama y trayectoria, luego emitir espacio.
            if (pendingSwipeWord != null) {
                confirmPendingSwipeIfAny()
                ic?.commitText(" ", 1)
            }

            // Consultar PersonalDictionary con las trayectorias SWIPE precalculadas
            // Las pausas se pasan al diccionario para que pueda segmentar el swipe si aplica
            val dictSuggestions = personalDict.suggestForSwipe(
                pathChars, lastCompletedWord, topN = 3, pauseIndices = pauseIndices
            )
            lastSwipeSuggestions = dictSuggestions

            // Capturar la decisión de mayúscula ANTES de consumirla con applyShiftToWord.
            // Si se consulta DESPUÉS (como antes), el shift ya está apagado y las
            // alternativas de la barra salen siempre en minúscula.
            currentSwipeShouldCap = keyboardView.isShiftActive()
            currentSwipeCapsLock = keyboardView.isCapsLockActive()

            if (dictSuggestions.isNotEmpty()) {
                // Aplicar el snapshot de shift y resetear SINGLE → OFF, igual que al clickear
                // una letra. La palabra se inserta SIN espacio: queda pendiente de confirmación.
                val wordToCommit = keyboardView.applyShiftToWord(dictSuggestions[0])
                swipeInsertedLength = wordToCommit.length   // sin +1: todavía no hay espacio
                ic?.commitText(wordToCommit, 1)
                pendingSwipeWord = wordToCommit
                // Guardar para aprendizaje DIFERIDO: learnSwipeWord, el bigrama y
                // lastCompletedWord se actualizan SOLO en el disparador legítimo
                // (siguiente swipe, espacio, puntuación, tap en sugerencia).
                // Aquí solo guardamos lo necesario para poder aprender en ese momento.
                lastSwipeCommittedWord = dictSuggestions[0]   // minúscula para el diccionario
                lastSwipePath = pathChars
                // lastCompletedWord NO se toca aquí: la palabra aún no está confirmada.
            } else {
                // Sin predicción: escribir las letras del path respetando shift (fallback),
                // también sin espacio final — queda igualmente pendiente de confirmación.
                val builtWord = StringBuilder()
                pathChars.forEachIndexed { i, c ->
                    val out = if (i == 0) keyboardView.applyShiftToWord(c.toString())[0] else c
                    builtWord.append(out)
                    ic?.commitText(out.toString(), 1)
                }
                lastSwipeCommittedWord = null
                lastSwipePath = emptyList()
                pendingSwipeWord = builtWord.toString().ifEmpty { null }
            }
            showSwipeSuggestions(bar, dictSuggestions)
        }

        container.addView(topRow)
        container.addView(bar)
        container.addView(keyboardView)
        return container
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sugerencias mientras se escribe letra a letra
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateTypingSuggestions() {
        if (personalDict.isEmpty()) { hideSuggestions(); return }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return

        // partial = fragmento que el usuario está escribiendo ahora.
        // Si el último carácter es espacio/salto, partial es "" (entre palabras).
        // NO usar trimEnd() aquí porque borra ese espacio y confunde "hola " con "hola".
        val partial = if (before.isEmpty() || before.last() == ' ' || before.last() == '\n')
            ""
        else
            before.substringAfterLast(' ').substringAfterLast('\n')

        val prevWord = before.trimEnd()
            .substringBeforeLast(' ').trimEnd()
            .substringAfterLast(' ').substringAfterLast('\n')
            .takeIf { it.isNotBlank() }

        if (partial.length < 1) {
            // Sin prefix: sugerir siguiente palabra por bigrama si hay contexto
            if (!lastCompletedWord.isNullOrBlank()) {
                val nextSuggestions = personalDict.suggestNextWord(lastCompletedWord!!, topN = 3)
                if (nextSuggestions.isNotEmpty()) {
                    showNextWordSuggestions(requireNotNull(suggestionBar), nextSuggestions)
                    return
                }
            }
            hideSuggestions()
            return
        }

        val suggestions = personalDict.suggest(partial, prevWord, topN = 3)
        if (suggestions.isEmpty()) hideSuggestions()
        else showTypingSuggestions(requireNotNull(suggestionBar), suggestions, partial)
    }

    private fun showTypingSuggestions(
        bar: LinearLayout,
        suggestions: List<String>,
        partial: String
    ) {
        currentSuggestions = suggestions
        currentSuggestionMode = SuggestionMode.TYPING
        currentPartial = partial
        bar.removeAllViews()
        bar.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density

        suggestions.forEachIndexed { i, word ->
            if (i > 0) bar.addView(makeDivider(dp))
            // Usamos el propio texto ya escrito (partial) como fuente de verdad de la
            // mayúscula, no el estado "en vivo" del shift: para cuando llegamos aquí,
            // el shift SINGLE ya pudo haberse consumido al tipear la primera letra.
            val display = capitalizeForPartial(word, partial)
            bar.addView(makeSuggestionView(display, word, bold = i == 0) {
                val ic = currentInputConnection ?: return@makeSuggestionView
                val wordToCommit = capitalizeForPartial(word, partial)
                ic.beginBatchEdit()
                repeat(partial.length) { ic.deleteSurroundingText(1, 0) }
                ic.commitText("$wordToCommit ", 1)
                ic.endBatchEdit()
                val prevForLearn = lastCompletedWord
                personalDict.learnWord(word, prevForLearn)
                lastCompletedWord = word
                lastLearnedWord = word
                lastLearnedWordPrev = prevForLearn
                // Mostrar bigramas para la palabra recién elegida
                updateTypingSuggestions()
            })
        }
    }

    /** Muestra sugerencias de siguiente palabra por bigrama (sin prefix activo). */
    private fun showNextWordSuggestions(bar: LinearLayout, suggestions: List<String>) {
        currentSuggestions = suggestions
        currentSuggestionMode = SuggestionMode.BIGRAM
        currentPartial = ""
        bar.removeAllViews()
        bar.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density

        suggestions.forEachIndexed { i, word ->
            if (i > 0) bar.addView(makeDivider(dp))
            val display = capitalizeForDisplay(word)
            bar.addView(makeSuggestionView(display, word, bold = i == 0) {
                val ic = currentInputConnection ?: return@makeSuggestionView
                val wordToCommit = activeKeyboardView?.applyShiftToWord(word) ?: word
                ic.commitText("$wordToCommit ", 1)
                val prevForLearn = lastCompletedWord
                personalDict.learnWord(word, prevForLearn)
                lastCompletedWord = word
                lastLearnedWord = word
                lastLearnedWordPrev = prevForLearn
                // Mostrar bigramas para la palabra recién elegida
                updateTypingSuggestions()
            })
        }
    }

    /** Aplica una decisión de mayúscula ya tomada (no consulta ningún estado en vivo). */
    private fun applyCase(word: String, shouldCap: Boolean, capsLock: Boolean): String {
        if (!shouldCap) return word
        return if (capsLock) word.uppercase() else word.replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Devuelve la palabra con la capitalización visual correcta según el estado
     * ACTUAL (en vivo) de shift, SIN consumirlo. Válido solo cuando nada ha
     * consumido el shift todavía desde que se disparó la sugerencia (p. ej.
     * sugerencias BIGRAM sin prefijo, donde no se ha tecleado nada nuevo).
     */
    private fun capitalizeForDisplay(word: String): String {
        val kv = activeKeyboardView ?: return word
        return applyCase(word, kv.isShiftActive(), kv.isCapsLockActive())
    }

    /**
     * Decide la mayúscula de una sugerencia de completado (modo TYPING) mirando
     * el propio [partial] ya escrito en pantalla — es la única fuente de verdad
     * fiable, porque para cuando se construye la sugerencia el shift SINGLE del
     * teclado ya pudo haberse consumido (SINGLE→OFF) al tipear esa primera letra.
     */
    private fun capitalizeForPartial(word: String, partial: String): String {
        if (partial.isEmpty()) return word
        val shouldCap = partial[0].isUpperCase()
        // Heurística de caps lock: todo el partial (letras) está en mayúscula y
        // tiene más de un carácter → asumimos CAPS LOCK y ponemos todo en mayúscula.
        val capsLock = partial.length > 1 && partial.all { !it.isLetter() || it.isUpperCase() }
        return applyCase(word, shouldCap, capsLock)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sugerencias de swipe
    // ─────────────────────────────────────────────────────────────────────────

    private fun showSwipeSuggestions(bar: LinearLayout, suggestions: List<String>) {
        bar.removeAllViews()
        if (suggestions.isEmpty()) { bar.visibility = View.GONE; return }
        currentSuggestions = suggestions
        currentSuggestionMode = SuggestionMode.SWIPE
        currentPartial = ""
        bar.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density

        suggestions.forEachIndexed { i, word ->
            if (i > 0) bar.addView(makeDivider(dp))
            // Usamos el snapshot capturado ANTES de consumir el shift (ver
            // onSwipeSuggestions) para que la alternativa se vea EXACTAMENTE con
            // la misma mayúscula que tendría si la confirmas.
            val display = applyCase(word, currentSwipeShouldCap, currentSwipeCapsLock)
            bar.addView(makeSuggestionView(display, word, bold = i == 0) {
                confirmSwipeSelection(word, isAlternative = i > 0)
            })
        }
    }

    /**
     * Confirma la palabra pendiente de swipe. Si [word] es una alternativa distinta
     * a la ya commiteada, la reemplaza (respetando la misma mayúscula). En cualquier
     * caso agrega el espacio que había quedado pendiente y dispara el ciclo BIGRAM
     * mostrando sugerencias para la siguiente palabra.
     */
    private fun confirmSwipeSelection(word: String, isAlternative: Boolean) {
        val ic = currentInputConnection ?: return
        if (isAlternative) {
            // El usuario eligió una alternativa: la primera predicción fue un error.
            // Reportar el error y aprender la alternativa elegida.
            val wrongWord = lastSwipeCommittedWord
            val wrongPath = lastSwipePath
            if (wrongWord != null && wrongPath.isNotEmpty()) {
                personalDict.reportSwipeError(wrongWord, wrongPath)
            }
            val wordToCommit = applyCase(word, currentSwipeShouldCap, currentSwipeCapsLock)
            ic.beginBatchEdit()
            repeat(swipeInsertedLength) { ic.deleteSurroundingText(1, 0) }
            ic.commitText(wordToCommit, 1)
            ic.endBatchEdit()
            // Limpiar el estado de swipe ANTES de llamar confirmPendingSwipeIfAny
            // para que aprenda la alternativa correcta, no la predicción original.
            lastSwipeCommittedWord = word
            lastSwipePath = emptyList()
            confirmPendingSwipeIfAny()
        } else {
            // El usuario confirmó la primera predicción: registrar bigrama y trayectoria.
            confirmPendingSwipeIfAny()
        }
        // Agregar el espacio que había quedado pendiente.
        ic.commitText(" ", 1)
        // La confirmación dispara el ciclo BIGRAM.
        updateTypingSuggestions()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menú del diccionario — panel nativo (sin AlertDialog para evitar crash)
    // ─────────────────────────────────────────────────────────────────────────

    private var dictMenuPanel: LinearLayout? = null
    private var searchButton: Button? = null   // botón lupa, visible solo en campos de búsqueda

    private fun showDictMenu() {
        val root = rootContainer ?: return
        // Si ya hay uno abierto, cerrarlo
        dictMenuPanel?.let { root.removeView(it); dictMenuPanel = null }

        // Refresco por seguridad: si venimos de importar/exportar/borrar, que el conteo
        // y las sugerencias reflejen lo que hay realmente en disco, no una copia vieja.
        personalDict.reload()
        val wordCount = personalDict.wordCount()
        val dp = resources.displayMetrics.density

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            elevation = 12f * dp
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        // Título
        panel.addView(TextView(this).apply {
            text = if (wordCount == 0) "📖 Diccionario (vacío)" else "📖 Diccionario ($wordCount palabras)"
            textSize = 15f
            setTextColor(Color.parseColor("#1C1C1E"))
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        // Divisor
        fun addDivider() = panel.addView(android.view.View(this).apply {
            setBackgroundColor(Color.parseColor("#E5E5EA"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also { it.setMargins((16*dp).toInt(), 0, (16*dp).toInt(), 0) }
        })

        // Opción
        fun addOption(label: String, action: () -> Unit) {
            addDivider()
            panel.addView(TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#1668E3"))
                setPadding((16 * dp).toInt(), (13 * dp).toInt(), (16 * dp).toInt(), (13 * dp).toInt())
                setOnClickListener {
                    closeDictMenu()
                    action()
                }
            })
        }

        addOption("📤  Exportar diccionario") { launchExport() }
        addOption("📥  Importar diccionario") { launchImport() }
        addOption("🗑️  Borrar todo el diccionario") { confirmClear() }

        // Cancelar
        addDivider()
        panel.addView(TextView(this).apply {
            text = "✕  Cancelar"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding((16 * dp).toInt(), (13 * dp).toInt(), (16 * dp).toInt(), (13 * dp).toInt())
            setOnClickListener { closeDictMenu() }
        })

        // Insertar encima de todo (índice 0 = primera posición visual si el container es VERTICAL
        // pero queremos que aparezca arriba del teclado, así que lo añadimos antes del teclado)
        // El container tiene: topRow(0), bar(1), keyboardView(2)
        // Insertamos el panel en posición 1 (debajo de topRow, encima de la barra de sugerencias)
        root.addView(panel, 1)
        dictMenuPanel = panel
    }

    private fun closeDictMenu() {
        rootContainer?.removeView(dictMenuPanel)
        dictMenuPanel = null
    }

    private fun launchExport() {
        val intent = Intent(this, DictManagerActivity::class.java).apply {
            action = DictManagerActivity.ACTION_EXPORT
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun launchImport() {
        val intent = Intent(this, DictManagerActivity::class.java).apply {
            action = DictManagerActivity.ACTION_IMPORT
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun confirmClear() {
        val root = rootContainer ?: return
        val dp = resources.displayMetrics.density

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            elevation = 12f * dp
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        panel.addView(TextView(this).apply {
            text = "¿Borrar todas las palabras aprendidas?"
            textSize = 14f
            setTextColor(Color.parseColor("#1C1C1E"))
            setPadding((16*dp).toInt(), (14*dp).toInt(), (16*dp).toInt(), (6*dp).toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = "Esta acción no se puede deshacer."
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding((16*dp).toInt(), 0, (16*dp).toInt(), (10*dp).toInt())
        })
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16*dp).toInt(), (4*dp).toInt(), (16*dp).toInt(), (12*dp).toInt())
        }
        btnRow.addView(TextView(this).apply {
            text = "Cancelar"
            textSize = 14f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding((8*dp).toInt(), (8*dp).toInt(), (24*dp).toInt(), (8*dp).toInt())
            setOnClickListener { root.removeView(panel) }
        })
        btnRow.addView(TextView(this).apply {
            text = "Borrar"
            textSize = 14f
            setTextColor(Color.parseColor("#FF3B30"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding((8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt())
            setOnClickListener {
                root.removeView(panel)
                personalDict.clear()
                Toast.makeText(this@MyKeyboardService, "Diccionario borrado", Toast.LENGTH_SHORT).show()
            }
        })
        panel.addView(btnRow)
        root.addView(panel, 1)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Confirma la palabra swipeada pendiente registrando bigrama y trayectoria.
     * Llamar ANTES de emitir el disparador (espacio, puntuación, enter).
     * Si no hay swipe pendiente, no hace nada.
     */
    private fun confirmPendingSwipeIfAny() {
        val confirmedWord = lastSwipeCommittedWord ?: return
        val confirmedPath = lastSwipePath
        val prevForLearn = lastCompletedWord
        personalDict.learnSwipeWord(confirmedWord, confirmedPath, 0f, 0L, prevForLearn)
        personalDict.learnWord(confirmedWord, prevForLearn)
        lastCompletedWord = confirmedWord
        lastLearnedWord = confirmedWord
        lastLearnedWordPrev = prevForLearn
        pendingSwipeWord = null
        lastSwipeCommittedWord = null
        lastSwipePath = emptyList()
    }

    private fun learnCurrentWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        val word = before.trimEnd().substringAfterLast(' ').substringAfterLast('\n').trim()
        if (word.length >= 2) {
            val prevForLearn = lastCompletedWord
            personalDict.learnWord(word, prevForLearn)
            lastCompletedWord = word
            lastLearnedWord = word
            lastLearnedWordPrev = prevForLearn
        }
    }

    private fun hideSuggestions() {
        suggestionBar?.removeAllViews()
        suggestionBar?.visibility = View.GONE
        lastSwipeSuggestions = emptyList()
        swipeInsertedLength = 0
        currentSuggestions = emptyList()
        currentSuggestionMode = SuggestionMode.NONE
        currentPartial = ""
    }

    /** Limpia todo el estado asociado a un swipe pendiente de confirmación. */
    private fun clearPendingSwipe() {
        pendingSwipeWord = null
        lastSwipeCommittedWord = null
        lastSwipePath = emptyList()
    }

    /**
     * Re-renderiza los textos de la barra con la capitalización actualizada según
     * el estado de shift actual, sin volver a consultar el diccionario.
     * Se llama desde onShiftToggle cada vez que el usuario toca la tecla shift.
     */
    private fun refreshSuggestionBarDisplay() {
        val bar = suggestionBar ?: return
        if (currentSuggestions.isEmpty() || currentSuggestionMode == SuggestionMode.NONE) return
        val dp = resources.displayMetrics.density
        bar.removeAllViews()
        currentSuggestions.forEachIndexed { i, word ->
            if (i > 0) bar.addView(makeDivider(dp))
            when (currentSuggestionMode) {
                SuggestionMode.TYPING -> {
                    val partial = currentPartial
                    val display = capitalizeForPartial(word, partial)
                    bar.addView(makeSuggestionView(display, word, bold = i == 0) {
                        val ic = currentInputConnection ?: return@makeSuggestionView
                        val wordToCommit = capitalizeForPartial(word, partial)
                        ic.beginBatchEdit()
                        repeat(partial.length) { ic.deleteSurroundingText(1, 0) }
                        ic.commitText("$wordToCommit ", 1)
                        ic.endBatchEdit()
                        val prevForLearn = lastCompletedWord
                personalDict.learnWord(word, prevForLearn)
                        lastCompletedWord = word
                        lastLearnedWord = word
                        lastLearnedWordPrev = prevForLearn
                        updateTypingSuggestions()
                    })
                }
                SuggestionMode.BIGRAM -> {
                    val display = capitalizeForDisplay(word)
                    bar.addView(makeSuggestionView(display, word, bold = i == 0) {
                        val ic = currentInputConnection ?: return@makeSuggestionView
                        val wordToCommit = activeKeyboardView?.applyShiftToWord(word) ?: word
                        ic.commitText("$wordToCommit ", 1)
                        val prevForLearn = lastCompletedWord
                personalDict.learnWord(word, prevForLearn)
                        lastCompletedWord = word
                        lastLearnedWord = word
                        lastLearnedWordPrev = prevForLearn
                        updateTypingSuggestions()
                    })
                }
                SuggestionMode.SWIPE -> {
                    // Mismo snapshot que showSwipeSuggestions: NO volver a consultar el
                    // shift en vivo, porque ya fue consumido al commitear la palabra.
                    val display = applyCase(word, currentSwipeShouldCap, currentSwipeCapsLock)
                    bar.addView(makeSuggestionView(display, word, bold = i == 0) {
                        confirmSwipeSelection(word, isAlternative = i > 0)
                    })
                }
                SuggestionMode.NONE -> {}
            }
        }
    }

    /**
     * Signos que no deben ir precedidos de espacio.
     * Incluye puntuación final, apertura y cierre, y otros signos comunes.
     */
    private fun isPunctuation(c: Char): Boolean = c in ".,:;!?¡¿)]}'\"-…"

    /**
     * [displayText] es lo que se muestra en pantalla (puede tener mayúscula por shift).
     * [rawWord]     es la palabra en minúscula tal como vive en el diccionario.
     */
    private fun makeSuggestionView(
        displayText: String,
        rawWord: String,
        bold: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = displayText
            textSize = 14f
            setTextColor(Color.parseColor("#1C1C1E"))
            gravity = Gravity.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isLongClickable = true          // imprescindible para que el long press no lo absorba el click
            setOnClickListener { onClick() }
            // Long press: eliminar la palabra del diccionario con confirmación visual
            setOnLongClickListener {
                personalDict.removeWord(rawWord.lowercase().trim())
                text = "✕ $displayText"
                setTextColor(Color.parseColor("#FF3B30"))
                postDelayed({ hideSuggestions() }, 800)
                true
            }
        }
    }

    private fun makeDivider(dp: Float): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#D1D1D6"))
        layoutParams = LinearLayout.LayoutParams(1, (24 * dp).toInt()).also {
            it.gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun copySelectedText() {
        val ic = currentInputConnection
        val selected = ic?.getSelectedText(0)
        if (ic == null || selected.isNullOrEmpty()) {
            Toast.makeText(this, "No hay texto seleccionado", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Texto copiado", selected))
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
    }

    private fun pasteFullClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val fullText = clip.getItemAt(0).coerceToText(this).toString()
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        ic.commitText(fullText, 1)
        ic.endBatchEdit()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Reimportante: recarga desde disco por si DictManagerActivity importó/exportó/borró
        // mientras este servicio seguía vivo con una copia vieja en memoria.
        if (::personalDict.isInitialized) personalDict.reload()
        lastCompletedWord = null
        clearPendingSwipe()
        hideSuggestions()
        // Mayúscula inicial al abrir un campo de texto nuevo
        activeKeyboardView?.resetToSentenceCase()
    }

    /**
     * A diferencia de onStartInput (que puede dispararse ANTES de que exista la vista,
     * sobre todo la primera vez que se abre el teclado tras instalar la app),
     * onStartInputView se llama siempre después de onCreateInputView. Por eso todo lo
     * que depende de la vista (botón lupa, aviso de diccionario vacío) va aquí:
     * si se dejaba en onStartInput, en el primerísimo arranque personalDict/searchButton
     * aún no existían y el aviso simplemente nunca se mostraba.
     */
    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)

        // ── Botón lupa: visible solo en campos de búsqueda ───────────────────
        val inputVariation = (attribute?.inputType ?: 0) and 0x00000FF0
        val isSearchField = attribute != null && (
            (attribute.imeOptions and EditorInfo.IME_ACTION_SEARCH) != 0 ||
            inputVariation == 0x000000E0 ||   // TYPE_TEXT_VARIATION_WEB_SEARCH
            inputVariation == 0x000000B0      // TYPE_TEXT_VARIATION_FILTER
        )
        searchButton?.visibility = if (isSearchField) View.VISIBLE else View.GONE

        // ── Si el diccionario está vacío, sugerir importar ───────────────────
        if (::personalDict.isInitialized && personalDict.isEmpty()) {
            showImportHint()
        }
    }

    /**
     * Muestra un aviso en la barra de sugerencias invitando a importar el diccionario.
     * Desaparece en cuanto el usuario empieza a escribir (updateTypingSuggestions lo limpia).
     */
    private fun showImportHint() {
        val bar = suggestionBar ?: return
        val dp = resources.displayMetrics.density
        bar.removeAllViews()
        bar.visibility = View.VISIBLE
        val hint = TextView(this).apply {
            text = "📥 Importar diccionario para ver sugerencias"
            textSize = 13f
            setTextColor(Color.parseColor("#1668E3"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { launchImport() }
        }
        bar.addView(hint)
    }
}
