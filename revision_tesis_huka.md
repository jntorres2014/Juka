  # REVISIÓN CRÍTICA EXHAUSTIVA — TESIS HUKA
**Rol evaluador:** Jurado universitario / Director de tesis / Especialista en desarrollo de software  
**Documentos analizados:** Tesina Torres.docx | Doc_Tecnica_Huka_v1.1.docx | Manual_Huka_v1.1.docx | README.md | Código fuente (estructura de paquetes)  
**Fecha de revisión:** Junio 2026

---

## ESTADO GENERAL DEL DOCUMENTO

La tesis presenta una base académica sólida en su marco teórico y estado del arte, con referencias bibliográficas de calidad. Sin embargo, **el documento está claramente incompleto**: contiene múltiples placeholders explícitos (`COMPLETAR`, `XX`, `??`), capítulos sin desarrollar y una brecha significativa entre las funcionalidades reales del proyecto y lo documentado. El proyecto Huka es técnicamente más avanzado y completo de lo que la tesis refleja. En su estado actual, **el documento no está listo para defensa**.

---

## OBSERVACIONES DETALLADAS

---

### OBS-01

- **Sección de la tesis:** Resultados
- **Problema detectado:** El capítulo de Resultados está completamente sin terminar. Contiene texto placeholder literal: *"App FINAL (verificada con 4 usuarios cercanos)"*, *"Experimentación (pescadores/as conocidos 10??)"*, *"La app permitió acelerar la confección de reportes en tiempo XX"*, *"La app tuvo una recepción por parte de los usuarios de XX (Likert)"*.
- **Por qué es importante:** Es el capítulo que valida todo el trabajo. Un tribunal no puede evaluar un proyecto sin resultados concretos. Es el núcleo de la defensa.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Completar con los datos reales de las pruebas piloto: número exacto de participantes, instrumento utilizado (escala Likert, SUS, cuestionario propio), resultados estadísticos descriptivos, observaciones cualitativas relevantes y análisis de los datos.
- **Ejemplo de texto que podría incorporarse:**  
  > "Las pruebas piloto se realizaron con N=10 usuarios (8 hombres, 2 mujeres; rango de edad 24–52 años), seleccionados por conveniencia entre pescadores recreativos de la región del Chubut. La evaluación de usabilidad, medida mediante la escala SUS (System Usability Scale), arrojó una puntuación media de X.X/100 (DE=X.X), valor que se interpreta como [adjetivo según escala]. En cuanto al tiempo de confección de un parte de pesca, el promedio fue de X minutos X segundos, frente a los X minutos reportados con el método tradicional en papel. El 90% de los participantes indicó que volvería a utilizar la aplicación."

---

### OBS-02

- **Sección de la tesis:** Conclusiones
- **Problema detectado:** La sección contiene el marcador `COMPLETAR: los resultados de las pruebas piloto —` y el placeholder `[ COMPLETAR: limitación relacionada con el tamaño de la muestra en las pruebas con usuarios — ]"`. Son notas de trabajo internas visibles en el documento final.
- **Por qué es importante:** Las conclusiones son la síntesis del aporte científico. Presentarlas incompletas indica que el trabajo no está terminado.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Reescribir el párrafo con los datos reales. Eliminar todos los marcadores de edición.
- **Ejemplo de texto que podría incorporarse:**  
  > "La evaluación con usuarios reales reveló que [...]. Entre las limitaciones del presente estudio, se destaca que la muestra de prueba estuvo compuesta por N participantes de un perfil relativamente homogéneo (pescadores recreativos con experiencia en uso de smartphones), lo que limita la generalización de los hallazgos a poblaciones con menor familiaridad tecnológica o de regiones con características pesqueras muy diferentes."

---

### OBS-03

- **Sección de la tesis:** Capítulo ausente — Metodología
- **Problema detectado:** No existe un capítulo de Metodología. No se describe el proceso de desarrollo utilizado (¿Scrum? ¿Kanban? ¿proceso iterativo incremental?), ni las fases del proyecto, ni cómo se planificaron los sprints, ni las herramientas de gestión utilizadas.
- **Por qué es importante:** Toda tesis de ingeniería de software debe describir el proceso de desarrollo. Es uno de los aspectos que el tribunal evaluará con mayor rigor. Su ausencia es un error metodológico grave.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Incorporar un capítulo de Metodología con: enfoque metodológico seleccionado (e.g., desarrollo iterativo-incremental), justificación de la elección, descripción de las iteraciones o fases, herramientas utilizadas (Android Studio, Git, Firebase Console), criterios de aceptación y forma en que se validó cada funcionalidad.
- **Ejemplo de texto que podría incorporarse:**  
  > "El desarrollo de Huka adoptó un enfoque iterativo-incremental, organizado en ciclos de desarrollo de dos semanas. Cada iteración partió de la definición de un conjunto de funcionalidades a implementar, seguido del desarrollo, prueba interna y revisión con el equipo de tutores. Esta metodología permitió incorporar retroalimentación temprana y ajustar el diseño ante requerimientos cambiantes. Las herramientas de gestión utilizadas incluyeron [...]. El ciclo completo de desarrollo comprendió N iteraciones distribuidas en X meses."

---

### OBS-04

