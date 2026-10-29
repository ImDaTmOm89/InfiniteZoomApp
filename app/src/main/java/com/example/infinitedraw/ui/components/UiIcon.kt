package com.example.infinitedraw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * U2: the app's icon set, hand-drawn on a 24-unit grid — no icon library.
 * All coordinates below are in that 24-grid; the draw multiplies by k = width / 24.
 */
enum class UiIcon {
    PEN, MARKER, AIRBRUSH, PENCIL, CHARCOAL, ERASER, SELECT,
    UNDO, REDO, LAYERS, EYE, EYE_OFF, LOCK, UNLOCK,
    MENU, CHEVRON_LEFT, CHEVRON_RIGHT, PLUS, TRASH, CLOSE, SETTINGS, IMAGE,
    // U11 exploration icons (plan specs):
    SEARCH, PIN, HOME, COMPASS, BACK, FORWARD, BOOKMARK, FIT
}

@Composable
fun UiIcon(
    icon: UiIcon,
    tint: Color,
    size: Dp = 24.dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val cd = contentDescription
    val m = if (cd != null)
        modifier.size(size).semantics { this.contentDescription = cd }
    else
        modifier.size(size)
    Canvas(modifier = m) {
        val k = this.size.width / 24f
        drawIcon(icon, tint, k)
    }
}

// ---- small draw helpers (all coordinates x, y in the 24-grid) ----

private fun DrawScope.ln(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, k: Float, tint: Color, cap: StrokeCap = StrokeCap.Round) =
    drawLine(tint, Offset(x1 * k, y1 * k), Offset(x2 * k, y2 * k), strokeWidth = w * k, cap = cap)

private fun DrawScope.dot(cx: Float, cy: Float, r: Float, k: Float, tint: Color) =
    drawCircle(tint, radius = r * k, center = Offset(cx * k, cy * k))

private fun DrawScope.ring(cx: Float, cy: Float, r: Float, w: Float, k: Float, tint: Color) =
    drawCircle(tint, radius = r * k, center = Offset(cx * k, cy * k), style = Stroke(width = w * k))

