package com.binc.gastapp.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.binc.gastapp.BuildConfig
import com.binc.gastapp.core.remote.ApiJson
import com.binc.gastapp.core.remote.ApiResult
import com.binc.gastapp.core.remote.AppLatestVersionDto
import com.binc.gastapp.core.remote.GastappApi
import com.binc.gastapp.core.remote.apiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** La version que esta instalada. Las pruebas pasan otra. */
data class InstalledVersion(val code: Int, val name: String) {
    companion object {
        val Current = InstalledVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)
    }
}

sealed interface UpdateCheck {
    data class Available(val latest: AppLatestVersionDto) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
}

/**
 * Actualizacion sin Play Store (AppUpdateService de MAUI): el API expone el ultimo
 * release de GitHub (APK del telefono + version.json, con cache de 10 min), se compara su
 * versionCode con el instalado y, si es mayor, se baja el APK a la cache y se le pide a
 * Android que lo instale. Requiere el permiso de "instalar apps desconocidas".
 */
@Singleton
class AppUpdater internal constructor(
    private val context: Context,
    private val api: GastappApi,
    private val client: OkHttpClient,
    private val installed: InstalledVersion,
    /** Solo en debug y para probar: un version.json propio en vez del API (ver build.gradle.kts). */
    private val manifestUrl: String,
) {
    @Inject
    constructor(@ApplicationContext context: Context, api: GastappApi) : this(
        context = context,
        api = api,
        client = OkHttpClient.Builder()
            // El APK pesa decenas de MB; el limite es entre bytes, no para todo el archivo.
            .readTimeout(60, TimeUnit.SECONDS)
            .build(),
        installed = InstalledVersion.Current,
        manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
    )

    val installedVersion: InstalledVersion get() = installed

    /** Sin conexion o con el API caido es [UpdateCheck.Failed]: nunca interrumpe el arranque. */
    suspend fun check(): UpdateCheck {
        val latest = latestVersion() ?: return UpdateCheck.Failed
        return if (latest.versionCode > installed.code && latest.apkUrl.isNotBlank()) UpdateCheck.Available(latest) else UpdateCheck.UpToDate
    }

    private suspend fun latestVersion(): AppLatestVersionDto? {
        if (manifestUrl.isBlank()) {
            return (apiCall { api.getLatestVersion() } as? ApiResult.Success)?.value
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(manifestUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    ApiJson.decodeFromString(AppLatestVersionDto.serializer(), response.body!!.string())
                }
            }.getOrNull()
        }
    }

    /** Android 8+ pide permiso por app para instalar APKs. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** La pantalla del sistema donde se da ese permiso a Gastapp. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Baja el APK a la cache (lo que expone el FileProvider) y comprueba que sea de esta
     * app y mas nuevo que el instalado. [onProgress] recibe de 0 a 1, o null si el
     * servidor no dice el tamano.
     */
    suspend fun download(latest: AppLatestVersionDto, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, ApkName)
        // Ya se bajo en un intento anterior (p. ej. se fue a dar el permiso y volvio).
        if (target.exists() && isValidUpdate(target)) return@withContext target

        val partial = File(context.cacheDir, "$ApkName.part")
        client.newCall(Request.Builder().url(latest.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("El servidor respondió ${response.code}.")
            val body = response.body ?: throw IOException("Respuesta vacía.")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = -1
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val percent = total?.let { (copied * 100 / it).toInt() } ?: -1
                        if (percent != lastReported) {
                            lastReported = percent
                            onProgress(total?.let { copied.toFloat() / it })
                        }
                    }
                }
            }
        }
        if (!isValidUpdate(partial)) {
            partial.delete()
            throw IOException("El archivo descargado no es una actualización válida de Gastapp.")
        }
        target.delete()
        if (!partial.renameTo(target)) throw IOException("No se pudo guardar la actualización.")
        target
    }

    /** Abre el instalador del sistema con el APK descargado. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Mismo paquete y versionCode mayor que el instalado. La firma la revisa el instalador. */
    private fun isValidUpdate(apk: File): Boolean {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.path, 0)
        } ?: return false
        return info.packageName == context.packageName && info.longVersionCode > installed.code
    }

    private companion object {
        /** El mismo nombre que usaba MAUI. */
        const val ApkName = "gastapp-update.apk"
    }
}
