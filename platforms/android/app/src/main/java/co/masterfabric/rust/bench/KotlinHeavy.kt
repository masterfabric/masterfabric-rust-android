package co.masterfabric.rust.bench

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// One-to-one Kotlin port of `crates/app-core/src/heavy.rs` (single-thread versions).
// Same algorithms, same data, same checksums. Idiomatic Kotlin/JVM: primitive arrays,
// small classes, lambdas and standard collections; no JNI, no RenderScript, no SIMD intrinsics.
// u64 values are carried in Long (bit-identical wrapping arithmetic), u32 in Int.

object KotlinHeavy {
    fun run(id: String, p: Long): Long? {
        val n = p.toInt()
        return when (id) {
            "mandelbrot" -> mandelbrot(n)
            "convolve" -> convolve(n)
            "lanczos" -> lanczos(n)
            "dct" -> dct(n)
            "grade" -> grade(n)
            "pathtrace" -> pathtrace(n)
            "bvh" -> bvh(n)
            "isosurface" -> isosurface(n)
            "nbody" -> nbody(n)
            "fft" -> fft(n)
            "bigint" -> bigintFactorial(n)
            "particles" -> particles(n)
            "kmeans" -> kmeans(n)
            "lz77" -> lz77(n)
            "wordfreq" -> wordfreq(n)
            "textsearch" -> textsearch(n)
            "strsort" -> strsort(n)
            "levenshtein" -> levenshtein(n)
            "chacha20" -> chacha20(n)
            "crc32" -> crc32(n)
            "dijkstra" -> dijkstra(n)
            "bfs" -> bfs(n)
            "nqueens" -> nqueens(n)
            "sudoku" -> sudoku(n)
            else -> null
        }
    }

    // ------------------------------------------------------------ helpers

    @Suppress("NOTHING_TO_INLINE")
    private inline fun q(x: Double, s: Double): Long = roundHalfAway(x * s)

    @Suppress("NOTHING_TO_INLINE")
    private inline fun rnd01(r: XorShift32): Double = (r.next() ushr 8).toDouble() / 16_777_216.0

    private fun fnv(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): Long {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (i in from until to) {
            h = h xor (bytes[i].toLong() and 0xff)
            h *= 0x0000_0100_0000_01b3L
        }
        return h
    }

    private fun weightedSum(bytes: ByteArray, m: Long): Long {
        var a = 0L
        for (i in bytes.indices) a += (bytes[i].toLong() and 0xff) * (i.toLong() % m + 1)
        return a
    }

    // ------------------------------------------------------------ Image

    fun mandelbrot(n: Int): Long {
        val img = ShortArray(n * n)
        for (y in 0 until n) {
            val cy = -1.2 + 2.4 * y / n
            for (x in 0 until n) {
                val cx = -2.2 + 3.2 * x / n
                var zx = 0.0
                var zy = 0.0
                var i = 0
                while (i < 256) {
                    val zx2 = zx * zx
                    val zy2 = zy * zy
                    if (zx2 + zy2 > 4.0) break
                    zy = 2.0 * zx * zy + cy
                    zx = zx2 - zy2 + cx
                    i++
                }
                img[y * n + x] = i.toShort()
            }
        }
        var a = 0L
        for (i in img.indices) a += img[i].toLong() * (i.toLong() % 7 + 1)
        return a
    }

    private fun graySource(n: Int, seed: Int): ByteArray {
        val r = XorShift32(seed)
        val v = ByteArray(n * n)
        for (i in 0 until n * n) {
            val x = i % n
            val y = i / n
            v[i] = (((x xor y) + (r.next() and 63)) and 255).toByte()
        }
        return v
    }

    private val k5 = intArrayOf(1, 4, 6, 4, 1)

    fun convolve(n: Int): Long {
        val src = graySource(n, 0x9E37_79B9.toInt())
        val blur = ByteArray(n * n)
        for (y in 2 until n - 2) {
            for (x in 2 until n - 2) {
                var s = 0
                for (j in 0 until 5) {
                    val base = (y + j - 2) * n + x - 2
                    var t = 0
                    for (i in 0 until 5) t += k5[i] * (src[base + i].toInt() and 0xff)
                    s += k5[j] * t
                }
                blur[y * n + x] = (s ushr 8).toByte()
            }
        }
        val edges = ByteArray(n * n)
        for (y in 1 until n - 1) {
            for (x in 1 until n - 1) {
                fun p(dx: Int, dy: Int): Int = blur[(y + dy - 1) * n + x + dx - 1].toInt() and 0xff
                val gx = -p(0, 0) - 2 * p(0, 1) - p(0, 2) + p(2, 0) + 2 * p(2, 1) + p(2, 2)
                val gy = -p(0, 0) - 2 * p(1, 0) - p(2, 0) + p(0, 2) + 2 * p(1, 2) + p(2, 2)
                edges[y * n + x] = min(abs(gx) + abs(gy), 255).toByte()
            }
        }
        return weightedSum(edges, 13)
    }

    private fun lanczosK(t: Double): Double {
        if (t == 0.0) return 1.0
        if (abs(t) < 3) {
            val pt = PI * t
            return 3.0 * sin(pt) * sin(pt / 3.0) / (pt * pt)
        }
        return 0.0
    }

