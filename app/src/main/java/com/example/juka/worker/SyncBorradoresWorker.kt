package com.example.juka.worker

import android.content.Context
import android.util.Log
import AchievementsChecker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.juka.HukaApplication
import com.example.juka.data.AchievementsViewModel
import com.example.juka.data.firebase.FirebaseResult
import com.example.juka.data.firebase.StorageService
import com.example.juka.domain.model.ParteEnProgreso
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson

/**
 * Sincroniza únicamente los borradores completos pertenecientes al usuario
 * autenticado cuando comenzó este trabajo. Nunca reutiliza borradores de otro
 * UID aunque la sesión cambie mientras WorkManager está ejecutándose.
 */
class SyncBorradoresWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "huka_sync_borradores"
        private const val TAG = "🔄 SyncBorradoresWorker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as HukaApplication
        val firebase = app.firebaseManager
        val storageService = StorageService()
        val auth = FirebaseAuth.getInstance()
        val ownerUid = auth.currentUser?.uid

        if (ownerUid.isNullOrBlank()) {
            Log.d(TAG, "Sin usuario autenticado; no hay nada que sincronizar.")
            return Result.success()
        }

        return try {
            val dao = app.roomDatabase.borradorDao()
            val pendientes = dao.getAllForOwner(ownerUid)
                .filter { it.porcentajeCompletado == 100 }

            if (pendientes.isEmpty()) {
                Log.d(TAG, "✅ Sin borradores pendientes para el usuario actual.")
                return Result.success()
            }

            val gson = Gson()
            var algunoFallo = false

            for (entity in pendientes) {
                if (auth.currentUser?.uid != ownerUid) {
                    Log.w(TAG, "La sesión cambió durante la sincronización; se detiene el worker.")
                    return Result.success()
                }

                try {
                    val parte = gson.fromJson(entity.parteJson, ParteEnProgreso::class.java)
                    if (parte == null) {
                        Log.w(TAG, "Borrador inválido; se conserva para revisión.")
                        algunoFallo = true
                        continue
                    }

                    // Igual que en el envío online: antes de escribir el parte en
                    // Firestore, toda imagen local debe convertirse en URL remota.
                    val urlsRemotas = mutableListOf<String>()
                    var falloImagen = false

                    for (path in parte.imagenes) {
                        if (auth.currentUser?.uid != ownerUid) {
                            Log.w(TAG, "La sesión cambió durante la subida de fotos.")
                            return Result.success()
                        }

                        if (path.startsWith("http://") || path.startsWith("https://")) {
                            urlsRemotas.add(path)
                            continue
                        }

                        val url = storageService.subirImagen(path)
                        if (url.isNullOrBlank()) {
                            Log.w(TAG, "No se pudo subir una foto del borrador; se reintentará.")
                            falloImagen = true
                            break
                        }
                        urlsRemotas.add(url)
                    }

                    if (falloImagen) {
                        algunoFallo = true
                        continue
                    }

                    val parteConFotosRemotas = parte.copy(imagenes = urlsRemotas)
                    val resultado = firebase.guardarParteCompletado(
                        parteConFotosRemotas,
                        parteId = entity.id
                    )

                    when (resultado) {
                        is FirebaseResult.Success -> {
                            if (auth.currentUser?.uid != ownerUid) {
                                return Result.success()
                            }

                            dao.deleteByIdForOwner(entity.id, ownerUid)

                            try {
                                AchievementsChecker(AchievementsViewModel())
                                    .checkParteAchievements(parteConFotosRemotas, ownerUid)
                            } catch (e: Exception) {
                                Log.w(TAG, "No se pudieron evaluar logros: ${e.javaClass.simpleName}")
                            }
                        }
                        is FirebaseResult.Error -> algunoFallo = true
                        else -> algunoFallo = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando borrador: ${e.javaClass.simpleName}")
                    algunoFallo = true
                }
            }

            if (algunoFallo) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado de sincronización: ${e.javaClass.simpleName}")
            Result.retry()
        }
    }
}
