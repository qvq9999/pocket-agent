package com.pocketagent.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 手绘线性图标（24×24 网格），避免引入 material-icons 依赖 */
enum class G {
    MENU, BACK, CHEV_DOWN, CHEV_RIGHT, PLUS, ARROW_UP, ARROW_DOWN, SHIELD, SPARKLE,
    FOLDER, UNDO, CHECK, STOP, SEARCH, CLOSE, WARN, INFO,
    SETTINGS, CUBE, TRASH, THEME, SLIDERS, REFRESH, MORE,
    CAMERA, IMAGE, DOC,
}

@Composable
fun Glyph(
    kind: G,
    dim: Dp = 18.dp,
    color: Color = pal().muted,
    stroke: Float = 1.9f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(dim)) {
        draw24(kind, color, stroke)
    }
}

private fun DrawScope.draw24(kind: G, color: Color, stroke: Float) {
    val s = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    val sw = stroke * s
    val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (kind) {
        G.MENU -> {
            for (y in listOf(6.5f, 12f, 17.5f)) {
                drawLine(color, o(3.5f, y), o(20.5f, y), sw, StrokeCap.Round)
            }
        }
        G.BACK -> {
            val p = Path().apply {
                moveTo(15f * s, 5f * s); lineTo(8f * s, 12f * s); lineTo(15f * s, 19f * s)
            }
            drawPath(p, color, style = st)
        }
        G.CHEV_DOWN -> {
            val p = Path().apply {
                moveTo(6f * s, 9.5f * s); lineTo(12f * s, 15.5f * s); lineTo(18f * s, 9.5f * s)
            }
            drawPath(p, color, style = st)
        }
        G.CHEV_RIGHT -> {
            val p = Path().apply {
                moveTo(9.5f * s, 6f * s); lineTo(15.5f * s, 12f * s); lineTo(9.5f * s, 18f * s)
            }
            drawPath(p, color, style = st)
        }
        G.PLUS -> {
            drawLine(color, o(12f, 5f), o(12f, 19f), sw, StrokeCap.Round)
            drawLine(color, o(5f, 12f), o(19f, 12f), sw, StrokeCap.Round)
        }
        G.ARROW_UP -> {
            drawLine(color, o(12f, 19f), o(12f, 5f), sw, StrokeCap.Round)
            val p = Path().apply {
                moveTo(5f * s, 12f * s); lineTo(12f * s, 5f * s); lineTo(19f * s, 12f * s)
            }
            drawPath(p, color, style = st)
        }
        // 「回到底部」用：与发送键同形的向下箭头
        G.ARROW_DOWN -> {
            drawLine(color, o(12f, 5f), o(12f, 19f), sw, StrokeCap.Round)
            val p = Path().apply {
                moveTo(5f * s, 12f * s); lineTo(12f * s, 19f * s); lineTo(19f * s, 12f * s)
            }
            drawPath(p, color, style = st)
        }
        G.SHIELD -> {
            val p = Path().apply {
                moveTo(12f * s, 3f * s)
                lineTo(5f * s, 6f * s)
                lineTo(5f * s, 12f * s)
                quadraticBezierTo(5f * s, 18f * s, 12f * s, 21f * s)
                quadraticBezierTo(19f * s, 18f * s, 19f * s, 12f * s)
                lineTo(19f * s, 6f * s)
                close()
            }
            drawPath(p, color, style = st)
        }
        G.SPARKLE -> {
            val p = Path().apply {
                moveTo(12f * s, 3.4f * s)
                lineTo(13.6f * s, 9.2f * s)
                lineTo(19.4f * s, 10.8f * s)
                lineTo(13.6f * s, 12.4f * s)
                lineTo(12f * s, 18.2f * s)
                lineTo(10.4f * s, 12.4f * s)
                lineTo(4.6f * s, 10.8f * s)
                lineTo(10.4f * s, 9.2f * s)
                close()
            }
            drawPath(p, color, style = st)
        }
        G.FOLDER -> {
            val p = Path().apply {
                moveTo(3f * s, 7.5f * s)
                quadraticBezierTo(3f * s, 6f * s, 4.5f * s, 6f * s)
                lineTo(8.5f * s, 6f * s)
                lineTo(10.5f * s, 8.5f * s)
                lineTo(17.5f * s, 8.5f * s)
                quadraticBezierTo(19f * s, 8.5f * s, 19f * s, 10f * s)
                lineTo(19f * s, 17.5f * s)
                quadraticBezierTo(19f * s, 19f * s, 17.5f * s, 19f * s)
                lineTo(4.5f * s, 19f * s)
                quadraticBezierTo(3f * s, 19f * s, 3f * s, 17.5f * s)
                close()
            }
            drawPath(p, color, style = st)
        }
        G.UNDO -> {
            val p = Path().apply {
                moveTo(4f * s, 8f * s); lineTo(4f * s, 14f * s); lineTo(10f * s, 14f * s)
            }
            drawPath(p, color, style = st)
            drawArc(
                color = color,
                startAngle = 190f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = o(5f, 4f),
                size = androidx.compose.ui.geometry.Size(14f * s, 14f * s),
                style = Stroke(sw, cap = StrokeCap.Round),
            )
        }
        G.CHECK -> {
            val p = Path().apply {
                moveTo(4.5f * s, 12.5f * s); lineTo(9.8f * s, 17.8f * s); lineTo(19.5f * s, 6.5f * s)
            }
            drawPath(p, color, style = Stroke(sw * 1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        G.STOP -> {
            drawRoundRect(
                color = color,
                topLeft = o(7f, 7f),
                size = androidx.compose.ui.geometry.Size(10f * s, 10f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * s),
            )
        }
        G.SEARCH -> {
            drawCircle(color, radius = 6.2f * s, center = o(11f, 11f), style = st)
            drawLine(color, o(15.6f, 15.6f), o(20f, 20f), sw, StrokeCap.Round)
        }
        G.CLOSE -> {
            drawLine(color, o(6f, 6f), o(18f, 18f), sw, StrokeCap.Round)
            drawLine(color, o(18f, 6f), o(6f, 18f), sw, StrokeCap.Round)
        }
        G.WARN -> {
            val p = Path().apply {
                moveTo(12f * s, 3.5f * s); lineTo(21.5f * s, 20f * s); lineTo(2.5f * s, 20f * s); close()
            }
            drawPath(p, color, style = st)
            drawLine(color, o(12f, 9.5f), o(12f, 14.5f), sw, StrokeCap.Round)
            drawCircle(color, radius = sw * 0.62f, center = o(12f, 17.2f))
        }
        G.INFO -> {
            drawCircle(color, radius = 9f * s, center = o(12f, 12f), style = st)
            drawLine(color, o(12f, 11f), o(12f, 16.5f), sw, StrokeCap.Round)
            drawCircle(color, radius = sw * 0.62f, center = o(12f, 7.8f))
        }
        G.SETTINGS -> {
            // 齿轮：内圈 + 8 个齿（比画完整齿形更稳，小尺寸下也不会糊）
            drawCircle(color, radius = 4.6f * s, center = o(12f, 12f), style = st)
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                val c = Math.cos(a).toFloat()
                val sn = Math.sin(a).toFloat()
                drawLine(
                    color,
                    o(12f + 6.0f * c, 12f + 6.0f * sn),
                    o(12f + 8.4f * c, 12f + 8.4f * sn),
                    sw, StrokeCap.Round,
                )
            }
        }
        G.CUBE -> {
            val p = Path().apply {
                moveTo(12f * s, 3f * s); lineTo(20f * s, 7.5f * s); lineTo(20f * s, 16.5f * s)
                lineTo(12f * s, 21f * s); lineTo(4f * s, 16.5f * s); lineTo(4f * s, 7.5f * s)
                close()
            }
            drawPath(p, color, style = st)
            drawLine(color, o(12f, 3f), o(12f, 12f), sw, StrokeCap.Round)
            drawLine(color, o(4f, 7.5f), o(12f, 12f), sw, StrokeCap.Round)
            drawLine(color, o(20f, 7.5f), o(12f, 12f), sw, StrokeCap.Round)
        }
        G.TRASH -> {
            drawLine(color, o(3.5f, 6.5f), o(20.5f, 6.5f), sw, StrokeCap.Round)
            val lid = Path().apply {
                moveTo(9.5f * s, 6.5f * s); lineTo(9.5f * s, 4.2f * s)
                lineTo(14.5f * s, 4.2f * s); lineTo(14.5f * s, 6.5f * s)
            }
            drawPath(lid, color, style = st)
            val body = Path().apply {
                moveTo(6f * s, 6.5f * s); lineTo(7.2f * s, 19.8f * s)
                lineTo(16.8f * s, 19.8f * s); lineTo(18f * s, 6.5f * s)
            }
            drawPath(body, color, style = st)
            drawLine(color, o(10.2f, 10f), o(10.6f, 16.3f), sw, StrokeCap.Round)
            drawLine(color, o(13.8f, 10f), o(13.4f, 16.3f), sw, StrokeCap.Round)
        }
        G.THEME -> {
            // 半明半暗的圆：左半实心表示深色，右半留空表示浅色
            drawCircle(color, radius = 8.4f * s, center = o(12f, 12f), style = st)
            val half = Path().apply {
                moveTo(12f * s, 3.6f * s)
                arcTo(
                    androidx.compose.ui.geometry.Rect(3.6f * s, 3.6f * s, 20.4f * s, 20.4f * s),
                    -90f, -180f, false,
                )
                close()
            }
            drawPath(half, color)
        }
        G.REFRESH -> {
            // 开口圆弧 + 箭头：表示"重新获取"
            val r = 7.4f
            drawArc(
                color = color,
                startAngle = -55f,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = Offset((12f - r) * s, (12f - r) * s),
                size = androidx.compose.ui.geometry.Size(2 * r * s, 2 * r * s),
                style = st,
            )
            drawLine(color, o(12.6f, 3.0f), o(18.6f, 4.4f), sw, StrokeCap.Round)
            drawLine(color, o(18.6f, 4.4f), o(17.2f, 10.2f), sw, StrokeCap.Round)
        }
        G.SLIDERS -> {
            drawLine(color, o(4f, 7f), o(20f, 7f), sw, StrokeCap.Round)
            drawLine(color, o(4f, 17f), o(20f, 17f), sw, StrokeCap.Round)
            drawCircle(color, radius = 2.4f * s, center = o(9f, 7f))
            drawCircle(color, radius = 2.4f * s, center = o(15.5f, 17f))
        }
        G.CAMERA -> {
            drawRoundRect(
                color = color,
                topLeft = o(3.2f, 7.2f),
                size = androidx.compose.ui.geometry.Size(17.6f * s, 12.6f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.8f * s),
                style = st,
            )
            // 顶部取景凸起
            val bump = Path().apply {
                moveTo(8.6f * s, 7.2f * s); lineTo(9.9f * s, 4.5f * s)
                lineTo(14.1f * s, 4.5f * s); lineTo(15.4f * s, 7.2f * s)
            }
            drawPath(bump, color, style = st)
            drawCircle(color, radius = 3.3f * s, center = o(12f, 13.5f), style = st)
        }
        G.IMAGE -> {
            drawRoundRect(
                color = color,
                topLeft = o(3.4f, 4.8f),
                size = androidx.compose.ui.geometry.Size(17.2f * s, 14.4f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.6f * s),
                style = st,
            )
            drawCircle(color, radius = 1.5f * s, center = o(8.8f, 9.6f), style = st)
            val hill = Path().apply {
                moveTo(4.2f * s, 17.4f * s); lineTo(9.2f * s, 12.6f * s); lineTo(12.4f * s, 15.6f * s)
                lineTo(15.6f * s, 12.2f * s); lineTo(20.0f * s, 16.4f * s)
            }
            drawPath(hill, color, style = st)
        }
        G.DOC -> {
            val page = Path().apply {
                moveTo(6.4f * s, 3.4f * s); lineTo(13.4f * s, 3.4f * s); lineTo(18.4f * s, 8.4f * s)
                lineTo(18.4f * s, 20.6f * s); lineTo(6.4f * s, 20.6f * s); close()
            }
            drawPath(page, color, style = st)
            drawLine(color, o(13.4f, 3.6f), o(13.4f, 8.6f), sw, StrokeCap.Round)
            drawLine(color, o(13.4f, 8.6f), o(18.2f, 8.6f), sw, StrokeCap.Round)
            drawLine(color, o(9.4f, 13.2f), o(15.4f, 13.2f), sw, StrokeCap.Round)
            drawLine(color, o(9.4f, 16.6f), o(13.4f, 16.6f), sw, StrokeCap.Round)
        }
        G.MORE -> {
            // 三个实心点：抽屉里"当前会话的更多操作"。实心而不是描边，缩到 16dp 才不糊
            for (x in listOf(5.6f, 12f, 18.4f)) {
                drawCircle(color, radius = 1.7f * s, center = o(x, 12f))
            }
        }
    }
}
