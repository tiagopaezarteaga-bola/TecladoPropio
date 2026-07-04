package com.tunombre.tecladopegar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity transparente auxiliar para exportar/importar el diccionario.
 *
 * ─ EXPORTAR ─────────────────────────────────────────────────────────────────
 * Escribe el .txt directamente en filesDir sin SAF ni FileProvider.
 * Muestra la ruta del archivo al usuario mediante Toast.
 *
 * ─ IMPORTAR ─────────────────────────────────────────────────────────────────
 * Abre el picker SAF desde AQUÍ (contexto de Activity real, no desde el IME).
 *
 * En AndroidManifest.xml:
 *   <activity
 *       android:name=".DictManagerActivity"
 *       android:theme="@android:style/Theme.Translucent.NoTitleBar"
 *       android:exported="false" />
 */
class DictManagerActivity : Activity() {

    companion object {
        const val ACTION_EXPORT = "com.tunombre.tecladopegar.ACTION_DICT_EXPORT"
        const val ACTION_IMPORT = "com.tunombre.tecladopegar.ACTION_DICT_IMPORT"
        private const val REQ_IMPORT = 1002
    }

    private lateinit var personalDict: PersonalDictionary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personalDict = PersonalDictionary(this)

        when (intent?.action) {
            ACTION_EXPORT -> doExport()
            ACTION_IMPORT -> launchImportPicker()
            else -> finish()
        }
    }

    // ── Exportar directamente en filesDir (sin SAF, sin FileProvider) ─────────

    private fun doExport() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(filesDir, "diccionario_$timestamp.txt")

        val ok = personalDict.exportDirectToFile(outFile)

        if (ok) {
            val count = personalDict.wordCount()
            showToast(
                "Diccionario exportado ($count palabras)\n" +
                "Archivo: ${outFile.name}\n" +
                "Carpeta: ${filesDir.absolutePath}"
            )
        } else {
            showToast("Error al exportar el diccionario")
        }
        finish()
    }

    // ── Importar con SAF (abierto desde Activity: contexto válido) ────────────

    private fun launchImportPicker() {
        try {
            val importIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
            }
            startActivityForResult(importIntent, REQ_IMPORT)
        } catch (e: Exception) {
            showToast("No se pudo abrir el selector de archivos")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || requestCode != REQ_IMPORT) {
            finish()
            return
        }
        val uri: Uri = data?.data ?: run { finish(); return }

        val count = personalDict.importFrom(this, uri)
        if (count > 0) {
            showToast("Importadas $count entradas")
        } else {
            showToast("No se encontraron entradas válidas en el archivo")
        }
        finish()
    }

    private fun showToast(msg: String) {
        try {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {}
    }
}
