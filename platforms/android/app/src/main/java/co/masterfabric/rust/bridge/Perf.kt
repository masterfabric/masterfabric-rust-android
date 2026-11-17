package co.masterfabric.rust.bridge

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * App-level, platform-specific performance measurements (Android twin of the iOS `Perf`).
 * Results are sent to Rust via `Action.RecordPerf` and rendered from Rust state.
 */
object Perf {
    fun now(): Long = System.nanoTime()
    fun us(start: Long): Double = (now() - start) / 1000.0

    /** Process start → now, ms (Process.getStartUptimeMillis is the zygote fork time). */
    fun sinceProcessStartMs(): Double = (SystemClock.uptimeMillis() - Process.getStartUptimeMillis()).toDouble()

    var coreInitUs: Double = 0.0
    var launchToFirstFrameMs: Double? = null

    /**
     * Writes JSON to the app's external files dir (`/sdcard/Android/data/<pkg>/files/<file>`),
     * which `adb pull` can read without root or `run-as` (the benchmark build is not debuggable).
     * The same JSON is also logged under the `MFBench` tag.
     */
    fun export(context: Context, json: JSONObject, file: String) {
        val text = json.toString(2)
        Log.i("MFBench", "$file ${json}")
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        runCatching {
            val tmp = File(dir, "$file.tmp")
            tmp.writeText(text)
            tmp.renameTo(File(dir, file))
        }.onFailure { Log.w("MFBench", "export failed", it) }
    }
}
