import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// ------------------------------------------------------------------ Firma compartida
//
// :app (telefono) y :wear (reloj) firman SIEMPRE con la misma llave, en debug y en
// release, por partida doble: la Wearable Data Layer solo entrega mensajes entre apps
// con el mismo applicationId Y la misma firma (si no coinciden no llega nada y no hay
// ningun error), y Android rechaza actualizar un APK firmado con otra llave.

/** local.properties no va al repositorio: es el sitio para rutas y claves. */
val localProps: Properties = Properties().apply {
    val archivo = file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}

/** Busca primero en local.properties y luego en las variables de entorno. */
fun ajuste(clave: String, variableEntorno: String): String? =
    localProps.getProperty(clave)?.takeIf { it.isNotBlank() }
        ?: System.getenv(variableEntorno)?.takeIf { it.isNotBlank() }

/**
 * Keystore real de Gastapp (gastappkeystore), el mismo con el que se firmo la app MAUI.
 *
 * Si falta, el release sale sin firmar a proposito en vez de firmarse con la de debug
 * en silencio, que es como se cuelan estos errores. La huella buena empieza con
 * 2B:DD:2F:06 y el DN es CN="Cesar Maat"; verificar con apksigner antes de publicar.
 */
val releaseKeystore: File? = ajuste("gastappKeystore", "GASTAPP_KEYSTORE")
    ?.let(::File)
    ?.takeIf { it.exists() }

/**
 * Keystore de debug de la app MAUI (el de .NET Android, no el de Gradle).
 *
 * Mientras exista la app MAUI, las compilaciones de debug firman con este: asi el reloj
 * habla con la app MAUI de debug y la app nativa de debug se instala encima de ella.
 * Al retirar MAUI se pasa al debug keystore de Gradle (basta con borrar este bloque).
 *
 * Se busca en local.properties (mauiDebugKeystore=...), luego en la variable de entorno
 * MAUI_DEBUG_KEYSTORE y por ultimo en la ruta por defecto de .NET Android. Si no aparece,
 * se firma con el debug de Gradle y solo se pierde el canal con la app MAUI.
 */
val mauiDebugKeystore: File? = run {
    val rutaPorDefecto = System.getenv("LOCALAPPDATA")
        ?.let { "$it\\Xamarin\\Mono for Android\\debug.keystore" }

    val ruta = localProps.getProperty("mauiDebugKeystore")
        ?: System.getenv("MAUI_DEBUG_KEYSTORE")
        ?: rutaPorDefecto

    ruta?.let(::File)?.takeIf { it.exists() }
}

subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
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
                getByName("debug") {
                    signingConfigs.findByName("mauiDebug")?.let { signingConfig = it }
                }
                getByName("release") {
                    // Sin keystore configurado se queda sin firmar y el APK no se puede
                    // instalar. Es intencionado: mejor eso que descubrir tarde que salio
                    // firmado con la llave de debug.
                    signingConfigs.findByName("gastappRelease")?.let { signingConfig = it }
                }
            }
        }
    }
}
