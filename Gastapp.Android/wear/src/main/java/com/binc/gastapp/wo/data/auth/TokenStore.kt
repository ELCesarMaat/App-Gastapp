package com.binc.gastapp.wo.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.binc.gastapp.wo.data.remote.DeviceTokenResponse
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.tokenDataStore by preferencesDataStore(name = "gastapp_tokens")

/**
 * Guarda las credenciales del dispositivo.
 *
 * El refresh token se cifra con una llave AES/GCM del AndroidKeyStore, porque
 * androidx.security:security-crypto (EncryptedSharedPreferences) esta deprecada
 * desde 1.1.0-alpha07.
 *
 * El access token vive solo en memoria: dura 15 minutos, asi que persistirlo no
 * aporta nada y solo amplia la superficie expuesta.
 */
class TokenStore(private val context: Context) {

    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var deviceId: String? = null
        private set

    suspend fun save(tokens: DeviceTokenResponse) {
        accessToken = tokens.accessToken
        deviceId = tokens.deviceId

        val cifrado = encrypt(tokens.refreshToken)
        context.tokenDataStore.edit { prefs ->
            prefs[KEY_REFRESH] = cifrado
            prefs[KEY_DEVICE_ID] = tokens.deviceId
        }
    }

    suspend fun readRefreshToken(): String? {
        val guardado = context.tokenDataStore.data.first()[KEY_REFRESH] ?: return null
        return runCatching { decrypt(guardado) }.getOrNull()
    }

    /**
     * El deviceId en memoria se pierde al reiniciar la app, pero el guardado sobrevive.
     * Hace falta para desvincular sin obligar antes a una peticion que refresque.
     */
    suspend fun readDeviceId(): String? =
        context.tokenDataStore.data.first()[KEY_DEVICE_ID]

    /**
     * Saca deviceId y refresh token de una sola pasada. Sirve para desvincular: hay
     * que quedarse con las credenciales ANTES de borrarlas, porque la revocacion en
     * el servidor ocurre despues del borrado local.
     */
    suspend fun credentials(): Credentials? {
        val prefs = context.tokenDataStore.data.first()
        val deviceId = prefs[KEY_DEVICE_ID] ?: return null
        val refresh = prefs[KEY_REFRESH]?.let { guardado ->
            runCatching { decrypt(guardado) }.getOrNull()
        } ?: return null

        return Credentials(deviceId, refresh)
    }

    data class Credentials(val deviceId: String, val refreshToken: String)

    suspend fun hasSession(): Boolean = readRefreshToken() != null

    suspend fun clear() {
        accessToken = null
        deviceId = null
        context.tokenDataStore.edit { it.clear() }
    }

    // ---- Cifrado ----

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Sin autenticacion de usuario: el reloj bloqueado debe poder refrescar
                // el token en segundo plano para que el tile siga actualizandose.
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(valor: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val datos = cipher.doFinal(valor.toByteArray(Charsets.UTF_8))
        // IV + ciphertext concatenados. El IV de GCM siempre mide 12 bytes.
        val salida = cipher.iv + datos
        return Base64.encodeToString(salida, Base64.NO_WRAP)
    }

    private fun decrypt(valor: String): String {
        val bytes = Base64.decode(valor, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
        val datos = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(datos), Charsets.UTF_8)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "gastapp_wear_token_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128

        val KEY_REFRESH = stringPreferencesKey("refresh_token")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    }
}
