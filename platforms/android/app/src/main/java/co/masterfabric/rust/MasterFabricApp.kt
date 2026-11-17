package co.masterfabric.rust

import android.app.Application
import co.masterfabric.rust.bridge.CoreBridge

/** Owns the single Rust core for the process (survives Activity recreation). */
class MasterFabricApp : Application() {
    val bridge: CoreBridge by lazy { CoreBridge(this) }
}
