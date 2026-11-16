package app.selene.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [PeriodLogEntity::class, SymptomLogEntity::class, NotableEventEntity::class, SettingEntity::class],
    version = 2,
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
                .addMigrations(*MIGRATIONS)
                .build()
        }

        /**
         * v1 → v2: the 1–5 mood becomes a set of moods inside the new "symptoms of the day"
         * table; fatigue moves from the period day to the symptoms (only when > 0, 0 being the
         * former default). The 1–5 mapping is the same as [app.selene.domain.model.Mood.fromLegacyValue].
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `symptom_log` (`date` TEXT NOT NULL, `moods` TEXT NOT NULL, " +
                        "`symptoms` TEXT NOT NULL, `irritability` INTEGER, `fatigue` INTEGER, `note` TEXT, PRIMARY KEY(`date`))",
                )
                db.execSQL(
                    """
                    INSERT INTO symptom_log (date, moods, symptoms, irritability, fatigue, note)
                    SELECT d.date,
                           COALESCE(CASE m.mood WHEN 1 THEN 'crying' WHEN 2 THEN 'sad' WHEN 3 THEN 'neutral'
                                                WHEN 4 THEN 'good' WHEN 5 THEN 'joyful' END, ''),
                           '', NULL,
                           CASE WHEN p.fatigue BETWEEN 1 AND 4 THEN p.fatigue END,
                           NULL
                    FROM (SELECT date FROM mood_log WHERE mood BETWEEN 1 AND 5
                          UNION SELECT date FROM period_log WHERE fatigue BETWEEN 1 AND 4) d
                    LEFT JOIN mood_log m ON m.date = d.date
                    LEFT JOIN period_log p ON p.date = d.date
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE TABLE `period_log_new` (`date` TEXT NOT NULL, `flow` INTEGER NOT NULL, " +
                        "`pain` INTEGER NOT NULL, PRIMARY KEY(`date`))",
                )
                db.execSQL("INSERT INTO period_log_new (date, flow, pain) SELECT date, flow, pain FROM period_log")
                db.execSQL("DROP TABLE period_log")
                db.execSQL("ALTER TABLE period_log_new RENAME TO period_log")
                db.execSQL("DROP TABLE mood_log")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

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
