package app.selene.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn 24x24 icons. Kept local so the app needs neither material-icons-extended
 * nor any font/icon download.
 */
object SeleneIcons {
    private fun icon(name: String, strokes: List<String> = emptyList(), fills: List<String> = emptyList(), width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            fills.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
            strokes.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Cycle = icon(
        "cycle",
        strokes = listOf("M12,3A9,9 0 1,1 4.21,7.5"),
        fills = listOf("M12,10a2,2 0 1,1 0,4a2,2 0 1,1 0,-4z"),
    )
    val Calendar = icon(
        "calendar",
        strokes = listOf("M5,5h14a2,2 0 0,1 2,2v12a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2V7a2,2 0 0,1 2,-2z", "M3,10h18", "M8,3v4", "M16,3v4"),
    )
    val Analysis = icon("analysis", strokes = listOf("M5,20V13", "M10,20V6", "M15,20V10", "M20,20V4"), width = 2.5f)
    val Settings = icon(
        "settings",
        fills = listOf(
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
        ),
    )
    val Add = icon("add", strokes = listOf("M12,5v14", "M5,12h14"), width = 2.5f)
    val Bolt = icon("bolt", fills = listOf("M13,2L4,14h7l-1,8 9,-12h-7l1,-8z"))
    val Drop = icon("drop", fills = listOf("M12,2.5C12,2.5 5,10.2 5,15a7,7 0 0,0 14,0C19,10.2 12,2.5 12,2.5z"))
    val Edit = icon(
        "edit",
        fills = listOf(
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
        ),
    )
    val Delete = icon(
        "delete",
        fills = listOf("M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"),
    )
    val Back = icon("back", strokes = listOf("M15,5l-7,7 7,7"), width = 2.2f)
    val ChevronRight = icon("chevron_right", strokes = listOf("M9,5l7,7 -7,7"), width = 2.2f)
    val Eye = icon(
        "eye",
        strokes = listOf("M2,12c2.5,-4.5 6,-7 10,-7s7.5,2.5 10,7c-2.5,4.5 -6,7 -10,7s-7.5,-2.5 -10,-7z", "M12,9a3,3 0 1,0 0.01,0z"),
    )
    val EyeOff = icon(
        "eye_off",
        strokes = listOf("M2,12c2.5,-4.5 6,-7 10,-7s7.5,2.5 10,7c-2.5,4.5 -6,7 -10,7s-7.5,-2.5 -10,-7z", "M12,9a3,3 0 1,0 0.01,0z", "M4,4l16,16"),
    )
    val Close = icon("close", strokes = listOf("M6,6l12,12", "M18,6L6,18"), width = 2.2f)
    val Check = icon("check", strokes = listOf("M5,12.5l4.5,4.5L19,7"), width = 2.4f)
    val Lock = icon(
        "lock",
        fills = listOf(
            "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM9,6c0,-1.66 1.34,-3 3,-3s3,1.34 3,3v2H9V6z",
        ),
    )
    val Battery = icon(
        "battery",
        strokes = listOf("M4,8h13a1.5,1.5 0 0,1 1.5,1.5v5a1.5,1.5 0 0,1 -1.5,1.5H4a1.5,1.5 0 0,1 -1.5,-1.5v-5A1.5,1.5 0 0,1 4,8z", "M21.5,11v2"),
        fills = listOf("M5,10.5h3.5v3H5z"),
    )
    val Irritability = icon("irritability", strokes = listOf("M2.5,15l3,-5 3,5 3,-5 3,5 3,-5 3,5"), width = 2.2f)
    val Warning = icon("warning", fills = listOf("M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z"))
}
