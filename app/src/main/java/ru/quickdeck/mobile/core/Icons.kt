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
 * Иконки — набор Lucide (ISC), не самодельные пути.
 *
 * Раньше пути рисовались руками, и это было видно: разная толщина, разный
 * оптический размер, половина знаков не опознавалась. Здесь один набор,
 * нарисованный по сетке 24x24 с обводкой 2, — тот же, которым рисуют
 * интерфейсы вокруг. Дуги и прямоугольники развёрнуты в путь, потому что
 * рисовальщик понимает только d.
 *
 * Лицензия ISC, Lucide Contributors. Полный текст — в LICENSES.md.
 * Добавлять сюда самодельные пути нельзя: берём недостающее из Lucide.
 */
object Ic {

    // --- Разделы и сущности ---
    /** lucide: building-2 */
    const val sites = "M10 12h4 M10 8h4 M14 21v-3a2 2 0 0 0-4 0v3 M6 10H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-2 M6 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16"
    /** lucide: file-text */
    const val contracts = "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z M14 2v5a1 1 0 0 0 1 1h5 M10 9H8 M16 13H8 M16 17H8"
    /** lucide: briefcase */
    const val customers = "M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16 M4.0 6.0h16.0a2.0 2.0 0 0 1 2.0 2.0v10.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-10.0a2.0 2.0 0 0 1 2.0 -2.0"
    /** lucide: users */
    const val staff = "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3.128a4 4 0 0 1 0 7.744 M22 21v-2a4 4 0 0 0-3-3.87 M5.0 7.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0"
    /** lucide: chart-no-axes-column */
    const val summary = "M5 21v-6 M12 21V3 M19 21V9"
    /** lucide: square-check-big */
    const val task = "M21 10.656V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h12.344 m9 11 3 3L22 4"

    // --- Типы зданий ---
    /** lucide: building-2 */
    const val office = "M10 12h4 M10 8h4 M14 21v-3a2 2 0 0 0-4 0v3 M6 10H4a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-2 M6 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16"
    /** lucide: graduation-cap */
    const val school = "M21.42 10.922a1 1 0 0 0-.019-1.838L12.83 5.18a2 2 0 0 0-1.66 0L2.6 9.08a1 1 0 0 0 0 1.832l8.57 3.908a2 2 0 0 0 1.66 0z M22 10v6 M6 12.5V16a6 3 0 0 0 12 0v-3.5"
    /** lucide: house */
    const val living = "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8 M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
    /** lucide: factory */
    const val factory = "M12 16h.01 M16 16h.01 M3 19a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8.5a.5.5 0 0 0-.769-.422l-4.462 2.844A.5.5 0 0 1 15 10.5v-2a.5.5 0 0 0-.769-.422L9.77 10.922A.5.5 0 0 1 9 10.5V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2z M8 16h.01"
    /** lucide: cross */
    const val medical = "M4 9a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h4a1 1 0 0 1 1 1v4a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2v-4a1 1 0 0 1 1-1h4a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2h-4a1 1 0 0 1-1-1V4a2 2 0 0 0-2-2h-2a2 2 0 0 0-2 2v4a1 1 0 0 1-1 1z"
    /** lucide: store */
    const val store = "M15 21v-5a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5 M17.774 10.31a1.12 1.12 0 0 0-1.549 0 2.5 2.5 0 0 1-3.451 0 1.12 1.12 0 0 0-1.548 0 2.5 2.5 0 0 1-3.452 0 1.12 1.12 0 0 0-1.549 0 2.5 2.5 0 0 1-3.77-3.248l2.889-4.184A2 2 0 0 1 7 2h10a2 2 0 0 1 1.653.873l2.895 4.192a2.5 2.5 0 0 1-3.774 3.244 M4 10.95V19a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8.05"
    /** lucide: warehouse */
    const val storage = "M18 21V10a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1v11 M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 1.132-1.803l7.95-3.974a2 2 0 0 1 1.837 0l7.948 3.974A2 2 0 0 1 22 8z M6 13h12 M6 17h12"
    /** lucide: dumbbell */
    const val sport = "M17.596 12.768a2 2 0 1 0 2.829-2.829l-1.768-1.767a2 2 0 0 0 2.828-2.829l-2.828-2.828a2 2 0 0 0-2.829 2.828l-1.767-1.768a2 2 0 1 0-2.829 2.829z m2.5 21.5 1.4-1.4 m20.1 3.9 1.4-1.4 M5.343 21.485a2 2 0 1 0 2.829-2.828l1.767 1.768a2 2 0 1 0 2.829-2.829l-6.364-6.364a2 2 0 1 0-2.829 2.829l1.768 1.767a2 2 0 0 0-2.828 2.829z m9.6 14.4 4.8-4.8"
    /** lucide: landmark */
    const val culture = "M10 18v-7 M11.119 2.205a2 2 0 0 1 1.762 0l7.84 3.846A.5.5 0 0 1 20.5 7h-17a.5.5 0 0 1-.22-.949z M14 18v-7 M18 18v-7 M3 22h18 M6 18v-7"
    /** lucide: zap */
    const val energy = "M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z"

