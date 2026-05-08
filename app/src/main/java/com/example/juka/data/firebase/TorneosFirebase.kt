package com.example.juka.data.firebase

import android.util.Log
import com.example.juka.domain.model.EspecieTorneo
import com.example.juka.domain.model.EstadoParticipante
import com.example.juka.domain.model.EstadoParte
import com.example.juka.domain.model.EstadoParteTorneo
import com.example.juka.domain.model.ParteEnProgreso
import com.example.juka.domain.model.ParticipanteTorneo
import com.example.juka.domain.model.ParteTorneo
import com.example.juka.domain.model.Torneo
import com.example.juka.domain.model.TorneoConParticipantes
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TorneosFirebase {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TAG = "🏆 TorneosFirebase"
        private const val COL_TORNEOS = "torneos"
        private const val COL_PARTICIPANTES = "participantes"
        private const val COL_PARTES = "partes"
    }

    // ── Crear torneo ─────────────────────────────────────────────────────────

    suspend fun crearTorneo(torneo: Torneo): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No autenticado"))
            val codigo = generarCodigo()
            val docRef = firestore.collection(COL_TORNEOS).document()
            val torneoFinal = torneo.copy(id = docRef.id, creatorId = userId, codigoInvitacion = codigo, creadoEn = Timestamp.now())
            docRef.set(torneoFinal).await()
            Log.i(TAG, "✅ Torneo creado: ${docRef.id} — código: $codigo")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando torneo: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Buscar por código ────────────────────────────────────────────────────

    suspend fun buscarTorneoPorCodigo(codigo: String): Result<Torneo?> {
        return try {
            val snapshot = firestore.collection(COL_TORNEOS)
                .whereEqualTo("codigoInvitacion", codigo.trim().uppercase())
                .limit(1).get().await()
            Result.success(snapshot.documents.firstOrNull()?.toObject(Torneo::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando torneo: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Solicitar unirse ─────────────────────────────────────────────────────

    suspend fun solicitarUnirse(torneoId: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No autenticado"))
            val ref = firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTICIPANTES).document(user.uid)

            if (ref.get().await().exists())
                return Result.failure(Exception("Ya tenés una solicitud para este torneo"))

            ref.set(ParticipanteTorneo(
                userId = user.uid,
                userName = user.displayName ?: "Pescador",
                userPhoto = user.photoUrl?.toString() ?: "",
                estado = EstadoParticipante.PENDIENTE.name,
                solicitadoEn = Timestamp.now()
            )).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando unirse: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Responder solicitud ──────────────────────────────────────────────────

    suspend fun responderSolicitud(torneoId: String, participanteId: String, aceptar: Boolean): Result<Unit> {
        return try {
            firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTICIPANTES).document(participanteId)
                .update("estado", if (aceptar) EstadoParticipante.ACEPTADO.name else EstadoParticipante.RECHAZADO.name, "respondidoEn", Timestamp.now())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error respondiendo solicitud: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Guardar parte en torneo (auto-scoring) ───────────────────────────────

    suspend fun guardarParteTorneo(
        torneoId: String,
        parteId: String,
        parteData: ParteEnProgreso,
        puntaje: Int
    ): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No autenticado"))

            val parteTorneo = ParteTorneo(
                parteId = parteId,
                userId = user.uid,
                userName = user.displayName ?: "Pescador",
                fecha = parteData.fecha ?: "",
                especies = parteData.especiesCapturadas.map {
                    EspecieTorneo(nombre = it.nombre, cantidad = it.numeroEjemplares)
                },
                fotos = parteData.imagenes,
                puntaje = puntaje,
                estado = EstadoParteTorneo.ACTIVO.name,
                creadoEn = Timestamp.now()
            )

            // Guardar el parte en la subcolección
            firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTES).document(parteId)
                .set(parteTorneo).await()

            // Sumar puntaje al participante
            actualizarPuntaje(torneoId, puntaje, parteId)

            Log.i(TAG, "✅ Parte guardado en torneo $torneoId — +$puntaje pts")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando parte en torneo: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Rechazar parte (admin) — resta puntaje ───────────────────────────────

    suspend fun rechazarParte(torneoId: String, parteId: String, motivo: String = ""): Result<Unit> {
        return try {
            val parteRef = firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTES).document(parteId)

            val parteSnap = parteRef.get().await()
            val parte = parteSnap.toObject(ParteTorneo::class.java)
                ?: return Result.failure(Exception("Parte no encontrado"))



            // Marcar parte como rechazado
            parteRef.update(
                "estado", EstadoParteTorneo.RECHAZADO.name,
                "motivoRechazo", motivo
            ).await()

            // Restar puntaje al participante
            val participanteRef = firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTICIPANTES).document(parte.userId)

            firestore.runTransaction { transaction ->
                val snap = transaction.get(participanteRef)
                val puntajeActual = (snap.getLong("puntaje") ?: 0).toInt()
                val nuevoPuntaje = maxOf(0, puntajeActual - parte.puntaje)
                val partesActuales = (snap.get("parteIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                transaction.update(participanteRef,
                    "puntaje", nuevoPuntaje,
                    "parteIds", partesActuales - parteId
                )
            }.await()

            Log.i(TAG, "✅ Parte rechazado: $parteId — -${parte.puntaje} pts")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error rechazando parte: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Obtener torneos del usuario ──────────────────────────────────────────

    suspend fun obtenerMisTorneos(): Result<List<TorneoConParticipantes>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("No autenticado"))
            val resultado = mutableListOf<TorneoConParticipantes>()

            // Torneos donde soy creador
            val comoCreador = firestore.collection(COL_TORNEOS)
                .whereEqualTo("creatorId", userId).get().await()

            comoCreador.documents.forEach { doc ->
                val torneo = doc.toObject(Torneo::class.java)?.copy(id = doc.id) ?: return@forEach
                val participantes = obtenerParticipantes(torneo.id)
                val partes = obtenerPartesTorneo(torneo.id)
                resultado.add(TorneoConParticipantes(torneo = torneo, participantes = participantes, partes = partes, miEstado = null))
            }

            // Torneos donde participé
            val snapshot = firestore.collectionGroup(COL_PARTICIPANTES)
                .whereEqualTo("userId", userId).get().await()

            snapshot.documents.forEach { doc ->
                val torneoId = doc.reference.parent.parent?.id ?: return@forEach
                if (resultado.any { it.torneo.id == torneoId }) return@forEach

                val participante = doc.toObject(ParticipanteTorneo::class.java) ?: return@forEach
                val torneoDoc = firestore.collection(COL_TORNEOS).document(torneoId).get().await()
                val torneo = torneoDoc.toObject(Torneo::class.java)?.copy(id = torneoId) ?: return@forEach

                // Participante ve leaderboard anónimo (solo aceptados) y sus propios partes
                val participantesAnonimos = obtenerParticipantesAnonimos(torneoId)
                val misPartes = obtenerPartesUsuario(torneoId, userId)

                resultado.add(TorneoConParticipantes(
                    torneo = torneo,
                    participantes = participantesAnonimos,
                    partes = misPartes,
                    miEstado = participante.estadoEnum,
                    miPuntaje = participante.puntaje
                ))
            }

            Result.success(resultado.sortedByDescending { it.torneo.creadoEn.seconds })
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo torneos: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Actualizar puntaje ───────────────────────────────────────────────────

    suspend fun actualizarPuntaje(torneoId: String, puntaje: Int, parteId: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No autenticado"))
            val ref = firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTICIPANTES).document(user.uid)

            firestore.runTransaction { transaction ->
                val snap = transaction.get(ref)
                val partesActuales = (snap.get("parteIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                if (parteId !in partesActuales) {
                    val puntajeActual = (snap.getLong("puntaje") ?: 0).toInt()

                    if (snap.exists()) {
                        transaction.update(ref,
                            "puntaje", puntajeActual + puntaje,
                            "parteIds", partesActuales + parteId
                        )
                    } else {
                        // Admin no tiene documento aún → crear
                        transaction.set(ref, ParticipanteTorneo(
                            userId = user.uid,
                            userName = user.displayName ?: "Organizador",
                            userPhoto = user.photoUrl?.toString() ?: "",
                            estado = EstadoParticipante.ACEPTADO.name,
                            puntaje = puntaje,
                            parteIds = listOf(parteId),
                            solicitadoEn = Timestamp.now()
                        ))
                    }
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando puntaje: ${e.message}")
            Result.failure(e)
        }
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    private suspend fun obtenerParticipantes(torneoId: String): List<ParticipanteTorneo> {
        return try {
            firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTICIPANTES).get().await()
                .documents.mapNotNull { it.toObject(ParticipanteTorneo::class.java) }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun obtenerParticipantesAnonimos(torneoId: String): List<ParticipanteTorneo> {
        return obtenerParticipantes(torneoId).map { p ->
            if (p.estadoEnum == EstadoParticipante.ACEPTADO) p
            else p.copy(userName = "Pescador anónimo", userPhoto = "")
        }
    }

    private suspend fun obtenerPartesTorneo(torneoId: String): List<ParteTorneo> {
        return try {
            firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTES).get().await()
                .documents.mapNotNull { it.toObject(ParteTorneo::class.java) }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun obtenerPartesUsuario(torneoId: String, userId: String): List<ParteTorneo> {
        return try {
            firestore.collection(COL_TORNEOS).document(torneoId)
                .collection(COL_PARTES)
                .whereEqualTo("userId", userId).get().await()
                .documents.mapNotNull { it.toObject(ParteTorneo::class.java) }
        } catch (e: Exception) { emptyList() }
    }

    private fun generarCodigo(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return "HUKA-" + (1..6).map { chars.random() }.joinToString("")
    }
}