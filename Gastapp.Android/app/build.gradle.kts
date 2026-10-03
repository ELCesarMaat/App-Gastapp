import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// La firma (debug y release) se configura en el build.gradle.kts raiz, compartida con
// :wear: la Data Layer solo entrega entre apps firmadas con la misma llave.

android {
    namespace = "com.binc.gastapp"
    compileSdk = 36

    defaultConfig {
        // Tiene que ser IDENTICO al <ApplicationId> de Gastapp.csproj y al del reloj: asi
        // la app nativa se instala como actualizacion de la MAUI y la Data Layer entrega.
        applicationId = "com.binc.gastapp"
        minSdk = 31
        targetSdk = 36
        // Tiene que superar al <ApplicationVersion> de MAUI (130). El 200 marca la era nativa.
        versionCode = 200
        versionName = "2.0.0"

        buildConfigField("String", "API_BASE_URL", "\"https://app-gastapp.onrender.com/api/\"")
    }

    buildTypes {
        debug {
            // 10.0.2.2 es el host de la maquina vista desde el emulador de Android.
            // Descomenta para desarrollar contra la API corriendo en local (el texto
            // claro solo se permite en debug, ver src/debug/res/xml).
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5118/api/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric necesita los recursos de Android en las pruebas locales.
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // El esquema de Room de cada version se guarda en el repositorio: es lo que permite
    // escribir y probar las migraciones cuando cambie la base.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Datos locales (Fase 2): Room como fuente de verdad y DataStore para preferencias.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
}
