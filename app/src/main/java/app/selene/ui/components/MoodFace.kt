package app.selene.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.selene.domain.model.Mood

/**
 * Vector mood faces, drawn rather than emoji so they look the same on every device (some of
 * the matching emoji, such as 🥺, do not exist on Android 8). Each face echoes an emoji:
 * 😁 🙂 😐 😒 😔 😫 🥺 😭 😰. Purely decorative: callers set the content description.
 */
@Composable
fun MoodFace(mood: Mood, color: Color, modifier: Modifier = Modifier, size: Dp = 32.dp, filled: Color = Color.Transparent) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val face = FacePen(this, s, color)
        val c = Offset(s / 2, s / 2)
        val r = s / 2 - face.stroke / 2
        if (filled != Color.Transparent) drawCircle(filled, r, c, style = Fill)
        drawCircle(color, r, c, style = Stroke(face.stroke))
        when (mood) {
            Mood.JOYFUL -> {
                face.happyEyes()
                face.grin()
            }
            Mood.GOOD -> {
                face.dotEyes()
                face.mouth(curve = 0.10f)
            }
            Mood.NEUTRAL -> {
                face.dotEyes()
                face.mouth(curve = 0f)
            }
            Mood.ANNOYED -> {
                face.halfClosedEyes()
                face.mouth(curve = -0.05f, y = 0.69f, half = 0.14f, dx = 0.06f)
            }
            Mood.SAD -> {
                face.closedEyes(down = true)
                face.worriedBrows(tilt = 0.04f)
                face.mouth(curve = -0.08f, half = 0.14f)
            }
            Mood.DRAINED -> {
                face.squeezedEyes()
                face.openMouth(width = 0.30f, height = 0.14f, y = 0.70f)
            }
            Mood.FRAGILE -> {
                face.bigEyes()
                face.worriedBrows(tilt = 0.06f)
                face.mouth(curve = -0.06f, y = 0.72f, half = 0.10f)
            }
            Mood.CRYING -> {
                face.closedEyes(down = true)
                face.tears()
                face.openMouth(width = 0.30f, height = 0.16f, y = 0.69f)
            }
            Mood.ANXIOUS -> {
                face.dotEyes()
                face.worriedBrows(tilt = 0.05f)
                face.openMouth(width = 0.16f, height = 0.13f, y = 0.70f)
                face.sweatDrop()
            }
        }
    }
}

/** Drawing helpers, all expressed as fractions of the face size [s]. */
private class FacePen(private val scope: DrawScope, private val s: Float, private val color: Color) {
    val stroke = s * 0.075f
    private val cx = s / 2
    private val eyeY = s * 0.40f
    private val eyeDx = s * 0.17f

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = stroke * 0.85f) =
        scope.drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), width, StrokeCap.Round)

    private fun eyesX() = listOf(cx - eyeDx, cx + eyeDx)

    fun dotEyes() = eyesX().forEach { scope.drawCircle(color, s * 0.055f, Offset(it, eyeY)) }

    fun bigEyes() = eyesX().forEach {
        scope.drawCircle(color, s * 0.085f, Offset(it, eyeY + s * 0.02f), style = Stroke(stroke * 0.7f))
        scope.drawCircle(color, s * 0.045f, Offset(it, eyeY + s * 0.03f))
    }

    /** ^ ^ */
    fun happyEyes() = eyesX().forEach {
        val path = Path().apply {
            moveTo(it - s * 0.07f, eyeY + s * 0.03f)
            quadraticTo(it, eyeY - s * 0.07f, it + s * 0.07f, eyeY + s * 0.03f)
        }
        scope.drawPath(path, color, style = Stroke(stroke * 0.85f, cap = StrokeCap.Round))
    }

    /** Lowered arcs (pensive / crying). */
    fun closedEyes(down: Boolean) = eyesX().forEach {
        val bend = if (down) s * 0.06f else -s * 0.06f
        val path = Path().apply {
            moveTo(it - s * 0.07f, eyeY)
            quadraticTo(it, eyeY + bend, it + s * 0.07f, eyeY)
        }
        scope.drawPath(path, color, style = Stroke(stroke * 0.85f, cap = StrokeCap.Round))
    }

    /** Flat lids with the pupil looking aside (😒). */
    fun halfClosedEyes() = eyesX().forEach {
        scope.drawLine(color, Offset(it - s * 0.08f, eyeY - s * 0.02f), Offset(it + s * 0.08f, eyeY - s * 0.02f), stroke * 0.85f, StrokeCap.Round)
        scope.drawArc(
            color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(it - s * 0.01f, eyeY - s * 0.07f),
            size = Size(s * 0.09f, s * 0.10f),
        )
    }

    /** > < */
    fun squeezedEyes() {
        val (l, r) = eyesX().map { it / s }
        val y = eyeY / s
        line(l - 0.07f, y - 0.06f, l + 0.05f, y)
        line(l + 0.05f, y, l - 0.07f, y + 0.06f)
        line(r + 0.07f, y - 0.06f, r - 0.05f, y)
        line(r - 0.05f, y, r + 0.07f, y + 0.06f)
    }

    /** Inner ends raised. */
    fun worriedBrows(tilt: Float) {
        val (l, r) = eyesX().map { it / s }
        val y = 0.25f
        line(l - 0.10f, y + tilt, l + 0.08f, y - tilt)
        line(r - 0.08f, y - tilt, r + 0.10f, y + tilt)
    }

    /** Quadratic mouth: positive [curve] smiles, negative frowns. */
    fun mouth(curve: Float, y: Float = 0.66f, half: Float = 0.20f, dx: Float = 0f) {
        val my = y * s
        val h = half * s
        val x = cx + dx * s
        val c = curve * s
        val path = Path().apply {
            moveTo(x - h, my - c / 2)
            quadraticTo(x, my + c * 1.2f, x + h, my - c / 2)
        }
        scope.drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }

    /** Wide closed "D" smile, filled (😁). */
    fun grin() {
        val my = 0.60f * s
        val h = 0.22f * s
        val path = Path().apply {
            moveTo(cx - h, my)
            lineTo(cx + h, my)
            quadraticTo(cx + h, my + 0.24f * s, cx, my + 0.20f * s)
            quadraticTo(cx - h, my + 0.24f * s, cx - h, my)
            close()
        }
        scope.drawPath(path, color, style = Fill)
        scope.drawPath(path, color, style = Stroke(stroke * 0.6f, cap = StrokeCap.Round))
    }

    fun openMouth(width: Float, height: Float, y: Float) {
        val w = width * s
        val h = height * s
        val rect = Rect(Offset(cx - w / 2, y * s - h / 2), Size(w, h))
        scope.drawOval(color, rect.topLeft, rect.size, style = Fill)
    }

    fun tears() = eyesX().forEach {
        scope.drawLine(color, Offset(it, eyeY + s * 0.07f), Offset(it, s * 0.78f), stroke * 0.7f, StrokeCap.Round)
    }

    fun sweatDrop() {
        val x = 0.80f * s
        val top = 0.14f * s
        val path = Path().apply {
            moveTo(x, top)
            quadraticTo(x + 0.08f * s, top + 0.12f * s, x, top + 0.15f * s)
            quadraticTo(x - 0.08f * s, top + 0.12f * s, x, top)
            close()
        }
        scope.drawPath(path, color, style = Fill)
    }
}
