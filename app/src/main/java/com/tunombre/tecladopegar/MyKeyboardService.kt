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
    private var pendingSuggestions: List<String> = emptyList()

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ── Fila superior: Pegar + cambiar teclado
        val topRow = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        topRow.findViewById<Button>(R.id.btnPaste).setOnClickListener { pasteFullClipboard() }
        topRow.findViewById<Button>(R.id.btnSwitchKeyboard).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        // ── Barra de sugerencias swipe (inicialmente oculta)
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

        // ── Teclado principal
        val keyboardView = KeyboardView(this)
        keyboardView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (280 * resources.displayMetrics.density).toInt()
        )
        keyboardView.onLetterTyped = { letter -> currentInputConnection?.commitText(letter.toString(), 1) }
        keyboardView.onCharTyped = { c -> currentInputConnection?.commitText(c.toString(), 1) }
        keyboardView.onEmojiTyped = { emoji -> currentInputConnection?.commitText(emoji, 1) }
        keyboardView.onBackspace = { currentInputConnection?.deleteSurroundingText(1, 0) }
        keyboardView.onSpace = { currentInputConnection?.commitText(" ", 1) }
        keyboardView.onEnter = { currentInputConnection?.commitText("\n", 1) }
        keyboardView.onSwipeSuggestions = { suggestions ->
            updateSuggestionBar(bar, suggestions)
        }

        container.addView(topRow)
        container.addView(bar)
        container.addView(keyboardView)

        return container
    }

    private fun updateSuggestionBar(
        bar: LinearLayout,
        suggestions: List<String>
    ) {
        bar.removeAllViews()
        if (suggestions.isEmpty()) {
            bar.visibility = View.GONE
            return
        }

        bar.visibility = View.VISIBLE
        val dp = resources.displayMetrics.density

        suggestions.forEachIndexed { i, word ->
            // Separador vertical entre sugerencias
            if (i > 0) {
                val divider = View(this).apply {
                    setBackgroundColor(Color.parseColor("#D1D1D6"))
                    layoutParams = LinearLayout.LayoutParams(
                        1, (24 * dp).toInt()
                    ).also { it.gravity = Gravity.CENTER_VERTICAL }
                }
                bar.addView(divider)
            }

            val tv = TextView(this).apply {
                text = word
                textSize = 14f
                setTextColor(Color.parseColor("#1C1C1E"))
                gravity = Gravity.CENTER
                typeface = if (i == 0) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
                setOnClickListener {
                    // Reemplazar la palabra ya insertada por la elegida:
                    // El swipe ya insertó la primera sugerencia + espacio.
                    // Si el usuario toca otra, borramos la primera y escribimos la nueva.
                    if (i > 0) {
                        val toDelete = suggestions[0].length + 1 // +1 por el espacio
                        val ic = currentInputConnection ?: return@setOnClickListener
                        ic.beginBatchEdit()
                        repeat(toDelete) { ic.deleteSurroundingText(1, 0) }
                        ic.commitText("$word ", 1)
                        ic.endBatchEdit()
                    }
                    bar.visibility = View.GONE
                }
            }
            bar.addView(tv)
        }

        pendingSuggestions = suggestions
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
}
