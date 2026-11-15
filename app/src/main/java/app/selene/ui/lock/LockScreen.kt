package app.selene.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.selene.R
import app.selene.ui.components.PrimaryButton
import app.selene.ui.components.SeleneIcons
import app.selene.ui.theme.SeleneColors

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_prompt_title)
    fun prompt() {
        val activity = context.findFragmentActivity() ?: return
        // No method enrolled any more (e.g. PIN removed): do not lock the user out of her own data.
        if (!Biometric.isAvailable(context)) onUnlocked() else Biometric.authenticate(activity, title, onUnlocked)
    }
    LaunchedEffect(Unit) { prompt() }
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(SeleneIcons.Lock, contentDescription = null, tint = SeleneColors.Period, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall, color = SeleneColors.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        PrimaryButton(stringResource(R.string.lock_unlock), onClick = ::prompt)
    }
}