- **Sección de la tesis:** Desarrollo e Implementación — Arquitectura
- **Problema detectado:** La descripción de la arquitectura MVVM es correcta pero superficial. No hay diagrama de arquitectura, no se muestra el flujo de datos entre capas (View → ViewModel → Repository → Firebase), ni se describen los repositorios concretos implementados.
- **Por qué es importante:** La arquitectura es uno de los aportes de diseño centrales. Sin diagrama ni descripción detallada, el tribunal no puede evaluar la calidad del diseño.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Incluir: (1) diagrama de arquitectura del sistema mostrando capas y flujo de datos; (2) lista de los repositorios implementados; (3) descripción de cómo Coroutines y Flow conectan las capas; (4) diagrama de componentes o paquetes que muestre la estructura de features.
- **Ejemplo de texto que podría incorporarse:**  
  > "La Figura X ilustra la arquitectura de capas de Huka. La capa de presentación (Composables) observa StateFlows expuestos por los ViewModels. Los ViewModels, por su parte, invocan operaciones de los Repositorios a través de corrutinas, garantizando que ninguna operación de red o disco bloquee el hilo principal. Los Repositorios coordinan dos fuentes de datos: la base de datos local Room (para borradores y datos cacheados) y Firebase Firestore (para persistencia remota). Esta arquitectura desacopla completamente la lógica de negocio de los detalles de implementación de la interfaz."

---

### OBS-05

- **Sección de la tesis:** Desarrollo — Base de datos / Persistencia local
- **Problema detectado:** La tesis NO menciona en ningún momento la base de datos local Room. El código fuente revela la existencia de `HukaRoomDatabase`, `BorradorParteDao`, `ChatMessageDao`, `NotificacionDao` y `PescadexRecordDao`. Esta es una decisión de diseño técnico relevante que está completamente ausente del documento.
- **Por qué es importante:** La persistencia local es lo que habilita el modo offline, una de las características diferenciales del sistema. Omitirla oculta una decisión arquitectónica importante.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Incorporar una sección sobre la estrategia de persistencia dual (Room local + Firestore remoto), con descripción de las entidades Room, los DAOs implementados y la lógica de sincronización.
- **Ejemplo de texto que podría incorporarse:**  
  > "Para soportar el funcionamiento offline, Huka implementa una estrategia de persistencia dual. A nivel local, se utiliza Room, la biblioteca de abstracción sobre SQLite recomendada por Android Jetpack. Room gestiona cinco entidades principales: borradores de partes de pesca (`BorradorParte`), mensajes del historial de chat (`ChatMessage`), notificaciones locales (`Notificacion`) y registros del Pescadex (`PescadexRecord`). Cada entidad tiene su propio DAO (Data Access Object). La sincronización con Firestore ocurre cuando el dispositivo recupera conectividad, siguiendo el patrón offline-first."

---

### OBS-06

- **Sección de la tesis:** Desarrollo — Modo sin conexión
- **Problema detectado:** La tesis no documenta el modo offline. El Manual de Usuario dedica todo el capítulo 5 a este tema: auto-guardado de borradores, múltiples borradores simultáneos, gestión de envíos fallidos, indicador de conectividad (banner rojo/verde), botón "Guardar borrador". Esta funcionalidad es técnicamente sofisticada y pedagógicamente valiosa.
- **Por qué es importante:** El modo offline resuelve un caso de uso real crítico (pescadores en zonas sin señal). Es una fortaleza técnica del proyecto que no está reflejada en la tesis.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Incorporar una subsección sobre el diseño offline-first: estrategia de borradores, detección de conectividad, sincronización diferida, indicador visual de estado.
- **Ejemplo de texto que podría incorporarse:**  
  > "Un requerimiento no funcional central del sistema es la capacidad de operar en entornos con conectividad reducida o nula, escenario habitual en zonas de pesca alejadas de centros urbanos. Para satisfacer este requerimiento, Huka adopta una estrategia offline-first: toda la captura de datos del parte ocurre localmente y el envío a Firestore es una operación diferida. El estado de la conexión se monitorea en tiempo real y se comunica visualmente al usuario mediante un banner persistente. Un repositorio de borradores permite mantener múltiples partes parciales en paralelo, que el usuario puede retomar y enviar individualmente al recuperar señal."

---

### OBS-07

- **Sección de la tesis:** Desarrollos realizados — Módulo de Geolocalización
- **Problema detectado:** La tesis indica que se utilizó "Google Maps Platform" pero el código y el Manual confirman que se usa **OpenStreetMap con osmdroid**. La tesis menciona en la sección de Implementación que se usó osmdroid, pero la sección de Infraestructura Tecnológica dice "Google Maps Platform para geolocalización" — inconsistencia directa.
- **Por qué es importante:** Evidencia falta de revisión y coherencia interna. Un tribunal puede señalarlo como falta de rigor.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Corregir la inconsistencia. Eliminar la mención a Google Maps Platform. Documentar la decisión de usar osmdroid (licenciamiento libre, sin cuotas de API, criterio económico).
- **Ejemplo de texto que podría incorporarse:**  
  > "Para la visualización de mapas se eligió OpenStreetMap, integrado mediante la biblioteca osmdroid. Esta decisión respondió a criterios de licenciamiento (OSM es de uso libre sin restricciones comerciales) y sostenibilidad operativa (evita los costos asociados a la API de Google Maps Platform, que aplica cargos a partir de ciertos umbrales de uso). osmdroid provee tiles de mapa descargables que además pueden cachearse localmente, aspecto compatible con el modelo offline-first de la aplicación."

---

### OBS-08

