// MLKitManager.kt - Integración con ML Kit para extracción inteligente
package com.example.juka

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.entityextraction.*
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class MLKitManager(private val context: Context) {

    private val entityExtractor: EntityExtractor
    private val fishDatabase = FishDatabase(context)

    companion object {
        private const val TAG = "🤖 MLKitManager"

        // Patrones específicos para pesca argentina
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

        private val PATRONES_MODALIDAD = mapOf(
            "costa" to ModalidadPesca.CON_LINEA_COSTA,
            "orilla" to ModalidadPesca.CON_LINEA_COSTA,
            "playa" to ModalidadPesca.CON_LINEA_COSTA,
            "embarcado" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "barco" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "lancha" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "kayak" to ModalidadPesca.CON_LINEA_EMBARCACION,
            "submarina" to ModalidadPesca.PESCA_SUBMARINA_COSTA,
            "buceo" to ModalidadPesca.PESCA_SUBMARINA_COSTA,
            "red" to ModalidadPesca.CON_RED
        )
    }

    init {
        // Configurar ML Kit Entity Extraction para español
        entityExtractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.SPANISH)
                .build()
        )

        // Inicializar el modelo (descarga si es necesario)
        initializeMLKit()
    }

    private fun initializeMLKit() {
        entityExtractor.downloadModelIfNeeded()
            .addOnSuccessListener {
                Log.d(TAG, "✅ ML Kit modelo descargado exitosamente")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error descargando modelo ML Kit: ${e.message}")
            }
    }

    /**
     * Extrae información de pesca del texto usando ML Kit + patrones personalizados
     */
    suspend fun extraerInformacionPesca(texto: String): MLKitExtractionResult {
        return try {
            Log.d(TAG, "🔍 Extrayendo información de: '$texto'")

            // Usar ML Kit para entidades básicas
            val entitiesMLKit = extraerEntidadesMLKit(texto)

            // Agregar entidades específicas de pesca
            val entidadesPesca = extraerEntidadesPesca(texto)

            // Combinar resultados
            val todasEntidades = entitiesMLKit + entidadesPesca

            val resultado = MLKitExtractionResult(
                textoExtraido = texto,
                entidadesDetectadas = todasEntidades,
                confianza = calcularConfianzaPromedio(todasEntidades)
            )

            Log.d(TAG, "✅ Extracción completada: ${todasEntidades.size} entidades detectadas")
            todasEntidades.forEach { entity ->
                Log.d(TAG, "  - ${entity.tipo}: '${entity.valor}' (${(entity.confianza * 100).toInt()}%)")
            }

            resultado

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error en extracción: ${e.message}", e)
            MLKitExtractionResult(texto, emptyList(), 0f)
        }
    }

    /**
     * Usar ML Kit para extraer entidades estándar
     */
    private suspend fun extraerEntidadesMLKit(texto: String): List<MLKitEntity> {
        return try {
            val entidades = mutableListOf<MLKitEntity>()

            val params = EntityExtractionParams.Builder(texto).build()
            val extractedEntities = entityExtractor.annotate(params).await()

            extractedEntities.forEach { annotation ->
                annotation.entities.forEach { entity ->
                    val tipo = when (entity.type) {
                        Entity.TYPE_DATE_TIME -> "FECHA_HORA"
                        Entity.TYPE_ADDRESS -> "DIRECCION"
                        Entity.TYPE_PHONE -> "TELEFONO"
                        Entity.TYPE_EMAIL -> "EMAIL"
                        else -> "OTRO"
                    }

                    if (tipo == "FECHA_HORA") {
                        entidades.add(
                            MLKitEntity(
                                tipo = tipo,
                                valor = annotation.annotatedText,
                                confianza = 0.8f, // ML Kit no proporciona confianza explícita
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

    /**
     * Extraer entidades específicas de pesca usando patrones personalizados
     */
    private suspend fun extraerEntidadesPesca(texto: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()
        val textoLower = texto.lowercase()

        // Extraer fechas
        entidades.addAll(extraerFechas(texto, textoLower))

        // Extraer horas
        entidades.addAll(extraerHoras(texto, textoLower))

        // Extraer lugares
        entidades.addAll(extraerLugares(texto, textoLower))

        // Extraer provincias
        entidades.addAll(extraerProvincias(texto, textoLower))

        // Extraer modalidades de pesca
        entidades.addAll(extraerModalidades(texto, textoLower))

        // Extraer especies
        entidades.addAll(extraerEspecies(texto, textoLower))

        // Extraer números de cañas
        entidades.addAll(extraerNumeroCanas(texto, textoLower))

        // Extraer cantidad de peces
        entidades.addAll(extraerCantidadPeces(texto, textoLower))

        return entidades
    }

    private fun extraerFechas(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()

        PATRONES_FECHA.forEach { patron ->
            val matcher = patron.matcher(textoLower)
            while (matcher.find()) {
                val fechaTexto = matcher.group()
                val fechaNormalizada = normalizarFecha(fechaTexto)

                entidades.add(
                    MLKitEntity(
                        tipo = "FECHA",
                        valor = fechaNormalizada,
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
                            valor = lugar.split(" ").joinToString(" ") {
                                it.replaceFirstChar { char -> char.uppercase() }
                            },
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
                entidades.add(
                    MLKitEntity(
                        tipo = "PROVINCIA",
                        valor = provincia.displayName,
                        confianza = 0.9f,
                        posicionInicio = inicio,
                        posicionFin = inicio + patron.length
                    )
                )
            }
        }

        return entidades
    }

    private fun extraerModalidades(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()

        PATRONES_MODALIDAD.forEach { (patron, modalidad) ->
            if (textoLower.contains(patron)) {
                val inicio = textoLower.indexOf(patron)
                entidades.add(
                    MLKitEntity(
                        tipo = "MODALIDAD",
                        valor = modalidad.displayName,
                        confianza = 0.85f,
                        posicionInicio = inicio,
                        posicionFin = inicio + patron.length
                    )
                )
            }
        }

        return entidades
    }

    private suspend fun extraerEspecies(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()

        // Inicializar base de datos si no está lista
        if (!fishDatabase.isInitialized()) {
            fishDatabase.initialize()
        }

        // Buscar especies conocidas en el texto
        fishDatabase.getAllSpecies().forEach { especie ->
            val nombreLower = especie.name.lowercase()
            if (textoLower.contains(nombreLower)) {
                val inicio = textoLower.indexOf(nombreLower)
                entidades.add(
                    MLKitEntity(
                        tipo = "ESPECIE",
                        valor = especie.name,
                        confianza = 0.9f,
                        posicionInicio = inicio,
                        posicionFin = inicio + nombreLower.length
                    )
                )
            }
        }

        return entidades
    }

    private fun extraerNumeroCanas(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()

        val patronCanas = Pattern.compile(
            """(\d+|una?|dos|tres|cuatro|cinco)\s+cañas?""",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = patronCanas.matcher(textoLower)
        while (matcher.find()) {
            val numero = convertirNumeroTextoAEntero(matcher.group(1))
            if (numero != null) {
                entidades.add(
                    MLKitEntity(
                        tipo = "NUMERO_CANAS",
                        valor = numero.toString(),
                        confianza = 0.9f,
                        posicionInicio = matcher.start(),
                        posicionFin = matcher.end()
                    )
                )
            }
        }

        return entidades
    }

    private fun extraerCantidadPeces(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()

        val patronPeces = Pattern.compile(
            """(?:pesqu[éeí]|saq[uéeí]|captur[éeí])\s+(\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)""",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = patronPeces.matcher(textoLower)
        while (matcher.find()) {
            val numero = convertirNumeroTextoAEntero(matcher.group(1))
            if (numero != null) {
                entidades.add(
                    MLKitEntity(
                        tipo = "CANTIDAD_PECES",
                        valor = numero.toString(),
                        confianza = 0.85f,
                        posicionInicio = matcher.start(),
                        posicionFin = matcher.end()
                    )
                )
            }
        }

        return entidades
    }

    private fun normalizarFecha(fechaTexto: String): String {
        return when (fechaTexto.lowercase()) {
            "hoy" -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            "ayer" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -1)
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            }
            "anteayer" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -2)
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            }
            else -> fechaTexto // Para fechas en formato DD/MM/YYYY
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
        return if (entidades.isEmpty()) 0f
        else entidades.map { it.confianza }.average().toFloat()
    }

    /**
     * Convierte entidades ML Kit a datos del parte
     */
    fun convertirEntidadesAParteDatos(entidades: List<MLKitEntity>): ParteEnProgreso {
        var parteData = ParteEnProgreso()

        entidades.forEach { entity ->
            when (entity.tipo) {
                "FECHA" -> parteData = parteData.copy(fecha = entity.valor)
                "HORA_INICIO" -> parteData = parteData.copy(horaInicio = entity.valor)
                "HORA_FIN" -> parteData = parteData.copy(horaFin = entity.valor)
                "LUGAR" -> parteData = parteData.copy(lugar = entity.valor)
                "PROVINCIA" -> {
                    val provincia = Provincia.fromString(entity.valor)
                    parteData = parteData.copy(provincia = provincia)
                }
                "MODALIDAD" -> {
                    val modalidad = ModalidadPesca.fromString(entity.valor)
                    parteData = parteData.copy(modalidad = modalidad)
                }
                "NUMERO_CANAS" -> {
                    val numero = entity.valor.toIntOrNull()
                    parteData = parteData.copy(numeroCanas = numero)
                }
                "ESPECIE" -> {
                    val especieExistente = parteData.especiesCapturadas.find { it.nombre == entity.valor }
                    if (especieExistente == null) {
                        val nuevaEspecie = EspecieCapturada(nombre = entity.valor, numeroEjemplares = 1)
                        parteData = parteData.copy(
                            especiesCapturadas = parteData.especiesCapturadas + nuevaEspecie
                        )
                    }
                }
                "CANTIDAD_PECES" -> {
                    // Esta lógica puede mejorarse para asociar cantidades con especies específicas
                    val cantidad = entity.valor.toIntOrNull() ?: 1
                    if (parteData.especiesCapturadas.isNotEmpty()) {
                        val especieActualizada = parteData.especiesCapturadas.last().copy(
                            numeroEjemplares = cantidad
                        )
                        val especiesActualizadas = parteData.especiesCapturadas.dropLast(1) + especieActualizada
                        parteData = parteData.copy(especiesCapturadas = especiesActualizadas)
                    }
                }
            }
        }

        return parteData
    }

    fun cleanup() {
        try {
            entityExtractor.close()
            Log.d(TAG, "🧹 ML Kit limpiado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error limpiando ML Kit: ${e.message}")
        }
    }
}