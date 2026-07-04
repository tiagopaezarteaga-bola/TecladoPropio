package com.tunombre.tecladopegar

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// PersonalDictionary — v2
//
// Aprende de TODAS las formas de escritura:
//
//   words      : frecuencia de cada palabra completa (cliqueo y swipe)
//   bigrams    : pares de palabras consecutivas  "hola_mundo" → 4
//   letterNgrams: pares/tríos de letras consecutivas dentro de palabras
//                 "ho", "ol", "la", "hol", "ola" → refuerzan autocompletado
//   swipePaths : secuencias de teclas recorridas en swipe para cada palabra
//                "hola" → ["hloa","hola","hola"] (distintos intentos)
//   gestureStats: por palabra, métricas de gesto promedio
//                 {"avg_len_px": 340.5, "avg_speed_px_ms": 2.1, "samples": 3}
//
// Formato JSON interno  (personal_dict.json):
// {
//   "words":       { "hola": 5 },
//   "bigrams":     { "hola_mundo": 2 },
//   "letterNgrams":{ "ho": 12, "ol": 10, "hol": 6 },
//   "swipePaths":  { "hola": ["hloa","hola"] },
//   "gestureStats":{ "hola": {"avg_len":340.5,"avg_spd":2.1,"n":3} }
// }
//
// Formato de exportación (texto plano, re-importable):
// WORD    hola    5
// BIGRAM  hola_mundo    2
// NGRAM   ho    12
// NGRAM   hol   6
// SWIPE   hola  hloa|hola
// GESTURE hola  avg_len=340.5 avg_spd=2.1 n=3
// ─────────────────────────────────────────────────────────────────────────────

class PersonalDictionary(private val context: Context) {

    // ── Almacenes ─────────────────────────────────────────────────────────────
    private val words        = mutableMapOf<String, Int>()
    private val bigrams      = mutableMapOf<String, Int>()
    private val letterNgrams = mutableMapOf<String, Int>()
    private val swipePaths   = mutableMapOf<String, MutableList<String>>()  // word -> lista de paths observados
    private val gestureStats = mutableMapOf<String, GestureStat>()

    private data class GestureStat(
        var avgLenPx: Float,
        var avgSpdPxMs: Float,
        var samples: Int
    )

    private val file: File get() = File(context.filesDir, "personal_dict.json")

    init { load() }

    // ── API pública ───────────────────────────────────────────────────────────

    fun isEmpty(): Boolean = words.isEmpty()
    fun wordCount(): Int   = words.size

    /**
     * Registra una palabra escrita letra a letra (cliqueo normal).
     * [prevWord] para bigrama. No hay datos de gesto.
     */
    fun learnWord(word: String, prevWord: String? = null) {
        val w = word.lowercase().trim()
        if (w.length < 2) return
        incrementWord(w)
        recordBigram(prevWord, w)
        recordLetterNgrams(w)
        save()
    }

    /**
     * Registra una palabra producida por swipe, con la trayectoria de letras
     * y las métricas del gesto (longitud total en px, duración en ms).
     *
     * @param word        La palabra reconocida (ej: "hola")
     * @param pathChars   Letras recorridas durante el swipe (ej: ['h','l','a'])
     * @param totalLenPx  Longitud total de la trayectoria en píxeles
     * @param durationMs  Duración del gesto en milisegundos
     * @param prevWord    Palabra anterior para bigrama
     */
    fun learnSwipeWord(
        word: String,
        pathChars: List<Char>,
        totalLenPx: Float,
        durationMs: Long,
        prevWord: String? = null
    ) {
        val w = word.lowercase().trim()
        if (w.length < 2) return

        incrementWord(w)
        recordBigram(prevWord, w)
        recordLetterNgrams(w)

        // Guardar path de swipe (máx 10 muestras por palabra)
        val pathStr = pathChars.joinToString("")
        val paths = swipePaths.getOrPut(w) { mutableListOf() }
        if (paths.size < 10) paths.add(pathStr)

        // Actualizar estadísticas de gesto (media acumulativa)
        if (totalLenPx > 0f && durationMs > 0L) {
            val spd = totalLenPx / durationMs
            val existing = gestureStats[w]
            if (existing == null) {
                gestureStats[w] = GestureStat(totalLenPx, spd, 1)
            } else {
                val n = existing.samples.toFloat()
                existing.avgLenPx   = (existing.avgLenPx * n + totalLenPx) / (n + 1)
                existing.avgSpdPxMs = (existing.avgSpdPxMs * n + spd) / (n + 1)
                existing.samples++
            }
        }

        save()
    }

