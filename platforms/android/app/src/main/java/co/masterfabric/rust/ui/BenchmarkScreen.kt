package co.masterfabric.rust.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.masterfabric.rust.bench.KotlinMedia
import co.masterfabric.rust.bridge.CoreBridge
import co.masterfabric.rust.core.Action
import co.masterfabric.rust.core.BenchMeasurement
import co.masterfabric.rust.core.BenchRow
import co.masterfabric.rust.core.BenchStatus
import co.masterfabric.rust.core.BenchmarkState
import co.masterfabric.rust.core.MeshSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Benchmark screen. Test list, categories, order, progress, ratios and verdicts live in Rust;
 * measurements run on a Rust background thread (Rust single-thread, Rust rayon, then the same
 * algorithm in Kotlin). 36 tests in 8 categories; run all, one category, or one test.
 * Test #37 (FFI boundary / chatty calls) is its own section: [FfiSection].
 */
@Composable
fun BenchmarkScreen(bridge: CoreBridge) {
    val b = bridge.benchmark ?: return
    val categories = remember(b.rows) { b.rows.map { it.category }.distinct() }
    val busy = b.running || b.ffi.running
    var chartCategory by rememberSaveable { mutableStateOf("Image") }
    val listState = rememberLazyListState()

    val sections = buildList<Pair<String, @Composable () -> Unit>> {
        add("env" to { EnvironmentCard(bridge, b) })
        add("controls" to { Controls(bridge, b) })
        add("categories" to { CategoryRunGrid(bridge, b, categories) })
        b.summary?.let { s -> add("summary" to { SummaryCard(s, b.lastTotalMs) }) }
        add("media" to { SectionTitle("Rendered output: Rust vs Kotlin", "photo.fill"); Spacer(Modifier.height(10.dp)); MediaPreviewCard(bridge, b.running || b.ffi.running) })
        add("table" to { SectionTitle("Results table", "chart.bar.doc.horizontal"); Spacer(Modifier.height(10.dp)); ResultsTable(b.rows, categories, b.rustThreads) })
        add("charts" to { SectionTitle("Charts", "chart.xyaxis.line"); Spacer(Modifier.height(10.dp)); RatioChartCard(b.rows) })
        add("durations" to { DurationChartCard(b.rows, categories, chartCategory) { chartCategory = it } })
        add("perf" to { SectionTitle("App performance", "speedometer"); Spacer(Modifier.height(10.dp)); AppPerfCard(bridge) })
        add("ffi" to { FfiSection(bridge, b.ffi, busy) })
        categories.forEach { cat ->
            val rows = b.rows.filter { it.category == cat }
            add("cat-$cat" to { CategoryHeader(cat, rows, busy) { bridge.dispatch(Action.RunCategory(cat)) } })
            rows.forEach { row -> add(row.id to { BenchCard(row, b.rustThreads, busy) { bridge.dispatch(Action.RunBenchmark(row.id)) } }) }
        }
        add("footer" to {
            Text(
                "Each test: 1 warm-up run + the shown number of measured runs. Ratio = Kotlin avg / Rust single-thread avg. " +
                    "The Rust rayon variant is shown separately and is not part of the ratio. Checksums confirm both languages " +
                    "(and the parallel variant) produce the same result.",
                style = Type.caption,
            )
        })
    }

    // Debug/automation: `--es scrollTo <key>` scrolls there when a run finishes.
    val target = bridge.scrollTo
    val wasRunning = remember { booleanArrayOf(false, false) }
    LaunchedEffect(b.running, b.ffi.running) {
        val finished = (wasRunning[0] && !b.running) || (wasRunning[1] && !b.ffi.running)
        wasRunning[0] = b.running
        wasRunning[1] = b.ffi.running
        if (finished && !b.running && !b.ffi.running && target != null) {
            delay(800)
            val idx = sections.indexOfFirst { it.first == target }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Benchmark", back = { bridge.dispatch(Action.Back) }) {
            TextButton(onClick = { bridge.dispatch(Action.ResetBenchmarks) }, enabled = !busy) { Text("Reset", color = if (busy) Palette.tertiary else Palette.orange) }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().testTag("benchmark-list"),
        ) {
            items(sections, key = { it.first }) { (_, content) -> Column { content() } }
        }
    }
}

