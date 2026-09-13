import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.juka"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jonytorres.huka"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.reader())
        }

        // Compatibilidad temporal con el Chat IA anterior de Huka.
        // La clave permanece fuera del repositorio, en local.properties.
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("geminiApiKey", "")}\""
        )

        // Configuración del segundo Firebase usado solo para AI Logic.
        // Los valores permanecen fuera del repositorio, en local.properties.
        buildConfigField(
            "String",
            "HUKA_AI_PROJECT_ID",
            "\"${localProperties.getProperty("hukaAiProjectId", "")}\""
        )
        buildConfigField(
            "String",
            "HUKA_AI_APP_ID",
            "\"${localProperties.getProperty("hukaAiAppId", "")}\""
        )
        buildConfigField(
            "String",
            "HUKA_AI_API_KEY",
            "\"${localProperties.getProperty("hukaAiFirebaseApiKey", "")}\""
        )
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // BoM 33.15.0 incluye Firebase AI Logic 16.1.0 + App Check 18.0.0.
    // Kotlin/Compose/KSP ya están alineados con la metadata Kotlin 2.1 usada
    // por estas versiones de Firebase.
    implementation(platform("com.google.firebase:firebase-bom:33.15.0"))

    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Firebase AI Logic para la prueba con el proyecto secundario Huka AI Free.
    implementation("com.google.firebase:firebase-ai")

    // SDK directo de Gemini usado como fallback estable durante la prueba.
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // App Check. Debug para Android Studio; Play Integrity para release.
    // Enforcement se habilita recién después de validar la migración.
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Google Sign In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Firebase In-App Messaging
    implementation("com.google.firebase:firebase-inappmessaging-display")

    // Core
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coil
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // OkHttp y Gson
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Maps y Location
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // ML Kit
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta5")
    implementation("com.google.mlkit:language-id:17.0.4")
    implementation("com.google.mlkit:translate:17.0.1")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:smart-reply:17.0.2")

    // Serialización
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // LiteRT (sucesor de TFLite — soporta ops v12+)
    implementation("com.google.ai.edge.litert:litert:1.0.1")
    implementation("com.google.ai.edge.litert:litert-support:1.0.1")

    // WorkManager — sincronización offline de borradores
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
