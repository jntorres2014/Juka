# Checklist actualizado — Tesina Torres (versión 3)
Comparación contra la versión original y los pendientes previos.

---

## ✅ LO QUE YA ESTÁ RESUELTO EN ESTA VERSIÓN

| # | Qué se resolvió |
|---|----------------|
| 1 | **Capítulo de Metodología** agregado con 5 subsecciones completas |
| 2 | **Estado del Arte** restructurado con subsecciones (Heading 3) bien definidas |
| 3 | **Typo "Desarrollos realizado"** corregido a "Desarrollos realizados" |
| 4 | **Dashboard de Monitoreo** agregado como módulo funcional |
| 5 | **Infraestructura Tecnológica** separada en "Aplicación Móvil" y "Aplicación Web" |
| 6 | **Sección técnica del panel web** documentada en "Aplicación Web" |
| 7 | **Objetivo específico del panel web** agregado en los objetivos específicos |
| 8 | **Módulo de Reportes** agregado en "Desarrollos realizados" |
| 9 | **Módulo de Notificaciones** agregado |
| 10 | **Privacidad y Seguridad de los Datos** nuevo capítulo agregado |
| 11 | **Pruebas de concepto** documentadas en detalle (EfficientNetB0, 5 especies, 77.72%) |
| 12 | **4to CJM** agregado (investigadora del CONICET usando Huka Web) |
| 13 | **Fishial API** mencionada como motor alternativo de identificación |
| 14 | **Prompt engineering** contextualizado académicamente |
| 15 | **Notas internas del estado del arte** limpiadas ("Algo de inteligencia artificial", duplicados, etc.) |
| 16 | **Referencia Flask** (Grinberg, 2018) incluida en el texto |

---

## ❌ PENDIENTE CRÍTICO — Sin esto no hay defensa

### PEND-01 · Capítulo de Resultados vacío
**Sección:** Resultados  
**Problema:** El capítulo entero sigue sin datos reales. No hay ningún resultado concreto de las pruebas con usuarios. Solo dice "se espera haber desarrollado..." en futuro/condicional.  
**Qué necesitás:** N° de usuarios de la prueba piloto, instrumento usado (SUS, Likert, cuestionario), puntuaciones obtenidas, observaciones relevantes, tiempo promedio de uso.  
**Urgencia:** 🔴 Máxima — el tribunal no puede evaluar el trabajo sin resultados.

### PEND-02 · Conclusiones con placeholders visibles
**Sección:** Conclusiones y discusiones  
**Problema:** Siguen los marcadores `COMPLETAR: los resultados de las pruebas piloto` y `[ COMPLETAR: limitación relacionada con el tamaño de la muestra ]`.  
**Qué necesitás:** Completar con los datos reales de las pruebas y el N° de participantes.  
**Urgencia:** 🔴 Máxima — no se puede presentar un documento con notas de edición internas visibles.

---

## 🟠 PENDIENTE IMPORTANTE — El tribunal lo va a notar

### PEND-03 · Objetivo general no menciona el panel web
**Sección:** Objetivos → Objetivo general  
**Problema:** El objetivo general todavía describe solo la app móvil. El objetivo específico del panel sí está, pero el general no lo integra.  
**Sugerencia:** Agregar al cierre del objetivo general: *"...complementada por un panel de administración web para la visualización y exportación de los datos por equipos técnicos e investigadores."*

### PEND-04 · Objetivo específico del panel web es demasiado débil
**Sección:** Objetivos → Objetivos específicos  
**Problema:** El nuevo objetivo dice *"Diseñar una aplicación web para poder visualizar de forma rápida los datos obtenidos de la aplicación móvil"* — subestima lo que el panel realmente hace.  
**Sugerencia:** Reemplazarlo por: *"Desarrollar un panel de administración web que permita a los equipos técnicos visualizar, filtrar por especie y período, y exportar en formatos estándar (Excel/CSV) los datos recopilados por la plataforma móvil, cerrando el ciclo de la ciencia ciudadana."*

### PEND-05 · Sin diagramas de software
**Sección:** Implementación / Diseño  
**Problema:** Sigue sin haber ningún diagrama formal: ni casos de uso, ni arquitectura de capas, ni modelo de datos, ni secuencia. La tesis de una Licenciatura en Sistemas los requiere.  
**Urgencia:** 🟠 Alta — el tribunal lo pedirá.  
**Mínimo aceptable:** Un diagrama de arquitectura del sistema completo (app móvil + Firebase + panel web) y un diagrama de casos de uso principal. ¿Querés que los genere?

