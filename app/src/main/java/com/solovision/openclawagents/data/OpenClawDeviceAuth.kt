package com.solovision.openclawagents.data

import android.content.Context
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

private const val DEVICE_AUTH_PREFS = "openclaw_device_auth"
private const val PREF_DEVICE_TOKEN = "device_token"
private const val PREF_GRANTED_SCOPES = "granted_scopes"
private const val PREF_DEVICE_ID = "identity_v2_device_id"
private const val PREF_PUBLIC_KEY_RAW = "identity_v2_public_key_raw"
private const val PREF_PRIVATE_KEY_PKCS8 = "identity_v2_private_key_pkcs8"
private val ED25519_SPKI_PREFIX = byteArrayOf(
    0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00
)

internal data class StoredDeviceAuth(
    val deviceToken: String? = null,
    val grantedScopes: List<String> = emptyList()
)

internal data class DeviceIdentity(
    val deviceId: String,
    val publicKeyRawBase64: String,
    val privateKeyPkcs8Base64: String
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

    fun loadIdentity(): DeviceIdentity? {
        val deviceId = prefs.getString(PREF_DEVICE_ID, null)?.trim().orEmpty()
        val publicKeyRawBase64 = prefs.getString(PREF_PUBLIC_KEY_RAW, null)?.trim().orEmpty()
        val privateKeyPkcs8Base64 = prefs.getString(PREF_PRIVATE_KEY_PKCS8, null)?.trim().orEmpty()
        if (deviceId.isBlank() || publicKeyRawBase64.isBlank() || privateKeyPkcs8Base64.isBlank()) {
            return null
        }
        return DeviceIdentity(
            deviceId = deviceId,
            publicKeyRawBase64 = publicKeyRawBase64,
            privateKeyPkcs8Base64 = privateKeyPkcs8Base64
        )
    }

    fun saveIdentity(identity: DeviceIdentity) {
        prefs.edit()
            .putString(PREF_DEVICE_ID, identity.deviceId)
            .putString(PREF_PUBLIC_KEY_RAW, identity.publicKeyRawBase64)
            .putString(PREF_PRIVATE_KEY_PKCS8, identity.privateKeyPkcs8Base64)
            .apply()
    }
}

internal class OpenClawDeviceIdentity(
    private val store: OpenClawDeviceAuthStore
) {
    @Volatile
    private var cachedIdentity: DeviceIdentity? = null

    fun publicKeyBase64Url(): String {
        val raw = Base64.decode(identity().publicKeyRawBase64, Base64.DEFAULT)
        return base64UrlEncode(raw)
    }

    fun publicKeyRawSize(): Int = Base64.decode(identity().publicKeyRawBase64, Base64.DEFAULT).size

    fun deviceId(): String = identity().deviceId

    fun signPayload(payload: String): String {
        val pkcs8 = Base64.decode(identity().privateKeyPkcs8Base64, Base64.DEFAULT)
        val privateKey = KeyFactory.getInstance("Ed25519")
            .generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(signer.sign())
    }

    fun verifySelfSignature(payload: String, signatureBase64Url: String): Boolean {
        return runCatching {
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey())
            verifier.update(payload.toByteArray(Charsets.UTF_8))
            verifier.verify(base64UrlDecode(signatureBase64Url))
        }.getOrElse { error ->
            Log.w("OpenClawDeviceAuth", "Self verification failed", error)
            false
        }
    }

    private fun identity(): DeviceIdentity {
        cachedIdentity?.let { return it }
        val existing = store.loadIdentity()?.let(::normalizeIdentity)
        if (existing != null) {
            cachedIdentity = existing
            return existing
        }

        val generator = KeyPairGenerator.getInstance("Ed25519")
        val pair = generator.generateKeyPair()
        val rawPublic = extractEd25519RawPublicKey(pair.public.encoded)
        val generated = DeviceIdentity(
            deviceId = sha256Hex(rawPublic),
            publicKeyRawBase64 = Base64.encodeToString(rawPublic, Base64.NO_WRAP),
            privateKeyPkcs8Base64 = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
        )
        store.saveIdentity(generated)
        cachedIdentity = generated
        return generated
    }

    private fun normalizeIdentity(identity: DeviceIdentity): DeviceIdentity? {
        return runCatching {
            val rawPublic = Base64.decode(identity.publicKeyRawBase64, Base64.DEFAULT)
            require(rawPublic.size == 32) { "Unexpected Ed25519 public key size: ${rawPublic.size}" }
            KeyFactory.getInstance("Ed25519")
                .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(identity.privateKeyPkcs8Base64, Base64.DEFAULT)))
            KeyFactory.getInstance("Ed25519")
                .generatePublic(
                    X509EncodedKeySpec(
                        ByteArray(ED25519_SPKI_PREFIX.size + rawPublic.size).also { encoded ->
                            System.arraycopy(ED25519_SPKI_PREFIX, 0, encoded, 0, ED25519_SPKI_PREFIX.size)
                            System.arraycopy(rawPublic, 0, encoded, ED25519_SPKI_PREFIX.size, rawPublic.size)
                        }
                    )
                )
            val normalized = identity.copy(deviceId = sha256Hex(rawPublic))
            if (normalized != identity) {
                store.saveIdentity(normalized)
            }
            normalized
        }.getOrElse { error ->
            Log.w("OpenClawDeviceAuth", "Stored identity invalid, regenerating", error)
            null
        }
    }

    private fun extractEd25519RawPublicKey(encoded: ByteArray): ByteArray {
        if (encoded.size == ED25519_SPKI_PREFIX.size + 32 &&
            encoded.copyOfRange(0, ED25519_SPKI_PREFIX.size).contentEquals(ED25519_SPKI_PREFIX)
        ) {
            return encoded.copyOfRange(ED25519_SPKI_PREFIX.size, encoded.size)
        }
        error("Unexpected Ed25519 public key encoding")
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun publicKey(): PublicKey {
        val rawPublic = Base64.decode(identity().publicKeyRawBase64, Base64.DEFAULT)
        val encoded = ByteArray(ED25519_SPKI_PREFIX.size + rawPublic.size)
        System.arraycopy(ED25519_SPKI_PREFIX, 0, encoded, 0, ED25519_SPKI_PREFIX.size)
        System.arraycopy(rawPublic, 0, encoded, ED25519_SPKI_PREFIX.size, rawPublic.size)
        return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(encoded))
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun base64UrlDecode(data: String): ByteArray {
        val normalized = data.replace('-', '+').replace('_', '/')
        val padding = "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.decode(normalized + padding, Base64.DEFAULT)
    }
}
