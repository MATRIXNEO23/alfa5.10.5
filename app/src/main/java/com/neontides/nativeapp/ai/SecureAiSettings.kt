package com.neontides.nativeapp.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureAiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("secure_ai_settings", Context.MODE_PRIVATE)

    var geminiKey: String
        get() = decrypt(prefs.getString("gemini_key", null))
        set(value) = save("gemini_key", value)

    var openAiKey: String
        get() = decrypt(prefs.getString("openai_key", null))
        set(value) = save("openai_key", value)

    fun hasGemini() = geminiKey.isNotBlank()
    fun hasOpenAi() = openAiKey.isNotBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun save(name: String, value: String) {
        val clean = value.trim()
        if (clean.isBlank()) prefs.edit().remove(name).apply()
        else prefs.edit().putString(name, encrypt(clean)).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(value: String?): String = runCatching {
        if (value.isNullOrBlank()) return@runCatching ""
        val parts = value.split(':', limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }.getOrDefault("")

    private companion object {
        const val KEY_ALIAS = "neon_tides_ai_keys_v1"
    }
}
