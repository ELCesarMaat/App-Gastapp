import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Logica de negocio de Gastapp en Kotlin puro: sin Android, para que no pueda
// depender de el aunque se quiera y para que sus pruebas corran en segundos.
// Las pruebas de paridad comparan contra los resultados de la app MAUI
// (fixtures generados por tools/Gastapp.Paridad).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.test {
    maxHeapSize = "1g"
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