- **Sección de la tesis:** Objetivos específicos
- **Problema detectado:** El objetivo específico menciona "modelos de visión computacional" y sugiere identificación automática con modelos locales. Sin embargo, la implementación real utiliza la **API cloud de Gemini Flash de Google** (multimodal), que es un modelo remoto. El marco teórico habla de TensorFlow Lite y modelos embebidos, lo que puede generar expectativa errónea.
- **Por qué es importante:** Hay una brecha entre lo que se promete y lo que se implementó. Puede generar preguntas difíciles del tribunal sobre por qué no se entrenó un modelo propio.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Reformular el objetivo para especificar que se usa una API de visión multimodal (Gemini). En la implementación, justificar explícitamente por qué se prefirió una API cloud frente a un modelo local (calidad, datos de entrenamiento, tiempo de desarrollo, costo computacional en dispositivo).
- **Ejemplo de texto que podría incorporarse:**  
  > "La identificación de especies se implementó mediante la API multimodal de Gemini Flash (Google DeepMind). Esta decisión se fundamenta en que el entrenamiento de un modelo de visión computacional propio requiere conjuntos de datos etiquetados de alta calidad sobre fauna acuática argentina —actualmente inexistentes en el dominio público— así como recursos computacionales significativos. Gemini ofrece capacidades de clasificación de imágenes de alta precisión en el dominio ictioló́gico, accesibles mediante prompting especializado sin requerir entrenamiento adicional. El approach de prompt engineering utilizado se describe en la sección X."

---

### OBS-09

- **Sección de la tesis:** Capítulo ausente — Pruebas y Validación
- **Problema detectado:** No existe ninguna sección dedicada a pruebas: no se describen pruebas unitarias, de integración, de usabilidad, ni el protocolo de las pruebas piloto con usuarios. Solo se menciona brevemente que se realizaron pruebas con usuarios.
- **Por qué es importante:** La validación del sistema es un aspecto central de cualquier proyecto de ingeniería de software. Sin ella, no se puede demostrar que los objetivos fueron cumplidos.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Incorporar un capítulo de Pruebas con: (1) tipos de pruebas realizadas; (2) instrumentos de evaluación de usabilidad (SUS, cuestionario propio, Likert); (3) protocolo de las pruebas piloto; (4) perfil de los participantes; (5) resultados con métricas concretas.
- **Ejemplo de texto que podría incorporarse:**  
  > "La validación del sistema se realizó en dos etapas complementarias. En primer lugar, se llevaron a cabo pruebas funcionales internas orientadas a verificar que cada módulo cumpliera con los requerimientos especificados. En segundo lugar, se realizó una prueba piloto exploratoria con N pescadores recreativos, aplicando el protocolo de pensamiento en voz alta (think-aloud). La usabilidad se midió mediante la Escala de Usabilidad del Sistema (SUS, Brooke 1996), obteniéndose una puntuación promedio de X puntos. Adicionalmente, se administró un cuestionario de satisfacción con escala Likert de 5 puntos sobre las funcionalidades más relevantes."

---

### OBS-10

- **Sección de la tesis:** Estado del arte — Sección incompleta con texto placeholder
- **Problema detectado:** La sección del estado del arte contiene los textos *"LLMs / Modelos de Lenguaje de Gran Escala"* duplicados, *"Algo de inteligencia artificial"* y *"Tecnología en Pesca Recreativa (Definir los Grandes Conceptos)"*, que son notas de trabajo internas, no texto académico. También hay una entrada de bibliografía (`James et al., 2025`) que tiene una URL que parece incorrecta (openRxiv y una fecha de 2025 con día futuro).
- **Por qué es importante:** Si el tribunal ve notas de edición internas en el documento, daña la imagen académica y genera desconfianza en la calidad general del trabajo.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Revisar y limpiar todo el documento de marcadores internos. Completar todas las secciones con texto definitivo. Verificar las URLs de las referencias bibliográficas.

---

### OBS-11

- **Sección de la tesis:** Diseño — Artefactos de diseño
- **Problema detectado:** La tesis menciona los Customer Journey Maps (CJM) para tres escenarios, pero el texto contiene la nota interna: *"Diseño (hay artefactos de Ágiles para describir de forma piola una app, por ejemplo User Journeys, User Story Mappings, Personas, etc)"*, que es una nota del autor sin resolver. Además, los CJMs no están incorporados como figuras con número y título formal.
- **Por qué es importante:** Los artefactos de diseño son evidencia del proceso de Diseño Centrado en el Usuario que el marco teórico describe. Sin ellos como figuras formales referenciadas, el capítulo de diseño queda débil.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Incorporar los CJMs como figuras numeradas (Figura X, Figura Y, Figura Z). Considerar también incluir: Personas de usuario, User Story Map o Backlog priorizado como evidencia del proceso UCD.
- **Ejemplo de texto que podría incorporarse:**  
  > "La Figura 3 presenta el Mapa de Recorrido del Usuario para el escenario de identificación de una especie. El análisis de este recorrido permitió identificar el punto de mayor tensión en la experiencia: el instante previo a recibir la respuesta del modelo, donde el usuario experimenta incertidumbre respecto a la capacidad de identificación del sistema. Esta observación motivó dos decisiones de diseño: (1) la inclusión de un indicador de progreso durante el procesamiento de la imagen; (2) la redacción de un mensaje de respaldo en caso de identificación fallida."

---

### OBS-12

