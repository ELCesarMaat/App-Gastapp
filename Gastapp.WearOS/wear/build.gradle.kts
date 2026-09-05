import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/** local.properties no va al repositorio: es el sitio para rutas y claves. */
val localProps: Properties = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

/** Busca primero en local.properties y luego en las variables de entorno. */
fun ajuste(clave: String, variableEntorno: String): String? =
    localProps.getProperty(clave)?.takeIf { it.isNotBlank() }
        ?: System.getenv(variableEntorno)?.takeIf { it.isNotBlank() }

/**
 * Keystore real de Gastapp, el mismo con el que se firma el APK del telefono.
 *
 * Tiene que ser el mismo en las dos apps por partida doble: la Data Layer solo entrega
 * entre apps con la misma firma, y Android rechaza actualizar un APK firmado con otra
 * llave. Si falta, el release sale sin firmar a proposito en vez de firmarse con la de
 * debug en silencio, que es como se cuelan estos errores.
 */
val releaseKeystore: File? = ajuste("gastappKeystore", "GASTAPP_KEYSTORE")
    ?.let(::File)
    ?.takeIf { it.exists() }

/**
 * Keystore de debug de la app MAUI.
 *
 * La Wearable Data Layer solo entrega mensajes entre apps con el mismo applicationId
 * Y la misma firma. .NET Android y Gradle usan keystores de debug DISTINTOS por
 * defecto, asi que si el reloj firmara con el suyo los mensajes no llegarian nunca,
 * y sin ningun error: simplemente no se entregan.
 *
 * Se busca en local.properties (mauiDebugKeystore=...), luego en la variable de
 * entorno MAUI_DEBUG_KEYSTORE, y por ultimo en la ruta por defecto de .NET Android.
 * Si no aparece, se firma como siempre y solo se pierde el canal con el telefono.
 */
val mauiDebugKeystore: File? = run {
    val locales = localProps

    val rutaPorDefecto = System.getenv("LOCALAPPDATA")
        ?.let { "$it\\Xamarin\\Mono for Android\\debug.keystore" }

    val ruta = locales.getProperty("mauiDebugKeystore")
        ?: System.getenv("MAUI_DEBUG_KEYSTORE")
        ?: rutaPorDefecto

    ruta?.let(::File)?.takeIf { it.exists() }
}

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

    signingConfigs {
        mauiDebugKeystore?.let { archivo ->
            create("mauiDebug") {
                storeFile = archivo
                // Credenciales fijas del keystore de debug de Android; no son secretas.
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }

        releaseKeystore?.let { archivo ->
            create("gastappRelease") {
                storeFile = archivo
                storePassword = ajuste("gastappKeystorePassword", "GASTAPP_KEYSTORE_PASSWORD")
                keyAlias = ajuste("gastappKeyAlias", "GASTAPP_KEY_ALIAS")
                keyPassword = ajuste("gastappKeyPassword", "GASTAPP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Firmar con el keystore de la app MAUI para que la Data Layer entregue.
            signingConfigs.findByName("mauiDebug")?.let { signingConfig = it }

            // 10.0.2.2 es el host de la maquina vista desde el emulador de Android.
            // Descomenta para desarrollar contra la API corriendo en local.
            // buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5199/api/\"")
        }
        release {
            // Si no hay keystore configurado se queda sin firmar y el APK no se puede
            // instalar. Es intencionado: mejor que fallar al instalarlo que descubrir
            // tarde que salio firmado con la llave de debug.
            signingConfigs.findByName("gastappRelease")?.let { signingConfig = it }

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

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
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
