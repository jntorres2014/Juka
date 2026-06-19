// PescadexManager.kt - Gestión completa de la Pescadex integrada con Firebase
package com.example.juka

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import android.provider.Settings
import com.example.juka.data.local.room.PescadexRecordEntity

class PescadexManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = (context.applicationContext as HukaApplication).localStorageHelper

    /**
     * `FishDatabase` es la fuente de verdad de especies argentinas
     * (carga el JSON `peces_argentinos1.json` con todas las especies).
     * Antes el Pescadex usaba un catálogo paralelo hardcoded de 10 entries
     * (`EspeciesArgentinas` en `Pescadex.kt`) lo cual hacía que muchas
     * especies legítimas no se reconocieran al cargar partes. Ahora usamos
     * la misma fuente que el chat y el wizard — una única fuente.
     */
    private val fishDatabase: FishDatabase by lazy {
        (context.applicationContext as HukaApplication).fishDatabase
    }

    /**
     * Identificador del Pescadex en Firestore. Hoy usamos el UID de Firebase
     * Auth para que el Pescadex viaje con la cuenta (no con el celular).
     * Si por alguna razón no hay usuario autenticado caemos al deviceId
     * legacy como fallback — esto solo aplica en escenarios degradados.
     */
    private val pescadexId: String
        get() = auth.currentUser?.uid ?: getDeviceId()

    /**
     * Devuelve el catálogo actual de especies (id normalizado -> EspecieInfo).
     * Se construye on-demand desde `FishDatabase`. La inicialización del
     * JSON es idempotente: solo cargas una vez aunque llamés muchas veces.
     *
     * El "id" es el nombre normalizado (lowercase, sin acentos, espacios
     * → underscore). Esto se persiste en Firestore como key en
     * `especiesDescubiertas`. Si el JSON cambia un nombre, podría romper
     * la lectura de docs viejos — riesgo aceptable mientras el catálogo
     * esté estable.
     */
    private suspend fun catalogoActual(): Map<String, EspecieInfo> {
        if (!fishDatabase.isInitialized()) {
            android.util.Log.d(TAG, "🔄 FishDatabase no estaba inicializada, inicializando...")
            fishDatabase.initialize()
        }
        val especies = fishDatabase.getAllSpecies()
        android.util.Log.d(TAG, "📚 catalogoActual(): ${especies.size} especies del FishDatabase")
        if (especies.isEmpty()) {
            android.util.Log.w(TAG, "⚠️ FishDatabase.getAllSpecies() devolvió lista vacía — el grid del Pescadex va a estar vacío")
        }
        return especies.associate { fish ->
            val id = normalizarParaId(fish.name)
            id to EspecieInfo(
                id = id,
                nombreComun = fish.name,
                nombreCientifico = fish.scientificName,
                habitat = fish.habitat,
                mejoresCarnadas = fish.bestBaits,
                mejorHorario = fish.bestTime,
                tecnica = fish.technique,
                tamaño = fish.avgSize,
                temporada = fish.season,
                // FishInfo no tiene rareza/descripcion/region. Quedan en
                // default para no romper la UI que las consume.
                rareza = "comun",
                descripcion = "",
                region = "",
                consejoEspecial = ""
            )
        }
    }

    /** Normaliza para usar como id estable de una especie. */
    private fun normalizarParaId(nombre: String): String =
        nombre.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    companion object {
        private const val TAG = "🐟 PescadexManager"
        private const val COLLECTION_PESCADEX = "pescadex_usuarios"
        
        // Logros disponibles
        val LOGROS_DISPONIBLES = listOf(
            LogroPescadex("primer_pez", "Primer Pez", "Tu primera captura registrada", "🎣", "primera_captura"),
            LogroPescadex("explorador", "Explorador", "5 especies diferentes", "🗺️", "5_especies"),
            LogroPescadex("coleccionista", "Coleccionista", "10 especies diferentes", "📋", "10_especies"),
            LogroPescadex("especialista", "Especialista", "15 especies diferentes", "⭐", "15_especies"),
            LogroPescadex("maestro", "Maestro Pescador", "20 especies diferentes", "🏆", "20_especies"),
            LogroPescadex("cazador_raros", "Cazador de Raros", "Una especie rara", "💎", "especie_rara"),
            LogroPescadex("cazador_epicos", "Cazador Épico", "Una especie épica", "👑", "especie_epica"),
            LogroPescadex("completista", "Completista", "30 especies registradas", "🌟", "completista")
        )
    }
    
    /**
     * Registra una nueva captura de una especie en el Pescadex. Pensado para
     * ser llamado desde el flujo automático de `registrarEspeciesDeParte`
     * (al guardar un parte).
     *
     * - `cantidadEnParte`: cuántos ejemplares de esta especie se capturaron
     *   en este parte particular. Se usa para mantener el récord de "mejor día".
     * - `fechaParte`: fecha del parte (string libre, normalmente "dd/MM/yyyy").
     *   Se guarda tal cual viene para acompañar la marca de "mejor día".
     *
     * Importante: NO acepta `peso` ni `fotoPath` como parámetros — esos campos
     * son récord personal manual y se editan vía `actualizarRecordPersonal`.
     */
    suspend fun registrarEspecieCapturada(
        especieId: String,
        cantidadEnParte: Int = 1,
        locacion: String? = null,
        fechaParte: String? = null
    ): RegistroResult {
        return try {
            android.util.Log.d(TAG, "🎯 Registrando especie: $especieId (x$cantidadEnParte)")

            val catalogo = catalogoActual()
            val especieInfo = catalogo[especieId]
            if (especieInfo == null) {
                android.util.Log.w(TAG, "⚠️ Especie desconocida: $especieId")
                return RegistroResult.Error("Especie no reconocida")
            }

            val pescadexActual = obtenerPescadexUsuario()
            val especieExistente = pescadexActual.especiesDescubiertas[especieId]

            val esNuevaEspecie = especieExistente == null
            val cantidadSegura = cantidadEnParte.coerceAtLeast(1)

            val especieActualizada = if (esNuevaEspecie) {
                // Primera vez capturando esta especie. La cantidad de este
                // parte queda como el primer "mejor día".
                EspecieDescubierta(
                    especieId = especieId,
                    nombreComun = especieInfo.nombreComun,
                    nombreCientifico = especieInfo.nombreCientifico,
                    fechaDescubrimiento = Timestamp.now(),
                    totalCapturas = cantidadSegura,
                    pesoRecord = null,
                    primeraFoto = null,
                    locaciones = listOfNotNull(locacion),
                    rareza = especieInfo.rareza,
                    mejorDiaFecha = fechaParte,
                    mejorDiaCantidad = cantidadSegura
                )
            } else {
                // Actualizar especie existente. Si la cantidad de este parte
                // supera (o iguala) el récord previo, lo reemplazamos —
                // empate gana al más reciente.
                val superaMejorDia = cantidadSegura >= especieExistente!!.mejorDiaCantidad
                especieExistente.copy(
                    totalCapturas = especieExistente.totalCapturas + cantidadSegura,
                    locaciones = (especieExistente.locaciones + listOfNotNull(locacion)).distinct(),
                    mejorDiaFecha = if (superaMejorDia) fechaParte else especieExistente.mejorDiaFecha,
                    mejorDiaCantidad = if (superaMejorDia) cantidadSegura else especieExistente.mejorDiaCantidad
                )
            }
            
            // Calcular tamaño FINAL del map (para devolver totalEspecies y para
            // mantener consistencia con la lógica de logros que mira el contador).
            val especiesActualizadas = pescadexActual.especiesDescubiertas.toMutableMap()
            especiesActualizadas[especieId] = especieActualizada

            val pescadexActualizado = pescadexActual.copy(
                especiesDescubiertas = especiesActualizadas,
                ultimaActividad = Timestamp.now()
            )

            // IMPORTANTE: usamos `update` con dot notation y los nombres en
            // camelCase (matching el default de Kotlin), no snake_case. Es
            // consistente con cómo serializa/deserializa el SDK de Firestore
            // sin @PropertyName. Antes mezclábamos snake_case en los updates
            // explícitos y camelCase en la (de)serialización automática, y
            // por eso se escribía a `especies_descubiertas` pero se leía
            // buscando `especiesDescubiertas` → siempre vacío.
            firestore.document("$COLLECTION_PESCADEX/$pescadexId")
                .update(
                    "especiesDescubiertas.$especieId", especieActualizada,
                    "ultimaActividad", Timestamp.now()
                )
                .await()

            // Guardar en cache local (Room)
            storage.saveSinglePescadexRecord(especieActualizada.toEntity())

            // Verificar y desbloquear logros
            val logrosDesbloqueados = verificarLogros(pescadexActualizado)
            
            android.util.Log.i(TAG, "✅ Especie registrada: ${especieInfo.nombreComun}")
            
            RegistroResult.Success(
                esNuevaEspecie = esNuevaEspecie,
                especieInfo = especieInfo,
                totalEspecies = especiesActualizadas.size,
                logrosDesbloqueados = logrosDesbloqueados
            )
            
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error registrando especie: ${e.message}", e)
            RegistroResult.Error("Error guardando especie: ${e.localizedMessage}")
        }
    }
    
    /**
     * Obtiene la Pescadex actual del usuario, priorizando el cache local (Room)
     * y sincronizando con Firestore en segundo plano para evitar pérdida
     * de datos tras reinstall.
     */
    suspend fun obtenerPescadexUsuario(): PescadexUsuario {
        // 1. Intentar cargar desde Room (Cache rápido)
        val cacheLocal = storage.getPescadexRecords()
        if (cacheLocal.isNotEmpty()) {
            android.util.Log.d(TAG, "📦 Usando cache local de Room (${cacheLocal.size} especies)")
            // Retornamos el cache pero disparamos sync en segundo plano
            // (esto es una simplificación, lo ideal es que el UI reaccione al Flow)
            // Por ahora, devolvemos el objeto construido desde Room.
            return PescadexUsuario(
                deviceId = pescadexId,
                especiesDescubiertas = cacheLocal.associate { it.especieId to it.toDomain() },
                fechaInicio = null, // No persistido en Room
                ultimaActividad = Timestamp.now(),
                logrosDesbloqueados = emptyList() // Room solo guarda récords de peces
            )
        }

        // 2. Si Room está vacío (ej: tras reinstall), vamos a Firebase
        return syncPescadexConNube()
    }

    /**
     * Sincroniza Firestore -> Room. Descarga el Pescadex de la nube y lo
     * persiste localmente para que no se pierda al reinstalar.
     */
    suspend fun syncPescadexConNube(): PescadexUsuario {
        return try {
            val id = pescadexId
            android.util.Log.d(TAG, "📥 Sincronizando Pescadex desde nube: $id")
            val document = firestore.document("$COLLECTION_PESCADEX/$id").get().await()

            if (document.exists()) {
                val deserializado = document.toObject(PescadexUsuario::class.java)
                if (deserializado != null) {
                    // Guardar en Room para la próxima vez
                    val entities = deserializado.especiesDescubiertas.values.map { it.toEntity() }
                    storage.savePescadexRecords(entities)
                    android.util.Log.d(TAG, "✅ Cache local actualizado con ${entities.size} especies")
                    return deserializado
                }
            }
            
            val nueva = crearPescadexNuevo()
            // No la guardamos en Firestore aquí para evitar escrituras innecesarias,
            // ya se guardará al registrar el primer pez.
            nueva
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error sync Pescadex: ${e.message}")
            crearPescadexNuevo()
        }
    }

    // Extensiones de mapeo
    private fun EspecieDescubierta.toEntity() = PescadexRecordEntity(
        especieId = especieId,
        nombreComun = nombreComun,
        nombreCientifico = nombreCientifico,
        totalCapturas = totalCapturas,
        pesoRecord = pesoRecord,
        primeraFoto = primeraFoto,
        fechaDescubrimiento = fechaDescubrimiento?.toDate()?.time,
        mejorDiaCantidad = mejorDiaCantidad,
        mejorDiaFecha = mejorDiaFecha,
        rareza = rareza,
        locacionesRaw = locaciones.joinToString("|")
    )

    private fun PescadexRecordEntity.toDomain() = EspecieDescubierta(
        especieId = especieId,
        nombreComun = nombreComun,
        nombreCientifico = nombreCientifico,
        totalCapturas = totalCapturas,
        pesoRecord = pesoRecord,
        primeraFoto = primeraFoto,
        fechaDescubrimiento = fechaDescubrimiento?.let { Timestamp(java.util.Date(it)) },
        mejorDiaCantidad = mejorDiaCantidad,
        mejorDiaFecha = mejorDiaFecha,
        rareza = rareza,
        locaciones = if (locacionesRaw.isBlank()) emptyList() else locacionesRaw.split("|")
    )
    
    /**
     * Obtiene estadísticas completas de la Pescadex
     */
    suspend fun obtenerEstadisticasPescadex(): EstadisticasPescadex {
        return try {
            val pescadex = obtenerPescadexUsuario()
            val especiesDescubiertas = pescadex.especiesDescubiertas.size
            val totalEspecies = catalogoActual().size
            val progreso = if (totalEspecies > 0) especiesDescubiertas.toFloat() / totalEspecies * 100 else 0f
            
            val totalCapturas = pescadex.especiesDescubiertas.values.sumOf { it.totalCapturas }
            val especiesFavorita = pescadex.especiesDescubiertas.maxByOrNull { it.value.totalCapturas }?.key
            val logrosCount = pescadex.logrosDesbloqueados.size
            
            // Calcular días pescando (aproximado)
            val diasPescando = pescadex.especiesDescubiertas.values.mapNotNull { it.fechaDescubrimiento }
                .map { it.toDate().time }
                .distinct()
                .size
            
            EstadisticasPescadex(
                especiesDescubiertas = especiesDescubiertas,
                totalEspecies = totalEspecies,
                porcentajeCompletado = progreso,
                totalCapturas = totalCapturas,
                especiesFavorita = especiesFavorita,
                logrosDesbloqueados = logrosCount,
                diasPescando = diasPescando
            )
            
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e(TAG, "Error obteniendo estadísticas: ${e.message}")
            EstadisticasPescadex(0, 0, 0f, 0, null, 0, 0)
        }
    }
    
    /**
     * Obtiene especies disponibles con estado de captura
     */
    suspend fun obtenerEspeciesConEstado(): List<EspecieConEstado> {
        return try {
            val pescadex = obtenerPescadexUsuario()
            val catalogo = catalogoActual()

            android.util.Log.d(
                TAG,
                "🧮 obtenerEspeciesConEstado: catalogo=${catalogo.size} especies, " +
                    "usuario tiene ${pescadex.especiesDescubiertas.size} capturadas"
            )
            if (pescadex.especiesDescubiertas.isNotEmpty()) {
                android.util.Log.d(
                    TAG,
                    "🎣 Ids capturados: ${pescadex.especiesDescubiertas.keys}"
                )
            }

            val resultado = catalogo.map { (id, info) ->
                val especieCapturada = pescadex.especiesDescubiertas[id]
                EspecieConEstado(
                    info = info,
                    esCapturada = especieCapturada != null,
                    datosCaptura = especieCapturada,
                    orden = obtenerOrdenRareza(info.rareza)
                )
            }.sortedWith(compareBy({ it.orden }, { it.info.nombreComun }))

            android.util.Log.d(TAG, "📦 Devolviendo ${resultado.size} EspecieConEstado")
            resultado
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error obteniendo especies: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Mapping del id legacy del Pescadex al id unificado en AchievementCatalog.
     * Los achievements del Pescadex ahora viven en /users/{uid}/unlocked_achievements
     * junto al resto, y aparecen en la pantalla "Mis Logros" con categoría ESPECIES.
     */
    private val LOGRO_TO_ACHIEVEMENT = mapOf(
        "primer_pez" to "pescadex_primer_pez",
        "explorador" to "pescadex_explorador",
        "coleccionista" to "pescadex_coleccionista",
        "especialista" to "pescadex_especialista",
        "maestro" to "pescadex_maestro",
        "cazador_raros" to "pescadex_cazador_raros",
        "cazador_epicos" to "pescadex_cazador_epicos",
        "completista" to "pescadex_completista"
    )

    private suspend fun verificarLogros(pescadex: PescadexUsuario): List<LogroPescadex> {
        val logrosDesbloqueados = mutableListOf<LogroPescadex>()
        val especiesCount = pescadex.especiesDescubiertas.size
        val tieneEspecieRara = pescadex.especiesDescubiertas.values.any {
            it.rareza in listOf("raro", "epico", "legendario")
        }
        val tieneEspecieEpica = pescadex.especiesDescubiertas.values.any {
            it.rareza in listOf("epico", "legendario")
        }

        LOGROS_DISPONIBLES.forEach { logro ->
            val cumpleCondicion = when (logro.condicion) {
                "primera_captura" -> especiesCount >= 1
                "5_especies" -> especiesCount >= 5
                "10_especies" -> especiesCount >= 10
                "15_especies" -> especiesCount >= 15
                "20_especies" -> especiesCount >= 20
                "completista" -> especiesCount >= 30
                "especie_rara" -> tieneEspecieRara
                "especie_epica" -> tieneEspecieEpica
                else -> false
            }

            if (cumpleCondicion && !pescadex.logrosDesbloqueados.contains(logro.id)) {
                logrosDesbloqueados.add(logro.copy(
                    desbloqueado = true,
                    fechaDesbloqueo = Timestamp.now()
                ))

                // Disparar también el achievement unificado en /users/{uid}/unlocked_achievements
                // para que aparezca en la pantalla "Mis Logros" general.
                val achievementId = LOGRO_TO_ACHIEVEMENT[logro.id]
                if (achievementId != null) {
                    try {
                        val ref = firestore.collection("users")
                            .document(auth.currentUser?.uid ?: return@forEach)
                            .collection("unlocked_achievements")
                            .document(achievementId)
                        val doc = ref.get().await()
                        if (!doc.exists()) {
                            ref.set(mapOf("timestamp" to System.currentTimeMillis())).await()
                        }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        android.util.Log.w(TAG, "No se pudo persistir achievement $achievementId: ${e.message}")
                    }
                }
            }
        }

        // Actualizar logros legacy del Pescadex (compat con pantalla actual del Pescadex)
        if (logrosDesbloqueados.isNotEmpty()) {
            val nuevosLogros = pescadex.logrosDesbloqueados + logrosDesbloqueados.map { it.id }
            firestore.document("$COLLECTION_PESCADEX/$pescadexId")
                .update("logrosDesbloqueados", nuevosLogros)
                .await()
        }

        return logrosDesbloqueados
    }
    
    private fun crearPescadexNuevo(): PescadexUsuario {
        return PescadexUsuario(
            deviceId = pescadexId,  // legacy: el campo se llama deviceId pero hoy guarda el UID
            especiesDescubiertas = emptyMap(),
            fechaInicio = Timestamp.now(),
            ultimaActividad = Timestamp.now(),
            logrosDesbloqueados = emptyList()
        )
    }

    /**
     * Auto-registro de las especies de un parte. Llamado desde el flujo
     * de guardarParteCompletado: por cada especie del parte se intenta
     * matchear con el catálogo de `FishDatabase` por nombre normalizado;
     * si hay match, se llama a `registrarEspecieCapturada` pasando la
     * cantidad de ejemplares y la fecha del parte (para llevar el récord
     * de "mejor día"). Los nombres que no estén en el catálogo se descartan
     * en silencio — el Pescadex es una colección curada de especies
     * argentinas conocidas.
     */
    suspend fun registrarEspeciesDeParte(
        especiesDelParte: List<EspecieDelParte>,
        locacion: String? = null,
        fechaParte: String? = null
    ): List<RegistroResult> {
        val catalogo = catalogoActual()
        val resultados = mutableListOf<RegistroResult>()
        // Si por alguna razón llegan duplicados de la misma especie en el
        // parte, los agrupamos sumando cantidades para no contar dos veces.
        especiesDelParte
            .groupBy { it.nombre.trim().lowercase() }
            .forEach { (_, grupo) ->
                val nombre = grupo.first().nombre
                val cantidad = grupo.sumOf { it.cantidad }.coerceAtLeast(1)
                val id = matchearEspecie(nombre, catalogo) ?: return@forEach
                resultados.add(
                    registrarEspecieCapturada(
                        especieId = id,
                        cantidadEnParte = cantidad,
                        locacion = locacion,
                        fechaParte = fechaParte
                    )
                )
            }
        return resultados
    }

    /**
     * Edita el récord personal de una especie capturada: peso máximo y/o
     * foto del récord. Pensado para el botón "Editar récord" del modal
     * de detalle de la pantalla del Pescadex.
     *
     * - Si `fotoLocalPath` es no-null, sube la foto a Firebase Storage vía
     *   `StorageService` y guarda la URL pública resultante. Si la subida
     *   falla, se conserva la foto previa.
     * - Si `peso` es no-null, reemplaza el `pesoRecord` previo (el usuario
     *   es quien decide qué es su récord — no aplicamos max automático
     *   para que pueda corregir un dato mal cargado).
     * - Solo aplica si la especie ya fue capturada al menos una vez.
     *   Si no existe en el Pescadex del usuario, devuelve Error.
     *
     * No toca `totalCapturas`, `locaciones`, `mejorDia*` ni la fecha de
     * descubrimiento — esos vienen del flujo del parte.
     */
    suspend fun actualizarRecordPersonal(
        especieId: String,
        peso: Double? = null,
        fotoLocalPath: String? = null
    ): RegistroResult {
        return try {
            val pescadexActual = obtenerPescadexUsuario()
            val especieExistente = pescadexActual.especiesDescubiertas[especieId]
                ?: return RegistroResult.Error("Esta especie todavía no está en tu Pescadex")

            // Subir foto si vino una nueva. Si falla la subida, no pisamos la
            // foto anterior — preferible perder la nueva que dejar al usuario
            // sin ningún registro visual.
            val nuevaUrlFoto = if (fotoLocalPath != null) {
                val storage = com.example.juka.data.firebase.StorageService()
                storage.subirImagen(fotoLocalPath) ?: especieExistente.primeraFoto
            } else {
                especieExistente.primeraFoto
            }

            val actualizada = especieExistente.copy(
                pesoRecord = peso ?: especieExistente.pesoRecord,
                primeraFoto = nuevaUrlFoto
            )

            // Misma técnica que en registrarEspecieCapturada: dot notation
            // con camelCase para alinear con cómo (de)serializa el SDK.
            firestore.document("$COLLECTION_PESCADEX/$pescadexId")
                .update(
                    "especiesDescubiertas.$especieId", actualizada,
                    "ultimaActividad", Timestamp.now()
                )
                .await()

            // Guardar en cache local (Room)
            storage.saveSinglePescadexRecord(actualizada.toEntity())

            val mapaActualizado = pescadexActual.especiesDescubiertas.toMutableMap()
            mapaActualizado[especieId] = actualizada

            val catalogo = catalogoActual()
            val info = catalogo[especieId]
                ?: return RegistroResult.Error("Catálogo desactualizado")

            android.util.Log.i(TAG, "✅ Récord personal actualizado: $especieId (peso=$peso)")
            RegistroResult.Success(
                esNuevaEspecie = false,
                especieInfo = info,
                totalEspecies = mapaActualizado.size,
                logrosDesbloqueados = emptyList()
            )
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            android.util.Log.e(TAG, "💥 Error actualizando récord: ${e.message}", e)
            RegistroResult.Error("No se pudo guardar el récord: ${e.localizedMessage}")
        }
    }

    /**
     * Devuelve el id del catálogo correspondiente al nombre dado, o null
     * si no hay coincidencia.
     *
     * Estrategia: normaliza ambos lados (el nombre del usuario Y los ids
     * del catálogo) y compara. Esto evita el bug histórico donde el
     * catálogo tenía "manguruyú" con acento y el nombre normalizado del
     * usuario llegaba como "manguruyu" sin acento → no matcheaban nunca.
     *
     * Soporta dos modos de match:
     *   - Exacto: "Pejerrey" → "pejerrey" → id "pejerrey".
     *   - Parcial: "dorado del Paraná" contiene "dorado" → id "dorado".
     */
    private fun matchearEspecie(
        nombre: String,
        catalogo: Map<String, EspecieInfo>
    ): String? {
        val normalizado = normalizarParaId(nombre)
        if (normalizado.isEmpty()) return null

        // Match exacto contra ids del catálogo.
        if (catalogo.containsKey(normalizado)) return normalizado

        // Match parcial: alguno de los tokens del nombre coincide con un id.
        // Útil para "dorado del Paraná" → "dorado", "trucha marrón" → "trucha".
        return catalogo.keys.firstOrNull { id ->
            normalizado.contains(id) || id.contains(normalizado)
        }
    }
    
    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown_device_${System.currentTimeMillis()}"
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            "fallback_device_${System.currentTimeMillis()}"
        }
    }
    
    private fun obtenerOrdenRareza(rareza: String): Int {
        return when (rareza) {
            "comun" -> 1
            "poco_comun" -> 2
            "raro" -> 3
            "epico" -> 4
            "legendario" -> 5
            else -> 0
        }
    }
    
}

// Resultado de registro
sealed class RegistroResult {
    data class Success(
        val esNuevaEspecie: Boolean,
        val especieInfo: EspecieInfo,
        val totalEspecies: Int,
        val logrosDesbloqueados: List<LogroPescadex>
    ) : RegistroResult()
    
    data class Error(val mensaje: String) : RegistroResult()
}

// Estado de especie
data class EspecieConEstado(
    val info: EspecieInfo,
    val esCapturada: Boolean,
    val datosCaptura: EspecieDescubierta?,
    val orden: Int
)

// Estadísticas
data class EstadisticasPescadex(
    val especiesDescubiertas: Int,
    val totalEspecies: Int,
    val porcentajeCompletado: Float,
    val totalCapturas: Int,
    val especiesFavorita: String?,
    val logrosDesbloqueados: Int,
    val diasPescando: Int
) {
    val progreso: String get() = "$especiesDescubiertas/$totalEspecies"
}