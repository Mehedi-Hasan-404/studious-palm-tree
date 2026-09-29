package com.svglottie.studio

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import kotlin.math.*

class Node(var x: Double, var y: Double) {
    var ix = 0.0; var iy = 0.0; var ox = 0.0; var oy = 0.0
}

class SubPath(val nodes: List<Node>, val closed: Boolean)

class SvgLayer(
    val name: String, val paths: List<SubPath>, val fill: Int?, val evenOdd: Boolean,
    val stroke: Int?, val strokeWidth: Double, val opacity: Double
) {
    /** minX, minY, maxX, maxY */
    val bounds: DoubleArray by lazy {
        var a = Double.MAX_VALUE; var b = Double.MAX_VALUE; var c = -Double.MAX_VALUE; var d = -Double.MAX_VALUE
        for (p in paths) for (n in p.nodes) { a = min(a, n.x); b = min(b, n.y); c = max(c, n.x); d = max(d, n.y) }
        doubleArrayOf(a, b, c, d)
    }
}

class SvgDoc(val width: Double, val height: Double, val layers: List<SvgLayer>)

private val NUM = Regex("[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?")

object PathParser {
    private val TOK = Regex("[MmLlHhVvCcSsQqTtAaZz]|" + NUM.pattern)

    fun parse(d: String): List<SubPath> {
        val t = TOK.findAll(d).map { it.value }.toList()
        var i = 0
        val out = mutableListOf<SubPath>()
        var cur = mutableListOf<Node>()
        var cx = 0.0; var cy = 0.0; var sx = 0.0; var sy = 0.0; var lx = 0.0; var ly = 0.0
        var cmd = 'M'
        fun num() = t[i++].toDouble()
        fun flush(closed: Boolean) {
            if (cur.size > 1) {
                if (closed) {
                    val f = cur.first(); val l = cur.last()
                    if (abs(f.x - l.x) < 1e-6 && abs(f.y - l.y) < 1e-6) {
                        f.ix = l.ix; f.iy = l.iy; cur.removeAt(cur.size - 1)
                    }
                }
                out += SubPath(cur, closed)
            }
            cur = mutableListOf()
        }
        fun start() { if (cur.isEmpty()) cur.add(Node(cx, cy)) }
        fun line(x: Double, y: Double) { start(); cur.add(Node(x, y)); cx = x; cy = y; lx = x; ly = y }
        fun cubic(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double) {
            start()
            val l = cur.last(); l.ox = x1 - l.x; l.oy = y1 - l.y
            val n = Node(x, y); n.ix = x2 - x; n.iy = y2 - y
            cur.add(n); cx = x; cy = y; lx = x2; ly = y2
        }
        fun quad(qx: Double, qy: Double, x: Double, y: Double) {
            val x0 = cx; val y0 = cy
            cubic(x0 + 2.0 / 3 * (qx - x0), y0 + 2.0 / 3 * (qy - y0), x + 2.0 / 3 * (qx - x), y + 2.0 / 3 * (qy - y), x, y)
            lx = qx; ly = qy
        }
        fun arc(rx0: Double, ry0: Double, rotDeg: Double, large: Boolean, sweep: Boolean, x: Double, y: Double) {
            val x0 = cx; val y0 = cy
            var rx = abs(rx0); var ry = abs(ry0)
            if (rx == 0.0 || ry == 0.0 || (x0 == x && y0 == y)) { line(x, y); return }
            val phi = Math.toRadians(rotDeg); val co = cos(phi); val si = sin(phi)
            val dx = (x0 - x) / 2; val dy = (y0 - y) / 2
            val x1 = co * dx + si * dy; val y1 = -si * dx + co * dy
            val lam = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
            if (lam > 1) { val s = sqrt(lam); rx *= s; ry *= s }
            val nu = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            val de = rx * rx * y1 * y1 + ry * ry * x1 * x1
            var f = sqrt(max(0.0, nu / de)); if (large == sweep) f = -f
            val cxp = f * rx * y1 / ry; val cyp = -f * ry * x1 / rx
            val ccx = co * cxp - si * cyp + (x0 + x) / 2; val ccy = si * cxp + co * cyp + (y0 + y) / 2
            val th1 = atan2((y1 - cyp) / ry, (x1 - cxp) / rx)
            var dth = atan2((-y1 - cyp) / ry, (-x1 - cxp) / rx) - th1
            if (sweep && dth < 0) dth += 2 * PI else if (!sweep && dth > 0) dth -= 2 * PI
            val n = ceil(abs(dth) / (PI / 2) - 1e-9).toInt().coerceAtLeast(1)
            val step = dth / n; val kk = 4.0 / 3 * tan(step / 4)
            fun px(a: Double) = ccx + rx * cos(a) * co - ry * sin(a) * si
            fun py(a: Double) = ccy + rx * cos(a) * si + ry * sin(a) * co
            fun tx(a: Double) = -rx * sin(a) * co - ry * cos(a) * si
            fun ty(a: Double) = -rx * sin(a) * si + ry * cos(a) * co
            var a = th1
            for (j in 0 until n) {
                val b = a + step
                val ex = if (j == n - 1) x else px(b); val ey = if (j == n - 1) y else py(b)
                cubic(px(a) + kk * tx(a), py(a) + kk * ty(a), px(b) - kk * tx(b), py(b) - kk * ty(b), ex, ey)
                a = b
            }
        }

        while (i < t.size) {
            if (t[i][0].isLetter()) cmd = t[i++][0] else if (cmd == 'Z' || cmd == 'z') { i++; continue }
            val rel = cmd.isLowerCase()
            val bx = if (rel) cx else 0.0; val by = if (rel) cy else 0.0
            when (cmd.uppercaseChar()) {
                'M' -> {
                    flush(false)
                    cx = num() + bx; cy = num() + by; sx = cx; sy = cy; lx = cx; ly = cy
                    cur.add(Node(cx, cy)); cmd = if (rel) 'l' else 'L'
                }
                'L' -> { val x = num() + bx; val y = num() + by; line(x, y) }
                'H' -> line(num() + bx, cy)
                'V' -> line(cx, num() + by)
                'C' -> {
                    val x1 = num() + bx; val y1 = num() + by; val x2 = num() + bx; val y2 = num() + by
                    val x = num() + bx; val y = num() + by
                    cubic(x1, y1, x2, y2, x, y)
                }
                'S' -> {
                    val x1 = 2 * cx - lx; val y1 = 2 * cy - ly
                    val x2 = num() + bx; val y2 = num() + by; val x = num() + bx; val y = num() + by
                    cubic(x1, y1, x2, y2, x, y)
                }
                'Q' -> { val qx = num() + bx; val qy = num() + by; val x = num() + bx; val y = num() + by; quad(qx, qy, x, y) }
                'T' -> { val qx = 2 * cx - lx; val qy = 2 * cy - ly; val x = num() + bx; val y = num() + by; quad(qx, qy, x, y) }
                'A' -> {
                    val rx = num(); val ry = num(); val rot = num()
                    val large = num() != 0.0; val sweep = num() != 0.0
                    val x = num() + bx; val y = num() + by
                    arc(rx, ry, rot, large, sweep, x, y)
                }
                'Z' -> { flush(true); cx = sx; cy = sy; lx = cx; ly = cy; cur.add(Node(cx, cy)) }
                else -> i++
            }
        }
        flush(false)
        return out
    }
}

