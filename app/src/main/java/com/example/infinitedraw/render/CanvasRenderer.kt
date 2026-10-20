package com.example.infinitedraw.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.infinitedraw.model.BrushType
import com.example.infinitedraw.model.F
import com.example.infinitedraw.model.Stroke
import com.example.infinitedraw.model.ZoomLevel
import com.example.infinitedraw.ui.DrawingViewModel
import kotlin.math.hypot

class CanvasRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

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
            var gy = firstY
            while (gy <= h) {
                if (paper.pattern == com.example.infinitedraw.model.PaperPattern.DOTS) {
                    canvas.drawPoint(gx.toFloat(), gy.toFloat(), paint)
                } else {
                    canvas.drawLine(gx.toFloat(), 0f, gx.toFloat(), h.toFloat(), paint)
                    canvas.drawLine(0f, gy.toFloat(), w.toFloat(), gy.toFloat(), paint)
                }
                gy += sp
            }
            gx += sp
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
        for (layer in vm.project.layers) {
            if (!layer.visible) continue
            val layerAlpha = (layer.opacity * 255).toInt().coerceIn(0, 255)
            val save = canvas.save()
            paint.xfermode = blendMode(layer.blend)
            drawRasterTiles(canvas, vm, level, camX, camY, zoom, w, h, layer.id, layerAlpha)
            for (img in level.images) {
                if (img.layerId != layer.id) continue
                drawImage(canvas, vm, img, camX, camY, zoom, w, h, layerAlpha)
            }
            for (s in level.strokes) {
                if (s.layerId != layer.id) continue
                if (s in vm.pendingRemoved) continue
                if (cull(s, camX.toFloat(), camY.toFloat(), z, w, h)) continue
                drawStroke(canvas, s, camX, camY, zoom, w, h, paint, path)
            }
            paint.xfermode = null
            canvas.restoreToCount(save)
        }
    }

    /** Phase 2: draw every raster tile of this level+layer that intersects the viewport. */
    private fun drawRasterTiles(canvas: Canvas, vm: DrawingViewModel, level: ZoomLevel, camX: Double, camY: Double,
                                zoom: Double, w: Int, h: Int, layerId: Long, layerAlpha: Int) {
        for (t in com.example.infinitedraw.render.RasterTiles.tiles.values) {
            if (t.key.levelId != level.id || t.key.layerId != layerId) continue
            // tile rect in level-local coords
            val x0 = t.key.tx * com.example.infinitedraw.render.RasterTiles.TILE.toDouble()
            val y0 = t.key.ty * com.example.infinitedraw.render.RasterTiles.TILE.toDouble()
            val x1 = x0 + com.example.infinitedraw.render.RasterTiles.TILE
            val y1 = y0 + com.example.infinitedraw.render.RasterTiles.TILE
            val sx0 = ((x0 - camX) * zoom + w / 2.0)
            val sy0 = ((y0 - camY) * zoom + h / 2.0)
            val sx1 = ((x1 - camX) * zoom + w / 2.0)
            val sy1 = ((y1 - camY) * zoom + h / 2.0)
            if (sx1 < 0 || sy1 < 0 || sx0 > w || sy0 > h) continue   // cull
            val dst = android.graphics.RectF(sx0.toFloat(), sy0.toFloat(), sx1.toFloat(), sy1.toFloat())
            paint.alpha = layerAlpha
            paint.style = Paint.Style.FILL
            paint.isFilterBitmap = true
            canvas.drawBitmap(t.bitmap, null, dst, paint)
        }
    }

    private fun drawImage(canvas: Canvas, vm: DrawingViewModel, img: com.example.infinitedraw.model.PlacedImage,
                          camX: Double, camY: Double, zoom: Double, w: Int, h: Int, layerAlpha: Int) {
        val bmp = vm.bitmaps[img.id] ?: return
        val l = ((img.x - camX) * zoom + w / 2f).toFloat()
        val t = ((img.y - camY) * zoom + h / 2f).toFloat()
        val r = ((img.x + img.w - camX) * zoom + w / 2f).toFloat()
        val b = ((img.y + img.h - camY) * zoom + h / 2f).toFloat()
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
                    else -> BrushType.PEN
                },
                vm.color, vm.brushSizePx / cam.zoom.toFloat(), vm.usePressure && vm.tool == com.example.infinitedraw.ui.Tool.PEN, a.toList()
            )
            drawStroke(canvas, temp, cam.cx, cam.cy, cam.zoom, w, h, paint, path)
        }
    }

    private fun cull(s: Stroke, camX: Float, camY: Float, zoom: Float, w: Int, h: Int): Boolean {
        val b = s.bounds
        val l = ((b[0] - camX) * zoom + w / 2f)
        val t = ((b[1] - camY) * zoom + h / 2f)
        val r = ((b[2] - camX) * zoom + w / 2f)
        val bo = ((b[3] - camY) * zoom + h / 2f)
        return r < 0 || bo < 0 || l > w || t > h
    }

    fun drawStroke(canvas: Canvas, s: Stroke, camX: Double, camY: Double, zoom: Double,
                   w: Int, h: Int, paint: Paint, path: Path) {
        val pts = s.points
        val n = pts.size
        if (n == 0) return
        val b = s.bounds
        val x0 = (b[0] - camX) * zoom + w / 2.0; val x1 = (b[2] - camX) * zoom + w / 2.0
        val y0 = (b[1] - camY) * zoom + h / 2.0; val y1 = (b[3] - camY) * zoom + h / 2.0
        if (x1 < 0 || x0 > w || y1 < 0 || y0 > h) return   // cull

        val xs = FloatArray(n); val ys = FloatArray(n); val rs = FloatArray(n)
        for (i in 0 until n) {
            xs[i] = ((pts[i].x - camX) * zoom + w / 2.0).coerceIn(-100000.0, 100000.0).toFloat()
            ys[i] = ((pts[i].y - camY) * zoom + h / 2.0).coerceIn(-100000.0, 100000.0).toFloat()
            val pf = if (s.usePressure) 0.15 + 0.85 * pts[i].p else 1.0
            rs[i] = (0.5 * s.width * zoom * pf).toFloat()
        }
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        paint.maskFilter = null
        paint.color = s.color

        when (s.brush) {
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

    private fun clampS(v: Double): Float = v.coerceIn(-100000.0, 100000.0).toFloat()
}
