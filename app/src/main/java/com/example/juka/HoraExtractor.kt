// HoraExtractor.kt - Detección mejorada de horas para pesca
package com.example.juka

import android.util.Log
import java.util.regex.Pattern


class HoraExtractor {

    companion object {
        private const val TAG = "⏰ HoraExtractor"

        // Patrones ordenados por prioridad y especificidad
        private val PATRONES_HORA_EXACTA = listOf(
            // Formatos HH:MM y HH.MM
            Pattern.compile("""(?:a\s+las\s+)?(\d{1,2})[:\.](\d{2})(?:\s*(?:hs?|horas?))?""", Pattern.CASE_INSENSITIVE),

            // Solo hora sin minutos
            Pattern.compile("""(?:a\s+las\s+)?(\d{1,2})(?:\s*(?:hs?|horas?))(?!\d)""", Pattern.CASE_INSENSITIVE),

            // Formato AM/PM
            Pattern.compile("""(?:a\s+las\s+)?(\d{1,2})(?:[:\.](\d{2}))?\s*([ap]m)""", Pattern.CASE_INSENSITIVE),

            // Con "a las" explícito
            Pattern.compile("""a\s+las\s+(\d{1,2})(?:[:\.](\d{2}))?""", Pattern.CASE_INSENSITIVE)
        )

        private val PATRONES_RANGO_HORA = listOf(
            // "de X a Y" - más flexible
            Pattern.compile("""de(?:\s+las)?\s+(\d{1,2})(?:[:\.](\d{2}))?\s+a(?:\s+las)?\s+(\d{1,2})(?:[:\.](\d{2}))?""", Pattern.CASE_INSENSITIVE),

            // "desde las X hasta las Y"
            Pattern.compile("""desde\s+las\s+(\d{1,2})(?:[:\.](\d{2}))?\s+hasta\s+las\s+(\d{1,2})(?:[:\.](\d{2}))?""", Pattern.CASE_INSENSITIVE),

            // "entre las X y las Y"
            Pattern.compile("""entre\s+las\s+(\d{1,2})(?:[:\.](\d{2}))?\s+y\s+las\s+(\d{1,2})(?:[:\.](\d{2}))?""", Pattern.CASE_INSENSITIVE),

            // "de X:XX a Y:XX" (formato corto)
            Pattern.compile("""(\d{1,2})[:\.](\d{2})\s*-\s*(\d{1,2})[:\.](\d{2})"""),

            // Rangos aproximados
            Pattern.compile("""(?:cerca\s+de\s+las|sobre\s+las)\s+(\d{1,2})(?:[:\.](\d{2}))?""", Pattern.CASE_INSENSITIVE)
        )

        private val PATRONES_HORA_TEXTO = listOf(
            // Números en texto con contexto
            Pattern.compile("""(?:a\s+las\s+)?(una?|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\s+y\s+(media|cuarto|treinta|quince|45|cuarenta\s+y\s+cinco))?(?:\s+de\s+la\s+(mañana|tarde|noche))?""", Pattern.CASE_INSENSITIVE),

            // Mediodía y medianoche
            Pattern.compile("""(mediodía|medianoche)""", Pattern.CASE_INSENSITIVE),

            // Amanecer, atardecer
            Pattern.compile("""(amanecer|alba|atardecer|anochecer)""", Pattern.CASE_INSENSITIVE)
        )

        private val PATRONES_PERIODO_DIA = listOf(
            // Períodos amplios
            Pattern.compile("""(toda\s+la\s+mañana|por\s+la\s+mañana|en\s+la\s+mañana)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(toda\s+la\s+tarde|por\s+la\s+tarde|en\s+la\s+tarde)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(toda\s+la\s+noche|por\s+la\s+noche|en\s+la\s+noche)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(todo\s+el\s+día|durante\s+el\s+día)""", Pattern.CASE_INSENSITIVE)
        )

        // Mapeos para conversión de texto
        private val NUMEROS_TEXTO = mapOf(
            "una" to 1, "un" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4,
            "cinco" to 5, "seis" to 6, "siete" to 7, "ocho" to 8,
            "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12
        )

        private val MINUTOS_TEXTO = mapOf(
            "cuarto" to 15, "media" to 30, "treinta" to 30, "quince" to 15,
            "cuarenta y cinco" to 45, "45" to 45
        )

        private val HORAS_ESPECIALES = mapOf(
            "mediodía" to "12:00",
            "medianoche" to "00:00",
            "amanecer" to "06:00",
            "alba" to "06:00",
            "atardecer" to "18:00",
            "anochecer" to "19:00"
        )

        private val PERIODOS_DIA = mapOf(
            "toda la mañana" to Pair("06:00", "12:00"),
            "por la mañana" to Pair("06:00", "12:00"),
            "en la mañana" to Pair("06:00", "12:00"),
            "toda la tarde" to Pair("12:00", "18:00"),
            "por la tarde" to Pair("12:00", "18:00"),
            "en la tarde" to Pair("12:00", "18:00"),
            "toda la noche" to Pair("18:00", "23:59"),
            "por la noche" to Pair("18:00", "23:59"),
            "en la noche" to Pair("18:00", "23:59"),
            "todo el día" to Pair("06:00", "18:00"),
            "durante el día" to Pair("06:00", "18:00")
        )
    }

