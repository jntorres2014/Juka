package com.example.juka.util

import java.text.SimpleDateFormat
import java.util.*

/**
 * Funciones de fecha/hora centralizadas.
 * Reemplaza las 5 copias dispersas en el proyecto.
 */
object DateUtils {

    /** "HH:mm" — para timestamps de mensajes de chat */
    fun timestampChat(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    /** "yyyy-MM-dd HH:mm:ss" — para logs y sesiones */
    fun timestampFull(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    /** "yyyy-MM-dd" — para fechas de partes de pesca */
    fun currentDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** "dd/MM/yyyy" — para mostrar fechas al usuario */
    fun currentDateDisplay(): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
}