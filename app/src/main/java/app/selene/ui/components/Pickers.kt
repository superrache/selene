package app.selene.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.selene.R
import app.selene.domain.model.Mood
import app.selene.domain.model.PeriodLog
import app.selene.ui.flowLabelRes
import app.selene.ui.labelRes
import app.selene.ui.levelLabelRes
import app.selene.ui.theme.SeleneColors

/** Drawn mood faces, several can be selected (rows of [perRow]). */
@Composable
fun MoodPicker(selected: Set<Mood>, onToggle: (Mood) -> Unit, modifier: Modifier = Modifier, perRow: Int = 3) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mood.entries.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { mood ->
                    val isSelected = mood in selected
                    val label = stringResource(mood.labelRes())
                    BorderChoice(
                        selected = isSelected,
                        onClick = { onToggle(mood) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {
                                contentDescription = label
                                role = Role.Checkbox
                                toggleableState = ToggleableState(isSelected)
                            },
                    ) {
                        MoodFace(mood, color = if (isSelected) SeleneColors.Period else SeleneColors.TextPrimary, size = 34.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) SeleneColors.TextPrimary else SeleneColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Flow: four levels, shown with 1 to 4 drops. */
@Composable
fun FlowPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PeriodLog.FLOW_RANGE.forEach { level ->
            val isSelected = level == selected
            val label = stringResource(flowLabelRes(level))
            BorderChoice(
                selected = isSelected,
                onClick = { onSelect(level) },
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        role = Role.RadioButton
                        this.selected = isSelected
                    },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    repeat(level) {
                        Icon(SeleneIcons.Drop, contentDescription = null, tint = SeleneColors.Period, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) SeleneColors.TextPrimary else SeleneColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Level 0–4 (pain). */
@Composable
fun LevelPicker(title: String, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    OptionalLevelPicker(title, selected, { level -> level?.let(onSelect) }, modifier, clearable = false)
}

/**
 * Level 0–4 that may stay unset (null): touching the selected level again clears it
 * when [clearable]. [icon] is drawn before the title.
 */
@Composable
fun OptionalLevelPicker(
    title: String,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color = SeleneColors.TextSecondary,
) {
    val selectedLabel = selected?.let { stringResource(levelLabelRes(it)) } ?: stringResource(R.string.level_not_noted)
    Column(modifier) {
        if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                SectionTitle(title, Modifier.weight(1f), trailing = selectedLabel)
            }
        } else {
            SectionTitle(title, trailing = selectedLabel)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodLog.LEVEL_RANGE.forEach { level ->
                val isSelected = level == selected
                val description = stringResource(R.string.level_value, level, stringResource(levelLabelRes(level)))
                BorderChoice(
                    selected = isSelected,
                    onClick = { onSelect(if (clearable && isSelected) null else level) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$title, $description"
                            stateDescription = description
                            role = Role.RadioButton
                            this.selected = isSelected
                        },
                ) {
                    Text(
                        level.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) SeleneColors.TextPrimary else SeleneColors.TextSecondary,
                    )
                }
            }
        }
    }
}
