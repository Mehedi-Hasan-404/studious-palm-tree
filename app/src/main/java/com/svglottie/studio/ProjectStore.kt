package com.svglottie.studio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Project(
    val id: String, val name: String, val svg: String, val anims: List<LayerAnim>, val total: Float,
    val bg: Bg, val updated: Long, val exported: Boolean, val layers: Int
)

class ProjectStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "projects").apply { mkdirs() }

    fun list(): List<Project> = (dir.listFiles() ?: emptyArray()).filter { it.extension == "json" }
        .mapNotNull { f -> runCatching { read(f.readText()) }.getOrNull() }
        .sortedByDescending { it.updated }

    fun save(p: Project) {
        val tmp = File(dir, "${p.id}.tmp")
        tmp.writeText(write(p).toString())
        tmp.renameTo(File(dir, "${p.id}.json"))
    }

    fun delete(id: String) { File(dir, "$id.json").delete() }

    private fun write(p: Project): JSONObject {
        val arr = JSONArray()
        p.anims.forEach {
            arr.put(JSONObject().put("e", it.effect.name).put("d", it.delay.toDouble())
                .put("u", it.duration.toDouble()).put("x", it.ease.name))
        }
        return JSONObject().put("id", p.id).put("name", p.name).put("svg", p.svg).put("total", p.total.toDouble())
            .put("bg", p.bg.name).put("updated", p.updated).put("exported", p.exported)
            .put("layers", p.layers).put("anims", arr)
    }

    private fun read(s: String): Project {
        val o = JSONObject(s); val a = o.getJSONArray("anims")
        return Project(
            o.getString("id"), o.getString("name"), o.getString("svg"),
            List(a.length()) {
                val e = a.getJSONObject(it)
                LayerAnim(Effect.valueOf(e.getString("e")), e.getDouble("d").toFloat(), e.getDouble("u").toFloat(), Ease.valueOf(e.getString("x")))
            },
            o.getDouble("total").toFloat(), Bg.valueOf(o.getString("bg")), o.getLong("updated"),
            o.getBoolean("exported"), o.getInt("layers")
        )
    }
}
