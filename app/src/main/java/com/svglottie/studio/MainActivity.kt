package com.svglottie.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StudioTheme { StudioApp() } }
    }
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AA7FF), onPrimary = Color(0xFF0B1B38),
    background = Color(0xFF0E1014), onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF16191F), onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1F232B), onSurfaceVariant = Color(0xFF9AA3B2),
    outline = Color(0xFF2B303A), secondaryContainer = Color(0xFF243250), onSecondaryContainer = Color(0xFFDCE7FF)
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5FD0), onPrimary = Color.White,
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF14171C),
    surface = Color.White, onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFECEEF2), onSurfaceVariant = Color(0xFF5B6473),
    outline = Color(0xFFDADEE5), secondaryContainer = Color(0xFFDCE6FB), onSecondaryContainer = Color(0xFF12275A)
)

@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(6.dp), medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp)
        ),
        content = content
    )
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioApp(vm: MainViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::load) }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.dismissMessage() } }
    val doc = s.doc

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text("SVG to Lottie", style = MaterialTheme.typography.titleMedium)
                        if (doc != null) Text(
                            s.name, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { openPicker.launch(arrayOf("*/*")) }) { Text("Import") }
                    FilledTonalButton(enabled = doc != null, onClick = { savePicker.launch("${s.name}.json") }) { Text("Export") }
                    Spacer(Modifier.width(12.dp))
                }
            )
        }
    ) { pad ->
        if (doc == null) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No SVG open", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Import an SVG to build a Lottie animation with live preview.",
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { openPicker.launch(arrayOf("*/*")) }) { Text("Import SVG") }
                TextButton(onClick = vm::loadSample) { Text("Use sample") }
            }
        } else {
            val json = remember(doc, s.anims, s.total) { LottieBuilder.build(s.name, doc, s.anims, s.total).toString() }
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PreviewPanel(json, s.total)
                Panel { Column(Modifier.padding(14.dp)) { ParamSlider("Total duration", s.total, 1f..10f, vm::setTotal) } }
                Text(
                    "LAYERS · ${doc.layers.size}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
                doc.layers.forEachIndexed { i, layer ->
                    LayerRow(
                        layer, s.anims[i], s.total, s.selected == i,
                        onToggle = { vm.select(i) }, onEdit = { f -> vm.edit(i, f) }, onApplyAll = { vm.applyToAll(i) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun Modifier.checker(a: Color, b: Color) = drawBehind {
    drawRect(a)
    val sz = 14.dp.toPx()
    var y = 0f; var row = 0
    while (y < size.height) {
        var x = 0f; var c = row % 2
        while (x < size.width) {
            if (c % 2 == 0) drawRect(b, Offset(x, y), Size(sz, sz))
            c++; x += sz
        }
        y += sz; row++
    }
}

@Composable
private fun PreviewPanel(json: String, total: Float) {
    var playing by remember { mutableStateOf(true) }
    var scrub by remember { mutableFloatStateOf(0f) }
    val result = rememberLottieComposition(LottieCompositionSpec.JsonString(json))
    var last by remember { mutableStateOf<LottieComposition?>(null) }
    LaunchedEffect(result.value) { result.value?.let { last = it } }
    val comp = result.value ?: last
    val progress by animateLottieCompositionAsState(
        comp, iterations = LottieConstants.IterateForever, isPlaying = playing, restartOnPlay = false
    )
    val shown = if (playing) progress else scrub
    val cs = MaterialTheme.colorScheme
    Panel {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).checker(cs.surfaceVariant, cs.surface)) {
                LottieAnimation(comp, progress = { shown }, modifier = Modifier.fillMaxSize().padding(20.dp))
            }
            HorizontalDivider(color = cs.outline)
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { if (playing) scrub = progress; playing = !playing }) {
                    Text(if (playing) "Pause" else "Play")
                }
                Slider(
                    value = shown.coerceIn(0f, 1f),
                    onValueChange = { playing = false; scrub = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                )
                Text("%.1f / %.1fs".format(shown * total, total), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.2f s".format(value), style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayerRow(
    layer: SvgLayer, anim: LayerAnim, total: Float, expanded: Boolean,
    onToggle: () -> Unit, onEdit: ((LayerAnim) -> LayerAnim) -> Unit, onApplyAll: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Panel {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sw = RoundedCornerShape(5.dp)
                Box(
                    Modifier.size(18.dp).clip(sw).background(Color(layer.fill ?: layer.stroke ?: 0xFF888888.toInt()))
                        .border(1.dp, cs.outline, sw)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(layer.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        "${anim.effect.label} · ${"%.1f".format(anim.duration)}s · ${anim.ease.label}",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                Text("+${"%.2f".format(anim.delay)}s", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            if (expanded) {
                HorizontalDivider(color = cs.outline)
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Transition", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Effect.entries.forEach { e ->
                            FilterChip(selected = anim.effect == e, onClick = { onEdit { it.copy(effect = e) } }, label = { Text(e.label) })
                        }
                    }
                    ParamSlider("Delay", anim.delay, 0f..total) { v -> onEdit { it.copy(delay = v) } }
                    ParamSlider("Duration", anim.duration, 0.1f..4f) { v -> onEdit { it.copy(duration = v) } }
                    Text("Easing", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ease.entries.forEach { e ->
                            FilterChip(selected = anim.ease == e, onClick = { onEdit { it.copy(ease = e) } }, label = { Text(e.label) })
                        }
                    }
                    TextButton(onClick = onApplyAll) { Text("Apply style to all layers") }
                }
            }
        }
    }
}
