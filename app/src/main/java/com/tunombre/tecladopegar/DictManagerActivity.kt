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
 * CORRECCIÓN P1: en lugar de intentar abrir el selector SAF desde el IME (que crashea),
 * esta Activity se encarga de todo:
 *   - Exportar: escribe el archivo directamente en filesDir con exportDirectToFile()
 *     y muestra la ruta al usuario mediante Toast. No usa SAF en absoluto.
 *   - Importar: abre el picker SAF desde AQUÍ (contexto de Activity válido),
 *     lo que sí está permitido.
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

    // ── Exportar directamente en filesDir (sin SAF, sin crash) ───────────────

    private fun doExport() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(filesDir, "diccionario_$timestamp.txt")

        val ok = personalDict.exportDirectToFile(outFile)

        if (ok) {
            val count = personalDict.wordCount()
            Toast.makeText(
                this,
                "Diccionario exportado ($count palabras)\nArchivo: ${outFile.name}\nCarpeta: ${filesDir.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Error al exportar el diccionario", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    // ── Importar con SAF (abierto desde Activity: contexto válido) ───────────

    private fun launchImportPicker() {
        val importIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(importIntent, REQ_IMPORT)
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
            Toast.makeText(this, "Importadas $count entradas", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "No se encontraron entradas válidas en el archivo",
                Toast.LENGTH_SHORT
            ).show()
        }
        finish()
    }
}