### PEND-06 · Room Database no documentada en Implementación (móvil)
**Sección:** Implementación → Arquitectura de la aplicación  
**Problema:** La sección técnica de la app móvil no menciona Room ni la estrategia offline-first. Se habla de Firebase pero no de la persistencia local que es lo que habilita el modo sin conexión.  
**Sugerencia:** Agregar un párrafo sobre Room y la estrategia de borradores locales.

### PEND-07 · "Se realizaron pruebas manuales" — oración incompleta
**Sección:** Desarrollos realizados → Pruebas de concepto  
**Problema:** La sección del modelo propio termina con *"Se realizaron pruebas manuales"* sin completar. ¿Cuáles fueron los resultados de esas pruebas manuales?  
**Sugerencia:** Completar con: qué imágenes se probaron, qué resultado dio el modelo, cuáles fueron los errores más comunes.

### PEND-08 · CJMs mencionados como texto plano, no como figuras formales
**Sección:** Desarrollos realizados → CJMs  
**Problema:** Los 4 Customer Journey Maps están descriptos en texto pero no como figuras numeradas (Figura 1, Figura 2...) con título y referencia en el cuerpo del texto. El lector no sabe dónde están las imágenes.  
**Sugerencia:** Insertar los mapas como imágenes numeradas con epígrafe formal, o eliminar las referencias a "la figura" si no hay imagen real.

---

## 🟡 PENDIENTE MENOR — Mejora la calidad académica

### PEND-09 · Introducción no menciona el panel web
**Sección:** Introducción  
**Problema:** La intro describe solo la app móvil. El lector llega al capítulo de "Desarrollos" y encuentra el panel sin haber sido anticipado.  
**Sugerencia:** Agregar una oración al final de la intro: *"Como componente complementario, se desarrolló también un panel de administración web que permite a los equipos de investigación acceder, analizar y exportar los datos recopilados."*

### PEND-10 · Título de la tesina no refleja el sistema completo
**Sección:** Portada  
**Problema:** El título habla de "Tecnología Móvil" pero el sistema final incluye también una aplicación web y un modelo de visión computacional propio.  
**Opciones:** (a) Mantener el título actual (aceptable si el panel web se presenta como componente secundario); (b) ampliar a *"...mediante una Plataforma Digital Integrada"*. Esta es una decisión tuya.

### PEND-11 · Sin modelo de datos formal
**Sección:** Implementación  
**Problema:** No hay descripción de las colecciones de Firestore ni del esquema Room. Son decisiones de diseño evaluables.  
**Urgencia:** Menor si ya hay diagramas de arquitectura; importante si no los hay.

### PEND-12 · Trabajos futuros sin sección propia
**Sección:** Conclusiones  
**Problema:** Los trabajos futuros están al final de las conclusiones, mezclados, sin encabezado propio.  
**Sugerencia:** Agregar un subtítulo "Trabajos futuros" para separarlo visualmente.

### PEND-13 · Tabla de contenidos desactualizada
**Sección:** Índice  
**Problema:** Con todos los cambios hechos en esta versión, el índice seguramente está desactualizado.  
**Solución:** En Word: clic derecho sobre el índice → "Actualizar campos" → "Actualizar toda la tabla".

### PEND-14 · Referencias de pandas y Folium faltan
**Sección:** Referencias bibliográficas  
**Problema:** Se agregó Grinberg (Flask) ✅, pero faltan:  
- McKinney, W. (2010). Data structures for statistical computing in Python. *Proc. 9th Python in Science Conf.*, 56–61.  
- Folium: Filipe et al. (2013). Folium. GitHub. https://github.com/python-visualization/folium

---

## ORDEN SUGERIDO PARA ATACAR LO QUE QUEDA

1. 🔴 **PEND-01** — Completar Resultados con datos reales (necesita tu input)
2. 🔴 **PEND-02** — Eliminar placeholders de Conclusiones (necesita tu input)
3. 🟠 **PEND-05** — Generar diagramas de software (puedo hacerlos yo)
4. 🟠 **PEND-06** — Agregar Room/offline en Implementación (puedo redactarlo)
5. 🟠 **PEND-07** — Completar "Se realizaron pruebas manuales" (necesita tu input)
6. 🟠 **PEND-03/04** — Ajustar objetivos general y específico del panel (puedo redactarlo)
7. 🟡 **PEND-09** — Agregar mención del panel en la intro (puedo redactarlo)
8. 🟡 **PEND-12** — Separar trabajos futuros con subtítulo (cambio menor)
9. 🟡 **PEND-14** — Agregar 2 referencias faltantes
10. 🟡 **PEND-13** — Actualizar índice (lo hacés vos en Word al final)