    data class HoraExtraida(
        val tipo: TipoHora,
        val horaInicio: String?,
        val horaFin: String?,
        val textoOriginal: String,
        val confianza: Float,
        val posicionInicio: Int,
        val posicionFin: Int
    )

    enum class TipoHora {
        HORA_ESPECIFICA,    // "a las 8:30"
        RANGO_HORAS,       // "de 8 a 12"
        PERIODO_DIA,       // "por la mañana"
        HORA_APROXIMADA    // "cerca de las 8"
    }

    fun extraerHoras(texto: String): List<HoraExtraida> {
        val horasExtraidas = mutableListOf<HoraExtraida>()
        val textoLower = texto.lowercase()

        Log.d(TAG, "🔍 Analizando texto: '$texto'")

        // Pasada 1: Rangos de horas (mayor prioridad)
        horasExtraidas.addAll(extraerRangosHora(texto, textoLower))

        // Pasada 2: Horas exactas (solo si no hay rangos en esa posición)
        val horasExactas = extraerHorasExactas(texto, textoLower)
        horasExtraidas.addAll(filtrarSolapamientos(horasExactas, horasExtraidas))

        // Pasada 3: Horas en texto
        val horasTexto = extraerHorasTexto(texto, textoLower)
        horasExtraidas.addAll(filtrarSolapamientos(horasTexto, horasExtraidas))

        // Pasada 4: Períodos del día
        val periodos = extraerPeriodosDia(texto, textoLower)
        horasExtraidas.addAll(filtrarSolapamientos(periodos, horasExtraidas))

        // Ordenar por posición en el texto
        val resultado = horasExtraidas.sortedBy { it.posicionInicio }

        Log.d(TAG, "✅ Extraídas ${resultado.size} referencias de hora:")
        resultado.forEach { hora ->
            Log.d(TAG, "  - ${hora.tipo}: ${hora.horaInicio}${if (hora.horaFin != null) " a ${hora.horaFin}" else ""} " +
                    "(${(hora.confianza * 100).toInt()}%) '${hora.textoOriginal}'")
        }

        return resultado
    }

