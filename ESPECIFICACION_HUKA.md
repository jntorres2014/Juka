# HUKA — Especificación completa para reimplementación

> Documento de requerimientos, arquitectura, modelo de datos e infraestructura de **Huka**, asistente de pesca recreativa para el pescador argentino. Está escrito para que una IA (o un equipo) pueda desarrollar la app completa **en cualquier lenguaje/plataforma**. La implementación de referencia es Android nativo (Kotlin + Jetpack Compose), pero todo lo descripto acá es agnóstico de plataforma salvo donde se indica.

---

## 1. Visión general

Huka es una app móvil para pescadores recreativos de Argentina. Sus pilares:

1. **Registro de jornadas de pesca ("partes")** con auto-guardado offline-first, por formulario guiado (wizard), por chat o por voz.
2. **Chatbot especializado en pesca** con IA generativa (Gemini), con cuota diaria.
3. **Identificación de especies por foto** (modelo on-device propio / API Fishial gratis e ilimitado; Gemini multimodal como opción "premium" con 1 uso diario).
4. **Pescadex**: enciclopedia de 85 especies argentinas con mecánica de descubrimiento (estilo Pokédex).
5. **Torneos privados** entre amigos con código de invitación, moderación y leaderboard.
6. **Gamificación por logros** (achievements por eventos, especies, horarios y especiales).
7. **Encuesta inicial** con fines de investigación (la app nace de una tesina universitaria; los partes alimentan un dashboard de análisis).

Idioma de toda la UI y el bot: **español rioplatense (es-AR), con voseo**.

---

## 2. Actores y roles

| Actor | Descripción |
|---|---|
| Pescador (usuario) | Único rol de la app. Se autentica con Google. No hay modo invitado ni registro email/contraseña. |
| Organizador de torneo | Cualquier usuario que crea un torneo; modera solicitudes y partes de ese torneo. |
| Usuario premium | Flag `isPremium` en su cuota: límites de chat ampliados. No hay flujo de pago implementado (se setea manualmente). |
| Backend/Investigador | No usa la app: consume los partes desde Firestore para un dashboard externo. |

---

## 3. Flujo de arranque y autenticación

### RF-AUTH
1. **RF-AUTH-01**: Login exclusivamente con Google Sign-In (OAuth 2.0) intercambiado por credencial de Firebase Auth. El UID de Firebase es la clave raíz de todos los datos del usuario.
2. **RF-AUTH-02**: La sesión persiste entre arranques (si `currentUser != null`, se saltea el login).
3. **RF-AUTH-03**: Todo el flujo de login está envuelto en timeouts (`signInWithCredential`: 15 s; lectura de perfil: 8 s best-effort; escritura de perfil: 5 s best-effort; token FCM: 5 s best-effort). Si vence: error "Sin conexión o red muy lenta", nunca spinner infinito.
4. **RF-AUTH-04**: Primer ingreso obligatorio: pantalla de **Términos y Condiciones** (aceptar para continuar) → **Encuesta inicial** (ver §10) → app principal. Usuarios recurrentes van directo a la app.
5. **RF-AUTH-05**: Al loguear se persiste/actualiza en `/users/{uid}`: datos de perfil (nombre, email, foto) + `fcmToken` + flags (términos aceptados, encuesta completada).
6. **RF-AUTH-06**: Logout desde Perfil; borra borradores locales (`deleteAllBorradores`).

Rutas del flujo: `login_screen` → `terminos_screen` → `encuesta_screen` → `main_app_root`.

---

## 4. Navegación

- **Bottom navigation** (4 tabs): **Identificar** (cámara), **Chat**, **Reportes**, **Perfil**.
- **Rutas adicionales** (drawer/accesos): `chat_menu`, `chat`, `pescadex`, `fish_counter` (Contador), `torneos`, `achievements_screen` (Logros), `notificaciones`, `parte_wizard`.
- El wizard acepta argumentos opcionales por query params: `parte_wizard?fotoUri={uri}&especie={nombre}` — para precargar foto + especie cuando se entra desde "Identificar captura".
- **Banner global de conectividad** como topBar del Scaffold raíz (ver §12).
- Campana de notificaciones (`CampanaIcon`) con badge en el header.

---

## 5. Chat con IA

### 5.1 Menú de entrada (`chat_menu`)
Pantalla de tarjetas previa al chat (no mezclar el menú con el input del chat). Está definida por un **JSON de configuración** (`chatbot_config.json`) con nodos de tipo `MENU` y acciones:

