# Juka - Tu Compañero de Pesca Inteligente 🎣

Juka es una aplicación móvil para Android diseñada para ser el asistente definitivo de todo pescador. Ya seas un aficionado o un experto, Juka te proporciona las herramientas necesarias para mejorar tu experiencia de pesca.

## ✨ Características Principales

*   **🔍 Identificador de Peces:** Utiliza la cámara de tu móvil para identificar diferentes especies de peces al instante.
*   **📊 Mis Reportes:** Lleva un registro detallado de tus jornadas de pesca, incluyendo capturas, ubicaciones y condiciones climáticas.
*   **🤖 Asistente de Chat con IA:** ¿Tienes alguna pregunta sobre técnicas de pesca, carnadas o el mejor momento para pescar? Nuestro chatbot inteligente está aquí para ayudarte 24/7.
*   **📚 Pescadex:** Una completa enciclopedia de peces de Argentina, con información detallada sobre hábitats, carnadas recomendadas, temporadas de pesca y mucho más.
*   **👤 Perfil de Usuario:** Inicia sesión de forma segura con Google para mantener tus registros y preferencias sincronizadas.

## 🛠️ Tecnologías Utilizadas

*   **Lenguaje:** Kotlin
*   **UI:** Jetpack Compose para una interfaz de usuario moderna y declarativa.
*   **Base de Datos:** Firebase (Authentication para la gestión de usuarios y Firestore para el almacenamiento de datos).
*   **Navegación:** Jetpack Navigation Compose para una navegación fluida y consistente.
*   **Arquitectura:** MVVM (Model-View-ViewModel) con una estructura de paquetes basada en features.

## 🚀 Cómo Empezar

Sigue estos pasos para compilar y ejecutar el proyecto en tu máquina local:

1.  **Clonar el repositorio:**
    ```bash
    git clone https://github.com/tu-usuario/juka.git
    ```
2.  **Abrir en Android Studio:**
    Abre el proyecto clonado con la última versión de Android Studio.

3.  **Configurar Firebase:**
    *   Ve a la [Consola de Firebase](https://console.firebase.google.com/).
    *   Crea un nuevo proyecto.
    *   Añade una aplicación Android con el nombre de paquete `com.example.juka`.
    *   Descarga el archivo `google-services.json` y colócalo en el directorio `app/`.

4.  **Compilar y Ejecutar:**
    Sincroniza el proyecto con los archivos de Gradle y ejecútalo en un emulador o dispositivo físico.

## 📂 Estructura del Proyecto

El código está organizado siguiendo una arquitectura limpia y modular para facilitar el mantenimiento y la escalabilidad.

```
juka/
└── app/
    └── src/
        └── main/
            ├── java/com/example/juka/
            │   ├── data/         # Modelos de datos, repositorios, y gestores como AuthManager.
            │   ├── features/     # Cada feature de la app en su propio paquete (auth, chat, identificar, etc.).
            │   ├── navigation/   # Lógica de navegación con Jetpack Compose.
            │   ├── ui/           # Temas, tipografía y componentes de UI reutilizables.
            │   └── viewmodel/    # ViewModels que conectan la lógica de negocio con la UI.
            └── assets/           # Archivos estáticos, como el JSON de la Pescadex.
```

## 🤝 Contribuciones

¡Las contribuciones son bienvenidas! Si tienes ideas, sugerencias o quieres arreglar un bug, por favor, abre un issue o envía un pull request.

---

_Creado con ❤️ por el equipo de Juka._
