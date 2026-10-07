package com.omersusin.mochi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omersusin.earlab.audio.ToneGen
import com.omersusin.earlab.audio.ToneGen.Ear
import com.omersusin.earlab.data.HearingStore
import com.omersusin.earlab.data.Threshold
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.log10

private enum class Tab(val label: String) { SWEEP("Sweep"), EARS("Ears"), HEARING("Hearing"), NOISE("Noise") }

class LabViewModel : ViewModel() {
    var freq by mutableStateOf(20f); private set
    var sweeping by mutableStateOf(false); private set
    var noiseOn by mutableStateOf(false); private set
    var noiseKind by mutableStateOf(true); private set // true = pink
    var noiseSecsLeft by mutableIntStateOf(0); private set
    private var timer: Job? = null

    fun startSweep(scope: kotlinx.coroutines.CoroutineScope) {
        if (sweeping) return
        sweeping = true
        ToneGen.sweep(
            onFreq = { freq = it },
            onDone = { sweeping = false },
        )
    }

    fun stopAll() {
        ToneGen.stop()
        sweeping = false
        noiseOn = false
        timer?.cancel()
        noiseSecsLeft = 0
    }

    fun startNoise(pink: Boolean, minutes: Int, scope: kotlinx.coroutines.CoroutineScope) {
        stopAll()
        noiseKind = pink
        noiseOn = true
        ToneGen.noise(pink)
        if (minutes > 0) {
            noiseSecsLeft = minutes * 60
            timer = scope.launch {
                while (noiseSecsLeft > 0 && noiseOn) {
                    delay(1000)
                    noiseSecsLeft--
                }
                stopAll()
            }
        }
    }

    override fun onCleared() {
        ToneGen.stop()
    }
}

@Composable
fun EarLabApp(vm: LabViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.SWEEP) }
    val snacks = remember { SnackbarHostState() }
    DisposableEffect(Unit) { onDispose { ToneGen.stop() } }
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("EarLab", style = MaterialTheme.typography.headlineMedium) })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.SWEEP, onClick = { tab = Tab.SWEEP },
                    icon = { Icon(Icons.Filled.Waves, null) }, label = { Text("Sweep") },
                )
                NavigationBarItem(
                    selected = tab == Tab.EARS, onClick = { tab = Tab.EARS },
                    icon = { Icon(Icons.Filled.MusicNote, null) }, label = { Text("Ears") },
                )
                NavigationBarItem(
                    selected = tab == Tab.HEARING, onClick = { tab = Tab.HEARING },
                    icon = { Icon(Icons.Filled.Hearing, null) }, label = { Text("Hearing") },
                )
                NavigationBarItem(
                    selected = tab == Tab.NOISE, onClick = { tab = Tab.NOISE },
                    icon = { Icon(Icons.Filled.PlayArrow, null) }, label = { Text("Noise") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { pads ->
        val mod = Modifier.padding(pads)
        when (tab) {
            Tab.SWEEP -> SweepScreen(vm, mod)
            Tab.EARS -> EarsScreen(mod)
            Tab.HEARING -> HearingScreen(mod, snacks)
            Tab.NOISE -> NoiseScreen(vm, mod)
        }
    }
}

/** Log-position arc: real sweep progress, drawn, never faked. */
@Composable
private fun SweepArc(freq: Float, modifier: Modifier = Modifier) {
    val frac = (log10(freq / 20f) / 3f).coerceIn(0f, 1f)
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        val w = size.width
        drawArc(track, 135f, 270f, false, Offset(w / 2, w / 2), w / 2, Stroke(w * 0.09f, StrokeCap.Round))
        drawArc(color, 135f, 270f * frac, false, Offset(w / 2, w / 2), w / 2, Stroke(w * 0.09f, StrokeCap.Round))
    }
}

@Composable
private fun SweepScreen(vm: LabViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SweepArc(vm.freq, Modifier.fillMaxWidth(0.7f))
        Spacer(Modifier.height(8.dp))
        Text(
            ToneGen.fmtFreq(vm.freq),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "20 Hz → 20 kHz in 30 seconds. Start quiet, let your ears adjust.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (vm.sweeping) vm.stopAll()
                else vm.startSweep(scope)
            },
        ) {
            Icon(if (vm.sweeping) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (vm.sweeping) "Stop sweep" else "Start sweep")
        }
    }
}