- `ENABLE_CHAT` (data: `chat_type`) → abre el chat libre con Gemini.
- `EXTERNAL_LINK` (data: `url`) → abre browser. Links actuales: tabla de mareas (hidro.gov.ar), guía de especies y reglamentos (pescaargentina.com.ar), viento (windguru.cz/53), vedas (argentina.gob.ar/inidep), calendario lunar.
- `NAVIGATE` (target: otro nodo) → submenús (ej. `species_menu`).
- Otros definidos en config: `DOWNLOAD`, `SHOW_MAP` (reservados).

El motor del menú (ChatBotManager + ChatBotActionHandler) debe ser data-driven: agregar opciones editando el JSON, sin tocar código.

### 5.2 Modos de chat
`ChatMode = GENERAL | CREAR_PARTE`.

- **GENERAL**: consulta libre. Historial en memoria (`List<ChatMessageWithMode>`) + persistido localmente (tabla `chat_messages`). Cada consulta consume cuota (ver §5.4).
- **CREAR_PARTE**: conversación estructurada para cargar un parte (ver §6.3).

### 5.3 Servicio de IA (Gemini)
- Modelo: **Gemini 1.5 Flash** (REST v1beta), API key inyectada por build config desde `local.properties` (`geminiApiKey`), nunca en el repo.
- Timeout de 30 s por llamada; si vence → error "La respuesta está demorando..." y **no se consume cuota** (`shouldConsumeQuota=false`).
- **System prompt** (transcribir tal cual en la nueva implementación):
  - Rol: "guía experto en pesca deportiva argentina" para pescadores que ya conocen el oficio.
  - Estilo: conciso, datos primero; prohibido abrir con elogios genéricos ("Qué buena idea", "Excelente pregunta"); casi sin signos de exclamación; voseo argentino.
  - Dominio: especies argentinas, técnicas/equipos, horarios/estaciones, lugares, regulaciones; adaptar consejos a la ubicación si el usuario la menciona.
  - Off-topic: responder con humor (chiste corto que vuelva al tema pesca), nunca negarse fríamente ni ser grosero.

### 5.4 Cuotas de uso (`ChatQuotaManager`)
- Chat: **5 consultas/día** gratis (`DAILY_LIMIT=5`); premium: 999.
- Identificación por foto premium: **1/día** (`PHOTO_DAILY_LIMIT=1`), independiente de la cuota de chat.
- Reset diario por comparación de `lastResetDate` con la fecha actual.
- Persistencia en `/user_quotas/{uid}`; incremento con `FieldValue.increment(1)` dentro de **transacción**.
- Timeout de chequeo: 10 s; si falla la red se marca `quotaCheckFailed=true` para distinguir "sin red" de "límite alcanzado" (mensajes distintos al usuario).
- Indicador de cuota visible en la UI del chat (`QuotaIndicator`).

### 5.5 Entrada por voz
- Reconocimiento de voz **on-device**, continuo y acumulativo (no se corta entre pausas), idioma es-AR.
- El texto reconocido se muestra en el input en tiempo real; botón de micrófono en el chat (`AudioButton`).
- Permiso requerido: RECORD_AUDIO.

---

## 6. Parte de pesca (feature central)

### 6.1 Modelo `ParteEnProgreso` (estado de carga)
```
fecha: String?               // yyyy-MM-dd
horaInicio, horaFin: String? // HH:mm
provincia: Provincia?        // enum con las 23 provincias + CABA
ubicacion: GeoPoint?         // lat/lon opcionales
nombreLugar: String?
modalidad: ModalidadPesca?   // enum, ver 6.5
modalidadOtra: String?       // texto libre si eligió "Otra" (precedencia: modalidadOtra ?: modalidad.displayName)
numeroCanas: Int?
tipoEmbarcacion: TipoEmbarcacion? // A_MOTOR | KAYAK | VELERO | GOMONES
especiesCapturadas: List<EspecieCapturada>  // {nombre, numeroEjemplares, devueltos?}
imagenes: List<String>       // paths locales durante la carga
observaciones: String?
noIdentificoEspecie: Boolean
sinCapturas: Boolean         // "zapatero" = no pescó nada
porcentajeCompletado: Int    // calculado
camposFaltantes: List<String>
```
Nota: el **peso** de los ejemplares fue eliminado del modelo deliberadamente (nunca se cargaba y generaba datos falsos). No reintroducirlo.

