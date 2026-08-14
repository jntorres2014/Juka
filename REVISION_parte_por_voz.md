# Revisión: carga de parte por voz — debilidades y mejoras propuestas

> Diffs para revisar **antes** de aplicar. Nada de esto está aplicado todavía.
> Decime cuáles aprobás y los implemento.

## Flujo actual

```
🎤 WorkingAudioButton (AudioButton.kt)
        │ onAudioTranscribed(texto)
        ▼
sendAudioTranscript → sendParteAudioMessage (EnhancedChatViewModel.kt)
        │
        ▼
procesarEntradaInteligente(texto)
   ├─ detecta "zapatero" (no pescó nada)
   └─ mlKitManager.extraerInformacionPesca(texto)      ← regex + ML Kit + FishDatabase
        └─ convertirEntidadesAParteDatos(entidades)     ← entidades → ParteEnProgreso
              └─ parteLogicUseCase.mergearDatos(...)     ← merge con lo ya cargado
```

---

# CAPA 2 — Extracción (MLKitManager.kt)
Es donde más se rompe la carga por voz. Ordenado por impacto.

## E1 · Sinónimos descartados (alto impacto, bajo riesgo)
`extraerCapturas` y `extraerEspecies` usan `getAllSpecies()`, que hace
`distinctBy { it.name }` → **tira todos los sinónimos** que la FishDatabase ya
carga del JSON (`pecesMap` tiene nombre + sinónimos). La STT escribe variantes
("pejerrei", "tarira", "dorados") y el match exacto contra el nombre principal
falla. Usar las claves del mapa (que incluyen sinónimos) lo arregla casi gratis.

```diff
 // extraerCapturas()
-        val speciesNames = fishDatabase.getAllSpecies()
-            .map { Pattern.quote(it.name.lowercase()) }
-            .joinToString("|")
-        if (speciesNames.isEmpty()) return entidades
+        // Usamos TODAS las claves (nombres + sinónimos), más largas primero
+        // para que "medio mundo" gane a "mundo", etc.
+        val clavesEspecie = fishDatabase.fishSpeciesDB.keys.sortedByDescending { it.length }
+        if (clavesEspecie.isEmpty()) return entidades
+        val speciesNames = clavesEspecie.joinToString("|") { Pattern.quote(it) }
```
```diff
         while (matcher.find()) {
             val cantidadStr = matcher.group(1)
             val cantidad = convertirNumeroTextoAEntero(cantidadStr) ?: continue
-            val especieOriginal = texto.substring(matcher.start(2), matcher.end(2))
+            // Mapear la clave detectada (puede ser sinónimo) al nombre canónico
+            val claveMatch = textoLower.substring(matcher.start(2), matcher.end(2))
+            val especieCanonica = fishDatabase.fishSpeciesDB[claveMatch]?.name
+                ?: texto.substring(matcher.start(2), matcher.end(2))
+
+            if (estaNegado(textoLower, matcher.start(2))) continue   // ver E4
 
             entidades.add(MLKitEntity("CANTIDAD_PECES", cantidad.toString(), 0.9f, matcher.start(1), matcher.end(1)))
-            entidades.add(MLKitEntity("ESPECIE", especieOriginal, 0.9f, matcher.start(2), matcher.end(2)))
+            entidades.add(MLKitEntity("ESPECIE", especieCanonica, 0.9f, matcher.start(2), matcher.end(2)))
         }
```
```diff
 // extraerEspecies() — mismo criterio: recorrer claves con sinónimos y límites de palabra
-        fishDatabase.getAllSpecies().forEach { especie ->
-            val nombreLower = especie.name.lowercase()
-            var start = 0
-            while (true) {
-                val inicio = textoLower.indexOf(nombreLower, start)
-                if (inicio == -1) break
-                entidades.add(MLKitEntity("ESPECIE", especie.name, 0.9f, inicio, inicio + nombreLower.length))
-                start = inicio + nombreLower.length
-            }
-        }
+        fishDatabase.fishSpeciesDB.keys.sortedByDescending { it.length }.forEach { clave ->
+            val patron = Regex("(?<![\\p{L}])${Regex.escape(clave)}(?![\\p{L}])")
+            patron.findAll(textoLower).forEach { m ->
+                if (estaNegado(textoLower, m.range.first)) return@forEach   // ver E4
+                val canonica = fishDatabase.fishSpeciesDB[clave]?.name ?: clave
+                entidades.add(MLKitEntity("ESPECIE", canonica, 0.9f, m.range.first, m.range.last + 1))
+            }
+        }
```

