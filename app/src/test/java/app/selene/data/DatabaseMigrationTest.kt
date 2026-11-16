package app.selene.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.data.db.SeleneDatabase
import app.selene.domain.model.DayLog
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.SymptomLog
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Builds a version 1 database exactly as Room created it (schemas/…/1.json), then opens it with
 * the current Room schema: Room runs the migration and validates the resulting tables.
 * Plain SQLite under Robolectric; SQLCipher does not change the SQL.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun version1MoodsAndFatigueMoveToTheSymptomsOfTheDay() = runTest {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            V1_SCHEMA.forEach(db::execSQL)
            db.execSQL("INSERT INTO period_log VALUES ('2026-11-01', 3, 2, 4), ('2026-11-02', 2, 1, 0)")
            db.execSQL("INSERT INTO mood_log VALUES ('2026-11-01', 1), ('2026-11-02', 4), ('2026-11-05', 5), ('2026-11-06', 9)")
            db.execSQL("INSERT INTO notable_event VALUES ('e1', '2026-11-05', 'Voyage')")
            db.execSQL("INSERT INTO settings VALUES ('calendar_sync', 'true')")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, SeleneDatabase::class.java, name)
            .addMigrations(*SeleneDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val days = LogRepository(room).snapshot()
        val d1 = LocalDate.of(2026, 11, 1)
        val d2 = LocalDate.of(2026, 11, 2)
        val d5 = LocalDate.of(2026, 11, 5)
        assertEquals(
            listOf(
                DayLog(d1, PeriodLog(d1, 3, 2), SymptomLog(d1, moods = setOf(Mood.CRYING), fatigue = 4)),
                DayLog(d2, PeriodLog(d2, 2, 1), SymptomLog(d2, moods = setOf(Mood.GOOD))),
                DayLog(d5, symptoms = SymptomLog(d5, moods = setOf(Mood.JOYFUL)), events = listOf(NotableEvent("e1", d5, "Voyage"))),
            ),
            days,
        )
        assertEquals("true", room.settingsDao().get("calendar_sync"))
        room.close()
        context.deleteDatabase(name)
    }

    private companion object {
        val V1_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `period_log` (`date` TEXT NOT NULL, `flow` INTEGER NOT NULL, `pain` INTEGER NOT NULL, `fatigue` INTEGER NOT NULL, PRIMARY KEY(`date`))",
            "CREATE TABLE IF NOT EXISTS `mood_log` (`date` TEXT NOT NULL, `mood` INTEGER NOT NULL, PRIMARY KEY(`date`))",
            "CREATE TABLE IF NOT EXISTS `notable_event` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `title` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_notable_event_date` ON `notable_event` (`date`)",
            "CREATE TABLE IF NOT EXISTS `settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'da2eee8bd7239bd58b4ec616f682eeda')",
        )
    }
}
