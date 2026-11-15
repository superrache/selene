package app.selene.ui.entry

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selene.R
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.ui.DateFormats
import app.selene.ui.components.BorderChoice
import app.selene.ui.components.FlowPicker
import app.selene.ui.components.LevelPicker
import app.selene.ui.components.MoodFace
import app.selene.ui.components.MoodPicker
import app.selene.ui.components.PrimaryButton
import app.selene.ui.components.ScreenHeader
import app.selene.ui.components.SectionTitle
import app.selene.ui.components.SeleneCard
import app.selene.ui.components.SeleneIcons
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun EntryScreen(viewModel: EntryViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    EntryContent(
        state = state,
        onClose = onClose,
        onDate = viewModel::setDate,
        onKind = viewModel::setKind,
        onFlow = viewModel::setFlow,
        onPain = viewModel::setPain,
        onFatigue = viewModel::setFatigue,
        onMood = viewModel::setMood,
        onTitle = viewModel::setTitle,
        onSave = viewModel::save,
    )
}

@Composable
fun EntryContent(
    state: EntryUiState,
    onClose: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onKind: (EntryKind) -> Unit,
    onFlow: (Int) -> Unit,
    onPain: (Int) -> Unit,
    onFatigue: (Int) -> Unit,
    onMood: (Mood) -> Unit,
    onTitle: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ScreenHeader(
            title = stringResource(if (state.isEdit) R.string.entry_title_edit else R.string.entry_title_add),
            navigationIcon = SeleneIcons.Close,
            navigationLabel = stringResource(R.string.action_close),
            onNavigation = onClose,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SeleneDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DateRow(state, onClick = { showDatePicker = true })
            if (!state.isEdit) KindSelector(state.kind, onKind)
            when (state.kind) {
                EntryKind.PERIOD -> PeriodFields(state, onFlow, onPain, onFatigue, onMood)
                EntryKind.MOOD -> Column {
                    SectionTitle(stringResource(R.string.mood_label))
                    Spacer(Modifier.height(8.dp))
                    MoodPicker(state.mood, onMood)
                }
                EntryKind.EVENT -> EventFields(state.title, onTitle)
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = SeleneDimens.ScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hint = when {
                state.kind == EntryKind.PERIOD && state.mood == null -> stringResource(R.string.entry_need_mood)
                state.kind == EntryKind.EVENT && !state.titleValid -> stringResource(R.string.entry_need_title)
                state.error -> stringResource(R.string.entry_error)
                state.replacesExisting -> stringResource(R.string.entry_replaces_existing)
                else -> null
            }
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextSecondary)
            }
            PrimaryButton(stringResource(R.string.action_save), onClick = onSave, enabled = state.canSave)
        }
    }
    if (showDatePicker) {
        EntryDatePicker(
            selected = state.date,
            today = state.today,
            onDismiss = { showDatePicker = false },
            onPick = {
                showDatePicker = false
                onDate(it)
            },
        )
    }
}

@Composable
private fun DateRow(state: EntryUiState, onClick: () -> Unit) {
    val label = when (state.date) {
        state.today -> stringResource(R.string.today) + " · " + DateFormats.weekdayDayMonth(state.date)
        state.today.minusDays(1) -> stringResource(R.string.yesterday) + " · " + DateFormats.weekdayDayMonth(state.date)
        else -> DateFormats.full(state.date)
    }
    val description = stringResource(R.string.entry_date) + " : " + label
    SeleneCard(
        onClick = if (state.isEdit) null else onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SeleneIcons.Calendar, contentDescription = null, tint = SeleneColors.TextSecondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.entry_date), style = MaterialTheme.typography.labelMedium, color = SeleneColors.TextSecondary)
                Text(label, style = MaterialTheme.typography.titleMedium, color = SeleneColors.TextPrimary)
            }
            if (!state.isEdit) Icon(SeleneIcons.ChevronRight, contentDescription = null, tint = SeleneColors.TextSecondary)
        }
    }
}

