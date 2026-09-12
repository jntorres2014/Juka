# Huka — reglas específicas para release con R8.
# Objetivo: ofuscar código de la app sin romper modelos que Gson/Firestore
# leen/escriben por reflexión.

# Metadatos necesarios para genéricos, anotaciones y reflexión.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# ─────────────────────────────────────────────────────────────────────────────
# Firestore: modelos de reportes
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.example.juka.data.firebase.PezCapturado { *; }
-keep class com.example.juka.data.firebase.DeviceInfo { *; }
-keep class com.example.juka.data.firebase.PartePesca { *; }
-keep class com.example.juka.data.firebase.UbicacionParte { *; }
-keep class com.example.juka.data.firebase.Captura { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Gson: borradores offline y modelos persistidos como JSON.
# Se preservan para que una actualización de la app no cambie los nombres de
# campos y vuelva ilegibles borradores creados por una versión anterior.
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.example.juka.domain.model.ParteEnProgreso { *; }
-keep class com.example.juka.domain.model.EspecieCapturada { *; }
-keep class com.example.juka.domain.model.Provincia { *; }
-keep class com.example.juka.domain.model.ModalidadPesca { *; }
-keep class com.example.juka.domain.model.TipoEmbarcacion { *; }

# JSON de especies usado por el repositorio de chat.
-keep class com.example.juka.data.repository.ChatRepository$EspecieJson { *; }

# FishIdentifier carga este DTO desde assets mediante Gson.
-keep class com.example.juka.domain.usecase.PezArgentino { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Firestore: Pescadex
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.example.juka.EspecieDescubierta { *; }
-keep class com.example.juka.PescadexUsuario { *; }

# Firestore: perfil de usuario
-keep class com.example.juka.auth.UserProfileData { *; }

# ─────────────────────────────────────────────────────────────────────────────
# Firestore: torneos
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.example.juka.domain.model.ReglasPuntaje { *; }
-keep class com.example.juka.domain.model.Torneo { *; }
-keep class com.example.juka.domain.model.ParticipanteTorneo { *; }
-keep class com.example.juka.domain.model.EspecieTorneo { *; }
-keep class com.example.juka.domain.model.ParteTorneo { *; }

# En release eliminamos logs verbosos/informativos. Conservamos warnings y
# errores para diagnóstico real en producción.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
