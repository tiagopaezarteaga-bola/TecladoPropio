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

    companion object {
        /** Nombre base del archivo exportado. DictManagerActivity debe usar esta constante. */
        const val EXPORT_FILE_NAME = "TecladoPegarDiccionario.txt"
    }

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
     * Vuelve a leer personal_dict.json desde disco, descartando el estado en memoria actual.
     * Imprescindible llamarlo cuando OTRA instancia de PersonalDictionary (por ejemplo la de
     * DictManagerActivity, al importar/exportar/borrar) pudo haber modificado el archivo en
     * disco mientras esta instancia (la del servicio de teclado) seguía viva con datos viejos.
     * Sin este refresco, el siguiente save() del teclado sobrescribiría el archivo recién
     * fusionado con la copia obsoleta en memoria, borrando lo importado.
     */
    fun reload() = load()

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
        // Si la palabra es nueva y no tiene trayectoria, calcular el teórico
        if (!swipePaths.containsKey(w)) {
            swipePaths[w] = mutableListOf(computeTheoreticalSwipe(w))
        }
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
        val paths = swipePaths.getOrPut(w) {
            // Primera vez: precalcular el teórico como punto de partida
            mutableListOf(computeTheoreticalSwipe(w))
        }
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
     * con frecuencia, bigramas, paths aprendidos, bonus de ancla, bonus de orden
     * de letras, penalizaciones de error y detección de pausas.
     *
     * @param pauseIndices índices en el path de puntos donde el dedo se pausó.
     *   Si hay pausas, se intenta predecir múltiples palabras separadas.
     */
    fun suggestForSwipe(
        swipedLetters: List<Char>,
        prevWord: String? = null,
        topN: Int = 3,
        pauseIndices: List<Int> = emptyList()
    ): List<String> {
        if (swipedLetters.size < 2 || words.isEmpty()) return emptyList()

        // Si hay pausas significativas, intentar segmentar el swipe en sub-swipes
        // y predecir cada segmento por separado como palabras compuestas detectadas.
        // Solo lo hacemos si los segmentos tienen longitud mínima razonable (≥2 letras cada uno).
        if (pauseIndices.isNotEmpty()) {
            val segments = splitAtPauses(swipedLetters, pauseIndices)
            if (segments.size >= 2 && segments.all { it.size >= 2 }) {
                // Predecir cada segmento y retornar la combinación como una sola sugerencia
                val perSegment = segments.map { seg ->
                    suggestSingleSwipe(seg, prevWord, topN = 1)
                }
                if (perSegment.all { it.isNotEmpty() }) {
                    val combined = perSegment.map { it.first() }.joinToString(" ")
                    // Agregar también las sugerencias normales del swipe completo como alternativas
                    val normal = suggestSingleSwipe(swipedLetters, prevWord, topN)
                    return (listOf(combined) + normal).distinct().take(topN)
                }
            }
        }

        return suggestSingleSwipe(swipedLetters, prevWord, topN)
    }

    /** Divide swipedLetters en segmentos según los índices de pausa. */
    private fun splitAtPauses(letters: List<Char>, pauseIndices: List<Int>): List<List<Char>> {
        val sorted = pauseIndices.sorted()
        val segments = mutableListOf<List<Char>>()
        var start = 0
        for (idx in sorted) {
            if (idx > start && idx <= letters.size) {
                segments.add(letters.subList(start, idx))
                start = idx
            }
        }
        if (start < letters.size) segments.add(letters.subList(start, letters.size))
        return segments
    }

    /** Predice para un único segmento de swipe (sin pausas). */
    private fun suggestSingleSwipe(
        swipedLetters: List<Char>,
        prevWord: String? = null,
        topN: Int = 3
    ): List<String> {
        if (swipedLetters.size < 2) return emptyList()
        val swipe = swipedLetters.joinToString("").uppercase()
        val swipeLower = swipe.lowercase()
        val prev  = prevWord?.lowercase()?.trim()
        val swipeLen = swipe.length
        val firstChar = swipedLetters.first().lowercaseChar()
        val lastChar  = swipedLetters.last().lowercaseChar()

        // Candidato compuesto: bigrama de alta frecuencia como unidad
        val compoundBoost = getCompoundBoost(prev)

        return words.entries
            // Filtro ±4: solo candidatos cuyo swipe teórico tenga longitud similar al real
            .filter { (word, _) ->
                val theoretical = swipePaths[word]?.firstOrNull() ?: computeTheoreticalSwipe(word)
                abs(theoretical.length - swipeLen) <= 4
            }
            // Filtro de ancla: descartar si ningún extremo coincide (exacto o vecino)
            .filter { (word, _) ->
                val wFirst = word.firstOrNull() ?: return@filter false
                val wLast  = word.lastOrNull()  ?: return@filter false
                val firstOk = firstChar == wFirst || (keyNeighbors[firstChar]?.contains(wFirst) == true)
                val lastOk  = lastChar  == wLast  || (keyNeighbors[lastChar]?.contains(wLast)  == true)
                // Regla: si ningún extremo coincide (ni exacto ni vecino) → descartar
                firstOk || lastOk
            }
            .map { (word, freq) ->
                val baseLev      = swipeScore(swipe, word)
                val learnedBonus = bestPathBonus(swipe, word)
                val bigramBonus  = if (!prev.isNullOrBlank()) (bigrams["${prev}_${word}"] ?: 0) * 1.5f else 0f
                val freqBonus    = freq * 0.5f
                val anchorBonus  = anchorScore(firstChar, lastChar, word)
                val orderBonus   = orderScore(swipeLower, word)
                val penalty      = swipePenalties[word] ?: 0f
                val compBonus    = if (!prev.isNullOrBlank()) compoundBoost[word] ?: 0f else 0f
                word to (baseLev - learnedBonus - bigramBonus - freqBonus - anchorBonus - orderBonus - compBonus + penalty)
            }
            .sortedBy { it.second }
            .take(topN)
            .map { it.first }
    }

    /**
     * Bonus de ancla según la tabla acordada.
     * Cada extremo (primera/última letra del swipe) se compara con la palabra candidata.
     * "Exacto" = misma letra. "Vecino" = tecla adyacente. "Ausente" = ninguno.
     *
     * | Primera | Última  | Bonus |
     * |---------|---------|-------|
     * | Exacta  | Exacta  | 6.0   |
     * | Exacta  | Vecina  | 4.0   |
     * | Vecina  | Exacta  | 4.0   |
     * | Exacta  | Ausente | 2.0   |
     * | Ausente | Exacta  | 2.0   |
     * | Vecina  | Vecina  | 2.0   |
     * | Vecina  | Ausente | 0 (ya filtrado arriba) |
     * | Ausente | Vecina  | 0 (ya filtrado arriba) |
     * | Ausente | Ausente | 0 (ya filtrado arriba) |
     */
    private fun anchorScore(firstChar: Char, lastChar: Char, word: String): Float {
        val wFirst = word.firstOrNull() ?: return 0f
        val wLast  = word.lastOrNull()  ?: return 0f
        val firstExact = firstChar == wFirst
        val firstNeigh = !firstExact && (keyNeighbors[firstChar]?.contains(wFirst) == true)
        val lastExact  = lastChar  == wLast
        val lastNeigh  = !lastExact && (keyNeighbors[lastChar]?.contains(wLast)  == true)

        return when {
            firstExact && lastExact  -> 6.0f
            firstExact && lastNeigh  -> 4.0f
            firstNeigh && lastExact  -> 4.0f
            firstExact               -> 2.0f   // last ausente
            lastExact                -> 2.0f   // first ausente
            firstNeigh && lastNeigh  -> 2.0f
            else                     -> 0.0f
        }
    }

    /**
     * Bonus por orden correcto: por cada letra de la palabra candidata que aparece
     * en el swipe en el orden correcto (subsecuencia), suma puntos.
     * Letras únicas que mantienen el orden = cantidad de aciertos.
     */
    private fun orderScore(swipe: String, word: String): Float {
        var swipeIdx = 0
        var matches = 0
        for (wChar in word) {
            while (swipeIdx < swipe.length) {
                if (swipe[swipeIdx] == wChar) {
                    matches++
                    swipeIdx++
                    break
                }
                swipeIdx++
            }
        }
        // Cada letra en orden correcto vale 0.4 puntos de bonus
        return matches * 0.4f
    }

    /**
     * Retorna un mapa word→bonus para palabras que forman compuesto frecuente
     * con la palabra anterior (basado en bigramas de alta coocurrencia).
     */
    private fun getCompoundBoost(prevWord: String?): Map<String, Float> {
        if (prevWord.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, Float>()
        val prefix = "${prevWord}_"
        bigrams.entries
            .filter { (k, v) -> k.startsWith(prefix) && v >= COMPOUND_THRESHOLD }
            .forEach { (k, v) ->
                val nextWord = k.removePrefix(prefix)
                // Bonus proporcional a la frecuencia del bigrama
                result[nextWord] = (v.toFloat() / COMPOUND_THRESHOLD) * 1.5f
            }
        return result
    }

    /**
     * Registra que la palabra [wrongWord] fue predicha incorrectamente para el [path] dado.
     * Incrementa la penalización de ese candidato para futuros swipes similares.
     */
    fun reportSwipeError(wrongWord: String, path: List<Char>) {
        val w = wrongWord.lowercase().trim()
        if (w.length < 2) return
        // Penalizar con un valor fijo por error reportado (acumulable)
        swipePenalties[w] = (swipePenalties[w] ?: 0f) + 2.0f
        save()
    }

    // ── Exportar ──────────────────────────────────────────────────────────────

    fun exportTo(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val w = OutputStreamWriter(out, Charsets.UTF_8)
                w.appendLine("# Diccionario personal TecladoPegar — ${EXPORT_FILE_NAME}")
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
     * Exporta directamente a un File de filesDir sin pasar por SAF.
     * Úsalo desde DictManagerActivity para evitar el crash del picker sobre el IME.
     */
    fun exportDirectToFile(file: File): Boolean {
        return try {
            file.bufferedWriter(Charsets.UTF_8).use { w -> w.write(buildExportContent()) }
            true
        } catch (_: Exception) { false }
    }

    /**
     * Genera el contenido completo del diccionario exportado como texto plano.
     * Se usa tanto para exportar a filesDir como a la carpeta pública Descargas.
     */
    fun buildExportContent(): String {
        val sb = StringBuilder()
        sb.appendLine("# Diccionario personal TecladoPegar — ${EXPORT_FILE_NAME}")
        sb.appendLine("# Exportado: ${java.util.Date()}")
        sb.appendLine("#")
        sb.appendLine("# PALABRAS (${words.size})")
        words.entries.sortedByDescending { it.value }.forEach { (word, freq) ->
            sb.appendLine("WORD\t$word\t$freq")
        }
        sb.appendLine("#")
        sb.appendLine("# BIGRAMAS (${bigrams.size})")
        bigrams.entries.sortedByDescending { it.value }.forEach { (pair, freq) ->
            sb.appendLine("BIGRAM\t$pair\t$freq")
        }
        sb.appendLine("#")
        sb.appendLine("# N-GRAMAS DE LETRAS (${letterNgrams.size})")
        letterNgrams.entries.sortedByDescending { it.value }.forEach { (ng, freq) ->
            sb.appendLine("NGRAM\t$ng\t$freq")
        }
        sb.appendLine("#")
        sb.appendLine("# TRAYECTORIAS SWIPE (${swipePaths.size} palabras)")
        swipePaths.entries.forEach { (word, paths) ->
            sb.appendLine("SWIPE\t$word\t${paths.joinToString("|")}")
        }
        sb.appendLine("#")
        sb.appendLine("# ESTADÍSTICAS DE GESTO (${gestureStats.size} palabras)")
        gestureStats.entries.forEach { (word, stat) ->
            sb.appendLine("GESTURE\t$word\tavg_len=${"%.1f".format(stat.avgLenPx)}\tavg_spd=${"%.3f".format(stat.avgSpdPxMs)}\tn=${stat.samples}")
        }
        return sb.toString()
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
        swipePaths.clear(); gestureStats.clear(); swipePenalties.clear()
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

            val pj = JSONObject(); swipePenalties.forEach { (k, v) -> pj.put(k, v.toDouble()) }
            root.put("swipePenalties", pj)

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
            root.optJSONObject("swipePenalties")?.keys()?.forEach { k ->
                swipePenalties[k] = root.getJSONObject("swipePenalties").getDouble(k).toFloat()
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

    // ── Motor de trayectoria SWIPE teórica ───────────────────────────────────
    //
    // Calcula en tiempo real el swipe teórico de cualquier palabra usando el
    // layout QWERTY con offsets por fila, igual que el generador del diccionario.
    // Se usa para: (1) filtrar candidatos por longitud ±4, (2) comparar contra
    // el swipe real en lugar de buildKey(), (3) guardar el teórico en swipePaths
    // cuando se aprende una palabra nueva que aún no tiene trayectoria.

    // ── Penalizaciones de swipe (word → score de penalización acumulada) ─────
    // Cuando el usuario corrige una predicción swipe, se acumula penalización
    // para ese candidato con ese path concreto. Se guarda en memoria; se persiste
    // en el JSON bajo la clave "swipePenalties".
    private val swipePenalties = mutableMapOf<String, Float>()   // word → penaltyScore

    // ── Palabras compuestas detectadas automáticamente ────────────────────────
    // bigrams con frecuencia ≥ COMPOUND_THRESHOLD se tratan como unidad en sugerencias.
    // No se persisten por separado: se derivan de bigrams en cada sesión con getCompounds().
    private val COMPOUND_THRESHOLD = 5

    // ── Vecinas del teclado para tolerancia de ancla ──────────────────────────
    private val keyNeighbors: Map<Char, Set<Char>> by lazy { buildNeighborMap() }

    private fun buildNeighborMap(): Map<Char, Set<Char>> {
        val map = mutableMapOf<Char, MutableSet<Char>>()
        for ((c1, _) in keyLayout) {
            val p1 = keyPos(c1) ?: continue
            val neighbors = mutableSetOf<Char>()
            for ((c2, _) in keyLayout) {
                if (c1 == c2) continue
                val p2 = keyPos(c2) ?: continue
                val dx = p1.first - p2.first
                val dy = p1.second - p2.second
                if (dx * dx + dy * dy <= 2.0f) neighbors.add(c2)  // distancia ≤ √2 (diagonales incluidas)
            }
            map[c1] = neighbors
        }
        return map
    }

    private val rowOffset = mapOf(0 to 0.0f, 1 to 0.5f, 2 to 1.0f)
    private val keyLayout = mapOf(
        'q' to Pair(0,0),'w' to Pair(1,0),'e' to Pair(2,0),'r' to Pair(3,0),'t' to Pair(4,0),
        'y' to Pair(5,0),'u' to Pair(6,0),'i' to Pair(7,0),'o' to Pair(8,0),'p' to Pair(9,0),
        'a' to Pair(0,1),'s' to Pair(1,1),'d' to Pair(2,1),'f' to Pair(3,1),'g' to Pair(4,1),
        'h' to Pair(5,1),'j' to Pair(6,1),'k' to Pair(7,1),'l' to Pair(8,1),'ñ' to Pair(9,1),
        'z' to Pair(0,2),'x' to Pair(1,2),'c' to Pair(2,2),'v' to Pair(3,2),'b' to Pair(4,2),
        'n' to Pair(5,2),'m' to Pair(6,2)
    )
    private val accentMap = mapOf(
        'á' to 'a','é' to 'e','í' to 'i','ó' to 'o','ú' to 'u','ü' to 'u','ñ' to 'ñ'
    )

    private fun keyPos(c: Char): Pair<Float, Float>? {
        val base = accentMap[c] ?: c
        val (col, row) = keyLayout[base] ?: return null
        return Pair(col + (rowOffset[row] ?: 0f), row.toFloat())
    }

    private fun keysBetween(c1: Char, c2: Char): List<Char> {
        val p1 = keyPos(c1) ?: return listOf(c1)
        val p2 = keyPos(c2) ?: return listOf(c2)
        val (x1, y1) = p1; val (x2, y2) = p2
        val dx = x2 - x1; val dy = y2 - y1
        val touched = mutableListOf<Pair<Float, Char>>()
        for ((ch, colRow) in keyLayout) {
            val cx = colRow.first + (rowOffset[colRow.second] ?: 0f)
            val cy = colRow.second.toFloat()
            val t = if (dx == 0f && dy == 0f) 0f
                    else ((cx - x1)*dx + (cy - y1)*dy) / (dx*dx + dy*dy)
            val tc = t.coerceIn(0f, 1f)
            val px = x1 + tc*dx; val py = y1 + tc*dy
            val dist = sqrt((cx-px)*(cx-px) + (cy-py)*(cy-py))
            if (dist < 0.6f) {
                val along = if (dx == 0f && dy == 0f) 0f
                            else ((cx - x1)*dx + (cy - y1)*dy) / (dx*dx + dy*dy)
                touched.add(Pair(along, ch))
            }
        }
        return touched.sortedBy { it.first }.map { it.second }
    }

    /** Calcula el swipe teórico de una palabra en tiempo real. */
    fun computeTheoreticalSwipe(word: String): String {
        val norm = word.lowercase().map { accentMap[it] ?: it }.filter { keyLayout.containsKey(it) }
        if (norm.size < 2) return norm.joinToString("").uppercase()
        val fullPath = mutableListOf<Char>()
        for (i in 0 until norm.size - 1) {
            for (ch in keysBetween(norm[i], norm[i + 1])) {
                if (fullPath.isEmpty() || fullPath.last() != ch) fullPath.add(ch)
            }
        }
        return fullPath.joinToString("").uppercase()
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
        var score = 0
        for (i in prefix.indices) {
            if (i + 1 < prefix.length) score += letterNgrams["${prefix[i]}${prefix[i+1]}"] ?: 0
            if (i + 2 < prefix.length) score += (letterNgrams["${prefix[i]}${prefix[i+1]}${prefix[i+2]}"] ?: 0) * 2
        }
        return score
    }

    private fun swipeScore(swipe: String, word: String): Float {
        if (word.isEmpty()) return Float.MAX_VALUE
        // Comparar contra el swipe teórico de la palabra, no contra buildKey()
        val theoretical = swipePaths[word]?.firstOrNull() ?: computeTheoreticalSwipe(word)
        val lev = levenshtein(swipe, theoretical).toFloat()
        val lenDiff = abs(theoretical.length - swipe.length) * 0.3f
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
