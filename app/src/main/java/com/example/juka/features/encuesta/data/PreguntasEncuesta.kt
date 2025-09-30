package com.example.juka.features.encuesta.data

/**
 * Constantes con las preguntas de la encuesta
 */
object PreguntasEncuesta {

    val PREGUNTAS = listOf(
        Pregunta(
            id = 1,
            texto = "¿Cuál es tu nivel de experiencia en pesca?",
            tipo = TipoPregunta.OPCION_MULTIPLE,
            opciones = listOf(
                "Principiante (menos de 1 año)",
                "Intermedio (1-5 años)",
                "Avanzado (5-10 años)",
                "Experto (más de 10 años)"
            )
        ),

        Pregunta(
            id = 2,
            texto = "¿Con qué frecuencia pescas?",
            tipo = TipoPregunta.OPCION_MULTIPLE,
            opciones = listOf(
                "Varias veces por semana",
                "Una vez por semana",
                "Algunas veces al mes",
                "Solo en vacaciones",
                "Muy raramente"
            )
        ),

        Pregunta(
            id = 3,
            texto = "¿Qué modalidades de pesca practicas? (puedes seleccionar varias)",
            tipo = TipoPregunta.SELECCION_MULTIPLE,
            opciones = listOf(
                "Pesca desde costa",
                "Pesca embarcada",
                "Pesca en ríos",
                "Pesca en lagos",
                "Pesca deportiva",
                "Pesca submarina"
            )
        ),

        Pregunta(
            id = 4,
            texto = "¿En qué provincias o regiones pescas habitualmente?",
            tipo = TipoPregunta.SELECCION_MULTIPLE,

            placeholder = "Ej: Buenos Aires, Chubut, Patagonia..."
        ),

        Pregunta(
            id = 5,
            texto = "¿Qué tan importante es para ti registrar tus jornadas de pesca?",
            tipo = TipoPregunta.ESCALA,
            opciones = listOf("1 - Nada importante", "2", "3", "4", "5 - Muy importante")
        ),

        Pregunta(
            id = 6,
            texto = "¿Compartes tus experiencias de pesca en redes sociales?",
            tipo = TipoPregunta.SI_NO
        ),

        Pregunta(
            id = 7,
            texto = "¿Qué especies te interesan más capturar?",
            tipo = TipoPregunta.TEXTO_LIBRE,
            placeholder = "Ej: Salmón, pejerrey, trucha, dorado..."
        ),

        Pregunta(
            id = 8,
            texto = "¿Qué características te gustaría que tuviera una app de pesca? (selecciona las que te interesen)",
            tipo = TipoPregunta.SELECCION_MULTIPLE,
            opciones = listOf(
                "Registro automático de capturas",
                "Identificación de especies por foto",
                "Compartir reportes con amigos",
                "Estadísticas personales",
                "Mapas de lugares de pesca",
                "Pronóstico del tiempo",
                "Consejos y técnicas",
                "Comunidad de pescadores"
            )
        ),

        Pregunta(
            id = 9,
            texto = "¿Cómo calificarías tu interés en la tecnología aplicada a la pesca?",
            tipo = TipoPregunta.ESCALA,
            opciones = listOf("1 - Poco interés", "2", "3", "4", "5 - Muy interesado")
        ),

        Pregunta(
            id = 10,
            texto = "¿Hay algo específico que te gustaría que mejoremos en esta aplicación?",
            tipo = TipoPregunta.SELECCION_MULTIPLE,
            placeholder = "Comparte tus ideas, sugerencias o comentarios...",
            esObligatoria = false
        )
    )

    fun obtenerPreguntaPorId(id: Int): Pregunta? {
        return PREGUNTAS.find { it.id == id }
    }

    fun obtenerTotalPreguntas(): Int = PREGUNTAS.size

    fun calcularProgreso(preguntaActual: Int): Int {
        return ((preguntaActual.toFloat() / obtenerTotalPreguntas()) * 100).toInt()
    }
}