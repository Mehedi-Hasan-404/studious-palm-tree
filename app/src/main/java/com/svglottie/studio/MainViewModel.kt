package com.svglottie.studio

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val name: String = "",
    val doc: SvgDoc? = null,
    val anims: List<LayerAnim> = emptyList(),
    val selected: Int = 0,
    val total: Float = 3f,
    val message: String? = null
)

private const val SAMPLE = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200">
<circle id="Disc" cx="100" cy="100" r="80" fill="#2F6BFF"/>
<path id="Star" d="M100 40 L118 84 L166 88 L129 119 L141 166 L100 141 L59 166 L71 119 L34 88 L82 84 Z" fill="#FFFFFF"/>
<rect id="Bar" x="60" y="172" width="80" height="10" fill="#FFB020"/></svg>"""

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _s = MutableStateFlow(UiState())
    val state = _s.asStateFlow()

    fun load(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cr = getApplication<Application>().contentResolver
                val text = cr.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                val name = cr.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0 && c.moveToFirst()) c.getString(i) else null
                }?.substringBeforeLast('.') ?: "animation"
                open(name, text)
            } catch (e: Exception) {
                _s.update { it.copy(message = "Could not read file: ${e.message}") }
            }
        }
    }

    fun loadSample() = open("sample", SAMPLE)

    private fun open(name: String, text: String) {
        try {
            val d = SvgParser.parse(text)
            if (d.layers.isEmpty()) error("No drawable shapes found")
            _s.value = UiState(
                name = name, doc = d,
                anims = List(d.layers.size) { LayerAnim(delay = minOf(it * 0.15f, 2f)) },
                total = (d.layers.size * 0.15f + 1.6f).coerceIn(2f, 8f)
            )
        } catch (e: Exception) {
            _s.update { it.copy(message = "Could not import SVG: ${e.message}") }
        }
    }

    fun select(i: Int) = _s.update { it.copy(selected = if (it.selected == i) -1 else i) }
    fun setTotal(v: Float) = _s.update { it.copy(total = v) }
    fun edit(i: Int, f: (LayerAnim) -> LayerAnim) =
        _s.update { s -> s.copy(anims = s.anims.mapIndexed { j, a -> if (j == i) f(a) else a }) }
    fun applyToAll(i: Int) = _s.update { s ->
        val a = s.anims[i]
        s.copy(anims = s.anims.map { a.copy(delay = it.delay) })
    }
    fun dismissMessage() = _s.update { it.copy(message = null) }

    fun export(uri: Uri) {
        val s = _s.value
        val d = s.doc ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val msg = try {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(LottieBuilder.build(s.name, d, s.anims, s.total).toString().toByteArray())
                }
                "Lottie JSON exported"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
            _s.update { it.copy(message = msg) }
        }
    }
}
