package app.selene.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.selene.data.db.DatabaseKeyStore
import app.selene.data.db.SeleneDatabase
import app.selene.domain.model.Mood
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "encryption-test.db"
    private val day = LocalDate.of(2026, 11, 9)

    @After
    fun cleanUp() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun databaseDoesNotOpenWithoutTheKey() = runTest {
        val key = Random.nextBytes(32)
        SeleneDatabase.openEncrypted(context, key, dbName).apply {
            LogRepository(this).saveMood(day, Mood.GOOD)
            close()
        }
        val wrongKey = Random.nextBytes(32)
        val wrong = SeleneDatabase.openEncrypted(context, wrongKey, dbName)
        assertFails { LogRepository(wrong).snapshot() }
        wrong.close()

        val right = SeleneDatabase.openEncrypted(context, key, dbName)
        assertEquals(Mood.GOOD, LogRepository(right).getDay(day).mood)
        right.close()
    }

    @Test
    fun databaseFileIsNotPlainSqlite() = runTest {
        SeleneDatabase.openEncrypted(context, Random.nextBytes(32), dbName).apply {
            LogRepository(this).saveMood(day, Mood.GOOD)
            close()
        }
        val header = context.getDatabasePath(dbName).readBytes().copyOfRange(0, 16)
        assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun keyStoreReturnsTheSameKeyAndStoresItEncrypted() {
        val file = File(context.noBackupFilesDir, "test-key.enc")
        val store = DatabaseKeyStore(file, alias = "selene_test_key")
        try {
            val first = store.getOrCreateKey()
            assertEquals(32, first.size)
            assertContentEquals(first, DatabaseKeyStore(file, alias = "selene_test_key").getOrCreateKey())
            val onDisk = file.readBytes()
            assertFalse(onDisk.toList().windowed(32).any { it.toByteArray().contentEquals(first) })
        } finally {
            store.destroy()
        }
    }
}