    fun lanczos(n: Int): Long {
        val src = graySource(n, 0x1357_9BDF)
        val m = n * 3 / 4
        val scale = n.toDouble() / m
        val support = 3.0 * scale
        val taps = ceil(support * 2.0).toInt() + 1
        val starts = IntArray(m)
        val ws = DoubleArray(m * taps)
        for (x in 0 until m) {
            val center = (x + 0.5) * scale - 0.5
            val start = ceil(center - support).toInt()
            val first = x * taps
            var sum = 0.0
            for (k in 0 until taps) {
                val w = lanczosK(((start + k) - center) / scale)
                ws[first + k] = w
                sum += w
            }
            for (i in first until first + taps) ws[i] /= sum
            starts[x] = start
        }
        val last = n - 1
        val tmp = DoubleArray(n * m)
        for (y in 0 until n) {
            for (x in 0 until m) {
                var acc = 0.0
                for (k in 0 until taps) {
                    val j = min(max(starts[x] + k, 0), last)
                    acc += ws[x * taps + k] * (src[y * n + j].toInt() and 0xff)
                }
                tmp[y * m + x] = acc
            }
        }
        val out = ByteArray(m * m)
        for (y in 0 until m) {
            for (x in 0 until m) {
                var acc = 0.0
                for (k in 0 until taps) {
                    val j = min(max(starts[y] + k, 0), last)
                    acc += ws[y * taps + k] * tmp[j * m + x]
                }
                out[y * m + x] = min(max(roundHalfAway(acc), 0L), 255L).toInt().toByte()
            }
        }
        return weightedSum(out, 11)
    }

    // ------------------------------------------------------------ Video

    private val qy = doubleArrayOf(
        16.0, 11.0, 10.0, 16.0, 24.0, 40.0, 51.0, 61.0, 12.0, 12.0, 14.0, 19.0, 26.0, 58.0, 60.0, 55.0,
        14.0, 13.0, 16.0, 24.0, 40.0, 57.0, 69.0, 56.0, 14.0, 17.0, 22.0, 29.0, 51.0, 87.0, 80.0, 62.0,
        18.0, 22.0, 37.0, 56.0, 68.0, 109.0, 103.0, 77.0, 24.0, 35.0, 55.0, 64.0, 81.0, 104.0, 113.0, 92.0,
        49.0, 64.0, 78.0, 87.0, 103.0, 121.0, 120.0, 101.0, 72.0, 92.0, 95.0, 98.0, 112.0, 100.0, 103.0, 99.0,
    )

