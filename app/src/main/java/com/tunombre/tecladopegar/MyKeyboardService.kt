package com.tunombre.tecladopegar

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MyKeyboardService : InputMethodService() {

    private var suggestionBar: LinearLayout? = null

    // Diccionario personal (se carga una sola vez por sesión)
    private lateinit var personalDict: PersonalDictionary

    // Última palabra completada (para bigramas)
    private var lastCompletedWord: String? = null

    // Palabra que se insertó por swipe (para poder reemplazarla si el usuario
    // toca otra sugerencia)
    private var lastSwipeSuggestions: List<String> = emptyList()
    private var swipeInsertedLength: Int = 0   // largo de "palabra + espacio" ya insertados

    override fun onCreateInputView(): View {
        personalDict = PersonalDictionary(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ── Fila superior: Pegar + cambiar teclado ────────────────────────────
        val topRow = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        topRow.setBackgroundColor(Color.parseColor("#D1D4DB"))
        topRow.findViewById<Button>(R.id.btnPaste).setOnClickListener { pasteFullClipboard() }
        topRow.findViewById<Button>(R.id.btnSwitchKeyboard).setOnClickListener {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showInputMethodPicker()
        }

        // ── Barra de sugerencias (swipe + escritura) ──────────────────────────
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#F4F4F6"))
            val dp = resources.displayMetrics.density
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (40 * dp).toInt()
            )
            visibility = View.GONE
        }
        suggestionBar = bar

        // ── Teclado principal ─────────────────────────────────────────────────
        val keyboardView = KeyboardView(this)
        keyboardView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (280 * resources.displayMetrics.density).toInt()
        )

        // Letras (modo LETTERS)
        keyboardView.onLetterTyped = { letter ->
            currentInputConnection?.commitText(letter.toString(), 1)
            // Actualizar sugerencias mientras se escribe
            updateTypingSuggestions()
        }

        // Caracteres especiales (coma, punto, símbolos…)
        keyboardView.onCharTyped = { c ->
            currentInputConnection?.commitText(c.toString(), 1)
            // Sugerencias solo en modo letras; otros modos no las necesitan
        }

        keyboardView.onEmojiTyped = { emoji ->
            currentInputConnection?.commitText(emoji, 1)
        }

        keyboardView.onBackspace = {
            currentInputConnection?.deleteSurroundingText(1, 0)
            updateTypingSuggestions()
        }

        keyboardView.onSpace = {
            // Aprender la palabra actual antes de insertar el espacio
            learnCurrentWord()
            currentInputConnection?.commitText(" ", 1)
            hideSuggestions()
        }

        keyboardView.onEnter = {
            learnCurrentWord()
            currentInputConnection?.commitText("\n", 1)
            hideSuggestions()
        }

        // Sugerencias de swipe
        keyboardView.onSwipeSuggestions = { suggestions ->
            lastSwipeSuggestions = suggestions
            // El swipe ya insertó la primera sugerencia + espacio en commitSwipeWord.
            // Calculamos cuántos caracteres insertar para poder reemplazar.
            swipeInsertedLength = if (suggestions.isNotEmpty()) suggestions[0].length + 1 else 0
            // Aprender la palabra que se acaba de swipear
            if (suggestions.isNotEmpty()) {
                personalDict.learnWord(suggestions[0], lastCompletedWord)
                lastCompletedWord = suggestions[0]
            }
            showSwipeSuggestions(bar, suggestions)
        }

        container.addView(topRow)
        container.addView(bar)
        container.addView(keyboardView)

        return container
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sugerencias mientras se escribe
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateTypingSuggestions() {
        val ic = currentInputConnection ?: return
        // Leer hasta 50 caracteres antes del cursor
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        // Extraer la palabra parcial actual (desde el último espacio/salto)
        val partial = before.trimEnd().substringAfterLast(' ').substringAfterLast('\n')

        if (partial.length < 1) {
            hideSuggestions()
            return
        }

        // Palabras previas para bigramas
        val prevWord = before.trimEnd().substringBeforeLast(' ')
            .trimEnd().substringAfterLast(' ').substringAfterLast('\n')
            .takeIf { it.isNotBlank() }

        val suggestions = personalDict.suggest(partial, prevWord, topN = 3)
        if (suggestions.isEmpty()) {
            hideSuggestions()
        } else {
            showTypingSuggestions(requireNotNull(suggestionBar), suggestions, partial)
        }
    }

    private fun showTypingSuggestions(
        bar: LinearLayout,
        suggestions: List<String>,
        currentPartial: String
    ) {
        bar.removeAllViews()
        bar.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density

        suggestions.forEachIndexed { i, word ->
            if (i > 0) bar.addView(makeDivider(dp))

            val tv = TextView(this).apply {
                text = word
                textSize = 14f
                setTextColor(Color.parseColor("#1C1C1E"))
                gravity = Gravity.CENTER
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    val ic = currentInputConnection ?: return@setOnClickListener
                    ic.beginBatchEdit()
                    // Borrar la parte ya escrita y reemplazar con la sugerencia
                    repeat(currentPartial.length) { ic.deleteSurroundingText(1, 0) }
                    ic.commitText("$word ", 1)
                    ic.endBatchEdit()
                    // Aprender
                    personalDict.learnWord(word, lastCompletedWord)
                    lastCompletedWord = word
                    hideSuggestions()
                }
            }
            bar.addView(tv)
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

            val tv = TextView(this).apply {
                text = word
                textSize = 14f
                setTextColor(Color.parseColor("#1C1C1E"))
                gravity = Gravity.CENTER
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    if (i > 0) {
                        // Reemplazar la primera sugerencia ya insertada por esta
                        val ic = currentInputConnection ?: return@setOnClickListener
                        ic.beginBatchEdit()
                        repeat(swipeInsertedLength) { ic.deleteSurroundingText(1, 0) }
                        ic.commitText("$word ", 1)
                        ic.endBatchEdit()
                        // Aprender la corrección del usuario
                        personalDict.learnWord(word, lastCompletedWord)
                        lastCompletedWord = word
                    }
                    hideSuggestions()
                }
            }
            bar.addView(tv)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Aprende la palabra que está justo antes del cursor (sin espacio aún). */
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

    private fun makeDivider(dp: Float): View = View(this).apply {
        setBackgroundColor(Color.parseColor("#D1D1D6"))
        layoutParams = LinearLayout.LayoutParams(1, (24 * dp).toInt()).also {
            it.gravity = Gravity.CENTER_VERTICAL
        }
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

    // Limpiar estado al cambiar de campo
    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lastCompletedWord = null
        hideSuggestions()
    }
}
