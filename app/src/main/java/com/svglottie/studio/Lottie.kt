package com.svglottie.studio

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

enum class Effect(val label: String) {
    None("None"), Fade("Fade"), Scale("Pop"), Rotate("Spin"), Slide("Slide"), Draw("Draw")
}

enum class Ease(val label: String, val x1: Double, val y1: Double, val x2: Double, val y2: Double) {
    Linear("Linear", 0.0, 0.0, 1.0, 1.0),
    InOut("Ease in-out", 0.42, 0.0, 0.58, 1.0),
    Out("Ease out", 0.0, 0.0, 0.2, 1.0),
    Back("Overshoot", 0.34, 1.56, 0.64, 1.0)
}

data class LayerAnim(
    val effect: Effect = Effect.Fade,
    val delay: Float = 0f,
    val duration: Float = 0.8f,
    val ease: Ease = Ease.InOut
)

object LottieBuilder {
    const val FPS = 30

    fun build(name: String, doc: SvgDoc, anims: List<LayerAnim>, total: Float): JSONObject {
        val op = (total * FPS).roundToInt().coerceAtLeast(1)
        val layers = JSONArray()
        for (i in doc.layers.indices.reversed()) layers.put(layer(doc, doc.layers[i], anims[i], i + 1, op))
        return JSONObject().put("v", "5.7.0").put("fr", FPS).put("ip", 0).put("op", op)
            .put("w", doc.width.roundToInt()).put("h", doc.height.roundToInt())
            .put("nm", name).put("ddd", 0).put("assets", JSONArray()).put("layers", layers)
    }

    private fun layer(doc: SvgDoc, l: SvgLayer, an: LayerAnim, ind: Int, op: Int): JSONObject {
        val b = l.bounds
        val cx = (b[0] + b[2]) / 2; val cy = (b[1] + b[3]) / 2
        val t0 = (an.delay * FPS).toDouble().coerceIn(0.0, op - 1.0)
        val t1 = ((an.delay + an.duration) * FPS).toDouble().coerceIn(t0 + 1, op.toDouble())
        val e = an.effect
        fun anim(from: List<Double>, to: List<Double>) = animated(t0, t1, from, to, an.ease)
        val base = l.opacity * 100
        val o = if (e == Effect.Fade || e == Effect.Slide || e == Effect.Rotate) anim(listOf(0.0), listOf(base)) else stat(base)
        val r = if (e == Effect.Rotate) anim(listOf(-180.0), listOf(0.0)) else stat(0.0)
        val s = if (e == Effect.Scale) anim(listOf(0.0, 0.0, 100.0), listOf(100.0, 100.0, 100.0)) else stat(arr(100.0, 100.0, 100.0))
        val p = if (e == Effect.Slide) anim(listOf(cx - doc.width * 0.25, cy, 0.0), listOf(cx, cy, 0.0)) else stat(arr(cx, cy, 0.0))

        val items = JSONArray()
        l.paths.forEach { items.put(shapeJson(it)) }
        if (e == Effect.Draw) {
            items.put(JSONObject().put("ty", "tm").put("nm", "Trim").put("s", stat(0.0))
                .put("e", anim(listOf(0.0), listOf(100.0))).put("o", stat(0.0)).put("m", 1))
        }
        l.stroke?.let {
            items.put(JSONObject().put("ty", "st").put("nm", "Stroke").put("c", stat(rgb(it))).put("o", stat(100.0))
                .put("w", stat(l.strokeWidth)).put("lc", 2).put("lj", 2).put("ml", 4))
        }
        l.fill?.let {
            items.put(JSONObject().put("ty", "fl").put("nm", "Fill").put("c", stat(rgb(it))).put("o", stat(100.0))
                .put("r", if (l.evenOdd) 2 else 1))
        }
        items.put(JSONObject().put("ty", "tr").put("p", stat(arr(0.0, 0.0))).put("a", stat(arr(0.0, 0.0)))
            .put("s", stat(arr(100.0, 100.0))).put("r", stat(0.0)).put("o", stat(100.0)).put("sk", stat(0.0)).put("sa", stat(0.0)))
        val group = JSONObject().put("ty", "gr").put("nm", l.name).put("it", items)

        val ks = JSONObject().put("o", o).put("r", r).put("p", p).put("a", stat(arr(cx, cy, 0.0))).put("s", s)
        return JSONObject().put("ddd", 0).put("ind", ind).put("ty", 4).put("nm", l.name).put("sr", 1)
            .put("ks", ks).put("ao", 0).put("shapes", JSONArray().put(group))
            .put("ip", 0).put("op", op).put("st", 0).put("bm", 0)
    }

    private fun shapeJson(sp: SubPath): JSONObject {
        val v = JSONArray(); val i = JSONArray(); val o = JSONArray()
        for (n in sp.nodes) {
            v.put(arr(n.x, n.y)); i.put(arr(n.ix, n.iy)); o.put(arr(n.ox, n.oy))
        }
        val k = JSONObject().put("i", i).put("o", o).put("v", v).put("c", sp.closed)
        return JSONObject().put("ty", "sh").put("nm", "Path").put("ks", JSONObject().put("a", 0).put("k", k))
    }

    private fun animated(t0: Double, t1: Double, from: List<Double>, to: List<Double>, e: Ease): JSONObject {
        val k = JSONArray()
        k.put(JSONObject().put("t", t0).put("s", JSONArray(from)).put("o", bz(e.x1, e.y1)).put("i", bz(e.x2, e.y2)))
        k.put(JSONObject().put("t", t1).put("s", JSONArray(to)))
        return JSONObject().put("a", 1).put("k", k)
    }

    private fun bz(x: Double, y: Double) =
        JSONObject().put("x", JSONArray(listOf(x))).put("y", JSONArray(listOf(y)))

    private fun stat(v: Double) = JSONObject().put("a", 0).put("k", v)
    private fun stat(v: JSONArray) = JSONObject().put("a", 0).put("k", v)
    private fun arr(vararg d: Double) = JSONArray(d.toList())
    private fun rgb(c: Int) = arr(((c shr 16) and 255) / 255.0, ((c shr 8) and 255) / 255.0, (c and 255) / 255.0, 1.0)
}
