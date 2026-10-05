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
import androidx.compose.foundation.shape.CircleShape
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
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import kotlin.math.roundToInt
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StudioTheme { StudioApp(vm) } }
    }
    override fun onStop() { vm.flush(); super.onStop() }
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
fun StudioApp(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importSvg) }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::export) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.dismissMessage() } }
    BackHandler(enabled = s.current != null) { vm.close() }
    val p = s.current
    val doc = s.doc
    var deleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            if (p == null) ExtendedFloatingActionButton(onClick = { openPicker.launch(arrayOf("*/*")) }) { Text("Import SVG") }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { if (p != null) TextButton(onClick = { vm.close() }) { Text("Back") } },
                title = {
                    Column {
                        Text(p?.name ?: "Projects", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (p != null && doc != null) Text(
                            "${doc.width.roundToInt()} \u00d7 ${doc.height.roundToInt()} px",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (p != null) {
                        FilledTonalButton(onClick = { savePicker.launch("${p.name}.json") }) { Text("Export") }
                        Spacer(Modifier.width(12.dp))
                    }
                }
            )
        }
    ) { pad ->
        if (p == null || doc == null) Home(s.projects, pad, vm::sample, vm::open) { deleteId = it }
        else Editor(p, doc, s.selected, vm, pad)
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete project?") },
            text = { Text("This removes the project and its draft from this device.") },
            confirmButton = { TextButton(onClick = { vm.delete(id); deleteId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Home(
    projects: List<Project>, pad: PaddingValues, onSample: () -> Unit,
    onOpen: (String) -> Unit, onDelete: (String) -> Unit
) {
    if (projects.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(pad).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No projects yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Import an SVG to start. Your work is saved automatically as a draft.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onSample) { Text("Try a sample") }
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(projects, key = { it.id }) { pr ->
                val cs = MaterialTheme.colorScheme
                Panel {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(pr.id) }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pr.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                "${pr.layers} layers \u00b7 " + DateUtils.getRelativeTimeSpanString(
                                    pr.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                                ),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (pr.exported) cs.secondaryContainer else cs.surfaceVariant
                        ) {
                            Text(
                                if (pr.exported) "Exported" else "Draft", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        TextButton(onClick = { onDelete(pr.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Editor(p: Project, doc: SvgDoc, selected: Int, vm: MainViewModel, pad: PaddingValues) {
    val json = remember(doc, p.anims, p.total, p.bg) { LottieBuilder.build(p.name, doc, p.anims, p.total, p.bg).toString() }
    Column(Modifier.fillMaxSize().padding(pad).imePadding().padding(horizontal = 16.dp)) {
        PreviewPanel(json, p.total, (doc.width / doc.height).toFloat(), p.bg, vm::setBg)
        LazyColumn(
            Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Panel {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimingRow("Total duration", p.total, 1f..10f, vm::setTotal)
                        if (doc.backgroundIdx.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Include SVG background", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Turn off for a clean transparent export", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = doc.backgroundIdx.all { p.anims[it].visible }, onCheckedChange = vm::setSvgBackground)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            var step by remember { mutableFloatStateOf(0.1f) }
                            TimeField("Stagger step", step, Modifier.weight(1f)) { step = it }
                            FilledTonalButton(onClick = { vm.stagger(step) }) { Text("Apply stagger") }
                        }
                    }
                }
            }
            item {
                Text(
                    "LAYERS \u00b7 ${doc.layers.size}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            itemsIndexed(doc.layers) { i, layer ->
                LayerRow(
                    layer, p.anims[i], p.total, selected == i,
                    onToggle = { vm.select(i) }, onEdit = { f -> vm.edit(i, f) }, onApplyAll = { vm.applyToAll(i) }
                )
            }
        }
    }
}

private fun Modifier.checker(a: Color, b: Color) = drawBehind {
    drawRect(a)
    val sz = 12.dp.toPx()
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
private fun PreviewPanel(json: String, total: Float, ratio: Float, bg: Bg, onBg: (Bg) -> Unit) {
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
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Background \u00b7 ${bg.label}", style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurfaceVariant, modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Bg.entries.forEach { b ->
                        val sel = bg == b
                        Box(
                            Modifier.size(28.dp).clip(CircleShape)
                                .then(
                                    when (b) {
                                        Bg.Transparent -> Modifier.checker(cs.surfaceVariant, cs.onSurfaceVariant.copy(alpha = 0.35f))
                                        Bg.White -> Modifier.background(Color.White)
                                        Bg.Black -> Modifier.background(Color.Black)
                                    }
                                )
                                .border(if (sel) 2.dp else 1.dp, if (sel) cs.primary else cs.outline, CircleShape)
                                .clickable { onBg(b) }
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(240.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.aspectRatio(ratio.coerceIn(0.05f, 20f)).clip(RoundedCornerShape(4.dp))
                        .checker(cs.surfaceVariant, cs.surface)
                ) {
                    LottieAnimation(comp, progress = { shown }, modifier = Modifier.fillMaxSize())
                }
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

private fun fmt(v: Float) = "%.2f".format(Locale.US, v)

@Composable
private fun TimeField(label: String?, value: Float, modifier: Modifier = Modifier, onValue: (Float) -> Unit) {
    var text by remember { mutableStateOf(fmt(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = fmt(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            t.replace(',', '.').toFloatOrNull()?.takeIf { it >= 0f }?.let(onValue)
        },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        label = if (label != null) ({ Text(label) }) else null,
        suffix = { Text("s") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
private fun TimingRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimeField(null, value, Modifier.width(120.dp), onChange)
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
                        if (anim.visible) "${anim.effect.label} \u00b7 ${fmt(anim.duration)}s \u00b7 ${anim.ease.label}" else "Hidden",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                Text("+${fmt(anim.delay)}s", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Switch(checked = anim.visible, onCheckedChange = { v -> onEdit { a -> a.copy(visible = v) } })
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
                    TimingRow("Start delay", anim.delay, 0f..maxOf(total, 0.5f)) { v -> onEdit { it.copy(delay = v) } }
                    TimingRow("Duration", anim.duration, 0.05f..4f) { v -> onEdit { it.copy(duration = v.coerceAtLeast(0.05f)) } }
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
