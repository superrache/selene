package app.selene.data.json

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted envelope around a `selene-export` JSON document (see CLAUDE.md
 * « Format JSON d'export / import »).
 *
 * - Key: PBKDF2-HMAC-SHA256(password as NFC-normalised UTF-8, 16-byte random salt), 32 bytes.
 * - Cipher: AES-256-GCM, 12-byte random IV, 128-bit tag appended to the ciphertext.
 * - Plaintext: the UTF-8 bytes of the plain export.
 *
 * PBKDF2 is computed with [Mac] rather than `SecretKeyFactory` so that the password encoding is
 * the same on every provider (Android, desktop JVM, other tools).
 */
object ExportCrypto {
    const val FORMAT = "selene-encrypted"
    const val CURRENT_VERSION = 1
    const val KDF = "PBKDF2-HMAC-SHA256"
    const val CIPHER = "AES-256-GCM"

    /** OWASP recommendation for PBKDF2-HMAC-SHA256. */
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 8

    /** Accepted on import: guards against a crafted file that would freeze the phone. */
    val ITERATIONS_RANGE = 10_000..10_000_000

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @kotlinx.serialization.Serializable
    data class Envelope(
        val format: String,
        val version: Int,
        val kdf: Kdf,
        val cipher: CipherParams,
        val data: String,
    )

    @kotlinx.serialization.Serializable
    data class Kdf(val name: String, val iterations: Int, val salt: String)

    @kotlinx.serialization.Serializable
    data class CipherParams(val name: String, val iv: String)

    sealed interface DecryptResult {
        data class Success(val plaintext: String) : DecryptResult

        /** GCM cannot tell a wrong password from an altered file. */
        data object WrongPassword : DecryptResult
        data class Invalid(val error: ImportError) : DecryptResult
    }

    /** True when [text] is a JSON object whose `format` is [FORMAT]. Cheap enough for any input. */
    fun isEncrypted(text: String): Boolean = runCatching {
        (json.parseToJsonElement(text) as? JsonObject)?.get("format")?.jsonPrimitive?.contentOrNull == FORMAT
    }.getOrDefault(false)

    fun isAcceptablePassword(password: String): Boolean = normalize(password).codePointCount() >= MIN_PASSWORD_LENGTH

    fun encrypt(
        plaintext: String,
        password: String,
        random: SecureRandom = SecureRandom(),
        iterations: Int = ITERATIONS,
    ): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val data = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        key.fill(0)
        val envelope = Envelope(
            format = FORMAT,
            version = CURRENT_VERSION,
            kdf = Kdf(KDF, iterations, b64(salt)),
            cipher = CipherParams(CIPHER, b64(iv)),
            data = b64(data),
        )
        return json.encodeToString(Envelope.serializer(), envelope)
    }

    fun decrypt(text: String, password: String): DecryptResult {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: return DecryptResult.Invalid(ImportError.NotJson)
        if (runCatching { root["format"]?.jsonPrimitive?.contentOrNull }.getOrNull() != FORMAT) {
            return DecryptResult.Invalid(ImportError.NotSeleneExport)
        }
        val version = runCatching { root["version"]?.jsonPrimitive?.intOrNull }.getOrNull()
        if (version == null || version < 1) return DecryptResult.Invalid(ImportError.MissingVersion)
        if (version > CURRENT_VERSION) return DecryptResult.Invalid(ImportError.UnsupportedVersion(version, CURRENT_VERSION))

        val envelope = try {
            json.decodeFromJsonElement(Envelope.serializer(), root)
        } catch (e: SerializationException) {
            return invalid(e.message)
        } catch (e: IllegalArgumentException) {
            return invalid(e.message)
        }
        if (envelope.kdf.name != KDF) return invalid("kdf.name")
        if (envelope.cipher.name != CIPHER) return invalid("cipher.name")
        if (envelope.kdf.iterations !in ITERATIONS_RANGE) return invalid("kdf.iterations")
        val salt = unb64(envelope.kdf.salt)?.takeIf { it.size >= SALT_BYTES } ?: return invalid("kdf.salt")
        val iv = unb64(envelope.cipher.iv)?.takeIf { it.size == IV_BYTES } ?: return invalid("cipher.iv")
        val data = unb64(envelope.data)?.takeIf { it.size >= TAG_BITS / 8 } ?: return invalid("data")

        val key = deriveKey(password, salt, envelope.kdf.iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            val plain = cipher.doFinal(data)
            DecryptResult.Success(plain.toString(Charsets.UTF_8))
        } catch (e: AEADBadTagException) {
            DecryptResult.WrongPassword
        } catch (e: GeneralSecurityException) {
            DecryptResult.WrongPassword
        } finally {
            key.fill(0)
        }
    }

    /** PBKDF2 (RFC 8018) with HMAC-SHA256, password as NFC-normalised UTF-8. */
    internal fun deriveKey(password: String, salt: ByteArray, iterations: Int, length: Int = KEY_BYTES): ByteArray {
        val passwordBytes = normalize(password).toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        // An empty HMAC key is valid but SecretKeySpec refuses it: HMAC pads short keys with zeros.
        mac.init(SecretKeySpec(passwordBytes.takeIf { it.isNotEmpty() } ?: ByteArray(1), "HmacSHA256"))
        passwordBytes.fill(0)
        val hLen = mac.macLength
        val out = ByteArray(length)
        var block = 1
        var offset = 0
        while (offset < length) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            t.copyInto(out, offset, 0, minOf(hLen, length - offset))
            offset += hLen
            block++
        }
        return out
    }

    private fun normalize(password: String): String = Normalizer.normalize(password, Normalizer.Form.NFC)

    private fun String.codePointCount(): Int = codePointCount(0, length)

    private fun invalid(detail: String?) = DecryptResult.Invalid(ImportError.Structure(detail?.lineSequence()?.firstOrNull().orEmpty()))

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String): ByteArray? = runCatching { Base64.getDecoder().decode(text) }.getOrNull()
}
