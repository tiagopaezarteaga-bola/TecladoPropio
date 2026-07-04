package com.tunombre.tecladopegar

import android.app.AlertDialog
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

    // Última palabra completada (para bigramas)
    private var lastCompletedWord: String? = null

    // Estado de sugerencias de swipe (para reemplazar si el usuario elige otra)
    private var lastSwipeSuggestions: List<String> = emptyList()
    private var swipeInsertedLength: Int = 0

    // Códigos de request para el selector de archivos (SAF)
    // El IME no puede usar startActivityForResult directamente;
    // usamos un Activity auxiliar que el usuario invoca desde el diálogo.
    // Ver DictManagerActivity al final de este archivo.

    override fun onCreateInputView(): View {
        personalDict = PersonalDictionary(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

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
        topRow.addView(makeTopButton("📋 Pegar") { pasteFullClipboard() })
        topRow.addView(makeTopButton("⌨️ Teclados") {
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
            currentInputConnection?.commitText(c.toString(), 1)
            // Tras punto/signo de cierre → mayúscula inicial de oración
            if (c == '.' || c == '!' || c == '?') {
                keyboardView.resetToSentenceCase()
            }
        }
        keyboardView.onEmojiTyped = { emoji ->
            currentInputConnection?.commitText(emoji, 1)
        }
        keyboardView.onBackspace = {
            currentInputConnection?.deleteSurroundingText(1, 0)
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
        keyboardView.onSwipeSuggestions = { suggestions ->
            lastSwipeSuggestions = suggestions
            swipeInsertedLength = if (suggestions.isNotEmpty()) suggestions[0].length + 1 else 0
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
    // Sugerencias mientras se escribe letra a letra
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateTypingSuggestions() {
        if (personalDict.isEmpty()) { hideSuggestions(); return }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        val partial = before.trimEnd().substringAfterLast(' ').substringAfterLast('\n')
        if (partial.length < 1) { hideSuggestions(); return }

        val prevWord = before.trimEnd()
            .substringBeforeLast(' ').trimEnd()
            .substringAfterLast(' ').substringAfterLast('\n')
            .takeIf { it.isNotBlank() }

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
            bar.addView(makeSuggestionView(word, bold = i == 0) {
                val ic = currentInputConnection ?: return@makeSuggestionView
                ic.beginBatchEdit()
                repeat(partial.length) { ic.deleteSurroundingText(1, 0) }
                ic.commitText("$word ", 1)
                ic.endBatchEdit()
                personalDict.learnWord(word, lastCompletedWord)
                lastCompletedWord = word
                hideSuggestions()
            })
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
            bar.addView(makeSuggestionView(word, bold = i == 0) {
                if (i > 0) {
                    val ic = currentInputConnection ?: return@makeSuggestionView
                    ic.beginBatchEdit()
                    repeat(swipeInsertedLength) { ic.deleteSurroundingText(1, 0) }
                    ic.commitText("$word ", 1)
                    ic.endBatchEdit()
                    personalDict.learnWord(word, lastCompletedWord)
                    lastCompletedWord = word
                }
                hideSuggestions()
            })
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Menú del diccionario
    // ─────────────────────────────────────────────────────────────────────────

    private fun showDictMenu() {
        val wordCount = personalDict.wordCount()
        val title = if (wordCount == 0) "Diccionario (vacío)"
                    else "Diccionario ($wordCount palabras)"

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(title)
            .setItems(arrayOf(
                "📤  Exportar diccionario",
                "📥  Importar diccionario",
                "🗑️  Borrar todo el diccionario"
            )) { _, which ->
                when (which) {
                    0 -> launchExport()
                    1 -> launchImport()
                    2 -> confirmClear()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
            .window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
    }

    private fun launchExport() {
        // Lanzar DictManagerActivity para que maneje el SAF
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
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("Borrar diccionario")
            .setMessage("¿Borrar todas las palabras aprendidas? Esta acción no se puede deshacer.")
            .setPositiveButton("Borrar") { _, _ ->
                personalDict.clear()
                Toast.makeText(this, "Diccionario borrado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
            .window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
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

    private fun makeSuggestionView(word: String, bold: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = word
            textSize = 14f
            setTextColor(Color.parseColor("#1C1C1E"))
            gravity = Gravity.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { onClick() }
        }
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

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lastCompletedWord = null
        hideSuggestions()
        // Mayúscula inicial al abrir un campo de texto nuevo
        activeKeyboardView?.resetToSentenceCase()
    }
}
