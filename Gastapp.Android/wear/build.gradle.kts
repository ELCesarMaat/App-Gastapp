import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// La firma (debug y release) se configura en el build.gradle.kts raiz, compartida con
// :app: la Data Layer solo entrega entre apps firmadas con la misma llave.

android {
    namespace = "com.binc.gastapp.wo"
    compileSdk = 36

    defaultConfig {
        // Tiene que ser IDENTICO al <ApplicationId> de Gastapp.csproj: la Data Layer
        // solo entrega entre apps con el mismo nombre de paquete. El namespace de
        // Kotlin se queda como estaba, son cosas independientes en AGP.
        applicationId = "com.binc.gastapp"
        minSdk = 30
        targetSdk = 35
        // Se versiona a la par que la app del telefono para que sea evidente que
        // pareja de APK corresponde a cada release.
        versionCode = 3
        versionName = "1.1.2-alpha1"

        // URL de la API. Se lee desde BuildConfig para poder apuntar a una instancia
        // local durante el desarrollo sin tocar codigo.
        buildConfigField("String", "API_BASE_URL", "\"https://app-gastapp.onrender.com/api/\"")
    }

    buildTypes {
        debug {
            // 10.0.2.2 es el host de la maquina vista desde el emulador de Android.
            // Descomenta para desarrollar contra la API corriendo en local.
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5199/api/\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.tiles.material)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)

    implementation(libs.androidx.wear.input)
    implementation(libs.kotlinx.coroutines.guava)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
