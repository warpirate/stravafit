package routeexport

object Simplify {
    /**
     * Iterative Douglas–Peucker on already-projected points. [tolerance] is in output units (px),
     * so detail scales with the export size. Returns the kept indices in ascending order.
     */
    fun douglasPeucker(pts: List<XY>, tolerance: Double): IntArray {
        val n = pts.size
        if (n < 3) return IntArray(n) { it }
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        val tol2 = tolerance * tolerance
        val stack = ArrayDeque<Long>()
        stack.addLast(pack(0, n - 1))
        while (stack.isNotEmpty()) {
            val packed = stack.removeLast()
            val s = (packed shr 32).toInt()
            val e = packed.toInt()
            var maxD = -1.0
            var idx = -1
            for (i in s + 1 until e) {
                val d = segmentDist2(pts[i], pts[s], pts[e])
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx != -1 && maxD > tol2) {
                keep[idx] = true
                stack.addLast(pack(s, idx))
                stack.addLast(pack(idx, e))
            }
        }
        return (0 until n).filter { keep[it] }.toIntArray()
    }

    private fun pack(a: Int, b: Int) = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)

    fun segmentDist2(p: XY, a: XY, b: XY): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return sq(p.x - a.x) + sq(p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        return sq(p.x - (a.x + t * dx)) + sq(p.y - (a.y + t * dy))
    }

    private fun sq(v: Double) = v * v
}
