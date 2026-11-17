package co.masterfabric.rust.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.masterfabric.rust.brand.Wordmark
import co.masterfabric.rust.bridge.CoreBridge
import co.masterfabric.rust.core.Action
import co.masterfabric.rust.core.ArchStep
import co.masterfabric.rust.core.ProjectCategory

/** "What can you build with Rust?" infographic. All content comes from the Rust `InfoViewModel`;
 *  numbers in the chart are only this app's own benchmark measurements. */
@Composable
fun InfoScreen(bridge: CoreBridge) {
    val info = bridge.info ?: return
    Column(Modifier.fillMaxSize()) {
        TopBar("Rust guide", back = { bridge.dispatch(Action.Back) })
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            item("wordmark") { Wordmark() }
            item("hero") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Palette.orange, Palette.red.copy(alpha = 0.85f))))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(info.title, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White))
                    }
                    Text(info.intro, style = TextStyle(fontSize = 15.sp, color = Color.White))
                }
            }
            item("arch-title") { SectionTitle("This app's architecture", "arrow.up.arrow.down") }
            item("arch") { ArchitectureDiagram(info.architecture) }
            item("projects-title") { SectionTitle("What you can build with Rust", "square.grid.3x3.fill") }
            items(info.categories.chunked(2), key = { "cat-" + it.first().id }) { pair ->
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { CategoryCard(it, Modifier.weight(1f).fillMaxHeight()) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item("measured-title") { SectionTitle("Measured on this device", "speedometer") }
            item("measured") {
                Card {
                    Text(info.measuredNote, style = Type.caption)
                    Spacer(Modifier.height(10.dp))
                    if (info.measured.isEmpty()) {
                        Button(onClick = { bridge.dispatch(Action.OpenBenchmark) }, colors = ButtonDefaults.buttonColors(containerColor = Palette.orange), modifier = Modifier.fillMaxWidth()) {
                            Text("Open benchmark")
                        }
                    } else {
                        HBarChart(info.measured.map { Bar(shortName(it.benchId, it.name), it.ratio, categoryColor(it.category), f("%.2f×", it.ratio)) }, rule = 1.0, rowHeight = 22)
                        Spacer(Modifier.height(8.dp))
                        Legend(allCategories.map { it to categoryColor(it) })
                    }
                }
            }
            item("perf-title") { SectionTitle("App performance", "speedometer") }
            item("perf") { AppPerfCard(bridge) }
            item("bullets") {
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BulletCard("Strengths", Icons.Filled.CheckCircle, Palette.green, info.strengths, Modifier.weight(1f).fillMaxHeight())
                    BulletCard("Trade-offs", Icons.Filled.Error, Palette.orange, info.tradeoffs, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Compose renders → dispatch(Action) → Rust state → partial ViewUpdate → Compose again. */
@Composable
private fun ArchitectureDiagram(steps: List<ArchStep>) {
    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(listOf("Kotlin (native UI)" to Palette.kotlin, "Rust (core)" to Palette.orange))
        }
        Spacer(Modifier.height(10.dp))
        steps.forEachIndexed { i, step ->
            val color = if (step.layer == "rust") Palette.orange else Palette.kotlin
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f))
                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(step.symbol, color, 38.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(step.title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = Palette.label))
                    Text(step.detail, style = Type.caption)
                }
            }
            if (i < steps.size - 1) {
                Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ArrowDownward, null, tint = Palette.secondary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text("↺ the last step feeds back into the first", style = Type.caption2, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CategoryCard(c: ProjectCategory, modifier: Modifier) {
    val color = tintColor(c.tint)
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Palette.card).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTile(c.symbol, color, 44.dp)
        Text(c.title, style = Type.headline)
        Text(c.summary, style = Type.caption)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            c.examples.forEach { ex ->
                Row {
                    Icon(Icons.Filled.Check, null, tint = color, modifier = Modifier.size(12.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ex, style = TextStyle(fontSize = 11.sp, color = Palette.label))
                }
            }
        }
        Text(c.crates.joinToString(" · "), style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = color))
    }
}

@Composable
private fun BulletCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, items: List<String>, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Palette.card).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color))
        }
        items.forEach { Text("• $it", style = TextStyle(fontSize = 12.sp, color = Palette.label)) }
    }
}