## E2 · "Lugar" se traga toda la frase (alto impacto)
`Regex("en\\s+([A-Za-záéíóúñ\\s]+)")` es greedy: captura hasta el final.
"pesqué **en el río con mi hermano**" → lugar = "el río con mi hermano".
Limitar a pocos tokens y cortar en palabras de conexión.

```diff
 private val PATRONES_LUGAR = listOf(
-    Pattern.compile("""en\s+([A-Za-záéíóúñ\s]+)""", Pattern.CASE_INSENSITIVE),
-    Pattern.compile("""playa\s+([A-Za-záéíóúñ\s]+)""", Pattern.CASE_INSENSITIVE),
-    Pattern.compile("""puerto\s+([A-Za-záéíóúñ\s]+)""", Pattern.CASE_INSENSITIVE),
-    Pattern.compile("""bahía\s+([A-Za-záéíóúñ\s]+)""", Pattern.CASE_INSENSITIVE)
+    // Máximo 3 palabras tras el conector; luego limpiamos stopwords al final.
+    Pattern.compile("""\ben\s+((?:[A-Za-záéíóúñ]+\s?){1,3})""", Pattern.CASE_INSENSITIVE),
+    Pattern.compile("""\bplaya\s+((?:[A-Za-záéíóúñ]+\s?){1,3})""", Pattern.CASE_INSENSITIVE),
+    Pattern.compile("""\bpuerto\s+((?:[A-Za-záéíóúñ]+\s?){1,3})""", Pattern.CASE_INSENSITIVE),
+    Pattern.compile("""\bbah[ií]a\s+((?:[A-Za-záéíóúñ]+\s?){1,3})""", Pattern.CASE_INSENSITIVE)
 )
```
```diff
 // extraerLugares() — recortar el match en la primera stopword
             val lugar = matcher.group(1)?.trim()
-            if (!lugar.isNullOrBlank() && lugar.length > 2) {
+            val lugarLimpio = limpiarLugar(lugar)
+            if (!lugarLimpio.isNullOrBlank() && lugarLimpio.length > 2) {
                 entidades.add(
                     MLKitEntity(
                         tipo = "LUGAR",
-                        valor = lugar.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
+                        valor = lugarLimpio.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                         confianza = 0.8f,
                         posicionInicio = matcher.start(),
-                        posicionFin = matcher.end()
+                        posicionFin = matcher.start() + lugarLimpio.length
                     )
                 )
             }
```
```kotlin
// NUEVO helper
private val STOPWORDS_LUGAR = setOf(
    "con","y","de","del","la","el","los","las","mi","mis","porque","mientras",
    "mas","más","mas","mas","pesque","pesqué","saque","saqué","capture","capturé",
    "mas","ayer","hoy","total","mucho","muy","mas"
)
private fun limpiarLugar(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val tokens = raw.trim().split(Regex("\\s+"))
    val out = mutableListOf<String>()
    for (t in tokens) {
        if (t.lowercase() in STOPWORDS_LUGAR) break
        out.add(t)
    }
    return out.joinToString(" ").trim().ifBlank { null }
}
```

## E3 · Modalidad / provincia por substring sin límites de palabra (alto impacto)
`textoLower.contains("red")` pega dentro de "pa**red**"; `"costa"` dentro de
"**Costa**nera"; `"playa"` fija modalidad costa aunque hayan ido embarcados.
Usar límites de palabra.

