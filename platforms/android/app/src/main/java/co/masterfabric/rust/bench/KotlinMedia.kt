package co.masterfabric.rust.bench

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// One-to-one Kotlin port of `crates/app-core/src/media.rs`.
// Pure CPU, idiomatic Kotlin (primitive arrays, small classes), no Android graphics APIs.
// UInt64 values are carried in `Long` (wrapping arithmetic is bit-identical).

@Suppress("NOTHING_TO_INLINE")
private inline fun hashStep(acc: Long, b: Long): Long = acc * 31 + b

object KotlinMedia {
    // ------------------------------------------------------------ Ray tracer

    class V3(@JvmField val x: Double, @JvmField val y: Double, @JvmField val z: Double) {
        operator fun plus(b: V3) = V3(x + b.x, y + b.y, z + b.z)
        operator fun minus(b: V3) = V3(x - b.x, y - b.y, z - b.z)
        operator fun times(s: Double) = V3(x * s, y * s, z * s)
        fun dot(o: V3) = x * o.x + y * o.y + z * o.z
        fun norm(): V3 { val l = sqrt(dot(this)); return V3(x / l, y / l, z / l) }
    }

    class Sphere(val c: V3, val r: Double, val color: V3, val reflect: Double)

    private val spheres = arrayOf(
        Sphere(V3(0.0, 0.0, -3.0), 0.5, V3(0.9, 0.2, 0.2), 0.3),
        Sphere(V3(1.1, 0.0, -3.5), 0.5, V3(0.2, 0.9, 0.3), 0.3),
        Sphere(V3(-1.1, 0.0, -3.5), 0.5, V3(0.2, 0.4, 0.9), 0.3),
        Sphere(V3(0.0, 0.9, -4.0), 0.4, V3(0.95, 0.85, 0.2), 0.5),
    )
    private const val PLANE_Y = -0.5
    private const val EPS = 1e-4

    /** Returns the hit distance or NaN for a miss (avoids boxing an optional Double). */
    private fun hitSphere(s: Sphere, o: V3, d: V3): Double {
        val oc = o - s.c
        val b = oc.dot(d)
        val c = oc.dot(oc) - s.r * s.r
        val disc = b * b - c
        if (disc < 0) return Double.NaN
        val sq = sqrt(disc)
        val t0 = -b - sq
        if (t0 > EPS) return t0
        val t1 = -b + sq
        return if (t1 > EPS) t1 else Double.NaN
    }

    private fun trace(o: V3, d: V3, depth: Int, light: V3): V3 {
        var bestT = Double.MAX_VALUE
        var best = -1
        for (i in spheres.indices) {
            val t = hitSphere(spheres[i], o, d)
            if (!t.isNaN() && t < bestT) { bestT = t; best = i }
        }
        var plane = false
        if (d.y < -1e-9) {
            val t = (PLANE_Y - o.y) / d.y
            if (t > EPS && t < bestT) { bestT = t; plane = true; best = -1 }
        }
        if (best < 0 && !plane) {
            val k = 0.5 * (d.y + 1)
            return V3(1.0, 1.0, 1.0) * (1 - k) + V3(0.5, 0.7, 1.0) * k
        }
        val p = o + d * bestT
        val n: V3
        val color: V3
        val reflect: Double
        if (best >= 0) {
            val s = spheres[best]
            n = (p - s.c) * (1 / s.r); color = s.color; reflect = s.reflect
        } else {
            val parity = (floor(p.x).toLong() + floor(p.z).toLong()) and 1L
            val g = if (parity == 0L) 0.9 else 0.3
            n = V3(0.0, 1.0, 0.0); color = V3(g, g, g); reflect = 0.2
        }
        var diffuse = max(n.dot(light), 0.0)
        val sp = p + n * EPS
        if (spheres.any { !hitSphere(it, sp, light).isNaN() }) diffuse *= 0.3
        var col = color * (0.1 + 0.9 * diffuse)
        if (depth < 2 && reflect > 0) {
            val rd = d - n * (2 * d.dot(n))
            col = col * (1 - reflect) + trace(sp, rd, depth + 1, light) * reflect
        }
        return col
    }

