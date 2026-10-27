package com.example.infinitedraw.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.infinitedraw.model.BrushType
import com.example.infinitedraw.model.F
import com.example.infinitedraw.model.Stroke
import com.example.infinitedraw.model.StrokePoint
import com.example.infinitedraw.model.ZoomLevel
import com.example.infinitedraw.ui.DrawingViewModel
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class CanvasRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val dashEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 8f), 0f)
    // Step 24: worldToScreen scratch buffer (never used reentrantly within one draw path).
    private val wsOut = FloatArray(2)
    // Testing fix (plan Step 10): saveLayer paint for layer opacity + blend.
    private val layerPaint = Paint()

    private class Frame(val level: ZoomLevel, val x: Double, val y: Double, val z: Double, val exclude: ZoomLevel?)

    fun render(canvas: Canvas, vm: DrawingViewModel, w: Int, h: Int, debug: Boolean = true) {
        drawPaper(canvas, vm, w, h)
        renderWorld(canvas, vm, w, h)
        // U10: hover preview — a thin circle the size of the brush under the pen tip.
        vm.hover?.let { (hx, hy) ->
            paint.style = Paint.Style.STROKE
            paint.maskFilter = null
            paint.alpha = 255
            paint.color = Color.argb(90, 128, 128, 128)
            paint.strokeWidth = 2f
            canvas.drawCircle(hx, hy, vm.brushSizePx / 2f, paint)
        }
        val cam = vm.engine.camera
        // U1: debug text shows only when the renderer allows it AND vm.showDebug is on.
        if (!debug || !vm.showDebug) return
        // debug text
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textSize = 40f
        canvas.drawText("level=${cam.level.id} zoom=${cam.zoom} rot=${Math.toDegrees(cam.rotation).toInt()}", 20f, 60f, paint)
    }

    /** Paper color + grid/dots in screen space; follows the current camera. */
    private fun drawPaper(canvas: Canvas, vm: DrawingViewModel, w: Int, h: Int) {
        val paper = vm.project.paper
        canvas.drawColor(if (paper.color == -1) Color.WHITE else paper.color)
        if (paper.pattern == com.example.infinitedraw.model.PaperPattern.NONE) return

        val cam = vm.engine.camera
        val zoom = cam.zoom
        var sp = 20.0 * zoom
        while (sp > 160.0) sp /= 5.0
        while (sp < 20.0) sp *= 5.0
        val x0 = w / 2.0 - cam.cx * zoom
        val y0 = h / 2.0 - cam.cy * zoom
        val isDark = paper.color == -1 || (paper.color and 0x000000FF) + ((paper.color shr 8) and 0xFF) + ((paper.color shr 16) and 0xFF) < 381
        val lineColor = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(40, 0, 0, 0)
        paint.style = if (paper.pattern == com.example.infinitedraw.model.PaperPattern.DOTS) Paint.Style.FILL else Paint.Style.STROKE
        paint.color = lineColor
        paint.strokeWidth = 1f

        val firstX = x0 - Math.floor(x0 / sp) * sp
        val firstY = y0 - Math.floor(y0 / sp) * sp
        // Step 24: the grid is anchored to the world, so rotate the canvas with the
        // camera and extend the ranges by the screen diagonal so the rotated grid
        // still covers the viewport corners.
        val diag = kotlin.math.hypot(w.toDouble(), h.toDouble())
        val save = canvas.save()
        canvas.rotate(Math.toDegrees(cam.rotation).toFloat(), w / 2f, h / 2f)
        var gx = firstX
        while (gx > -diag) gx -= sp
        if (paper.pattern == com.example.infinitedraw.model.PaperPattern.GRID) {
            var x = gx
            while (x <= w + diag) {
                canvas.drawLine(x.toFloat(), (-diag).toFloat(), x.toFloat(), (h + diag).toFloat(), paint)
                x += sp
            }
            var gy = firstY
            while (gy > -diag) gy -= sp
            while (gy <= h + diag) {
                canvas.drawLine((-diag).toFloat(), gy.toFloat(), (w + diag).toFloat(), gy.toFloat(), paint)
                gy += sp
            }
        } else {
            var gy = firstY
            while (gy > -diag) gy -= sp
            val gyStart = gy
            var x = gx
            while (x <= w + diag) {
                var yy = gyStart
                while (yy <= h + diag) {
                    canvas.drawPoint(x.toFloat(), yy.toFloat(), paint)
                    yy += sp
                }
                x += sp
            }
        }
        canvas.restoreToCount(save)
    }

    fun renderWorld(canvas: Canvas, vm: DrawingViewModel, w: Int, h: Int) {
        val cam = vm.engine.camera
        // 1) ancestors first (underneath)
        val chain = ArrayList<Frame>()
        var l = cam.level; var x = cam.cx; var y = cam.cy; var z = cam.zoom
        while (l.parent != null) {
            val p = l.parent!!
            x = l.originX + x / F; y = l.originY + y / F; z *= F
            chain.add(Frame(p, x, y, z, l)); l = p
        }
        for (f in chain.asReversed()) drawLevelAndDown(canvas, vm, f.level, f.x, f.y, f.z, f.exclude, w, h)
        // 2) current level + all descendants on top
        drawLevelAndDown(canvas, vm, cam.level, cam.cx, cam.cy, cam.zoom, null, w, h)
        // 3) in-progress stroke on top
        drawInProgress(canvas, vm, w, h)
    }

    private fun drawLevelAndDown(canvas: Canvas, vm: DrawingViewModel, level: ZoomLevel, camX: Double, camY: Double,
                                 zoom: Double, exclude: ZoomLevel?, w: Int, h: Int) {
        drawLevelContent(canvas, vm, level, camX, camY, zoom, w, h)
        for (c in level.children) {
            if (c === exclude) continue
            val cz = zoom / F
            if (cz < 0.02) continue                                    // would be sub-pixel
            drawLevelAndDown(canvas, vm, c, (camX - c.originX) * F, (camY - c.originY) * F, cz, null, w, h)
        }
    }

    private fun drawLevelContent(canvas: Canvas, vm: DrawingViewModel, level: ZoomLevel, camX: Double, camY: Double,
                                 zoom: Double, w: Int, h: Int) {
        // Step 21: during a move gesture the selected item is drawn offset by the live preview.
        val sel = vm.selection
        val selOff = sel != null && sel.levelId == level.id && (vm.previewDx != 0f || vm.previewDy != 0f)
        for (layer in vm.project.layers) {
            if (!layer.visible) continue
            // Testing fix (plan Step 10): wrap the WHOLE layer in saveLayer when it has
            // opacity < 1 or a blend mode — previously only tiles/images got the alpha and
            // vector strokes ignored layer opacity entirely. Plain layers skip saveLayer (fast).
            val needsLayer = layer.opacity < 1f || layer.blend != com.example.infinitedraw.model.LayerBlend.NORMAL
            val save = if (needsLayer) {
                layerPaint.alpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
                layerPaint.blendMode = blendOf(layer.blend)
                canvas.saveLayer(null, layerPaint)
            } else canvas.save()
            drawRasterTiles(canvas, vm, level, camX, camY, zoom, w, h, layer.id)
            for (img in level.images) {
                if (img.layerId != layer.id) continue
                val iox = if (selOff && sel!!.imageId == img.id) vm.previewDx.toDouble() else 0.0
                val ioy = if (selOff && sel!!.imageId == img.id) vm.previewDy.toDouble() else 0.0
                drawImage(canvas, vm, img, camX, camY, zoom, w, h, iox, ioy)
            }
            for (s in level.strokes) {
                if (s.layerId != layer.id) continue
                if (s in vm.pendingRemoved) continue
                val sox = if (selOff && sel!!.strokeId == s.id) vm.previewDx.toDouble() else 0.0
                val soy = if (selOff && sel!!.strokeId == s.id) vm.previewDy.toDouble() else 0.0
                drawStroke(canvas, s, camX, camY, zoom, w, h, paint, path, sox, soy, vm.engine.camera.rotation)
            }
            canvas.restoreToCount(save)
        }
        drawSelectionOutline(canvas, vm, level, camX, camY, zoom, w, h)
    }

    /**
     * Step 21: dashed rectangle around the selected item's bounds (its own level,
     * preview offset applied). Only drawn while the SELECT tool is active.
     */
    private fun drawSelectionOutline(canvas: Canvas, vm: DrawingViewModel, level: ZoomLevel,
                                     camX: Double, camY: Double, zoom: Double, w: Int, h: Int) {
        if (vm.tool != com.example.infinitedraw.ui.Tool.SELECT) return
        val sel = vm.selection ?: return
        if (sel.levelId != level.id) return
        val b = FloatArray(4)
        if (!vm.selectionBounds(b)) return
        val ox = vm.previewDx.toDouble(); val oy = vm.previewDy.toDouble()
        val rot = vm.engine.camera.rotation
        val sinR = sin(rot); val cosR = cos(rot)
        paint.style = Paint.Style.STROKE
        paint.maskFilter = null
        paint.alpha = 255
        paint.strokeWidth = 2f
        paint.color = 0xFF2196F3.toInt()
        paint.pathEffect = dashEffect
        // Step 24: the bounds rect rotates with the world — draw its 4 corners.
        path.rewind()
        worldToScreen(b[0] + ox, b[1] + oy, camX, camY, zoom, sinR, cosR, w, h, wsOut)
        path.moveTo(wsOut[0], wsOut[1])
        worldToScreen(b[2] + ox, b[1] + oy, camX, camY, zoom, sinR, cosR, w, h, wsOut)
        path.lineTo(wsOut[0], wsOut[1])
        worldToScreen(b[2] + ox, b[3] + oy, camX, camY, zoom, sinR, cosR, w, h, wsOut)
        path.lineTo(wsOut[0], wsOut[1])
        worldToScreen(b[0] + ox, b[3] + oy, camX, camY, zoom, sinR, cosR, w, h, wsOut)
        path.lineTo(wsOut[0], wsOut[1])
        path.close()
        canvas.drawPath(path, paint)
        paint.pathEffect = null
    }

    /** Phase 2: draw every raster tile of this level+layer that intersects the viewport. */
    private fun drawRasterTiles(canvas: Canvas, vm: DrawingViewModel, level: ZoomLevel, camX: Double, camY: Double,
                                zoom: Double, w: Int, h: Int, layerId: Long) {
        val rt = com.example.infinitedraw.render.RasterTiles
        val rot = vm.engine.camera.rotation
        val sinR = sin(rot); val cosR = cos(rot)
        val tileR = rt.TILE * 0.7072 * zoom   // half-diagonal in px (Step 24 circle culling)
        for (t in rt.tilesSnapshot()) {
            if (t.key.levelId != level.id || t.key.layerId != layerId) continue
            // tile rect in level-local coords
            val x0 = t.key.tx * rt.TILE.toDouble()
            val y0 = t.key.ty * rt.TILE.toDouble()
            val x1 = x0 + rt.TILE
            val y1 = y0 + rt.TILE
            // Step 24: circle cull (rect culls are wrong under rotation).
            worldToScreen((x0 + x1) / 2, (y0 + y1) / 2, camX, camY, zoom, sinR, cosR, w, h, wsOut)
            if (wsOut[0] + tileR < 0 || wsOut[0] - tileR > w || wsOut[1] + tileR < 0 || wsOut[1] - tileR > h) continue
            rt.touch(t)
            // Evicted tiles: reload their PNG from disk when scrolled back into view.
            if (t.bitmap.isRecycled()) {
                rt.loadTile(vm.getApplication<android.app.Application>(), vm.project.name, t.key)
            }
            if (!t.bitmap.isRecycled()) {
                val sx0 = ((x0 - camX) * zoom + w / 2.0).toFloat()
                val sy0 = ((y0 - camY) * zoom + h / 2.0).toFloat()
                val sx1 = ((x1 - camX) * zoom + w / 2.0).toFloat()
                val sy1 = ((y1 - camY) * zoom + h / 2.0).toFloat()
                val dst = android.graphics.RectF(sx0, sy0, sx1, sy1)
                paint.style = Paint.Style.FILL
                paint.isFilterBitmap = true
                val save = canvas.save()
                canvas.rotate(Math.toDegrees(rot).toFloat(), (sx0 + sx1) / 2f, (sy0 + sy1) / 2f)
                canvas.drawBitmap(t.bitmap, null, dst, paint)
                canvas.restoreToCount(save)
            }
        }
    }

    private fun drawImage(canvas: Canvas, vm: DrawingViewModel, img: com.example.infinitedraw.model.PlacedImage,
                          camX: Double, camY: Double, zoom: Double, w: Int, h: Int,
                          offX: Double = 0.0, offY: Double = 0.0) {
        val bmp = vm.bitmaps[img.id] ?: return
        val rot = vm.engine.camera.rotation
        val sinR = sin(rot); val cosR = cos(rot)
        // Step 24: circle cull, then canvas.rotate about the image's screen center.
        worldToScreen(img.x + img.w / 2.0 + offX, img.y + img.h / 2.0 + offY, camX, camY, zoom, sinR, cosR, w, h, wsOut)
        val rPix = hypot(img.w.toDouble(), img.h.toDouble()) / 2.0 * zoom
        if (wsOut[0] + rPix < 0 || wsOut[0] - rPix > w || wsOut[1] + rPix < 0 || wsOut[1] - rPix > h) return
        val l = ((img.x + offX - camX) * zoom + w / 2f).toFloat()
        val t = ((img.y + offY - camY) * zoom + h / 2f).toFloat()
        val r = ((img.x + offX + img.w - camX) * zoom + w / 2f).toFloat()
        val b = ((img.y + offY + img.h - camY) * zoom + h / 2f).toFloat()
        val dst = android.graphics.RectF(l, t, r, b)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        val save = canvas.save()
        canvas.rotate(Math.toDegrees(rot).toFloat(), (l + r) / 2f, (t + b) / 2f)
        canvas.drawBitmap(bmp, null, dst, paint)
        canvas.restoreToCount(save)
    }

    /** Testing fix (plan Step 10): layer blend for the saveLayer paint (minSdk 29). */
    private fun blendOf(b: com.example.infinitedraw.model.LayerBlend): android.graphics.BlendMode = when (b) {
        com.example.infinitedraw.model.LayerBlend.NORMAL -> android.graphics.BlendMode.SRC_OVER
        com.example.infinitedraw.model.LayerBlend.MULTIPLY -> android.graphics.BlendMode.MULTIPLY
        com.example.infinitedraw.model.LayerBlend.SCREEN -> android.graphics.BlendMode.SCREEN
        com.example.infinitedraw.model.LayerBlend.OVERLAY -> android.graphics.BlendMode.OVERLAY
        com.example.infinitedraw.model.LayerBlend.DARKEN -> android.graphics.BlendMode.DARKEN
        com.example.infinitedraw.model.LayerBlend.LIGHTEN -> android.graphics.BlendMode.LIGHTEN
    }

    private fun drawInProgress(canvas: Canvas, vm: DrawingViewModel, w: Int, h: Int) {
        val cam = vm.engine.camera
        val a = vm.active
        if (a != null) {
            val temp = Stroke(
                -1L, vm.activeLayerId, when (vm.tool) {
                    com.example.infinitedraw.ui.Tool.MARKER -> BrushType.MARKER
                    com.example.infinitedraw.ui.Tool.AIRBRUSH -> BrushType.AIRBRUSH
                    com.example.infinitedraw.ui.Tool.PENCIL -> BrushType.PENCIL
                    com.example.infinitedraw.ui.Tool.CHARCOAL -> BrushType.CHARCOAL
                    else -> BrushType.PEN
                },
                vm.color, vm.brushSizePx / cam.zoom.toFloat(),
                vm.usePressure && (vm.tool == com.example.infinitedraw.ui.Tool.PEN ||
                    vm.tool == com.example.infinitedraw.ui.Tool.PENCIL ||
                    vm.tool == com.example.infinitedraw.ui.Tool.CHARCOAL), a.toList()
            )
            drawStroke(canvas, temp, cam.cx, cam.cy, cam.zoom, w, h, paint, path, 0.0, 0.0, cam.rotation)
        }
    }

    /** Step 24: shared world -> screen transform — the inverse of engine.screenToLocal. Fills out = [sx, sy]. */
    private fun worldToScreen(lx: Double, ly: Double, camX: Double, camY: Double, zoom: Double,
                               sinR: Double, cosR: Double, w: Int, h: Int, out: FloatArray) {
        val px = (lx - camX) * zoom
        val py = (ly - camY) * zoom
        out[0] = (w / 2.0 + cosR * px - sinR * py).toFloat()
        out[1] = (h / 2.0 + sinR * px + cosR * py).toFloat()
    }

    fun drawStroke(canvas: Canvas, s: Stroke, camX: Double, camY: Double, zoom: Double,
                   w: Int, h: Int, paint: Paint, path: Path, offX: Double = 0.0, offY: Double = 0.0,
                   rotation: Double = 0.0) {
        val pts = s.points
        val n = pts.size
        if (n == 0) return
        val sinR = sin(rotation); val cosR = cos(rotation)
        val b = s.bounds
        // Step 24: circle cull — project the bounds center and cull by its
        // half-diagonal circle (rect culls are wrong under rotation).
        // Pencil/charcoal dabs jitter ~0.5 radius past the padded bounds, so pad them.
        worldToScreen((b[0] + b[2]) / 2.0 + offX, (b[1] + b[3]) / 2.0 + offY, camX, camY, zoom, sinR, cosR, w, h, wsOut)
        var rPix = hypot((b[2] - b[0]).toDouble(), (b[3] - b[1]).toDouble()) / 2.0 * zoom
        if (s.brush == BrushType.PENCIL || s.brush == BrushType.CHARCOAL) rPix += 0.6 * s.width * zoom
        if (wsOut[0] + rPix < 0 || wsOut[0] - rPix > w || wsOut[1] + rPix < 0 || wsOut[1] - rPix > h) return   // cull

        val xs = FloatArray(n); val ys = FloatArray(n); val rs = FloatArray(n)
        for (i in 0 until n) {
            worldToScreen(pts[i].x + offX, pts[i].y + offY, camX, camY, zoom, sinR, cosR, w, h, wsOut)
            xs[i] = wsOut[0].coerceIn(-100000f, 100000f)
            ys[i] = wsOut[1].coerceIn(-100000f, 100000f)
            val pf = if (s.usePressure) 0.15 + 0.85 * pts[i].p else 1.0
            rs[i] = (0.5 * s.width * zoom * pf).toFloat()
        }
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        paint.maskFilter = null
        paint.color = s.color

        when (s.brush) {
            BrushType.PENCIL, BrushType.CHARCOAL -> {
                // Step 22: grainy dab stamping instead of a filled ribbon.
                drawDabs(canvas, s, camX, camY, zoom, w, h, paint, rotation)
                return
            }
            BrushType.MARKER -> {
                // flat, slightly translucent, pressure ignored
                paint.alpha = ((paint.color ushr 24) * 0.7f).toInt().coerceAtMost(255)
            }
            BrushType.AIRBRUSH -> {
                // Step 8 spec: 50% alpha + soft blur radius 0.6*width*zoom, only while <= 300px
                val blur = (0.6 * s.width * zoom).toFloat()
                if (blur in 1f..300f)
                    paint.maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
                paint.alpha = ((paint.color ushr 24) * 0.5f).toInt().coerceAtMost(255)
            }
            else -> {}
        }

        if (n >= 2) {
            val lx = FloatArray(n); val ly = FloatArray(n); val rx = FloatArray(n); val ry = FloatArray(n)
            for (i in 0 until n) {
                val a = maxOf(i - 1, 0); val c = minOf(i + 1, n - 1)
                var dx = xs[c] - xs[a]; var dy = ys[c] - ys[a]
                val len = kotlin.math.hypot(dx, dy)
                if (len == 0f) { dx = 1f; dy = 0f } else { dx /= len; dy /= len }
                val nx = -dy; val ny = dx
                lx[i] = xs[i] + nx * rs[i]; ly[i] = ys[i] + ny * rs[i]
                rx[i] = xs[i] - nx * rs[i]; ry[i] = ys[i] - ny * rs[i]
            }
            path.rewind()
            path.moveTo(lx[0], ly[0])
            for (i in 1 until n) path.lineTo(lx[i], ly[i])
            for (i in n - 1 downTo 0) path.lineTo(rx[i], ry[i])
            path.close()
            canvas.drawPath(path, paint)
        }
        canvas.drawCircle(xs[0], ys[0], rs[0], paint)
        canvas.drawCircle(xs[n - 1], ys[n - 1], rs[n - 1], paint)
    }

    /**
     * Step 22: PENCIL / CHARCOAL. Stamps small dabs along the polyline in
     * LEVEL-LOCAL space (spacing = 0.35 x local radius) so the dab set is fixed
     * by the stroke geometry alone: the grain cannot swim while panning or
     * zooming. Each dab's offset, size and alpha come from dabHash(id, index) —
     * a pure function, no Random, no image assets. Capped at 3000 dabs per stroke.
     */
    private fun drawDabs(canvas: Canvas, s: Stroke, camX: Double, camY: Double, zoom: Double,
                         w: Int, h: Int, paint: Paint, rotation: Double) {
        val pts = s.points
        val n = pts.size
        if (n == 0 || s.width <= 0f) return
        val sinR = sin(rotation); val cosR = cos(rotation)
        val pencil = s.brush == BrushType.PENCIL
        // Brush character, first-pass values (tune on device):
        val sizeLo = if (pencil) 0.55f else 0.45f    // dab radius as fraction of local radius
        val sizeHi = if (pencil) 0.95f else 0.90f
        val alphaLo = if (pencil) 0.60f else 0.28f    // per-dab alpha range (x stroke color alpha)
        val alphaHi = if (pencil) 1.00f else 0.55f
        val jitter = if (pencil) 0.30f else 0.45f     // center offset as fraction of local radius
        val rgb = s.color and 0x00FFFFFF
        val baseA = s.color ushr 24
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        paint.maskFilter = null

        var dab = 0
        fun rad(p: StrokePoint): Float =
            0.5f * s.width * (if (s.usePressure) 0.15f + 0.85f * p.p else 1f)

        fun stamp(lx: Float, ly: Float, r: Float) {
            if (dab >= 3000) return
            val hv = dabHash(s.id, dab)
            val f1 = (hv and 0xFF) / 255f
            val f2 = ((hv ushr 8) and 0xFF) / 255f
            val f3 = ((hv ushr 16) and 0xFF) / 255f
            val f4 = ((hv ushr 24) and 0xFF) / 255f
            dab++   // offscreen dabs still burn an index -> the grain cannot shift while panning
            val dr = r * (sizeLo + (sizeHi - sizeLo) * f3)
            // Step 24: jitter in world space, then one shared transform to screen.
            worldToScreen((lx + (f1 * 2f - 1f) * jitter * r).toDouble(), (ly + (f2 * 2f - 1f) * jitter * r).toDouble(),
                camX, camY, zoom, sinR, cosR, w, h, wsOut)
            val sx = wsOut[0]
            val sy = wsOut[1]
            val sr = (dr * zoom).toFloat()
            if (sx + sr < 0f || sx - sr > w || sy + sr < 0f || sy - sr > h) return
            val a = ((alphaLo + (alphaHi - alphaLo) * f4) * baseA).toInt().coerceIn(0, 255)
            paint.color = (a shl 24) or rgb
            canvas.drawCircle(sx, sy, sr, paint)
        }

        stamp(pts[0].x, pts[0].y, rad(pts[0]))      // start dab (also the whole stroke for n == 1)
        var carry = 0.35f * rad(pts[0])             // arclength to the next dab
        for (i in 0 until n - 1) {
            if (dab >= 3000) break
            val a = pts[i]; val c = pts[i + 1]
            val segLen = hypot(c.x - a.x, c.y - a.y)
            if (segLen < 1e-5f) continue
            val ar = rad(a); val cr = rad(c)
            var t = carry / segLen
            while (t <= 1f && dab < 3000) {
                val r = ar + (cr - ar) * t
                stamp(a.x + (c.x - a.x) * t, a.y + (c.y - a.y) * t, r)
                val sp = 0.35f * r
                if (sp <= 1e-5f) break
                t += sp / segLen
            }
            carry = if (t > 1f) (t - 1f) * segLen else 0f
        }
        if (n > 1 && dab < 3000) {                  // guarantee the pen-lift end is covered
            val last = pts[n - 1]
            stamp(last.x, last.y, rad(last))
        }
    }

    private fun clampS(v: Double): Float = v.coerceIn(-100000.0, 100000.0).toFloat()
}

/** Step 22: deterministic dab jitter hash — pure function of (strokeId, dabIndex); never Random. */
internal fun dabHash(id: Long, dab: Int): Int {
    var h = id.toInt() * 0x45d9f3b + dab * 0x2545f491
    h = (h xor (h ushr 15)) * 0x2c1b3c6c
    h = (h xor (h ushr 12)) * 0x297a2d39
    return h xor (h ushr 15)
}
