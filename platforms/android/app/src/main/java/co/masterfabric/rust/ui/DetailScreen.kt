package co.masterfabric.rust.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.masterfabric.rust.bridge.CoreBridge
import co.masterfabric.rust.core.Action
import co.masterfabric.rust.core.DetailState

/** Page 2: detail. The favorite rule and related items are computed in Rust. */
@Composable
fun DetailScreen(bridge: CoreBridge, detail: DetailState) {
    val item = detail.item
    Column(Modifier.fillMaxSize()) {
        TopBar(item.title, back = { bridge.dispatch(Action.Back) })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(item.symbol, itemColor(item.category), 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(item.category.uppercase(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = itemColor(item.category)))
                    Text(item.title, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.label))
                    Text(item.subtitle, style = Type.subheadSecondary)
                }
            }
            Text(item.body, style = Type.body)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.tags.forEach { tag ->
                    Text(
                        "#$tag",
                        style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Palette.label),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Palette.orange.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            Button(
                onClick = { bridge.dispatch(Action.ToggleFavorite(item.id)) },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.orange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(if (item.favorite) Icons.Filled.StarBorder else Icons.Filled.Star, null)
                Spacer(Modifier.width(8.dp))
                Text(detail.favoriteLabel, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            }
            if (detail.related.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("More in this category", style = Type.headline)
                    detail.related.forEach { row ->
                        ItemRowView(
                            row,
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .clickable { bridge.dispatch(Action.OpenDetail(row.id)) }
                                .padding(12.dp),
                        )
                    }
                }
            }
            Text("All data on this screen comes from the Rust `DetailViewModel` · ${detail.key}", style = Type.monoCaption.copy(color = Palette.tertiary))
            Spacer(Modifier.size(8.dp))
        }
    }
}