- **Sección de la tesis:** Capítulo ausente — Diagramas de ingeniería de software
- **Problema detectado:** No hay ningún diagrama formal de software: ni diagrama de clases, ni diagrama de secuencia, ni diagrama de casos de uso, ni modelo de datos (esquema Firestore), ni diagrama de componentes. Un proyecto de Licenciatura en Sistemas debe incluirlos obligatoriamente.
- **Por qué es importante:** Los diagramas son el lenguaje de la ingeniería de software. El tribunal los esperará y su ausencia es uno de los errores más graves detectados.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Incorporar como mínimo: (1) diagrama de casos de uso del sistema; (2) diagrama de arquitectura de componentes; (3) modelo de datos (colecciones Firestore y esquema Room); (4) al menos un diagrama de secuencia para un flujo crítico (ej.: registro de parte con IA).
- **Ejemplo de texto que podría incorporarse:**  
  > "La Figura X presenta el diagrama de casos de uso del sistema Huka. Los actores identificados son el Pescador (usuario final), el Organizador de Torneo (rol especial del pescador) y los servicios externos (Google Firebase, Gemini API). El caso de uso central es 'Registrar parte de pesca', del que se extienden 'Registrar captura por voz', 'Adjuntar fotografía' y 'Geolocalizar captura'."

---

### OBS-13

- **Sección de la tesis:** Capítulo ausente — Modelo de datos
- **Problema detectado:** No se documenta la estructura de datos. El proyecto usa Firestore (NoSQL, colecciones de documentos) y Room (SQLite local). No se describen las colecciones, los campos, los tipos de datos ni las relaciones.
- **Por qué es importante:** El modelo de datos es una decisión de diseño crítica y evaluable. En proyectos con Firebase, el diseño de las colecciones tiene implicancias directas en el rendimiento, costo y escalabilidad.
- **Nivel de importancia:** ALTO
- **Propuesta de mejora:** Incluir el esquema de colecciones Firestore (usuarios, partes, torneos, logros, etc.) y el esquema Room (tablas locales). Pueden presentarse en forma de diagrama o tabla descriptiva.
- **Ejemplo de texto que podría incorporarse:**  
  > "La colección `partes` de Firestore contiene documentos con la siguiente estructura: `usuarioId` (String), `fecha` (Timestamp), `modalidad` (String, enum), `coordenadas` (GeoPoint), `capturas` (Array de objetos {especie, cantidad, talla}), `horaInicio` / `horaFin` (Timestamp), `fotosUrls` (Array de String), `esfuerzo` (Map {cañas, horas}). Cada documento de parte está anidado bajo la colección del usuario: `users/{uid}/partes/{parteId}`. Esta estructura jerárquica optimiza las consultas por usuario y permite aplicar reglas de seguridad a nivel de documento."

---

### OBS-14

- **Sección de la tesis:** Módulo de Torneos — descripción
- **Problema detectado:** La descripción del módulo de torneos es funcional pero no documenta aspectos técnicos relevantes: el sistema de solicitudes de adhesión (el organizador debe aceptar a cada participante), la moderación de partes (el organizador puede rechazar partes con motivo), la lógica de puntaje automático al enviar un parte activo en un torneo.
- **Por qué es importante:** Son decisiones de diseño interesantes que muestran la madurez del sistema y merecen documentación técnica.
- **Nivel de importancia:** MEDIO
- **Propuesta de mejora:** Ampliar la sección del módulo de torneos con la descripción del flujo de solicitudes, el rol del organizador y la lógica de puntaje. Considerar un diagrama de estados del torneo (Creado → En curso → Finalizado).
- **Ejemplo de texto que podría incorporarse:**  
  > "El diseño del módulo de torneos contempla un sistema de control de participación en dos etapas. En la primera, el usuario solicita unirse al torneo mediante el código de invitación; en la segunda, el organizador revisa y aprueba o rechaza la solicitud. Esta decisión de diseño evita la incorporación de participantes no autorizados en torneos privados. La moderación de partes permite al organizador invalidar registros que no cumplan con las reglas del torneo, con deducción automática del puntaje correspondiente."

---

### OBS-15

- **Sección de la tesis:** Módulo de Asistente Inteligente — Prompt Engineering
- **Problema detectado:** Se incluye el prompt del sistema del chatbot como texto plano en el cuerpo de la tesis, sin contextualizarlo académicamente. No se hace referencia al concepto de "system prompt", "instrucción de sistema" ni al paradigma de prompt engineering como técnica.
- **Por qué es importante:** El uso intencional de prompt engineering es un aporte técnico que merece sustentarse en la literatura (ej.: Brown et al. 2020, que sí está citado). El tribunal puede preguntar sobre la fundamentación de esta decisión.
- **Nivel de importancia:** MEDIO
- **Propuesta de mejora:** Contextualizar el uso de prompt engineering: qué es, por qué se usó en lugar de fine-tuning, cuáles son sus limitaciones. Hacer referencia explícita a la literatura del marco teórico.
- **Ejemplo de texto que podría incorporarse:**  
  > "La especialización del comportamiento del modelo se implementó mediante prompt engineering, técnica que consiste en diseñar instrucciones de sistema (system prompts) que condicionan el comportamiento del modelo hacia un dominio específico sin modificar sus parámetros (Brown et al., 2020). Esta aproximación se eligió frente al fine-tuning por razones prácticas: la inexistencia de un corpus etiquetado de consultas de pesca recreativa argentina, el costo computacional del ajuste fino de LLMs y la alta calidad de razonamiento contextual que Gemini exhibe bajo prompting especializado."

---

### OBS-16

