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
    const val staff = "M2 18h20 M4 18v-3a8 8 0 0 1 16 0v3 M10 7.5V4h4v3.5"
    const val contracts = "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z " +
        "M14 2v6h6 M8 13h8 M8 17h5"
    const val plus = "M12 5v14 M5 12h14"
    const val close = "M18 6 6 18 M6 6l12 12"
    const val alert = "M12 3 2 20h20L12 3z M12 9v5 M12 17h.01"
    const val search = "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16 M21 21l-4.3-4.3"
    const val chevronRight = "m9 6 6 6-6 6"
    const val chevronLeft = "m15 6-6 6 6 6"
    const val chevronDown = "m6 9 6 6 6-6"
    const val check = "M20 6 9 17l-5-5"
    const val calendar = "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
    const val wallet = "M19 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0 0 4h15a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5 M17.5 13h.5"
    const val layers = "m12 2 9 5-9 5-9-5 9-5 M3 12l9 5 9-5 M3 17l9 5 9-5"
    const val trash = "M3 6h18 M8 6V4h8v2 M19 6l-1 15H6L5 6 M10 11v6 M14 11v6"
    const val share = "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8 M16 6l-4-4-4 4 M12 2v13"
    const val sliders = "M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6"
    const val edit = "M12 20h9 M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"

    // --- быстрые действия ------------------------------------------------
    const val phone = "M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.5c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2z"
    const val chat = "M21 11.5a8.4 8.4 0 0 1-9 8.4 8.5 8.5 0 0 1-3.8-.9L3 21l1.9-5.2A8.4 8.4 0 0 1 4 12a8.4 8.4 0 0 1 8.5-8.5 8.4 8.4 0 0 1 8.5 8z"
    const val mail = "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M22 7l-10 6L2 7"
    const val send = "M22 2 11 13 M22 2l-7 20-4-9-9-4z"
    const val task = "M9 11l3 3L22 4 M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"
    const val photo = "M3 5h18a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3 M22 15l-5-5-11 9"
    const val save = "M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z M17 21v-8H7v8 M7 3v5h8"
    const val settings = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6 M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-1.8-.3 1.6 1.6 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1A1.6 1.6 0 0 0 9 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.6 1.6 0 0 0 .3-1.8 1.6 1.6 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.6 1.6 0 0 0 4.6 9a1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.6 1.6 0 0 0 1.8.3H9a1.6 1.6 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.6 1.6 0 0 0 1 1.5 1.6 1.6 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.6 1.6 0 0 0-.3 1.8V9a1.6 1.6 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.6 1.6 0 0 0-1.5 1z"

    // --- типы зданий: объект должен узнаваться по силуэту, а не по каске ---
    const val office = "M4 21V4h10v17 M14 9h6v12 M7 8h1 M11 8h1 M7 12h1 M11 12h1 M7 16h1 M11 16h1 M17 13h1 M17 17h1"
    const val school = "M12 3 2 8l10 5 10-5-10-5 M6 10.5V16c0 1.7 2.7 3 6 3s6-1.3 6-3v-5.5 M22 8v6"
    const val living = "M3 11l9-7 9 7 M5 10v10h14V10 M10 20v-6h4v6"
    const val factory = "M3 21V10l6 4V10l6 4V8l6 5v8z M7 21v-3 M13 21v-3 M19 21v-3"
    const val medical = "M8 21V6a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v15 M4 21V12h4 M16 12h4v9 M12 8v5 M9.5 10.5h5"
    const val store = "M4 9h16l-1 12H5z M4 9l2-5h12l2 5 M9 13v4 M15 13v4"
    const val storage = "M3 21V9l9-5 9 5v12z M8 21v-7h8v7 M8 17h8"
    const val sport = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 3v18 M3.5 8.5h17 M3.5 15.5h17"
    const val culture = "M4 21V9l8-6 8 6v12z M9 21v-6h6v6 M12 6.5v1"
    const val energy = "M13 2 4 14h7l-1 8 9-12h-7z"

    // --- отделы: у каждого своё дело, и знак у каждого свой ---
    const val deptPto = "M4 20 8 4l8 3-4 16z M20 20H4 M8.5 8l6 2 M7.5 12l6 2"
    const val deptEstimate = "M7 3h10a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M9 7h6 M9 11h1 M12 11h1 M15 11h1 M9 15h1 M12 15h1 M15 15v3"
    const val deptDesign = "M12 3v3 M12 6 5 21 M12 6l7 15 M8.5 15h7"
    const val deptSurvey = "M11 3a8 8 0 1 0 0 16 8 8 0 0 0 0-16 M21 21l-4.3-4.3 M11 7v4l3 2"
    const val deptLegal = "M12 3v18 M6 7h12 M6 7 3 14h6z M18 7l-3 7h6z M8 21h8"
    const val deptSupply = "M3 7h11v10H3z M14 10h4l3 3v4h-7z M7 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4 M18 20a2 2 0 1 0 0-4 2 2 0 0 0 0 4"
    const val deptBuild = "M12 3a7 7 0 0 0-7 7v4h14v-4a7 7 0 0 0-7-7z M3 18h18 M3 14h18"
    const val deptLead = "M12 3l2.6 5.5 6 .9-4.3 4.3 1 6.1-5.3-2.9-5.3 2.9 1-6.1L3.4 9.4l6-.9z"

    // --- сводка ---
    const val summary = "M4 20V10 M10 20V4 M16 20v-8 M22 20H2"
    const val clock = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 7v5l3 2"
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