```diff
 // extraerProvincias()
-        PATRONES_PROVINCIA.forEach { (patron, provincia) ->
-            if (textoLower.contains(patron)) {
-                val inicio = textoLower.indexOf(patron)
-                entidades.add(MLKitEntity("PROVINCIA", provincia.displayName, 0.9f, inicio, inicio + patron.length))
-            }
-        }
+        PATRONES_PROVINCIA.forEach { (patron, provincia) ->
+            val m = Regex("(?<![\\p{L}])${Regex.escape(patron)}(?![\\p{L}])").find(textoLower)
+            if (m != null) {
+                entidades.add(MLKitEntity("PROVINCIA", provincia.displayName, 0.9f, m.range.first, m.range.last + 1))
+            }
+        }
```
```diff
 // extraerModalidades()
         PATRONES_MODALIDAD.forEach { (patron, modalidad) ->
-            val inicio = textoLower.indexOf(patron)
-            if (inicio == -1) return@forEach
-            val fin = inicio + patron.length
+            val m = Regex("(?<![\\p{L}])${Regex.escape(patron)}(?![\\p{L}])").find(textoLower)
+                ?: return@forEach
+            val inicio = m.range.first
+            val fin = m.range.last + 1
             val solapa = rangosOcupados.any { rango -> inicio in rango || (fin - 1) in rango }
             if (solapa) return@forEach
             entidades.add(MLKitEntity("MODALIDAD", modalidad.displayName, 0.85f, inicio, fin))
             rangosOcupados.add(inicio until fin)
         }
```

## E4 · Negación no detectada (alto impacto — datos falsos)
"no pesqué **ningún dorado**" hoy carga *dorado* como captura. El chequeo de
zapatero no cubre estos casos. Helper de negación, usado en E1.

```kotlin
// NUEVO — nivel de clase
private val PALABRAS_NEGACION = listOf("no","ningún","ningun","ninguna","ningunos","sin","tampoco")
/** ¿Hay una negación en los ~20 chars previos a la especie? */
private fun estaNegado(textoLower: String, posInicio: Int): Boolean {
    val ventana = textoLower.substring(maxOf(0, posInicio - 20), posInicio)
    return PALABRAS_NEGACION.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(ventana) }
}
```

## E5 · Números solo hasta "diez" (medio impacto)
"**quince** dorados" se descarta (`quince` no está en el map → match perdido).
Extender el conversor y las alternativas de los regex.

```diff
 private fun convertirNumeroTextoAEntero(texto: String): Int? {
     return when (texto.lowercase()) {
         "un", "una", "uno" -> 1
         "dos" -> 2
         "tres" -> 3
         "cuatro" -> 4
         "cinco" -> 5
         "seis" -> 6
         "siete" -> 7
         "ocho" -> 8
         "nueve" -> 9
         "diez" -> 10
+        "once" -> 11
+        "doce" -> 12
+        "trece" -> 13
+        "catorce" -> 14
+        "quince" -> 15
+        "dieciséis", "dieciseis" -> 16
+        "diecisiete" -> 17
+        "dieciocho" -> 18
+        "diecinueve" -> 19
+        "veinte" -> 20
+        "treinta" -> 30
         else -> texto.toIntOrNull()
     }
 }
```
```diff
 // extraerCapturas() — alternativa de número en el regex
-        val patron = Pattern.compile(
-            """(\d+|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez)\s*($speciesNames)""",
+        val numeros = "\\d+|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieci(?:séis|seis|siete|ocho|nueve)|veinte|treinta"
+        val patron = Pattern.compile(
+            """($numeros)\s*($speciesNames)""",
             Pattern.CASE_INSENSITIVE
         )
```
*(Mismo agregado en `extraerNumeroCanas` si querés cubrir "quince cañas", raro pero gratis.)*

## E6 · `convertirEntidadesAParteDatos` ignora LUGAR, HORA y FECHA_HORA (medio impacto)
En el flujo de voz general, `convertir...` mapea entidades a `ParteEnProgreso`
pero **no tiene caso para `LUGAR`** (se pierde el lugar detectado), ni para
`HORA` (single) ni `FECHA_HORA` de ML Kit → datos detectados que se descartan.

