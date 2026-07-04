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
 * Escribe el .txt directamente en filesDir sin SAF (evita el crash del picker
 * cuando se lanza desde el IME). Luego abre un Intent de compartir (ACTION_SEND)
 * para que el usuario pueda guardarlo o enviarlo donde quiera.
 *
 * ─ IMPORTAR ─────────────────────────────────────────────────────────────────
 * Abre el picker SAF desde AQUÍ (contexto de Activity real, no desde el IME),
 * lo que está permitido sin restricciones.
 *
 * En AndroidManifest.xml, dentro de <application>:
 *
 *   <activity
 *       android:name=".DictManagerActivity"
 *       android:theme="@android:style/Theme.Translucent.NoTitleBar"
 *       android:exported="false" />
 *
 * Si compilas para Android 7+ (API 24+), añade también un FileProvider en el
 * Manifest para poder compartir el archivo con ACTION_SEND:
 *
 *   <provider
 *       android:name="androidx.core.content.FileProvider"
 *       android:authorities="${applicationId}.fileprovider"
 *       android:exported="false"
 *       android:grantUriPermissions="true">
 *       <meta-data
 *           android:name="android.support.FILE_PROVIDER_PATHS"
 *           android:resource="@xml/file_paths" />
 *   </provider>
 *
 * Y en res/xml/file_paths.xml:
 *   <paths>
 *       <files-path name="dict" path="." />
 *   </paths>
 */
class DictManagerActivity : Activity() {

    companion object {
        const val ACTION_EXPORT = "com.tunombre.tecladopegar.ACTION_DICT_EXPORT"
        const val ACTION_IMPORT = "com.tunombre.tecladopegar.ACTION_DICT_IMPORT"
        private const val REQ_IMPORT = 1002
        private const val REQ_SHARE  = 1003
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

    // ── Exportar ─────────────────────────────────────────────────────────────

    private fun doExport() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(filesDir, "diccionario_$timestamp.txt")

        val ok = personalDict.exportDirectToFile(outFile)

        if (!ok) {
            showToast("Error al exportar el diccionario")
            finish()
            return
        }

        val count = personalDict.wordCount()

        // Intentar compartir con FileProvider (API 24+)
        // Si no está configurado el FileProvider, hacemos fallback con el path.
        val shared = tryShareFile(outFile)
        if (!shared) {
            // Fallback: informar la ruta directamente
            showToast(
                "Diccionario exportado ($count palabras)\n" +
                "Archivo guardado en:\n${outFile.absolutePath}"
            )
            finish()
        }
        // Si se pudo compartir, la Activity cierra tras volver del share sheet
    }

    private fun tryShareFile(file: File): Boolean {
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Diccionario TecladoPropio")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(
                Intent.createChooser(shareIntent, "Guardar / compartir diccionario"),
                REQ_SHARE
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Importar ─────────────────────────────────────────────────────────────

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

    // ── Resultados ────────────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQ_SHARE -> {
                // El usuario volvió del share sheet (haya compartido o no)
                finish()
            }
            REQ_IMPORT -> {
                if (resultCode != RESULT_OK) { finish(); return }
                val uri: Uri = data?.data ?: run { finish(); return }
                val count = personalDict.importFrom(this, uri)
                if (count > 0) {
                    showToast("Importadas $count entradas")
                } else {
                    showToast("No se encontraron entradas válidas en el archivo")
                }
                finish()
            }
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun showToast(msg: String) {
        try {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {}
    }
}
