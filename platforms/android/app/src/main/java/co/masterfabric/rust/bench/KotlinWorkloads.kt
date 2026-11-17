package co.masterfabric.rust.bench

import org.json.JSONArray
import org.json.JSONObject

// One-to-one Kotlin port of `crates/app-core/src/workloads.rs`.
// Same algorithm, same data, same checksum. Idiomatic Kotlin (no JNI, no Unsafe);
// the Rust side is idiomatic Rust too: a "language + standard library" comparison.

/** One measurement on the Kotlin side (converted to the UniFFI record by the runner). */
class KMeasurement(val avgMs: Double, val minMs: Double, val iterations: Int, val checksum: Long)

object KotlinWorkloads {
    fun runOnce(id: String, param: Long): Long? = when (id) {
        "fib" -> fib(param.toInt())
        "sort" -> sortInts(param.toInt())
        "sha256" -> sha256Buffer(param.toInt())
        "json" -> jsonRoundtrip(param.toInt())
        "sieve" -> primeSieve(param.toInt())
        "matmul" -> matmul(param.toInt())
        "hashmap" -> hashmapCount(param.toInt())
        "raytrace" -> KotlinMedia.raytraceBench(param.toInt())
        "raster" -> KotlinMedia.rasterize(param.toInt())
        "image" -> KotlinMedia.imagePipeline(param.toInt())
        "video" -> KotlinMedia.videoPipeline(param.toInt())
        "mesh" -> KotlinMedia.generateMesh(param.toInt()).checksum
        else -> KotlinHeavy.run(id, param)
    }

    /** 1 warm-up + `iterations` measured runs (same as Rust `measure`). */
    fun measure(id: String, param: Long, iterations: Int): KMeasurement? {
        val checksum = runOnce(id, param) ?: return null
        val n = maxOf(iterations, 1)
        var total = 0.0
        var best = Double.MAX_VALUE
        repeat(n) {
            val t0 = System.nanoTime()
            val c = runOnce(id, param) ?: return null
            val ms = (System.nanoTime() - t0) / 1_000_000.0
            Sink.consume(c)
            total += ms
            best = minOf(best, ms)
        }
        return KMeasurement(total / n, best, iterations, checksum)
    }

    // ------------------------------------------------------------ Workloads

    fun fib(n: Int): Long = if (n < 2) n.toLong() else fib(n - 1) + fib(n - 2)

    fun sortInts(n: Int): Long {
        val rng = XorShift32(0x1234_5678)
        // u32 values: flip the sign bit so the JVM's signed IntArray.sort() orders them as unsigned.
        val v = IntArray(n) { rng.next() xor Int.MIN_VALUE }
        v.sort()
        var acc = 0L
        var i = 0
        while (i < n) {
            acc = acc * 31 + u32(v[i] xor Int.MIN_VALUE)
            i += 1000
        }
        return acc xor u32(v[n - 1] xor Int.MIN_VALUE)
    }

    fun sha256Buffer(n: Int): Long {
        val data = ByteArray(n) { (it * 31 + 7).toByte() }
        val d = Sha256Kotlin.hash(data)
        var out = 0L
        for (k in 0 until 8) out = (out shl 8) or (d[k].toLong() and 0xff)
        return out
    }

    fun jsonRoundtrip(n: Int): Long {
        // org.json is the JSON library that ships with Android (the platform's standard one).
        val arr = JSONArray()
        for (i in 0 until n) {
            val o = JSONObject()
            o.put("id", i)
            o.put("name", "item-$i")
            o.put("score", i * 0.5)
            o.put("active", i % 2 == 0)
            o.put("tags", JSONArray(listOf("rust", "kotlin", "t${i % 10}")))
            arr.put(o)
        }
        val text = arr.toString()
        val parsed = JSONArray(text)
        var acc = 0L
        for (k in 0 until parsed.length()) {
            val r = parsed.getJSONObject(k)
            acc += r.getLong("id") + (if (r.getBoolean("active")) 1 else 0) +
                r.getJSONArray("tags").length() + r.getDouble("score").toLong()
            // `name` is decoded too (as serde / Codable do).
            Sink.consume(r.getString("name").length.toLong())
        }
        return acc
    }

