package co.masterfabric.rust.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.masterfabric.rust.bridge.CoreBridge
import co.masterfabric.rust.core.Action
import co.masterfabric.rust.core.FfiRow
import co.masterfabric.rust.core.FfiSample
import co.masterfabric.rust.core.FfiSection as FfiState

/**
 * Test #37 section: FFI boundary / chatty calls. Its own section, separate from the 8 compute
 * categories. Rows, verdicts and the crossover analysis come from Rust.
 */
@Composable
fun FfiSection(bridge: CoreBridge, ffi: FfiState, busy: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Icon(Icons.Filled.CompareArrows, null, tint = Palette.teal, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(ffi.title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.teal))
        }
        Text(ffi.intro, style = Type.caption)
        Button(
            onClick = { bridge.dispatch(Action.RunFfiBench) },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.teal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run-ffi"),
        ) {
            Icon(if (ffi.running) Icons.Filled.HourglassTop else Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (ffi.running) "Measuring…" else "Run #37 (${ffi.rows.size} cases)", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
        }
        if (ffi.running || ffi.progress > 0) {
            Row {
                Text(ffi.currentLabel ?: if (ffi.running) "Preparing…" else "Done", style = Type.caption, modifier = Modifier.weight(1f))
                Text("${(ffi.progress * 100).toInt()}%", style = Type.caption)
            }
            LinearProgressIndicator(progress = { ffi.progress.toFloat() }, color = Palette.teal, modifier = Modifier.fillMaxWidth())
        }
        ffi.crossingNs?.let { c ->
            Card {
                Row {
                    Text("Extra cost per Kotlin→Rust call (UniFFI over JNA)", style = Type.caption, modifier = Modifier.weight(1f))
                    ffi.lastTotalMs?.let { Text(f("%.1f s total", it / 1000), style = Type.caption) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cost("u64", c, Modifier.weight(1f))
                    ffi.rows.filter { it.group == "marshal" }.forEach { r -> r.overheadNs?.let { Cost(shortCase(r.caseId), it, Modifier.weight(1f)) } }
                }
            }
        }
        ffi.crossingNs?.takeIf { it >= 1_000.0 }?.let { c ->
            Card(Modifier.testTag("ffi-jna-note")) {
                Text(
                    "JNA crossing: ≈ " + f("%.1f µs", c / 1000) + " per Kotlin→Rust call on this device",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.red),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "That is ≈ 3,000× the iOS twin, where Swift calls the same Rust function directly " +
                        "through C in ≈ 1.9 ns. UniFFI's Kotlin bindings go through JNA (libffi + a " +
                        "RustCallStatus structure per call). Keep calls coarse: one Action per user event, " +
                        "batched work (see \"Rust batched\"). Future option: a hand-written direct JNI " +
                        "entry point for hot scalar calls.",
                    style = Type.caption,
                )
            }
        }
        Card(Modifier.testTag("ffi-table")) { Table(ffi.rows) }
        Card(Modifier.testTag("ffi-chart")) { Chart(ffi.rows.filter { it.group == "sweep" }) }
        ffi.summary?.let { s ->
            Card {
                Row {
                    Icon(Icons.Filled.GpsFixed, null, tint = Palette.teal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(s, style = Type.subhead)
                }
            }
        }
        Text(
            "ns per operation, median of 5 samples (1 warm-up). Each sample ≈ 1,000,000 operations: reps × calls. " +
                "Kotlin inline = no FFI; Rust per call = one UniFFI call per op (Kotlin → JNA direct mapping → libffi → Rust, " +
                "plus a RustCallStatus struct per call); Rust batched = one UniFFI call doing all ops in Rust. Overhead = per call − inline. " +
                "Checksums of all variants must equal Rust's reference." +
                (if (bridge.device.isEmulator) " Emulator: host Mac CPU, not a real phone; JNA costs differ on devices." else ""),
            style = Type.caption,
        )
    }
}

@Composable
private fun Cost(title: String, ns: Double, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Palette.groupedBackground).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("+" + fmt(ns), style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (ns < 5) Palette.teal else if (ns < 100) Palette.orange else Palette.red))
        Text("$title · ns", style = Type.caption2)
    }
}

