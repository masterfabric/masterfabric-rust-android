package co.masterfabric.rust.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** One horizontal bar (Compose Canvas twin of a Swift Charts `BarMark`). */
class Bar(val label: String, val value: Double, val color: Color, val annotation: String? = null)

/** Horizontal bar chart with an optional dashed reference rule (e.g. 1× = equal). */
@Composable
fun HBarChart(bars: List<Bar>, rule: Double? = null, rowHeight: Int = 26, labelWidth: Int = 104) {
    val maxV = max(bars.maxOfOrNull { it.value } ?: 1.0, rule ?: 0.0) * 1.18
    val measurer = rememberTextMeasurer()
    val annStyle = TextStyle(fontSize = 10.sp, color = Palette.secondary)
    Column {
        bars.forEach { b ->
            Row(Modifier.fillMaxWidth().height(rowHeight.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, style = TextStyle(fontSize = 11.sp, color = Palette.label), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(labelWidth.dp))
                Canvas(Modifier.weight(1f).height(rowHeight.dp)) {
                    val w = size.width
                    val barH = size.height * 0.62f
                    val top = (size.height - barH) / 2
                    val bw = (w * (b.value / maxV)).toFloat().coerceAtLeast(2f)
                    drawRoundRect(b.color, Offset(0f, top), Size(bw, barH), CornerRadius(3f, 3f))
                    rule?.let {
                        val x = (w * (it / maxV)).toFloat()
                        drawLine(Palette.secondary, Offset(x, 0f), Offset(x, size.height), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    }
                    b.annotation?.let { a ->
                        val layout = measurer.measure(a, annStyle)
                        val x = (bw + 6f).coerceAtMost(w - layout.size.width)
                        drawText(layout, topLeft = Offset(x, (size.height - layout.size.height) / 2))
                    }
                }
            }
        }
    }
}

/** Grouped horizontal bars: one group per test, one bar per series. */
class BarGroup(val label: String, val bars: List<Pair<String, Double?>>)

@Composable
fun GroupedBarChart(groups: List<BarGroup>, colors: Map<String, Color>, format: (Double) -> String) {
    val maxV = (groups.flatMap { g -> g.bars.mapNotNull { it.second } }.maxOrNull() ?: 1.0) * 1.3
    val measurer = rememberTextMeasurer()
    val annStyle = TextStyle(fontSize = 10.sp, color = Palette.secondary)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { g ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(g.label, style = TextStyle(fontSize = 11.sp, color = Palette.label), maxLines = 2, modifier = Modifier.width(96.dp))
                Column(Modifier.weight(1f)) {
                    g.bars.forEach { (series, v) ->
                        Canvas(Modifier.fillMaxWidth().height(15.dp)) {
                            if (v == null) return@Canvas
                            val bw = (size.width * (v / maxV)).toFloat().coerceAtLeast(2f)
                            val barH = size.height * 0.75f
                            drawRoundRect(colors[series] ?: Palette.gray, Offset(0f, (size.height - barH) / 2), Size(bw, barH), CornerRadius(3f, 3f))
                            val layout = measurer.measure(format(v), annStyle)
                            drawText(layout, topLeft = Offset((bw + 6f).coerceAtMost(size.width - layout.size.width), (size.height - layout.size.height) / 2))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(entries: List<Pair<String, Color>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (name, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c))
                Spacer(Modifier.width(5.dp))
                Text(name, style = TextStyle(fontSize = 11.sp, color = Palette.secondary))
            }
        }
    }
}

/** One line series for [LogLogChart]. */
class Series(val name: String, val color: Color, val points: List<Pair<Double, Double>>)

/** Log–log line chart with symbols (twin of the Swift Charts `LineMark` with log scales). */
@Composable
fun LogLogChart(series: List<Series>, xTicks: List<Double>, height: Int = 200) {
    val measurer = rememberTextMeasurer()
    val tick = TextStyle(fontSize = 10.sp, color = Palette.secondary)
    val all = series.flatMap { it.points }
    if (all.isEmpty()) return
    val xMin = log10(xTicks.first())
    val xMax = log10(xTicks.last())
    val yLo = floor(log10(all.minOf { it.second }))
    val yHi = kotlin.math.ceil(log10(all.maxOf { it.second }))
    val yMax = if (yHi <= yLo) yLo + 1 else yHi
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val left = 44f
        val bottom = size.height - 26f
        val right = size.width - 8f
        val top = 6f
        fun px(x: Double) = (left + (log10(x) - xMin) / (xMax - xMin) * (right - left)).toFloat()
        fun py(y: Double) = (bottom - (log10(y) - yLo) / (yMax - yLo) * (bottom - top)).toFloat()
        // grid + y labels (decades)
        var d = yLo
        while (d <= yMax + 1e-9) {
            val y = py(10.0.pow(d))
            drawLine(Color(0x1A000000), Offset(left, y), Offset(right, y), 1f)
            val v = 10.0.pow(d)
            val label = if (v >= 1000) "${(v / 1000).toInt()}k" else if (v >= 1) "${v.toInt()}" else f("%.1f", v)
            val l = measurer.measure(label, tick)
            drawText(l, topLeft = Offset(left - l.size.width - 6f, y - l.size.height / 2))
            d += 1.0
        }
        xTicks.forEach { x ->
            val xx = px(x)
            drawLine(Color(0x1A000000), Offset(xx, top), Offset(xx, bottom), 1f)
            val label = if (x >= 1000) "${(x / 1000).toInt()}k" else "${x.toInt()}"
            val l = measurer.measure(label, tick)
            drawText(l, topLeft = Offset(xx - l.size.width / 2, bottom + 6f))
        }
        series.forEach { s ->
            val pts = s.points.sortedBy { it.first }
            val path = Path()
            pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
            drawPath(path, s.color, style = Stroke(width = 4f))
            pts.forEach { (x, y) -> drawCircle(s.color, 7f, Offset(px(x), py(y))) }
        }
    }
}
