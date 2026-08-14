# BORRADOR — Capítulo: Metodología de Desarrollo

> **Nota:** Este es un borrador para revisión. Los valores entre corchetes `[X]` deben completarse con datos reales antes de insertar en la tesis. El capítulo iría ubicado entre el capítulo de **Estado del Arte** y el capítulo de **Desarrollos realizados**.

---

## Metodología

El desarrollo de Huka se llevó a cabo siguiendo un enfoque iterativo-incremental, adaptado a las características propias de un proyecto de tesina en el que un único desarrollador trabaja en estrecha colaboración con un equipo de tutores. Este enfoque permitió construir el sistema de manera progresiva, incorporando funcionalidades módulo a módulo y ajustando el diseño a partir de la retroalimentación recibida en cada etapa.

La elección de un modelo iterativo-incremental por sobre enfoques más estructurados como el modelo en cascada responde a la naturaleza exploratoria del proyecto. Al tratarse de un sistema novedoso en el contexto de la pesca recreativa argentina —sin antecedentes locales directos—, los requerimientos no podían especificarse en su totalidad desde el inicio. La incorporación de nuevos módulos (gamificación, torneos, asistente inteligente) fue resultado de decisiones tomadas durante el proceso de desarrollo, en función del avance técnico y las sugerencias del equipo tutor.

### Relevamiento de requerimientos

La fase inicial del proyecto consistió en una serie de reuniones con los tutores y co-tutores del trabajo, orientadas a identificar las necesidades del dominio, los usuarios objetivo y las funcionalidades prioritarias del sistema. En estas instancias se analizó la problemática del monitoreo de la pesca recreativa en Argentina, se revisaron las aplicaciones existentes en el mercado internacional (FishBrain, iAngler, PescaREC) y se definieron los módulos centrales que el sistema debía contemplar. Este relevamiento permitió establecer un conjunto inicial de requerimientos funcionales que sirvió de punto de partida para la primera iteración de desarrollo.

### Fases de desarrollo

El proceso de desarrollo se organizó en [X] fases iterativas a lo largo de aproximadamente [N] meses, cada una centrada en un conjunto acotado de módulos funcionales. La figura X ilustra la secuencia de fases y los módulos implementados en cada una.

**Fase 1 — Infraestructura base y autenticación**
Se estableció la arquitectura del proyecto (patrón MVVM, estructura de paquetes por features), se configuró el entorno de Firebase (Authentication, Firestore, Storage) y se implementó el módulo de registro e inicio de sesión con Google Sign-In. Esta fase sentó las bases técnicas sobre las que se construyeron los módulos posteriores.

**Fase 2 — Módulo de Parte de Pesca y Geolocalización**
Se desarrolló el núcleo funcional del sistema: el registro de partes de pesca en sus dos modalidades (modo guiado paso a paso y modo conversacional asistido por IA), junto con el módulo de geolocalización basado en OpenStreetMap/osmdroid. También se implementó el sistema de borradores locales con Room para soporte offline.

**Fase 3 — Módulo de Encuestas e Identificación de Especies**
Se incorporó el cuestionario de perfil inicial del pescador, de presentación única al momento del primer inicio de sesión, y el módulo de identificación de especies mediante fotografía utilizando la API multimodal de Gemini Flash.

**Fase 4 — Asistente inteligente, Gamificación y Módulo Pescadex**
Se desarrolló el chatbot especializado en pesca recreativa argentina, el sistema de logros con categorías temáticas y el módulo Pescadex de colección de especies capturadas. Estos módulos conforman la dimensión de retención y motivación de la plataforma.

**Fase 5 — Módulo de Torneos y Panel Web**
Se implementó el módulo de torneos con sistema de invitación por código, tabla de posiciones en tiempo real y moderación por parte del organizador. En paralelo, se desarrolló el panel de administración web (Huka Web) para la visualización y exportación de datos por parte de los equipos técnicos.

**Fase 6 — Pruebas, refinamiento y documentación**
Se realizaron pruebas funcionales internas sobre cada módulo y pruebas piloto exploratorias con usuarios reales. Con base en los resultados, se realizaron ajustes de usabilidad y se completó la documentación del proyecto (presente informe y manual de usuario).

### Herramientas de desarrollo

El entorno de desarrollo principal fue Android Studio, el IDE oficial de Google para el desarrollo de aplicaciones Android, utilizado para la escritura de código, depuración, ejecución en emuladores y dispositivos físicos, y previsualización de las interfaces en Jetpack Compose. El control de versiones se gestionó mediante Git, con repositorio remoto en GitHub, lo que permitió mantener un historial de cambios detallado y recuperar versiones anteriores ante errores durante el desarrollo.

La configuración de los servicios de backend (Firestore, Authentication, Storage) se realizó a través de la consola web de Firebase. Para el desarrollo del panel de administración web se utilizó Python 3 con el editor de código Visual Studio Code.

### Revisiones con el equipo tutor

Durante todo el proceso de desarrollo se mantuvieron reuniones periódicas con el equipo de tutores y co-tutores con una frecuencia aproximada de cada dos a tres semanas. Cada instancia de revisión incluyó una demostración del avance funcional del sistema, la discusión de decisiones de diseño y la definición de los objetivos para la siguiente fase. Este ciclo de revisión permitió detectar desviaciones respecto a los objetivos del proyecto de forma temprana y orientar el desarrollo hacia las prioridades establecidas por el equipo.

---

> **[MARCADORES A COMPLETAR ANTES DE INSERTAR]**
> - `[X]` número de fases: contá cuántas fases reales tuviste (puede coincidir o diferir de las 6 descriptas arriba — ajustá según tu proceso real)
> - `[N]` meses de duración: sabemos que fue menos de 6 meses, ponele el número exacto
> - `Figura X` referencia al diagrama de fases: si no tenés un diagrama, podés eliminar esa oración o agregarlo como trabajo futuro de mejora del documento
> - Revisá si el orden de las fases refleja exactamente cómo lo hiciste — si fue diferente, ajustalo

---

> **[POSICIÓN EN LA TESIS]**
> Este capítulo va **entre** "Estado del arte" y "Desarrollos realizados".
> En la tabla de contenidos quedaría:
> 1. Objetivos
> 2. Introducción
> 3. Estado del arte
> 4. **Metodología** ← NUEVO
> 5. Desarrollos realizados
> 6. Implementación
> 7. Resultados
> 8. Conclusiones y discusiones
> 9. Referencias bibliográficas