@Composable
private fun KindSelector(kind: EntryKind, onKind: (EntryKind) -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.entry_type))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindChoice(EntryKind.PERIOD, kind, stringResource(R.string.entry_type_period), SeleneIcons.Drop, onKind, Modifier.weight(1f))
            KindChoice(EntryKind.MOOD, kind, stringResource(R.string.entry_type_mood), null, onKind, Modifier.weight(1f))
            KindChoice(EntryKind.EVENT, kind, stringResource(R.string.entry_type_event), SeleneIcons.Bolt, onKind, Modifier.weight(1f))
        }
    }
}

@Composable
private fun KindChoice(
    value: EntryKind,
    current: EntryKind,
    label: String,
    icon: ImageVector?,
    onKind: (EntryKind) -> Unit,
    modifier: Modifier,
) {
    val isSelected = value == current
    BorderChoice(
        selected = isSelected,
        onClick = { onKind(value) },
        modifier = modifier.semantics(mergeDescendants = true) {
            role = Role.Tab
            selected = isSelected
        },
    ) {
        val tint = when (value) {
            EntryKind.PERIOD -> SeleneColors.Period
            EntryKind.EVENT -> SeleneColors.Event
            EntryKind.MOOD -> SeleneColors.TextPrimary
        }
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        else MoodFace(Mood.GOOD, color = tint, size = 24.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = SeleneColors.TextPrimary)
    }
}

@Composable
private fun PeriodFields(
    state: EntryUiState,
    onFlow: (Int) -> Unit,
    onPain: (Int) -> Unit,
    onFatigue: (Int) -> Unit,
    onMood: (Mood) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            SectionTitle(stringResource(R.string.flow_label))
            Spacer(Modifier.height(8.dp))
            FlowPicker(state.flow, onFlow)
        }
        LevelPicker(stringResource(R.string.pain_label), state.pain, onPain)
        LevelPicker(stringResource(R.string.fatigue_label), state.fatigue, onFatigue)
        Column {
            SectionTitle(stringResource(R.string.mood_required))
            Spacer(Modifier.height(8.dp))
            MoodPicker(state.mood, onMood)
        }
    }
}

@Composable
private fun EventFields(title: String, onTitle: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SeleneIcons.Bolt, contentDescription = null, tint = SeleneColors.Event, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            SectionTitle(stringResource(R.string.entry_event_title))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = onTitle,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.entry_event_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = {
                Text(stringResource(R.string.entry_event_counter, title.length, NotableEvent.TITLE_MAX_LENGTH))
            },
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SeleneColors.Period,
                unfocusedBorderColor = SeleneColors.Border,
                cursorColor = SeleneColors.Period,
            ),
        )
    }
}

@SuppressLint("AppBundleLocaleChanges") // French-only app: only the picker's locale is changed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePicker(selected: LocalDate, today: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val todayMillis = today.toUtcMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcMillis(),
        yearRange = (today.year - 10)..today.year,
        selectableDates = remember(todayMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            }
        },
    )
    // The app is French only: show the picker (month names, first day of week) in French.
    val french = Configuration(LocalConfiguration.current).apply { setLocale(Locale.FRENCH) }
    CompositionLocalProvider(LocalConfiguration provides french) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { pickerState.selectedDateMillis?.let { onPick(it.toLocalDate()) } ?: onDismiss() },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            colors = DatePickerDefaults.colors(containerColor = SeleneColors.Surface),
        ) {
            DatePicker(
                state = pickerState,
                showModeToggle = false,
                title = {
                    Text(
                        stringResource(R.string.entry_choose_date),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                headline = {
                    Text(
                        pickerState.selectedDateMillis?.let { DateFormats.dayMonthYear(it.toLocalDate()) } ?: "",
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = SeleneColors.Surface,
                    selectedDayContainerColor = SeleneColors.Period,
                    selectedDayContentColor = SeleneColors.OnPeriod,
                    todayDateBorderColor = SeleneColors.TextPrimary,
                    todayContentColor = SeleneColors.TextPrimary,
                ),
            )
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