object SvgParser {
    private val SKIP = setOf(
        "defs", "clipPath", "mask", "symbol", "linearGradient", "radialGradient", "pattern",
        "style", "title", "desc", "metadata", "filter", "marker"
    )
    private val INH = listOf("fill", "stroke", "stroke-width", "fill-rule")
    private val BLACK = 0xFF000000.toInt()
    private val NAMED = mapOf(
        "black" to BLACK, "white" to 0xFFFFFFFF.toInt(), "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF008000.toInt(), "blue" to 0xFF0000FF.toInt(), "yellow" to 0xFFFFFF00.toInt(),
        "orange" to 0xFFFFA500.toInt(), "gray" to 0xFF808080.toInt(), "grey" to 0xFF808080.toInt(),
        "purple" to 0xFF800080.toInt(), "pink" to 0xFFFFC0CB.toInt(), "cyan" to 0xFF00FFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt()
    )
    private val IDENT = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

    fun parse(text: String): SvgDoc {
        val p = Xml.newPullParser()
        p.setInput(text.reader())
        var w = 0.0; var h = 0.0
        val layers = mutableListOf<SvgLayer>()
        val props = ArrayDeque<Map<String, String>>().apply { addLast(emptyMap()) }
        val mats = ArrayDeque<DoubleArray>().apply { addLast(IDENT) }
        val ops = ArrayDeque<Double>().apply { addLast(1.0) }
        var skip = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val tag = p.name.substringAfter(':')
                if (skip > 0 || tag in SKIP) skip++ else {
                    val a = HashMap<String, String>()
                    for (i in 0 until p.attributeCount) a[p.getAttributeName(i).substringAfter(':')] = p.getAttributeValue(i)
                    a["style"]?.split(';')?.forEach { d ->
                        val kv = d.split(':', limit = 2)
                        if (kv.size == 2) a[kv[0].trim()] = kv[1].trim()
                    }
                    val inh = props.last().toMutableMap()
                    for (k in INH) a[k]?.let { inh[k] = it }
                    var m = a["transform"]?.let { mul(mats.last(), parseTransform(it)) } ?: mats.last()
                    val op = ops.last() * (a["opacity"]?.toDoubleOrNull() ?: 1.0)
                    if (tag == "svg" && w == 0.0) {
                        val vb = a["viewBox"]?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toDoubleOrNull() }
                        fun dim(k: String) = a[k]?.takeIf { '%' !in it }?.let { NUM.find(it)?.value?.toDoubleOrNull() } ?: 0.0
                        val aw = dim("width"); val ah = dim("height")
                        if (vb != null && vb.size == 4 && vb[2] > 0 && vb[3] > 0) {
                            val sw = if (aw > 0 && ah > 0) aw else vb[2]
                            val sh = if (aw > 0 && ah > 0) ah else vb[3]
                            val sc = min(sw / vb[2], sh / vb[3])
                            w = sw; h = sh
                            m = mul(m, doubleArrayOf(sc, 0.0, 0.0, sc, (sw - vb[2] * sc) / 2 - vb[0] * sc, (sh - vb[3] * sc) / 2 - vb[1] * sc))
                        } else { w = aw; h = ah }
                    } else {
                        val subs = shapeOf(tag, a)
                        if (!subs.isNullOrEmpty()) {
                            val fill = if ("fill" in inh) color(inh["fill"]) else BLACK
                            val stroke = color(inh["stroke"])
                            if (fill != null || stroke != null) {
                                subs.forEach { transform(it, m) }
                                val sc = sqrt(abs(m[0] * m[3] - m[1] * m[2]))
                                layers += SvgLayer(
                                    a["id"] ?: "${tag.replaceFirstChar { it.uppercase() }} ${layers.size + 1}",
                                    subs, fill, inh["fill-rule"] == "evenodd", stroke,
                                    (inh["stroke-width"]?.toDoubleOrNull() ?: 1.0) * sc, op
                                )
                            }
                        }
                    }
                    props.addLast(inh); mats.addLast(m); ops.addLast(op)
                }
            } else if (ev == XmlPullParser.END_TAG) {
                if (skip > 0) skip-- else { props.removeLast(); mats.removeLast(); ops.removeLast() }
            }
            ev = p.next()
        }
        if (w <= 0.0 || h <= 0.0) {
            w = layers.maxOfOrNull { it.bounds[2] }?.let { ceil(it) } ?: 0.0
            h = layers.maxOfOrNull { it.bounds[3] }?.let { ceil(it) } ?: 0.0
            if (w <= 0.0) w = 512.0
            if (h <= 0.0) h = 512.0
        }
        return SvgDoc(w, h, layers)
    }

    private fun shapeOf(tag: String, a: Map<String, String>): List<SubPath>? {
        fun d(k: String) = a[k]?.let { NUM.find(it)?.value?.toDoubleOrNull() } ?: 0.0
        return when (tag) {
            "path" -> PathParser.parse(a["d"] ?: return null)
            "rect" -> {
                val x = d("x"); val y = d("y"); val rw = d("width"); val rh = d("height")
                if (rw <= 0 || rh <= 0) null
                else listOf(SubPath(listOf(Node(x, y), Node(x + rw, y), Node(x + rw, y + rh), Node(x, y + rh)), true))
            }
            "circle" -> ellipse(d("cx"), d("cy"), d("r"), d("r"))
            "ellipse" -> ellipse(d("cx"), d("cy"), d("rx"), d("ry"))
            "line" -> listOf(SubPath(listOf(Node(d("x1"), d("y1")), Node(d("x2"), d("y2"))), false))
            "polygon", "polyline" -> {
                val v = NUM.findAll(a["points"] ?: "").map { it.value.toDouble() }.toList()
                val nodes = (0 until v.size / 2).map { Node(v[it * 2], v[it * 2 + 1]) }
                if (nodes.size < 2) null else listOf(SubPath(nodes, tag == "polygon"))
            }
            else -> null
        }
    }

    private fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double): List<SubPath>? {
        if (rx <= 0 || ry <= 0) return null
        val k = 0.5523
        val n = listOf(
            Node(cx + rx, cy).also { it.iy = -k * ry; it.oy = k * ry },
            Node(cx, cy + ry).also { it.ix = k * rx; it.ox = -k * rx },
            Node(cx - rx, cy).also { it.iy = k * ry; it.oy = -k * ry },
            Node(cx, cy - ry).also { it.ix = -k * rx; it.ox = k * rx }
        )
        return listOf(SubPath(n, true))
    }

    private fun mul(p: DoubleArray, l: DoubleArray) = doubleArrayOf(
        p[0] * l[0] + p[2] * l[1], p[1] * l[0] + p[3] * l[1],
        p[0] * l[2] + p[2] * l[3], p[1] * l[2] + p[3] * l[3],
        p[0] * l[4] + p[2] * l[5] + p[4], p[1] * l[4] + p[3] * l[5] + p[5]
    )

    private fun parseTransform(s: String): DoubleArray {
        var m = IDENT
        for (r in Regex("(\\w+)\\s*\\(([^)]*)\\)").findAll(s)) {
            val v = NUM.findAll(r.groupValues[2]).map { it.value.toDouble() }.toList()
            val l = when (r.groupValues[1]) {
                "translate" -> doubleArrayOf(1.0, 0.0, 0.0, 1.0, v.getOrElse(0) { 0.0 }, v.getOrElse(1) { 0.0 })
                "scale" -> { val sx = v.getOrElse(0) { 1.0 }; doubleArrayOf(sx, 0.0, 0.0, v.getOrElse(1) { sx }, 0.0, 0.0) }
                "rotate" -> {
                    val r0 = Math.toRadians(v.getOrElse(0) { 0.0 }); val c = cos(r0); val si = sin(r0)
                    val px = v.getOrElse(1) { 0.0 }; val py = v.getOrElse(2) { 0.0 }
                    doubleArrayOf(c, si, -si, c, px - c * px + si * py, py - si * px - c * py)
                }
                "matrix" -> if (v.size == 6) v.toDoubleArray() else IDENT
                else -> IDENT
            }
            m = mul(m, l)
        }
        return m
    }

    private fun transform(sp: SubPath, m: DoubleArray) {
        for (n in sp.nodes) {
            val x = n.x; val y = n.y
            n.x = m[0] * x + m[2] * y + m[4]; n.y = m[1] * x + m[3] * y + m[5]
            val ix = n.ix; val iy = n.iy; val ox = n.ox; val oy = n.oy
            n.ix = m[0] * ix + m[2] * iy; n.iy = m[1] * ix + m[3] * iy
            n.ox = m[0] * ox + m[2] * oy; n.oy = m[1] * ox + m[3] * oy
        }
    }

    private fun color(s: String?): Int? {
        val v = s?.trim()?.lowercase() ?: return null
        return when {
            v.isEmpty() || v == "none" || v == "transparent" -> null
            v.startsWith("#") -> {
                var h = v.drop(1)
                if (h.length == 3 || h.length == 4) h = h.map { "$it$it" }.joinToString("")
                h.take(6).toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            }
            v.startsWith("rgb") -> {
                val n = NUM.findAll(v).map { it.value.toDouble().toInt().coerceIn(0, 255) }.toList()
                if (n.size >= 3) (0xFF shl 24) or (n[0] shl 16) or (n[1] shl 8) or n[2] else null
            }
            v.startsWith("url") -> 0xFF888888.toInt()
            else -> NAMED[v] ?: BLACK
        }
    }
}
