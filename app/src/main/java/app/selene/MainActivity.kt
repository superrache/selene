package app.selene

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selene.ui.components.PrimaryButton
import app.selene.ui.lock.LockScreen
import app.selene.ui.navigation.SeleneNavHost
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

// FragmentActivity (not ComponentActivity) because BiometricPrompt needs it.
class MainActivity : FragmentActivity() {
    private val container get() = (application as SeleneApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SeleneTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(SeleneColors.Background),
                ) {
                    SeleneRoot(container, onReset = ::resetAndRestart)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.refreshToday()
        if (container.isDatabaseReady) container.calendarCoordinator.refresh()
    }

    override fun onStop() {
        super.onStop()
        // Rotation is not "leaving the app": don't lock.
        if (!isChangingConfigurations && container.isDatabaseReady) container.appLock.onBackground()
    }

    private fun resetAndRestart() {
        container.resetUnreadableDatabase()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
        exitProcess(0)
    }
}

private enum class DbState { OPENING, READY, FAILED }

@Composable
private fun SeleneRoot(container: AppContainer, onReset: () -> Unit) {
    val dbState by produceState(DbState.OPENING) {
        value = withContext(Dispatchers.IO) {
            runCatching { container.openDatabase() }.fold({ DbState.READY }, { DbState.FAILED })
        }
        if (value == DbState.READY) container.calendarCoordinator.start()
    }
    when (dbState) {
        DbState.OPENING -> Unit
        DbState.FAILED -> DatabaseError(onReset)
        DbState.READY -> {
            val lock by container.appLock.state.collectAsStateWithLifecycle()
            when (lock) {
                AppLock.State.CHECKING -> Unit
                AppLock.State.LOCKED -> LockScreen(onUnlocked = container.appLock::unlock)
                AppLock.State.UNLOCKED -> SeleneNavHost()
            }
        }
    }
}

@Composable
private fun DatabaseError(onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.db_error_title), style = MaterialTheme.typography.headlineSmall, color = SeleneColors.TextPrimary)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.db_error_body), style = MaterialTheme.typography.bodyLarge, color = SeleneColors.TextSecondary)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.db_error_reset), onClick = onReset)
    }
}
