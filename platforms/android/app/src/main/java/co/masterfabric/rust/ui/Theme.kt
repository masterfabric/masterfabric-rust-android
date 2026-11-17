package co.masterfabric.rust.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** iOS-like system palette, so both twins read the same in screenshots. */
object Palette {
    val orange = Color(0xFFFF9500)
    val blue = Color(0xFF007AFF)
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF3B30)
    val purple = Color(0xFFAF52DE)
    val pink = Color(0xFFFF2D55)
    val indigo = Color(0xFF5856D6)
    val teal = Color(0xFF30B0C7)
    val mint = Color(0xFF00C7BE)
    val cyan = Color(0xFF32ADE6)
    val brown = Color(0xFFA2845E)
    val yellow = Color(0xFFFFCC00)
    val gray = Color(0xFF8E8E93)
    val kotlin = Color(0xFF7F52FF)
    val groupedBackground = Color(0xFFF2F2F7)
    val card = Color.White
    val secondary = Color(0xFF6C6C70)
    val tertiary = Color(0xFFAEAEB2)
    val label = Color(0xFF111114)
}

@Composable
fun MasterFabricTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.orange,
            onPrimary = Color.White,
            secondary = Palette.kotlin,
            background = Palette.groupedBackground,
            surface = Palette.groupedBackground,
            surfaceContainer = Color.White,
            onBackground = Palette.label,
            onSurface = Palette.label,
            onSurfaceVariant = Palette.secondary,
        ),
        content = content,
    )
}

fun tintColor(key: String): Color = when (key) {
    "orange" -> Palette.orange
    "blue" -> Palette.blue
    "green" -> Palette.green
    "purple" -> Palette.purple
    "teal" -> Palette.teal
    "pink" -> Palette.pink
    "indigo" -> Palette.indigo
    "red" -> Palette.red
    "mint" -> Palette.mint
    "brown" -> Palette.brown
    "cyan" -> Palette.cyan
    else -> Palette.gray
}

fun itemColor(category: String): Color = when (category) {
    "Mobile" -> Palette.blue
    "Embedded" -> Palette.green
    "Robotics" -> Palette.purple
    "Tools" -> Palette.teal
    "Games" -> Palette.pink
    else -> Palette.orange
}

fun categoryColor(c: String): Color = when (c) {
    "Image" -> Palette.pink
    "Video" -> Palette.purple
    "3D" -> Palette.indigo
    "Math" -> Palette.blue
    "Data" -> Palette.teal
    "Text" -> Palette.green
    "Crypto" -> Palette.orange
    "Algorithms" -> Palette.red
    else -> Palette.gray
}

fun ratioColor(r: Double): Color = if (r > 1.05) Palette.orange else if (r < 0.95) Palette.kotlin else Palette.secondary
