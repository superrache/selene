package app.selene.ui

import android.content.Context
import app.selene.AppContainer
import app.selene.data.db.SeleneDatabase
import app.selene.data.inMemoryDatabase

/** Container backed by an in-memory database (no Keystore, no SQLCipher under Robolectric). */
class TestContainer(context: Context) : AppContainer(context) {
    override val database: SeleneDatabase = inMemoryDatabase()
}
