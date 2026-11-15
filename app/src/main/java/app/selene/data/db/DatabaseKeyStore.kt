package app.selene.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Database key management.
 *
 * At first launch, 32 random bytes are generated; they are encrypted with a non-exportable
 * AES-GCM key held by the Android Keystore and stored, encrypted, in the app's private
 * no-backup directory. The clear key only ever lives in memory.
 *
 * File format: [version:1][iv:12][ciphertext+tag].
 */
class DatabaseKeyStore(
    private val keyFile: File,
    private val alias: String = KEYSTORE_ALIAS,
) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, KEY_FILE_NAME))

    class KeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

    val hasKey: Boolean get() = keyFile.exists()

    @Synchronized
    fun getOrCreateKey(): ByteArray = if (keyFile.exists()) readKey() else createKey()

    /** Forgets the key: the database becomes unreadable. Used by "erase all data" after deleting the file. */
    @Synchronized
    fun destroy() {
        keyFile.delete()
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun createKey(): ByteArray {
        val raw = ByteArray(KEY_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(createIfMissing = true))
        val iv = cipher.iv
        check(iv.size == IV_SIZE)
        val encrypted = cipher.doFinal(raw)
        keyFile.parentFile?.mkdirs()
        val atomic = AtomicFile(keyFile)
        val out = atomic.startWrite()
        try {
            out.write(FORMAT_VERSION)
            out.write(iv)
            out.write(encrypted)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
        return raw
    }

    private fun readKey(): ByteArray {
        try {
            val bytes = AtomicFile(keyFile).readFully()
            if (bytes.isEmpty() || bytes[0].toInt() != FORMAT_VERSION) throw KeyUnavailableException("unknown key file format")
            val iv = bytes.copyOfRange(1, 1 + IV_SIZE)
            val encrypted = bytes.copyOfRange(1 + IV_SIZE, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(createIfMissing = false), GCMParameterSpec(TAG_BITS, iv))
            return cipher.doFinal(encrypted).also { check(it.size == KEY_SIZE) }
        } catch (e: KeyUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw KeyUnavailableException("database key cannot be decrypted", e)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun wrappingKey(createIfMissing: Boolean): SecretKey {
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        if (!createIfMissing) throw KeyUnavailableException("keystore key missing")
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val KEY_FILE_NAME = "db.key.enc"
        const val KEYSTORE_ALIAS = "selene_db_wrapping_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 32
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
        private const val FORMAT_VERSION = 1
    }
}
