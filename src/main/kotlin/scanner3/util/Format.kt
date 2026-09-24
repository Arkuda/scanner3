package scanner3.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

fun formatSize(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB", "PiB")
    var value = bytes.toDouble()
    var unit = -1
    do {
        value /= 1_024.0
        unit++
    } while (value >= 1_024.0 && unit < units.lastIndex)
    return "%.1f %s".format(value, units[unit])
}

fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val hue = ((h % 1f) + 1f) % 1f
    val saturation = s.coerceIn(0f, 1f)
    val lightness = l.coerceIn(0f, 1f)
    val chroma = (1f - abs(2f * lightness - 1f)) * saturation
    val segment = hue * 6f
    val secondary = chroma * (1f - abs(segment % 2f - 1f))
    val (r1, g1, b1) = when (segment.toInt()) {
        0 -> Triple(chroma, secondary, 0f)
        1 -> Triple(secondary, chroma, 0f)
        2 -> Triple(0f, chroma, secondary)
        3 -> Triple(0f, secondary, chroma)
        4 -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val match = lightness - chroma / 2f
    return Color(r1 + match, g1 + match, b1 + match, alpha.coerceIn(0f, 1f))
}
