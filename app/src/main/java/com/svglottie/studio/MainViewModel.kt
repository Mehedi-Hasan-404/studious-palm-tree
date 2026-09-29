package com.svglottie.studio

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val projects: List<Project> = emptyList(),
    val current: Project? = null,
    val doc: SvgDoc? = null,
    val selected: Int = 0,
    val message: String? = null
)

private const val SAMPLE = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200">
<circle id="Disc" cx="100" cy="100" r="80" fill="#2F6BFF"/>
<path id="Star" d="M100 40 L118 84 L166 88 L129 119 L141 166 L100 141 L59 166 L71 119 L34 88 L82 84 Z" fill="#FFFFFF"/>
<rect id="Bar" x="60" y="172" width="80" height="10" fill="#FFB020"/></svg>"""

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    private val _s = MutableStateFlow(UiState())
    val state = _s.asStateFlow()
    private var saveJob: Job? = null

    init { refresh() }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val l = store.list()
            _s.update { it.copy(projects = l) }
        }
    }

    private fun toast(m: String) = _s.update { it.copy(message = m) }
    fun dismissMessage() = _s.update { it.copy(message = null) }

    fun importSvg(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cr = getApplication<Application>().contentResolver
                val text = cr.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val name = cr.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) c.getString(i) else null
                }?.substringBeforeLast('.') ?: "animation"
                create(name, text)
            } catch (e: Exception) {
                toast("Could not read file: ${e.message}")
            }
        }
    }

    fun sample() { viewModelScope.launch(Dispatchers.Default) { create("Sample", SAMPLE) } }

    private fun create(name: String, text: String) {
        try {
            val d = SvgParser.parse(text)
            if (d.layers.isEmpty()) error("No drawable shapes found")
            val n = d.layers.size
            val step = minOf(0.15f, 2f / n)
            val p = Project(
                UUID.randomUUID().toString(), name, text, List(n) { LayerAnim(delay = it * step) },
                (step * (n - 1) + 1.5f).coerceIn(2f, 8f), Bg.Transparent, System.currentTimeMillis(), false, n
            )
            store.save(p)
            _s.update { it.copy(current = p, doc = d, selected = 0) }
        } catch (e: Exception) {
            toast("Could not import SVG: ${e.message}")
        }
    }

    fun open(id: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val p = _s.value.projects.firstOrNull { it.id == id } ?: return@launch
            try {
                val d = SvgParser.parse(p.svg)
                _s.update { it.copy(current = p, doc = d, selected = 0) }
            } catch (e: Exception) {
                toast("Could not open project: ${e.message}")
            }
        }
    }

    fun close() {
        flush()
        _s.update { it.copy(current = null, doc = null) }
        refresh()
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) { store.delete(id); refresh() }
    }

    /** Synchronous save of the open project (called on back / app stop). */
    fun flush() {
        saveJob?.cancel()
        _s.value.current?.let { store.save(it) }
    }

    private fun mutate(f: (Project) -> Project) {
        _s.update { s ->
            val c = s.current
            if (c == null) s else s.copy(current = f(c).copy(updated = System.currentTimeMillis(), exported = false))
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) { delay(400); _s.value.current?.let { store.save(it) } }
    }

    fun select(i: Int) = _s.update { it.copy(selected = if (it.selected == i) -1 else i) }
    fun setTotal(v: Float) { mutate { it.copy(total = v.coerceIn(0.5f, 60f)) } }
    fun setBg(b: Bg) { mutate { it.copy(bg = b) } }
    fun edit(i: Int, f: (LayerAnim) -> LayerAnim) {
        mutate { p -> p.copy(anims = p.anims.mapIndexed { j, a -> if (j == i) f(a) else a }) }
    }
    fun applyToAll(i: Int) {
        mutate { p -> val a = p.anims[i]; p.copy(anims = p.anims.map { a.copy(delay = it.delay) }) }
    }
    fun stagger(step: Float) {
        mutate { p ->
            val an = p.anims.mapIndexed { i, a -> a.copy(delay = i * step) }
            val end = an.maxOf { it.delay + it.duration } + 0.3f
            p.copy(anims = an, total = maxOf(p.total, end).coerceAtMost(60f))
        }
    }

    fun export(uri: Uri) {
        val p = _s.value.current ?: return
        val d = _s.value.doc ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(LottieBuilder.build(p.name, d, p.anims, p.total, p.bg).toString().toByteArray())
                }
                _s.update { it.copy(current = it.current?.copy(exported = true), message = "Lottie JSON exported") }
                _s.value.current?.let { store.save(it) }
            } catch (e: Exception) {
                toast("Export failed: ${e.message}")
            }
        }
    }
}
