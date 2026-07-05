package com.tunombre.tecladopegar

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.OutputStream

/**
 * Activity transparente auxiliar para exportar/importar el diccionario.
 *
 * ─ EXPORTAR ─────────────────────────────────────────────────────────────────
 * Escribe el .txt en la carpeta pública Descargas del dispositivo:
 *   - Android 10+ (API 29+): vía MediaStore (scoped storage, sin permisos).
 *   - Android 7-9 (API 24-28): directo a Environment.DIRECTORY_DOWNLOADS,
 *     solicitando el permiso WRITE_EXTERNAL_STORAGE si hace falta.
 * Muestra el nombre del archivo al usuario mediante Toast.
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
        private const val REQ_WRITE_PERMISSION = 1003
    }

    private lateinit var personalDict: PersonalDictionary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        personalDict = PersonalDictionary(this)

        when (intent?.action) {
            ACTION_EXPORT -> startExportFlow()
            ACTION_IMPORT -> launchImportPicker()
            else -> finish()
        }
    }

    // ── Exportar a la carpeta pública Descargas ───────────────────────────────

    private fun startExportFlow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage: MediaStore no necesita permiso en tiempo de ejecución.
            doExport()
        } else if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            doExport()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQ_WRITE_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_WRITE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doExport()
            } else {
                showToast("Permiso de almacenamiento denegado. No se pudo exportar a Descargas.")
                finish()
            }
        }
    }

    private fun doExport() {
        val fileName = PersonalDictionary.EXPORT_FILE_NAME
        val content = personalDict.buildExportContent()

        val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(fileName, content)
        } else {
            exportViaLegacyFile(fileName, content)
        }

        if (ok) {
            val count = personalDict.wordCount()
            showToast(
                "Diccionario exportado ($count palabras)\n" +
                "Archivo: $fileName\n" +
                "Carpeta: Descargas"
            )
        } else {
            showToast("Error al exportar el diccionario")
        }
        finish()
    }

    /** Android 10+ (API 29+): escribe en Descargas vía MediaStore, sin permisos. */
    private fun exportViaMediaStore(fileName: String, content: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = applicationContext.contentResolver
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            val stream: OutputStream = resolver.openOutputStream(uri) ?: return false
            stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Android 7-9 (API 24-28): escribe directamente en la carpeta pública Descargas. */
    private fun exportViaLegacyFile(fileName: String, content: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val outFile = File(downloadsDir, fileName)
            outFile.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            true
        } catch (_: Exception) {
            false
        }
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
