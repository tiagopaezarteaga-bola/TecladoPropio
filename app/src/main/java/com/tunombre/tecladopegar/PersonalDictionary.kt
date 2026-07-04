package com.tunombre.tecladopegar

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// PersonalDictionary
//
// Diccionario personal aprendido por uso. Arranca absolutamente vacío.
// Se guarda en <filesDir>/personal_dict.json.
//
// Formato del archivo JSON:
// {
//   "words":   { "hola": 5, "mundo": 3 },
//   "bigrams": { "buenos_dias": 4, "hola_mundo": 2 }
// }
//
// Formato del archivo de exportación (texto plano, una línea por entrada):
// WORD   hola   5
// WORD   mundo  3
// BIGRAM buenos_dias  4
// BIGRAM hola_mundo   2
// ─────────────────────────────────────────────────────────────────────────────

class PersonalDictionary(private val context: Context) {

    /** word -> frecuencia de uso */
    private val words = mutableMapOf<String, Int>()
    /** "prevWord_nextWord" -> frecuencia de co-ocurrencia */
    private val bigrams = mutableMapOf<String, Int>()

    private val file: File get() = File(context.filesDir, "personal_dict.json")

    init { load() }

    // ── API pública ───────────────────────────────────────────────────────────

    /** Verdad si el usuario todavía no ha escrito ninguna palabra. */
    fun isEmpty(): Boolean = words.isEmpty()

    /** Cuántas palabras distintas conoce el diccionario. */
    fun wordCount(): Int = words.size

    /**
     * Registra una palabra completada.
     * [prevWord] = la palabra inmediatamente anterior en la misma sesión,
     * para registrar el bigrama correspondiente.
     */
    fun learnWord(word: String, prevWord: String? = null) {
        val w = word.lowercase().trim()
        if (w.length < 2) return
        words[w] = (words[w] ?: 0) + 1
        if (!prevWord.isNullOrBlank()) {
            val prev = prevWord.lowercase().trim()
            if (prev.length >= 2) {
                val key = "${prev}_${w}"
                bigrams[key] = (bigrams[key] ?: 0) + 1
            }
        }
        save()
    }

    /**
     * Sugerencias para escritura letra a letra.
     * Devuelve hasta [topN] palabras que empiezan con [prefix],
     * ordenadas por (frecuencia + bono de bigrama).
     */
    fun suggest(prefix: String, prevWord: String? = null, topN: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val prev = prevWord?.lowercase()?.trim()

        return words.entries
            .filter { (word, _) -> word.startsWith(p) && word != p }
            .map { (word, freq) ->
                val bigramBonus = if (!prev.isNullOrBlank())
                    (bigrams["${prev}_${word}"] ?: 0) * 3 else 0
                word to (freq + bigramBonus)
            }
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
    }

    /**
     * Sugerencias para swipe (trayectoria de letras).
     * Combina puntuación Levenshtein con frecuencia y bigramas del diccionario personal.
     */
    fun suggestForSwipe(
        swipedLetters: List<Char>,
        prevWord: String? = null,
        topN: Int = 3
    ): List<String> {
        if (swipedLetters.size < 2 || words.isEmpty()) return emptyList()
        val swipe = swipedLetters.joinToString("")
        val prev = prevWord?.lowercase()?.trim()

        return words.entries
            .map { (word, freq) ->
                val swipeScore = swipeScore(swipe, word)
                val bigramBonus = if (!prev.isNullOrBlank())
                    (bigrams["${prev}_${word}"] ?: 0) * 1.5f else 0f
                val freqBonus = freq * 0.5f
                word to (swipeScore - bigramBonus - freqBonus)
            }
            .sortedBy { it.second }
            .take(topN)
            .map { it.first }
    }

    // ── Exportar ──────────────────────────────────────────────────────────────

