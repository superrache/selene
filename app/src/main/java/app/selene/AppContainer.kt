package app.selene

import android.content.Context
import app.selene.data.LogRepository
import app.selene.data.SettingsRepository
import app.selene.data.calendar.CalendarSyncCoordinator
import app.selene.data.calendar.PeriodCalendarSync
import app.selene.data.db.DatabaseKeyStore
import app.selene.data.db.SeleneDatabase
import app.selene.domain.Predictor
import app.selene.domain.model.Prediction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Manual dependency injection: one instance per process, created in [SeleneApp]. */
open class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /** Process-wide scope for work that must outlive a screen (calendar sync). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _today = MutableStateFlow(LocalDate.now())

    /** "Today", refreshed when the app comes back to the foreground. */
    val today: StateFlow<LocalDate> = _today

    fun refreshToday() {
        _today.value = LocalDate.now()
    }

    val keyStore by lazy { DatabaseKeyStore(appContext) }

    open val database: SeleneDatabase by lazy {
        SeleneDatabase.openEncrypted(appContext, keyStore.getOrCreateKey())
    }

    val logRepository by lazy { LogRepository(database) }
    val settingsRepository by lazy { SettingsRepository(database.settingsDao()) }

    val calendarSync by lazy { PeriodCalendarSync(appContext) }

    val calendarCoordinator by lazy {
        CalendarSyncCoordinator(
            sync = calendarSync,
            settings = settingsRepository,
            prediction = prediction,
            defaultTitle = appContext.getString(R.string.calendar_default_event_title),
            scope = appScope,
        )
    }

    val appLock by lazy { AppLock(settingsRepository, appScope) }

    /** Forces the database open (key decryption + SQLCipher check). Call off the main thread. */
    fun openDatabase() {
        database.openHelper.writableDatabase
        isDatabaseReady = true
    }

    @Volatile
    var isDatabaseReady: Boolean = false
        private set

    /** Last resort when the key is lost: deletes the unreadable database and its key. */
    fun resetUnreadableDatabase() {
        appContext.deleteDatabase(SeleneDatabase.FILE_NAME)
        keyStore.destroy()
    }

    /** Predictions are never stored: recomputed whenever the data or the date changes. */
    val prediction: StateFlow<Prediction?> by lazy {
        combine(logRepository.days, today) { days, day -> Predictor.predict(days, day) }
            .flowOn(Dispatchers.Default)
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }
}
