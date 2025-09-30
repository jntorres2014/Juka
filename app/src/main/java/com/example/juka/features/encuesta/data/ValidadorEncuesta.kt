package com.example.juka.features.encuesta.data

/**
 * Utilidades para validar respuestas
 */
object ValidadorEncuesta {

    fun validarRespuesta(pregunta: Pregunta, respuesta: RespuestaPregunta): ValidacionRespuesta {
        if (!pregunta.esObligatoria) {
            return ValidacionRespuesta(true)
        }

        return when (pregunta.tipo) {
            TipoPregunta.TEXTO_LIBRE -> {
                if (respuesta.respuestaTexto.isNullOrBlank()) {
                    ValidacionRespuesta(false, "Este campo es obligatorio")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.OPCION_MULTIPLE -> {
                if (respuesta.opcionSeleccionada.isNullOrBlank()) {
                    ValidacionRespuesta(false, "Debes seleccionar una opción")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.SELECCION_MULTIPLE -> {
                if (respuesta.opcionesSeleccionadas.isEmpty()) {
                    ValidacionRespuesta(false, "Debes seleccionar al menos una opción")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.ESCALA -> {
                if (respuesta.valorEscala == null || respuesta.valorEscala !in 1..5) {
                    ValidacionRespuesta(false, "Debes seleccionar un valor entre 1 y 5")
                } else {
                    ValidacionRespuesta(true)
                }
            }

            TipoPregunta.SI_NO -> {
                if (respuesta.respuestaSiNo == null) {
                    ValidacionRespuesta(false, "Debes seleccionar Sí o No")
                } else {
                    ValidacionRespuesta(true)
                }
            }
        }
    }

    fun validarEncuestaCompleta(respuestas: List<RespuestaPregunta>): ValidacionRespuesta {
        val preguntasObligatorias = PreguntasEncuesta.PREGUNTAS.filter { it.esObligatoria }
        val respuestasObligatorias = respuestas.filter { respuesta ->
            preguntasObligatorias.any { it.id == respuesta.preguntaId }
        }

        if (respuestasObligatorias.size < preguntasObligatorias.size) {
            val faltantes = preguntasObligatorias.size - respuestasObligatorias.size
            return ValidacionRespuesta(
                false,
                "Faltan $faltantes preguntas obligatorias por responder"
            )
        }

        // Validar cada respuesta individual
        preguntasObligatorias.forEach { pregunta ->
            val respuesta = respuestas.find { it.preguntaId == pregunta.id }
            if (respuesta != null) {
                val validacion = validarRespuesta(pregunta, respuesta)
                if (!validacion.esValida) {
                    return ValidacionRespuesta(
                        false,
                        "Error en pregunta ${pregunta.id}: ${validacion.mensajeError}"
                    )
                }
            }
        }

        return ValidacionRespuesta(true)
    }
}