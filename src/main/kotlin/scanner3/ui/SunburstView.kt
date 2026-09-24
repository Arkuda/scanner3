package scanner3.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import scanner3.platform.OS
import scanner3.render.Sector
import scanner3.render.SunburstModel
import scanner3.util.formatSize
import scanner3.util.hslToColor

private val Background = Color(0xFF0D1117)
private val TooltipBackground = Color(0xF21A2232)
private const val MinimumArcPixels = 0.65

private data class RenderedSector(
    val sector: Sector,
    val path: Path,
    val color: Color,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SunburstView(
    model: SunburstModel,
    onOpen: (path: String) -> Unit,
    onTrash: (path: String) -> Unit,
    onDelete: (path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bloom = remember(model) { Animatable(0f) }
    var hovered by remember(model) { mutableStateOf<Sector?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(model) { mutableFloatStateOf(1f) }
    var contextSector by remember(model) { mutableStateOf<Sector?>(null) }
    var contextPosition by remember { mutableStateOf(Offset.Zero) }
    var pendingDelete by remember(model) { mutableStateOf<Sector?>(null) }

    LaunchedEffect(model) {
        if (model.sectorCount > 20_000) {
            bloom.snapTo(1f)
        } else {
            bloom.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 520,
                    easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f),
                ),
            )
        }
    }

    fun hit(position: Offset): Sector? = model.hitTest(
        position.x,
        position.y,
        canvasSize.width.toFloat(),
        canvasSize.height.toFloat(),
        zoom,
    )

    val growth = bloom.value
    val geometry = remember(model, canvasSize, zoom, growth) {
        if (canvasSize == IntSize.Zero) return@remember emptyList()
        val width = canvasSize.width.toFloat()
        val height = canvasSize.height.toFloat()
        val center = Offset(width / 2f, height / 2f)
        val maxRadius = ((min(width, height) / 2f - 24f) * zoom).coerceAtLeast(1f)
        val band = maxRadius / (model.maxDepth + 1).coerceAtLeast(1)

        fun point(radius: Float, angle: Double): Offset = Offset(
            x = center.x + radius * sin(angle).toFloat(),
            y = center.y - radius * cos(angle).toFloat(),
        )

        fun pathFor(sector: Sector, innerRadius: Float, outerRadius: Float): Path {
            val sweep = Math.toDegrees(sector.endAngle - sector.startAngle).toFloat()
            val startDegrees = Math.toDegrees(sector.startAngle).toFloat() - 90f
            val outerStart = point(outerRadius, sector.startAngle)
            return Path().apply {
                moveTo(outerStart.x, outerStart.y)
                val outerRect = Rect(
                    center.x - outerRadius,
                    center.y - outerRadius,
                    center.x + outerRadius,
                    center.y + outerRadius,
                )
                val innerRect = Rect(
                    center.x - innerRadius,
                    center.y - innerRadius,
                    center.x + innerRadius,
                    center.y + innerRadius,
                )
                if (sweep >= 359.9f) {
                    arcTo(outerRect, startDegrees, 180f, false)
                    arcTo(outerRect, startDegrees + 180f, sweep - 180f, false)
                } else {
                    arcTo(outerRect, startDegrees, sweep, false)
                }
                val innerEnd = point(innerRadius, sector.endAngle)
                lineTo(innerEnd.x, innerEnd.y)
                if (sweep >= 359.9f) {
                    arcTo(innerRect, startDegrees + sweep, -180f, false)
                    arcTo(innerRect, startDegrees + sweep - 180f, -(sweep - 180f), false)
                } else {
                    arcTo(innerRect, startDegrees + sweep, -sweep, false)
                }
                close()
            }
        }

        buildList {
            model.rings.forEachIndexed { depth, ring ->
                if (depth == 0) return@forEachIndexed
                val innerRadius = depth * band * growth
                val outerRadius = (depth + 1) * band * growth
                val lightness = 0.50f + 0.17f * depth / model.maxDepth.coerceAtLeast(1)
                for (sector in ring) {
                    val arcPixels = (sector.endAngle - sector.startAngle) * outerRadius
                    if (arcPixels < MinimumArcPixels) continue
                    add(
                        RenderedSector(
                            sector = sector,
                            path = pathFor(sector, innerRadius, outerRadius),
                            color = hslToColor(sector.hue, 0.78f, lightness, growth),
                        ),
                    )
                }
            }
        }
    }

    Box(modifier.fillMaxSize().background(Background)) {
        Canvas(
            Modifier.matchParentSize()
                .onSizeChanged { canvasSize = it }
                .onPointerEvent(PointerEventType.Move) { event ->
                    val candidate = event.changes.firstOrNull()?.position?.let(::hit)
                    if (candidate?.node?.path != hovered?.node?.path) hovered = candidate
                }
                .onPointerEvent(PointerEventType.Exit) { hovered = null }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (delta != 0f) zoom = (zoom * if (delta < 0f) 1.12f else 0.89f).coerceIn(0.45f, 3f)
                }
                .onPointerEvent(PointerEventType.Release) { event ->
                    val position = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                    hit(position)?.let { sector ->
                        contextPosition = position
                        contextSector = sector
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = ((min(size.width, size.height) / 2f - 24f) * zoom).coerceAtLeast(1f)
            val band = maxRadius / (model.maxDepth + 1).coerceAtLeast(1)
            val rootHovered = hovered?.node?.path == model.root.path
            drawCircle(
                color = hslToColor(0.56f, 0.66f, if (rootHovered) 0.68f else 0.54f, growth),
                radius = band * growth,
                center = center,
            )
            if (rootHovered) {
                drawCircle(Color.White.copy(alpha = 0.9f), band * growth, center, style = Stroke(1.5f))
            }

            geometry.forEach { rendered ->
                val isHovered = rendered.sector.node.path == hovered?.node?.path
                drawPath(
                    rendered.path,
                    if (isHovered) rendered.color.copy(
                        red = (rendered.color.red + 0.16f).coerceAtMost(1f),
                        green = (rendered.color.green + 0.16f).coerceAtMost(1f),
                        blue = (rendered.color.blue + 0.16f).coerceAtMost(1f),
                    ) else rendered.color,
                )
                drawPath(rendered.path, Background.copy(alpha = 0.38f), style = Stroke(0.65f))
                if (isHovered) drawPath(rendered.path, Color.White.copy(alpha = 0.95f), style = Stroke(1.5f))
            }
        }

        hovered?.let { sector ->
            Column(
                Modifier.align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(TooltipBackground, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(sector.node.name, color = Color.White, fontSize = 14.sp)
                Text(
                    "${formatSize(sector.node.size)}${if (sector.node.truncated) "  •  partial scan" else ""}",
                    color = Color(0xFF73D7FF),
                    fontSize = 12.sp,
                )
                Text(sector.node.path, color = Color(0xFF9DA9BA), fontSize = 11.sp, maxLines = 1)
            }
        }

        contextSector?.let { sector ->
            Box(
                Modifier.offset {
                    IntOffset(contextPosition.x.roundToInt(), contextPosition.y.roundToInt())
                },
            ) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { contextSector = null },
                ) {
                    DropdownMenuItem(onClick = {
                        contextSector = null
                        onOpen(sector.node.path)
                    }) {
                        val manager = when (OS.name()) {
                            "macos" -> "Finder"
                            "windows" -> "Explorer"
                            else -> "file manager"
                        }
                        Text("Open in $manager")
                    }
                    DropdownMenuItem(
                        enabled = sector.node.depth > 0,
                        onClick = {
                            contextSector = null
                            onTrash(sector.node.path)
                        },
                    ) {
                        Text(
                            "Move to Trash",
                            color = if (sector.node.depth > 0) Color.White else Color.Gray,
                        )
                    }
                    DropdownMenuItem(
                        enabled = sector.node.depth > 0,
                        onClick = {
                            contextSector = null
                            pendingDelete = sector
                        },
                    ) {
                        Text("Delete", color = if (sector.node.depth > 0) Color(0xFFFF6B78) else Color.Gray)
                    }
                }
            }
        }

        pendingDelete?.let { sector ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete folder?") },
                text = {
                    Text(
                        "${sector.node.path}\n\nThis permanently deletes the folder and everything inside it.",
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        pendingDelete = null
                        onDelete(sector.node.path)
                    }) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(onClick = { pendingDelete = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        Row(
            Modifier.align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(TooltipBackground, RoundedCornerShape(12.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { zoom = (zoom / 1.2f).coerceAtLeast(0.45f) }) { Text("−") }
            Text("${(zoom * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
            Button(onClick = { zoom = (zoom * 1.2f).coerceAtMost(3f) }) { Text("+") }
            Button(onClick = { zoom = 1f }) { Text("Reset") }
        }
    }
}