### 6.2 Carga por wizard (formulario guiado, 6 pasos)
Ruta `parte_wizard`, barra de progreso "Paso X de 6", animación entre pasos, validación por paso con mensaje de error:

1. **Fecha y hora** — "¿Cuándo fuiste a pescar?"
2. **Modalidad** — "¿Cómo pescaste?" (+ cañas / embarcación según modalidad; opción "Otra" con texto libre)
3. **Ubicación** — "¿Dónde pescaste?": nombre del lugar + provincia + **selector en mapa** (`MapPickerScreen`, tiles OpenStreetMap/OSMDroid, sin API key) + detección de ubicación actual (permiso FINE/COARSE_LOCATION)
4. **Especies** — lista de capturas {especie, cantidad}; autocompletar contra la base de especies; flag "no pesqué nada" y "no identifico la especie"
5. **Foto** — obligatoria; cámara o galería (FileProvider para captura)
6. **Notas/Observaciones** — texto libre; botón final "Enviar"

### 6.3 Carga por chat/voz (modo CREAR_PARTE)
Pipeline de extracción sobre cada mensaje (texto o transcripción de voz):

```
texto → detección "zapatero" (frases de captura nula)
      → extracción de entidades (regex + entity extraction on-device + diccionarios propios)
         entidades: FECHA, HORA, LUGAR, PROVINCIA, MODALIDAD, ESPECIE, CANTIDAD_PECES
      → conversión entidades → ParteEnProgreso
      → merge con lo ya cargado (nunca pisar datos confirmados con datos de menor confianza)
```

Reglas de extracción importantes (lecciones aprendidas, ver `REVISION_parte_por_voz.md`):
- Matchear especies contra **nombres + sinónimos** de la base (la voz transcribe variantes: "pejerrei", "tarira"), mapeando al nombre canónico; claves ordenadas por longitud descendente y con límites de palabra.
- Detectar **negaciones** ("no pesqué ninguna trucha") y no registrar la especie negada.
- Lugares: limitar la captura a ~3 tokens tras el conector ("en", "playa", "puerto", "bahía") y cortar en stopwords — evitar que "en el río con mi hermano" dé lugar="el río con mi hermano".
- Números escritos con palabras ("tres pejerreyes") → convertir a entero.
- Extracción de horas con tolerancia de formatos ("de 8 a 12", "18:30", "seis de la tarde").
- Modalidades por diccionario de palabras clave (ver 6.5); si nada matchea, guardar la frase en `modalidadOtra` en vez de descartarla.

La UI muestra el progreso del parte (`ParteProgress`), selectores rápidos para campos faltantes (`ParteFieldSelectors`, `ParteQuickActions`) y una hoja de confirmación final (`ParteConfirmacionSheet`) antes de enviar.

### 6.4 Validación y envío
- No se puede enviar con `porcentajeCompletado < 70` → mensaje "faltan datos".
- Envío offline-first (ver §12.4). Al confirmar OK: borrar borrador, resetear estado, evento `parteSaved` (dispara chequeo de logros §9 y suma a torneos activos §8), volver al chat general.

### 6.5 Modalidades de pesca (enum + diccionario de detección)
| Modalidad | Palabras clave |
|---|---|
| CON_LINEA_COSTA | costa, orilla, playa, muelle, escollera |
| CON_LINEA_EMBARCACION | embarcado/a, barco, lancha, kayak, gomon/gomón, velero |
| CON_RED | agallera, arrastre, mediomundo, medio mundo, redes, malla, red |
| PESCA_SUBMARINA_COSTA | submarina costa, submarina, buceo |
| PESCA_SUBMARINA_EMBARCACION | submarina embarcado/a, buceo embarcado |
| OTRA | texto libre en `modalidadOtra` |

### 6.6 Borradores múltiples (offline)
- Cada parte a medio cargar es un **borrador** con UUID, persistido localmente (tabla `borradores_parte`, ver §11.2) con auto-save en cada cambio (upsert fire-and-forget).
- Al tocar "Crear parte": si hay borradores pendientes, mostrar dialog con cards (lugar, fecha, % completado, última edición) + botón "+ Nuevo parte".
- "Cancelar parte" (X) borra solo el borrador activo. Botón "Borrador" guarda y vuelve sin borrar. Envío exitoso borra el borrador.

---

