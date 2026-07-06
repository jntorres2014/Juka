package com.example.juka

import android.content.Context
import android.util.Log
import com.example.juka.domain.model.EspecieCapturada
import com.example.juka.domain.model.MLKitEntity
import com.example.juka.domain.model.MLKitExtractionResult
import com.example.juka.domain.model.ModalidadPesca
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.Provincia
import com.google.mlkit.nl.entityextraction.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// ✅ CAMBIO: fishDatabase se recibe como parámetro en lugar de crearse internamente
class MLKitManager(
    private val context: Context,
    private val fishDatabase: FishDatabase          // ← inyectado desde HukaApplication
) {

    private val entityExtractor: EntityExtractor

    companion object {
        private const val TAG = "🤖 MLKitManager"

        private val PATRONES_FECHA = listOf(
            Pattern.compile("""(hoy|ayer|anteayer)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})"""),
            Pattern.compile("""(lunes|martes|miércoles|jueves|viernes|sábado|domingo)\s+(pasado|último)""", Pattern.CASE_INSENSITIVE)
        )

        private val PATRONES_HORA = listOf(
            Pattern.compile("""(\d{1,2}):(\d{2})"""),
            Pattern.compile("""de\s+(\d{1,2})\s+a\s+(\d{1,2})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""desde\s+las\s+(\d{1,2})\s+hasta\s+las\s+(\d{1,2})""", Pattern.CASE_INSENSITIVE)
        )

        // Capturamos como máximo 3 palabras tras el conector; luego limpiarLugar()
        // recorta en la primera palabra de "corte" (verbo/preposición). Esto evita
        // el bug greedy: "en el río con mi hermano" ya no se traga toda la frase.
        private val PATRONES_LUGAR = listOf(
            Pattern.compile("""\ben\s+((?:[A-Za-záéíóúñ]+\s?){1,3})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bplaya\s+((?:[A-Za-záéíóúñ]+\s?){1,3})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bpuerto\s+((?:[A-Za-záéíóúñ]+\s?){1,3})""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""\bbah[ií]a\s+((?:[A-Za-záéíóúñ]+\s?){1,3})""", Pattern.CASE_INSENSITIVE)
        )

        // Palabras que CORTAN el nombre del lugar (verbos, preposiciones, conectores).
        // OJO: los artículos (la/el/los/las/del) NO van acá, para no romper nombres
        // legítimos como "La Plata", "Mar del Plata", "Río de la Plata".
        private val CORTES_LUGAR = setOf(
            "a", "al", "con", "y", "o", "porque", "mientras", "cuando", "donde",
            "pescar", "pescando", "pesque", "pesqué", "saque", "saqué",
            "capture", "capturé", "fui", "fuimos", "ir", "desde", "hasta"
        )

        // Palabras que niegan una captura cuando aparecen justo antes de la especie.
        private val PALABRAS_NEGACION = listOf(
            "no", "ningún", "ningun", "ninguna", "ningunos", "sin", "tampoco"
        )

        // Alternativa de números (palabra o dígito) reutilizada por varios regex.
        private const val NUMEROS_REGEX =
            """\d+|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieci(?:séis|seis|siete|ocho|nueve)|veinte|treinta"""

        private val PATRONES_PROVINCIA = mapOf(
            "buenos aires" to Provincia.BUENOS_AIRES,
            "chubut" to Provincia.CHUBUT,
            "neuquén" to Provincia.NEUQUEN,
            "neuquen" to Provincia.NEUQUEN,
            "río negro" to Provincia.RIO_NEGRO,
            "rio negro" to Provincia.RIO_NEGRO,
            "santa cruz" to Provincia.SANTA_CRUZ,
            "tierra del fuego" to Provincia.TIERRA_DEL_FUEGO
        )

        // Orden importa: las claves más largas/específicas primero para que
        // "submarina embarcado" pegue antes que "submarina" sola, etc.
        // extraerModalidades usa firstOrNull{ tipo == "MODALIDAD" } al final.
        private val PATRONES_MODALIDAD = linkedMapOf(
            // PESCA_SUBMARINA_EMBARCACION (frases largas primero)
            "submarina embarcado" to ModalidadPesca.PESCA_SUBMARINA_EMBARCACION,
            "submarina embarcada" to ModalidadPesca.PESCA_SUBMARINA_EMBARCACION,
            "buceo embarcado" to ModalidadPesca.PESCA_SUBMARINA_EMBARCACION,
            // PESCA_SUBMARINA_COSTA
            "submarina costa" to ModalidadPesca.PESCA_SUBMARINA_COSTA,
            "submarina" to ModalidadPesca.PESCA_SUBMARINA_COSTA,
            "buceo" to ModalidadPesca.PESCA_SUBMARINA_COSTA,
            // CON_RED
            "agallera" to ModalidadPesca.CON_RED,
            "arrastre" to ModalidadPesca.CON_RED,
            "mediomundo" to ModalidadPesca.CON_RED,
            "medio mundo" to ModalidadPesca.CON_RED,
            "redes" to ModalidadPesca.CON_RED,
            "malla" to ModalidadPesca.CON_RED,
            "red" to ModalidadPesca.CON_RED,
            // CON_LINEA_EMBARCACION
            "embarcado" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "embarcada" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "barco" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "lancha" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "kayak" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "gomon" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "gomón" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "velero" to ModalidadPesca.CON_LINEA_EMBARCACION,
            // CON_LINEA_COSTA
            "costa" to ModalidadPesca.CON_LINEA_COSTA,
            "orilla" to ModalidadPesca.CON_LINEA_COSTA,
            "playa" to ModalidadPesca.CON_LINEA_COSTA,
            "muelle" to ModalidadPesca.CON_LINEA_COSTA,
            "escollera" to ModalidadPesca.CON_LINEA_COSTA
        )
    }

    init {
        entityExtractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.SPANISH).build()
        )
        entityExtractor.downloadModelIfNeeded()
            .addOnSuccessListener { Log.d(TAG, "✅ ML Kit modelo descargado") }
            .addOnFailureListener { e -> Log.e(TAG, "❌ Error descargando modelo ML Kit: ${e.message}") }
    }

    suspend fun extraerInformacionPesca(texto: String): MLKitExtractionResult {
        return try {
            Log.d(TAG, "🔍 Extrayendo información de: '$texto'")
            val entidadesPesca = extraerEntidadesPesca(texto)
            val entitiesMLKit = extraerEntidadesMLKit(texto)

            val posicionesOcupadas = entidadesPesca.map { it.posicionInicio..it.posicionFin }
            val mlKitFiltrado = entitiesMLKit.filter { mlEntity ->
                posicionesOcupadas.none { mlEntity.posicionInicio in it || mlEntity.posicionFin in it }
            }

            val todasEntidades = (entidadesPesca + mlKitFiltrado).sortedBy { it.posicionInicio }

            MLKitExtractionResult(
                textoExtraido = texto,
                entidadesDetectadas = todasEntidades,
                confianza = calcularConfianzaPromedio(todasEntidades)
            )
        } catch (e: Exception) {
            Log.e(TAG, "💥 Error en extracción: ${e.message}", e)
            MLKitExtractionResult(texto, emptyList(), 0f)
        }
    }

    private suspend fun extraerEntidadesMLKit(texto: String): List<MLKitEntity> {
        return try {
            val entidades = mutableListOf<MLKitEntity>()
            val params = EntityExtractionParams.Builder(texto).build()
            val extractedEntities = entityExtractor.annotate(params).await()

            extractedEntities.forEach { annotation ->
                annotation.entities.forEach { entity ->
                    if (entity.type == Entity.TYPE_DATE_TIME) {
                        entidades.add(
                            MLKitEntity(
                                tipo = "FECHA_HORA",
                                valor = annotation.annotatedText,
                                confianza = 0.8f,
                                posicionInicio = annotation.start,
                                posicionFin = annotation.end
                            )
                        )
                    }
                }
            }
            entidades
        } catch (e: Exception) {
            Log.e(TAG, "Error ML Kit extraction: ${e.message}")
            emptyList()
        }
    }

    private suspend fun extraerEntidadesPesca(texto: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        val textoLower = texto.lowercase()

        entidades.addAll(extraerFechas(texto, textoLower))
        entidades.addAll(extraerHoras(texto, textoLower))
        entidades.addAll(extraerLugares(texto, textoLower))
        entidades.addAll(extraerProvincias(texto, textoLower))
        entidades.addAll(extraerModalidades(texto, textoLower))
        entidades.addAll(extraerNumeroCanas(texto, textoLower))

        val capturas = extraerCapturas(texto, textoLower)
        entidades.addAll(capturas)

        val coveredEspecieStarts = capturas.filter { it.tipo == "ESPECIE" }.map { it.posicionInicio }.toSet()
        val especiesLone = extraerEspecies(texto, textoLower).filter { it.posicionInicio !in coveredEspecieStarts }
        entidades.addAll(especiesLone)

        return entidades
    }

    private fun extraerFechas(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        PATRONES_FECHA.forEach { patron ->
            val matcher = patron.matcher(textoLower)
            while (matcher.find()) {
                entidades.add(
                    MLKitEntity(
                        tipo = "FECHA",
                        valor = normalizarFecha(matcher.group()),
                        confianza = 0.9f,
                        posicionInicio = matcher.start(),
                        posicionFin = matcher.end()
                    )
                )
            }
        }
        return entidades
    }

    private fun extraerHoras(texto: String, textoLower: String): List<MLKitEntity> {
        return extraerHorasConNuevoExtractor(texto)
    }

    private suspend fun extraerCapturas(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        if (!fishDatabase.isInitialized()) fishDatabase.initialize()
        if (fishDatabase.fishSpeciesDB.isEmpty()) return entidades

        // ✅ E1 + acentos: trabajamos sobre el texto sin tildes (1:1, conserva
        // posiciones) y un alfabeto de claves también sin tildes, para que
        // "róbalos" (STT) matchee la clave "robalo" del JSON.
        val textoNorm = sinAcentos(textoLower)
        val normAName = normalToCanonical()           // "robalo" → "Robalo"
        val claves = normAName.keys.sortedByDescending { it.length }
        val speciesNames = claves.joinToString("|") { Pattern.quote(it) }

        val patron = Pattern.compile(
            """($NUMEROS_REGEX)\s*($speciesNames)""",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = patron.matcher(textoNorm)
        while (matcher.find()) {
            val cantidad = convertirNumeroTextoAEntero(matcher.group(1)) ?: continue

            // ✅ E4: si hay una negación justo antes ("no saqué ningún dorado"),
            // no es una captura.
            if (estaNegado(textoNorm, matcher.start(2))) continue

            // ✅ E1: la clave detectada (sinónimo o nombre) → nombre canónico.
            val claveNorm = textoNorm.substring(matcher.start(2), matcher.end(2))
            val especieCanonica = normAName[claveNorm]
                ?: texto.substring(matcher.start(2), matcher.end(2))

            entidades.add(MLKitEntity("CANTIDAD_PECES", cantidad.toString(), 0.9f, matcher.start(1), matcher.end(1)))
            entidades.add(MLKitEntity("ESPECIE", especieCanonica, 0.9f, matcher.start(2), matcher.end(2)))
        }
        return entidades
    }

    private fun extraerLugares(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        PATRONES_LUGAR.forEach { patron ->
            val matcher = patron.matcher(textoLower)
            while (matcher.find()) {
                val lugarLimpio = limpiarLugar(matcher.group(1))
                if (!lugarLimpio.isNullOrBlank() && lugarLimpio.length > 2) {
                    entidades.add(
                        MLKitEntity(
                            tipo = "LUGAR",
                            valor = lugarLimpio.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                            confianza = 0.8f,
                            posicionInicio = matcher.start(),
                            posicionFin = matcher.start() + lugarLimpio.length
                        )
                    )
                }
            }
        }
        return entidades
    }

    private fun extraerProvincias(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        PATRONES_PROVINCIA.forEach { (patron, provincia) ->
            // ✅ E3: límites de palabra para que "río negro" no pegue dentro de
            // otra cosa y "salta" (provincia) no se confunda con el verbo.
            val m = Regex("(?<![\\p{L}])${Regex.escape(patron)}(?![\\p{L}])").find(textoLower)
            if (m != null) {
                entidades.add(MLKitEntity("PROVINCIA", provincia.displayName, 0.9f, m.range.first, m.range.last + 1))
            }
        }
        return entidades
    }

    private fun extraerModalidades(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        // Iteramos en el orden definido en PATRONES_MODALIDAD (más específico
        // primero gracias al linkedMapOf). Cuando un patrón pega, marcamos su
        // rango como ocupado para que patrones más cortos que estén dentro
        // (ej. "embarcado" dentro de "submarina embarcado") no se detecten
        // como una segunda modalidad distinta.
        val rangosOcupados = mutableListOf<IntRange>()
        PATRONES_MODALIDAD.forEach { (patron, modalidad) ->
            // ✅ E3: límites de palabra para que "red" no pegue dentro de "pared"
            // ni "costa" dentro de "Costanera".
            val m = Regex("(?<![\\p{L}])${Regex.escape(patron)}(?![\\p{L}])").find(textoLower)
                ?: return@forEach
            val inicio = m.range.first
            val fin = m.range.last + 1
            val solapa = rangosOcupados.any { rango -> inicio in rango || (fin - 1) in rango }
            if (solapa) return@forEach
            entidades.add(MLKitEntity("MODALIDAD", modalidad.displayName, 0.85f, inicio, fin))
            rangosOcupados.add(inicio until fin)
        }
        return entidades
    }

    private suspend fun extraerEspecies(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        if (!fishDatabase.isInitialized()) fishDatabase.initialize()

        // ✅ E1 + acentos: claves normalizadas (sin tildes) recorridas de la más
        // larga a la más corta, con límites de palabra (E3) para no pegar "boga"
        // dentro de "abogado", y respetando negaciones (E4).
        val textoNorm = sinAcentos(textoLower)
        val normAName = normalToCanonical()
        normAName.keys.sortedByDescending { it.length }.forEach { claveNorm ->
            val patron = Regex("(?<![\\p{L}])${Regex.escape(claveNorm)}(?![\\p{L}])")
            patron.findAll(textoNorm).forEach { m ->
                if (estaNegado(textoNorm, m.range.first)) return@forEach
                val canonica = normAName[claveNorm] ?: claveNorm
                entidades.add(MLKitEntity("ESPECIE", canonica, 0.9f, m.range.first, m.range.last + 1))
            }
        }
        return entidades
    }

    private fun extraerNumeroCanas(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        val patronCanas = Pattern.compile("""($NUMEROS_REGEX)\s+cañas?""", Pattern.CASE_INSENSITIVE)
        val matcher = patronCanas.matcher(textoLower)
        while (matcher.find()) {
            val numero = convertirNumeroTextoAEntero(matcher.group(1))
            if (numero != null) {
                entidades.add(MLKitEntity("NUMERO_CANAS", numero.toString(), 0.9f, matcher.start(), matcher.end()))
            }
        }
        return entidades
    }

    private fun normalizarFecha(fechaTexto: String): String {
        return when (fechaTexto.lowercase()) {
            "hoy" -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            "ayer" -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            }
            "anteayer" -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            }
            else -> fechaTexto
        }
    }

    private fun convertirNumeroTextoAEntero(texto: String): Int? {
        return when (texto.lowercase()) {
            "un", "una", "uno" -> 1
            "dos" -> 2
            "tres" -> 3
            "cuatro" -> 4
            "cinco" -> 5
            "seis" -> 6
            "siete" -> 7
            "ocho" -> 8
            "nueve" -> 9
            "diez" -> 10
            "once" -> 11
            "doce" -> 12
            "trece" -> 13
            "catorce" -> 14
            "quince" -> 15
            "dieciséis", "dieciseis" -> 16
            "diecisiete" -> 17
            "dieciocho" -> 18
            "diecinueve" -> 19
            "veinte" -> 20
            "treinta" -> 30
            else -> texto.toIntOrNull()
        }
    }

    /** Quita tildes 1:1 (conserva la longitud, así las posiciones siguen
     *  siendo válidas contra el texto original). No toca la ñ. */
    private fun sinAcentos(s: String): String = s
        .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
        .replace('à', 'a').replace('è', 'e').replace('ì', 'i').replace('ò', 'o').replace('ù', 'u')
        .replace('ä', 'a').replace('ë', 'e').replace('ï', 'i').replace('ö', 'o').replace('ü', 'u')

    /** Mapa "clave sin tildes en minúscula" → nombre canónico de la especie.
     *  Incluye nombres principales y sinónimos cargados del JSON. */
    private fun normalToCanonical(): Map<String, String> {
        val out = HashMap<String, String>()
        fishDatabase.fishSpeciesDB.forEach { (clave, info) ->
            val k = sinAcentos(clave.lowercase()).trim()
            if (k.length >= 3) out[k] = info.name
        }
        return out
    }

    /** ✅ E4: ¿hay una negación en los ~20 caracteres previos a la especie?
     *  Detecta casos como "no pesqué ningún dorado" para no cargarlo como captura. */
    private fun estaNegado(textoLower: String, posInicio: Int): Boolean {
        val ventana = textoLower.substring(maxOf(0, posInicio - 20), posInicio)
        return PALABRAS_NEGACION.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(ventana) }
    }

    /** ✅ E2: recorta el nombre del lugar en la primera palabra de "corte"
     *  (verbo/preposición) para no arrastrar el resto de la frase. */
    private fun limpiarLugar(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val out = mutableListOf<String>()
        for (token in raw.trim().split(Regex("\\s+"))) {
            if (token.lowercase() in CORTES_LUGAR) break
            out.add(token)
        }
        return out.joinToString(" ").trim().ifBlank { null }
    }

    private fun calcularConfianzaPromedio(entidades: List<MLKitEntity>): Float {
        return if (entidades.isEmpty()) 0f else entidades.map { it.confianza }.average().toFloat()
    }

    fun convertirEntidadesAParteDatos(entidades: List<MLKitEntity>): ParteEnProgreso {
        var parteData = ParteEnProgreso()
        var pendingCantidad: Int? = null

        entidades.sortedBy { it.posicionInicio }.forEach { entity ->
            when (entity.tipo) {
                "CANTIDAD_PECES" -> pendingCantidad = entity.valor.toIntOrNull()
                "ESPECIE" -> {
                    val cantidad = pendingCantidad ?: 1
                    val especieExistente = parteData.especiesCapturadas.find { it.nombre == entity.valor }
                    if (especieExistente != null) {
                        val updated = especieExistente.copy(numeroEjemplares = especieExistente.numeroEjemplares + cantidad)
                        parteData = parteData.copy(especiesCapturadas = parteData.especiesCapturadas.map { if (it.nombre == entity.valor) updated else it })
                    } else {
                        parteData = parteData.copy(especiesCapturadas = parteData.especiesCapturadas + EspecieCapturada(entity.valor, cantidad))
                    }
                    pendingCantidad = null
                }
                "FECHA"       -> parteData = parteData.copy(fecha = entity.valor)
                "HORA_INICIO" -> parteData = parteData.copy(horaInicio = entity.valor)
                "HORA_FIN"    -> parteData = parteData.copy(horaFin = entity.valor)
                // ✅ E6: antes se descartaban "HORA" (suelta) y "LUGAR" en este
                // camino de voz general. Ahora se mapean.
                "HORA"        -> parteData = if (parteData.horaInicio == null)
                    parteData.copy(horaInicio = entity.valor)
                else parteData.copy(horaFin = entity.valor)
                "LUGAR"       -> parteData = parteData.copy(nombreLugar = entity.valor)
                "PROVINCIA"   -> parteData = parteData.copy(provincia = Provincia.fromString(entity.valor))
                "MODALIDAD"   -> parteData = parteData.copy(modalidad = ModalidadPesca.fromString(entity.valor))
                "NUMERO_CANAS"-> parteData = parteData.copy(numeroCanas = entity.valor.toIntOrNull())
            }
        }
        return parteData
    }

    fun cleanup() {
        try {
            entityExtractor.close()
            Log.d(TAG, "🧹 ML Kit limpiado")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando ML Kit: ${e.message}")
        }
    }
}