@Composable
private fun EarsScreen(modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf<Ear?>(null) }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Which side plays?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "A 440 Hz tone on one channel. Close your eyes — the answer should be obvious.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Ear.entries.forEach { ear ->
            val label = when (ear) {
                Ear.LEFT -> "Left ear"
                Ear.RIGHT -> "Right ear"
                Ear.BOTH -> "Both (reference)"
            }
            Button(
                onClick = {
                    active = ear
                    ToneGen.channel(ear, onDone = { active = null })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (active == ear) Icons.Filled.Stop else Icons.Filled.PlayArrow, null,
                )
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
        OutlinedButton(onClick = { active = null; ToneGen.stop() }, modifier = Modifier.fillMaxWidth()) {
            Text("Silence")
        }
    }
}

private val HEAR_FREQS = listOf(250, 500, 1000, 2000, 4000, 8000)
private val HEAR_LEVELS = listOf(-10f, -16f, -22f, -28f, -34f, -40f, -46f)

@Composable
private fun HearingScreen(modifier: Modifier = Modifier, snacks: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { HearingStore(context) }
    val saved by store.lastResult.collectAsState(initial = "")
    var step by remember { mutableIntStateOf(0) } // 0..11 = L/R x 6 freqs, 12 = done
    var levelIdx by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(listOf<Threshold>()) }
    var running by remember { mutableStateOf(false) }

    fun current(): Triple<String, Int, Float> {
        val ear = if (step < 6) "L" else "R"
        val freq = HEAR_FREQS[step % 6]
        return Triple(ear, freq, HEAR_LEVELS[levelIdx.coerceAtMost(HEAR_LEVELS.lastIndex)])
    }

    fun playStep() {
        val (ear, freq, db) = current()
        running = true
        ToneGen.tone(
            freq.toFloat(), db,
            if (ear == "L") Ear.LEFT else Ear.RIGHT,
            onDone = { running = false },
        )
    }

    fun advance(lockedDb: Float?) {
        val (ear, freq, _) = current()
        if (lockedDb != null) results = results + Threshold(ear, freq, lockedDb.toInt())
        ToneGen.stop()
        running = false
        if (step >= 11) {
            step = 12
            scope.launch { store.save(results) }
        } else {
            step++
            levelIdx = 0
        }
    }

    DisposableEffect(Unit) { onDispose { ToneGen.stop() } }

    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hearing walkthrough", style = MaterialTheme.typography.headlineSmall)
        if (step >= 12) {
            Text(
                "Done. Lower is better — these are rough thresholds, not a medical test.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results.size) { i ->
                    val r = results[i]
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${if (r.ear == "L") "Left" else "Right"} · ${r.freqHz} Hz",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${r.dbFs} dB",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
            OutlinedButton(onClick = { step = 0; levelIdx = 0; results = emptyList() }) {
                Text("Run again")
            }
        } else {
            val (ear, freq, db) = current()
            LinearProgressIndicator(progress = { step / 12f }, modifier = Modifier.fillMaxWidth())
            Text(
                "${if (ear == "L") "Left" else "Right"} ear · ${freq} Hz · ${db.toInt()} dB",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Tap Play, then tap “I hear it” at the faintest level you can still hear. " +
                    "If you hear nothing, go quieter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { playStep() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Playing…" else "Play tone")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (levelIdx >= HEAR_LEVELS.lastIndex) advance(null)
                        else {
                            levelIdx++
                            playStep()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Quieter") }
                Button(onClick = { advance(db) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text("I hear it")
                }
            }
            if (saved.isNotEmpty()) {
                Text(
                    "Last run saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoiseScreen(vm: LabViewModel, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var minutes by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Burn-in & masking", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Looping noise for driver burn-in or masking the outside world. " +
                "Pink sounds deeper, white sounds brighter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = vm.noiseKind, onClick = { vm.noiseKind = true },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Pink") }
            SegmentedButton(
                selected = !vm.noiseKind, onClick = { vm.noiseKind = false },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("White") }
        }
        @OptIn(ExperimentalMaterial3Api::class)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(0 to "∞", 1 to "1m", 5 to "5m", 15 to "15m").forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = minutes == m, onClick = { minutes = m },
                    shape = SegmentedButtonDefaults.itemShape(i, 4),
                ) { Text(label) }
            }
        }
        if (vm.noiseOn && minutes > 0) {
            Text(
                "Stopping in %d:%02d".format(vm.noiseSecsLeft / 60, vm.noiseSecsLeft % 60),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
            )
        }
        Button(
            onClick = {
                if (vm.noiseOn) vm.stopAll()
                else vm.startNoise(vm.noiseKind, minutes, scope)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (vm.noiseOn) Icons.Filled.Stop else Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (vm.noiseOn) "Stop noise" else "Start noise")
        }
    }
}
