package com.example.infinitedraw.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.infinitedraw.model.F
import com.example.infinitedraw.model.Stroke
import com.example.infinitedraw.model.ZoomLevel
import com.example.infinitedraw.ui.DrawingViewModel
import kotlin.math.hypot

class CanvasRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private class Frame(val level: ZoomLevel, val x: Double, val y: Double, val z: Double, val exclude: ZoomLevel?)

    fun render(canvas: Canvas, vm: DrawingViewModel, w: Int, h: Int) {
        drawPaper(canvas, vm, w, h)
        renderWorld(canvas, vm, w, h)
        val cam = vm.engine.camera
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
            for (img in level.images) {
                if (img.layerId != layer.id) continue
                drawImage(canvas, vm, img, camX, camY, zoom, w, h, layerAlpha)
            }
            for (s in level.strokes) {
                if (s.layerId != layer.id) continue
                if (cull(s, camX.toFloat(), camY.toFloat(), z, w, h)) continue
                drawStroke(canvas, s, camX, camY, zoom, w, h, paint, path, layerAlpha)
            }
            paint.xfermode = null
            canvas.restoreToCount(save)
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
                -1L, vm.activeLayerId, com.example.infinitedraw.model.BrushType.PEN,
                vm.color, vm.brushSizePx / cam.zoom.toFloat(), vm.usePressure, a.toList()
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

    fun drawStroke(canvas: Canvas, stroke: Stroke, camX: Double, camY: Double, zoom: Double,
                   w: Int, h: Int, paint: Paint, path: Path, layerAlpha: Int = 255) {
        val pts = stroke.points
        if (pts.isEmpty()) return
        val z = zoom.toFloat()
        val halfW = (w / 2f).toFloat()
        val halfH = (h / 2f).toFloat()
        paint.alpha = layerAlpha

        // screen coords
        val n = pts.size
        val sx = FloatArray(n); val sy = FloatArray(n)
        for (i in 0 until n) {
            sx[i] = clampS((pts[i].x - camX) * zoom + halfW)
            sy[i] = clampS((pts[i].y - camY) * zoom + halfH)
        }
        fun radius(i: Int): Float {
            val pressure = if (stroke.usePressure) (0.15f + 0.85f * pts[i].p) else 1f
            return when (stroke.brush) {
                com.example.infinitedraw.model.BrushType.PEN -> 0.5f * stroke.width * z * pressure
                com.example.infinitedraw.model.BrushType.MARKER -> 0.5f * stroke.width * z   // no pressure
                com.example.infinitedraw.model.BrushType.AIRBRUSH -> 1.5f * stroke.width * z * pressure
            }
        }
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        when (stroke.brush) {
            com.example.infinitedraw.model.BrushType.PEN -> paint.color = stroke.color
            com.example.infinitedraw.model.BrushType.MARKER -> {
                // ~70% alpha solid
                paint.color = stroke.color
                paint.alpha = ((layerAlpha / 255f) * 0.7f * 255f).toInt().coerceIn(0, 255)
            }
            com.example.infinitedraw.model.BrushType.AIRBRUSH -> {
                // soft dots via alpha
                paint.color = stroke.color
                paint.alpha = ((layerAlpha / 255f) * 0.4f * 255f).toInt().coerceIn(0, 255)
            }
        }
        if (n == 1) {
            path.rewind()
            path.addCircle(sx[0], sy[0], radius(0).coerceAtLeast(0.5f), Path.Direction.CW)
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = true
            canvas.drawPath(path, paint)
            paint.xfermode = null
            return
        }

        // ribbon
        val leftX = FloatArray(n); val leftY = FloatArray(n)
        val rightX = FloatArray(n); val rightY = FloatArray(n)
        for (i in 0 until n) {
            val r = radius(i)
            val px = if (i == 0) pts[0] else pts[i - 1]
            val nx = if (i == n - 1) pts[n - 1] else pts[i + 1]
            val dx = nx.x - px.x; val dy = nx.y - px.y
            val len = hypot(dx, dy)
            if (len == 0f) {
                leftX[i] = sx[i]; leftY[i] = sy[i]; rightX[i] = sx[i]; rightY[i] = sy[i]
                continue
            }
            val nX = -dy / len; val nY = dx / len
            leftX[i] = sx[i] + nX * r; leftY[i] = sy[i] + nY * r
            rightX[i] = sx[i] - nX * r; rightY[i] = sx[i] - nY * r
        }
        path.rewind()
        path.moveTo(leftX[0], leftY[0])
        for (i in 1 until n) path.lineTo(leftX[i], leftY[i])
        for (i in n - 1 downTo 0) path.lineTo(rightX[i], rightY[i])
        path.close()
        path.addCircle(sx[0], sy[0], radius(0).coerceAtLeast(0.5f), Path.Direction.CW)
        path.addCircle(sx[n - 1], sy[n - 1], radius(n - 1).coerceAtLeast(0.5f), Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        canvas.drawPath(path, paint)
        paint.xfermode = null
    }

    private fun clampS(v: Double): Float = v.coerceIn(-100000.0, 100000.0).toFloat()
}
