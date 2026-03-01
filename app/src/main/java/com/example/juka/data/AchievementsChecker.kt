import com.example.juka.data.AchievementsViewModel
import com.example.juka.domain.model.EspecieCapturada
import com.example.juka.domain.model.ParteEnProgreso


// En tu ViewModel principal o en una clase separada AchievementsChecker
class AchievementsChecker(
    private val achievementsViewModel: AchievementsViewModel
) {
    fun checkParteAchievements(parteData: ParteEnProgreso, userId: String) {
        // Calcular métricas
            val totalPeces = parteData.especiesCapturadas.sumOf { it.numeroEjemplares }
            val totalEspecies = parteData.especiesCapturadas.size
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

    }

    private fun checkFirstParte(userId: String) {
        // Verificar si es el primer parte del usuario

//        val partesCount = getPartesCount(userId) // Implementar esta función
        val partesCount = 1
        if (partesCount == 1) {
            achievementsViewModel.unlockAchievement("mi_primer_parte")
        }
    }

    private fun checkSeasonalAchievements(fecha: String?) {
        // Parsear fecha (asumo formato dd/MM/yyyy)
        val parts = fecha?.split("/")
        if (parts?.size == 3) {
            val dia = parts?.get(0)?.toIntOrNull() ?: 0
            val mes = parts?.get(1)?.toIntOrNull() ?: 0

            // Pescador Navideño (24-31 de diciembre)
            if (mes == 12 && dia in 24..31) {
                achievementsViewModel.unlockAchievement("pescador_navideño")
            }

            // Pescador de Año Nuevo (1-7 de enero)
            if (mes == 1 && dia in 1..7) {
                achievementsViewModel.unlockAchievement("pescador_año_nuevo")
            }

            // Pescador de Reyes (6 de enero)
            if (mes == 1 && dia == 6) {
                achievementsViewModel.unlockAchievement("regalo_de_reyes")
            }

            // Pescador Invernal (junio, julio, agosto en Argentina)
            if (mes in 6..8) {
                achievementsViewModel.unlockAchievement("pescador_invernal")
            }

            // Pescador Primaveral
            if (mes in 9..11) {
                achievementsViewModel.unlockAchievement("pescador_primaveral")
            }
        }
    }

    private fun checkTimeBasedAchievements(hora: String?) {
        hora?.let {
            val horaInt = it.substringBefore(":").toIntOrNull() ?: return

            when {
                horaInt in 5..7 -> achievementsViewModel.unlockAchievement("madrugador")
                horaInt in 19..23 -> achievementsViewModel.unlockAchievement("pescador_nocturno")
                horaInt in 0..4 -> achievementsViewModel.unlockAchievement("noctambulo")
            }
        }
    }

    private fun checkSpeciesAchievements(especies: List<EspecieCapturada>) {
        // Variedad
        if (especies.size >= 5) {
            achievementsViewModel.unlockAchievement("variedad_es_vida")
        }

        // Especies específicas
        especies.forEach { especie ->
            when (especie.nombre.lowercase()) {
                "dorado" -> {
                    achievementsViewModel.unlockAchievement("cazador_de_dorados")
                }
                "surubí", "surubi" -> achievementsViewModel.unlockAchievement("amigo_del_surubi")
                "pejerrey" -> {
                    if (especie.numeroEjemplares >= 10) {
                        achievementsViewModel.unlockAchievement("pejerreyes_master")
                    }
                }
            }
        }
    }
}