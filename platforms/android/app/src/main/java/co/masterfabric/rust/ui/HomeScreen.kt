package co.masterfabric.rust.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.masterfabric.rust.brand.KotlinRustHero
import co.masterfabric.rust.brand.Wordmark
import co.masterfabric.rust.bridge.CoreBridge
import co.masterfabric.rust.core.Action
import co.masterfabric.rust.core.ItemRow

/** Page 1: list. Search, sorting and favorite rules live in Rust. */
@Composable
fun HomeScreen(bridge: CoreBridge) {
    val home = bridge.home ?: return
    Column(Modifier.fillMaxSize()) {
        TopBar(home.title, back = null, center = { Wordmark(navigation = true) }) {
            IconButton(onClick = { bridge.dispatch(Action.OpenInfo) }) { Icon(Icons.Filled.Info, "Rust guide", tint = Palette.orange) }
            IconButton(onClick = { bridge.dispatch(Action.OpenBenchmark) }) { Icon(Icons.Filled.Speed, "Benchmark", tint = Palette.orange) }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("home-list"),
        ) {
            item("hero") {
                Group {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { KotlinRustHero() }
                    Divider()
                    RevisionRow(bridge.revision)
                }
            }
            item("nav") {
                Group {
                    NavRow("speedometer", Palette.orange, "Benchmark: Rust vs Kotlin", "36 tests in 8 categories · real measurements", "open-benchmark") {
                        bridge.dispatch(Action.OpenBenchmark)
                    }
                    Divider()
                    NavRow("lightbulb.max.fill", Palette.indigo, "What can you build with Rust?", "Infographic: projects, architecture, measurements", "open-info") {
                        bridge.dispatch(Action.OpenInfo)
                    }
                }
            }
            item("search") {
                OutlinedTextField(
                    value = home.query,
                    onValueChange = { bridge.dispatch(Action.SetQuery(it)) },
                    placeholder = { Text("Search: embassy, mobile, robot…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Palette.orange,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("search"),
                )
            }
            item("header") {
                val fav = if (home.favoriteCount > 0u) " · ★ ${home.favoriteCount}" else ""
                Text(
                    "RUST PROJECTS · ${home.rows.size}/${home.totalCount}$fav",
                    style = Type.caption,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
            home.emptyMessage?.let { msg ->
                item("empty") {
                    Group { Text(msg, style = Type.subheadSecondary, modifier = Modifier.padding(24.dp).fillMaxWidth()) }
                }
            }
            items(home.rows, key = { "item-${it.id}" }) { row ->
                Group {
                    ItemRowView(row, Modifier.clickable { bridge.dispatch(Action.OpenDetail(row.id)) }.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
    ) { content() }
}

@Composable
fun Divider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(Color(0x22000000)),
    )
}

@Composable
private fun NavRow(symbol: String, color: Color, title: String, subtitle: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(symbol, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline)
            Text(subtitle, style = Type.subheadSecondary)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Palette.tertiary)
    }
}

/** Live proof that the screen is driven by Rust state. */
@Composable
fun RevisionRow(revision: ULong) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Settings, null, tint = Palette.orange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Shared core · native UI", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
        Spacer(Modifier.weight(1f))
        Text("state rev #$revision", style = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Palette.secondary), modifier = Modifier.testTag("revision"))
    }
}

@Composable
fun ItemRowView(row: ItemRow, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconTile(row.symbol, itemColor(row.category))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, style = Type.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (row.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, null, tint = Palette.yellow, modifier = Modifier.size(14.dp))
                }
            }
            Text(row.subtitle, style = Type.subheadSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.category.uppercase(), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = itemColor(row.category)))
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Palette.tertiary)
    }
}
