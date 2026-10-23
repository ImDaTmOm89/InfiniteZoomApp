package com.example.infinitedraw.model
import kotlinx.serialization.Serializable

const val F = 100.0   // zoom factor between levels

@Serializable data class StrokePoint(val x: Float, val y: Float, val p: Float)
@Serializable enum class BrushType { PEN, MARKER, AIRBRUSH, PENCIL, CHARCOAL }
@Serializable enum class LayerBlend { NORMAL, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN }
@Serializable enum class PaperPattern { NONE, GRID, DOTS }
@Serializable class Paper(var color: Int = -1, var pattern: PaperPattern = PaperPattern.NONE) // -1 = opaque white

@Serializable
class Stroke(
    val id: Long, val layerId: Long, val brush: BrushType,
    val color: Int,            // ARGB
    val width: Float,          // LEVEL-LOCAL units, not pixels
    val usePressure: Boolean,
    val points: List<StrokePoint>,
) {
    /** minX, minY, maxX, maxY in level-local units, padded by half the width */
    val bounds: FloatArray by lazy {
        var a = Float.MAX_VALUE; var b = Float.MAX_VALUE; var c = -Float.MAX_VALUE; var d = -Float.MAX_VALUE
        for (q in points) { if (q.x < a) a = q.x; if (q.y < b) b = q.y; if (q.x > c) c = q.x; if (q.y > d) d = q.y }
        val h = width / 2f
        floatArrayOf(a - h, b - h, c + h, d + h)
    }
}

@Serializable
class Layer(
    val id: Long, var name: String,
    var visible: Boolean = true, var locked: Boolean = false,
    var opacity: Float = 1f, var blend: LayerBlend = LayerBlend.NORMAL,
)

@Serializable
class PlacedImage(val id: Long, val layerId: Long, val file: String,
                  val x: Float, val y: Float, val w: Float, val h: Float) // local units, x,y = top-left

class ZoomLevel(val id: Int, var parent: ZoomLevel?, var originX: Double, var originY: Double) {
    val children = mutableListOf<ZoomLevel>()
    val strokes = mutableListOf<Stroke>()
    val images = mutableListOf<PlacedImage>()
}
