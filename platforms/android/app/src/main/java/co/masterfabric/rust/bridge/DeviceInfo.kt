package co.masterfabric.rust.bridge

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

/** Device / build info shown on the benchmark screen (Android twin of iOS `DeviceInfo`). */
class DeviceInfo(context: Context) {
    val isEmulator: Boolean = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
        Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone") ||
        Build.PRODUCT.contains("sdk")

    /** Kotlin/ART build: "Debuggable" builds run with ART's debug restrictions. */
    val kotlinBuild: String =
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) "Debuggable" else "Release"

    val deviceName: String = if (isEmulator) "Emulator ${Build.MODEL}" else "${Build.MANUFACTURER} ${Build.MODEL}"
    val os: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val abi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "?"
    val machine: String = "${Build.HARDWARE} · ${Build.BOARD} · $abi"
    val cores: Int = Runtime.getRuntime().availableProcessors()
    val memoryGB: String = run {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        String.format(java.util.Locale.ROOT, "%.0f GB", mi.totalMem / 1_073_741_824.0)
    }
    val vm: String = "ART " + (System.getProperty("java.vm.version") ?: "?")
}
