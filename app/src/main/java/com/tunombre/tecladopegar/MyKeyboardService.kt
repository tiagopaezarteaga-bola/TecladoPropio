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

    // Estado de sugerencias de swipe (para reemplazar si el usuario elige otra)
    private var lastSwipeSuggestions: List<String> = emptyList()
    private var swipeInsertedLength: Int = 0

    // Para detección de corrección: guardamos la última palabra commitida por swipe
    // y el path que la produjo. Si el usuario borra esa palabra y escribe otra, reportamos error.
    private var lastSwipeCommittedWord: String? = null
    private var lastSwipePath: List<Char> = emptyList()

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
        topRow.addView(makeTopButton("🌐 Teclados") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
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

        keyboardView.onLetterTyped = { letter ->
            currentInputConnection?.commitText(letter.toString(), 1)
            updateTypingSuggestions()
        }
        keyboardView.onCharTyped = { c ->
            val ic = currentInputConnection
            if (ic != null && isPunctuation(c)) {
                // Aprender la palabra que precede al signo ANTES de commitear
                // (getTextBeforeCursor todavía apunta a la palabra, no al signo)
                learnCurrentWord()
                // Si hay un espacio justo antes, borrarlo para evitar "palabra ."
                val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
                if (before == " ") ic.deleteSurroundingText(1, 0)
            }
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
                // Detección de corrección de swipe: si el usuario borra la palabra que acabamos
                // de commitir por swipe, registramos que esa predicción fue un error.
                if (lastSwipeCommittedWord != null) {
                    val before = ic.getTextBeforeCursor(lastSwipeCommittedWord!!.length + 2, 0)?.toString() ?: ""
                    // La palabra insertada va seguida de espacio: "palabra "
                    val expectedSuffix = lastSwipeCommittedWord!! + " "
                    if (before.endsWith(expectedSuffix)) {
                        // El usuario está borrando la predicción — reportar error al diccionario
                        personalDict.reportSwipeError(lastSwipeCommittedWord!!, lastSwipePath)
                        lastSwipeCommittedWord = null
                        lastSwipePath = emptyList()
                    } else {
                        // Ya no aplica (escribió más texto después)
                        lastSwipeCommittedWord = null
                    }
                }

                // P3: si hay texto seleccionado, borrarlo en lugar de borrar el carácter anterior
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) {
                    ic.commitText("", 0)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
                // P2: tras borrar, restaurar mayúscula de oración si corresponde
                val before2 = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                val trimmed = before2.trimEnd(' ')
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
            learnCurrentWord()
            currentInputConnection?.commitText(" ", 1)
            hideSuggestions()
        }
        keyboardView.onEnter = {
            learnCurrentWord()
            currentInputConnection?.commitText("\n", 1)
            hideSuggestions()
            keyboardView.resetToSentenceCase()
        }
        keyboardView.onSwipeSuggestions = { _, pathChars, totalLenPx, durationMs, pauseIndices ->
            // Consultar PersonalDictionary con las trayectorias SWIPE precalculadas
            // Las pausas se pasan al diccionario para que pueda segmentar el swipe si aplica
            val dictSuggestions = personalDict.suggestForSwipe(
                pathChars, lastCompletedWord, topN = 3, pauseIndices = pauseIndices
            )
            lastSwipeSuggestions = dictSuggestions
            swipeInsertedLength = if (dictSuggestions.isNotEmpty()) dictSuggestions[0].length + 1 else 0
            if (dictSuggestions.isNotEmpty()) {
                // Aplicar estado de shift (mayúscula inicial de oración, caps lock, etc.)
                // y resetear SINGLE → OFF, igual que al clickear una letra.
                val wordToCommit = keyboardView.applyShiftToWord(dictSuggestions[0])
                // Ajustar longitud insertada al tamaño real (con mayúscula incluida)
                swipeInsertedLength = wordToCommit.length + 1
                // Commitear la mejor sugerencia directamente en el campo de texto
                val ic = currentInputConnection
                ic?.commitText("$wordToCommit ", 1)
                // Aprender la trayectoria para mejorar futuras predicciones
                personalDict.learnSwipeWord(
                    dictSuggestions[0], pathChars, totalLenPx, durationMs, lastCompletedWord
                )
                // Guardar para detección de corrección (la palabra en minúscula para el diccionario)
                lastSwipeCommittedWord = wordToCommit
                lastSwipePath = pathChars
                lastCompletedWord = dictSuggestions[0]
            } else {
                // Sin predicción: escribir las letras del path respetando shift (fallback)
                val ic = currentInputConnection
                pathChars.forEachIndexed { i, c ->
                    val out = if (i == 0) keyboardView.applyShiftToWord(c.toString())[0] else c
                    ic?.commitText(out.toString(), 1)
                }
                lastSwipeCommittedWord = null
                lastSwipePath = emptyList()
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
        val partial = before.trimEnd().substringAfterLast(' ').substringAfterLast('\n')

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
        bar.removeAllViews()
        bar.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density

        suggestions.forEachIndexed { i, word ->
            if (i > 0) bar.addView(makeDivider(dp))
            val display = capitalizeForDisplay(word)
            bar.addView(makeSuggestionView(display, word, bold = i == 0) {
                val ic = currentInputConnection ?: return@makeSuggestionView
                val wordToCommit = activeKeyboardView?.applyShiftToWord(word) ?: word
                ic.beginBatchEdit()
                repeat(partial.length) { ic.deleteSurroundingText(1, 0) }
                ic.commitText("$wordToCommit ", 1)
                ic.endBatchEdit()
                personalDict.learnWord(word, lastCompletedWord)
                lastCompletedWord = word
                // Mostrar bigramas para la palabra recién elegida
                updateTypingSuggestions()
            })
        }
    }

    /** Muestra sugerencias de siguiente palabra por bigrama (sin prefix activo). */
    private fun showNextWordSuggestions(bar: LinearLayout, suggestions: List<String>) {
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
                personalDict.learnWord(word, lastCompletedWord)
                lastCompletedWord = word
                // Mostrar bigramas para la palabra recién elegida
                updateTypingSuggestions()
            })
        }
    }

    /**
     * Devuelve la palabra con la capitalización visual correcta según el estado
     * actual de shift, SIN consumirlo.
     *
     * Para detectar caps lock sin llamar a applyShiftToWord (que consume el shift
     * SINGLE→OFF), leemos isCapsLockActive() si existe, o como fallback aplicamos
     * la heurística: caps lock = isShiftActive() sigue activo después de que ya
     * se commitió la última letra (es decir, no se apagó solo → es CAPS).
     * La forma más limpia es exponer isCapsLockActive en KeyboardView.
     */
    private fun capitalizeForDisplay(word: String): String {
        val kv = activeKeyboardView ?: return word
        if (!kv.isShiftActive()) return word
        // isCapsLockActive() es el método seguro; si no existe en esta versión
        // de KeyboardView, el catch devuelve single-shift como fallback.
        return try {
            if (kv.isCapsLockActive()) word.uppercase()
            else word.replaceFirstChar { it.uppercaseChar() }
        } catch (_: Exception) {
            word.replaceFirstChar { it.uppercaseChar() }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sugerencias de swipe
    // ─────────────────────────────────────────────────────────────────────────

    private fun showSwipeSuggestions(bar: LinearLayout, suggestions: List<String>) {
        bar.removeAllViews()
        if (suggestions.isEmpty()) { bar.visibility = View.GONE; return }
        bar.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density

        suggestions.forEachIndexed { i, word ->
            if (i > 0) bar.addView(makeDivider(dp))
            bar.addView(makeSuggestionView(word, word, bold = i == 0) {
                if (i > 0) {
                    val ic = currentInputConnection ?: return@makeSuggestionView
                    // El usuario eligió una alternativa: la primera predicción fue un error
                    val wrongWord = lastSwipeCommittedWord
                    val wrongPath = lastSwipePath
                    if (wrongWord != null && wrongPath.isNotEmpty()) {
                        personalDict.reportSwipeError(wrongWord, wrongPath)
                    }
                    ic.beginBatchEdit()
                    repeat(swipeInsertedLength) { ic.deleteSurroundingText(1, 0) }
                    ic.commitText("$word ", 1)
                    ic.endBatchEdit()
                    personalDict.learnWord(word, lastCompletedWord)
                    lastCompletedWord = word
                }
                lastSwipeCommittedWord = null
                lastSwipePath = emptyList()
                hideSuggestions()
            })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menú del diccionario — panel nativo (sin AlertDialog para evitar crash)
    // ─────────────────────────────────────────────────────────────────────────

    private var dictMenuPanel: LinearLayout? = null

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

    private fun learnCurrentWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        val word = before.trimEnd().substringAfterLast(' ').substringAfterLast('\n').trim()
        if (word.length >= 2) {
            personalDict.learnWord(word, lastCompletedWord)
            lastCompletedWord = word
        }
    }

    private fun hideSuggestions() {
        suggestionBar?.removeAllViews()
        suggestionBar?.visibility = View.GONE
        lastSwipeSuggestions = emptyList()
        swipeInsertedLength = 0
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
        // mientras este servicio seguía vivo con una copia vieja en memoria. Sin esto, el
        // próximo aprendizaje de palabra (learnWord/save) sobrescribiría el archivo recién
        // fusionado y las palabras importadas desaparecerían.
        if (::personalDict.isInitialized) personalDict.reload()
        lastCompletedWord = null
        hideSuggestions()
        // Mayúscula inicial al abrir un campo de texto nuevo
        activeKeyboardView?.resetToSentenceCase()
    }
}
