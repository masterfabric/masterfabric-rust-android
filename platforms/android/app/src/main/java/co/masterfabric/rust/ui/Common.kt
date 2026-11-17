package co.masterfabric.rust.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.masterfabric.rust.core.PerfMetric
import java.util.Locale

/** Rust state carries SF Symbol names (shared with iOS); Android maps them to Material icons. */
fun icon(symbol: String): ImageVector = when (symbol) {
    "speedometer" -> Icons.Filled.Speed
    "lightbulb.max.fill", "light.max" -> Icons.Filled.Lightbulb
    "info.circle" -> Icons.Filled.Info
    "gearshape.2.fill" -> Icons.Filled.Settings
    "photo.fill", "photo.on.rectangle.angled", "photo.stack" -> Icons.Filled.Photo
    "film.fill", "film.stack" -> Icons.Filled.Movie
    "cube.fill", "cube.transparent" -> Icons.Filled.ViewInAr
    "function" -> Icons.Filled.Functions
    "cylinder.split.1x2.fill" -> Icons.Filled.Storage
    "textformat", "textformat.abc" -> Icons.Filled.Abc
    "textformat.123", "number.square" -> Icons.Filled.Numbers
    "lock.fill" -> Icons.Filled.Lock
    "lock.shield.fill", "checkmark.shield.fill" -> Icons.Filled.Security
    "point.3.connected.trianglepath.dotted" -> Icons.Filled.Hub
    "point.topleft.down.to.point.bottomright.curvepath" -> Icons.Filled.Route
    "cpu", "cpu.fill" -> Icons.Filled.Memory
    "iphone.gen3" -> Icons.Filled.PhoneAndroid
    "gamecontroller.fill" -> Icons.Filled.SportsEsports
    "terminal.fill" -> Icons.Filled.Terminal
    "server.rack" -> Icons.Filled.Dns
    "globe.europe.africa.fill" -> Icons.Filled.Public
    "sensor.fill" -> Icons.Filled.Sensors
    "aqi.medium" -> Icons.Filled.Air
    "drop.fill" -> Icons.Filled.WaterDrop
    "archivebox.fill" -> Icons.Filled.Archive
    "camera.aperture" -> Icons.Filled.Camera
    "camera.filters" -> Icons.Filled.FilterVintage
    "chart.bar.doc.horizontal" -> Icons.Filled.BarChart
    "chart.xyaxis.line" -> Icons.Filled.ShowChart
    "figure.walk.motion" -> Icons.Filled.DirectionsWalk
    "circle.grid.cross.fill", "circle.hexagongrid.fill" -> Icons.Filled.Grain
    "crown.fill" -> Icons.Filled.EmojiEvents
    "square.grid.3x3", "square.grid.3x3.fill", "square.grid.3x3.square", "square.grid.4x3.fill" -> Icons.Filled.GridOn
    "square.stack.3d.down.right.fill", "square.stack.3d.up.fill" -> Icons.Filled.Layers
    "triangle.fill" -> Icons.Filled.ChangeHistory
    "text.magnifyingglass" -> Icons.Filled.ManageSearch
    "text.word.spacing" -> Icons.Filled.SpaceBar
    "character.cursor.ibeam" -> Icons.Filled.TextFields
    "arrow.up.arrow.down" -> Icons.Filled.SwapVert
    "arrow.down.right.and.arrow.up.left" -> Icons.Filled.Compress
    "arrow.down.circle.fill" -> Icons.Filled.ArrowCircleDown
    "arrow.up.circle.fill" -> Icons.Filled.ArrowCircleUp
    "hand.tap.fill" -> Icons.Filled.TouchApp
    "rectangle.on.rectangle" -> Icons.Filled.Layers
    "kotlin", "swift" -> Icons.Filled.Code
    "rust" -> Icons.Filled.Settings
    else -> Icons.Filled.Memory
}

fun categorySymbol(c: String): String = when (c) {
    "Image" -> "photo.fill"
    "Video" -> "film.fill"
    "3D" -> "cube.fill"
    "Math" -> "function"
    "Data" -> "cylinder.split.1x2.fill"
    "Text" -> "textformat"
    "Crypto" -> "lock.fill"
    "Algorithms" -> "point.3.connected.trianglepath.dotted"
    else -> "cpu"
}

val allCategories = listOf("Image", "Video", "3D", "Math", "Data", "Text", "Crypto", "Algorithms")

val Mono = FontFamily.Monospace

fun f(fmt: String, vararg args: Any?): String = String.format(Locale.ROOT, fmt, *args)

fun formatMs(ms: Double): String = when {
    ms >= 100 -> f("%.0f ms", ms)
    ms >= 10 -> f("%.1f ms", ms)
    else -> f("%.2f ms", ms)
}

fun formatPerf(m: PerfMetric): String = when (m.unit) {
    "µs" -> if (m.value >= 1000) f("%.2f ms", m.value / 1000) else f("%.1f µs", m.value)
    "ms" -> f("%.1f ms", m.value)
    else -> f("%.2f %s", m.value, m.unit)
}

fun shortName(id: String, fallback: String = ""): String = when (id) {
    "raytrace" -> "Ray tracing"
    "raster" -> "Rasterization"
    "image" -> "Image pipeline"
    "video" -> "Video processing"
    "mesh" -> "3D mesh"
    "fib" -> "Fibonacci"
    "sort" -> "Sort 1M"
    "sha256" -> "SHA-256"
    "json" -> "JSON"
    "sieve" -> "Prime sieve"
    "matmul" -> "Matrix"
    "hashmap" -> "HashMap"
    else -> fallback
}

fun geomean(ratios: List<Double>): Double? =
    if (ratios.isEmpty()) null else kotlin.math.exp(ratios.sumOf { kotlin.math.ln(it) } / ratios.size)

@Composable
fun IconTile(symbol: String, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon(symbol), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.card)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(title: String, symbol: String, color: Color = Palette.label) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(icon(symbol), null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color))
    }
}

@Composable
fun Chip(text: String, warn: Boolean = false) {
    val c = if (warn) Palette.orange else Palette.secondary
    Text(
        text,
        maxLines = 1,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (warn) Palette.orange else Palette.label),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

object Type {
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.label)
    val body = TextStyle(fontSize = 16.sp, color = Palette.label)
    val subhead = TextStyle(fontSize = 15.sp, color = Palette.label)
    val subheadSecondary = TextStyle(fontSize = 14.sp, color = Palette.secondary)
    val footnote = TextStyle(fontSize = 13.sp, color = Palette.label)
    val caption = TextStyle(fontSize = 12.sp, color = Palette.secondary)
    val caption2 = TextStyle(fontSize = 11.sp, color = Palette.secondary)
    val monoCaption = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Palette.secondary)
}