- **Sección de la tesis:** Implementación — ML Kit
- **Problema detectado:** Se menciona ML Kit de Google para extracción de entidades (NER) del texto dictado por voz, pero no se explica qué entidades se extraen, cómo se usa la transcripción de voz, ni qué pasa cuando la extracción falla o es ambigua.
- **Por qué es importante:** Es una integración técnica no trivial que el tribunal puede preguntar en detalle.
- **Nivel de importancia:** MEDIO
- **Propuesta de mejora:** Ampliar la descripción con: qué modelo de ML Kit se usa (Text Recognition, Entity Extraction), qué entidades se detectan (fechas, números, nombres de especies), y cómo se integra con el flujo de creación de partes.
- **Ejemplo de texto que podría incorporarse:**  
  > "ML Kit se integra en el módulo de creación de partes para el reconocimiento de entidades en texto en lenguaje natural. Cuando el usuario dicta una descripción libre como 'hoy saqué tres dorados en el río Limay', ML Kit analiza la transcripción e identifica las siguientes entidades: cantidad numérica (3), nombre de especie ('dorado') y ubicación geográfica ('río Limay'). Las entidades detectadas se mapean automáticamente a los campos del modelo de datos del parte, reduciendo la carga manual de completar formularios."

---

### OBS-17

- **Sección de la tesis:** Trabajos futuros
- **Problema detectado:** Los trabajos futuros están en el final de las Conclusiones, en un solo párrafo, sin sección propia. Las líneas mencionadas son genéricas y no aprovechan el potencial identificado en el proyecto.
- **Por qué es importante:** Los trabajos futuros demuestran visión a largo plazo y conciencia de las limitaciones del trabajo actual. Los tribunales valoran su profundidad.
- **Nivel de importancia:** MEDIO
- **Propuesta de mejora:** Crear una sección dedicada "Trabajos Futuros" con al menos 5-6 líneas específicas y justificadas.
- **Ejemplo de texto que podría incorporarse:**  
  > "Entre las principales líneas de trabajo futuro se identifican: (1) Integración de datos meteorológicos en tiempo real para correlacionar condiciones climáticas con registros de capturas, generando modelos predictivos de actividad pesquera. (2) Desarrollo de un panel de administración web para gestores y organismos gubernamentales, que permita visualizar agregados estadísticos georreferenciados. (3) Extensión a plataforma iOS para ampliar la cobertura de usuarios. (4) Implementación de un modelo de visión computacional propio entrenado con datos recopilados por la comunidad Huka, reduciendo la dependencia de APIs externas. (5) Integración con registros oficiales de habilitaciones de pesca provinciales para validación cruzada automática de normativas."

---

### OBS-18

- **Sección de la tesis:** Introducción — Alineación con ODS
- **Problema detectado:** Se mencionan los ODS (ODS 14, ODS 15) pero de forma superficial. No se explica concretamente de qué manera Huka contribuye a estas metas ni qué indicadores de los ODS son relevantes.
- **Por qué es importante:** Es un marco de pertinencia académica y social que, bien desarrollado, fortalece la justificación del proyecto.
- **Nivel de importancia:** BAJO
- **Propuesta de mejora:** Referenciar los indicadores específicos de los ODS 14 y 15 que la aplicación aborda y explicar el mecanismo de contribución.

---

### OBS-19

- **Sección de la tesis:** Marco teórico — Separación con Estado del Arte
- **Problema detectado:** La tesis no tiene un capítulo de "Marco Teórico" diferenciado del "Estado del Arte". Ambos están mezclados. En una tesis de Licenciatura, el marco teórico establece los conceptos base (IA, ML, LLMs, ciencia ciudadana, gamificación) mientras el estado del arte analiza trabajos previos relacionados con el problema concreto.
- **Por qué es importante:** Es un error metodológico formal. El tribunal puede cuestionarlo.
- **Nivel de importancia:** MEDIO
- **Propuesta de mejora:** Reestructurar separando: (a) Marco Teórico: conceptos fundacionales; (b) Estado del Arte: aplicaciones y trabajos previos sobre monitoreo de pesca recreativa y apps de ciencia ciudadana.

---

### OBS-20

- **Sección de la tesis:** Implementación — Sistema de cuotas
- **Problema detectado:** Se describe el sistema de cuotas diarias para la API de Gemini pero no se justifica académicamente la decisión de diseño ni se describe cómo está implementado (¿en Firestore? ¿con timestamp de reinicio?).
- **Por qué es importante:** Es una decisión de diseño que afecta la experiencia del usuario y la sostenibilidad del sistema.
- **Nivel de importancia:** MEDIO
- **Propuesta de mejora:** Documentar la arquitectura del sistema de cuotas: dónde se persiste el contador, cómo se reinicia, qué pasa con el estado offline.
- **Ejemplo de texto que podría incorporarse:**  
  > "El sistema de cuotas diarias se implementa mediante un documento en Firestore por usuario con la siguiente estructura: `{consultasChat: N, consultasIdentificacion: N, ultimoReinicio: Timestamp}`. Cada consulta verifica y actualiza este documento en una transacción atómica. El reinicio diario se evalúa comparando el timestamp actual con el de último reinicio; si supera las 24 horas, los contadores se restablecen. Cuando el dispositivo no tiene conexión, el sistema informa al usuario en lugar de bloquear la funcionalidad preventivamente, evitando falsos positivos."

---

### OBS-21

