# Checklist actualizado — Tesina Torres (versión 4)
Basado en la lectura completa del PDF compartido el 09/07/2026.

---

## ✅ RESUELTO EN ESTA VERSIÓN

| # | Qué se resolvió |
|---|----------------|
| 1 | **Room Database** documentada en Implementación (p. 60) con las 4 entidades y el patrón DAO |
| 2 | **WorkManager / SyncBorradoresWorker** documentado en Implementación (p. 60) |
| 3 | **Diagramas de software** — diagrama de casos de uso (p. 24), arquitectura del sistema (p. 58), MVVM con capas (p. 59-60) |
| 4 | **Trabajos futuros** con su propia subsección (p. 66) |
| 5 | **Pandas y Folium** en referencias (p. 72, 74) |
| 6 | **Modos ESTÁNDAR / PREMIUM** bien documentados en Módulo de Identificación (p. 31-33) |
| 7 | **Flujo identificación → parte** documentado al final del módulo de identificación (p. 34) |
| 8 | **Calidad de datos en ciencia ciudadana** incorporada en Estado del Arte (p. 17-18) |
| 9 | **Dashboard completo** con KPIs, secciones, control de acceso y exportación (p. 41-44) |
| 10 | **Objetivo específico del panel web** (obj. 8, p. 2) — redacción aceptable |
| 11 | **Tabla de contenidos** actualizada |

---

## 🔴 PENDIENTE CRÍTICO — Sin esto no hay defensa

### PEND-01 · Capítulo de Resultados vacío
**Página:** 62-63  
**Problema:** El capítulo sigue en futuro/condicional: *"Al finalizar la tesina, se espera haber desarrollado..."*. El tribunal espera resultados medibles de algo ya hecho, no expectativas.  
**Qué necesitás:** N° de usuarios de la prueba piloto, instrumento de evaluación (SUS, Likert, cuestionario), puntuaciones, observaciones concretas.  
**Urgencia:** 🔴 Máxima.

### PEND-02 · Conclusiones con placeholders visibles
**Página:** 64-65  
**Problema:** Dos marcadores sin completar:
- *"COMPLETAR: los resultados de las pruebas piloto —"*
- *"[ COMPLETAR: limitación relacionada con el tamaño de la muestra en las pruebas con usuarios — ]"*  
- Además, en el párrafo siguiente dice *"grupo reducido de N participantes"* — la N está sin completar.  
**Urgencia:** 🔴 Máxima — no se puede presentar con notas de edición visibles.

---

## 🟠 PENDIENTE IMPORTANTE — El tribunal lo va a notar

### PEND-03 · Objetivo general no menciona el panel web
**Página:** 2  
**Problema:** El objetivo general sigue describiendo solo la app móvil. El objetivo específico 8 sí lo menciona, pero el general no.  
**Sugerencia:** Agregar al final del objetivo general: *"...complementada por un panel de administración web orientado a equipos técnicos e investigadores."*

### PEND-04 · Error técnico: PostgreSQL vs SQLite en la tabla de infraestructura
**Página:** 44 (tabla de componentes)  
**Problema:** La fila "Base local web" dice **PostgreSQL**, pero el código real usa **SQLite** (con SQLAlchemy). PostgreSQL es una base de datos de servidor; SQLite es un archivo local.  
**Urgencia:** 🟠 Alta — es un error técnico concreto que el tribunal puede detectar.

### PEND-05 · "ML Kit de Google (2026)" — año incorrecto
**Página:** 27  
**Problema:** La cita dice *"ML Kit de Google (2026)"*, pero la fecha no puede ser 2026 si la tesis fue desarrollada antes. Posiblemente sea 2024.  
**Solución:** Revisar la fecha de la documentación de ML Kit y corregir.

### PEND-06 · "Se realizaron pruebas manuales" — oración incompleta
**Página:** 49 (al final de Pruebas de concepto)  
**Problema:** Sigue siendo una oración suelta sin continuación. El lector queda sin saber qué se probó ni qué resultó.  
**Sugerencia:** Completar indicando qué imágenes se probaron, cuántas, y cuál fue el resultado observado (aunque sea descriptivo).

