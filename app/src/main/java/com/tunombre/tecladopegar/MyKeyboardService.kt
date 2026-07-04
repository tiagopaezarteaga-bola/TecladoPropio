package com.tunombre.tecladopegar

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button

class MyKeyboardService : InputMethodService() {

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null)

        keyboardView.findViewById<Button>(R.id.btnPaste).setOnClickListener {
            pasteFullClipboard()
        }

        keyboardView.findViewById<Button>(R.id.btnSwitchKeyboard).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        return keyboardView
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