- **Sección de la tesis:** Nomenclatura del proyecto
- **Problema detectado:** El proyecto se llama "Huka" en toda la tesis y en el Manual. Sin embargo, el repositorio, el workspace y el paquete Android son `com.example.juka` / `Juka`. Esta inconsistencia de nombres no se explica en ninguna parte del documento.
- **Por qué es importante:** El tribunal puede preguntar por la diferencia. Debe aclararse si "Juka" es el nombre técnico/provisional o si se cambió el nombre.
- **Nivel de importancia:** BAJO
- **Propuesta de mejora:** Agregar una nota aclaratoria sobre el nombre del paquete Android, o corregirlo al nombre definitivo del proyecto.

---

### OBS-22

- **Sección de la tesis:** Requerimientos del sistema
- **Problema detectado:** No hay una sección de requerimientos funcionales y no funcionales formal. Solo se infieren de la descripción de módulos.
- **Por qué es importante:** La especificación de requerimientos es la base del diseño. Su ausencia es una omisión metodológica.
- **Nivel de importancia:** MEDIO
- **Propuesta de mejora:** Incorporar una tabla de requerimientos funcionales (RF) y no funcionales (RNF) con identificador, descripción y prioridad.

---

## A. LISTADO DE PRIORIDADES (Mayor a Menor)

| Prioridad | Observación | Impacto |
|-----------|------------|---------|
| 1 | OBS-01 — Capítulo de Resultados incompleto (placeholders XX) | Impide la defensa |
| 2 | OBS-02 — Conclusiones con marcadores de edición visibles | Impide la defensa |
| 3 | OBS-09 — Ausencia total de capítulo de Pruebas | Error metodológico grave |
| 4 | OBS-03 — Ausencia total de Metodología | Error metodológico grave |
| 5 | OBS-12 — Sin ningún diagrama de software | Error técnico grave |
| 6 | OBS-13 — Sin modelo de datos documentado | Error técnico grave |
| 7 | OBS-04 — Arquitectura MVVM superficial, sin diagrama | Error técnico importante |
| 8 | OBS-05 — Room Database omitida completamente | Decisión técnica oculta |
| 9 | OBS-06 — Modo offline no documentado | Funcionalidad clave ausente |
| 10 | OBS-07 — Inconsistencia Google Maps vs. osmdroid | Inconsistencia factual |
| 11 | OBS-08 — Objetivos hablan de modelo local, se usa API cloud | Brecha objetivos/implementación |
| 12 | OBS-10 — Notas internas visibles en el texto | Falta de revisión editorial |
| 13 | OBS-11 — CJMs sin figuras formales, nota interna sin resolver | Diseño débil |
| 14 | OBS-22 — Sin tabla de requerimientos | Omisión metodológica |
| 15 | OBS-19 — Marco teórico y estado del arte mezclados | Error estructural |
| 16 | OBS-14 — Torneos: flujo técnico incompleto | Detalle técnico faltante |
| 17 | OBS-15 — Prompt engineering sin sustento académico | Fundamentación débil |
| 18 | OBS-16 — ML Kit: descripción insuficiente | Detalle técnico faltante |
| 19 | OBS-20 — Sistema de cuotas sin descripción técnica | Detalle de implementación |
| 20 | OBS-17 — Trabajos futuros superficiales y sin sección propia | Falta de visión |
| 21 | OBS-18 — ODS sin detalle de contribución | Justificación débil |
| 22 | OBS-21 — Inconsistencia de nombre Huka/Juka | Aclaración necesaria |

---

## B. RESUMEN EJECUTIVO — LAS 10 MEJORAS MÁS IMPORTANTES

**1. Completar los Resultados con datos reales.**  
El capítulo de Resultados tiene valores de "XX" y "??" que nunca fueron completados. Deben reemplazarse con los datos reales de las pruebas piloto: número de usuarios, instrumento de evaluación (SUS o Likert), métricas de tiempo, puntuaciones y citas representativas.

**2. Eliminar todos los placeholders y notas internas del documento.**  
Hay al menos 8 marcadores de trabajo internos visibles (`COMPLETAR`, `Algo de inteligencia artificial`, `Definir los Grandes Conceptos`, etc.) que deben resolverse antes de cualquier defensa.

**3. Crear un capítulo de Metodología.**  
El proceso de desarrollo no está descripto en ningún lugar. Debe incluirse: enfoque iterativo-incremental, fases, herramientas de gestión, criterios de aceptación.

**4. Crear un capítulo de Pruebas y Validación.**  
No hay ningún tipo de validación documentada formalmente. Es el corazón académico de cualquier proyecto de software.

**5. Incorporar diagramas de software obligatorios.**  
Mínimo: diagrama de casos de uso, diagrama de arquitectura de capas, modelo de datos (Firestore + Room), y un diagrama de secuencia para un flujo crítico.

**6. Documentar la base de datos Room y el modo offline.**  
Room, BorradorParteDao, ChatMessageDao y toda la estrategia offline-first están completamente ausentes de la tesis pese a ser una de las funcionalidades más sofisticadas del sistema.

**7. Corregir la inconsistencia Google Maps / osmdroid.**  
La Infraestructura Tecnológica dice "Google Maps Platform" pero el sistema real usa osmdroid con OpenStreetMap. Es un error factual directo.

**8. Alinear los objetivos con la implementación real.**  
Los objetivos hablan de "modelos de visión computacional" sugiriendo un modelo local, pero se usa Gemini API cloud. Debe reformularse y justificarse la decisión.

**9. Agregar una tabla de requerimientos funcionales y no funcionales.**  
Es la base del diseño de cualquier sistema de software y está completamente ausente.

