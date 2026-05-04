package com.example.juka.domain.usecase

import com.example.juka.CampoParte
import com.example.juka.MLKitManager
import com.example.juka.domain.model.MLKitExtractionResult
import com.example.juka.domain.model.ModalidadPesca
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.Provincia

/**
 * Encapsula TODA la lógica matemática y de transformación de datos del Parte.
 * El ViewModel solo le pasa datos y recibe resultados.
 */
class ParteLogicUseCase(
    private val mlKitManager: MLKitManager
) {

    data class ProgresoInfo(val porcentaje: Int, val camposFaltantes: List<String>)

    fun mergearDatos(existente: ParteEnProgreso, nuevo: ParteEnProgreso): ParteEnProgreso {
        return ParteEnProgreso(
            fecha = nuevo.fecha ?: existente.fecha,
            horaInicio = nuevo.horaInicio ?: existente.horaInicio,
            horaFin = nuevo.horaFin ?: existente.horaFin,
            provincia = nuevo.provincia ?: existente.provincia,
            ubicacion = nuevo.ubicacion ?: existente.ubicacion,
            nombreLugar = nuevo.nombreLugar ?: existente.nombreLugar,
            modalidad = nuevo.modalidad ?: existente.modalidad,
            numeroCanas = nuevo.numeroCanas ?: existente.numeroCanas,
            especiesCapturadas = (existente.especiesCapturadas + nuevo.especiesCapturadas).distinctBy { it.nombre },
            imagenes = existente.imagenes + nuevo.imagenes,
            observaciones = nuevo.observaciones ?: existente.observaciones,
            noIdentificoEspecie = nuevo.noIdentificoEspecie || existente.noIdentificoEspecie,
            // ✅ CORREGIDO: Ahora el flag "sinCapturas" sobrevive al mergeo
            sinCapturas = nuevo.sinCapturas || existente.sinCapturas
        )
    }

    fun filtrarEntidadesPorCampo(
        extractionResult: MLKitExtractionResult,
        campo: CampoParte
    ): MLKitExtractionResult {
        val entidadesFiltradas = when (campo) {
            CampoParte.ESPECIES -> extractionResult.entidadesDetectadas.filter { it.tipo in listOf("ESPECIE", "CANTIDAD_PECES") }
            CampoParte.FECHA -> extractionResult.entidadesDetectadas.filter { it.tipo == "FECHA" }
            CampoParte.HORARIOS -> extractionResult.entidadesDetectadas.filter { it.tipo in listOf("HORA_INICIO", "HORA_FIN", "HORA") }
            CampoParte.MODALIDAD -> extractionResult.entidadesDetectadas.filter { it.tipo == "MODALIDAD" }
            CampoParte.CANAS -> extractionResult.entidadesDetectadas.filter { it.tipo == "NUMERO_CANAS" }
            CampoParte.UBICACION -> extractionResult.entidadesDetectadas.filter { it.tipo in listOf("LUGAR", "PROVINCIA") }
            CampoParte.OBSERVACIONES -> extractionResult.entidadesDetectadas
            else -> emptyList()
        }

        return MLKitExtractionResult(
            textoExtraido = extractionResult.textoExtraido,
            entidadesDetectadas = entidadesFiltradas,
            confianza = if (entidadesFiltradas.isNotEmpty()) extractionResult.confianza else 0f
        )
    }

    fun actualizarDatosPorCampo(
        datosActuales: ParteEnProgreso,
        campo: CampoParte,
        extractionResult: MLKitExtractionResult
    ): ParteEnProgreso {
        var datosActualizados = datosActuales

        when (campo) {
            CampoParte.ESPECIES -> {
                val entidadesEspecies = extractionResult.entidadesDetectadas.filter { it.tipo in listOf("ESPECIE", "CANTIDAD_PECES") }
                if (entidadesEspecies.isNotEmpty()) {
                    val nuevosData = mlKitManager.convertirEntidadesAParteDatos(entidadesEspecies)
                    val especiesExistentes = datosActualizados.especiesCapturadas.toMutableList()
                    nuevosData.especiesCapturadas.forEach { nueva ->
                        val existe = especiesExistentes.find { it.nombre == nueva.nombre }
                        if (existe != null) {
                            val idx = especiesExistentes.indexOf(existe)
                            especiesExistentes[idx] = existe.copy(numeroEjemplares = existe.numeroEjemplares + nueva.numeroEjemplares)
                        } else {
                            especiesExistentes.add(nueva)
                        }
                    }
                    // Si agregan una especie manualmente, quitamos el flag de "sin capturas"
                    datosActualizados = datosActualizados.copy(
                        especiesCapturadas = especiesExistentes,
                        sinCapturas = false
                    )
                }
            }
            CampoParte.FECHA -> extractionResult.entidadesDetectadas.firstOrNull { it.tipo == "FECHA" }?.let { datosActualizados = datosActualizados.copy(fecha = it.valor) }
            CampoParte.HORARIOS -> {
                extractionResult.entidadesDetectadas.forEach { entity ->
                    when (entity.tipo) {
                        "HORA_INICIO" -> datosActualizados = datosActualizados.copy(horaInicio = entity.valor)
                        "HORA_FIN" -> datosActualizados = datosActualizados.copy(horaFin = entity.valor)
                        "HORA" -> if (datosActualizados.horaInicio == null) datosActualizados = datosActualizados.copy(horaInicio = entity.valor)
                        else if (datosActualizados.horaFin == null) datosActualizados = datosActualizados.copy(horaFin = entity.valor)
                    }
                }
            }
            CampoParte.MODALIDAD -> extractionResult.entidadesDetectadas.firstOrNull { it.tipo == "MODALIDAD" }?.let { datosActualizados = datosActualizados.copy(modalidad = ModalidadPesca.fromString(it.valor)) }
            CampoParte.CANAS -> extractionResult.entidadesDetectadas.firstOrNull { it.tipo == "NUMERO_CANAS" }?.let { datosActualizados = datosActualizados.copy(numeroCanas = it.valor.toIntOrNull() ?: 0) }
            CampoParte.UBICACION -> {
                extractionResult.entidadesDetectadas.forEach {
                    if (it.tipo == "LUGAR") datosActualizados = datosActualizados.copy(nombreLugar = it.valor)
                    if (it.tipo == "PROVINCIA") datosActualizados = datosActualizados.copy(provincia = Provincia.fromString(it.valor))
                }
            }
            CampoParte.OBSERVACIONES -> datosActualizados = datosActualizados.copy(observaciones = extractionResult.textoExtraido)
            else -> {}
        }
        return datosActualizados
    }

    // ✅ CORREGIDO: Unificamos todo en una sola función de progreso sólida
    fun calcularProgreso(datos: ParteEnProgreso): ProgresoInfo {
        // Si marcó "sin capturas", damos la sección de especies por completada
        val estadoEspecies = if (datos.sinCapturas || datos.especiesCapturadas.isNotEmpty()) "completado" else null

        val camposObligatorios = listOf(
            "fecha" to datos.fecha,
            "modalidad" to datos.modalidad?.displayName,
            "especies" to estadoEspecies
        )

        val camposOpcionales = listOf(
            "provincia" to datos.provincia?.displayName,
            "hora_inicio" to datos.horaInicio,
            "hora_fin" to datos.horaFin,
            "numero de cañas" to datos.numeroCanas?.toString(),
            "imagenes" to if (datos.imagenes.isNotEmpty()) "completado" else null,
            "ubicacion" to datos.nombreLugar
        )

        val totalCompletos = camposObligatorios.count { it.second != null } + camposOpcionales.count { it.second != null }
        val totalCampos = camposObligatorios.size + camposOpcionales.size

        val porcentaje = (totalCompletos.toFloat() / totalCampos * 100).toInt()

        // Creamos la lista de faltantes (ahora sí es segura)
        val faltantes = (camposObligatorios.filter { it.second == null }.map { it.first } +
                camposOpcionales.filter { it.second == null }.map { it.first })

        return ProgresoInfo(porcentaje, faltantes)
    }

    private fun generarResumenProgreso(datos: ParteEnProgreso?): String {
        if (datos == null) return ""
        val progreso = calcularProgreso(datos)

        val resumen = StringBuilder()
        resumen.append("📋 **Progreso: ${progreso.porcentaje}%**\n")

        if (progreso.camposFaltantes.isNotEmpty()) {
            resumen.append("\n📝 **Para completar tu parte, contame:**\n")

            // Tomamos los 2 más importantes para no abrumar al usuario
            progreso.camposFaltantes.take(2).forEach { campo ->
                val pregunta = when (campo.lowercase()) {
                    "fecha" -> "• ¿Qué día saliste a pescar?"
                    "modalidad" -> "• ¿Pescaste de costa o embarcado?"
                    "especies" -> "• ¿Qué especies lograste capturar?"
                    "ubicacion", "nombrelugar" -> "• ¿En qué lugar o playa estuviste?"
                    "horarios" -> "• ¿En qué horario estuviste pescando?"
                    else -> "• ¿Podrías decirme el dato de $campo?"
                }
                resumen.append("$pregunta\n")
            }
        } else {
            resumen.append("\n🎉 **¡Excelente! Tu reporte está completo.** Ya podés enviarlo.")
        }
        return resumen.toString()
    }

    fun generarRespuestaParte(
        extractionResult: MLKitExtractionResult,
        datosActuales: ParteEnProgreso?
    ): String {
        if (extractionResult.entidadesDetectadas.isEmpty()) {
            return """
🤖 No detecté información específica de pesca en tu mensaje.

¿Podrías contarme más detalladamente? Por ejemplo:
• **Cuándo:** "Ayer de mañana" o "El sábado"
• **Dónde:** "En el río Paraná" o "Playa tal"
• **Qué pescaste:** "Dos surubíes" o "Me fui zapatero"
• **Cómo:** "Desde costa" o "Embarcado"
            """.trimIndent()
        }

        val respuesta = StringBuilder()
        respuesta.append("🤖 **Información extraída automáticamente:**\n\n")

        // Mostrar lo que se detectó
        extractionResult.entidadesDetectadas.forEach { entity ->
            val emoji = when (entity.tipo) {
                "FECHA" -> "📅"
                "HORA_INICIO", "HORA_FIN", "HORA" -> "⏰"
                "LUGAR" -> "📍"
                "PROVINCIA" -> "🗺️"
                "MODALIDAD" -> "🎣"
                "ESPECIE" -> "🐟"
                "NUMERO_CANAS" -> "🎯"
                "CANTIDAD_PECES" -> "📊"
                else -> "ℹ️"
            }

            respuesta.append(
                "$emoji **${
                    entity.tipo.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                }:** ${entity.valor}\n"
            )
        }

        respuesta.append("\n")
        respuesta.append(generarResumenProgreso(datosActuales))

        return respuesta.toString()
    }
}