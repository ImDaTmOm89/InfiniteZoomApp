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
import kotlin.math.hypot

class CanvasRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val dashEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 8f), 0f)

    private class Frame(val level: ZoomLevel, val x: Double, val y: Double, val z: Double, val exclude: ZoomLevel?)

    fun render(canvas: Canvas, vm: DrawingViewModel, w: Int, h: Int, debug: Boolean = true) {
        drawPaper(canvas, vm, w, h)
        renderWorld(canvas, vm, w, h)
        val cam = vm.engine.camera
        if (!debug) return
        // debug text
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textSize = 40f
        canvas.drawText("level=${cam.level.id} zoom=${cam.zoom}", 20f, 60f, paint)
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
        var gx = firstX
        while (gx <= w) {
            if (paper.pattern == com.example.infinitedraw.model.PaperPattern.GRID) {
                canvas.drawLine(gx.toFloat(), 0f, gx.toFloat(), h.toFloat(), paint)
            } else {
                var gy = firstY
                while (gy <= h) {
                    canvas.drawPoint(gx.toFloat(), gy.toFloat(), paint)
                    gy += sp
                }
            }
            gx += sp
        }
        if (paper.pattern == com.example.infinitedraw.model.PaperPattern.GRID) {
            var gy = firstY
            while (gy <= h) {
                canvas.drawLine(0f, gy.toFloat(), w.toFloat(), gy.toFloat(), paint)
                gy += sp
            }
        }
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
        val z = zoom.toFloat()
        // Step 21: during a move gesture the selected item is drawn offset by the live preview.
        val sel = vm.selection
        val selOff = sel != null && sel.levelId == level.id && (vm.previewDx != 0f || vm.previewDy != 0f)
        for (layer in vm.project.layers) {
            if (!layer.visible) continue
            val layerAlpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
            val save = canvas.save()
            paint.xfermode = blendMode(layer.blend)
            drawRasterTiles(canvas, vm, level, camX, camY, zoom, w, h, layer.id, layerAlpha)
            for (img in level.images) {
                if (img.layerId != layer.id) continue
                val iox = if (selOff && sel!!.imageId == img.id) vm.previewDx.toDouble() else 0.0
                val ioy = if (selOff && sel!!.imageId == img.id) vm.previewDy.toDouble() else 0.0
                drawImage(canvas, vm, img, camX, camY, zoom, w, h, layerAlpha, iox, ioy)
            }
            for (s in level.strokes) {
                if (s.layerId != layer.id) continue
                if (s in vm.pendingRemoved) continue
                val sox = if (selOff && sel!!.strokeId == s.id) vm.previewDx.toDouble() else 0.0
                val soy = if (selOff && sel!!.strokeId == s.id) vm.previewDy.toDouble() else 0.0
                if (cull(s, camX.toFloat(), camY.toFloat(), z, w, h, sox, soy)) continue
                drawStroke(canvas, s, camX, camY, zoom, w, h, paint, path, sox, soy)
            }
            paint.xfermode = null
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
        val l = ((b[0] + ox - camX) * zoom + w / 2.0).toFloat()
        val t = ((b[1] + oy - camY) * zoom + h / 2.0).toFloat()
        val r = ((b[2] + ox - camX) * zoom + w / 2.0).toFloat()
        val bo = ((b[3] + oy - camY) * zoom + h / 2.0).toFloat()
        if (r < 0 || bo < 0 || l > w || t > h) return
        paint.style = Paint.Style.STROKE
        paint.maskFilter = null
        paint.alpha = 255
        paint.strokeWidth = 2f
        paint.color = 0xFF2196F3.toInt()
        paint.pathEffect = dashEffect
        canvas.drawRect(l, t, r, bo, paint)
        paint.pathEffect = null
    }

    /** Phase 2: draw every raster tile of this level+layer that intersects the viewport. */
    private fun drawRasterTiles(canvas: Canvas, vm: DrawingViewModel, level: ZoomLevel, camX: Double, camY: Double,
                                zoom: Double, w: Int, h: Int, layerId: Long, layerAlpha: Int) {
        val rt = com.example.infinitedraw.render.RasterTiles
        for (t in rt.tilesSnapshot()) {
            if (t.key.levelId != level.id || t.key.layerId != layerId) continue
            // tile rect in level-local coords
            val x0 = t.key.tx * rt.TILE.toDouble()
            val y0 = t.key.ty * rt.TILE.toDouble()
            val x1 = x0 + rt.TILE
            val y1 = y0 + rt.TILE
            val sx0 = ((x0 - camX) * zoom + w / 2.0)
            val sy0 = ((y0 - camY) * zoom + h / 2.0)
            val sx1 = ((x1 - camX) * zoom + w / 2.0)
            val sy1 = ((y1 - camY) * zoom + h / 2.0)
            if (sx1 < 0 || sy1 < 0 || sx0 > w || sy0 > h) continue   // cull
            rt.touch(t)
            // Evicted tiles: reload their PNG from disk when scrolled back into view.
            if (t.bitmap.isRecycled()) {
                rt.loadTile(vm.getApplication<android.app.Application>(), vm.project.name, t.key)
            }
            if (!t.bitmap.isRecycled()) {
                val dst = android.graphics.RectF(sx0.toFloat(), sy0.toFloat(), sx1.toFloat(), sy1.toFloat())
                paint.alpha = layerAlpha
                paint.style = Paint.Style.FILL
                paint.isFilterBitmap = true
                canvas.drawBitmap(t.bitmap, null, dst, paint)
            }
        }
    }

    private fun drawImage(canvas: Canvas, vm: DrawingViewModel, img: com.example.infinitedraw.model.PlacedImage,
                          camX: Double, camY: Double, zoom: Double, w: Int, h: Int, layerAlpha: Int,
                          offX: Double = 0.0, offY: Double = 0.0) {
        val bmp = vm.bitmaps[img.id] ?: return
        val l = ((img.x + offX - camX) * zoom + w / 2f).toFloat()
        val t = ((img.y + offY - camY) * zoom + h / 2f).toFloat()
        val r = ((img.x + offX + img.w - camX) * zoom + w / 2f).toFloat()
        val b = ((img.y + offY + img.h - camY) * zoom + h / 2f).toFloat()
        if (r < 0 || b < 0 || l > w || t > h) return
        val dst = android.graphics.RectF(l, t, r, b)
        paint.alpha = layerAlpha
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        canvas.drawBitmap(bmp, null, dst, paint)
        paint.alpha = 255
    }

    private fun blendMode(blend: com.example.infinitedraw.model.LayerBlend): android.graphics.PorterDuffXfermode? = when (blend) {
        com.example.infinitedraw.model.LayerBlend.NORMAL -> null
        com.example.infinitedraw.model.LayerBlend.MULTIPLY -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
        com.example.infinitedraw.model.LayerBlend.SCREEN -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
        com.example.infinitedraw.model.LayerBlend.OVERLAY -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.OVERLAY)
        com.example.infinitedraw.model.LayerBlend.DARKEN -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DARKEN)
        com.example.infinitedraw.model.LayerBlend.LIGHTEN -> android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.LIGHTEN)
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
            drawStroke(canvas, temp, cam.cx, cam.cy, cam.zoom, w, h, paint, path)
        }
    }

    private fun cull(s: Stroke, camX: Float, camY: Float, zoom: Float, w: Int, h: Int,
                     offX: Double = 0.0, offY: Double = 0.0): Boolean {
        val b = s.bounds
        val l = ((b[0] + offX - camX) * zoom + w / 2f)
        val t = ((b[1] + offY - camY) * zoom + h / 2f)
        val r = ((b[2] + offX - camX) * zoom + w / 2f)
        val bo = ((b[3] + offY - camY) * zoom + h / 2f)
        return r < 0 || bo < 0 || l > w || t > h
    }

    fun drawStroke(canvas: Canvas, s: Stroke, camX: Double, camY: Double, zoom: Double,
                   w: Int, h: Int, paint: Paint, path: Path, offX: Double = 0.0, offY: Double = 0.0) {
        val pts = s.points
        val n = pts.size
        if (n == 0) return
        val b = s.bounds
        val x0 = (b[0] + offX - camX) * zoom + w / 2.0; val x1 = (b[2] + offX - camX) * zoom + w / 2.0
        val y0 = (b[1] + offY - camY) * zoom + h / 2.0; val y1 = (b[3] + offY - camY) * zoom + h / 2.0
        // Pencil/charcoal dabs jitter up to ~0.5 radius outside the padded bounds.
        val pad = if (s.brush == BrushType.PENCIL || s.brush == BrushType.CHARCOAL) 0.6 * s.width * zoom else 0.0
        if (x1 + pad < 0 || x0 - pad > w || y1 + pad < 0 || y0 - pad > h) return   // cull

        val xs = FloatArray(n); val ys = FloatArray(n); val rs = FloatArray(n)
        for (i in 0 until n) {
            xs[i] = ((pts[i].x + offX - camX) * zoom + w / 2.0).coerceIn(-100000.0, 100000.0).toFloat()
            ys[i] = ((pts[i].y + offY - camY) * zoom + h / 2.0).coerceIn(-100000.0, 100000.0).toFloat()
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
                drawDabs(canvas, s, camX, camY, zoom, w, h, paint)
                return
            }
            BrushType.MARKER -> {
                // flat, slightly translucent, pressure ignored
                paint.alpha = ((paint.color ushr 24) * 0.7f).toInt().coerceAtMost(255)
            }
            BrushType.AIRBRUSH -> {
                // soft blurred edge
                paint.maskFilter = android.graphics.BlurMaskFilter(
                    (0.35f * s.width * zoom).toFloat().coerceAtLeast(1f),
                    android.graphics.BlurMaskFilter.Blur.NORMAL)
                paint.alpha = ((paint.color ushr 24) * 0.8f).toInt().coerceAtMost(255)
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
                         w: Int, h: Int, paint: Paint) {
        val pts = s.points
        val n = pts.size
        if (n == 0 || s.width <= 0f) return
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
            val sx = ((lx + (f1 * 2f - 1f) * jitter * r - camX) * zoom + w / 2.0).toFloat()
            val sy = ((ly + (f2 * 2f - 1f) * jitter * r - camY) * zoom + h / 2.0).toFloat()
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