    /**
     * Sugerencias para escritura letra a letra.
     * Combina: frecuencia de palabra + bigrama + n-gramas de letras.
     */
    fun suggest(prefix: String, prevWord: String? = null, topN: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val prev = prevWord?.lowercase()?.trim()

        return words.entries
            .filter { (word, _) -> word.startsWith(p) && word != p }
            .map { (word, freq) ->
                val bigramBonus  = if (!prev.isNullOrBlank()) (bigrams["${prev}_${word}"] ?: 0) * 3 else 0
                val ngramBonus   = ngramScore(p, word)
                word to (freq + bigramBonus + ngramBonus)
            }
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
    }

    /**
     * Sugerencias para swipe. Combina Levenshtein sobre la trayectoria
     * con frecuencia, bigramas y paths de swipe aprendidos.
     */
    fun suggestForSwipe(
        swipedLetters: List<Char>,
        prevWord: String? = null,
        topN: Int = 3
    ): List<String> {
        if (swipedLetters.size < 2 || words.isEmpty()) return emptyList()
        val swipe = swipedLetters.joinToString("")
        val prev  = prevWord?.lowercase()?.trim()

        return words.entries
            .map { (word, freq) ->
                val baseLev      = swipeScore(swipe, word)
                // Bonus si algún path aprendido coincide bien con este swipe
                val learnedBonus = bestPathBonus(swipe, word)
                val bigramBonus  = if (!prev.isNullOrBlank()) (bigrams["${prev}_${word}"] ?: 0) * 1.5f else 0f
                val freqBonus    = freq * 0.5f
                word to (baseLev - learnedBonus - bigramBonus - freqBonus)
            }
            .sortedBy { it.second }
            .take(topN)
            .map { it.first }
    }

    // ── Exportar ──────────────────────────────────────────────────────────────

