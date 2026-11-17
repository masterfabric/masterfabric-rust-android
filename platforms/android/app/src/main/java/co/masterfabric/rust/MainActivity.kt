package co.masterfabric.rust

import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import co.masterfabric.rust.bridge.Perf
import co.masterfabric.rust.ui.MasterFabricTheme
import co.masterfabric.rust.ui.RootView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bridge = (application as MasterFabricApp).bridge
        if (Perf.launchToFirstFrameMs == null) {
            // First Choreographer frame after setContent = the frame that draws the root view.
            Choreographer.getInstance().postFrameCallback {
                if (Perf.launchToFirstFrameMs == null) Perf.launchToFirstFrameMs = Perf.sinceProcessStartMs()
            }
        }
        val launchIntent = intent
        setContent {
            MasterFabricTheme {
                RootView(bridge)
            }
            LaunchedEffect(Unit) {
                // Let the first frame land so launch metrics exist before they are recorded.
                kotlinx.coroutines.delay(300)
                bridge.applyLaunchOptions(launchIntent)
            }
        }
    }
}
