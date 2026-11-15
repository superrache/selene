package app.selene.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.selene.ui.theme.SeleneColors
import app.selene.ui.theme.SeleneDimens

data class BottomTab(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

/** Four tabs with a big round "+" button in the middle. */
@Composable
fun SeleneBottomBar(
    tabs: List<BottomTab>,
    addLabel: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(tabs.size == 4)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(SeleneColors.BottomBar)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            HorizontalDivider(color = SeleneColors.Divider)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.take(2).forEach { TabItem(it, Modifier.weight(1f)) }
                Spacer(Modifier.width(SeleneDimens.AddButtonSize + 16.dp))
                tabs.drop(2).forEach { TabItem(it, Modifier.weight(1f)) }
            }
        }
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .offset(y = (-26).dp),
        ) {
            AddButton(addLabel, onAdd)
        }
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = SeleneColors.Period,
        contentColor = SeleneColors.OnPeriod,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(SeleneDimens.AddButtonSize)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(SeleneIcons.Add, contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun TabItem(tab: BottomTab, modifier: Modifier) {
    val color = if (tab.selected) SeleneColors.TextPrimary else SeleneColors.TextSecondary
    Column(
        modifier
            .height(SeleneDimens.MinTouchTarget + 12.dp)
            .selectable(selected = tab.selected, onClick = tab.onClick, role = Role.Tab)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = if (tab.selected) SeleneColors.Period else color,
            modifier = Modifier.size(24.dp),
        )
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
