package com.iclonud.bridge.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Guarda el Apple ID y la contraseña específica de app en EncryptedSharedPreferences,
 * cifradas con una clave del Android Keystore. La contraseña nunca sale del dispositivo
 * salvo hacia los servidores de Apple (IMAP/SMTP/DAV) por TLS.
 */
class Credentials(context: Context) {

    private val prefs by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "iclonud_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var appleId: String?
        get() = prefs.getString("apple_id", null)
        set(v) = prefs.edit().putString("apple_id", v).apply()

    var appPassword: String?
        get() = prefs.getString("app_password", null)
        set(v) = prefs.edit().putString("app_password", v).apply()

    val isLoggedIn: Boolean get() = !appleId.isNullOrBlank() && !appPassword.isNullOrBlank()

    fun save(appleId: String, appPassword: String) {
        this.appleId = appleId.trim()
        this.appPassword = appPassword.trim().replace(" ", "") // las de Apple vienen con espacios
    }

    fun clear() = prefs.edit().clear().apply()
}