    fun dct(frames: Int): Long {
        val cosT = DoubleArray(64)
        for (x in 0 until 8) for (u in 0 until 8) cosT[x * 8 + u] = cos((2 * x + 1).toDouble() * u * PI / 16.0)
        val w = 640
        val h = 360
        var total = 0L
        val luma = ByteArray(w * h)
        val tmp = DoubleArray(64)
        for (f in 0 until frames) {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val base = ((x + f * 4) xor (y * 2)) and 255
                    val noise = ((x * 7 + y * 13 + f * 31) * 0x9E3779B1.toInt()) ushr 27
                    luma[y * w + x] = ((base + noise) and 255).toByte()
                }
            }
            var acc = 0L
            var nz = 0L
            for (by in 0 until h / 8) {
                for (bx in 0 until w / 8) {
                    for (y in 0 until 8) {
                        for (u in 0 until 8) {
                            var s = 0.0
                            for (x in 0 until 8) {
                                val p = (luma[(by * 8 + y) * w + bx * 8 + x].toInt() and 0xff) - 128.0
                                s += p * cosT[x * 8 + u]
                            }
                            tmp[y * 8 + u] = s
                        }
                    }
                    for (v in 0 until 8) {
                        for (u in 0 until 8) {
                            var s = 0.0
                            for (y in 0 until 8) s += tmp[y * 8 + u] * cosT[y * 8 + v]
                            val cu = if (u == 0) 0.7071067811865476 else 1.0
                            val cv = if (v == 0) 0.7071067811865476 else 1.0
                            val value = 0.25 * cu * cv * s
                            val qv = roundHalfAway(value / qy[v * 8 + u])
                            acc += qv * (v * 8 + u + 1)
                            if (qv != 0L) nz++
                        }
                    }
                }
            }
            total += acc xor (nz shl 40)
        }
        return total
    }

    fun grade(frames: Int): Long {
        val lut = IntArray(256) { i -> roundHalfAway((i / 255.0).pow(0.8) * 255.0).toInt() }
        val w = 1280
        val h = 720
        val maxD2 = (w / 2) * (w / 2) + (h / 2) * (h / 2)
        var total = 0L
        for (f in 0 until frames) {
            val hist = IntArray(256)
            var acc = 0L
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var r = lut[(x + f * 3) and 255]
                    var g = lut[(y + f * 2) and 255]
                    var b = lut[((x xor y) + f) and 255]
                    val l = (77 * r + 150 * g + 29 * b) shr 8
                    r = min(max(l + (r - l) * 5 / 4, 0), 255)
                    g = min(max(l + (g - l) * 5 / 4, 0), 255)
                    b = min(max(l + (b - l) * 5 / 4, 0), 255)
                    val dx = x - w / 2
                    val dy = y - h / 2
                    val factor = 256 - (dx * dx + dy * dy) * 96 / maxD2
                    r = (r * factor) shr 8
                    g = (g * factor) shr 8
                    b = (b * factor) shr 8
                    hist[(77 * r + 150 * g + 29 * b) shr 8]++
                    acc += (r xor (g shl 8) xor (b shl 16)).toLong()
                }
            }
            for (i in hist.indices) acc += u32(hist[i]) * (i + 1)
            total += acc
        }
        return total
    }

    // ------------------------------------------------------------ 3D

    class V3(@JvmField val x: Double, @JvmField val y: Double, @JvmField val z: Double) {
        fun add(o: V3) = V3(x + o.x, y + o.y, z + o.z)
        fun sub(o: V3) = V3(x - o.x, y - o.y, z - o.z)
        fun mul(o: V3) = V3(x * o.x, y * o.y, z * o.z)
        fun scale(s: Double) = V3(x * s, y * s, z * s)
        fun dot(o: V3) = x * o.x + y * o.y + z * o.z
        fun norm(): V3 { val l = sqrt(dot(this)); return V3(x / l, y / l, z / l) }
    }

    private class PtSphere(val c: V3, val r: Double, val albedo: V3)

    private val ptSpheres = arrayOf(
        PtSphere(V3(0.0, -1000.5, -1.0), 1000.0, V3(0.5, 0.5, 0.5)),
        PtSphere(V3(0.0, 0.0, -1.0), 0.5, V3(0.7, 0.3, 0.3)),
        PtSphere(V3(-1.05, 0.0, -1.0), 0.5, V3(0.3, 0.7, 0.3)),
        PtSphere(V3(1.05, 0.0, -1.0), 0.5, V3(0.3, 0.3, 0.8)),
        PtSphere(V3(0.0, -0.35, -0.45), 0.15, V3(0.9, 0.9, 0.2)),
    )

    private fun ptTrace(o0: V3, d0: V3, r: XorShift32): V3 {
        var o = o0
        var d = d0
        var thr = V3(1.0, 1.0, 1.0)
        repeat(4) {
            var best = Double.MAX_VALUE
            var hit = -1
            for (i in ptSpheres.indices) {
                val s = ptSpheres[i]
                val oc = o.sub(s.c)
                val b = oc.dot(d)
                val cc = oc.dot(oc) - s.r * s.r
                val disc = b * b - cc
                if (disc < 0) continue
                val sq = sqrt(disc)
                var t = -b - sq
                if (t < 1e-4) t = -b + sq
                if (t >= 1e-4 && t < best) { best = t; hit = i }
            }
            if (hit < 0) {
                val t = 0.5 * (d.y + 1.0)
                val sky = V3(1.0 - t + t * 0.5, 1.0 - t + t * 0.7, 1.0 - t + t * 1.0)
                return thr.mul(sky)
            }
            val s = ptSpheres[hit]
            val p = o.add(d.scale(best))
            val nrm = p.sub(s.c).scale(1.0 / s.r)
            var ru: V3
            while (true) {
                val v = V3(rnd01(r) * 2.0 - 1.0, rnd01(r) * 2.0 - 1.0, rnd01(r) * 2.0 - 1.0)
                val l2 = v.dot(v)
                if (l2 < 1.0 && l2 > 1e-6) { ru = v.norm(); break }
            }
            thr = thr.mul(s.albedo)
            o = p
            d = nrm.add(ru).norm()
        }
        return V3(0.0, 0.0, 0.0)
    }

    fun pathtrace(n: Int): Long {
        val spp = 8
        val img = ByteArray(n * n * 3)
        for (y in 0 until n) {
            for (x in 0 until n) {
                val seed = ((y * n + x) * 0x9E3779B1.toInt()) xor 0xA5A5_5A5A.toInt()
                val r = XorShift32(if (seed == 0) 1 else seed)
                var col = V3(0.0, 0.0, 0.0)
                repeat(spp) {
                    val u = ((x + rnd01(r)) / n) * 2.0 - 1.0
                    val v = 1.0 - ((y + rnd01(r)) / n) * 2.0
                    val d = V3(u * 1.2, v * 1.2, -1.0).norm()
                    col = col.add(ptTrace(V3(0.0, 0.0, 0.0), d, r))
                }
                val base = (y * n + x) * 3
                val cs = doubleArrayOf(col.x, col.y, col.z)
                for (k in 0 until 3) {
                    img[base + k] = (min(max(sqrt(cs[k] / spp), 0.0), 0.999) * 256.0).toInt().toByte()
                }
            }
        }
        return weightedSum(img, 13)
    }

    private class BVHBuilder(val bmin: DoubleArray, val bmax: DoubleArray, val cent: DoubleArray) {
        val idx = IntArray(cent.size / 3) { it }
        var nodes = 0L
        var leaves = 0L
        var depthSum = 0L
        var areaQ = 0L

        fun build(lo: Int, hi: Int, depth: Long) {
            val mn = DoubleArray(3) { Double.MAX_VALUE }
            val mx = DoubleArray(3) { -Double.MAX_VALUE }
            val cmn = mn.copyOf()
            val cmx = mx.copyOf()
            for (k in lo until hi) {
                val i = idx[k]
                for (a in 0 until 3) {
                    if (bmin[i * 3 + a] < mn[a]) mn[a] = bmin[i * 3 + a]
                    if (bmax[i * 3 + a] > mx[a]) mx[a] = bmax[i * 3 + a]
                    if (cent[i * 3 + a] < cmn[a]) cmn[a] = cent[i * 3 + a]
                    if (cent[i * 3 + a] > cmx[a]) cmx[a] = cent[i * 3 + a]
                }
            }
            val dx = mx[0] - mn[0]
            val dy = mx[1] - mn[1]
            val dz = mx[2] - mn[2]
            nodes++
            areaQ += q(2.0 * (dx * dy + dy * dz + dz * dx), 10.0)
            if (hi - lo <= 4) { leaves++; depthSum += depth; return }
            val ext = doubleArrayOf(cmx[0] - cmn[0], cmx[1] - cmn[1], cmx[2] - cmn[2])
            var axis = 0
            if (ext[1] > ext[axis]) axis = 1
            if (ext[2] > ext[axis]) axis = 2
            val c = cent
            val slice = idx.copyOfRange(lo, hi).toTypedArray()
            slice.sortWith { a, b ->
                val ca = c[a * 3 + axis]
                val cb = c[b * 3 + axis]
                if (ca < cb || (ca == cb && a < b)) -1 else if (ca == cb && a == b) 0 else 1
            }
            for (k in slice.indices) idx[lo + k] = slice[k]
            val mid = lo + (hi - lo) / 2
            build(lo, mid, depth + 1)
            build(mid, hi, depth + 1)
        }
    }

    fun bvh(n: Int): Long {
        val r = XorShift32(0xB0B0_CAFE.toInt())
        val bmin = DoubleArray(n * 3)
        val bmax = DoubleArray(n * 3)
        val cent = DoubleArray(n * 3)
        val v = Array(3) { DoubleArray(3) }
        for (t in 0 until n) {
            val base = doubleArrayOf(rnd01(r) * 100.0, rnd01(r) * 100.0, rnd01(r) * 100.0)
            for (k in 0 until 3) for (a in 0 until 3) v[k][a] = base[a] + (rnd01(r) - 0.5) * 2.0
            for (a in 0 until 3) {
                var mn = v[0][a]
                var mx = v[0][a]
                for (k in 1 until 3) {
                    if (v[k][a] < mn) mn = v[k][a]
                    if (v[k][a] > mx) mx = v[k][a]
                }
                bmin[t * 3 + a] = mn
                bmax[t * 3 + a] = mx
                cent[t * 3 + a] = (v[0][a] + v[1][a] + v[2][a]) / 3.0
            }
        }
        val b = BVHBuilder(bmin, bmax, cent)
        b.build(0, n, 0)
        return (b.nodes xor (b.leaves shl 20) xor (b.depthSum shl 32)) + b.areaQ
    }

    private val balls = arrayOf(
        doubleArrayOf(-0.3, 0.0, 0.0, 0.35), doubleArrayOf(0.35, 0.1, 0.0, 0.3), doubleArrayOf(0.0, -0.25, 0.3, 0.25),
    )
    private val cubeEdges = arrayOf(
        intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6, 7), intArrayOf(0, 2), intArrayOf(1, 3),
        intArrayOf(4, 6), intArrayOf(5, 7), intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
    )

    fun isosurface(n: Int): Long {
        val g = n + 1
        val field = DoubleArray(g * g * g)
        for (k in 0 until g) {
            val pz = k.toDouble() / n * 2.0 - 1.0
            for (j in 0 until g) {
                val py = j.toDouble() / n * 2.0 - 1.0
                for (i in 0 until g) {
                    val px = i.toDouble() / n * 2.0 - 1.0
                    var f = 0.0
                    for (ball in balls) {
                        val dx = px - ball[0]
                        val dy = py - ball[1]
                        val dz = pz - ball[2]
                        f += ball[3] * ball[3] / (dx * dx + dy * dy + dz * dz + 1e-6)
                    }
                    field[(k * g + j) * g + i] = f - 1.0
                }
            }
        }
        fun at(i: Int, j: Int, k: Int): Double = field[(k * g + j) * g + i]
        var vsum = 0L
        val v = DoubleArray(8)
        for (k in 0 until n) {
            var verts = 0L
            var acc = 0L
            for (j in 0 until n) {
                for (i in 0 until n) {
                    var mask = 0
                    for (c in 0 until 8) {
                        v[c] = at(i + (c and 1), j + ((c shr 1) and 1), k + (c shr 2))
                        if (v[c] > 0) mask = mask or (1 shl c)
                    }
                    if (mask == 0 || mask == 255) continue
                    var sx = 0.0
                    var sy = 0.0
                    var sz = 0.0
                    var cnt = 0.0
                    for (e in cubeEdges) {
                        val a = e[0]
                        val b = e[1]
                        if ((v[a] > 0) == (v[b] > 0)) continue
                        val t = v[a] / (v[a] - v[b])
                        val pa0 = (a and 1).toDouble(); val pa1 = ((a shr 1) and 1).toDouble(); val pa2 = (a shr 2).toDouble()
                        val pb0 = (b and 1).toDouble(); val pb1 = ((b shr 1) and 1).toDouble(); val pb2 = (b shr 2).toDouble()
                        sx += pa0 + (pb0 - pa0) * t
                        sy += pa1 + (pb1 - pa1) * t
                        sz += pa2 + (pb2 - pa2) * t
                        cnt += 1.0
                    }
                    verts++
                    acc += q(i + sx / cnt, 1000.0) + q(j + sy / cnt, 1000.0) + q(k + sz / cnt, 1000.0)
                }
            }
            vsum += acc + (verts shl 40)
        }
        var quads = 0L
        for (k in 1 until n) {
            for (j in 1 until n) {
                for (i in 0 until n) {
                    if ((at(i, j, k) > 0) != (at(i + 1, j, k) > 0)) quads++
                    if ((at(j, i, k) > 0) != (at(j, i + 1, k) > 0)) quads++
                    if ((at(j, k, i) > 0) != (at(j, k, i + 1) > 0)) quads++
                }
            }
        }
        return vsum + quads * 2
    }

    // ------------------------------------------------------------ Math

    fun nbody(steps: Int): Long {
        val sm = 4.0 * PI * PI
        val dpy = 365.24
        val b = arrayOf(
            doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, sm),
            doubleArrayOf(
                4.84143144246472e0, -1.1603200440274284e0, -1.0362204447112311e-1,
                1.660076642744037e-3 * dpy, 7.699011184197404e-3 * dpy, -6.90460016972063e-5 * dpy,
                9.547919384243266e-4 * sm,
            ),
            doubleArrayOf(
                8.34336671824458e0, 4.124798564124305e0, -4.035234171143214e-1,
                -2.767425107268624e-3 * dpy, 4.998528012349172e-3 * dpy, 2.3041729757376393e-5 * dpy,
                2.858859806661308e-4 * sm,
            ),
            doubleArrayOf(
                1.2894369562139131e1, -1.5111151401698631e1, -2.2330757889265573e-1,
                2.964601375647616e-3 * dpy, 2.3784717395948095e-3 * dpy, -2.9658956854023756e-5 * dpy,
                4.366244043351563e-5 * sm,
            ),
            doubleArrayOf(
                1.5379697114850911e1, -2.5919314609987964e1, 1.7925877295037118e-1,
                2.6806777249038932e-3 * dpy, 1.628241700382423e-3 * dpy, -9.515922545197159e-5 * dpy,
                5.1513890204661145e-5 * sm,
            ),
        )
        var px = 0.0
        var py = 0.0
        var pz = 0.0
        for (body in b) {
            px += body[3] * body[6]
            py += body[4] * body[6]
            pz += body[5] * body[6]
        }
        b[0][3] = -px / sm
        b[0][4] = -py / sm
        b[0][5] = -pz / sm
        val dt = 0.01
        repeat(steps) {
            for (i in 0 until 5) {
                val bi = b[i]
                for (j in i + 1 until 5) {
                    val bj = b[j]
                    val dx = bi[0] - bj[0]
                    val dy = bi[1] - bj[1]
                    val dz = bi[2] - bj[2]
                    val d2 = dx * dx + dy * dy + dz * dz
                    val mag = dt / (d2 * sqrt(d2))
                    val mj = bj[6] * mag
                    val mi = bi[6] * mag
                    bi[3] -= dx * mj
                    bi[4] -= dy * mj
                    bi[5] -= dz * mj
                    bj[3] += dx * mi
                    bj[4] += dy * mi
                    bj[5] += dz * mi
                }
            }
            for (i in 0 until 5) {
                b[i][0] += dt * b[i][3]
                b[i][1] += dt * b[i][4]
                b[i][2] += dt * b[i][5]
            }
        }
        var e = 0.0
        for (i in 0 until 5) {
            e += 0.5 * b[i][6] * (b[i][3] * b[i][3] + b[i][4] * b[i][4] + b[i][5] * b[i][5])
            for (j in i + 1 until 5) {
                val dx = b[i][0] - b[j][0]
                val dy = b[i][1] - b[j][1]
                val dz = b[i][2] - b[j][2]
                e -= b[i][6] * b[j][6] / sqrt(dx * dx + dy * dy + dz * dz)
            }
        }
        return q(e, 1e9)
    }

    fun fft(n: Int): Long {
        val r = XorShift32(0xF00D_F00D.toInt())
        val re = DoubleArray(n) { rnd01(r) - 0.5 }
        val im = DoubleArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        val half = n / 2
        val twr = DoubleArray(half)
        val twi = DoubleArray(half)
        for (k in 0 until half) {
            val ang = 2.0 * PI * k / n
            twr[k] = cos(ang)
            twi[k] = -sin(ang)
        }
        var len = 2
        while (len <= n) {
            val step = n / len
            val h = len / 2
            var i = 0
            while (i < n) {
                for (k in 0 until h) {
                    val wr = twr[k * step]
                    val wi = twi[k * step]
                    val a = i + k
                    val b = a + h
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                i += len
            }
            len = len shl 1
        }
        var acc = 0L
        for (k in 0 until n) acc += q(abs(re[k]) + abs(im[k]), 1000.0)
        return acc
    }

    fun bigintFactorial(n: Int): Long {
        val base = 1_000_000_000L
        var limbs = IntArray(16)
        limbs[0] = 1
        var count = 1
        if (n >= 2) {
            for (k in 2L..n.toLong()) {
                var carry = 0L
                for (i in 0 until count) {
                    val cur = limbs[i].toLong() * k + carry
                    limbs[i] = (cur % base).toInt()
                    carry = cur / base
                }
                while (carry > 0) {
                    if (count == limbs.size) limbs = limbs.copyOf(limbs.size * 2)
                    limbs[count++] = (carry % base).toInt()
                    carry /= base
                }
            }
        }
        var digits = 0L
        for (i in 0 until count) {
            var v = limbs[i]
            while (v > 0) { digits += v % 10; v /= 10 }
        }
        return digits + (count.toLong() shl 32)
    }

    fun particles(n: Int): Long {
        val r = XorShift32(0x0BAD_5EED)
        val ps = Array(n) {
            val x = rnd01(r)
            val y = rnd01(r)
            val vx = rnd01(r) - 0.5
            val vy = rnd01(r) - 0.5
            doubleArrayOf(x, y, vx, vy)
        }
        val dt = 0.005
        for (k in 0 until n) {
            val p = ps[k]
            repeat(50) {
                p[3] -= 9.81 * dt
                p[0] += p[2] * dt
                p[1] += p[3] * dt
                if (p[0] < 0) { p[0] = -p[0]; p[2] = -p[2] * 0.9 } else if (p[0] > 1) { p[0] = 2 - p[0]; p[2] = -p[2] * 0.9 }
                if (p[1] < 0) { p[1] = -p[1]; p[3] = -p[3] * 0.8 } else if (p[1] > 1) { p[1] = 2 - p[1]; p[3] = -p[3] * 0.8 }
                p[2] *= 0.999
                p[3] *= 0.999
            }
        }
        var acc = 0L
        for (p in ps) acc += q(p[0], 1e6) + q(p[1], 1e6)
        return acc
    }

    // ------------------------------------------------------------ Data

    fun kmeans(n: Int): Long {
        val k = 16
        val r = XorShift32(0x00C0_FFEE)
        val seedX = DoubleArray(k)
        val seedY = DoubleArray(k)
        for (i in 0 until k) { seedX[i] = rnd01(r) * 10.0; seedY[i] = rnd01(r) * 10.0 }
        val px = DoubleArray(n)
        val py = DoubleArray(n)
        for (i in 0 until n) {
            val c = umod(r.next(), k)
            px[i] = seedX[c] + (rnd01(r) - 0.5) * 1.5
            py[i] = seedY[c] + (rnd01(r) - 0.5) * 1.5
        }
        val cx = px.copyOfRange(0, k)
        val cy = py.copyOfRange(0, k)
        val assign = IntArray(n)
        repeat(8) {
            for (i in 0 until n) {
                var best = 0
                var bd = Double.MAX_VALUE
                for (c in 0 until k) {
                    val dx = px[i] - cx[c]
                    val dy = py[i] - cy[c]
                    val d = dx * dx + dy * dy
                    if (d < bd) { bd = d; best = c }
                }
                assign[i] = best
            }
            val sx = DoubleArray(k)
            val sy = DoubleArray(k)
            val counts = IntArray(k)
            for (i in 0 until n) {
                sx[assign[i]] += px[i]
                sy[assign[i]] += py[i]
                counts[assign[i]]++
            }
            for (c in 0 until k) {
                if (counts[c] > 0) {
                    cx[c] = sx[c] / counts[c]
                    cy[c] = sy[c] / counts[c]
                }
            }
        }
        var acc = 0L
        for (i in 0 until n) acc += assign[i].toLong() * (i.toLong() % 7 + 1)
        for (c in 0 until k) acc += q(cx[c], 1e6) + q(cy[c], 1e6)
        return acc
    }

    private fun genText(n: Int, seed: Int): ByteArray {
        val r = XorShift32(seed)
        val vocab = Array(500) {
            val len = 2 + umod(r.next(), 8)
            ByteArray(len) { (97 + umod(r.next(), 26)).toByte() }
        }
        val out = ByteArray(n + 16)
        var size = 0
        while (size < n) {
            val w = vocab[umod(r.next(), 500)]
            val cap = umod(r.next(), 10) == 0
            val start = size
            System.arraycopy(w, 0, out, size, w.size)
            size += w.size
            if (cap) out[start] = (out[start] - 32).toByte()
            val s = umod(r.next(), 30)
            if (s == 0) { out[size++] = 46; out[size++] = 10 } else if (s < 3) { out[size++] = 44; out[size++] = 32 } else { out[size++] = 32 }
        }
        return out.copyOf(n)
    }

    private fun lzBlock(d: ByteArray): Long {
        val hbits = 14
        val len = d.size
        val head = IntArray(1 shl hbits) { -1 }
        val prev = IntArray(len) { -1 }
        fun hash(p: Int): Int {
            val v = (d[p].toInt() and 0xff) or ((d[p + 1].toInt() and 0xff) shl 8) or
                ((d[p + 2].toInt() and 0xff) shl 16) or ((d[p + 3].toInt() and 0xff) shl 24)
            return (v * 0x9E3779B1.toInt()) ushr (32 - hbits)
        }
        fun insert(p: Int) {
            if (p + 4 <= len) {
                val h = hash(p)
                prev[p] = head[h]
                head[h] = p
            }
        }
        var acc = 0L
        var bits = 0L
        var pos = 0
        while (pos < len) {
            var bestLen = 0
            var bestDist = 0
            if (pos + 4 <= len) {
                var cand = head[hash(pos)]
                var depth = 0
                val maxL = min(len - pos, 258)
                while (cand >= 0 && depth < 16 && pos - cand <= 32_768) {
                    val c = cand
                    var l = 0
                    while (l < maxL && d[c + l] == d[pos + l]) l++
                    if (l > bestLen) {
                        bestLen = l
                        bestDist = pos - c
                        if (l == maxL) break
                    }
                    cand = prev[c]
                    depth++
                }
            }
            if (bestLen >= 4) {
                acc = acc * 31 + (((bestLen shl 16) or bestDist).toLong() xor 0x8000_0000_0000L)
                bits += 32
                for (p in pos until pos + bestLen) insert(p)
                pos += bestLen
            } else {
                acc = acc * 31 + (d[pos].toLong() and 0xff)
                bits += 9
                insert(pos)
                pos++
            }
        }
        return acc + (bits shl 40)
    }

    fun lz77(n: Int): Long {
        val block = 1 shl 16
        val data = genText(n, 0x0012_3457)
        val blocks = (data.size + block - 1) / block
        var total = 0L
        for (b in 0 until blocks) {
            val end = min((b + 1) * block, data.size)
            total += lzBlock(data.copyOfRange(b * block, end)) * (b + 1)
        }
        return total
    }

    // ------------------------------------------------------------ Text

    fun wordfreq(n: Int): Long {
        val text = genText(n, 0x0BEE_F123)
        val counts = HashMap<String, Int>()
        val buf = ByteArray(64)
        var bl = 0
        for (b in text) {
            val c = b.toInt()
            if ((c in 65..90) || (c in 97..122)) {
                buf[bl++] = (if (c in 65..90) c + 32 else c).toByte()
            } else if (bl > 0) {
                counts.merge(String(buf, 0, bl, Charsets.ISO_8859_1), 1, Int::plus)
                bl = 0
            }
        }
        if (bl > 0) counts.merge(String(buf, 0, bl, Charsets.ISO_8859_1), 1, Int::plus)
        var acc = counts.size.toLong() shl 48
        for ((w, c) in counts) acc += fnv(w.toByteArray(Charsets.ISO_8859_1)) * c
        return acc
    }

    private fun reHere(re: ByteArray, ri: Int, t: ByteArray, tFrom: Int, tTo: Int, ti: Int): Boolean {
        if (ri == re.size) return true
        if (ri + 1 < re.size && re[ri + 1] == 42.toByte()) return reStar(re[ri], re, ri + 2, t, tFrom, tTo, ti)
        if (re[ri] == 36.toByte() && ri + 1 == re.size) return ti == tTo
        if (ti < tTo && (re[ri] == 46.toByte() || re[ri] == t[ti])) return reHere(re, ri + 1, t, tFrom, tTo, ti + 1)
        return false
    }

    private fun reStar(c: Byte, re: ByteArray, ri: Int, t: ByteArray, tFrom: Int, tTo: Int, ti0: Int): Boolean {
        var ti = ti0
        while (true) {
            if (reHere(re, ri, t, tFrom, tTo, ti)) return true
            if (ti < tTo && (t[ti] == c || c == 46.toByte())) ti++ else return false
        }
    }

    /** Matches `re` against the line `t[tFrom, tTo)` (no copy per line). */
    private fun reMatch(re: ByteArray, t: ByteArray, tFrom: Int, tTo: Int): Boolean {
        if (re.isNotEmpty() && re[0] == 94.toByte()) return reHere(re, 1, t, tFrom, tTo, tFrom)
        var ti = tFrom
        while (true) {
            if (reHere(re, 0, t, tFrom, tTo, ti)) return true
            if (ti == tTo) return false
            ti++
        }
    }

    private val patterns = listOf("ab.*ef", "^b", "q.z", "e*st$", "xa*y").map { it.toByteArray(Charsets.US_ASCII) }

    fun textsearch(n: Int): Long {
        val text = genText(n, 0x7E57_7E57)
        var acc = 0L
        var line = 0
        var start = 0
        var i = 0
        while (i <= text.size) {
            if (i == text.size || text[i] == 10.toByte()) {
                for (k in patterns.indices) {
                    if (reMatch(patterns[k], text, start, i)) acc += (k + 1).toLong() * (line.toLong() % 3 + 1)
                }
                line++
                start = i + 1
            }
            i++
        }
        return acc
    }

    fun strsort(n: Int): Long {
        val r = XorShift32(0x5747_5747)
        val v = ArrayList<String>(n)
        repeat(n) {
            val len = 4 + umod(r.next(), 13)
            val bytes = ByteArray(len) { (97 + umod(r.next(), 26)).toByte() }
            v.add(String(bytes, Charsets.US_ASCII))
        }
        v.sort()
        var acc = fnv(v[n - 1].toByteArray(Charsets.US_ASCII))
        var i = 0
        while (i < n) {
            acc = acc * 31 + fnv(v[i].toByteArray(Charsets.US_ASCII)) + i
            i += 97
        }
        return acc
    }

    private fun levPair(len: Int, pair: Int): Long {
        val r = XorShift32(0x5EED + pair * 7919)
        val acgt = "acgt".toByteArray(Charsets.US_ASCII)
        val a = ByteArray(len) { acgt[umod(r.next(), 4)] }
        val bl = ArrayList<Byte>(len + len / 8)
        for (c in a) {
            val k = umod(r.next(), 25)
            if (k == 0) continue
            bl.add(if (umod(r.next(), 10) == 0) acgt[umod(r.next(), 4)] else c)
            if (k == 1) bl.add(acgt[umod(r.next(), 4)])
        }
        val b = bl.toByteArray()
        var prev = IntArray(b.size + 1) { it }
        var cur = IntArray(b.size + 1)
        for (i in a.indices) {
            val ca = a[i]
            cur[0] = i + 1
            for (j in b.indices) {
                val sub = prev[j] + (if (ca != b[j]) 1 else 0)
                cur[j + 1] = min(min(sub, prev[j + 1] + 1), cur[j] + 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.size].toLong() * (pair + 1)
    }

    fun levenshtein(n: Int): Long {
        var acc = 0L
        for (p in 0 until 8) acc += levPair(n, p)
        return acc
    }

    // ------------------------------------------------------------ Crypto

    @Suppress("NOTHING_TO_INLINE")
    private inline fun rotl(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun qr(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]; s[d] = rotl(s[d] xor s[a], 16)
        s[c] += s[d]; s[b] = rotl(s[b] xor s[c], 12)
        s[a] += s[b]; s[d] = rotl(s[d] xor s[a], 8)
        s[c] += s[d]; s[b] = rotl(s[b] xor s[c], 7)
    }

    private fun chachaBlock(key: IntArray, counter: Int, nonce: IntArray, out: ByteArray, initial: IntArray, s: IntArray) {
        initial[0] = 0x6170_7865; initial[1] = 0x3320_646e; initial[2] = 0x7962_2d32; initial[3] = 0x6b20_6574
        System.arraycopy(key, 0, initial, 4, 8)
        initial[12] = counter
        System.arraycopy(nonce, 0, initial, 13, 3)
        System.arraycopy(initial, 0, s, 0, 16)
        repeat(10) {
            qr(s, 0, 4, 8, 12); qr(s, 1, 5, 9, 13); qr(s, 2, 6, 10, 14); qr(s, 3, 7, 11, 15)
            qr(s, 0, 5, 10, 15); qr(s, 1, 6, 11, 12); qr(s, 2, 7, 8, 13); qr(s, 3, 4, 9, 14)
        }
        for (i in 0 until 16) {
            val w = s[i] + initial[i]
            out[i * 4] = w.toByte()
            out[i * 4 + 1] = (w ushr 8).toByte()
            out[i * 4 + 2] = (w ushr 16).toByte()
            out[i * 4 + 3] = (w ushr 24).toByte()
        }
    }

    fun chacha20(n: Int): Long {
        val chunk = 4096
        val key = IntArray(8) { (it * 0x0101_0101) xor 0xDEAD_BEEF.toInt() }
        val nonce = intArrayOf(0x0000_0009, 0x4a00_0000, 0)
        val data = ByteArray(n) { (it * 7 + 3).toByte() }
        val ks = ByteArray(64)
        val initial = IntArray(16)
        val st = IntArray(16)
        var block = 0
        var off = 0
        while (off < n) {
            chachaBlock(key, block + 1, nonce, ks, initial, st)
            val end = min(off + 64, n)
            for (i in off until end) data[i] = (data[i].toInt() xor ks[i - off].toInt()).toByte()
            off += 64
            block++
        }
        var acc = 0L
        var ci = 0
        var start = 0
        while (start < n) {
            val end = min(start + chunk, n)
            var h = 0L
            var p = start
            while (p + 8 <= end) {
                var w = 0L
                for (k in 0 until 8) w = w or ((data[p + k].toLong() and 0xff) shl (8 * k))
                h = ((h shl 5) or (h ushr 59)) xor w
                p += 8
            }
            acc += h * (ci + 1)
            ci++
            start += chunk
        }
        return acc
    }

    fun crc32(n: Int): Long {
        val table = IntArray(256)
        for (i in 0 until 256) {
            var c = i
            repeat(8) { c = if ((c and 1) != 0) (0xEDB8_8320.toInt() xor (c ushr 1)) else (c ushr 1) }
            table[i] = c
        }
        val r = XorShift32(0xC4C3_2C32.toInt())
        val data = ByteArray(n) { r.next().toByte() }
        var crc = -1
        for (b in data) crc = table[(crc xor (b.toInt() and 0xff)) and 0xff] xor (crc ushr 8)
        return u32(crc.inv()) or (n.toLong() shl 32)
    }

    // ------------------------------------------------------------ Algorithms

    /** Minimal binary min-heap over (dist, node) pairs packed into one Long (dist high, node low). */
    private class MinHeap {
        private var a = LongArray(64)
        var size = 0
            private set

        fun push(v: Long) {
            if (size == a.size) a = a.copyOf(size * 2)
            a[size] = v
            var i = size++
            while (i > 0) {
                val p = (i - 1) / 2
                if (a[i] < a[p]) { val t = a[i]; a[i] = a[p]; a[p] = t; i = p } else break
            }
        }

        fun pop(): Long {
            val top = a[0]
            val last = a[--size]
            if (size > 0) {
                a[0] = last
                var i = 0
                while (true) {
                    val l = 2 * i + 1
                    val r = l + 1
                    var m = i
                    if (l < size && a[l] < a[m]) m = l
                    if (r < size && a[r] < a[m]) m = r
                    if (m == i) break
                    val t = a[i]; a[i] = a[m]; a[m] = t
                    i = m
                }
            }
            return top
        }
    }

    fun dijkstra(n: Int): Long {
        val r = XorShift32(0xD1D1_D1D1.toInt())
        val cost = IntArray(n * n) { 1 + umod(r.next(), 16) }
        val dist = IntArray(n * n) { Int.MAX_VALUE }
        val heap = MinHeap()
        dist[0] = 0
        heap.push(0L)
        while (heap.size > 0) {
            val e = heap.pop()
            val d = (e ushr 32).toInt()
            val u = e.toInt()
            if (d > dist[u]) continue
            val x = u % n
            val y = u / n
            fun relax(v: Int) {
                val nd = d + cost[v]
                if (nd < dist[v]) { dist[v] = nd; heap.push((nd.toLong() shl 32) or v.toLong()) }
            }
            if (x > 0) relax(u - 1)
            if (x + 1 < n) relax(u + 1)
            if (y > 0) relax(u - n)
            if (y + 1 < n) relax(u + n)
        }
        var sum = 0L
        var mx = 0
        for (d in dist) { sum += d.toLong(); if (d > mx) mx = d }
        return sum + (mx.toLong() shl 40)
    }

    fun bfs(n: Int): Long {
        val r = XorShift32(0xBF5B_F5BF.toInt())
        val m = n * 4
        val eu = IntArray(m)
        val ev = IntArray(m)
        for (e in 0 until m) {
            eu[e] = umod(r.next(), n)
            ev[e] = umod(r.next(), n)
        }
        val off = IntArray(n + 1)
        for (e in 0 until m) { off[eu[e] + 1]++; off[ev[e] + 1]++ }
        for (i in 0 until n) off[i + 1] += off[i]
        val fill = off.copyOf()
        val adj = IntArray(m * 2)
        for (e in 0 until m) {
            val u = eu[e]
            val v = ev[e]
            adj[fill[u]++] = v
            adj[fill[v]++] = u
        }
        val level = IntArray(n) { -1 }
        val queue = IntArray(n)
        var qn = 0
        level[0] = 0
        queue[qn++] = 0
        var head = 0
        while (head < qn) {
            val u = queue[head++]
            for (k in off[u] until off[u + 1]) {
                val v = adj[k]
                if (level[v] == -1) { level[v] = level[u] + 1; queue[qn++] = v }
            }
        }
        var acc = qn.toLong() shl 40
        for (i in level.indices) {
            val l = level[i]
            if (l != -1) acc += l.toLong() * (i.toLong() % 5 + 1)
        }
        return acc
    }

    private fun queens(mask: Int, cols: Int, d1: Int, d2: Int): Long {
        if (cols == mask) return 1
        var avail = (cols or d1 or d2).inv() and mask
        var count = 0L
        while (avail != 0) {
            val bit = avail and -avail
            avail = avail xor bit
            count += queens(mask, cols or bit, (d1 or bit) shl 1, (d2 or bit) ushr 1)
        }
        return count
    }

    fun nqueens(n: Int): Long {
        val mask = (1 shl n) - 1
        var total = 0L
        for (c in 0 until n) {
            val bit = 1 shl c
            total += queens(mask, bit, bit shl 1, bit ushr 1)
        }
        return total
    }

    private val puzzles = listOf(
        "4.....8.5.3..........7......2.....6.....8.4......1.......6.3.7.5..2.....1.4......",
        "52...6.........7.13...........4..8..6......5...........418.........3..2...87.....",
        "85...24..72......9..4.........1.7..23.5...9...4...........8..7..17..........36.4.",
        "..53.....8......2..7..1.5..4....53...1..7...6..32...8..6.5....9..4....3......97..",
    )

    private class Sudoku(p: String) {
        val g = IntArray(81)
        private val rows = IntArray(9)
        private val cols = IntArray(9)
        private val boxes = IntArray(9)
        var nodes = 0L

        init {
            for (i in p.indices) {
                val ch = p[i].code
                if (ch in 49..57) set(i, ch - 48)
            }
        }

        private fun set(i: Int, d: Int) {
            val bit = 1 shl d
            g[i] = d
            rows[i / 9] = rows[i / 9] or bit
            cols[i % 9] = cols[i % 9] or bit
            val bx = (i / 27) * 3 + (i % 9) / 3
            boxes[bx] = boxes[bx] or bit
        }

        private fun unset(i: Int, d: Int) {
            val bit = (1 shl d).inv()
            g[i] = 0
            rows[i / 9] = rows[i / 9] and bit
            cols[i % 9] = cols[i % 9] and bit
            val bx = (i / 27) * 3 + (i % 9) / 3
            boxes[bx] = boxes[bx] and bit
        }

        fun solve(): Boolean {
            nodes++
            var best = -1
            var bestCnt = 10
            var bestMask = 0
            for (i in 0 until 81) {
                if (g[i] != 0) continue
                val used = rows[i / 9] or cols[i % 9] or boxes[(i / 27) * 3 + (i % 9) / 3]
                val free = used.inv() and 0x3FE
                val cnt = Integer.bitCount(free)
                if (cnt < bestCnt) {
                    best = i; bestCnt = cnt; bestMask = free
                    if (cnt <= 1) break
                }
            }
            if (best < 0) return true
            for (d in 1..9) {
                if (bestMask and (1 shl d) == 0) continue
                set(best, d)
                if (solve()) return true
                unset(best, d)
            }
            return false
        }
    }

    fun sudoku(n: Int): Long {
        var total = 0L
        for (k in 0 until n * puzzles.size) {
            val s = Sudoku(puzzles[k % puzzles.size])
            val ok = s.solve()
            var grid = 0L
            for (i in s.g.indices) grid += s.g[i].toLong() * (i + 1)
            total += grid + (s.nodes shl 20) + (if (ok) 1 else 0)
        }
        return total
    }
}