private fun DrawScope.outline(points: List<Pair<Float, Float>>, w: Float, k: Float, tint: Color, close: Boolean = false) {
    val path = Path()
    path.moveTo(points.first().first * k, points.first().second * k)
    for (i in 1 until points.size) path.lineTo(points[i].first * k, points[i].second * k)
    if (close) path.close()
    drawPath(path, tint, style = Stroke(width = w * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.fill(points: List<Pair<Float, Float>>, k: Float, tint: Color) {
    val path = Path()
    path.moveTo(points.first().first * k, points.first().second * k)
    for (i in 1 until points.size) path.lineTo(points[i].first * k, points[i].second * k)
    path.close()
    drawPath(path, tint)
}

private fun DrawScope.drawIcon(icon: UiIcon, tint: Color, k: Float) {
    when (icon) {
        UiIcon.PEN -> { ln(6f, 18f, 17f, 7f, 3f, k, tint); dot(5f, 19f, 1.6f, k, tint) }
        UiIcon.MARKER -> {
            ln(8f, 16f, 17f, 7f, 6f, k, tint, cap = StrokeCap.Butt)
            ln(5f, 19f, 8f, 16f, 2.5f, k, tint)
        }
        UiIcon.AIRBRUSH -> {
            dot(6f, 7f, 1.2f, k, tint); dot(10f, 5f, 1.0f, k, tint); dot(14f, 8f, 1.4f, k, tint)
            dot(8f, 12f, 1.6f, k, tint); dot(13f, 13f, 1.2f, k, tint); dot(17f, 12f, 1.0f, k, tint)
            dot(11f, 18f, 1.4f, k, tint); dot(18f, 18f, 1.1f, k, tint)
        }
        UiIcon.PENCIL -> {
            ln(6f, 18f, 17f, 7f, 2.2f, k, tint)
            fill(listOf(4f to 20f, 5.5f to 15.5f, 8.5f to 18.5f), k, tint)
            dot(19f, 6f, 0.9f, k, tint); dot(20f, 10f, 0.9f, k, tint)
        }
        UiIcon.CHARCOAL -> {
            ln(7f, 17f, 17f, 7f, 5f, k, tint)
            dot(5f, 20f, 1.0f, k, tint); dot(9f, 21f, 0.9f, k, tint); dot(4f, 16f, 0.9f, k, tint)
        }
        UiIcon.ERASER -> {
            outline(listOf(4f to 15f, 12f to 7f, 19f to 14f, 11f to 22f), 2f, k, tint, close = true)
            ln(8f, 11f, 15f, 18f, 2f, k, tint)
        }
        UiIcon.SELECT -> {
            drawRect(tint, topLeft = Offset(5f * k, 5f * k), size = Size(14f * k, 14f * k),
                style = Stroke(width = 2f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * k, 2.5f * k))))
            fill(listOf(13f to 13f, 21f to 16f, 16f to 18f), k, tint)
        }
        UiIcon.UNDO -> {
            val p = Path()
            p.moveTo(9f * k, 8f * k)
            p.quadraticBezierTo(6.5f * k, 9.5f * k, 6f * k, 13f * k)
            p.quadraticBezierTo(10.5f * k, 17f * k, 17f * k, 17f * k)
            drawPath(p, tint, style = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            ln(9f, 8f, 5f, 8f, 2f, k, tint)
            ln(9f, 8f, 9f, 12f, 2f, k, tint)
        }
        UiIcon.REDO -> {
            val p = Path()
            p.moveTo(15f * k, 8f * k)
            p.quadraticBezierTo(17.5f * k, 9.5f * k, 18f * k, 13f * k)
            p.quadraticBezierTo(13.5f * k, 17f * k, 7f * k, 17f * k)
            drawPath(p, tint, style = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            ln(15f, 8f, 19f, 8f, 2f, k, tint)
            ln(15f, 8f, 15f, 12f, 2f, k, tint)
        }
        UiIcon.LAYERS -> {
            outline(listOf(12f to 4f, 20f to 8f, 12f to 12f, 4f to 8f), 2f, k, tint, close = true)
            outline(listOf(4f to 12f, 12f to 16f, 20f to 12f), 2f, k, tint)
            outline(listOf(4f to 16f, 12f to 20f, 20f to 16f), 2f, k, tint)
        }
        UiIcon.EYE -> {
            drawOval(tint, topLeft = Offset(3f * k, 8f * k), size = Size(18f * k, 8f * k), style = Stroke(width = 2f * k))
            dot(12f, 12f, 2.6f, k, tint)
        }
        UiIcon.EYE_OFF -> {
            drawOval(tint, topLeft = Offset(3f * k, 8f * k), size = Size(18f * k, 8f * k), style = Stroke(width = 2f * k))
            dot(12f, 12f, 2.6f, k, tint)
            ln(5f, 19f, 19f, 5f, 2f, k, tint)
        }
        UiIcon.LOCK -> {
            drawRoundRect(tint, topLeft = Offset(6f * k, 11f * k), size = Size(12f * k, 9f * k),
                cornerRadius = CornerRadius(2f * k), style = Stroke(width = 2f * k))
            val shackle = Path()
            shackle.moveTo(8f * k, 11f * k)
            shackle.quadraticBezierTo(8f * k, 5f * k, 12f * k, 5f * k)
            shackle.quadraticBezierTo(16f * k, 5f * k, 16f * k, 11f * k)
            shackle.close()
            drawPath(shackle, tint)
        }
        UiIcon.UNLOCK -> {
            drawRoundRect(tint, topLeft = Offset(6f * k, 11f * k), size = Size(12f * k, 9f * k),
                cornerRadius = CornerRadius(2f * k), style = Stroke(width = 2f * k))
            val shackle = Path()
            shackle.moveTo(8f * k, 11f * k)
            shackle.quadraticBezierTo(8f * k, 5f * k, 12f * k, 5f * k)
            shackle.quadraticBezierTo(16f * k, 5f * k, 16f * k, 8f * k)
            drawPath(shackle, tint, style = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        UiIcon.MENU -> { dot(12f, 5f, 1.8f, k, tint); dot(12f, 12f, 1.8f, k, tint); dot(12f, 19f, 1.8f, k, tint) }
        UiIcon.CHEVRON_LEFT -> outline(listOf(14f to 6f, 8f to 12f, 14f to 18f), 2.4f, k, tint)
        UiIcon.CHEVRON_RIGHT -> outline(listOf(10f to 6f, 16f to 12f, 10f to 18f), 2.4f, k, tint)
        UiIcon.PLUS -> { ln(12f, 5f, 12f, 19f, 2.4f, k, tint); ln(5f, 12f, 19f, 12f, 2.4f, k, tint) }
        UiIcon.TRASH -> {
            drawRect(tint, topLeft = Offset(7f * k, 8f * k), size = Size(10f * k, 12f * k), style = Stroke(width = 2f * k))
            ln(5f, 8f, 19f, 8f, 2f, k, tint)
            ln(10f, 5f, 14f, 5f, 2f, k, tint)
            ln(10f, 11f, 10f, 17f, 2f, k, tint)
            ln(14f, 11f, 14f, 17f, 2f, k, tint)
        }
        UiIcon.CLOSE -> { ln(6f, 6f, 18f, 18f, 2.4f, k, tint); ln(18f, 6f, 6f, 18f, 2.4f, k, tint) }
        UiIcon.SETTINGS -> {
            ring(12f, 12f, 4f, 2f, k, tint)
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4f
                ln(12f + 6.5f * cos(a), 12f + 6.5f * sin(a), 12f + 8.5f * cos(a), 12f + 8.5f * sin(a), 2.2f, k, tint)
            }
        }
        UiIcon.IMAGE -> {
            drawRoundRect(tint, topLeft = Offset(4f * k, 5f * k), size = Size(16f * k, 14f * k),
                cornerRadius = CornerRadius(2f * k), style = Stroke(width = 2f * k))
            dot(9f, 10f, 1.6f, k, tint)
            outline(listOf(5f to 18f, 10f to 13f, 14f to 17f, 17f to 14f, 19f to 16f), 2f, k, tint)
        }
        // ---- U11 exploration icons ----
        UiIcon.SEARCH -> { ring(10f, 10f, 5f, 2f, k, tint); ln(14f, 14f, 19f, 19f, 2.4f, k, tint) }
        UiIcon.PIN -> {
            ring(12f, 9f, 4.5f, 2f, k, tint)
            outline(listOf(8.2f to 12.5f, 12f to 21f, 15.8f to 12.5f), 2f, k, tint)
        }
        UiIcon.HOME -> {
            outline(listOf(4f to 12f, 12f to 4f, 20f to 12f), 2f, k, tint)
            outline(listOf(6f to 12f, 6f to 20f, 18f to 20f, 18f to 12f), 2f, k, tint)
        }
        UiIcon.COMPASS -> {
            ring(12f, 12f, 9f, 2f, k, tint)
            fill(listOf(12f to 5f, 14.5f to 12f, 9.5f to 12f), k, com.example.infinitedraw.ui.theme.accent)
            outline(listOf(12f to 19f, 14.5f to 12f, 9.5f to 12f), 2f, k, tint)
        }
        UiIcon.BACK -> { ln(19f, 12f, 5f, 12f, 2.4f, k, tint); outline(listOf(11f to 6f, 5f to 12f, 11f to 18f), 2.4f, k, tint) }
        UiIcon.FORWARD -> { ln(5f, 12f, 19f, 12f, 2.4f, k, tint); outline(listOf(13f to 6f, 19f to 12f, 13f to 18f), 2.4f, k, tint) }
        UiIcon.BOOKMARK -> outline(listOf(7f to 4f, 17f to 4f, 17f to 20f, 12f to 16f, 7f to 20f), 2f, k, tint)
        UiIcon.FIT -> {
            outline(listOf(4f to 9f, 4f to 4f, 9f to 4f), 2.4f, k, tint)
            outline(listOf(15f to 4f, 20f to 4f, 20f to 9f), 2.4f, k, tint)
            outline(listOf(20f to 15f, 20f to 20f, 15f to 20f), 2.4f, k, tint)
            outline(listOf(9f to 20f, 4f to 20f, 4f to 15f), 2.4f, k, tint)
        }
    }
}