private fun shortCase(id: String) = when (id) {
    "string" -> "String"
    "record" -> "record"
    "vec16" -> "Vec×16"
    else -> id
}

private fun fmt(ns: Double): String = if (ns >= 100) f("%.0f", ns) else if (ns >= 10) f("%.1f", ns) else f("%.2f", ns)

@Composable
private fun Table(rows: List<FfiRow>) {
    val head = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary, textAlign = TextAlign.End)
    @Composable
    fun Line(label: @Composable () -> Unit, a: @Composable () -> Unit, b: @Composable () -> Unit, c: @Composable () -> Unit, d: @Composable () -> Unit, e: @Composable () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1.25f)) { label() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { a() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { b() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { c() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { d() }
            Box(Modifier.weight(0.45f), contentAlignment = Alignment.CenterEnd) { e() }
        }
    }
    val num = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Palette.label, textAlign = TextAlign.End)
    @Composable
    fun Ns(s: FfiSample?, running: Boolean, color: Color) {
        when {
            s != null -> Text(fmt(s.nsPerCall), style = num.copy(color = color), maxLines = 1)
            running -> CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Palette.teal)
            else -> Text("—", style = num.copy(color = Palette.tertiary))
        }
    }
    @Composable
    fun RowOf(r: FfiRow) {
        Line(
            { Text(if (r.group == "sweep") r.label.replace(" calls", "").replace(" call", "") else r.label, style = TextStyle(fontSize = 12.sp, color = Palette.label), maxLines = 1) },
            { Ns(r.platformInline, r.running, Palette.kotlin) },
            { Ns(r.rustPerCall, r.running, Palette.orange) },
            { if (r.hasBatched) Ns(r.rustBatched, r.running, Palette.orange) else Text("—", style = num.copy(color = Palette.tertiary)) },
            { r.overheadNs?.let { Text(fmt(it), style = num.copy(fontWeight = FontWeight.SemiBold)) } ?: Text("—", style = num.copy(color = Palette.tertiary)) },
            {
                when (r.checksumMatch) {
                    true -> Icon(Icons.Filled.Verified, "checksum match", tint = Palette.green, modifier = Modifier.size(14.dp))
                    false -> Icon(Icons.Filled.Cancel, "checksum mismatch", tint = Palette.red, modifier = Modifier.size(14.dp))
                    null -> Icon(Icons.Filled.RadioButtonUnchecked, null, tint = Palette.tertiary, modifier = Modifier.size(14.dp))
                }
            },
        )
    }
    Line(
        { Text("Calls", style = head.copy(textAlign = TextAlign.Start)) },
        { Text("Kotlin\ninline", style = head) },
        { Text("Rust\nper call", style = head) },
        { Text("Rust\nbatched", style = head) },
        { Text("Over-\nhead", style = head) },
        { },
    )
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x22000000)))
    rows.filter { it.group == "sweep" }.forEach { RowOf(it) }
    Text("Marshaling · 10,000 calls", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.teal), modifier = Modifier.padding(top = 6.dp))
    rows.filter { it.group == "marshal" }.forEach { RowOf(it) }
}

@Composable
private fun Chart(rows: List<FfiRow>) {
    Text("ns per operation vs calls (log–log)", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.label))
    Spacer(Modifier.height(6.dp))
    val series = listOf(
        Series("Kotlin inline", Palette.kotlin, rows.mapNotNull { r -> r.platformInline?.let { r.calls.toDouble() to it.nsPerCall } }),
        Series("Rust per call", Palette.orange, rows.mapNotNull { r -> r.rustPerCall?.let { r.calls.toDouble() to it.nsPerCall } }),
        Series("Rust batched", Palette.red, rows.mapNotNull { r -> r.rustBatched?.let { r.calls.toDouble() to it.nsPerCall } }),
    ).filter { it.points.isNotEmpty() }
    if (series.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("Run #37 to see where the crossing cost dominates.", style = Type.caption)
        }
    } else {
        LogLogChart(series, listOf(1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0), 200)
        Spacer(Modifier.height(8.dp))
        Legend(series.map { it.name to it.color })
    }
}
