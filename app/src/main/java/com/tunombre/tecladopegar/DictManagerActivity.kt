package com.tunombre.tecladopegar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Activity transparente auxiliar para manejar exportar/importar el diccionario.
 *
 * Los InputMethodService no pueden usar startActivityForResult directamente.
 * Esta Activity actúa de puente: recibe la acción, lanza el selector de archivos
 * del sistema (SAF), y devuelve el resultado a PersonalDictionary.
 *
 * Para que funcione, agregar en AndroidManifest.xml dentro de <application>:
 *
 *   <activity
 *       android:name=".DictManagerActivity"
 *       android:theme="@android:style/Theme.Translucent.NoTitleBar"
 *       android:exported="false" />
 */
class DictManagerActivity : Activity() {

    companion object {
        const val ACTION_EXPORT = "com.tunombre.tecladopegar.ACTION_DICT_EXPORT"
        const val ACTION_IMPORT = "com.tunombre.tecladopegar.ACTION_DICT_IMPORT"
        private const val REQ_EXPORT = 1001
        private const val REQ_IMPORT = 1002
    }

    private lateinit var personalDict: PersonalDictionary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personalDict = PersonalDictionary(this)

        when (intent?.action) {
            ACTION_EXPORT -> {
                val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "diccionario_teclado.txt")
                }
                startActivityForResult(exportIntent, REQ_EXPORT)
            }
            ACTION_IMPORT -> {
                val importIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                }
                startActivityForResult(importIntent, REQ_IMPORT)
            }
            else -> finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) { finish(); return }
        val uri: Uri = data?.data ?: run { finish(); return }

        when (requestCode) {
            REQ_EXPORT -> {
                val ok = personalDict.exportTo(this, uri)
                if (ok) {
                    val count = personalDict.wordCount()
                    Toast.makeText(
                        this,
                        "Diccionario exportado ($count palabras)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "Error al exportar", Toast.LENGTH_SHORT).show()
                }
            }
            REQ_IMPORT -> {
                val count = personalDict.importFrom(this, uri)
                if (count > 0) {
                    Toast.makeText(
                        this,
                        "Importadas $count entradas",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "No se encontraron entradas válidas en el archivo",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        finish()
    }
}