## 7. Identificación de peces por foto

Pantalla `identificar` (tab con cámara). Dos modos visibles al usuario:

1. **Estándar (gratis, ilimitado)**: identificación vía **API Fishial** (servicio REST externo de reconocimiento de peces) con **fallback a modelo propio on-device** (TFLite/LiteRT, `modelo_nuevo.tflite`, clasificador de 5 clases: `bagre, carpa, pejerrey_patagonico, trucha_arcoiris, trucha_marron`) cuando no hay internet o Fishial falla.
2. **Premium (1/día)**: **Gemini multimodal** (imagen en Base64 + prompt especializado). Chequear cuota ANTES de tomar/gastar la foto; si no queda cuota, avisar en el momento de la selección del modo y ofrecer el modo estándar. Al responder, anexar el mensaje de cuota restante.

Post-identificación: acción "Registrar captura" → navega al wizard con `fotoUri` + `especie` precargadas.

---

## 8. Torneos

Torneos privados identificados por **código de invitación** formato `HUKA-XXXXXX` (6 caracteres, alfabeto sin caracteres ambiguos).

### Modelo
```
Torneo { id, nombre, descripcion, creatorId, creatorName,
         fechaInicio, fechaFin (timestamps), tipoPuntaje,
         reglasPersonalizadas, codigoInvitacion, creadoEn }
  estado (derivado por fechas, NO persistido): PROXIMO | ACTIVO | FINALIZADO

ParticipanteTorneo { userId, userName, userPhoto, estado (PENDIENTE|ACEPTADO|RECHAZADO), puntaje, parteIds }

ParteTorneo { parteId, userId, fecha, especies, fotos, puntaje,
              estado (ACTIVO|RECHAZADO), motivoRechazo, creadoEn }
```

### Puntaje (`tipoPuntaje`)
| Tipo | Fórmula |
|---|---|
| CANTIDAD_PECES | suma de ejemplares del parte |
| ESPECIES_DISTINTAS | cantidad de especies distintas del parte |
| PERSONALIZADO | 0 automático; el organizador asigna puntos manualmente |

(La modalidad PESO_TOTAL fue eliminada junto con el campo peso.)

### Flujo
1. Crear torneo → genera código; creador es admin.
2. Unirse por código → solicitud queda PENDIENTE; admin acepta/rechaza.
3. Cada parte guardado por un participante aceptado durante un torneo ACTIVO suma puntaje automáticamente (`onParteSaved` itera torneos activos).
4. Admin puede **rechazar un parte**: transacción atómica que marca RECHAZADO + escribe motivo + **resta** el puntaje.
5. Leaderboard en vivo con posición propia destacada. Refresh reactivo; si el torneo desaparece, volver atrás automáticamente.
6. Todas las operaciones remotas de torneos con timeout de 12 s y error tipado "Sin conexión...".

Pantallas: `TorneosScreen` (lista + estados), `CrearUnirseScreen`, `ParteTorneoScreen`/`PartesTorneoScreen` (moderación).

---

## 9. Gamificación — Logros

- **Catálogo estático en la app** (fuente de verdad de TODOS los logros, para poder mostrar los bloqueados con candado); lo desbloqueado se persiste en `/users/{uid}/unlocked_achievements/{id}` con timestamp.
- Chequeo (`AchievementsChecker`) al enviar cada parte, antes de subirlo; resiliente a errores de red (el próximo parte re-chequea).
- Popup de logro desbloqueado + notificación "¡Desbloqueaste el logro X! 🎣".

### Categorías y logros
| Categoría | Color | Logros |
|---|---|---|
| EVENTOS | Rojo 🎁 | pescador_navideño, pescador_año_nuevo, regalo_de_reyes, pescador_invernal, pescador_primaveral (por fecha del parte) |
| ESPECIES | Verde 🐟 | variedad_es_vida, rey_del_rio (5+ dorados en una jornada), cazador_de_dorados, amigo_del_surubi, pejerreyes_master |
| HORARIOS | Violeta 🌙 | madrugador, pescador_nocturno, noctambulo |
| ESPECIALES | Naranja ⭐ | mi_primer_parte, solo_un_pez, zapatero_wade (parte sin capturas), pesca_abundante, explorador |

### Pantalla "Mis Logros"
Header + card de progreso (avatar, contador grande, barra animada) + chips de filtro por categoría ("Todos" + 4) + grilla de 2 columnas. Card desbloqueada: imagen/gradiente por categoría, badge "NUEVO" si < 48 h, fecha en español. Card bloqueada: gris, "?", candado, descripción como pista.