    // --- Отделы ---
    /** lucide: ruler */
    const val deptPto = "M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0Z m14.5 12.5 2-2 m11.5 9.5 2-2 m8.5 6.5 2-2 m17.5 15.5 2-2"
    /** lucide: calculator */
    const val deptEstimate = "M6.0 2.0h12.0a2.0 2.0 0 0 1 2.0 2.0v16.0a2.0 2.0 0 0 1 -2.0 2.0h-12.0a2.0 2.0 0 0 1 -2.0 -2.0v-16.0a2.0 2.0 0 0 1 2.0 -2.0 M8 6L16 6 M16 14L16 18 M16 10h.01 M12 10h.01 M8 10h.01 M12 14h.01 M8 14h.01 M12 18h.01 M8 18h.01"
    /** lucide: pen-tool */
    const val deptDesign = "M15.707 21.293a1 1 0 0 1-1.414 0l-1.586-1.586a1 1 0 0 1 0-1.414l5.586-5.586a1 1 0 0 1 1.414 0l1.586 1.586a1 1 0 0 1 0 1.414z m18 13-1.375-6.874a1 1 0 0 0-.746-.776L3.235 2.028a1 1 0 0 0-1.207 1.207L5.35 15.879a1 1 0 0 0 .776.746L13 18 m2.3 2.3 7.286 7.286 M9.0 11.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0"
    /** lucide: scan-search */
    const val deptSurvey = "M3 7V5a2 2 0 0 1 2-2h2 M17 3h2a2 2 0 0 1 2 2v2 M21 17v2a2 2 0 0 1-2 2h-2 M7 21H5a2 2 0 0 1-2-2v-2 M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0 m16 16-1.9-1.9"
    /** lucide: scale */
    const val deptLegal = "M12 3v18 m19 8 3 8a5 5 0 0 1-6 0zV7 M3 7h1a17 17 0 0 0 8-2 17 17 0 0 0 8 2h1 m5 8 3 8a5 5 0 0 1-6 0zV7 M7 21h10"
    /** lucide: truck */
    const val deptSupply = "M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2 M15 18H9 M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.624l-3.48-4.35A1 1 0 0 0 17.52 8H14 M15.0 18.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0 M5.0 18.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0"
    /** lucide: hard-hat */
    const val deptBuild = "M10 10V5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v5 M14 6a6 6 0 0 1 6 6v3 M4 15v-3a6 6 0 0 1 6-6 M3.0 15.0h18.0a1.0 1.0 0 0 1 1.0 1.0v2.0a1.0 1.0 0 0 1 -1.0 1.0h-18.0a1.0 1.0 0 0 1 -1.0 -1.0v-2.0a1.0 1.0 0 0 1 1.0 -1.0"
    /** lucide: crown */
    const val deptLead = "M11.562 3.266a.5.5 0 0 1 .876 0L15.39 8.87a1 1 0 0 0 1.516.294L21.183 5.5a.5.5 0 0 1 .798.519l-2.834 10.246a1 1 0 0 1-.956.734H5.81a1 1 0 0 1-.957-.734L2.02 6.02a.5.5 0 0 1 .798-.519l4.276 3.664a1 1 0 0 0 1.516-.294z M5 21h14"