**10. Separar Marco Teórico de Estado del Arte.**  
La mezcla de ambos capítulos es un error metodológico formal que el tribunal señalará.

---

## C. PREGUNTAS PROBABLES DEL TRIBUNAL

### Sobre el proyecto y sus objetivos

**P1. ¿Por qué eligieron Firebase como backend en lugar de un servidor propio?**  
*Sugerencia de respuesta:* Destacar: (a) Firebase BaaS elimina la necesidad de administrar infraestructura; (b) Firestore ofrece sincronización en tiempo real; (c) Firebase Authentication simplifica OAuth con Google; (d) escalabilidad automática. Citar que es la recomendación oficial de Google para apps Android sin backend propio.

**P2. ¿Por qué usan la API de Gemini en lugar de entrenar un modelo propio para identificación de peces?**  
*Sugerencia de respuesta:* (a) Inexistencia de datasets etiquetados de peces argentinos en volumen suficiente; (b) costo computacional del entrenamiento; (c) Gemini Flash ofrece calidad de identificación superior a modelos livianos para dispositivos móviles; (d) el enfoque de prompt engineering permite specialización sin fine-tuning.

**P3. ¿Cómo garantizan la precisión de la información regulatoria que proporciona el chatbot?**  
*Sugerencia de respuesta:* Reconocer la limitación explícitamente: los LLMs tienen un corte de conocimiento y pueden no reflejar normativas actualizadas. La aplicación informa al usuario sobre esta limitación. Como trabajo futuro, se podría integrar una fuente de datos oficial actualizable.

**P4. ¿Cómo funciona el modo offline? ¿Qué pasa si el usuario pierde conexión a la mitad de un registro?**  
*Sugerencia de respuesta:* Describir Room como base de datos local, el auto-guardado de borradores en cada cambio, la detección de conectividad con banner visual, y la sincronización diferida al recuperar señal.

**P5. ¿Qué metodología de desarrollo utilizaron y cómo la justifican para este tipo de proyecto?**  
*Sugerencia de respuesta:* Describir el enfoque iterativo-incremental adoptado, sus fases, y por qué es apropiado para un proyecto de un solo desarrollador con requerimientos que evolucionan.

**P6. ¿Cómo validaron que la aplicación es usable para pescadores con poca experiencia tecnológica?**  
*Sugerencia de respuesta:* Describir el protocolo de pruebas piloto, el instrumento utilizado (SUS/Likert), el perfil de los participantes y los resultados. Si no se hicieron pruebas con usuarios de baja experiencia técnica, reconocerlo como limitación.

**P7. ¿Qué consideraciones de privacidad y seguridad tomaron al manejar datos de geolocalización de los usuarios?**  
*Sugerencia de respuesta:* (a) Los datos se almacenan bajo el UID del usuario con reglas de Firestore que impiden acceso cruzado; (b) la geolocalización es manual (el usuario elige el punto), no automática; (c) las imágenes en Storage son privadas por usuario.

**P8. ¿Cuáles son las limitaciones del patrón MVVM que eligieron y qué alternativas evaluaron?**  
*Sugerencia de respuesta:* Mencionar que MVVM es el estándar Android actual. Como limitación, el ViewModel puede crecer en complejidad. Alternativas: Clean Architecture con casos de uso, MVI (Model-View-Intent) para estados más complejos.

**P9. ¿Cómo escalaría el sistema si la cantidad de usuarios creciera 10 o 100 veces?**  
*Sugerencia de respuesta:* Firebase escala horizontalmente. Las reglas de seguridad de Firestore se mantienen. El cuello de botella sería el costo de la API de Gemini, mitigable con el sistema de cuotas ya implementado.

**P10. ¿Por qué eligieron Kotlin sobre Java para el desarrollo?**  
*Sugerencia de respuesta:* Kotlin es el lenguaje oficial de Google para Android desde 2017. Ofrece: null safety, coroutines nativas, sintaxis concisa, interoperabilidad con Java, y es requerido para aprovechar Jetpack Compose al máximo.

**P11. ¿Qué es Jetpack Compose y por qué lo prefirieron frente al desarrollo tradicional con XML?**  
*Sugerencia de respuesta:* Compose es el framework UI declarativo de Android. Ventajas: menos código, preview en tiempo real, composición de componentes, integración nativa con ViewModels y Flow, sin necesidad de findViewById ni binding.

**P12. ¿Cómo diseñaron el sistema de gamificación? ¿En qué teoría psicológica se basa?**  
*Sugerencia de respuesta:* Citar la Teoría de la Autodeterminación (Deci y Ryan, 2000) presente en el marco teórico. Los logros cubren competencia (Pescadex, hitos), autonomía (el usuario elige qué pescar) y relación social (torneos). El esquema PBL (Points, Badges, Leaderboards) está documentado en Deterding et al. (2011).

**P13. ¿Cómo aseguran la calidad de los datos recopilados? ¿Pueden los usuarios cargar datos falsos?**  
*Sugerencia de respuesta:* Esta es una limitación real de los proyectos de ciencia ciudadana. Medidas implementadas: (a) Google Sign-In garantiza unicidad de usuario; (b) el módulo de torneos incluye moderación por organizador; (c) las fotografías actúan como evidencia validable. Como trabajo futuro: validación cruzada y detección de anomalías.

