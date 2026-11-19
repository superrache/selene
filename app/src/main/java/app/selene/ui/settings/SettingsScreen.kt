package app.selene.ui.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selene.BuildConfig
import app.selene.R
import app.selene.data.LogRepository
import app.selene.data.calendar.CalendarInfo
import app.selene.data.calendar.PeriodCalendarSync
import app.selene.data.json.ExportCrypto
import app.selene.ui.appContainer
import app.selene.ui.components.ScreenHeader
import app.selene.ui.components.SectionTitle
import app.selene.ui.components.SeleneCard
import app.selene.ui.components.SeleneIcons
import app.selene.ui.lock.Biometric
import app.selene.ui.lock.findFragmentActivity
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens

private enum class Confirm { EXPORT, ERASE_1, ERASE_2, EVENT_TITLE }

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dialogs by viewModel.dialogs.collectAsStateWithLifecycle()
    val calendarName by viewModel.calendarName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val appLock = appContainer().appLock
    val snackbar = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf<Confirm?>(null) }
    var accountCalendar by remember { mutableStateOf<CalendarInfo?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(resources.getString(it)) }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri) else viewModel.cancelExport()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readImport(it, displayName(context, it)) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) viewModel.openCalendarChooser() else viewModel.permissionDenied()
    }

    // Insets are handled by the header and the bottom bar.
    Scaffold(
        containerColor = SeleneColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader(title = stringResource(R.string.settings_title))
            Column(
                Modifier.padding(horizontal = SeleneDimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Data
                SettingsSection(stringResource(R.string.settings_data)) {
                    ActionRow(SeleneIcons.ChevronRight, stringResource(R.string.settings_export), stringResource(R.string.settings_export_summary)) {
                        confirm = Confirm.EXPORT
                    }
                    Divider()
                    ActionRow(SeleneIcons.ChevronRight, stringResource(R.string.settings_import), stringResource(R.string.settings_import_summary)) {
                        appLock.allowExternalScreen()
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }
                }

                // Calendar
                val s = settings
                SettingsSection(stringResource(R.string.settings_calendar)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_calendar_sync),
                        summary = stringResource(R.string.settings_calendar_sync_summary),
                        checked = s?.calendarSyncEnabled == true,
                        enabled = s != null,
                    ) { on ->
                        when {
                            !on -> viewModel.disableCalendar()
                            viewModel.hasCalendarPermission() -> viewModel.openCalendarChooser()
                            else -> permissionLauncher.launch(PeriodCalendarSync.PERMISSIONS)
                        }
                    }
                    if (s?.calendarSyncEnabled == true) {
                        Divider()
                        ActionRow(
                            SeleneIcons.Calendar,
                            stringResource(R.string.settings_calendar_choose),
                            calendarName?.let { stringResource(R.string.settings_calendar_selected, it) },
                        ) { viewModel.openCalendarChooser() }
                        Divider()
                        ActionRow(
                            SeleneIcons.Edit,
                            stringResource(R.string.settings_calendar_event_title),
                            s.calendarEventTitle ?: stringResource(R.string.calendar_default_event_title),
                        ) { confirm = Confirm.EVENT_TITLE }
                    }
                }

                // Security
                SettingsSection(stringResource(R.string.settings_security)) {
                    InfoRow(SeleneIcons.Lock, stringResource(R.string.settings_encryption), stringResource(R.string.settings_encryption_on))
                    Divider()
                    InfoRow(SeleneIcons.Check, stringResource(R.string.settings_offline_title), stringResource(R.string.settings_offline))
                    Divider()
                    val biometricAvailable = remember { Biometric.isAvailable(context) }
                    SwitchRow(
                        title = stringResource(R.string.settings_biometric),
                        summary = stringResource(if (biometricAvailable) R.string.settings_biometric_summary else R.string.settings_biometric_unavailable),
                        checked = s?.biometricLock == true,
                        enabled = s != null && (biometricAvailable || s.biometricLock),
                    ) { on ->
                        if (!on) {
                            viewModel.setBiometricLock(false)
                        } else {
                            context.findFragmentActivity()?.let { activity ->
                                Biometric.authenticate(activity, resources.getString(R.string.lock_prompt_enable), onSuccess = { viewModel.setBiometricLock(true) })
                            }
                        }
                    }
                }

                // Erase
                SeleneCard(Modifier.fillMaxWidth(), onClick = { confirm = Confirm.ERASE_1 }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(SeleneIcons.Delete, contentDescription = null, tint = SeleneColors.Period)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.settings_erase), style = MaterialTheme.typography.titleMedium, color = SeleneColors.Period)
                            Text(stringResource(R.string.settings_erase_summary), style = MaterialTheme.typography.bodySmall, color = SeleneColors.TextSecondary)
                        }
                    }
                }

                // About
                SettingsSection(stringResource(R.string.settings_about)) {
                    InfoRow(null, stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), stringResource(R.string.settings_licenses))
                }
                Spacer(Modifier.height(SeleneDimens.BottomBarClearance))
            }
        }
    }

    // --- Dialogs ---
    when (confirm) {
        Confirm.EXPORT -> ExportPasswordDialog(
            onDismiss = { confirm = null },
            onConfirm = { password ->
                confirm = null
                viewModel.prepareExport(password)
                appLock.allowExternalScreen()
                exportLauncher.launch(viewModel.exportFileName)
            },
        )
        Confirm.ERASE_1 -> ConfirmDialog(
            title = stringResource(R.string.settings_erase_confirm1_title),
            text = stringResource(R.string.settings_erase_confirm1),
            confirmLabel = stringResource(R.string.action_continue),
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = { confirm = Confirm.ERASE_2 },
        )
        Confirm.ERASE_2 -> ConfirmDialog(
            title = stringResource(R.string.settings_erase_confirm2_title),
            text = stringResource(R.string.settings_erase_confirm2),
            confirmLabel = stringResource(R.string.settings_erase_action),
            destructive = true,
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                viewModel.eraseAll()
            },
        )
        Confirm.EVENT_TITLE -> EventTitleDialog(
            initial = settings?.calendarEventTitle ?: stringResource(R.string.calendar_default_event_title),
            onDismiss = { confirm = null },
            onSave = {
                confirm = null
                viewModel.setEventTitle(it)
            },
        )
        null -> Unit
    }

    dialogs.calendars?.let { calendars ->
        CalendarChooserDialog(
            calendars = calendars,
            onDismiss = viewModel::dismissCalendarChooser,
            onChoose = { cal ->
                if (cal != null && !cal.isLocal) {
                    viewModel.dismissCalendarChooser()
                    accountCalendar = cal
                } else {
                    viewModel.chooseCalendar(cal)
                }
            },
        )
    }
    accountCalendar?.let { cal ->
        ConfirmDialog(
            title = stringResource(R.string.settings_calendar_account_warning_title),
            text = stringResource(R.string.settings_calendar_account_warning, cal.accountName.ifEmpty { cal.accountType }),
            confirmLabel = stringResource(R.string.action_continue),
            onDismiss = { accountCalendar = null },
            onConfirm = {
                accountCalendar = null
                viewModel.chooseCalendar(cal)
            },
        )
    }
    dialogs.pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            containerColor = SeleneColors.Surface,
            title = { Text(stringResource(R.string.settings_import_choose_title, pending.fileName)) },
            text = { Text(stringResource(R.string.settings_import_choose)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { viewModel.confirmImport(LogRepository.ImportMode.MERGE) }) {
                        Text(stringResource(R.string.settings_import_merge))
                    }
                    TextButton(onClick = { viewModel.confirmImport(LogRepository.ImportMode.REPLACE_ALL) }) {
                        Text(stringResource(R.string.settings_import_replace), color = SeleneColors.Period)
                    }
                    TextButton(onClick = viewModel::dismissImport) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
    dialogs.pendingDecrypt?.let { pending ->
        DecryptDialog(
            pending = pending,
            onDismiss = viewModel::dismissImport,
            onDecrypt = viewModel::decryptImport,
        )
    }
    if (dialogs.exporting) WorkingDialog(stringResource(R.string.settings_export_working))
    dialogs.importError?.let { failure ->
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            containerColor = SeleneColors.Surface,
            title = { Text(stringResource(R.string.settings_import_error_title)) },
            text = {
                Column {
                    Text(importFailureMessage(failure))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_import_error_footer), color = SeleneColors.TextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissImport) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        SeleneCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = SeleneColors.Divider, modifier = Modifier.padding(horizontal = 16.dp))

@Composable
private fun ActionRow(icon: ImageVector?, title: String, summary: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = SeleneColors.TextPrimary)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = SeleneColors.TextSecondary)
        }
        if (icon != null) Icon(icon, contentDescription = null, tint = SeleneColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoRow(icon: ImageVector?, title: String, summary: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = SeleneColors.Fertile, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = SeleneColors.TextPrimary)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = SeleneColors.TextSecondary)
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) SeleneColors.TextPrimary else SeleneColors.TextSecondary)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, color = SeleneColors.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SeleneColors.OnPeriod,
                checkedTrackColor = SeleneColors.Period,
                uncheckedThumbColor = SeleneColors.TextSecondary,
                uncheckedTrackColor = SeleneColors.SurfaceHigh,
                uncheckedBorderColor = SeleneColors.Border,
            ),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SeleneColors.Surface,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) SeleneColors.Period else SeleneColors.TextPrimary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ExportPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // Deliberately not rememberSaveable: the password must not end up in the saved instance state.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val longEnough = ExportCrypto.isAcceptablePassword(password)
    val matches = password == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SeleneColors.Surface,
        title = { Text(stringResource(R.string.settings_export_password_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_export_password_text))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_export_password),
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    error = if (password.isNotEmpty() && !longEnough) {
                        pluralStringResource(R.plurals.settings_export_password_too_short, ExportCrypto.MIN_PASSWORD_LENGTH, ExportCrypto.MIN_PASSWORD_LENGTH)
                    } else {
                        null
                    },
                )
                PasswordField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = stringResource(R.string.settings_export_password_confirm),
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    error = if (confirmation.isNotEmpty() && !matches) stringResource(R.string.settings_export_password_mismatch) else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = longEnough && matches) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DecryptDialog(pending: PendingDecrypt, onDismiss: () -> Unit, onDecrypt: (String) -> Unit) {
    var password by remember(pending.fileName, pending.text) { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!pending.working) onDismiss() },
        containerColor = SeleneColors.Surface,
        title = { Text(stringResource(R.string.settings_import_password_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_import_password_text, pending.fileName))
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_export_password),
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    error = if (pending.wrongPassword) stringResource(R.string.settings_import_password_wrong) else null,
                    enabled = !pending.working,
                    onDone = { if (password.isNotEmpty()) onDecrypt(password) },
                )
                if (pending.working) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = SeleneColors.Period, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.settings_import_working), color = SeleneColors.TextSecondary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecrypt(password) }, enabled = password.isNotEmpty() && !pending.working) {
                Text(stringResource(R.string.settings_import_decrypt))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pending.working) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    error: String?,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (visible) SeleneIcons.EyeOff else SeleneIcons.Eye,
                    contentDescription = stringResource(if (visible) R.string.password_hide else R.string.password_show),
                    tint = SeleneColors.TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SeleneColors.Period, cursorColor = SeleneColors.Period),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WorkingDialog(text: String) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = SeleneColors.Surface,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = SeleneColors.Period, strokeWidth = 2.dp)
                Spacer(Modifier.width(16.dp))
                Text(text)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun EventTitleDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SeleneColors.Surface,
        title = { Text(stringResource(R.string.settings_calendar_event_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(80) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SeleneColors.Period, cursorColor = SeleneColors.Period),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CalendarChooserDialog(calendars: List<CalendarInfo>, onDismiss: () -> Unit, onChoose: (CalendarInfo?) -> Unit) {
    val hasOwnLocal = calendars.any { it.isLocal && it.accountName == PeriodCalendarSync.LOCAL_ACCOUNT_NAME }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SeleneColors.Surface,
        title = { Text(stringResource(R.string.settings_calendar_choose)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!hasOwnLocal) {
                    ChooserRow(stringResource(R.string.settings_calendar_create_local), stringResource(R.string.settings_calendar_local_badge), true) {
                        onChoose(null)
                    }
                }
                calendars.forEach { cal ->
                    ChooserRow(
                        cal.displayName.ifEmpty { cal.accountName },
                        if (cal.isLocal) stringResource(R.string.settings_calendar_local_badge)
                        else stringResource(R.string.settings_calendar_account_badge, cal.accountName.ifEmpty { cal.accountType }),
                        cal.isLocal,
                    ) { onChoose(cal) }
                }
                if (calendars.isEmpty() && hasOwnLocal) Text(stringResource(R.string.settings_calendar_no_calendar))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ChooserRow(title: String, badge: String, local: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (local) SeleneIcons.Lock else SeleneIcons.Warning,
            contentDescription = null,
            tint = if (local) SeleneColors.Fertile else SeleneColors.Event,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = SeleneColors.TextPrimary)
            Text(badge, style = MaterialTheme.typography.bodySmall, color = SeleneColors.TextSecondary)
        }
    }
}
