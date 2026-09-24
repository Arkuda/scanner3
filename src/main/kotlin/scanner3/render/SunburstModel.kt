package scanner3.render

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import scanner3.model.FolderNode

const val TWO_PI = 2.0 * PI
private const val GOLDEN_RATIO_CONJUGATE = 0.6180339887498949

data class Sector(
    val node: FolderNode,
    val startAngle: Double,
    val endAngle: Double,
    val hue: Float,
)

class SunburstModel private constructor(
    val root: FolderNode,
    val maxDepth: Int,
    val rings: List<List<Sector>>,
) {
    val sectorCount: Int = rings.sumOf { it.size }

    fun hitTest(x: Float, y: Float, width: Float, height: Float, zoom: Float = 1f): Sector? {
        val maxRadius = ((min(width, height) / 2f - 24f) * zoom).coerceAtLeast(1f)
        val band = maxRadius / (maxDepth + 1).coerceAtLeast(1)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = hypot((x - centerX).toDouble(), (y - centerY).toDouble())
        if (radius > maxRadius) return null

        val depth = floor(radius / band).toInt().coerceAtMost(maxDepth)
        val ring = rings.getOrNull(depth).orEmpty()
        if (ring.isEmpty()) return null

        var angle = atan2((x - centerX).toDouble(), (centerY - y).toDouble())
        if (angle < 0.0) angle += TWO_PI

        var low = 0
        var high = ring.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val sector = ring[middle]
            when {
                angle < sector.startAngle -> high = middle - 1
                angle >= sector.endAngle -> low = middle + 1
                else -> return sector
            }
        }
        return null
    }

    companion object {
        fun build(root: FolderNode): SunburstModel {
            val maxDepth = maxDepth(root)
            val mutableRings = List(maxDepth + 1) { mutableListOf<Sector>() }
            val rootSector = Sector(root, 0.0, TWO_PI, 0.56f)
            mutableRings[0] += rootSector

            fun layout(parent: Sector) {
                val children = parent.node.children
                if (children.isEmpty() || parent.node.size <= 0L) return
                val parentSpan = parent.endAngle - parent.startAngle
                var cursor = parent.startAngle
                val lastIndex = children.lastIndex.coerceAtLeast(1)

                children.forEachIndexed { index, child ->
                    val childSpan = parentSpan * child.size.toDouble() / parent.node.size.toDouble()
                    if (childSpan <= 0.0) return@forEachIndexed
                    val hue = if (parent.node.depth == 0) {
                        ((0.55 + index * GOLDEN_RATIO_CONJUGATE) % 1.0).toFloat()
                    } else {
                        val siblingOffset = (index.toFloat() / lastIndex - 0.5f) * 0.08f
                        ((parent.hue + siblingOffset) % 1f + 1f) % 1f
                    }
                    val sector = Sector(child, cursor, cursor + childSpan, hue)
                    mutableRings[child.depth] += sector
                    layout(sector)
                    cursor += childSpan
                }
            }

            layout(rootSector)
            return SunburstModel(
                root = root,
                maxDepth = maxDepth,
                rings = mutableRings.map { ring -> ring.sortedBy { it.startAngle } },
            )
        }

        private fun maxDepth(root: FolderNode): Int {
            var result = root.depth
            fun visit(node: FolderNode) {
                result = maxOf(result, node.depth)
                node.children.forEach(::visit)
            }
            visit(root)
            return result
        }
    }
}
