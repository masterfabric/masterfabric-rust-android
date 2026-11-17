package co.masterfabric.rust.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.masterfabric.rust.bridge.CoreBridge
import co.masterfabric.rust.core.Action

/**
 * The navigation stack comes from Rust; Compose only renders the screen for the top key.
 * System back / the back arrow send `Action.Back` to Rust.
 */
@Composable
fun RootView(bridge: CoreBridge) {
    val stack = bridge.stack
    val depth = stack.size
    val key = stack.lastOrNull()?.key ?: "home"
    BackHandler(enabled = depth > 1) { bridge.dispatch(Action.Back) }
    val prevDepth = remember { intArrayOf(depth) }
    val forward = depth >= prevDepth[0]
    prevDepth[0] = depth
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.groupedBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AnimatedContent(
            targetState = key,
            transitionSpec = {
                if (forward) {
                    (slideInHorizontally(tween(260)) { it } + fadeIn()) togetherWith (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally(tween(260)) { it } + fadeOut())
                }
            },
            label = "nav",
        ) { k ->
            when {
                k == "home" -> HomeScreen(bridge)
                k == "benchmark" -> BenchmarkScreen(bridge)
                k == "info" -> InfoScreen(bridge)
                else -> bridge.detail(k)?.let { DetailScreen(bridge, it) } ?: Column(Modifier.fillMaxSize()) {
                    TopBar("Screen not found", back = { bridge.dispatch(Action.Back) })
                    Text(k, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun TopBar(title: String, back: (() -> Unit)?, center: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Palette.groupedBackground),
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.orange) }
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            }
        }
        Box(Modifier.align(Alignment.Center).padding(horizontal = 96.dp)) {
            if (center != null) {
                center()
            } else {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
