package co.masterfabric.rust.bridge

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.masterfabric.rust.bench.KotlinRunner
import co.masterfabric.rust.core.Action
import co.masterfabric.rust.core.AppCore
import co.masterfabric.rust.core.BenchmarkState
import co.masterfabric.rust.core.DetailState
import co.masterfabric.rust.core.HomeState
import co.masterfabric.rust.core.InfoState
import co.masterfabric.rust.core.NavEntry
import co.masterfabric.rust.core.PerfMetric
import co.masterfabric.rust.core.StateListener
import co.masterfabric.rust.core.ViewUpdate
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Compose-side mirror of the Rust state (Android twin of the iOS `CoreBridge`).
 *
 * No business logic lives here: user events go to Rust via [dispatch], Rust sends back a
 * *partial* `ViewUpdate` (only the screen state that changed) through `StateListener`, and
 * Compose renders it. Each slice is a separate snapshot state, so a benchmark progress tick
 * does not recompose the Home list and vice versa.
 */
class CoreBridge(private val context: Context) {
    val core: AppCore
    val device = DeviceInfo(context)

    var revision by mutableStateOf(0uL); private set
    var stack by mutableStateOf(emptyList<NavEntry>()); private set
    var home by mutableStateOf<HomeState?>(null); private set
    var details by mutableStateOf(emptyList<DetailState>()); private set
    var benchmark by mutableStateOf<BenchmarkState?>(null); private set
    var info by mutableStateOf<InfoState?>(null); private set
    var perf by mutableStateOf(emptyList<PerfMetric>()); private set
    var measuringLatency by mutableStateOf(false); private set

    /** Last details, so a screen being popped keeps its content during the exit animation. */
    private val detailCache = HashMap<String, DetailState>()
    private val listener = Listener(this)
    private val runner = KotlinRunner()
    private val main = Handler(Looper.getMainLooper())

    /** Launch options (intent extras), see [applyLaunchOptions]. */
    var scrollTo by mutableStateOf<String?>(null); private set
    private var ffiAutorun = false
    private var ffiAutorunDone = false
    private var launchApplied = false

    init {
        val t0 = Perf.now()
        core = AppCore()
        Perf.coreInitUs = Perf.us(t0)
        val s = core.state()
        revision = s.revision
        stack = s.stack
        home = s.home
        details = s.details
        benchmark = s.benchmark
        info = s.info
        perf = s.perf
        core.setListener(listener)
        core.setPlatformRunner(runner)
    }

    fun dispatch(action: Action) = core.dispatch(action)

    /** Apply only the slices present in the update; untouched slices keep their identity. */
    internal fun apply(u: ViewUpdate) {
        if (u.revision < revision) return // drop out-of-order notifications
        revision = u.revision
        u.stack?.let { stack = it }
        u.home?.let { home = it }
        u.details?.let { v ->
            v.forEach { detailCache[it.key] = it }
            details = v
        }
        u.info?.let { info = it }
        u.perf?.let { perf = it }
        u.benchmark?.let { v ->
            val old = benchmark
            val finished = old != null && old.running && !v.running
            val ffiFinished = old != null && old.ffi.running && !v.ffi.running
            benchmark = v
            if (finished) {
                exportPerf()
                // `autorun` + `ffiAutorun`: run test #37 after the compute tests, so its
                // break-even uses the measured compute geomean.
                if (ffiAutorun && !ffiAutorunDone) {
                    ffiAutorunDone = true
                    main.post { dispatch(Action.RunFfiBench) }
                }
            }
            if (ffiFinished) exportFfi()
        }
    }

    fun detail(key: String): DetailState? = details.firstOrNull { it.key == key } ?: detailCache[key]

    // ------------------------------------------------------------ latency probes

