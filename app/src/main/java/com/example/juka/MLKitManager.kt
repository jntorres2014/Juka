// MLKitManager.kt - Integración con ML Kit para extracción inteligente
package com.example.juka

import android.content.Context
import android.util.Log
import com.google.mlkit.nl.entityextraction.*
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

            // Combinar y ordenar por posición en el texto (clave para asociaciones)
            val todasEntidades = (entitiesMLKit + entidadesPesca).sortedBy { it.posicionInicio }

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

        // Extraer números de cañas
        entidades.addAll(extraerNumeroCanas(texto, textoLower))

        // Nueva: Extraer capturas (pares cantidad-especie)
        val capturas = extraerCapturas(texto, textoLower)
        entidades.addAll(capturas)

        // Extraer especies solitarias, evitando duplicados con capturas
        val coveredEspecieStarts = capturas.filter { it.tipo == "ESPECIE" }.map { it.posicionInicio }.toSet()
        val especiesLone = extraerEspecies(texto, textoLower).filter { !coveredEspecieStarts.contains(it.posicionInicio) }
        entidades.addAll(especiesLone)

        // Nota: Eliminamos extraerCantidadPeces, ya que lo maneja extraerCapturas

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
    private suspend fun extraerCapturas(texto: String, textoLower: String): List<MLKitEntity> {
        val entidades = mutableListOf<MLKitEntity>()

        // Inicializar base de datos si no está lista
        if (!fishDatabase.isInitialized()) {
            fishDatabase.initialize()
        }

        // Construir regex dinámico con nombres de especies (escapa caracteres especiales)
        val speciesNames = fishDatabase.getAllSpecies().map { Pattern.quote(it.name.lowercase()) }.joinToString("|")
        Log.d("Peces",speciesNames)
        if (speciesNames.isEmpty()) return entidades  // Si no hay especies, salir

        val patron = Pattern.compile(
            """(\d+|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s*($speciesNames)""",
            Pattern.CASE_INSENSITIVE
        )

        val matcher = patron.matcher(textoLower)
        while (matcher.find()) {
            val cantidadStr = matcher.group(1)
            val especieLower = matcher.group(2)

            val cantidad = convertirNumeroTextoAEntero(cantidadStr) ?: continue

            // Obtener el nombre original con mayúsculas del texto
            val especieOriginal = texto.substring(matcher.start(2), matcher.end(2))

            // Añadir CANTIDAD_PECES (posición del número)
            entidades.add(
                MLKitEntity(
                    tipo = "CANTIDAD_PECES",
                    valor = cantidad.toString(),
                    confianza = 0.9f,
                    posicionInicio = matcher.start(1),
                    posicionFin = matcher.end(1)
                )
            )

            // Añadir ESPECIE (posición del nombre)
            entidades.add(
                MLKitEntity(
                    tipo = "ESPECIE",
                    valor = especieOriginal,
                    confianza = 0.9f,
                    posicionInicio = matcher.start(2),
                    posicionFin = matcher.end(2)
                )
            )
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

        // Obtener capturas para saber posiciones cubiertas (llama a extraerCapturas si no lo has hecho, pero para evitar ciclo, asume se llama después)
        // Nota: Para evitar duplicación, pasaremos coveredStarts desde extraerEntidadesPesca

        // Por ahora, buscaremos todas las ocurrencias múltiples
        fishDatabase.getAllSpecies().forEach { especie ->
            val nombreLower = especie.name.lowercase()
            var start = 0
            while (true) {
                val inicio = textoLower.indexOf(nombreLower, start)
                if (inicio == -1) break

                // Aquí, en extraerEntidadesPesca filtraremos duplicados, pero por ahora añade todas
                entidades.add(
                    MLKitEntity(
                        tipo = "ESPECIE",
                        valor = especie.name,
                        confianza = 0.9f,
                        posicionInicio = inicio,
                        posicionFin = inicio + nombreLower.length
                    )
                )

                start = inicio + nombreLower.length  // Avanza para evitar overlap
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
        var pendingCantidad: Int? = null

        // Ordenar por posición (por si no viene ordenado)
        val entidadesOrdenadas = entidades.sortedBy { it.posicionInicio }

        entidadesOrdenadas.forEach { entity ->
            when (entity.tipo) {
                "CANTIDAD_PECES" -> {
                    pendingCantidad = entity.valor.toIntOrNull()
                    // No actualizamos aquí, solo pendemos
                }
                "ESPECIE" -> {
                    val cantidad = pendingCantidad ?: 1
                    val especieExistente = parteData.especiesCapturadas.find { it.nombre == entity.valor }
                    if (especieExistente != null) {
                        // Sumar si ya existe (por si duplicados)
                        val updated = especieExistente.copy(
                            numeroEjemplares = especieExistente.numeroEjemplares + cantidad
                        )
                        val newList = parteData.especiesCapturadas.map {
                            if (it.nombre == entity.valor) updated else it
                        }
                        parteData = parteData.copy(especiesCapturadas = newList)
                    } else {
                        val nuevaEspecie = EspecieCapturada(nombre = entity.valor, numeroEjemplares = cantidad)
                        parteData = parteData.copy(
                            especiesCapturadas = parteData.especiesCapturadas + nuevaEspecie
                        )
                    }
                    pendingCantidad = null  // Resetear
                }
                // Mantén los otros cases iguales
                "FECHA" -> parteData = parteData.copy(fecha = entity.valor)
                "HORA_INICIO" -> parteData = parteData.copy(horaInicio = entity.valor)
                "HORA_FIN" -> parteData = parteData.copy(horaFin = entity.valor)
//                "LUGAR" -> parteData = parteData.copy(lugar = entity.valor)
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
                // Ignora otros como FECHA_HORA si no los usas
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