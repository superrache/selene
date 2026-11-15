package app.selene.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens
import app.selene.ui.theme.SeleneShapes

@Composable
fun SeleneCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = SeleneShapes.Card, color = SeleneColors.Surface, content = inner)
    } else {
        Surface(modifier = modifier, shape = SeleneShapes.Card, color = SeleneColors.Surface, content = inner)
    }
}

/** Main action: 56 dp pill. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(SeleneDimens.PrimaryButtonHeight),
        shape = SeleneShapes.Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = SeleneColors.Period,
            contentColor = SeleneColors.OnPeriod,
            disabledContainerColor = SeleneColors.SurfaceHigh,
            disabledContentColor = SeleneColors.TextSecondary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = SeleneColors.TextPrimary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextSecondary)
    }
}

/** Screen title row, with optional back/close button and actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: ImageVector? = null,
    navigationLabel: String? = null,
    onNavigation: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(horizontal = if (navigationIcon != null) 4.dp else SeleneDimens.ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null && onNavigation != null) {
            IconButton(onClick = onNavigation) {
                Icon(navigationIcon, contentDescription = navigationLabel, tint = SeleneColors.TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = SeleneColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SeleneColors.TextSecondary)
            }
        }
        actions()
    }
}

/** Small row of "Modifier" / "Supprimer" text buttons. */
@Composable
fun EditDeleteRow(
    editLabel: String,
    deleteLabel: String,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    editDescription: String,
    deleteDescription: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = SeleneDimens.MinTouchTarget).semantics { contentDescription = editDescription }) {
            Icon(SeleneIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = SeleneColors.TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(editLabel, color = SeleneColors.TextPrimary, maxLines = 1)
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = SeleneDimens.MinTouchTarget).semantics { contentDescription = deleteDescription }) {
                Icon(SeleneIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = SeleneColors.Period)
                Spacer(Modifier.width(6.dp))
                Text(deleteLabel, color = SeleneColors.Period, maxLines = 1)
            }
        }
    }
}

/** Coloured round badge holding an icon. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, background: Color = SeleneColors.SurfaceHigh) {
    Surface(shape = SeleneShapes.Round, color = background, modifier = modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun BorderChoice(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        selected = selected,
        shape = SeleneShapes.Choice,
        color = if (selected) SeleneColors.Period.copy(alpha = 0.16f) else SeleneColors.SurfaceHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SeleneColors.Period else SeleneColors.Border),
        modifier = modifier.heightIn(min = SeleneDimens.MinTouchTarget),
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}