---

## 10. Encuesta inicial (investigación)

Obligatoria en el primer ingreso; respuestas en `/users/{uid}/encuestas/respuestas`. Tipos de pregunta: `NUMERO` (con rango validable), `OPCION_MULTIPLE`, `SI_NO`, `ESCALA` (con rango). Validación por pregunta + validación global de obligatorias; barra de progreso.

Preguntas (12):
1. ¿En qué año nació? (número)
2. Género (opción múltiple)
3. ¿Desde qué edad pesca? (número)
4. ¿Es socio/a de algún club de pesca recreativa? (sí/no)
5. ¿Participó alguna vez en concurso de pesca? (sí/no)
6. Autoevaluación de habilidad vs. otros pescadores (escala)
7. Expectativa de capturar pieza trofeo (escala)
8. Expectativa de capturar gran número de peces (escala)
9. Importancia del consumo de las capturas (escala)
10. ¿La mayoría de sus amistades están vinculadas a la pesca? (sí/no)
11. ¿Otros dirían que pasa mucho tiempo pescando? (sí/no)
12. ¿La pesca es su actividad recreativa favorita? (sí/no)

---

## 11. Features complementarias

### 11.1 Pescadex
- Enciclopedia basada en `peces_argentinos.json` (**85 especies**). Cada especie: `nombre, cientifico, habitat, carnadas[], mejor_horario, tecnica, tamaño, temporada, sinonimos[]`.
- Mecánica de descubrimiento: las especies se "capturan" al registrarlas en partes; guarda récords y logros propios de Pescadex; búsqueda y ficha detallada.

### 11.2 Mis Reportes
- Lista de partes confirmados del usuario (query con timeout 12 s; si vence → lista vacía con empty state, no error).
- Detalle del parte + **compartir** (sheet de compartir, imagen/resumen).
- Estadísticas agregadas del usuario (EstadisticasFirebase).

### 11.3 Contador de peces (`fish_counter`)
Contador en vivo para usar mientras se pesca: agregar/quitar especies, incrementar/decrementar cantidad, marcar devueltos, total general, persistencia del estado, y carga desde/hacia un parte existente.

### 11.4 Notificaciones
- **Push (FCM)**: token en `/users/{uid}/fcmToken`, actualizado en login y en `onNewToken`. Canal por defecto `huka_channel`. Casos: logro desbloqueado, inicio de temporada por provincia, alerta de especie en la zona.
- **Pantalla de notificaciones** + campana con badge en el header.
- Permiso POST_NOTIFICATIONS (Android 13+).

### 11.5 Perfil
Datos de Google (nombre, foto, email), accesos a Logros/Torneos/Notificaciones, logout.

---

## 12. Requerimientos no funcionales

### 12.1 Offline-first (crítico)
- **Monitor de conectividad reactivo** global: expone `isOnline` observable + chequeo síncrono `isOnlineNow()`.
- **Banner global**: oculto online; rojo "Sin conexión a internet" offline; verde "Conexión restablecida" 2.5 s al reconectar (no mostrarlo en el primer arranque: solo en transiciones offline→online).
- **Resultado de red tipado**: `Success | NoConnection | Timeout | Error`, con helper `withNetworkTimeout(monitor, timeoutMs, block)` que combina chequeo upfront + timeout.

### 12.2 Timeouts por dominio
| Operación | Timeout | Al vencer |
|---|---|---|
| Firestore genérico | 15 s | Error tipado |
| Subida de foto | 25 s | Guardar como borrador offline |
| Gemini | 30 s | Error sin consumir cuota |
| Torneos | 12 s | Result.failure con mensaje |
| Listado de partes | 12 s | Lista vacía + empty state |
| Cuotas | 10 s | `quotaCheckFailed=true` |
| Login | 15/8/5/5 s | Ver §3 |

### 12.3 Envío de parte (flujo completo)
1. `% < 70` → abortar con "faltan datos".
2. Sin red upfront → guardar borrador offline (motivo SIN_SENAL) y abortar.
3. Subir cada imagen con timeout 25 s → si falla alguna, borrador offline con motivo.
4. Guardar el parte en Firestore con timeout.
5. OK → borrar borrador, reset, evento parteSaved. NO OK → borrador offline con mensaje por motivo:
   - SIN_SENAL: "📶 Sin conexión a internet. Lo guardé como borrador..."
   - TIMEOUT: "⏳ La red está lenta y se cortó el envío..."
   - ERROR: "⚠️ No pudimos subir el parte ahora mismo..."