    /** `n` real state changes; times `dispatch` → partial `ViewUpdate` applied to the Compose
     *  state. The listener runs synchronously on the main thread, so when `dispatch` returns
     *  the state is updated. Also times a full `state()` snapshot for comparison. */
    private fun measureDispatchLatency(n: Int = 300): Triple<Double, Double, Double> {
        val q = home?.query ?: ""
        val alt = "$q "
        val samples = DoubleArray(n)
        for (i in 0 until n) {
            val t0 = Perf.now()
            dispatch(Action.SetQuery(if (i % 2 == 0) alt else q))
            samples[i] = Perf.us(t0)
        }
        if (home?.query != q) dispatch(Action.SetQuery(q))
        samples.sort()
        val avg = samples.average()
        val p95 = samples[minOf(n - 1, (n * 0.95).toInt())]
        val t1 = Perf.now()
        repeat(n) { core.state() }
        val snap = Perf.us(t1) / n
        return Triple(avg, p95, snap)
    }

    private suspend fun nextFrameNanos(): Long = suspendCancellableCoroutine { c ->
        Choreographer.getInstance().postFrameCallback { c.resume(System.nanoTime()) }
    }

    /** dispatch → next Choreographer frame (the frame that renders the new state). */
    private suspend fun measureRenderLatency(n: Int = 20): Pair<Double, Double> {
        val q = home?.query ?: ""
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val t0 = System.nanoTime()
            dispatch(Action.SetQuery(if (i % 2 == 0) "$q " else q))
            val t1 = nextFrameNanos()
            out[i] = (t1 - t0) / 1e6
        }
        if (home?.query != q) dispatch(Action.SetQuery(q))
        out.sort()
        return out.average() to out[minOf(n - 1, (n * 0.95).toInt())]
    }

    private var lastLatency: Triple<Double, Double, Double>? = null
    private var lastRender: Pair<Double, Double>? = null

    /** Runs both probes and records the numbers into Rust (`RecordPerf`). */
    suspend fun measureAndRecordLatency() {
        if (measuringLatency) return
        measuringLatency = true
        try {
            val l = measureDispatchLatency()
            lastLatency = l
            record("dispatch_apply_avg_us", "Dispatch → state applied (avg)", l.first, "µs",
                "300 real query changes; Rust reducer + partial ViewUpdate + JNA callback + Kotlin apply")
            record("dispatch_apply_p95_us", "Dispatch → state applied (p95)", l.second, "µs", "same samples, 95th percentile")
            record("full_snapshot_us", "Full state snapshot over FFI", l.third, "µs",
                "AppCore.state(): whole AppViewState for comparison with partial updates")
            val r = measureRenderLatency()
            lastRender = r
            record("dispatch_render_ms", "Dispatch → next frame (avg)", r.first, "ms",
                "20 samples, Choreographer frame callback; includes waiting for the next vsync")
        } finally {
            measuringLatency = false
        }
    }

    fun recordLaunchMetrics() {
        Perf.launchToFirstFrameMs?.let {
            record("launch_to_first_frame_ms", "Launch → first frame", it, "ms", "process start (zygote fork) → first Compose frame")
        }
        record("core_init_us", "Rust core init", Perf.coreInitUs, "µs", "AppCore() constructor incl. seed data and JNA load")
    }

    private fun record(id: String, label: String, value: Double, unit: String, detail: String) =
        dispatch(Action.RecordPerf(id, label, value, unit, detail))

    // ------------------------------------------------------------ exports (automation)

    private fun exportPerf() {
        val b = benchmark ?: return
        val rows = JSONArray()
        for (r in b.rows) {
            val d = JSONObject().put("id", r.id).put("category", r.category)
            r.rust?.let { d.put("rust_ms", it.avgMs); d.put("rust_checksum", it.checksum.toString(16)) }
            r.rustParallel?.let { d.put("rust_par_ms", it.avgMs) }
            r.platform?.let { d.put("kotlin_ms", it.avgMs); d.put("kotlin_checksum", it.checksum.toString(16)) }
            r.ratio?.let { d.put("ratio", it) }
            r.parallelSpeedup?.let { d.put("parallel_speedup", it) }
            r.checksumMatch?.let { d.put("checksum_match", it) }
            rows.put(d)
        }
        val out = JSONObject().put("rows", rows).put("core_init_us", Perf.coreInitUs)
            .put("rust_threads", b.rustThreads.toInt()).put("emulator", device.isEmulator)
            .put("kotlin_build", device.kotlinBuild).put("device", device.deviceName).put("os", device.os)
            .put("core_info", b.coreInfo)
        b.lastTotalMs?.let { out.put("total_ms", it) }
        b.summary?.let { out.put("summary", it) }
        Perf.launchToFirstFrameMs?.let { out.put("launch_to_first_frame_ms", it) }
        lastLatency?.let {
            out.put("dispatch_apply_avg_us", it.first).put("dispatch_apply_p95_us", it.second).put("full_snapshot_us", it.third)
        }
        lastRender?.let { out.put("dispatch_render_avg_ms", it.first).put("dispatch_render_p95_ms", it.second) }
        Perf.export(context, out, "perf.json")
    }

    /** Test #37 results → ffi.json (automation, see scripts/measure-ffi.sh). */
    private fun exportFfi() {
        val f = benchmark?.ffi ?: return
        val rows = JSONArray()
        for (r in f.rows) {
            val d = JSONObject().put("id", r.id).put("group", r.group).put("case", r.caseId)
                .put("calls", r.calls.toLong()).put("reps", r.reps.toLong())
            r.platformInline?.let { d.put("kotlin_inline_ns", it.nsPerCall) }
            r.rustPerCall?.let { d.put("rust_per_call_ns", it.nsPerCall) }
            r.rustBatched?.let { d.put("rust_batched_ns", it.nsPerCall) }
            r.overheadNs?.let { d.put("overhead_ns", it) }
            r.checksumMatch?.let { d.put("checksum_match", it) }
            rows.put(d)
        }
        val out = JSONObject().put("rows", rows).put("emulator", device.isEmulator)
        f.crossingNs?.let { out.put("crossing_ns", it) }
        f.summary?.let { out.put("summary", it) }
        f.lastTotalMs?.let { out.put("total_ms", it) }
        Perf.export(context, out, "ffi.json")
    }

    // ------------------------------------------------------------ launch options
    //   adb shell am start -n co.masterfabric.rust/.MainActivity \
    //     --es startScreen home|detail|benchmark|info  --ei detailId 2  --es query embassy
    //     --ez autorun true  --ez ffiAutorun true (test #37)  --es scrollTo ffi|table|charts|perf
    //     --ez probe true

    suspend fun applyLaunchOptions(intent: Intent) {
        if (launchApplied) return
        launchApplied = true
        recordLaunchMetrics()
        val x = intent.extras
        scrollTo = x?.getString("scrollTo")
        ffiAutorun = x?.getBoolean("ffiAutorun", false) == true
        x?.getString("query")?.takeIf { it.isNotEmpty() }?.let { dispatch(Action.SetQuery(it)) }
        when (x?.getString("startScreen")) {
            "detail" -> dispatch(Action.OpenDetail(maxOf(x.getInt("detailId", 1), 1).toUInt()))
            "benchmark" -> dispatch(Action.OpenBenchmark)
            "info" -> dispatch(Action.OpenInfo)
        }
        if (x?.getBoolean("probe", false) == true) measureAndRecordLatency()
        if (x?.getBoolean("autorun", false) == true) {
            dispatch(Action.RunAllBenchmarks)
        } else if (ffiAutorun) {
            ffiAutorunDone = true
            dispatch(Action.RunFfiBench)
        }
    }

    /** Listener Rust calls from any thread; hops the update to the main thread. */
    private class Listener(bridge: CoreBridge) : StateListener {
        private val ref = WeakReference(bridge)
        private val main = Handler(Looper.getMainLooper())

        override fun onUpdate(update: ViewUpdate) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                ref.get()?.apply(update)
            } else {
                main.post { ref.get()?.apply(update) }
            }
        }
    }
}