    fun primeSieve(n: Int): Long {
        val isComposite = BooleanArray(n + 1)
        var count = 0L
        var i = 2
        while (i <= n) {
            if (!isComposite[i]) {
                count++
                var j = i.toLong() * i
                while (j <= n) {
                    isComposite[j.toInt()] = true
                    j += i
                }
            }
            i++
        }
        return count
    }

    fun matmul(n: Int): Long {
        val a = DoubleArray(n * n)
        val b = DoubleArray(n * n)
        for (i in 0 until n) {
            for (j in 0 until n) {
                a[i * n + j] = ((i * j) % 7) + 0.5
                b[i * n + j] = ((i + j) % 5) - 1.0
            }
        }
        val c = DoubleArray(n * n)
        for (i in 0 until n) {
            for (k in 0 until n) {
                val aik = a[i * n + k]
                for (j in 0 until n) c[i * n + j] += aik * b[k * n + j]
            }
        }
        return c.sum().toLong()
    }

    fun hashmapCount(n: Int): Long {
        val rng = XorShift32(0xC0FF_EE11.toInt())
        val map = HashMap<Int, Int>()
        repeat(n) {
            val k = umod(rng.next(), 65_536)
            map[k] = (map[k] ?: 0) + 1
        }
        val mx = map.values.maxOrNull() ?: 0
        return (map.size.toLong() shl 32) or u32(mx)
    }
}

/** Hand-written SHA-256 (same as the Rust version); java.security is not used (it may be
 *  hardware accelerated and would skew a language comparison). */
object Sha256Kotlin {
    private val k = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun hash(data: ByteArray): ByteArray {
        val h = intArrayOf(
            0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6,
            0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19,
        )
        val w = IntArray(64)
        val full = data.size / 64
        for (blk in 0 until full) compress(h, data, blk * 64, w)
        val rem = data.size - full * 64
        val tail = ByteArray(128)
        System.arraycopy(data, full * 64, tail, 0, rem)
        tail[rem] = 0x80.toByte()
        val total = if (rem < 56) 64 else 128
        val bitLen = data.size.toLong() * 8
        for (i in 0 until 8) tail[total - 1 - i] = (bitLen ushr (i * 8)).toByte()
        compress(h, tail, 0, w)
        if (total == 128) compress(h, tail, 64, w)
        val out = ByteArray(32)
        for (i in 0 until 8) {
            val v = h[i]
            out[i * 4] = (v ushr 24).toByte()
            out[i * 4 + 1] = (v ushr 16).toByte()
            out[i * 4 + 2] = (v ushr 8).toByte()
            out[i * 4 + 3] = v.toByte()
        }
        return out
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun rotr(x: Int, n: Int): Int = (x ushr n) or (x shl (32 - n))

    private fun compress(h: IntArray, block: ByteArray, off: Int, w: IntArray) {
        for (i in 0 until 16) {
            val j = off + i * 4
            w[i] = ((block[j].toInt() and 0xff) shl 24) or ((block[j + 1].toInt() and 0xff) shl 16) or
                ((block[j + 2].toInt() and 0xff) shl 8) or (block[j + 3].toInt() and 0xff)
        }
        for (i in 16 until 64) {
            val s0 = rotr(w[i - 15], 7) xor rotr(w[i - 15], 18) xor (w[i - 15] ushr 3)
            val s1 = rotr(w[i - 2], 17) xor rotr(w[i - 2], 19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
        var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
        for (i in 0 until 64) {
            val s1 = rotr(e, 6) xor rotr(e, 11) xor rotr(e, 25)
            val ch = (e and f) xor (e.inv() and g)
            val t1 = hh + s1 + ch + k[i] + w[i]
            val s0 = rotr(a, 2) xor rotr(a, 13) xor rotr(a, 22)
            val maj = (a and b) xor (a and c) xor (b and c)
            val t2 = s0 + maj
            hh = g; g = f; f = e; e = d + t1
            d = c; c = b; b = a; a = t1 + t2
        }
        h[0] += a; h[1] += b; h[2] += c; h[3] += d
        h[4] += e; h[5] += f; h[6] += g; h[7] += hh
    }
}
