package co.masterfabric.rust.bench

import android.os.Looper
import co.masterfabric.rust.core.BenchMeasurement
import co.masterfabric.rust.core.FfiSample
import co.masterfabric.rust.core.PlatformBenchRunner

/**
 * Object the Rust core calls to measure the same workload in Kotlin (the Android twin of the
 * iOS `SwiftRunner`). Rust calls it from its own background thread; the main thread is never
 * blocked.
 */
class KotlinRunner : PlatformBenchRunner {
    override fun language(): String = "Kotlin"

    override fun run(benchId: String, param: ULong, iterations: UInt): BenchMeasurement? {
        check(Looper.myLooper() != Looper.getMainLooper()) { "benchmark must not run on the main thread" }
        val m = KotlinWorkloads.measure(benchId, param.toLong(), iterations.toInt()) ?: return null
        return BenchMeasurement(m.avgMs, m.minMs, m.iterations.toUInt(), m.checksum.toULong())
    }

    /** Test #37: Rust asks Kotlin to time one FFI-boundary variant (Kotlin is the caller,
     *  so the Kotlin→Rust crossing can only be observed here). */
    override fun runFfi(caseId: String, variant: String, calls: ULong, reps: ULong, samples: UInt): FfiSample? {
        check(Looper.myLooper() != Looper.getMainLooper()) { "benchmark must not run on the main thread" }
        val s = FfiBench.measure(caseId, variant, calls.toLong(), reps.toLong(), samples.toInt()) ?: return null
        return FfiSample(s.nsPerCall, s.checksum.toULong())
    }
}
