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
        // Cada release sube el versionCode: la actualizacion dentro de la app solo avisa si
        // el version.json del ultimo release de GitHub trae uno mayor.
        versionCode = 201
        versionName = "2.0.1"

        buildConfigField("String", "API_BASE_URL", "\"https://app-gastapp.onrender.com/api/\"")
        // Vacio = la ultima version la dice el API (App/LatestVersion). Ver debug abajo.
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"\"")
    }

    buildTypes {
        debug {
            // 10.0.2.2 es el host de la maquina vista desde el emulador de Android.
            // Descomenta para desarrollar contra la API corriendo en local (el texto
            // claro solo se permite en debug, ver src/debug/res/xml).
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5118/api/\"")
            // Para probar la actualizacion sin publicar un release: un version.json propio
            // (la forma de AppLatestVersionDto) servido desde la maquina, p. ej. con
            // `python -m http.server 8000` en una carpeta con el json y el APK.
            // buildConfigField("String", "UPDATE_MANIFEST_URL", "\"http://10.0.2.2:8000/version.json\"")
        }
        release {
            // R8 (Fase 6): quita codigo y recursos sin usar (sobre todo los miles de iconos
            // de material-icons-extended) y ofusca. Las reglas propias estan en
            // proguard-rules.pro; Retrofit, kotlinx.serialization, Room, Hilt y WorkManager
            // traen las suyas dentro de sus librerias.
            // Apagado por ahora (decision del usuario, v2.0.1): falta probar en el emulador
            // que el APK minificado no rompa login, sincronizacion ni Room. Para activarlo,
            // poner los dos en true.
            isMinifyEnabled = false
            isShrinkResources = false
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

    sourceSets {
        // Los fixtures de contrato del API viven en :core (los genera tools/Gastapp.Contratos);
        // las pruebas de sincronizacion de :app usan los mismos.
        getByName("test").resources.srcDir("../core/src/test/resources")
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

    // Red y sincronizacion (Fase 3): Retrofit/OkHttp llegan con :core.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Funciones de plataforma (Fase 5): reloj por la Data Layer y el "hoy" que cambia al
    // volver de segundo plano.
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.lifecycle.process)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
}
