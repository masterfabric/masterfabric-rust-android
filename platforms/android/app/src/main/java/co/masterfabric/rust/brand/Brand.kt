package co.masterfabric.rust.brand

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.masterfabric.rust.R
import co.masterfabric.rust.ui.Palette
import kotlin.math.cos
import kotlin.math.sin

/**
 * MasterFabric textmark, drawn the same way as the iOS twin: a typographic wordmark
 * (vector by nature) rather than a bitmap. Body/medium with wide tracking.
 */
@Composable
fun Wordmark(navigation: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        "MASTERFABRIC",
        maxLines = 1,
        textAlign = TextAlign.Center,
        style = TextStyle(
            fontSize = if (navigation) 15.sp else 17.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = if (navigation) 4.sp else 6.sp,
            color = Palette.label,
        ),
        modifier = modifier
            .then(if (navigation) Modifier else Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp))
            .testTag(if (navigation) "wordmark-nav" else "wordmark"),
    )
}

/** Kotlin logo mark (geometric "K" square), drawn with a Canvas path, Kotlin brand gradient. */
@Composable
private fun KotlinMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2f, h / 2f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(p, Color.White)
    }
}

/** Home hero: "Kotlin + Rust = 💥" (Android twin of `SwiftRustHero`). */
@Composable
fun KotlinRustHero() {
    val t = rememberInfiniteTransition(label = "boom")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val kotlinGradient = Brush.linearGradient(listOf(Color(0xFFE44857), Color(0xFFC711E1), Color(0xFF7F52FF)))
    val rustGradient = Brush.linearGradient(listOf(Color(0xFFB8452A), Color(0xFF5C2112)))
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        Wordmark()
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Kotlin plus Rust equals boom" }
                .testTag("hero-equation"),
        ) {
            Tile("Kotlin", kotlinGradient) { KotlinMark(Modifier.size(28.dp)) }
            Op(Icons.Filled.Add)
            Tile("Rust", rustGradient) {
                Icon(painterResource(R.drawable.rust_logo), null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Op(Icons.Filled.DragHandle)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Burst(
                        Modifier
                            .size(64.dp)
                            .rotate(-8f + 16f * pulse)
                            .scale(0.94f + 0.12f * pulse),
                    )
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White.copy(alpha = 0.7f + 0.3f * pulse), modifier = Modifier.size(22.dp))
                }
                Text("Boom", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary))
            }
        }
        Text(
            "Native Jetpack Compose on a shared Rust core",
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.label),
        )
        Text(
            "State, navigation, data and business rules run in Rust. Compose only renders.",
            textAlign = TextAlign.Center,
            style = TextStyle(fontSize = 12.sp, color = Palette.secondary),
        )
    }
}

@Composable
private fun Burst(modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension / 2f
        val inner = outer * 0.62f
        val n = 12
        val p = Path()
        for (i in 0 until n * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.PI * i / n - Math.PI / 2
            val x = c.x + r * cos(a).toFloat()
            val y = c.y + r * sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        drawPath(p, Brush.verticalGradient(listOf(Palette.yellow, Palette.orange, Palette.red)))
    }
}

@Composable
private fun Op(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, null, tint = Palette.secondary, modifier = Modifier.padding(top = 22.dp).size(20.dp))
}

@Composable
private fun Tile(label: String, fill: Brush, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(64.dp)
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) { content() }
        Text(label, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary))
    }
}
