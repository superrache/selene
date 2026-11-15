package app.selene.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.selene.data.db.SeleneDatabase

/** In-memory Room database (plain SQLite under Robolectric; SQLCipher is covered by instrumented tests). */
fun inMemoryDatabase(): SeleneDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SeleneDatabase::class.java)
        .allowMainThreadQueries()
        .build()
