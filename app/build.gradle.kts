plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.juka"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.juka"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
           // minifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "OPENAI_API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
        }
        debug {
            buildConfigField("String", "OPENAI_API_KEY", "\"${project.findProperty("OPENAI_API_KEY") ?: ""}\"")
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
        buildConfig = true // Asegura que BuildConfig se genere
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Firebase
    implementation("com.google.firebase:firebase-firestore-ktx:24.10.3") // Actualizado
    implementation("com.google.firebase:firebase-analytics-ktx:21.6.2") // Actualizado
    implementation("com.google.firebase:firebase-auth-ktx:22.3.1")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Core
    implementation("androidx.core:core-ktx:1.13.0") // Actualizado
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3") // Actualizado
    implementation("androidx.activity:activity-compose:1.9.1") // Actualizado

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00")) // Actualizado
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3") // Actualizado

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7") // Actualizado

    // Coil
    implementation("io.coil-kt:coil-compose:2.6.0") // Actualizado

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1") // Actualizado

    // OkHttp y Gson para xAI API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // ================== ML KIT DEPENDENCIES ==================

    // ML Kit Entity Extraction
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta5")

    // ML Kit Language Identification (opcional)
    implementation("com.google.mlkit:language-id:17.0.4")

    // ML Kit Translation (opcional - para traducir texto)
    implementation("com.google.mlkit:translate:17.0.1")

    // ML Kit Text Recognition (OCR - para leer texto en imágenes)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // ML Kit Smart Reply (para generar respuestas automáticas)
    implementation("com.google.mlkit:smart-reply:17.0.2")
}