    /**
     * Exporta el diccionario al Uri proporcionado por el sistema (SAF).
     * El archivo es texto plano, legible y re-importable.
     * Incluye frecuencias y relaciones (bigramas) tal cual están calculadas.
     *
     * Ejemplo de uso:
     *   val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
     *       addCategory(Intent.CATEGORY_OPENABLE)
     *       type = "text/plain"
     *       putExtra(Intent.EXTRA_TITLE, "mi_diccionario.txt")
     *   }
     *   startActivityForResult(intent, REQUEST_EXPORT)
     *
     *   // En onActivityResult:
     *   personalDict.exportTo(context, uri)
     */
    fun exportTo(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val writer = OutputStreamWriter(out, Charsets.UTF_8)
                writer.appendLine("# Diccionario personal TecladoPropio")
                writer.appendLine("# Exportado: ${java.util.Date()}")
                writer.appendLine("# Formato: WORD <palabra> <frecuencia>")
                writer.appendLine("#          BIGRAM <palabra_anterior_siguiente> <frecuencia>")
                writer.appendLine("#")
                writer.appendLine("# PALABRAS (${words.size})")
                words.entries
                    .sortedByDescending { it.value }
                    .forEach { (word, freq) ->
                        writer.appendLine("WORD\t$word\t$freq")
                    }
                writer.appendLine("#")
                writer.appendLine("# BIGRAMAS — relaciones entre palabras (${bigrams.size})")
                bigrams.entries
                    .sortedByDescending { it.value }
                    .forEach { (pair, freq) ->
                        writer.appendLine("BIGRAM\t$pair\t$freq")
                    }
                writer.flush()
            }
            true
        } catch (_: Exception) { false }
    }

    /**
     * Importa un diccionario desde un Uri (SAF).
     * Las frecuencias se SUMAN a las existentes (merge), no se reemplazan.
     * Devuelve el número de entradas importadas (palabras + bigramas).
     */
    fun importFrom(context: Context, uri: Uri): Int {
        var count = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                BufferedReader(InputStreamReader(inp, Charsets.UTF_8)).forEachLine { line ->
                    if (line.startsWith("#") || line.isBlank()) return@forEachLine
                    val parts = line.trim().split("\t")
                    when {
                        parts.size == 3 && parts[0] == "WORD" -> {
                            val word = parts[1].lowercase().trim()
                            val freq = parts[2].toIntOrNull() ?: 1
                            if (word.length >= 2) {
                                words[word] = (words[word] ?: 0) + freq
                                count++
                            }
                        }
                        parts.size == 3 && parts[0] == "BIGRAM" -> {
                            val key = parts[1].trim()
                            val freq = parts[2].toIntOrNull() ?: 1
                            if (key.contains("_")) {
                                bigrams[key] = (bigrams[key] ?: 0) + freq
                                count++
                            }
                        }
                    }
                }
            }
            if (count > 0) save()
        } catch (_: Exception) {}
        return count
    }

    /**
     * Borra completamente el diccionario (palabras + bigramas).
     */
    fun clear() {
        words.clear()
        bigrams.clear()
        save()
    }

    // ── Persistencia interna ──────────────────────────────────────────────────

    private fun save() {
        try {
            val wordsJson = JSONObject()
            words.forEach { (k, v) -> wordsJson.put(k, v) }
            val bigramsJson = JSONObject()
            bigrams.forEach { (k, v) -> bigramsJson.put(k, v) }
            val root = JSONObject()
            root.put("words", wordsJson)
            root.put("bigrams", bigramsJson)
            file.writeText(root.toString())
        } catch (_: Exception) {}
    }

    private fun load() {
        words.clear()
        bigrams.clear()
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val wordsJson = root.optJSONObject("words") ?: return
            wordsJson.keys().forEach { k -> words[k] = wordsJson.getInt(k) }
            val bigramsJson = root.optJSONObject("bigrams") ?: return
            bigramsJson.keys().forEach { k -> bigrams[k] = bigramsJson.getInt(k) }
        } catch (_: Exception) {}
    }

    // ── Helpers de puntuación swipe ───────────────────────────────────────────

    private fun swipeScore(swipe: String, word: String): Float {
        if (word.isEmpty()) return Float.MAX_VALUE
        val key = buildKey(word)
        val lev = levenshtein(swipe, key).toFloat()
        val lenDiff = abs(word.length - swipe.length) * 0.5f
        return lev + lenDiff
    }

    private fun buildKey(word: String): String {
        if (word.length <= 2) return word
        val mid = word.substring(1, word.length - 1).toList().distinct().joinToString("")
        return "${word.first()}$mid${word.last()}"
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
            else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }
}
