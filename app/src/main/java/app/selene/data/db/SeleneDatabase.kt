package app.selene.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [PeriodLogEntity::class, MoodLogEntity::class, NotableEventEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SeleneDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val FILE_NAME = "selene.db"

        /** Opens the SQLCipher-encrypted database with the given raw 32-byte key. */
        fun openEncrypted(context: Context, rawKey: ByteArray, fileName: String = FILE_NAME): SeleneDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(sqlCipherRawKey(rawKey))
            return Room.databaseBuilder(context.applicationContext, SeleneDatabase::class.java, fileName)
                .openHelperFactory(factory)
                .build()
        }

        /**
         * SQLCipher "raw key" syntax x'<64 hex>': the random key is used directly,
         * without the passphrase derivation (PBKDF2), which would only slow down opening.
         */
        internal fun sqlCipherRawKey(rawKey: ByteArray): ByteArray {
            require(rawKey.size == 32) { "key must be 32 bytes" }
            val hex = rawKey.joinToString("") { "%02x".format(it) }
            return "x'$hex'".toByteArray(Charsets.US_ASCII)
        }
    }
}