    fun exportTo(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val w = OutputStreamWriter(out, Charsets.UTF_8)
                w.appendLine("# Diccionario personal TecladoPropio v2")
                w.appendLine("# Exportado: ${java.util.Date()}")
                w.appendLine("#")
                w.appendLine("# PALABRAS (${words.size})")
                w.appendLine("# Formato: WORD <palabra> <frecuencia>")
                words.entries.sortedByDescending { it.value }.forEach { (word, freq) ->
                    w.appendLine("WORD\t$word\t$freq")
                }
                w.appendLine("#")
                w.appendLine("# BIGRAMAS — pares de palabras consecutivas (${bigrams.size})")
                w.appendLine("# Formato: BIGRAM <prev_next> <frecuencia>")
                bigrams.entries.sortedByDescending { it.value }.forEach { (pair, freq) ->
                    w.appendLine("BIGRAM\t$pair\t$freq")
                }
                w.appendLine("#")
                w.appendLine("# N-GRAMAS DE LETRAS — pares/tríos dentro de palabras (${letterNgrams.size})")
                w.appendLine("# Formato: NGRAM <secuencia> <frecuencia>")
                letterNgrams.entries.sortedByDescending { it.value }.forEach { (ng, freq) ->
                    w.appendLine("NGRAM\t$ng\t$freq")
                }
                w.appendLine("#")
                w.appendLine("# TRAYECTORIAS DE SWIPE por palabra (${swipePaths.size} palabras)")
                w.appendLine("# Formato: SWIPE <palabra> <path1|path2|...>")
                swipePaths.entries.forEach { (word, paths) ->
                    w.appendLine("SWIPE\t$word\t${paths.joinToString("|")}")
                }
                w.appendLine("#")
                w.appendLine("# ESTADÍSTICAS DE GESTO por palabra (${gestureStats.size} palabras)")
                w.appendLine("# Formato: GESTURE <palabra> avg_len=<px> avg_spd=<px/ms> n=<muestras>")
                gestureStats.entries.forEach { (word, stat) ->
                    w.appendLine("GESTURE\t$word\tavg_len=${"%.1f".format(stat.avgLenPx)}\tavg_spd=${"%.3f".format(stat.avgSpdPxMs)}\tn=${stat.samples}")
                }
                w.flush()
            }
            true
        } catch (_: Exception) { false }
    }

    /**
     * Importa un diccionario desde Uri (merge, no reemplaza).
     * Devuelve el número de entradas importadas.
     */
    fun importFrom(context: Context, uri: Uri): Int {
        var count = 0
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                BufferedReader(InputStreamReader(inp, Charsets.UTF_8)).forEachLine { line ->
                    if (line.startsWith("#") || line.isBlank()) return@forEachLine
                    val parts = line.trim().split("\t")
                    when {
                        parts.size >= 3 && parts[0] == "WORD" -> {
                            val word = parts[1].lowercase().trim()
                            val freq = parts[2].toIntOrNull() ?: 1
                            if (word.length >= 2) { words[word] = (words[word] ?: 0) + freq; count++ }
                        }
                        parts.size >= 3 && parts[0] == "BIGRAM" -> {
                            val key = parts[1].trim()
                            val freq = parts[2].toIntOrNull() ?: 1
                            if (key.contains("_")) { bigrams[key] = (bigrams[key] ?: 0) + freq; count++ }
                        }
                        parts.size >= 3 && parts[0] == "NGRAM" -> {
                            val ng = parts[1].trim()
                            val freq = parts[2].toIntOrNull() ?: 1
                            if (ng.isNotBlank()) { letterNgrams[ng] = (letterNgrams[ng] ?: 0) + freq; count++ }
                        }
                        parts.size >= 3 && parts[0] == "SWIPE" -> {
                            val word = parts[1].lowercase().trim()
                            val paths = parts[2].split("|").filter { it.isNotBlank() }
                            if (word.length >= 2 && paths.isNotEmpty()) {
                                val existing = swipePaths.getOrPut(word) { mutableListOf() }
                                paths.forEach { if (existing.size < 10) existing.add(it) }
                                count++
                            }
                        }
                        parts.size >= 5 && parts[0] == "GESTURE" -> {
                            val word = parts[1].lowercase().trim()
                            val len  = parts[2].removePrefix("avg_len=").toFloatOrNull()
                            val spd  = parts[3].removePrefix("avg_spd=").toFloatOrNull()
                            val n    = parts[4].removePrefix("n=").toIntOrNull()
                            if (word.length >= 2 && len != null && spd != null && n != null) {
                                gestureStats[word] = GestureStat(len, spd, n)
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

    fun clear() {
        words.clear(); bigrams.clear(); letterNgrams.clear()
        swipePaths.clear(); gestureStats.clear()
        save()
    }

    // ── Persistencia ──────────────────────────────────────────────────────────

    private fun save() {
        try {
            val root = JSONObject()

            val wj = JSONObject(); words.forEach { (k, v) -> wj.put(k, v) }
            root.put("words", wj)

            val bj = JSONObject(); bigrams.forEach { (k, v) -> bj.put(k, v) }
            root.put("bigrams", bj)

            val nj = JSONObject(); letterNgrams.forEach { (k, v) -> nj.put(k, v) }
            root.put("letterNgrams", nj)

            val spj = JSONObject()
            swipePaths.forEach { (word, paths) ->
                val arr = JSONArray(); paths.forEach { arr.put(it) }
                spj.put(word, arr)
            }
            root.put("swipePaths", spj)

            val gsj = JSONObject()
            gestureStats.forEach { (word, stat) ->
                val obj = JSONObject()
                obj.put("len", stat.avgLenPx.toDouble())
                obj.put("spd", stat.avgSpdPxMs.toDouble())
                obj.put("n", stat.samples)
                gsj.put(word, obj)
            }
            root.put("gestureStats", gsj)

            file.writeText(root.toString())
        } catch (_: Exception) {}
    }

    private fun load() {
        words.clear(); bigrams.clear(); letterNgrams.clear()
        swipePaths.clear(); gestureStats.clear()
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())

            root.optJSONObject("words")?.keys()?.forEach { k ->
                words[k] = root.getJSONObject("words").getInt(k)
            }
            root.optJSONObject("bigrams")?.keys()?.forEach { k ->
                bigrams[k] = root.getJSONObject("bigrams").getInt(k)
            }
            root.optJSONObject("letterNgrams")?.keys()?.forEach { k ->
                letterNgrams[k] = root.getJSONObject("letterNgrams").getInt(k)
            }
            root.optJSONObject("swipePaths")?.keys()?.forEach { k ->
                val arr = root.getJSONObject("swipePaths").getJSONArray(k)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                swipePaths[k] = list
            }
            root.optJSONObject("gestureStats")?.keys()?.forEach { k ->
                val obj = root.getJSONObject("gestureStats").getJSONObject(k)
                gestureStats[k] = GestureStat(
                    obj.getDouble("len").toFloat(),
                    obj.getDouble("spd").toFloat(),
                    obj.getInt("n")
                )
            }
        } catch (_: Exception) {}
    }

    // ── Helpers internos ──────────────────────────────────────────────────────

    private fun incrementWord(w: String) {
        words[w] = (words[w] ?: 0) + 1
    }

    private fun recordBigram(prevWord: String?, word: String) {
        if (!prevWord.isNullOrBlank()) {
            val prev = prevWord.lowercase().trim()
            if (prev.length >= 2) {
                val key = "${prev}_${word}"
                bigrams[key] = (bigrams[key] ?: 0) + 1
            }
        }
    }

    private fun recordLetterNgrams(word: String) {
        // Bigramas y trigramas de letras
        for (i in word.indices) {
            if (i + 1 < word.length) {
                val bg = word.substring(i, i + 2)
                letterNgrams[bg] = (letterNgrams[bg] ?: 0) + 1
            }
            if (i + 2 < word.length) {
                val tg = word.substring(i, i + 3)
                letterNgrams[tg] = (letterNgrams[tg] ?: 0) + 1
            }
        }
    }

    /** Bonus de score cuando un path aprendido se asemeja al swipe actual. */
    private fun bestPathBonus(swipe: String, word: String): Float {
        val paths = swipePaths[word] ?: return 0f
        if (paths.isEmpty()) return 0f
        val best = paths.minOf { levenshtein(swipe, it) }
        return if (best == 0) 3f else if (best == 1) 1.5f else 0f
    }

    /** Score para comparar swipe con palabra (menor = mejor). */
    private fun ngramScore(prefix: String, word: String): Int {
        // Cuántos n-gramas del prefijo aparecen en el diccionario de n-gramas
        var score = 0
        for (i in prefix.indices) {
            if (i + 1 < prefix.length) score += letterNgrams["${prefix[i]}${prefix[i+1]}"] ?: 0
            if (i + 2 < prefix.length) score += (letterNgrams["${prefix[i]}${prefix[i+1]}${prefix[i+2]}"] ?: 0) * 2
        }
        return score
    }

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