### 12.4 Arquitectura
- Patrón **MVVM** (o equivalente) con estado observable inmutable (UiState) y flujo unidireccional: UI → evento → ViewModel → use case/data source → estado → UI.
- La UI nunca accede directamente a la base remota; siempre vía managers/repositorios.
- Organización por features.

### 12.5 Seguridad
- API keys (Gemini, Fishial) fuera del repositorio, inyectadas en build (`local.properties` / variables de entorno / secret manager).
- Reglas de Firestore (base actual, **pendiente de endurecer para producción**):
```
/users/{uid}/**        read,write si request.auth.uid == uid
/partes_pesca/{uid}/** read,write si request.auth.uid == uid
/torneos/{torneoId}    read si autenticado; write solo creatorId
/**/participantes/{userId}  read si autenticado (escritura: reglas específicas)
```
- Pendientes conocidos: solo el creator debe poder modificar partes/participantes de un torneo; índice composite para `collectionGroup(participantes)`.
- Datos personales mínimos: perfil de Google + encuesta; cumplir con eliminación de datos si el usuario lo pide.

### 12.6 UX/Tono
- Voseo argentino en toda la UI y el bot; emojis moderados en mensajes del sistema.
- Nunca dejar spinners colgados: todo lo remoto tiene timeout y mensaje específico.
- No mentirle al usuario: distinguir "sin red" de "límite alcanzado", "guardado como borrador" de "enviado".

---

## 13. Modelo de datos

### 13.1 Firestore (nube)
```
/partes_pesca/{uid}/partes/{parteId}      partes confirmados
/users/{uid}                              perfil + fcmToken + flags
/users/{uid}/encuestas/respuestas         encuesta inicial
/users/{uid}/unlocked_achievements/{id}   logros (timestamp)
/user_quotas/{uid}                        cuotas diarias
/torneos/{torneoId}                       torneos
/torneos/{torneoId}/participantes/{uid}   solicitudes/miembros
/torneos/{torneoId}/partes/{parteId}      partes del torneo
```

**Documento PartePesca** (confirmado):
| Campo | Tipo | Descripción |
|---|---|---|
| id | String | `parte_YYYYMMDD_HHMMSS_NNNN` |
| userId | String | UID del autor |
| fecha | String | yyyy-MM-dd |
| horaInicio / horaFin | String | HH:mm |
| timestamp | Timestamp | server-side |
| duracionHoras | String | calculado ("5h 30min" / "N/A") |
| deviceInfo | objeto | modelo + marca + versión OS |
| tipo | String | `modalidadOtra ?: modalidad.displayName.lowercase()` |
| modalidadOtra | String? | texto libre |
| cantidadTotal | Int | suma de ejemplares |
| observaciones | String? | notas |
| transcripcionOriginal | String? | mensajes del chat unidos (si fue por chat) |
| numeroCanas | Int? | cañas |
| estado | String | "completado" |
| ubicacion | objeto | `{nombre, latitud?, longitud?, zona}` (lat/lon nullable ≠ 0.0) |
| peces | List | `[{especie, cantidad}]` — sin peso |
| fotos | List<String> | URLs en storage remoto |

**Documento user_quotas**: `{dailyChatsUsed, dailyPhotosUsed, lastResetDate, isPremium, createdAt}`.

### 13.2 Base local (equivalente a Room/SQLite)
```
chat_messages   { id autoinc, content, isFromUser, type (TEXT|AUDIO|IMAGE), timestamp }
borradores_parte{ id (UUID), parteJson (ParteEnProgreso serializado),
                  fechaActualizacion (epoch ms), porcentajeCompletado,
                  resumenLugar?, resumenFecha? }
```

### 13.3 Assets estáticos
- `peces_argentinos.json` — 85 especies (estructura en §11.1). Fuente también del autocompletado y de los sinónimos para extracción por voz.
- `chatbot_config.json` — árbol de menús del chat (§5.1).
- `modelo_nuevo.tflite` — clasificador de imágenes de 5 especies (§7).

---

## 14. Integraciones externas / infraestructura