    fun raytrace(w: Int, h: Int): ByteArray {
        val light = V3(-0.6, 1.0, 0.5).norm()
        val origin = V3(0.0, 0.2, 0.5)
        val offX = doubleArrayOf(0.25, 0.75, 0.25, 0.75)
        val offY = doubleArrayOf(0.25, 0.25, 0.75, 0.75)
        val out = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var acc = V3(0.0, 0.0, 0.0)
                for (s in 0 until 4) {
                    val u = ((x + offX[s]) / w) * 2 - 1
                    val v = 1 - ((y + offY[s]) / h) * 2
                    acc = acc + trace(origin, V3(u, v, -1.5).norm(), 0, light)
                }
                val c = acc * 0.25
                val i = (y * w + x) * 4
                out[i] = (sqrt(min(max(c.x, 0.0), 1.0)) * 255).toInt().toByte()
                out[i + 1] = (sqrt(min(max(c.y, 0.0), 1.0)) * 255).toInt().toByte()
                out[i + 2] = (sqrt(min(max(c.z, 0.0), 1.0)) * 255).toInt().toByte()
                out[i + 3] = 255.toByte()
            }
        }
        return out
    }

    fun raytraceBench(size: Int): Long {
        var acc = 0L
        for (b in raytrace(size, size)) acc = hashStep(acc, (b.toInt() and 0xff).toLong())
        return acc
    }

    // ------------------------------------------------------------ Rasterizer

    @Suppress("NOTHING_TO_INLINE")
    private inline fun edge(ax: Int, ay: Int, bx: Int, by: Int, px: Int, py: Int): Int =
        (bx - ax) * (py - ay) - (by - ay) * (px - ax)

    fun rasterize(n: Int): Long {
        val w = 512
        val h = 512
        val color = IntArray(w * h)
        val depth = IntArray(w * h) { Int.MAX_VALUE }
        val rng = XorShift32(0x0BAD_C0DE)
        repeat(n) {
            val cx = umod(rng.next(), w)
            val cy = umod(rng.next(), h)
            val x0 = cx + umod(rng.next(), 64) - 32
            val y0 = cy + umod(rng.next(), 64) - 32
            var x1 = cx + umod(rng.next(), 64) - 32
            var y1 = cy + umod(rng.next(), 64) - 32
            var x2 = cx + umod(rng.next(), 64) - 32
            var y2 = cy + umod(rng.next(), 64) - 32
            val z = umod(rng.next(), 10_000)
            val c = rng.next()
            val area = edge(x0, y0, x1, y1, x2, y2)
            if (area == 0) return@repeat
            if (area < 0) {
                var t = x1; x1 = x2; x2 = t
                t = y1; y1 = y2; y2 = t
            }
            val minx = max(minOf(x0, x1, x2), 0)
            val maxx = min(maxOf(x0, x1, x2), w - 1)
            val miny = max(minOf(y0, y1, y2), 0)
            val maxy = min(maxOf(y0, y1, y2), h - 1)
            if (minx > maxx || miny > maxy) return@repeat
            for (py in miny..maxy) {
                for (px in minx..maxx) {
                    val w0 = edge(x1, y1, x2, y2, px, py)
                    val w1 = edge(x2, y2, x0, y0, px, py)
                    val w2 = edge(x0, y0, x1, y1, px, py)
                    if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
                        val i = py * w + px
                        if (z < depth[i]) { depth[i] = z; color[i] = c }
                    }
                }
            }
        }
        var acc = 0L
        for (c in color) acc = hashStep(acc, c.toLong() and 0xffffffffL)
        return acc
    }

    // ------------------------------------------------------------ Image processing

    fun imagePipeline(size: Int): Long {
        val w = size
        val h = size
        val src = ByteArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 3
                src[i] = ((x xor y) and 0xff).toByte()
                src[i + 1] = ((x * 2 + y) and 0xff).toByte()
                src[i + 2] = (((x * y) shr 4) and 0xff).toByte()
            }
        }
        val k = intArrayOf(1, 4, 6, 4, 1)
        val tmp = ByteArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                for (ch in 0 until 3) {
                    var s = 0
                    for (t in 0 until 5) {
                        val xx = min(max(x + t - 2, 0), w - 1)
                        s += k[t] * (src[(y * w + xx) * 3 + ch].toInt() and 0xff)
                    }
                    tmp[(y * w + x) * 3 + ch] = ((s + 8) ushr 4).toByte()
                }
            }
        }
        val blur = ByteArray(w * h * 3)
        for (y in 0 until h) {
            for (x in 0 until w) {
                for (ch in 0 until 3) {
                    var s = 0
                    for (t in 0 until 5) {
                        val yy = min(max(y + t - 2, 0), h - 1)
                        s += k[t] * (tmp[(yy * w + x) * 3 + ch].toInt() and 0xff)
                    }
                    blur[(y * w + x) * 3 + ch] = ((s + 8) ushr 4).toByte()
                }
            }
        }
        val dw = w / 2
        val dh = h / 2
        val small = ByteArray(dw * dh * 3)
        for (y in 0 until dh) {
            val sy = (y.toLong() * ((h - 1).toLong() shl 16)) / (dh - 1)
            val y0 = (sy shr 16).toInt()
            val fy = sy and 0xffff
            val y1 = min(y0 + 1, h - 1)
            for (x in 0 until dw) {
                val sx = (x.toLong() * ((w - 1).toLong() shl 16)) / (dw - 1)
                val x0 = (sx shr 16).toInt()
                val fx = sx and 0xffff
                val x1 = min(x0 + 1, w - 1)
                for (ch in 0 until 3) {
                    val p00 = (blur[(y0 * w + x0) * 3 + ch].toInt() and 0xff).toLong()
                    val p10 = (blur[(y0 * w + x1) * 3 + ch].toInt() and 0xff).toLong()
                    val p01 = (blur[(y1 * w + x0) * 3 + ch].toInt() and 0xff).toLong()
                    val p11 = (blur[(y1 * w + x1) * 3 + ch].toInt() and 0xff).toLong()
                    val top = p00 * (65536 - fx) + p10 * fx
                    val bot = p01 * (65536 - fx) + p11 * fx
                    small[(y * dw + x) * 3 + ch] = ((top * (65536 - fy) + bot * fy) ushr 32).toByte()
                }
            }
        }
        val lut = IntArray(256) { i -> min(max(((i - 128) * 6 / 5) + 138, 0), 255) }
        var sum = 0L
        var i = 0
        while (i < small.size) {
            val r = lut[small[i].toInt() and 0xff]
            val g = lut[small[i + 1].toInt() and 0xff]
            val b = lut[small[i + 2].toInt() and 0xff]
            val yv = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
            val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
            val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
            sum += yv.toLong() + 3 * u.toLong() + 7 * v.toLong()
            i += 3
        }
        return sum
    }

    // ------------------------------------------------------------ Video processing

    fun videoPipeline(frames: Int): Long {
        val w = 640
        val h = 360
        val bs = 16
        val luma = ByteArray(w * h)
        var cur = ByteArray(w * h)
        var prev = ByteArray(w * h)
        var hasPrev = false
        var diffTotal = 0L
        var sadTotal = 0L
        var mvx = 0L
        var mvy = 0L
        for (f in 0 until frames) {
            val sqx = 40 + f * 6
            val sqy = 60 + f * 3
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val r: Int
                    val g: Int
                    val b: Int
                    if (x >= sqx && x < sqx + 80 && y >= sqy && y < sqy + 80) {
                        r = 240; g = 240; b = 240
                    } else {
                        r = (x + f * 4) and 0xff; g = (y * 2 + f * 2) and 0xff; b = ((x + y) / 2) and 0xff
                    }
                    luma[y * w + x] = (((66 * r + 129 * g + 25 * b + 128) ushr 8) + 16).toByte()
                }
            }
            for (y in 0 until h) {
                for (x in 0 until w) {
                    if (y == 0 || x == 0 || y == h - 1 || x == w - 1) {
                        cur[y * w + x] = luma[y * w + x]
                    } else {
                        var s = 0
                        for (dy in 0 until 3) for (dx in 0 until 3) s += luma[(y + dy - 1) * w + (x + dx - 1)].toInt() and 0xff
                        cur[y * w + x] = (s / 9).toByte()
                    }
                }
            }
            if (hasPrev) {
                for (i in 0 until w * h) diffTotal += abs((cur[i].toInt() and 0xff) - (prev[i].toInt() and 0xff)).toLong()
                for (by in 0 until h / bs) {
                    for (bx in 0 until w / bs) {
                        val ox = bx * bs
                        val oy = by * bs
                        var best = -1L // u64::MAX; compared unsigned below
                        var bdx = 0
                        var bdy = 0
                        for (dy in -2..2) {
                            for (dx in -2..2) {
                                val px = ox + dx
                                val py = oy + dy
                                if (px < 0 || py < 0 || px + bs > w || py + bs > h) continue
                                var sad = 0L
                                for (yy in 0 until bs) {
                                    val crow = (oy + yy) * w + ox
                                    val prow = (py + yy) * w + px
                                    for (xx in 0 until bs) sad += abs((cur[crow + xx].toInt() and 0xff) - (prev[prow + xx].toInt() and 0xff)).toLong()
                                }
                                if (java.lang.Long.compareUnsigned(sad, best) < 0) { best = sad; bdx = dx; bdy = dy }
                            }
                        }
                        sadTotal += best
                        mvx += bdx.toLong(); mvy += bdy.toLong()
                    }
                }
            }
            val t = cur; cur = prev; prev = t
            hasPrev = true
        }
        return hashStep(hashStep(hashStep(diffTotal, sadTotal), mvx + 100_000), mvy + 100_000)
    }

    // ------------------------------------------------------------ 3D mesh generation

    class MeshResult(val vertices: Int, val triangles: Int, val objBytes: Int, val checksum: Long)

    fun generateMesh(levels: Int): MeshResult {
        val t = (1.0 + sqrt(5.0)) / 2.0
        // Vertices as flat xyz triples.
        var vx = DoubleArray(0); var vy = DoubleArray(0); var vz = DoubleArray(0)
        var vn = 0
        fun push(x: Double, y: Double, z: Double): Int {
            if (vn == vx.size) {
                val cap = max(16, vx.size * 2)
                vx = vx.copyOf(cap); vy = vy.copyOf(cap); vz = vz.copyOf(cap)
            }
            val l = sqrt(x * x + y * y + z * z)
            vx[vn] = x / l; vy[vn] = y / l; vz[vn] = z / l
            return vn++
        }
        val base = arrayOf(
            doubleArrayOf(-1.0, t, 0.0), doubleArrayOf(1.0, t, 0.0), doubleArrayOf(-1.0, -t, 0.0), doubleArrayOf(1.0, -t, 0.0),
            doubleArrayOf(0.0, -1.0, t), doubleArrayOf(0.0, 1.0, t), doubleArrayOf(0.0, -1.0, -t), doubleArrayOf(0.0, 1.0, -t),
            doubleArrayOf(t, 0.0, -1.0), doubleArrayOf(t, 0.0, 1.0), doubleArrayOf(-t, 0.0, -1.0), doubleArrayOf(-t, 0.0, 1.0),
        )
        for (p in base) push(p[0], p[1], p[2])
        var faces = intArrayOf(
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11,
            1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9,
            4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
        )
        repeat(levels) {
            val cache = HashMap<Long, Int>()
            fun midpoint(a: Int, b: Int): Int {
                val key = (min(a, b).toLong() shl 32) or max(a, b).toLong()
                cache[key]?.let { return it }
                val i = push((vx[a] + vx[b]) * 0.5, (vy[a] + vy[b]) * 0.5, (vz[a] + vz[b]) * 0.5)
                cache[key] = i
                return i
            }
            val next = IntArray(faces.size * 4)
            var o = 0
            var f = 0
            while (f < faces.size) {
                val f0 = faces[f]; val f1 = faces[f + 1]; val f2 = faces[f + 2]
                val a = midpoint(f0, f1); val b = midpoint(f1, f2); val c = midpoint(f2, f0)
                next[o++] = f0; next[o++] = a; next[o++] = c
                next[o++] = f1; next[o++] = b; next[o++] = a
                next[o++] = f2; next[o++] = c; next[o++] = b
                next[o++] = a; next[o++] = b; next[o++] = c
                f += 3
            }
            faces = next
        }
        for (i in 0 until vn) {
            val x = vx[i]; val y = vy[i]; val z = vz[i]
            val d = 1.0 + 0.08 * sin(5.0 * x) * sin(5.0 * y) * sin(5.0 * z) + 0.03 * sin(13.0 * x + 7.0 * y)
            vx[i] = x * d; vy[i] = y * d; vz[i] = z * d
        }
        val nx = DoubleArray(vn); val ny = DoubleArray(vn); val nz = DoubleArray(vn)
        var f = 0
        while (f < faces.size) {
            val ia = faces[f]; val ib = faces[f + 1]; val ic = faces[f + 2]
            val ux = vx[ib] - vx[ia]; val uy = vy[ib] - vy[ia]; val uz = vz[ib] - vz[ia]
            val wx = vx[ic] - vx[ia]; val wy = vy[ic] - vy[ia]; val wz = vz[ic] - vz[ia]
            val cx = uy * wz - uz * wy; val cy = uz * wx - ux * wz; val cz = ux * wy - uy * wx
            for (i in intArrayOf(ia, ib, ic)) { nx[i] += cx; ny[i] += cy; nz[i] += cz }
            f += 3
        }
        for (i in 0 until vn) {
            val l = sqrt(nx[i] * nx[i] + ny[i] * ny[i] + nz[i] * nz[i])
            nx[i] /= l; ny[i] /= l; nz[i] /= l
        }
        val obj = StringBuilder(vn * 64 + faces.size / 3 * 32)
        for (i in 0 until vn) obj.append(String.format(Locale.ROOT, "v %.4f %.4f %.4f\n", vx[i], vy[i], vz[i]))
        for (i in 0 until vn) obj.append(String.format(Locale.ROOT, "vn %.4f %.4f %.4f\n", nx[i], ny[i], nz[i]))
        f = 0
        while (f < faces.size) {
            val a = faces[f] + 1; val b = faces[f + 1] + 1; val c = faces[f + 2] + 1
            obj.append("f ").append(a).append("//").append(a).append(' ')
                .append(b).append("//").append(b).append(' ')
                .append(c).append("//").append(c).append('\n')
            f += 3
        }
        var q = 0L
        for (i in 0 until vn) {
            q += roundHalfAway(vx[i] * 1e6) + roundHalfAway(vy[i] * 1e6) + roundHalfAway(vz[i] * 1e6)
            q += roundHalfAway(nx[i] * 1e3) + roundHalfAway(ny[i] * 1e3) + roundHalfAway(nz[i] * 1e3)
        }
        val tris = faces.size / 3
        val checksum = hashStep(hashStep(vn.toLong(), tris.toLong()), q)
        return MeshResult(vn, tris, obj.toString().toByteArray(Charsets.UTF_8).size, checksum)
    }
}
