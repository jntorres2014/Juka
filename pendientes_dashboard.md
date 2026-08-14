# Lista de pendientes — Integración del Panel Web (Huka Web) en la tesis

## Antes de agregar cualquier sección nueva

### P-01 · Título del trabajo
- **Sección:** Portada / Subtítulo
- **Cambio:** El título actual —*"Integración de Ciencia Ciudadana y Tecnología Móvil para el Monitoreo de Pesquerías Recreativas"*— no menciona el panel web.
- **Opciones:** (a) Ampliar el título: *"...mediante una Aplicación Móvil y un Panel de Administración Web"*; (b) dejarlo como está y mencionar el panel como un componente secundario en la introducción. Decidir antes de tocar cualquier otra sección.

---

## Sección: Objetivos

### P-02 · Objetivo general
- **Cambio necesario:** Actualmente describe solo el desarrollo de la app móvil. Si el panel web es parte del entregable, debe aparecer explícitamente. Ejemplo: agregar *"...complementada por un panel de administración web para la visualización y exportación de datos por parte de los equipos técnicos."*

### P-03 · Objetivos específicos
- **Cambio necesario:** Agregar un objetivo específico para el panel web. Ejemplo:
  *"Desarrollar un panel de administración web que permita a los equipos técnicos y de investigación visualizar, filtrar y exportar los datos recopilados por la plataforma móvil."*
- **Nota:** Sin este objetivo, el tribunal puede cuestionar por qué aparece implementado algo que no estaba en el alcance declarado.

---

## Sección: Introducción

### P-04 · Mención al componente web
- **Cambio necesario:** Agregar un párrafo (o una oración al cierre de la introducción) que anticipe la existencia del panel web como parte del sistema completo. No hace falta extenderse — solo que el lector sepa desde el principio que el sistema tiene dos componentes: app móvil + panel web.

---

## Sección: Desarrollos realizados

### P-05 · Corrección de typo en el título del capítulo
- **Cambio necesario:** El título dice *"Desarrollos realizado"* (le falta la 's'). Corregir a *"Desarrollos realizados"*.

### P-06 · Nuevo módulo: Panel de Administración Web
- **Cambio necesario:** Agregar una subsección (Heading4) al final de este capítulo que describa el panel web de la misma forma que se describen los módulos de la app móvil: propósito funcional, usuario destinatario y funcionalidades principales. Esta sección es la descripción *funcional*; el detalle técnico iría en Implementación.

### P-07 · Actualizar la subsección "Infraestructura Tecnológica"
- **Cambio necesario:** Actualmente lista solo Kotlin, Jetpack Compose y Firebase (stack móvil). Agregar el stack web: Python/Flask, pandas, Folium, Bootstrap 5, SQLite.

---

## Sección: Implementación

### P-08 · Nueva subsección: Panel de Administración Web
- **Cambio necesario:** Esta es la sección técnica propiamente dicha. Va después de las subsecciones actuales (Kotlin, MVVM, Firebase, Integraciones externas) y antes de Resultados.
- **Contenido a cubrir:** Stack tecnológico web, arquitectura Flask Blueprints, servicio Firebase Admin SDK, caché en memoria, módulo de mapas (Folium), control de acceso, exportación de datos.
- **Estado:** Ya redactada y lista para insertar (ver archivo `Tesina_Torres_con_dashboard.docx`).

---

## Sección: Resultados

### P-09 · Mencionar el uso del panel en las pruebas
- **Cambio necesario:** Si los datos de las pruebas piloto fueron analizados usando el panel web (exportación a Excel/CSV), mencionarlo. Ejemplo: *"Los datos recopilados durante las pruebas piloto fueron exportados desde el panel de administración web en formato Excel para su posterior análisis estadístico."*
- **Nota:** Si el panel no fue usado durante las pruebas, se puede omitir o mencionar como herramienta disponible para uso futuro de los investigadores.

---

## Sección: Conclusiones y discusiones

### P-10 · Ampliar la contribución principal
- **Cambio necesario:** El párrafo que describe la *"contribución central de Huka"* actualmente habla de tres elementos integrados solo en la app móvil. Ampliarlo para incluir el panel web como cuarto elemento: *"...y un panel de administración web que cierra el ciclo de ciencia ciudadana al poner los datos a disposición de los equipos de investigación en formatos listos para el análisis."*

### P-11 · Trabajos futuros
- **Cambio necesario (menor):** Si se agrega el panel, una línea de trabajo futuro obvia es su publicación en producción (servidor web) y la integración con organismos gubernamentales. Mencionar brevemente.

---

## Sección: Referencias bibliográficas

### P-12 · Agregar referencias del stack web
- **Cambio necesario:** Agregar al menos estas citas que hoy no están:
  - **Flask:** Grinberg, M. (2018). *Flask Web Development* (2nd ed.). O'Reilly Media.
  - **pandas:** McKinney, W. (2010). Data structures for statistical computing in Python. *Proceedings of the 9th Python in Science Conference*, 56–61.
  - **Folium:** Filipe, et al. (2013). Folium: Python data, Leaflet.js maps. GitHub. https://github.com/python-visualization/folium

---

## Sección: Índice / Tabla de contenidos

### P-13 · Actualizar el índice
- **Cambio necesario:** Una vez hechos todos los cambios anteriores, actualizar la tabla de contenidos para que refleje la nueva subsección dentro de Implementación y la corrección del título del capítulo "Desarrollos realizados".
- **Nota:** En Word esto se hace con clic derecho → *Actualizar campos* → *Actualizar toda la tabla*.

---

## Resumen de prioridades

| ID | Sección | Tipo | Impacto si se omite |
|----|---------|------|----------------------|
| P-01 | Portada | Decisión | Inconsistencia de alcance |
| P-02 | Objetivo general | Texto nuevo | Tribunal pregunta por alcance |
| P-03 | Objetivos específicos | Texto nuevo | Tribunal pregunta por alcance |
| P-04 | Introducción | Texto nuevo (1 párrafo) | El lector no sabe que existe el panel |
| P-05 | Desarrollos realizados | Corrección de typo | Error visible |
| P-06 | Desarrollos realizados | Texto nuevo (módulo) | Módulo sin descripción funcional |
| P-07 | Infraestructura Tecnológica | Ampliación | Stack incompleto |
| P-08 | Implementación | Texto nuevo (sección técnica) | El panel no está documentado técnicamente |
| P-09 | Resultados | Texto nuevo (1 oración/párrafo) | Falta mención al uso del panel |
| P-10 | Conclusiones | Ampliación (1 párrafo) | Contribución subvalorada |
| P-11 | Trabajos futuros | Texto nuevo (1-2 oraciones) | Menor |
| P-12 | Referencias | 3 referencias nuevas | Referencias faltantes |
| P-13 | Índice | Actualización automática | Índice desactualizado |

**Orden sugerido para editar:** P-01 (decisión) → P-03 → P-02 → P-04 → P-05/06/07 → P-08 → P-09 → P-10/11 → P-12 → P-13
