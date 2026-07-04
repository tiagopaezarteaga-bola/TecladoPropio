package com.tunombre.tecladopegar

import android.content.Context
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// PersonalDictionary
//
// Diccionario personal que aprende del uso. Arranca vacío.
// Cada palabra completada (al pulsar espacio o enter) se guarda con su
// frecuencia. Los bigramas registran qué palabra suele seguir a cuál.
//
// Archivo en disco: <filesDir>/personal_dict.json
// Formato JSON:
// {
//   "words": { "hola": 5, "mundo": 3, ... },
//   "bigrams": { "buenos_dias": 4, "hola_mundo": 2, ... }
// }
// ─────────────────────────────────────────────────────────────────────────────

class PersonalDictionary(private val context: Context) {

    // word -> frecuencia de uso
    private val words = mutableMapOf<String, Int>()
    // "prevWord_nextWord" -> frecuencia
    private val bigrams = mutableMapOf<String, Int>()

    private val file: File get() = File(context.filesDir, "personal_dict.json")

    // ── Inicialización ────────────────────────────────────────────────────────

    init {
        load()
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Registrar una palabra completada. [prevWord] es la palabra anterior
     * en el mismo campo (si existe), para guardar el bigrama.
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
     * Sugerencias para la palabra parcial [prefix].
     * Devuelve hasta [topN] palabras ordenadas por relevancia.
     * [prevWord] se usa para bonificar con bigramas.
     */
    fun suggest(prefix: String, prevWord: String? = null, topN: Int = 3): List<String> {
        if (prefix.length < 1) return emptyList()
        val p = prefix.lowercase()
        val prev = prevWord?.lowercase()?.trim()

        return words.entries
            .filter { (word, _) -> word.startsWith(p) }
            .map { (word, freq) ->
                val bigramBonus = if (!prev.isNullOrBlank()) {
                    (bigrams["${prev}_${word}"] ?: 0) * 3
                } else 0
                val score = freq + bigramBonus
                word to score
            }
            .sortedByDescending { it.second }
            .take(topN)
            .map { it.first }
    }

    /**
     * Sugerencias para swipe (trayectoria de letras).
     * Combina el diccionario personal con el diccionario estático de SwipePredictor.
     * Las palabras personales tienen prioridad.
     */
    fun suggestForSwipe(
        swipedLetters: List<Char>,
        prevWord: String? = null,
        topN: Int = 3
    ): List<String> {
        if (swipedLetters.size < 2) return emptyList()
        val swipe = swipedLetters.joinToString("")
        val prev = prevWord?.lowercase()?.trim()

        // Puntuar palabras del diccionario personal
        val personalCandidates = words.entries.map { (word, freq) ->
            val swipeScore = swipeScore(swipe, word)
            val bigramBonus = if (!prev.isNullOrBlank()) {
                (bigrams["${prev}_${word}"] ?: 0) * 1.5f
            } else 0f
            val freqBonus = freq * 0.5f
            // score más bajo = mejor (swipeScore es distancia)
            word to (swipeScore - bigramBonus - freqBonus)
        }.sortedBy { it.second }.take(topN)

        // Combinar con predictor estático (sin duplicar)
        val staticCandidates = SwipePredictor.predict(swipedLetters, topN)
        val merged = mutableListOf<String>()
        merged.addAll(personalCandidates.map { it.first })
        for (w in staticCandidates) {
            if (!merged.contains(w)) merged.add(w)
            if (merged.size >= topN) break
        }
        return merged.take(topN)
    }

    fun isEmpty() = words.isEmpty()

    // ── Persistencia ──────────────────────────────────────────────────────────

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