```diff
                 "FECHA"       -> parteData = parteData.copy(fecha = entity.valor)
                 "HORA_INICIO" -> parteData = parteData.copy(horaInicio = entity.valor)
                 "HORA_FIN"    -> parteData = parteData.copy(horaFin = entity.valor)
+                "HORA"        -> parteData = if (parteData.horaInicio == null)
+                                     parteData.copy(horaInicio = entity.valor)
+                                 else parteData.copy(horaFin = entity.valor)
+                "LUGAR"       -> parteData = parteData.copy(nombreLugar = entity.valor)
                 "PROVINCIA"   -> parteData = parteData.copy(provincia = Provincia.fromString(entity.valor))
                 "MODALIDAD"   -> parteData = parteData.copy(modalidad = ModalidadPesca.fromString(entity.valor))
                 "NUMERO_CANAS"-> parteData = parteData.copy(numeroCanas = entity.valor.toIntOrNull())
```
*(`FECHA_HORA` de ML Kit es ambiguo; si querés lo derivamos a fecha u hora con
un parser, pero lo dejo fuera salvo que lo pidas.)*

---

# CAPA 1 — Captura de voz (AudioButton.kt)

## V1 · El tope de 15s se reinicia por segmento (la UI miente)
El countdown se reinicia cada vez que `isRecording` pasa a true, y eso pasa en
**cada segmento** (`onReadyForSpeech`). Resultado: la grabación puede correr
indefinidamente aunque diga "máx 15s". Atar el countdown a la **sesión**, no al
segmento, y subir el tope a algo realista para narrar un parte (45s).

```diff
-private const val MAX_RECORDING_SECONDS = 15
+private const val MAX_RECORDING_SECONDS = 45
+/** Tope de segmentos vacíos consecutivos antes de cortar (ver V2). */
+private const val MAX_EMPTY_SEGMENTS = 3
```
```diff
     var secondsRemaining by remember { mutableStateOf(MAX_RECORDING_SECONDS) }
+    // Sesión activa = desde que el usuario toca grabar hasta que se entrega
+    // o se corta. El countdown se ata a ESTO, no a cada segmento.
+    var isSessionActive by remember { mutableStateOf(false) }
+    // Segmentos consecutivos sin texto (ver V2)
+    var emptySegments by remember { mutableStateOf(0) }
```
```diff
     val stopRecording: () -> Unit = {
         Log.i("🎤", "Deteniendo grabación")
         userStopped = true
         isProcessing = true
         isRecording = false
+        isSessionActive = false
         speechRecognizer?.stopListening()
     }
```
```diff
-    LaunchedEffect(isRecording) {
-        if (isRecording) {
-            secondsRemaining = MAX_RECORDING_SECONDS
-            while (secondsRemaining > 0 && isRecording && !userStopped) {
-                delay(1000L)
-                if (isRecording && !userStopped) secondsRemaining--
-            }
-            if (secondsRemaining <= 0 && !userStopped) {
-                Log.i("🎤", "Timeout de ${MAX_RECORDING_SECONDS}s alcanzado, autodetener")
-                stopRecording()
-            }
-        } else {
-            secondsRemaining = MAX_RECORDING_SECONDS
-        }
-    }
+    // Countdown ATADO A LA SESIÓN: corre una sola vez por sesión, sin
+    // reiniciarse entre segmentos.
+    LaunchedEffect(isSessionActive) {
+        if (isSessionActive) {
+            secondsRemaining = MAX_RECORDING_SECONDS
+            while (secondsRemaining > 0 && isSessionActive && !userStopped) {
+                delay(1000L)
+                if (isSessionActive && !userStopped) secondsRemaining--
+            }
+            if (secondsRemaining <= 0 && !userStopped) {
+                Log.i("🎤", "Timeout total de ${MAX_RECORDING_SECONDS}s, autodetener")
+                stopRecording()
+            }
+        } else {
+            secondsRemaining = MAX_RECORDING_SECONDS
+        }
+    }
```
```diff
 // onFabClick — al arrancar
             else -> {
                 userStopped = false
                 accumulatedText = ""
                 secondsRemaining = MAX_RECORDING_SECONDS
+                emptySegments = 0
+                isSessionActive = true
```
```diff
 // permissionLauncher — al conceder permiso
         if (isGranted) {
             userStopped = false
             accumulatedText = ""
+            emptySegments = 0
+            isSessionActive = true
             launchRecognizer()
```

