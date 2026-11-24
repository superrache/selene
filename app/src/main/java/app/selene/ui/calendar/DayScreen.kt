package app.selene.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.selene.R
import app.selene.domain.model.Mood
import app.selene.domain.model.NotableEvent
import app.selene.domain.model.PeriodLog
import app.selene.domain.model.Symptom
import app.selene.domain.model.SymptomLog
import app.selene.ui.DateFormats
import app.selene.ui.components.EditDeleteRow
import app.selene.ui.components.IconBadge
import app.selene.ui.components.MoodFace
import app.selene.ui.components.ScreenHeader
import app.selene.ui.components.SectionTitle
import app.selene.ui.components.SeleneCard
import app.selene.ui.components.SeleneIcons
import app.selene.ui.components.SymptomGlyph
import app.selene.ui.theme.SeleneShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.selene.ui.entry.EntryKind
import app.selene.ui.flowLabelRes
import app.selene.ui.labelRes
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens
import java.time.LocalTime

@Composable
fun DayScreen(
    viewModel: DayViewModel,
    onBack: () -> Unit,
    onAdd: (EntryKind) -> Unit,
    onEdit: (EntryKind, String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
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
            state.fullMoon?.let { FullMoonItem(it) }
            val day = state.day
            day.period?.let { PeriodItem(it, onEdit = { onEdit(EntryKind.PERIOD, null) }, onDelete = { viewModel.deletePeriod() }) }
            day.symptoms?.let { symptoms ->
                SymptomsItem(symptoms, onEdit = { onEdit(EntryKind.SYMPTOMS, null) }, onDelete = { viewModel.deleteSymptoms() })
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
            if (state.loaded && !state.isFuture) {
                Spacer(Modifier.height(8.dp))
                AddSection(state, onAdd)
            }
        }
    }
}

/**
 * Direct "add" tiles, one per entry kind still possible on this day (period and symptoms
 * are one per day; events are unlimited). Dashed outline, no filled coral: these must not
 * read as a confirm button.
 */
@Composable
private fun AddSection(state: DayUiState, onAdd: (EntryKind) -> Unit) {
    val date = DateFormats.dayMonth(state.date)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(stringResource(R.string.day_add_section))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.day.period == null) {
                AddTile(
                    label = stringResource(R.string.entry_type_period),
                    description = stringResource(R.string.day_add_period_description, date),
                    onClick = { onAdd(EntryKind.PERIOD) },
                    modifier = Modifier.weight(1f),
                ) { Icon(SeleneIcons.Drop, contentDescription = null, tint = SeleneColors.Period, modifier = Modifier.size(24.dp)) }
            }
            if (state.day.symptoms == null) {
                AddTile(
                    label = stringResource(R.string.entry_type_symptoms),
                    description = stringResource(R.string.day_add_symptoms_description, date),
                    onClick = { onAdd(EntryKind.SYMPTOMS) },
                    modifier = Modifier.weight(1f),
                ) { MoodFace(Mood.GOOD, color = SeleneColors.TextPrimary, size = 24.dp) }
            }
            AddTile(
                label = stringResource(R.string.entry_type_event),
                description = stringResource(R.string.day_add_event_description, date),
                onClick = { onAdd(EntryKind.EVENT) },
                modifier = Modifier.weight(1f),
            ) { Icon(SeleneIcons.Bolt, contentDescription = null, tint = SeleneColors.Event, modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun AddTile(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = 88.dp)
            .clip(SeleneShapes.Choice)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                val radius = 14.dp.toPx()
                drawRoundRect(
                    color = SeleneColors.Border,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(radius - stroke / 2),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SeleneIcons.Add, contentDescription = null, tint = SeleneColors.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            icon()
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = SeleneColors.TextPrimary, textAlign = TextAlign.Center)
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
private fun FullMoonItem(time: LocalTime) {
    SeleneCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.day_full_moon), style = MaterialTheme.typography.titleMedium, color = SeleneColors.TextPrimary)
        Text(
            stringResource(R.string.day_full_moon_time, DateFormats.time(time)),
            style = MaterialTheme.typography.bodyMedium,
            color = SeleneColors.TextSecondary,
        )
    }
}

@Composable
private fun PeriodItem(period: PeriodLog, onEdit: () -> Unit, onDelete: () -> Unit) {
    val flow = stringResource(flowLabelRes(period.flow)).lowercase()
    val title = stringResource(R.string.day_period_title, flow)
    ItemCard(
        badge = { IconBadge(SeleneIcons.Drop, SeleneColors.Period) },
        title = title,
        subtitle = stringResource(R.string.day_period_detail, period.pain),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymptomsItem(symptoms: SymptomLog, onEdit: () -> Unit, onDelete: () -> Unit) {
    val title = stringResource(R.string.symptoms_title)
    val levels = listOfNotNull(
        symptoms.irritability?.let { stringResource(R.string.day_symptoms_level, stringResource(R.string.irritability_label), it) },
        symptoms.fatigue?.let { stringResource(R.string.day_symptoms_level, stringResource(R.string.fatigue_label), it) },
    ).joinToString(" · ").ifEmpty { null }
    ItemCard(
        badge = {
            val first = symptoms.moods.minOrNull()
            if (first != null) MoodFace(first, color = SeleneColors.TextPrimary, size = 40.dp)
            else IconBadge(SeleneIcons.Check, SeleneColors.Fertile)
        },
        title = title,
        subtitle = levels,
        body = {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Mood.entries.filter { it in symptoms.moods }.forEach { mood ->
                    Tag(text = stringResource(mood.labelRes())) {
                        MoodFace(mood, color = SeleneColors.TextPrimary, size = 18.dp)
                    }
                }
                Symptom.entries.filter { it in symptoms.symptoms }.forEach { symptom ->
                    Tag(text = stringResource(symptom.labelRes())) { SymptomGlyph(symptom, size = 14) }
                }
            }
            symptoms.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SeleneColors.TextPrimary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
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
private fun Tag(text: String, icon: @Composable () -> Unit) {
    Row(
        Modifier
            .background(SeleneColors.SurfaceHigh, SeleneShapes.Choice)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = SeleneColors.TextPrimary)
    }
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
    body: @Composable () -> Unit = {},
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
        body()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
    }
}
