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

        private val PATRONES_LUGAR = listOf(
            Pattern.compile("""en\s+([A-Za-záéíóúñ\s]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""playa\s+([A-Za-záéíóúñ\s]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""puerto\s+([A-Za-záéíóúñ\s]+)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""bahía\s+([A-Za-záéíóúñ\s]+)""", Pattern.CASE_INSENSITIVE)
        )

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

        val speciesNames = fishDatabase.getAllSpecies()
            .map { Pattern.quote(it.name.lowercase()) }
            .joinToString("|")
        if (speciesNames.isEmpty()) return entidades

        val patron = Pattern.compile(
            """(\d+|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s*($speciesNames)""",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = patron.matcher(textoLower)
        while (matcher.find()) {
            val cantidadStr = matcher.group(1)
            val cantidad = convertirNumeroTextoAEntero(cantidadStr) ?: continue
            val especieOriginal = texto.substring(matcher.start(2), matcher.end(2))

            entidades.add(MLKitEntity("CANTIDAD_PECES", cantidad.toString(), 0.9f, matcher.start(1), matcher.end(1)))
            entidades.add(MLKitEntity("ESPECIE", especieOriginal, 0.9f, matcher.start(2), matcher.end(2)))
        }
        return entidades
    }

    private fun extraerLugares(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        PATRONES_LUGAR.forEach { patron ->
            val matcher = patron.matcher(textoLower)
            while (matcher.find()) {
                val lugar = matcher.group(1)?.trim()
                if (!lugar.isNullOrBlank() && lugar.length > 2) {
                    entidades.add(
                        MLKitEntity(
                            tipo = "LUGAR",
                            valor = lugar.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                            confianza = 0.8f,
                            posicionInicio = matcher.start(),
                            posicionFin = matcher.end()
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
            if (textoLower.contains(patron)) {
                val inicio = textoLower.indexOf(patron)
                entidades.add(MLKitEntity("PROVINCIA", provincia.displayName, 0.9f, inicio, inicio + patron.length))
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
            val inicio = textoLower.indexOf(patron)
            if (inicio == -1) return@forEach
            val fin = inicio + patron.length
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

        fishDatabase.getAllSpecies().forEach { especie ->
            val nombreLower = especie.name.lowercase()
            var start = 0
            while (true) {
                val inicio = textoLower.indexOf(nombreLower, start)
                if (inicio == -1) break
                entidades.add(MLKitEntity("ESPECIE", especie.name, 0.9f, inicio, inicio + nombreLower.length))
                start = inicio + nombreLower.length
            }
        }
        return entidades
    }

    private fun extraerNumeroCanas(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        val patronCanas = Pattern.compile("""(\d+|una?|dos|tres|cuatro|cinco)\s+cañas?""", Pattern.CASE_INSENSITIVE)
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
            else -> texto.toIntOrNull()
        }
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