    // --- Действия и состояния ---
    /** lucide: plus */
    const val plus = "M5 12h14 M12 5v14"
    /** lucide: x */
    const val close = "M18 6 6 18 m6 6 12 12"
    /** lucide: search */
    const val search = "m21 21-4.34-4.34 M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0"
    /** lucide: settings-2 */
    const val settings = "M14 17H5 M19 7h-9 M14.0 17.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0 M4.0 7.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0"
    /** lucide: check */
    const val check = "M20 6 9 17l-5-5"
    /** lucide: chevron-right */
    const val chevronRight = "m9 18 6-6-6-6"
    /** lucide: chevron-left */
    const val chevronLeft = "m15 18-6-6 6-6"
    /** lucide: chevron-down */
    const val chevronDown = "m6 9 6 6 6-6"
    /** lucide: arrow-left */
    const val back = "m12 19-7-7 7-7 M19 12H5"
    /** lucide: calendar */
    const val calendar = "M8 2v3 M16 2v3 M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0 M3 9h18"
    /** lucide: wallet */
    const val wallet = "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1 M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4"
    /** lucide: banknote */
    const val money = "M4.0 6.0h16.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0 M10.0 12.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0 M6 12h.01M18 12h.01"
    /** lucide: layers */
    const val layers = "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12 M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17"
    /** lucide: trash-2 */
    const val trash = "M10 11v6 M14 11v6 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6 M3 6h18 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
    /** lucide: share-2 */
    const val share = "M15.0 5.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0 M3.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0 M15.0 19.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0 M8.59 13.51L15.42 17.49 M15.41 6.51L8.59 10.49"
    /** lucide: sliders-horizontal */
    const val sliders = "M10 5H3 M12 19H3 M14 3v4 M16 17v4 M21 12h-9 M21 19h-5 M21 5h-7 M8 10v4 M8 12H3"
    /** lucide: pencil */
    const val edit = "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z m15 5 4 4"
    /** lucide: phone */
    const val phone = "M13.832 16.568a1 1 0 0 0 1.213-.303l.355-.465A2 2 0 0 1 17 15h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2A18 18 0 0 1 2 4a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-.8 1.6l-.468.351a1 1 0 0 0-.292 1.233 14 14 0 0 0 6.392 6.384"
    /** lucide: message-circle */
    const val chat = "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719"
    /** lucide: mail */
    const val mail = "m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7 M4.0 4.0h16.0a2.0 2.0 0 0 1 2.0 2.0v12.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-12.0a2.0 2.0 0 0 1 2.0 -2.0"
    /** lucide: send */
    const val send = "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z m21.854 2.147-10.94 10.939"
    /** lucide: image */
    const val photo = "M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0 M7.0 9.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0 m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
    /** lucide: save */
    const val save = "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7 M7 3v4a1 1 0 0 0 1 1h7"
    /** lucide: clock */
    const val clock = "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0 M12 6v6l4 2"
    /** lucide: triangle-alert */
    const val alert = "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3 M12 9v4 M12 17h.01"
    /** lucide: folder */
    const val folder = "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"
    /** lucide: folder-open */
    const val folderOpen = "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2"
    /** lucide: map-pin */
    const val pin = "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0 M9.0 10.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0"
    /** lucide: list-filter */
    const val filter = "M2 5h20 M6 12h12 M9 19h6"
    /** lucide: refresh-cw */
    const val sync = "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8 M21 3v5h-5 M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16 M8 16H3v5"
    /** lucide: archive */
    const val archive = "M3.0 3.0h18.0a1.0 1.0 0 0 1 1.0 1.0v3.0a1.0 1.0 0 0 1 -1.0 1.0h-18.0a1.0 1.0 0 0 1 -1.0 -1.0v-3.0a1.0 1.0 0 0 1 1.0 -1.0 M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8 M10 12h4"
    /** lucide: ellipsis */
    const val more = "M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0 M18.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0 M4.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0"
}

/**
 * Рисовальщик. Обводка 1.9 при 24 — оптическая плотность Lucide; тоньше
 * знак начинает теряться, толще — спорит с текстом.
 */
@Composable
fun QIcon(
    path: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = T.ink,
    stroke: Float = 1.9f
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
