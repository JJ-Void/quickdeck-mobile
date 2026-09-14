package ru.quickdeck.mobile.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Один набор на приложение — линейные иконки на сетке 24, толщина 1.75.
 * Геометрия в стиле Lucide, без смешивания с эмодзи и чужими наборами.
 */
object Ic {
    const val sites = "M3 21h18 M5 21V8l7-4 7 4v13 M9 21v-6h6v6 M9 11h.5 M14.5 11h.5"
    const val customers = "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 " +
        "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87"
    const val contractors = "M2 18h20 M4 18v-3a8 8 0 0 1 16 0v3 M10 7.5V4h4v3.5"
    const val contracts = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z " +
        "M14 2v6h6 M8 13h8 M8 17h5"
    const val plus = "M12 5v14 M5 12h14"
    const val close = "M18 6 6 18 M6 6l12 12"
    const val search = "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16 M21 21l-4.3-4.3"
    const val chevronRight = "m9 6 6 6-6 6"
    const val chevronLeft = "m15 6-6 6 6 6"
    const val check = "M20 6 9 17l-5-5"
    const val calendar = "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
    const val wallet = "M19 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0 0 4h15a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5 M17.5 13h.5"
    const val layers = "m12 2 9 5-9 5-9-5 9-5 M3 12l9 5 9-5 M3 17l9 5 9-5"
    const val trash = "M3 6h18 M8 6V4h8v2 M19 6l-1 15H6L5 6 M10 11v6 M14 11v6"
    const val share = "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8 M16 6l-4-4-4 4 M12 2v13"
    const val sliders = "M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6"
    const val edit = "M12 20h9 M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"
}

@Composable
fun QIcon(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = T.text,
    stroke: Float = 1.75f
) {
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(
                path = parsed,
                color = tint,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
