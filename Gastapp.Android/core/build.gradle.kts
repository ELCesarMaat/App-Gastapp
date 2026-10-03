import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Libreria Android compartida por el telefono (:app) y el reloj (:wear).
// Aqui van a vivir el cliente Retrofit y los DTO del API (Fase 3) y las rutas y payloads
// de la Wearable Data Layer (Fase 5.4). Por ahora esta vacia a proposito.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.binc.gastapp.core"
    compileSdk = 36

    defaultConfig {
        // El minimo del reloj: esta libreria la usan las dos apps.
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
