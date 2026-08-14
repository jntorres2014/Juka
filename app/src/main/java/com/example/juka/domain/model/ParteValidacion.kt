package com.example.juka.domain.model

/**
 * Fuente ÚNICA de verdad sobre qué campos son obligatorios para enviar un parte.
 *
 * La usan tanto el flujo de voz/chat (vía el sheet de confirmación y el
 * viewmodel) como el wizard, para que un parte tenga la misma completitud
 * sin importar cómo se cargó. Esto es clave para la calidad del dataset.
 *
 * Política:
 *  - Obligatorios siempre: fecha, hora inicio, hora fin, modalidad, ubicación
 *    (punto en el mapa) y foto.
 *  - Cañas: obligatorias solo si la modalidad usa caña (costa / embarcado).
 *  - Observaciones y especies: NO obligatorias (un día "zapatero" es válido, y
 *    las notas son un extra).
 */
object ParteValidacion {

    /** ¿La modalidad usa caña? Costa y embarcado sí; red y submarina no.
     *  "Otra" no la exige (puede ser un método sin caña). */
    fun canasObligatorias(p: ParteEnProgreso): Boolean =
        p.modalidad == ModalidadPesca.CON_LINEA_COSTA ||
                p.modalidad == ModalidadPesca.CON_LINEA_EMBARCACION

    /** Campos obligatorios que faltan. Lista vacía = listo para enviar. */
    fun camposFaltantes(p: ParteEnProgreso): List<String> {
        val faltan = mutableListOf<String>()
        if (p.fecha.isNullOrBlank()) faltan.add("Fecha")
        if (p.horaInicio.isNullOrBlank()) faltan.add("Hora de inicio")
        if (p.horaFin.isNullOrBlank()) faltan.add("Hora de fin")

        val modalidadOk = p.modalidad != null || !p.modalidadOtra.isNullOrBlank()
        if (!modalidadOk) faltan.add("Modalidad")

        if (canasObligatorias(p) && p.numeroCanas == null) faltan.add("Cantidad de cañas")
        if (p.ubicacion == null) faltan.add("Ubicación en el mapa")
        if (p.imagenes.isEmpty()) faltan.add("Foto")

        return faltan
    }

    fun esValido(p: ParteEnProgreso): Boolean = camposFaltantes(p).isEmpty()
}