## V2 · Loop de relanzado sin tope ante silencio (drena batería)
Ante silencio (`ERROR_NO_MATCH` / `ERROR_SPEECH_TIMEOUT`) o segmentos en blanco,
el recognizer se relanza sin límite. Con el tope total de V1 ya hay corte, pero
agregar un tope de segmentos vacíos corta antes y avisa.

```diff
 // onResults() — donde acumula
                 if (match.isNotBlank()) {
                     accumulatedText = if (accumulatedText.isBlank()) match
                     else "$accumulatedText $match"
+                    emptySegments = 0
+                } else {
+                    emptySegments++
                 }
```
```diff
                 } else {
+                    if (emptySegments >= MAX_EMPTY_SEGMENTS && accumulatedText.isBlank()) {
+                        Log.i("🎤", "Demasiados segmentos vacíos, corto")
+                        userStopped = true; isSessionActive = false
+                        isRecording = false; isProcessing = false
+                        errorMessage = "No te escuché. Probá de nuevo."
+                        return@onResults_placeholder   // ver nota
+                    }
                     Log.i("🎤", "Reiniciando segmento (con delay)...")
                     handler.postDelayed({ if (!userStopped) launchRecognizer() }, RESTART_DELAY_MS)
                 }
```
```diff
 // onError() — rama recuperable
                 if (isRecoverableError && !userStopped) {
+                    emptySegments++
+                    if (emptySegments >= MAX_EMPTY_SEGMENTS && accumulatedText.isBlank()) {
+                        userStopped = true; isSessionActive = false
+                        isRecording = false; isProcessing = false
+                        errorMessage = "No te escuché. Probá de nuevo."
+                        return
+                    }
                     handler.postDelayed({ if (!userStopped) launchRecognizer() }, RESTART_DELAY_MS)
                 }
```
> Nota: en `onResults` no se puede `return@` con ese nombre; al implementarlo se
> envuelve el bloque else en un `if/else` simple. Lo dejo prolijo al aplicar.

## V3 · `EXTRA_MAX_RESULTS = 1` (bajo impacto)
Pedir varias hipótesis da margen para elegir/derivar la mejor más adelante.

```diff
-            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
+            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
```

## V4 · `onPartialResults` vacío — sin feedback en vivo (opcional, requiere plumbing)
Mostrar texto parcial mientras se habla mejora mucho la sensación de respuesta,
pero exige pasar un callback por `SimpleParteInput` → `WorkingAudioButton`. Lo
dejo como opcional; si lo querés, agrego un parámetro `onPartialTranscript`
**con default vacío** para no romper los llamadores actuales.

---

# Lo que NO toco (a discutir)
- **E7 · Last-write-wins en `mergearDatos`**: un dato nuevo mal reconocido
  sobrescribe uno bueno. Arreglar el lugar greedy (E2) elimina la causa más
  común. Cambiar el merge a "no pisar si ya hay valor" podría romper las
  *correcciones* del usuario ("no, era en el Paraná"). Prefiero decidirlo con vos
  antes de tocarlo.
- **`FishingStoryAnalizer.kt`** está comentado entero (muerto). Se puede borrar
  o reactivar partes (tenía sinónimos y tallas). A decidir.

# Resumen de archivos
| Archivo | Cambios |
|---|---|
| `MLKitManager.kt` | E1, E2, E3, E4, E5, E6 |
| `AudioButton.kt` | V1, V2, V3, (V4 opcional) |
| `ParteLogicUseCase.kt` | sin cambios (E7 a decidir) |