@Composable
private fun Controls(bridge: CoreBridge, b: BenchmarkState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { bridge.dispatch(Action.RunAllBenchmarks) },
            enabled = !(b.running || b.ffi.running),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.orange),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run-all"),
        ) {
            Icon(if (b.running) Icons.Filled.HourglassTop else Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (b.running) "Running…" else "Run all (${b.rows.size} tests)", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        }
        if (b.running || b.progress > 0) {
            Row {
                Text(b.currentLabel ?: if (b.running) "Preparing…" else "Done", style = Type.caption, modifier = Modifier.weight(1f))
                Text("${(b.progress * 100).toInt()}%", style = Type.caption)
            }
            LinearProgressIndicator(progress = { b.progress.toFloat() }, color = Palette.orange, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryRunGrid(bridge: CoreBridge, b: BenchmarkState, categories: List<String>) {
    val busy = b.running || b.ffi.running
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { cat ->
                    val rows = b.rows.filter { it.category == cat }
                    val g = geomean(rows.mapNotNull { it.ratio })
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.card)
                            .clickable(enabled = !busy) { bridge.dispatch(Action.RunCategory(cat)) }
                            .padding(8.dp)
                            .testTag("run-category-$cat"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(categorySymbol(cat), categoryColor(cat), 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cat, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
                            Text(if (g != null) "${rows.size} tests · ${f("%.2f×", g)}" else "${rows.size} tests", style = Type.caption2)
                        }
                        Icon(Icons.Filled.PlayArrow, null, tint = if (busy) Palette.tertiary else categoryColor(cat), modifier = Modifier.size(16.dp))
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: String, rows: List<BenchRow>, running: Boolean, run: () -> Unit) {
    val g = geomean(rows.mapNotNull { it.ratio })
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(icon(categorySymbol(category)), null, tint = categoryColor(category), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(category, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = categoryColor(category)))
        Spacer(Modifier.width(8.dp))
        Text("${rows.size} tests", style = Type.caption)
        if (g != null) {
            Spacer(Modifier.width(6.dp))
            Text(f("geomean %.2f×", g), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ratioColor(g)))
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = run, enabled = !running) {
            Icon(Icons.Filled.PlayArrow, null, tint = categoryColor(category), modifier = Modifier.size(16.dp))
            Text("Run", color = categoryColor(category))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnvironmentCard(bridge: CoreBridge, b: BenchmarkState) {
    val d = bridge.device
    Card {
        Text("${d.deviceName} · ${d.os}", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Kotlin ${d.kotlinBuild}", warn = d.kotlinBuild != "Release")
            Chip("Rust ${b.rustProfile}", warn = b.rustProfile != "release")
            Chip("${d.cores} cores · ${d.memoryGB}")
            Chip(d.vm)
        }
        Spacer(Modifier.height(8.dp))
        Text("${d.machine} · ${b.coreInfo}", style = Type.monoCaption.copy(color = Palette.tertiary))
        val parts = buildList {
            if (d.isEmulator) add("Emulator: the measured CPU is the host Mac's (arm64 guest on Apple silicon), not a real phone.")
            if (d.kotlinBuild != "Release") add("Debuggable build: ART disables some optimizations; Kotlin results will be slow.")
            if (b.rustProfile != "release") add("Rust debug build: results are meaningless.")
            add("ART compiles Kotlin with its JIT (profile-guided AOT comes later via dex2oat); warm-up runs absorb JIT time.")
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Icon(Icons.Filled.Warning, null, tint = Palette.orange, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(parts.joinToString(" "), style = TextStyle(fontSize = 12.sp, color = Palette.orange))
        }
    }
}

/** Rust and Kotlin ray tracer output side by side + the 3D mesh generated in Rust. */
@Composable
private fun MediaPreviewCard(bridge: CoreBridge, busy: Boolean) {
    var rustImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var kotlinImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var diffPixels by remember { mutableStateOf<Int?>(null) }
    var mesh by remember { mutableStateOf<MeshSummary?>(null) }
    var rendering by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val size = 256

    suspend fun render() {
        rendering = true
        val core = bridge.core
        val (r, k, m) = withContext(Dispatchers.Default) {
            Triple(core.raytraceImage(size.toUInt(), size.toUInt()), KotlinMedia.raytrace(size, size), core.generateMesh(5u))
        }
        rustImage = toBitmap(r, size)
        kotlinImage = toBitmap(k, size)
        var diff = 0
        var p = 0
        while (p < minOf(r.size, k.size)) {
            if (r[p] != k[p] || r[p + 1] != k[p + 1] || r[p + 2] != k[p + 2]) diff++
            p += 4
        }
        diffPixels = diff
        mesh = m
        rendering = false
    }
    // Rendered lazily, and never while a benchmark runs (it would steal CPU from the timings).
    LaunchedEffect(busy) { if (!busy && rustImage == null) render() }

    Card {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Preview("Rust", Palette.orange, rustImage, Modifier.weight(1f))
            Preview("Kotlin", Palette.kotlin, kotlinImage, Modifier.weight(1f))
        }
        diffPixels?.let { dp ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (dp == 0) Icons.Filled.Verified else Icons.Filled.Warning, null, tint = if (dp == 0) Palette.green else Palette.orange, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (dp == 0) "Both outputs are pixel-identical (${size}×$size)" else "$dp pixels differ (floating-point rounding)", style = TextStyle(fontSize = 12.sp, color = if (dp == 0) Palette.green else Palette.orange))
            }
        }
        mesh?.let { m ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ViewInAr, null, tint = Palette.purple, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Rust 3D mesh: ${"%,d".format(m.vertices.toInt())} vertices · ${"%,d".format(m.triangles.toInt())} triangles · OBJ ${f("%.1f", m.objBytes.toDouble() / 1e6)} MB", style = Type.caption.copy(color = Palette.label))
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { scope.launch { render() } }, enabled = !rendering && !busy) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (rendering) "Rendering…" else "Render again")
        }
    }
}

private fun toBitmap(rgba: ByteArray, n: Int): ImageBitmap {
    val px = IntArray(n * n) { i ->
        val o = i * 4
        (0xFF shl 24) or ((rgba[o].toInt() and 0xff) shl 16) or ((rgba[o + 1].toInt() and 0xff) shl 8) or (rgba[o + 2].toInt() and 0xff)
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
private fun Preview(title: String, color: Color, image: ImageBitmap?, modifier: Modifier) {
    Column(modifier) {
        Text("$title ray tracer", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color))
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Palette.secondary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) Image(image, null, modifier = Modifier.fillMaxSize()) else CircularProgressIndicator(color = color, modifier = Modifier.size(24.dp))
        }
    }
}

