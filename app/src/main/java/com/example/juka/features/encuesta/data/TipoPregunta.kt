package com.example.juka.features.encuesta.data

/**
 * Tipos de preguntas soportadas
 */
enum class TipoPregunta {
    TEXTO_LIBRE,        // EditText
    OPCION_MULTIPLE,    // RadioButton group
    SELECCION_MULTIPLE, // CheckBox group
    ESCALA,            // SeekBar o RadioButtons 1-5
    SI_NO              // Switch o RadioButtons Si/No
}