**P14. ¿Por qué eligieron OpenStreetMap sobre Google Maps?**  
*Sugerencia de respuesta:* (a) Licenciamiento libre (OSM Attribution License vs. costo por llamada de Google Maps); (b) sostenibilidad económica del proyecto; (c) tiles cacheables para uso offline; (d) sin restricciones de uso en proyectos académicos.

**P15. ¿Qué análisis hicieron de aplicaciones similares existentes antes de desarrollar Huka?**  
*Sugerencia de respuesta:* Mencionar las aplicaciones citadas en el estado del arte (iAngler, FishBrain, Fishidy, PescaREC de España). Describir qué no tienen o hacen mal, y cómo Huka los supera en el contexto argentino (idioma, especies locales, normativas provinciales, modo offline).

**P16. ¿Qué diferencia a Huka de FishBrain o aplicaciones similares en el mercado?**  
*Sugerencia de respuesta:* (a) Foco en pesca recreativa argentina con especies locales y normativas provinciales; (b) modo offline para zonas sin señal; (c) integración de ciencia ciudadana (datos con fines científicos); (d) chatbot especializado en español rioplatense; (e) open source y sin modelo de negocio de datos.

**P17. ¿Cómo gestionaron el control de versiones y el trabajo colaborativo durante el desarrollo?**  
*Sugerencia de respuesta:* Describir el uso de Git (el repositorio tiene rama master y `mi-nueva-rama`), el servidor remoto (GitHub), las convenciones de commits utilizadas.

**P18. Si tuvieran que rehacer el proyecto, ¿qué cambiarían del diseño técnico?**  
*Sugerencia de respuesta:* Reflexionar honestamente. Posibles respuestas: adoptar Clean Architecture desde el inicio para mayor separabilidad; usar Hilt para inyección de dependencias; implementar pruebas unitarias desde el primer sprint; considerar Compose Navigation con type-safe routes.

**P19. ¿Cómo planean que la información recopilada llegue efectivamente a los organismos de gestión pesquera?**  
*Sugerencia de respuesta:* El sistema centraliza datos en Firestore accesibles para el equipo técnico. Como trabajo futuro: dashboard de administración web para organismos, exportación en formatos estándar (CSV, JSON, compatible con sistemas de CONICET/CENPAT), posible integración con el proyecto FAO-GEF citado en el marco teórico.

**P20. ¿Huka está disponible en Google Play Store? ¿Qué implica la distribución como APK?**  
*Sugerencia de respuesta:* Durante la fase de tesina, se distribuye como APK. La publicación en Google Play requiere: (a) cuenta de desarrollador (USD 25); (b) cumplimiento de políticas de Play (privacidad, permisos); (c) review de Google. La distribución por APK es una limitación para la adopción masiva reconocida en el documento.

---

## D. OPORTUNIDADES DE PUBLICACIÓN O CONTINUIDAD

### Líneas de investigación futura relacionadas con Huka

**1. Validación de datos de ciencia ciudadana en pesca recreativa**  
Comparar los datos recopilados por Huka con muestreos científicos tradicionales para evaluar la confiabilidad y representatividad de los datos de ciencia ciudadana. Publicable en *Fisheries Research*, *ICES Journal of Marine Science* o *PLoS ONE*. Posible colaboración con CENPAT-CONICET.

**2. Modelos de adopción y retención de usuarios en apps de ciencia ciudadana**  
Estudiar los patrones de uso de Huka a largo plazo: ¿qué gamificación retiene más? ¿qué módulos se usan más? Aplicar modelos TAM (Technology Acceptance Model) o UTAUT. Publicable en *Journal of Medical Internet Research* (que acepta estudios de apps) o *Human-Computer Interaction*.

**3. Entrenamiento de modelos de identificación de peces argentinos**  
Los datos fotográficos recopilados por la comunidad Huka constituyen potencialmente un dataset único de fauna íctica argentina. Con volumen suficiente, podría entrenarse un modelo de clasificación específico (transfer learning sobre EfficientNet o MobileNet). Publicable en *Ecological Informatics* o *Methods in Ecology and Evolution*.

**4. Correlación entre condiciones ambientales y actividad pesquera**  
Integrar los datos de capturas georreferenciadas de Huka con datos meteorológicos e hidrológicos para identificar patrones espacio-temporales. Posible colaboración con el Servicio Meteorológico Nacional. Publicable en *Fisheries Management and Ecology*.

**5. Análisis comparativo de estrategias de gamificación en aplicaciones ambientales**  
Evaluar experimentalmente qué elementos de gamificación (logros, Pescadex, torneos) generan mayor retención y calidad de datos. Diseño experimental A/B con grupos de usuarios. Publicable en *Computers in Human Behavior* o *Gamification & HCI conferences*.

**6. Extensión a otras pesquerías y contextos latinoamericanos**  
Adaptar el modelo de Huka a contextos de pesca artesanal o de subsistencia en otras provincias o países de Latinoamérica. Estudio de caso comparativo. Publicable en revistas de gestión de recursos naturales.

**7. Sistema de alertas tempranas basado en datos agregados**  
Desarrollar un módulo que detecte anomalías en los patrones de captura (posibles eventos de mortalidad masiva, sobrepesca estacional) y genere alertas para organismos reguladores. Publicable en *Remote Sensing* o *Environmental Monitoring and Assessment*.

---

*Fin del informe de revisión crítica*  
*Elaborado con base en el análisis completo de: Tesina Torres.docx, Manual_Huka_v1.1.docx, Doc_Tecnica_Huka_v1.1.docx (vacío), README.md y estructura del código fuente del proyecto.*
