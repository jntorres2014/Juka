package com.example.juka.worker

import android.content.Context
import android.util.Log
import AchievementsChecker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.juka.HukaApplication
import com.example.juka.data.AchievementsViewModel
import com.example.juka.data.firebase.FirebaseResult
import com.google.firebase.auth.FirebaseAuth

/**
 * Worker que sube a Firestore todos los borradores completos (porcentaje == 100)
 * que quedaron pendientes por falta de conexión.
 *
 * Se programa con constraint [NetworkType.CONNECTED], así WorkManager lo ejecuta
 * automáticamente en cuanto el dispositivo recupera internet — sin que el usuario
 * tenga que hacer nada.
 *
 * Política de reintento: si algún borrador falla, devuelve [Result.retry()] y
 * WorkManager reintenta con backoff exponencial. Si todos salen bien → [Result.success()]
 * y el job se elimina.
 */
class SyncBorradoresWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        /** Nombre único del job — permite usar ExistingWorkPolicy.KEEP. */
        const val WORK_NAME = "huka_sync_borradores"
        private const val TAG = "🔄 SyncBorradoresWorker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as HukaApplication
        val localHelper = app.localStorageHelper
        val firebase = app.firebaseManager

        return try {
            // Solo subir borradores marcados como 100 % completos.
            // Los de porcentaje < 100 están en progreso de edición — no tocarlos.
            val pendientes = localHelper.getAllBorradores()
                .filter { it.porcentajeCompletado == 100 }

            if (pendientes.isEmpty()) {
                Log.d(TAG, "✅ Sin borradores pendientes.")
                return Result.success()
            }

            Log.i(TAG, "📤 Sincronizando ${pendientes.size} borrador(es)...")

            var algunoFallo = false

            for (meta in pendientes) {
                try {
                    val parte = localHelper.getBorrador(meta.id)
                    if (parte == null) {
                        // El borrador fue eliminado mientras el worker corría — ignorar.
                        Log.w(TAG, "⚠️ Borrador ${meta.id} ya no existe, saltando.")
                        continue
                    }

                    Log.d(TAG, "  ↑ Subiendo borrador ${meta.id} (${meta.resumenFecha ?: "sin fecha"}, ${meta.resumenLugar ?: "sin lugar"})")

                    // ✅ Id idempotente = id del borrador. Si este parte ya fue
                    // subido (por un envío manual o un intento previo), se pisa
                    // el mismo documento en vez de crear un duplicado.
                    val resultado = firebase.guardarParteCompletado(parte, parteId = meta.id)

                    when (resultado) {
                        is FirebaseResult.Success -> {
                            localHelper.deleteBorrador(meta.id)
                            Log.i(TAG, "  ✅ Borrador ${meta.id} sincronizado y eliminado.")

                            // Logros: el flujo online evalúa los logros al
                            // guardar, pero los partes sincronizados offline no
                            // pasaban por ahí. Corremos el mismo evaluador acá
                            // para que también otorguen logros. Best-effort:
                            // si falla, no rompe la sincronización.
                            try {
                                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                if (uid.isNotEmpty()) {
                                    AchievementsChecker(AchievementsViewModel())
                                        .checkParteAchievements(parte, uid)
                                    Log.d(TAG, "  🏆 Logros evaluados para ${meta.id}")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "  ⚠️ No se pudieron evaluar logros de ${meta.id}: ${e.message}")
                            }
                        }
                        is FirebaseResult.Error -> {
                            Log.w(TAG, "  ⚠️ Error subiendo borrador ${meta.id}: ${resultado.message}")
                            algunoFallo = true
                        }
                        else -> {
                            algunoFallo = true
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "  💥 Excepción procesando borrador ${meta.id}: ${e.message}")
                    algunoFallo = true
                }
            }

            if (algunoFallo) {
                Log.w(TAG, "⚠️ Algunos borradores no se pudieron subir. WorkManager reintentará.")
                Result.retry()
            } else {
                Log.i(TAG, "🎉 Todos los borradores sincronizados.")
                Result.success()
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error inesperado en SyncBorradoresWorker: ${e.message}")
            Result.retry()
        }
    }
}
