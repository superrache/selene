package app.selene.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.selene.R

// Fonts are bundled in res/font (variable TTF). Never use downloadable fonts: they need the network.

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SpaceGrotesk = FontFamily(
    variableFont(R.font.space_grotesk, 400),
    variableFont(R.font.space_grotesk, 500),
    variableFont(R.font.space_grotesk, 600),
    variableFont(R.font.space_grotesk, 700),
)

val Inter = FontFamily(
    variableFont(R.font.inter, 400),
    variableFont(R.font.inter, 500),
    variableFont(R.font.inter, 600),
    variableFont(R.font.inter, 700),
)

private fun display(size: Int, weight: FontWeight, line: Int) =
    TextStyle(fontFamily = SpaceGrotesk, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun body(size: Int, weight: FontWeight, line: Int) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val SeleneTypography = Typography(
    displayLarge = display(48, FontWeight.Bold, 52),
    displayMedium = display(36, FontWeight.Bold, 40),
    displaySmall = display(30, FontWeight.SemiBold, 36),
    headlineLarge = display(28, FontWeight.SemiBold, 34),
    headlineMedium = display(24, FontWeight.SemiBold, 30),
    headlineSmall = display(20, FontWeight.SemiBold, 26),
    titleLarge = display(20, FontWeight.SemiBold, 26),
    titleMedium = display(16, FontWeight.SemiBold, 22),
    titleSmall = body(14, FontWeight.SemiBold, 20),
    bodyLarge = body(16, FontWeight.Normal, 24),
    bodyMedium = body(14, FontWeight.Normal, 20),
    bodySmall = body(12, FontWeight.Normal, 16),
    labelLarge = body(14, FontWeight.SemiBold, 20),
    labelMedium = body(12, FontWeight.Medium, 16),
    labelSmall = body(11, FontWeight.Medium, 16),
)
