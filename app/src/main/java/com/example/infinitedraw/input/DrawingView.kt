package com.example.infinitedraw.input
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import com.example.infinitedraw.ui.isRaster
import com.example.infinitedraw.render.CanvasRenderer
import com.example.infinitedraw.ui.DrawingViewModel
import kotlin.math.hypot
import kotlin.math.pow

class DrawingView(ctx: Context, private val vm: DrawingViewModel) : View(ctx) {
    private val renderer = CanvasRenderer()
    private enum class Mode { NONE, DRAW, NAVIGATE }
    private var mode = Mode.NONE
    private var stylusDown = false
    private var lastCount = -1
    private var lastMx = 0f; private var lastMy = 0f; private var lastDist = 0f
    private var lastWasOneFingerPan = false   // true only when the last navigate was a 1-finger pan

    // ---- Step 19: swipe momentum (fling) ----
    private var tracker: VelocityTracker? = null
    private var flingRunnable: Runnable? = null
    private var flingVx = 0f; private var flingVy = 0f
    private var flingLastNs = 0L

    init { vm.redraw = { postInvalidate() } }
    override fun onDraw(canvas: Canvas) { renderer.render(canvas, vm, width, height) }

    private fun isStylus(e: MotionEvent, i: Int) =
        e.getToolType(i).let { it == MotionEvent.TOOL_TYPE_STYLUS || it == MotionEvent.TOOL_TYPE_ERASER }
    private fun isEraserTool(e: MotionEvent, i: Int) = e.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER
    private fun stylusIndex(e: MotionEvent): Int {
        for (i in 0 until e.pointerCount) if (isStylus(e, i)) return i
        return -1
    }
    private fun startDraw(e: MotionEvent, i: Int) {
        mode = Mode.DRAW
        if (isEraserTool(e, i)) {
            // Hardware eraser: pick the raster eraser if a raster tool is active,
            // otherwise the vector eraser (vm.eraserMode). The setters remember
            // which tool to return to when the eraser tip lifts.
            if (vm.tool.isRaster()) vm.rasterEraserMode = true else vm.eraserMode = true
        }
        vm.beginStroke(e.getX(i), e.getY(i), e.getPressure(i), width, height)
    }
    private fun startNavigate(e: MotionEvent) {
        stopFling()                               // any touch down cancels the fling at once
        mode = Mode.NAVIGATE; lastCount = -1
        tracker?.recycle()
        tracker = VelocityTracker.obtain()
        tracker?.addMovement(e)
        navigate(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopFling()
                if (isStylus(e, 0)) { stylusDown = true; startDraw(e, 0) }
                else if (vm.fingerDraws) startDraw(e, 0)
                else startNavigate(e)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val si = stylusIndex(e)
                if (si == e.actionIndex && mode != Mode.DRAW) {          // stylus arrived after a palm/finger
                    stylusDown = true; vm.cancelStroke(); startDraw(e, si)
                } else if (!stylusDown && mode == Mode.DRAW) { vm.cancelStroke(); startNavigate(e) }
                else if (!stylusDown && mode == Mode.NAVIGATE) startNavigate(e)
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.DRAW -> {
                    val i = if (stylusDown) stylusIndex(e) else 0
                    if (i >= 0) {
                        for (h in 0 until e.historySize)
                            vm.addPoint(e.getHistoricalX(i, h), e.getHistoricalY(i, h), e.getHistoricalPressure(i, h), width, height)
                        vm.addPoint(e.getX(i), e.getY(i), e.getPressure(i), width, height)
                    }
                }
                Mode.NAVIGATE -> if (!stylusDown) { tracker?.addMovement(e); navigate(e) }
                Mode.NONE -> {}
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == Mode.NAVIGATE) lastCount = -1
                else if (mode == Mode.DRAW && stylusDown && e.actionIndex == stylusIndex(e)) {
                    vm.endStroke(); stylusDown = false; mode = Mode.NONE
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mode == Mode.DRAW) vm.endStroke()
                else if (mode == Mode.NAVIGATE && lastWasOneFingerPan) startFling()
                cleanupTracker()
                stylusDown = false; mode = Mode.NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                // Some tablets send ACTION_CANCEL (palm-rejection stream flush) when the
                // stylus lifts instead of ACTION_UP. If a stroke is in progress, commit it
                // rather than discarding it — otherwise drawings vanish on lift.
                if (mode == Mode.DRAW) vm.endStroke()
                cleanupTracker()
                stylusDown = false; mode = Mode.NONE
            }
        }
        invalidate()
        return true
    }

    private fun navigate(e: MotionEvent) {
        val n = e.pointerCount
        var mx = 0f; var my = 0f
        for (i in 0 until n) { mx += e.getX(i); my += e.getY(i) }
        mx /= n; my /= n
        val dist = if (n >= 2) hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)) else 0f
        if (n == lastCount) {
            vm.engine.panBy(mx - lastMx, my - lastMy)
            if (n >= 2 && lastDist > 0f)
                vm.engine.zoomAt((dist / lastDist).toDouble().coerceIn(0.5, 2.0), mx, my, width, height)
        }
        lastWasOneFingerPan = n == 1 && lastCount == 1   // momentum only after a pure 1-finger pan
        lastCount = n; lastMx = mx; lastMy = my; lastDist = dist
    }

    // ---- Step 19: fling helpers ----

    /** Momentum only after a pure 1-finger pan above 300 px/s; pinch-zoom never flings. */
    private fun startFling() {
        val t = tracker ?: return
        t.computeCurrentVelocity(1000)
        val vx = t.xVelocity; val vy = t.yVelocity
        if (hypot(vx, vy) < 300f) return
        flingVx = vx; flingVy = vy
        flingLastNs = System.nanoTime()
        val runnable = object : Runnable {
            override fun run() {
                val now = System.nanoTime()
                val dt = ((now - flingLastNs) / 1_000_000_000.0).toFloat()
                flingLastNs = now
                vm.engine.panBy(flingVx * dt, flingVy * dt)
                val decay = 0.92.pow(dt * 60.0).toFloat()
                flingVx *= decay; flingVy *= decay
                invalidate()
                if (hypot(flingVx, flingVy) >= 30f) postOnAnimation(this)
                else stopFling()
            }
        }
        flingRunnable = runnable
        postOnAnimation(runnable)
    }

    private fun stopFling() {
        flingRunnable?.let { removeCallbacks(it) }
        flingRunnable = null
    }

    private fun cleanupTracker() {
        tracker?.recycle(); tracker = null
    }
}