| Servicio | Uso | Notas |
|---|---|---|
| Firebase Auth + Google Sign-In | autenticación | único método |
| Firestore | BD principal | tiempo real, reglas §12.5 |
| Firebase Storage | fotos de partes | subida con timeout 25 s |
| Firebase Cloud Messaging | push | token por usuario, canal `huka_channel` |
| Firebase Analytics + In-App Messaging | métricas | secundario |
| Google Gemini 1.5 Flash (REST v1beta) | chatbot + fish-id premium | API key secreta, timeout 30 s |
| Fishial API | fish-id estándar | gratis/ilimitado, con fallback local |
| Modelo TFLite propio (LiteRT) | fish-id offline | 5 clases, on-device |
| Reconocimiento de voz on-device | dictado del parte | es-AR, continuo |
| Entity extraction on-device (ML Kit o equivalente) | parseo del texto del parte | + diccionarios propios |
| OpenStreetMap (OSMDroid o equivalente) | mapa selector de ubicación | sin API key |
| Servicios de ubicación | GPS para el parte | permisos fine/coarse |
| Links externos | mareas, vedas, viento, lunar, reglamentos | ver §5.1 |

**Setup de infraestructura para una nueva implementación**: crear proyecto Firebase (Auth con proveedor Google, Firestore, Storage, FCM), registrar la app (en Android: `google-services.json`), cargar reglas de Firestore, obtener API key de Gemini (y de Fishial), configurar secretos de build, y empaquetar los 3 assets estáticos.

**Permisos requeridos (o equivalentes de la plataforma)**: INTERNET, ACCESS_NETWORK_STATE, RECORD_AUDIO, ACCESS_FINE/COARSE_LOCATION, CAMERA (opcional en hardware), POST_NOTIFICATIONS.

---

## 15. Inventario de pantallas

| Pantalla | Contenido |
|---|---|
| Login | branding + botón Google |
| Términos y Condiciones | scroll + aceptar |
| Encuesta | 12 preguntas, progreso, validación |
| Chat Menú | tarjetas data-driven (consultar / mareas / info) |
| Chat | mensajes, input texto/voz/imagen, indicador de cuota, modo parte con progreso y quick-actions |
| Identificar | cámara/galería, selector estándar/premium, resultado, "registrar captura" |
| Wizard de parte | 6 pasos (§6.2) + MapPicker |
| Mis Reportes | lista, detalle, compartir, estadísticas |
| Pescadex | grilla de especies, descubiertas vs. bloqueadas, ficha |
| Contador | contador en vivo por especie |
| Torneos | lista, crear/unirse por código, leaderboard, moderación de partes |
| Logros | progreso, filtros, grilla 2 col, popup de desbloqueo |
| Notificaciones | lista + campana con badge |
| Perfil | datos Google, accesos, logout |
| Globales | banner de conectividad, dialog de borradores pendientes, sheet de confirmación de parte |

---

## 16. Criterios de aceptación clave (resumen testeable)

1. Sin conexión, un parte completo se guarda como borrador y se puede enviar después sin pérdida de datos.
2. Con red lenta, ninguna pantalla queda en spinner infinito (todos los timeouts de §12.2).
3. "pesqué tres pejerreis y una tarira en playa unión de 8 a 12" por voz carga: 3 Pejerrey + 1 Tararira, lugar "Playa Unión", horario 08:00–12:00.
4. La 6ª consulta de chat del día es rechazada con mensaje de límite (no de error de red).
5. La 2ª identificación premium del día se bloquea ANTES de sacar la foto.
6. Un parte con menos del 70% completado no se puede enviar.
7. Rechazar un parte de torneo resta el puntaje del participante de forma atómica.
8. El código `HUKA-XXXXXX` permite unirse a un torneo; el solicitante queda pendiente hasta aprobación.
9. Los logros de un parte se otorgan una sola vez y sobreviven a fallas de red.
10. Un usuario nuevo no llega a la app principal sin aceptar términos y completar la encuesta.

---

## 17. Fuera de alcance / pendientes conocidos

- Flujo de pago para premium (el flag existe, el checkout no).
- Endurecer reglas de Firestore de torneos + índice composite (§12.5).
- Dashboard de investigación: consume Firestore, es un proyecto aparte.
- Port a Flutter iniciado (`juka_flutter/`) pero sin código fuente relevante — esta especificación es la fuente para cualquier port.
- Acciones `DOWNLOAD` y `SHOW_MAP` del chatbot_config: definidas pero no implementadas.
