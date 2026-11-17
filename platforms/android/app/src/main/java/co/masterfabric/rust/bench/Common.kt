package co.masterfabric.rust.bench

import kotlin.math.abs
import kotlin.math.truncate

/** Same xorshift32 generator as the Rust core (state carried as the bit pattern of a u32). */
class XorShift32(seed: Int) {
    private var state = seed

    fun next(): Int {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        state = x
        return x
    }
}

const val U32_MASK = 0xffffffffL

/** `u32 % m` for a positive `m`. */
@Suppress("NOTHING_TO_INLINE")
inline fun umod(x: Int, m: Int): Int = ((x.toLong() and U32_MASK) % m).toInt()

/** `x as u64` for a u32 bit pattern. */
@Suppress("NOTHING_TO_INLINE")
inline fun u32(x: Int): Long = x.toLong() and U32_MASK

/** Rust `f64::round` / Swift `.rounded()`: half away from zero, as an i64. */
fun roundHalfAway(x: Double): Long {
    val t = truncate(x)
    return if (abs(x - t) >= 0.5) (t + (if (x < 0) -1.0 else 1.0)).toLong() else t.toLong()
}

/** Rust `x as u64` for an f64 (saturating, NaN → 0), returned as the u64 bit pattern. */
fun f64ToU64(x: Double): Long {
    if (x.isNaN() || x <= 0.0) return 0L
    if (x >= 18446744073709551615.0) return -1L
    return if (x < 9.223372036854775807E18) x.toLong() else (x - 9.223372036854775807E18).toLong() + Long.MIN_VALUE
}

/** Prevents results from being optimized away. */
object Sink {
    @Volatile
    @JvmField
    var value: Long = 0L

    fun consume(x: Long) { value = value xor x }
}
