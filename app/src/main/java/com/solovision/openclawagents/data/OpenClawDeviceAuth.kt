package com.solovision.openclawagents.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

private const val DEVICE_AUTH_PREFS = "openclaw_device_auth"
private const val PREF_DEVICE_TOKEN = "device_token"
private const val PREF_GRANTED_SCOPES = "granted_scopes"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALGORITHM_EC = "EC"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
private const val EC_CURVE = "secp256r1"
private val P256_SPKI_PREFIX = byteArrayOf(
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(),
    0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D,
    0x03, 0x01, 0x07, 0x03, 0x42, 0x00
)

internal data class StoredDeviceAuth(
    val deviceToken: String? = null,
    val grantedScopes: List<String> = emptyList()
)

internal class OpenClawDeviceAuthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(DEVICE_AUTH_PREFS, Context.MODE_PRIVATE)

    fun read(): StoredDeviceAuth {
        val scopes = prefs.getStringSet(PREF_GRANTED_SCOPES, emptySet())
            ?.toList()
            ?.sorted()
            .orEmpty()
        return StoredDeviceAuth(
            deviceToken = prefs.getString(PREF_DEVICE_TOKEN, null),
            grantedScopes = scopes
        )
    }

    fun write(deviceToken: String?, grantedScopes: List<String>) {
        prefs.edit()
            .putString(PREF_DEVICE_TOKEN, deviceToken)
            .putStringSet(PREF_GRANTED_SCOPES, grantedScopes.toSet())
            .apply()
    }
}

internal class OpenClawDeviceIdentity(private val alias: String) {
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    fun publicKeyBase64Url(): String = Base64.encodeToString(publicKeyRawBytes(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    fun deviceId(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyRawBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun signPayload(payload: String): String {
        val privateKey = keyStore.getKey(alias, null)
            ?: generateKeyPair().private
        val signer = Signature.getInstance(SIGNATURE_ALGORITHM)
        signer.initSign(privateKey as java.security.PrivateKey)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun publicKey(): PublicKey {
        val existing = keyStore.getCertificate(alias)?.publicKey
        return existing ?: generateKeyPair().public
    }

    private fun publicKeyRawBytes(): ByteArray {
        val encoded = publicKey().encoded
        return if (encoded.size == P256_SPKI_PREFIX.size + 64 && encoded.copyOfRange(0, P256_SPKI_PREFIX.size).contentEquals(P256_SPKI_PREFIX)) {
            encoded.copyOfRange(P256_SPKI_PREFIX.size, encoded.size)
        } else {
            encoded
        }
    }

    private fun generateKeyPair() = KeyPairGenerator.getInstance(KEY_ALGORITHM_EC, KEYSTORE_PROVIDER).apply {
        initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
    }.generateKeyPair()
}
