import com.example.juka.data.AchievementsViewModel
import com.example.juka.domain.model.EspecieCapturada
import com.example.juka.domain.model.ParteEnProgreso


class AchievementsChecker(
    private val achievementsViewModel: AchievementsViewModel
) {
    fun checkParteAchievements(parteData: ParteEnProgreso, userId: String) {
        val totalPeces = parteData.especiesCapturadas.sumOf { it.numeroEjemplares }
        val fecha = parteData.fecha
        val hora = parteData.horaInicio

        // === LOGROS DE INICIACIÓN ===
        checkFirstParte(userId = userId)

        // === LOGROS DE CANTIDAD ===
        if (totalPeces == 0) {
            achievementsViewModel.unlockAchievement("zapatero_wade")
        }

        if (totalPeces == 1) {
            achievementsViewModel.unlockAchievement("solo_un_pez")
        }

        if (totalPeces >= 10) {
            achievementsViewModel.unlockAchievement("pesca_abundante")
        }

        // === LOGROS ESTACIONALES ===
        checkSeasonalAchievements(fecha)

        // === LOGROS DE HORARIO ===
        checkTimeBasedAchievements(hora)

        // === LOGROS DE ESPECIES ===
        checkSpeciesAchievements(parteData.especiesCapturadas)

        // === LOGROS DE UBICACIÓN ===
        if (parteData.ubicacion != null) {
            achievementsViewModel.unlockAchievement("explorador")
        }

        // === LOGROS DE FOTO ===
        if (parteData.imagenes.isNotEmpty()) {
            achievementsViewModel.unlockAchievement("primera_foto")
        }
    }

    private fun checkFirstParte(userId: String) {
        // TODO: reemplazar con consulta real a Firestore cuando esté disponible
        val partesCount = 1
        if (partesCount == 1) {
            achievementsViewModel.unlockAchievement("mi_primer_parte")
        }
    }

    private fun checkSeasonalAchievements(fecha: String?) {
        val parts = fecha?.split("/")
        if (parts?.size == 3) {
            val dia = parts[0].toIntOrNull() ?: 0
            val mes = parts[1].toIntOrNull() ?: 0

            if (mes == 12 && dia in 24..31) achievementsViewModel.unlockAchievement("pescador_navideño")
            if (mes == 1 && dia in 1..7)  achievementsViewModel.unlockAchievement("pescador_año_nuevo")
            if (mes == 1 && dia == 6)     achievementsViewModel.unlockAchievement("regalo_de_reyes")
            if (mes in 6..8)              achievementsViewModel.unlockAchievement("pescador_invernal")
            if (mes in 9..11)             achievementsViewModel.unlockAchievement("pescador_primaveral")
        }
    }

    private fun checkTimeBasedAchievements(hora: String?) {
        hora?.let {
            val horaInt = it.substringBefore(":").toIntOrNull() ?: return
            when {
                horaInt in 5..7   -> achievementsViewModel.unlockAchievement("madrugador")
                horaInt in 19..23 -> achievementsViewModel.unlockAchievement("pescador_nocturno")
                horaInt in 0..4   -> achievementsViewModel.unlockAchievement("noctambulo")
            }
        }
    }

    private fun checkSpeciesAchievements(especies: List<EspecieCapturada>) {
        // Variedad
        if (especies.size >= 5) {
            achievementsViewModel.unlockAchievement("variedad_es_vida")
        }

        especies.forEach { especie ->
            when (especie.nombre.lowercase()) {

                "dorado" -> {
                    // Siempre desbloquea "cazador" la primera vez
                    achievementsViewModel.unlockAchievement("cazador_de_dorados")

                    // rey_del_rio: 5+ dorados en una sola jornada (antes era por
                    // peso, pero el peso se sacó del modelo de capturas).
                    if (especie.numeroEjemplares >= 5) {
                        achievementsViewModel.unlockAchievement("rey_del_rio")
                    }
                }

                "surubí", "surubi" -> {
                    achievementsViewModel.unlockAchievement("amigo_del_surubi")
                }

                "pejerrey" -> {
                    if (especie.numeroEjemplares >= 10) {
                        achievementsViewModel.unlockAchievement("pejerreyes_master")
                    }
                }
            }
        }
    }
}