/** Summary data table: all tests, grouped by category. */
@Composable
private fun ResultsTable(rows: List<BenchRow>, categories: List<String>, threads: UInt) {
    val num = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Palette.label, textAlign = TextAlign.End)
    val head = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.secondary, textAlign = TextAlign.End)
    @Composable
    fun Cells(name: @Composable () -> Unit, a: @Composable () -> Unit, b: @Composable () -> Unit, c: @Composable () -> Unit, d: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1.45f)) { name() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { a() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { b() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { c() }
            Box(Modifier.weight(0.85f), contentAlignment = Alignment.CenterEnd) { d() }
        }
    }
    @Composable
    fun Ms(m: BenchMeasurement?, active: Boolean, color: Color = Palette.label) {
        when {
            m != null -> Text(formatMs(m.avgMs), style = num.copy(color = color), maxLines = 1)
            active -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Palette.orange)
            else -> Text("—", style = num.copy(color = Palette.tertiary))
        }
    }
    Card {
        Cells({ Text("Test", style = head.copy(textAlign = TextAlign.Start)) }, { Text("Rust", style = head) }, { Text("Rayon", style = head) }, { Text("Kotlin", style = head) }, { Text("Ratio", style = head) })
        categories.forEach { cat ->
            Text(cat.uppercase(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = categoryColor(cat)), modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x22000000)))
            rows.filter { it.category == cat }.forEach { row ->
                Cells(
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(shortName(row.id, row.name), style = TextStyle(fontSize = 12.sp, color = Palette.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (row.checksumMatch == false) Icon(Icons.Filled.Cancel, null, tint = Palette.red, modifier = Modifier.size(12.dp))
                        }
                    },
                    { Ms(row.rust, row.status == BenchStatus.RunningRust && row.rust == null) },
                    { if (row.hasParallel) Ms(row.rustParallel, row.status == BenchStatus.RunningRust && row.rust != null, Palette.brown) else Text("n/a", style = num.copy(color = Palette.tertiary)) },
                    { Ms(row.platform, row.status == BenchStatus.RunningPlatform) },
                    {
                        val r = row.ratio
                        if (r != null) Text(f("%.2f×", r), style = num.copy(fontWeight = FontWeight.SemiBold, color = ratioColor(r)), maxLines = 1)
                        else Text("—", style = num.copy(color = Palette.tertiary))
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Rust = single thread · Rayon = Rust on $threads threads (not in ratio) · Ratio = Kotlin / Rust", style = Type.caption2)
    }
}

/** Kotlin/Rust ratio per test (1× = equal). */
@Composable
private fun RatioChartCard(rows: List<BenchRow>) {
    val done = rows.filter { it.ratio != null }
    Card {
        Text("Rust vs Kotlin (ratio)", style = Type.headline)
        Text("Bars right of 1× mean Rust is faster (single thread vs single thread).", style = Type.caption)
        Spacer(Modifier.height(8.dp))
        if (done.isEmpty()) {
            EmptyChartHint()
        } else {
            HBarChart(done.map { Bar(shortName(it.id, it.name), it.ratio!!, categoryColor(it.category), f("%.2f×", it.ratio)) }, rule = 1.0, rowHeight = 22)
            Spacer(Modifier.height(8.dp))
            Legend(allCategories.map { it to categoryColor(it) })
        }
    }
}

/** Rust, Rust (rayon) and Kotlin side by side in ms for the selected category. */
@Composable
private fun DurationChartCard(rows: List<BenchRow>, categories: List<String>, selection: String, select: (String) -> Unit) {
    val sel = rows.filter { it.category == selection }
    val groups = sel.filter { it.rust != null || it.platform != null }.map { r ->
        BarGroup(shortName(r.id, r.name), listOf("Rust" to r.rust?.avgMs, "Rust (rayon)" to r.rustParallel?.avgMs, "Kotlin" to r.platform?.avgMs))
    }
    val colors = mapOf("Rust" to Palette.orange, "Rust (rayon)" to Palette.brown, "Kotlin" to Palette.kotlin)
    Card {
        Text("Duration (ms, lower is better)", style = Type.headline)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { c ->
                val on = c == selection
                Text(
                    c,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else categoryColor(c)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) categoryColor(c) else categoryColor(c).copy(alpha = 0.12f))
                        .clickable { select(c) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (groups.isEmpty()) {
            EmptyChartHint()
        } else {
            GroupedBarChart(groups, colors) { formatMs(it) }
            Spacer(Modifier.height(8.dp))
            Legend(colors.toList())
        }
    }
}

@Composable
fun EmptyChartHint() {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        Text("Run the tests to see charts", style = Type.subheadSecondary)
    }
}

@Composable
private fun SummaryCard(summary: String, totalMs: Double?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.orange.copy(alpha = 0.10f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(summary, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
        totalMs?.let { Text("Total time (incl. warm-up, all variants): ${f("%.2f", it / 1000)} s", style = Type.caption) }
        Text("Ratio > 1: Rust faster (orange) · < 1: Kotlin faster (purple)", style = Type.caption2)
    }
}

@Composable
private fun BenchCard(row: BenchRow, threads: UInt, disabled: Boolean, run: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(row.symbol, categoryColor(row.category), 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
                Text("${row.description} · ${row.iterations}× measured", style = Type.caption)
            }
            IconButton(onClick = run, enabled = !disabled) {
                Icon(Icons.Filled.PlayCircle, "Run ${row.name}", tint = if (disabled) Palette.tertiary else Palette.orange, modifier = Modifier.size(28.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LangColumn("Rust", Palette.orange, row.rust, row.status == BenchStatus.RunningRust, Modifier.weight(1f))
            LangColumn("Kotlin", Palette.kotlin, row.platform, row.status == BenchStatus.RunningPlatform, Modifier.weight(1f))
        }
        val r = row.rust
        val k = row.platform
        if (r != null && k != null) {
            val maxV = maxOf(r.avgMs, k.avgMs)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.fillMaxWidth((r.avgMs / maxV).toFloat().coerceAtLeast(0.02f)).height(6.dp).clip(RoundedCornerShape(50)).background(Palette.orange))
                Box(Modifier.fillMaxWidth((k.avgMs / maxV).toFloat().coerceAtLeast(0.02f)).height(6.dp).clip(RoundedCornerShape(50)).background(Palette.kotlin))
            }
        }
        if (row.hasParallel) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Layers, null, tint = Palette.brown, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                val p = row.rustParallel
                val sp = row.parallelSpeedup
                Text(
                    if (p != null) "Rust ×$threads threads (rayon): ${formatMs(p.avgMs)}" + (sp?.let { f(" · %.1f× vs 1 thread", it) } ?: "")
                    else "Rust ×$threads threads (rayon): —",
                    style = Type.caption.copy(color = Palette.label),
                    maxLines = 1,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(row.status)
            Spacer(Modifier.weight(1f))
            row.verdict?.let { Text(it, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.label)) }
            row.checksumMatch?.let { ok ->
                Spacer(Modifier.width(8.dp))
                Icon(if (ok) Icons.Filled.Verified else Icons.Filled.Cancel, null, tint = if (ok) Palette.green else Palette.red, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(if (ok) "checksum match" else "checksum mismatch", style = TextStyle(fontSize = 11.sp, color = if (ok) Palette.green else Palette.red))
            }
        }
    }
}

@Composable
private fun LangColumn(lang: String, color: Color, m: BenchMeasurement?, active: Boolean, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.08f)).padding(10.dp)) {
        Text(lang, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color))
        when {
            m != null -> {
                Text(formatMs(m.avgMs), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
                Text("best ${formatMs(m.minMs)} · ${m.iterations}×", style = Type.caption2)
            }
            active -> CircularProgressIndicator(Modifier.padding(vertical = 4.dp).size(22.dp), color = color, strokeWidth = 2.dp)
            else -> Text("—", style = TextStyle(fontSize = 20.sp, color = Palette.tertiary))
        }
    }
}