### PEND-07 · Referencias bibliográficas faltantes
**Problema:** Las siguientes fuentes son citadas en el texto pero NO aparecen en la lista de referencias:
- **Hartill et al. (2019)** — citado en Introducción (p. 3)
- **Taylor et al. (2025)** — citado en Introducción (p. 3)
- **Banco Mundial (2012)** — citado en Introducción (p. 3)
- **Android Developers (2024) — Room** — citado implícitamente en Implementación; falta la referencia de Room específicamente (se cita "Guide to app architecture" y "Jetpack Compose" pero no Room)  
**Urgencia:** 🟠 Alta — el tribunal puede verificar las referencias.

---

## 🟡 PENDIENTE MENOR — Mejora la calidad académica

### PEND-08 · Objetivo general no menciona el panel web
**Página:** 3-5 (Introducción)  
**Problema:** La introducción describe solo la app móvil. El panel web aparece por primera vez sin anticipación en la p. 41.  
**Sugerencia:** Una oración al final de la intro: *"Como componente complementario, se desarrolló un panel de administración web que permite a los equipos de investigación consultar, analizar y exportar los datos recopilados."*

### PEND-09 · Último párrafo de Trabajos futuros necesita reescritura académica
**Página:** 67  
**Problema:** El párrafo final dice: *"Por último, llegar a la oferta premium que sea gratis para cualquier usuario. Se podría realizar a partir de las fotos de los partes de los usuarios, poder entrenar un modelo..."* — es lenguaje informal y de nota personal.  
**Sugerencia:** Reescribir como: *"Una línea de trabajo a largo plazo de alto valor estratégico sería eliminar la dependencia de la API de Gemini para la identificación de especies, reemplazándola por un modelo propio entrenado con las fotografías aportadas por los mismos pescadores a lo largo del tiempo. Este enfoque de aprendizaje colaborativo permitiría construir una base de datos etiquetada con especies de relevancia local, con el objetivo de ofrecer una identificación de calidad comparable a la del modo Premium de forma gratuita para todos los usuarios."*

### PEND-10 · CJMs sin numeración formal como figuras
**Páginas:** 52-57  
**Problema:** Los Customer Journey Maps son visualizaciones elaboradas pero no están numeradas como "Figura 1", "Figura 2", etc., ni referenciadas desde el texto con ese número.  
**Urgencia:** Menor — el tribunal puede aceptarlos como están, pero la numeración es una buena práctica.

### PEND-11 · "TFLite" y "OpenStreetMap" en diagrama de arquitectura (p. 58)
**Problema:** El diagrama aún dice **"TFlite"** (debería ser TFLite o LiteRT) y **"Oppen Street Map"** (doble P). Fue mencionado antes pero sigue igual.  
**Solución:** Corregir el diagrama antes de la versión final.

### PEND-12 · Título de la tesina (decisión pendiente del autor)
**Página:** portada  
**Problema:** El título menciona solo "Tecnología Móvil" pero el sistema incluye también panel web y modelo de visión propio. Decisión del autor mantener o ampliar.

---

## ORDEN SUGERIDO PARA ATACAR LO QUE QUEDA

1. 🔴 **PEND-01 + PEND-02** — Completar Resultados y Conclusiones con datos reales (necesita tu input sobre la prueba piloto)
2. 🟠 **PEND-04** — Corregir PostgreSQL → SQLite en tabla de infraestructura (cambio de 10 segundos en Word)
3. 🟠 **PEND-05** — Corregir año de ML Kit (2026 → lo que corresponda)
4. 🟠 **PEND-06** — Completar "Se realizaron pruebas manuales"
5. 🟠 **PEND-07** — Agregar las 3 referencias faltantes (Hartill, Taylor, Banco Mundial)
6. 🟠 **PEND-03** — Agregar mención del panel en objetivo general
7. 🟡 **PEND-09** — Reescribir último párrafo de Trabajos futuros
8. 🟡 **PEND-08** — Agregar mención del panel en Introducción
9. 🟡 **PEND-11** — Corregir errores de escritura en el diagrama de arquitectura
