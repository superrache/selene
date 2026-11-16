package app.selene.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.selene.domain.model.Symptom
import app.selene.ui.labelRes
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens
import app.selene.ui.theme.SeleneShapes

/**
 * Emoji shown on each symptom chip. Only emoji from Emoji 5.0 or older, so that they exist on
 * Android 8 (minSdk 26). Null: a vector icon is drawn instead (see [SymptomGlyph]).
 */
fun Symptom.emoji(): String? = when (this) {
    Symptom.CRAMPS -> "🌀"
    Symptom.HEADACHE -> "🤕"
    Symptom.MIGRAINE -> "💥"
    Symptom.BACK_PAIN -> "💢"
    Symptom.TENDER_BREASTS -> "👙"
    Symptom.LOW_LIBIDO -> "❄️"
    Symptom.HIGH_LIBIDO -> "🔥"
    Symptom.SEX -> "💞"
    Symptom.OILY_SKIN -> "💦"
    Symptom.PIMPLES -> "🔴"
    Symptom.MOUTH_ULCERS -> "👄"
    Symptom.DRY_SKIN -> "🌵"
    Symptom.CRAVINGS -> "🍫"
    Symptom.LOSS_OF_APPETITE -> "🍽️"
    Symptom.NAUSEA -> "🤢"
    Symptom.VOMITING -> "🤮"
    Symptom.BLOATING -> "🎈"
    Symptom.CONSTIPATION -> "⏳"
    Symptom.DIARRHEA -> "🚽"
    Symptom.SPOTTING -> null
    Symptom.DISCHARGE_DRY -> "🏜️"
    Symptom.DISCHARGE_CREAMY -> "🥛"
    Symptom.DISCHARGE_EGG_WHITE -> "🥚"
    Symptom.DISCHARGE_UNUSUAL -> "⚠️"
    Symptom.INSOMNIA -> "🦉"
    Symptom.RESTLESS_SLEEP -> "🌙"
    Symptom.DROWSINESS -> "😴"
    Symptom.WATER_RETENTION -> "💧"
    Symptom.HOT_FLASHES -> "🌡️"
    Symptom.DIZZINESS -> "💫"
}

/** Emoji, or the coral drop for spotting (no blood-drop emoji before Emoji 12). */
@Composable
fun SymptomGlyph(symptom: Symptom, size: Int = 18) {
    val emoji = symptom.emoji()
    if (emoji != null) Text(emoji, fontSize = size.sp)
    else Icon(SeleneIcons.Drop, contentDescription = null, tint = SeleneColors.Period, modifier = Modifier.size((size + 2).dp))
}

/** A multi-select group of symptom chips that wrap on several lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SymptomChips(symptoms: List<Symptom>, selected: Set<Symptom>, onToggle: (Symptom) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        symptoms.forEach { symptom -> SymptomChip(symptom, symptom in selected, onClick = { onToggle(symptom) }) }
    }
}

@Composable
fun SymptomChip(symptom: Symptom, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(symptom.labelRes())
    Surface(
        onClick = onClick,
        shape = SeleneShapes.Choice,
        color = if (selected) SeleneColors.Period.copy(alpha = 0.16f) else SeleneColors.SurfaceHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SeleneColors.Period else SeleneColors.Border),
        modifier = modifier
            .heightIn(min = SeleneDimens.MinTouchTarget)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Checkbox
                toggleableState = ToggleableState(selected)
            },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SymptomGlyph(symptom)
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) SeleneColors.TextPrimary else SeleneColors.TextSecondary,
            )
        }
    }
}
