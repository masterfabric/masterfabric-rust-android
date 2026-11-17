package co.masterfabric.rust.bench

import co.masterfabric.rust.core.FfiPoint
import co.masterfabric.rust.core.ffiMix
import co.masterfabric.rust.core.ffiMixBatch
import co.masterfabric.rust.core.ffiPointMix
import co.masterfabric.rust.core.ffiSampleStrings
import co.masterfabric.rust.core.ffiSampleVec
import co.masterfabric.rust.core.ffiStrMix
import co.masterfabric.rust.core.ffiVecMix

/**
 * Test #37: FFI boundary ("chatty calls"). Kotlin twin of `crates/app-core/src/ffi_bench.rs`.
 *
 * The 36 compute tests time each workload inside its own language, so the UniFFI crossing
 * (one per test) is outside the timer. Here the crossing *is* the workload: Kotlin calls a
 * tiny Rust function once per operation. On Android a UniFFI call goes Kotlin → JNA →
 * libffi → the Rust `extern "C"` symbol, so this measures JNA's direct-call path too.
 * Timing must happen on the Kotlin side because only Kotlin can observe the Kotlin→Rust
 * call; which cases run, with which parameters, and how the results are interpreted is
 * decided in Rust.
 *
 * Every sample: `reps` repetitions of `calls` chained operations, seeded with `0x5EED ^ rep`,
 * XOR-folded into a checksum that must equal Rust's `reference_checksum`.
 */
object FfiBench {
    const val PLATFORM_INLINE = "platform_inline"
    const val RUST_PER_CALL = "rust_per_call"
    const val RUST_BATCHED = "rust_batched"

    // ------------------------------------------------------------ tiny ops (identical to Rust)

    const val GOLDEN = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

    @Suppress("NOTHING_TO_INLINE")
    inline fun mix(acc: Long, i: Long): Long {
        val x = (acc xor i) * GOLDEN
        return (x shl 5) or (x ushr 59)
    }

    /** The sample strings are ASCII, so UTF-16 chars == UTF-8 bytes (no per-call encode). */
    fun strMix(s: String, seed: Long): Long {
        var a = seed
        for (ch in s) a = mix(a, ch.code.toLong())
        return a
    }

    fun pointMix(p: FfiPoint): Long =
        mix(mix(mix(0, u32(p.x)), u32(p.y)), u32(p.z))

    fun vecMix(v: IntArray, seed: Long): Long {
        var a = seed
        for (x in v) a = mix(a, u32(x))
        return a
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun point(i: Long): FfiPoint {
        val x = i.toInt()
        return FfiPoint(x, x * 3, -x)
    }

    /** Same inputs as Rust (fetched from Rust once, outside any timed region). */
    private val strings: List<String> by lazy { ffiSampleStrings() }
    private val vecU: List<UInt> by lazy { ffiSampleVec() }
    private val vecInts: IntArray by lazy { IntArray(vecU.size) { vecU[it].toInt() } }

    // ------------------------------------------------------------ one sample

    fun sample(caseId: String, variant: String, calls: Long, reps: Long): Long? {
        var total = 0L
        val strings = strings
        val vecU = vecU
        val vecInts = vecInts
        when (caseId to variant) {
            "u64" to PLATFORM_INLINE -> for (r in 0 until reps) {
                var a = 0x5EEDL xor r
                for (i in 0 until calls) a = mix(a, i)
                total = total xor a
            }
            "u64" to RUST_PER_CALL -> for (r in 0 until reps) {
                var a = (0x5EEDL xor r).toULong()
                for (i in 0 until calls) a = ffiMix(a, i.toULong())
                total = total xor a.toLong()
            }
            "u64" to RUST_BATCHED -> for (r in 0 until reps) {
                total = total xor ffiMixBatch((0x5EEDL xor r).toULong(), calls.toULong()).toLong()
            }
            "string" to PLATFORM_INLINE -> for (r in 0 until reps) {
                var a = 0x5EEDL xor r
                for (i in 0 until calls) a = mix(a, strMix(strings[(i and 15).toInt()], i))
                total = total xor a
            }
            "string" to RUST_PER_CALL -> for (r in 0 until reps) {
                var a = 0x5EEDL xor r
                for (i in 0 until calls) a = mix(a, ffiStrMix(strings[(i and 15).toInt()], i.toULong()).toLong())
                total = total xor a
            }
            "record" to PLATFORM_INLINE -> for (r in 0 until reps) {
                var a = 0x5EEDL xor r
                for (i in 0 until calls) a = mix(a, pointMix(point(i)))
                total = total xor a
            }
            "record" to RUST_PER_CALL -> for (r in 0 until reps) {
                var a = 0x5EEDL xor r
                for (i in 0 until calls) a = mix(a, ffiPointMix(point(i)).toLong())
                total = total xor a
            }
            "vec16" to PLATFORM_INLINE -> for (r in 0 until reps) {
                var a = 0x5EEDL xor r
                for (i in 0 until calls) a = mix(a, vecMix(vecInts, i))
                total = total xor a
            }
            "vec16" to RUST_PER_CALL -> for (r in 0 until reps) {
                var a = 0x5EEDL xor r
                for (i in 0 until calls) a = mix(a, ffiVecMix(vecU, i.toULong()).toLong())
                total = total xor a
            }
            else -> return null
        }
        return total
    }

    class Sample(val nsPerCall: Double, val checksum: Long)

    /** 1 untimed warm-up sample + `samples` timed ones; median ns per operation. */
    fun measure(caseId: String, variant: String, calls: Long, reps: Long, samples: Int): Sample? {
        val checksum = sample(caseId, variant, calls, reps) ?: return null
        val times = ArrayList<Double>()
        repeat(maxOf(samples, 1)) {
            val t0 = System.nanoTime()
            val c = sample(caseId, variant, calls, reps) ?: return null
            val ns = (System.nanoTime() - t0).toDouble()
            Sink.consume(c)
            if (c != checksum) return Sample(ns, c)
            times.add(ns / (calls * reps).toDouble())
        }
        times.sort()
        return Sample(times[times.size / 2], checksum)
    }
}
