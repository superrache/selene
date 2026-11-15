package app.selene.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object SeleneShapes {
    val Card = RoundedCornerShape(18.dp)
    val Choice = RoundedCornerShape(14.dp)
    val Pill = RoundedCornerShape(28.dp)
    val Round = CircleShape
}

object SeleneDimens {
    val PrimaryButtonHeight = 56.dp
    val AddButtonSize = 68.dp
    val MinTouchTarget = 48.dp
    val ScreenPadding = 20.dp

    /** Extra space at the end of tab screens so the raised "+" button never hides content. */
    val BottomBarClearance = 48.dp
}

val SeleneMaterialShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = SeleneShapes.Choice,
    large = SeleneShapes.Card,
    extraLarge = RoundedCornerShape(28.dp),
)
