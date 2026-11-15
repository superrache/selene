package app.selene.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selene.domain.model.Mood

/**
 * Vector mood face, drawn rather than an emoji so it looks the same on every device.
 * The mouth curve goes from a deep frown (1) to a wide open smile (5); the eyebrows of
 * the two lowest moods tilt. Purely decorative: callers set the content description.
 */
@Composable
fun MoodFace(mood: Mood, color: Color, modifier: Modifier = Modifier, size: Dp = 32.dp, filled: Color = Color.Transparent) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.075f
        val c = Offset(s / 2, s / 2)
        val r = s / 2 - stroke / 2
        if (filled != Color.Transparent) drawCircle(filled, r, c, style = Fill)
        drawCircle(color, r, c, style = Stroke(stroke))

        // eyes
        val eyeY = s * 0.40f
        val eyeDx = s * 0.17f
        val eyeR = s * 0.055f
        drawCircle(color, eyeR, Offset(c.x - eyeDx, eyeY))
        drawCircle(color, eyeR, Offset(c.x + eyeDx, eyeY))

        // eyebrows for low moods
        if (mood == Mood.VERY_BAD || mood == Mood.BAD) {
            val tilt = if (mood == Mood.VERY_BAD) s * 0.07f else s * 0.04f
            val browY = s * 0.27f
            val w = s * 0.11f
            // Sad brows: inner ends raised.
            drawLine(color, Offset(c.x - eyeDx - w, browY + tilt), Offset(c.x - eyeDx + w, browY - tilt), stroke * 0.8f, StrokeCap.Round)
            drawLine(color, Offset(c.x + eyeDx - w, browY - tilt), Offset(c.x + eyeDx + w, browY + tilt), stroke * 0.8f, StrokeCap.Round)
        }

        // mouth
        val mouthY = s * 0.66f
        val half = s * 0.2f
        val curve = when (mood) {
            Mood.VERY_BAD -> -0.16f
            Mood.BAD -> -0.08f
            Mood.NEUTRAL -> 0f
            Mood.GOOD -> 0.10f
            Mood.VERY_GOOD -> 0.16f
        } * s
        val path = Path().apply {
            moveTo(c.x - half, mouthY - curve / 2)
            quadraticTo(c.x, mouthY + curve * 1.2f, c.x + half, mouthY - curve / 2)
        }
        if (mood == Mood.VERY_GOOD) {
            path.close()
            drawPath(path, color, style = Fill)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