    private fun extraerHorasExactas(texto: String, textoLower: String): List<HoraExtraida> {
        val horas = mutableListOf<HoraExtraida>()

        PATRONES_HORA_EXACTA.forEachIndexed { indice, patron ->
            val matcher = patron.matcher(textoLower)

            while (matcher.find()) {
                try {
                    val horaStr = matcher.group(1)
                    val minutoStr = matcher.group(2) ?: "00"
                    val amPm = if (matcher.groupCount() >= 3) matcher.group(3) else null

                    Log.d(TAG, "🎯 Patrón $indice encontró: hora='$horaStr', minuto='$minutoStr', amPm='$amPm'")

                    val hora = horaStr.toInt()
                    val minuto = minutoStr.toInt()

                    if (validarHora(hora, minuto)) {
                        val horaFinal = ajustarAmPm(hora, amPm)
                        val horaFormateada = formatearHora(horaFinal, minuto)

                        val confianza = when (indice) {
                            0 -> 0.95f // HH:MM con marcadores
                            1 -> 0.90f // Solo hora
                            2 -> 0.85f // AM/PM
                            else -> 0.80f
                        }

                        horas.add(
                            HoraExtraida(
                                tipo = TipoHora.HORA_ESPECIFICA,
                                horaInicio = horaFormateada,
                                horaFin = null,
                                textoOriginal = matcher.group(),
                                confianza = confianza,
                                posicionInicio = matcher.start(),
                                posicionFin = matcher.end()
                            )
                        )

                        Log.d(TAG, "✅ Hora exacta detectada: $horaFormateada")
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error procesando hora exacta: ${e.message}")
                }
            }
        }

        return horas
    }

    private fun extraerRangosHora(texto: String, textoLower: String): List<HoraExtraida> {
        val rangos = mutableListOf<HoraExtraida>()

        PATRONES_RANGO_HORA.forEachIndexed { indice, patron ->
            val matcher = patron.matcher(textoLower)

            while (matcher.find()) {
                try {
                    Log.d(TAG, "🎯 Patrón rango $indice - Grupos: ${matcher.groupCount()}")
                    for (i in 0..matcher.groupCount()) {
                        Log.d(TAG, "  Grupo $i: '${matcher.group(i)}'")
                    }

                    when (matcher.groupCount()) {
                        4 -> { // de H:M a H:M
                            val hora1 = matcher.group(1).toInt()
                            val min1 = matcher.group(2)?.toInt() ?: 0
                            val hora2 = matcher.group(3).toInt()
                            val min2 = matcher.group(4)?.toInt() ?: 0

                            if (validarHora(hora1, min1) && validarHora(hora2, min2)) {
                                val horaInicio = formatearHora(hora1, min1)
                                val horaFin = formatearHora(hora2, min2)

                                rangos.add(
                                    HoraExtraida(
                                        tipo = TipoHora.RANGO_HORAS,
                                        horaInicio = horaInicio,
                                        horaFin = horaFin,
                                        textoOriginal = matcher.group(),
                                        confianza = 0.90f,
                                        posicionInicio = matcher.start(),
                                        posicionFin = matcher.end()
                                    )
                                )

                                Log.d(TAG, "✅ Rango detectado: $horaInicio a $horaFin")
                            }
                        }
                        2 -> { // Hora aproximada "cerca de las X"
                            val hora = matcher.group(1).toInt()
                            val min = matcher.group(2)?.toInt() ?: 0

                            if (validarHora(hora, min)) {
                                val horaFormateada = formatearHora(hora, min)

                                rangos.add(
                                    HoraExtraida(
                                        tipo = TipoHora.HORA_APROXIMADA,
                                        horaInicio = horaFormateada,
                                        horaFin = null,
                                        textoOriginal = matcher.group(),
                                        confianza = 0.70f,
                                        posicionInicio = matcher.start(),
                                        posicionFin = matcher.end()
                                    )
                                )

                                Log.d(TAG, "✅ Hora aproximada detectada: $horaFormateada")
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error procesando rango: ${e.message}")
                }
            }
        }

        return rangos
    }

    private fun extraerHorasTexto(texto: String, textoLower: String): List<HoraExtraida> {
        val horas = mutableListOf<HoraExtraida>()

        // Horas especiales primero
        HORAS_ESPECIALES.forEach { (palabra, hora) ->
            if (textoLower.contains(palabra)) {
                val inicio = textoLower.indexOf(palabra)
                horas.add(
                    HoraExtraida(
                        tipo = TipoHora.HORA_ESPECIFICA,
                        horaInicio = hora,
                        horaFin = null,
                        textoOriginal = palabra,
                        confianza = 0.85f,
                        posicionInicio = inicio,
                        posicionFin = inicio + palabra.length
                    )
                )
                Log.d(TAG, "✅ Hora especial detectada: $palabra -> $hora")
            }
        }

        // Números en texto
        PATRONES_HORA_TEXTO[0].let { patron ->
            val matcher = patron.matcher(textoLower)

            while (matcher.find()) {
                try {
                    val numeroTexto = matcher.group(1)
                    val minutoTexto = matcher.group(2)
                    val periodo = matcher.group(3)

                    val hora = NUMEROS_TEXTO[numeroTexto]
                    val minuto = if (minutoTexto != null) MINUTOS_TEXTO[minutoTexto] ?: 0 else 0

                    if (hora != null) {
                        val horaAjustada = ajustarPorPeriodo(hora, periodo)
                        val horaFormateada = formatearHora(horaAjustada, minuto)

                        horas.add(
                            HoraExtraida(
                                tipo = TipoHora.HORA_ESPECIFICA,
                                horaInicio = horaFormateada,
                                horaFin = null,
                                textoOriginal = matcher.group(),
                                confianza = 0.80f,
                                posicionInicio = matcher.start(),
                                posicionFin = matcher.end()
                            )
                        )

                        Log.d(TAG, "✅ Hora en texto detectada: ${matcher.group()} -> $horaFormateada")
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error procesando hora en texto: ${e.message}")
                }
            }
        }

        return horas
    }

    private fun extraerPeriodosDia(texto: String, textoLower: String): List<HoraExtraida> {
        val periodos = mutableListOf<HoraExtraida>()

        PERIODOS_DIA.forEach { (patron, rango) ->
            if (textoLower.contains(patron)) {
                val inicio = textoLower.indexOf(patron)
                periodos.add(
                    HoraExtraida(
                        tipo = TipoHora.PERIODO_DIA,
                        horaInicio = rango.first,
                        horaFin = rango.second,
                        textoOriginal = patron,
                        confianza = 0.60f,
                        posicionInicio = inicio,
                        posicionFin = inicio + patron.length
                    )
                )
                Log.d(TAG, "✅ Período detectado: $patron -> ${rango.first} a ${rango.second}")
            }
        }

        return periodos
    }

    private fun filtrarSolapamientos(nuevas: List<HoraExtraida>, existentes: List<HoraExtraida>): List<HoraExtraida> {
        return nuevas.filter { nueva ->
            !existentes.any { existente ->
                // Verificar solapamiento de posiciones
                !(nueva.posicionFin <= existente.posicionInicio || nueva.posicionInicio >= existente.posicionFin)
            }
        }
    }

    private fun validarHora(hora: Int, minuto: Int): Boolean {
        return hora in 0..23 && minuto in 0..59
    }

    private fun ajustarAmPm(hora: Int, amPm: String?): Int {
        return when (amPm?.lowercase()) {
            "am" -> if (hora == 12) 0 else hora
            "pm" -> if (hora == 12) 12 else hora + 12
            else -> hora
        }
    }

    private fun ajustarPorPeriodo(hora: Int, periodo: String?): Int {
        return when (periodo?.lowercase()) {
            "mañana" -> if (hora == 12) 0 else hora
            "tarde" -> if (hora < 12) hora + 12 else hora
            "noche" -> if (hora < 12) hora + 12 else hora
            else -> hora
        }
    }

    private fun formatearHora(hora: Int, minuto: Int): String {
        return String.format("%02d:%02d", hora, minuto)
    }
}

// Extensión para integrar con MLKitManager
fun MLKitManager.extraerHorasConNuevoExtractor(texto: String): List<MLKitEntity> {
    val extractor = HoraExtractor()
    val horasExtraidas = extractor.extraerHoras(texto)

    return horasExtraidas.map { hora ->
        when (hora.tipo) {
            HoraExtractor.TipoHora.RANGO_HORAS -> {
                // Crear dos entidades para inicio y fin
                listOf(
                    MLKitEntity(
                        tipo = "HORA_INICIO",
                        valor = hora.horaInicio ?: "",
                        confianza = hora.confianza,
                        posicionInicio = hora.posicionInicio,
                        posicionFin = hora.posicionFin
                    ),
                    MLKitEntity(
                        tipo = "HORA_FIN",
                        valor = hora.horaFin ?: "",
                        confianza = hora.confianza,
                        posicionInicio = hora.posicionInicio,
                        posicionFin = hora.posicionFin
                    )
                )
            }
            else -> {
                listOf(
                    MLKitEntity(
                        tipo = "HORA",
                        valor = hora.horaInicio ?: "",
                        confianza = hora.confianza,
                        posicionInicio = hora.posicionInicio,
                        posicionFin = hora.posicionFin
                    )
                )
            }
        }
    }.flatten()
}