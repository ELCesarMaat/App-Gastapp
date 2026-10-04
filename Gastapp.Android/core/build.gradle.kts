import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Libreria Android compartida por el telefono (:app) y el reloj (:wear).
// Aqui viven el cliente Retrofit y los DTO del API (Fase 3) y, en la Fase 5.4, las
// rutas y payloads de la Wearable Data Layer. El reloj todavia usa su propia red.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
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

dependencies {
    // api: quien usa GastappApi necesita ver Retrofit, OkHttp y los serializadores.
    api(libs.retrofit)
    api(libs.retrofit.serialization)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
