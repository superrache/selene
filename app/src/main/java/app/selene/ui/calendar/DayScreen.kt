package app.selene.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selene.R
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import app.selene.ui.DateFormats
import app.selene.ui.components.EditDeleteRow
import app.selene.ui.components.IconBadge
import app.selene.ui.components.MoodFace
import app.selene.ui.components.PrimaryButton
import app.selene.ui.components.ScreenHeader
import app.selene.ui.components.SeleneCard
import app.selene.ui.components.SeleneIcons
import app.selene.ui.entry.EntryKind
import app.selene.ui.flowLabelRes
import app.selene.ui.labelRes
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens

@Composable
fun DayScreen(
    viewModel: DayViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (EntryKind, String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.deletions.collect { deletion ->
            val result = snackbar.showSnackbar(
                message = resources.getString(deletion.message),
                actionLabel = undoLabel,
                withDismissAction = false,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(deletion)
        }
    }
    var confirmPeriodDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = SeleneColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ScreenHeader(
                title = DateFormats.full(state.date),
                navigationIcon = SeleneIcons.Back,
                navigationLabel = stringResource(R.string.action_back),
                onNavigation = onBack,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SeleneDimens.ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PredictionBadges(state)
            val day = state.day
            day.period?.let { PeriodItem(it, onEdit = { onEdit(EntryKind.PERIOD, null) }, onDelete = { confirmPeriodDelete = true }) }
            day.mood?.let { mood ->
                MoodItem(
                    mood = mood,
                    locked = day.period != null,
                    onEdit = { onEdit(EntryKind.MOOD, null) },
                    onDelete = { viewModel.deleteMood() },
                )
            }
            day.events.forEach { event ->
                EventItem(event, onEdit = { onEdit(EntryKind.EVENT, event.id) }, onDelete = { viewModel.deleteEvent(event.id) })
            }
            if (state.loaded && day.isEmpty) {
                Text(
                    stringResource(if (state.isFuture) R.string.day_future_info else R.string.day_nothing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SeleneColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!state.isFuture) PrimaryButton(stringResource(R.string.day_add), onClick = onAdd)
        }
    }

    if (confirmPeriodDelete) {
        DeletePeriodDialog(
            onDismiss = { confirmPeriodDelete = false },
            onConfirm = { alsoMood ->
                confirmPeriodDelete = false
                viewModel.deletePeriod(alsoMood)
            },
        )
    }
}

@Composable
private fun PredictionBadges(state: DayUiState) {
    val labels = buildList {
        if (state.predictedPeriod) add(stringResource(R.string.day_predicted_period) to SeleneColors.Period)
        if (state.ovulation) add(stringResource(R.string.day_ovulation) to SeleneColors.Fertile)
        else if (state.fertile) add(stringResource(R.string.day_fertile) to SeleneColors.Fertile)
    }
    if (labels.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { (label, color) ->
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PeriodItem(period: PeriodLog, onEdit: () -> Unit, onDelete: () -> Unit) {
    val flow = stringResource(flowLabelRes(period.flow)).lowercase()
    val title = stringResource(R.string.day_period_title, flow)
    ItemCard(
        badge = { IconBadge(SeleneIcons.Drop, SeleneColors.Period) },
        title = title,
        subtitle = stringResource(R.string.day_period_detail, period.pain, period.fatigue),
        actions = {
            EditDeleteRow(
                editLabel = stringResource(R.string.action_edit),
                deleteLabel = stringResource(R.string.action_delete),
                onEdit = onEdit,
                onDelete = onDelete,
                editDescription = stringResource(R.string.action_edit) + " : " + title,
                deleteDescription = stringResource(R.string.action_delete) + " : " + title,
            )
        },
    )
}

@Composable
private fun MoodItem(mood: Mood, locked: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val title = stringResource(R.string.day_mood_title, stringResource(mood.labelRes()).lowercase())
    ItemCard(
        badge = { MoodFace(mood, color = SeleneColors.TextPrimary, size = 40.dp) },
        title = title,
        subtitle = if (locked) stringResource(R.string.day_mood_locked) else null,
        actions = {
            EditDeleteRow(
                editLabel = stringResource(R.string.action_edit),
                deleteLabel = stringResource(R.string.action_delete),
                onEdit = onEdit,
                onDelete = if (locked) null else onDelete,
                editDescription = stringResource(R.string.action_edit) + " : " + title,
                deleteDescription = stringResource(R.string.action_delete) + " : " + title,
            )
        },
    )
}

@Composable
private fun EventItem(event: NotableEvent, onEdit: () -> Unit, onDelete: () -> Unit) {
    ItemCard(
        badge = { IconBadge(SeleneIcons.Bolt, SeleneColors.Event) },
        title = event.title,
        subtitle = null,
        actions = {
            EditDeleteRow(
                editLabel = stringResource(R.string.action_edit),
                deleteLabel = stringResource(R.string.action_delete),
                onEdit = onEdit,
                onDelete = onDelete,
                editDescription = stringResource(R.string.action_edit) + " : " + event.title,
                deleteDescription = stringResource(R.string.action_delete) + " : " + event.title,
            )
        },
    )
}

@Composable
private fun ItemCard(
    badge: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    actions: @Composable () -> Unit,
) {
    SeleneCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            badge()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = SeleneColors.TextPrimary)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextSecondary)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
    }
}

@Composable
private fun DeletePeriodDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var alsoMood by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SeleneColors.Surface,
        title = { Text(stringResource(R.string.day_delete_period_title)) },
        text = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = alsoMood, role = Role.Checkbox, onValueChange = { alsoMood = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = alsoMood,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = SeleneColors.Period, checkmarkColor = SeleneColors.OnPeriod),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.day_delete_period_also_mood), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(alsoMood) }) {
                Text(stringResource(R.string.action_delete), color = SeleneColors.Period)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
