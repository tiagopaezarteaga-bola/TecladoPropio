package com.tunombre.tecladopegar

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class MyKeyboardService : InputMethodService() {

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Fila superior: Pegar + cambiar teclado
        val topRow = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        topRow.findViewById<Button>(R.id.btnPaste).setOnClickListener { pasteFullClipboard() }
        topRow.findViewById<Button>(R.id.btnSwitchKeyboard).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        // Teclado con 3 estados: letras, números, símbolos
        val keyboardView = KeyboardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (280 * resources.displayMetrics.density).toInt()
            )
            onLetterTyped = { letter -> currentInputConnection?.commitText(letter.toString(), 1) }
            onCharTyped = { c -> currentInputConnection?.commitText(c.toString(), 1) }
            onBackspace = { currentInputConnection?.deleteSurroundingText(1, 0) }
            onSpace = { currentInputConnection?.commitText(" ", 1) }
            onEnter = { currentInputConnection?.commitText("\n", 1) }
            onEmojiLongPress = {
                // Pendiente: aquí se abrirá el selector de emojis real.
                Toast.makeText(this@MyKeyboardService, "Selector de emojis: próximamente", Toast.LENGTH_SHORT).show()
            }
        }

        container.addView(topRow)
        container.addView(keyboardView)

        return container
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