@Composable
private fun StatusBadge(status: BenchStatus) {
    val (text, color) = when (status) {
        is BenchStatus.Idle -> "Idle" to Palette.secondary
        is BenchStatus.Queued -> "Queued" to Palette.secondary
        is BenchStatus.RunningRust -> "Measuring Rust" to Palette.orange
        is BenchStatus.RunningPlatform -> "Measuring Kotlin" to Palette.kotlin
        is BenchStatus.Done -> "Done" to Palette.green
        is BenchStatus.Failed -> status.message to Palette.red
    }
    Text(
        text,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** App-level performance: launch time, dispatch → render latency, Rust dispatch cost.
 *  Values are recorded into Rust (`RecordPerf`) and rendered from Rust state. */
@Composable
fun AppPerfCard(bridge: CoreBridge) {
    val scope = rememberCoroutineScope()
    Card {
        if (bridge.perf.isEmpty()) Text("No measurements yet.", style = Type.subheadSecondary)
        bridge.perf.forEach { m ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(m.label, style = Type.subhead)
                    Text(m.detail, style = Type.caption2)
                }
                Spacer(Modifier.width(8.dp))
                Text(formatPerf(m), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { scope.launch { bridge.measureAndRecordLatency() } },
            enabled = !bridge.measuringLatency && bridge.benchmark?.running != true,
            modifier = Modifier.testTag("measure-latency"),
        ) {
            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (bridge.measuringLatency) "Measuring…" else "Measure UI latency")
        }
    }
}
