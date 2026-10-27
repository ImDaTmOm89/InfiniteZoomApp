package com.example.infinitedraw.input
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import com.example.infinitedraw.render.CanvasRenderer
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.Tool
import com.example.infinitedraw.ui.isRaster
import kotlin.math.hypot
import kotlin.math.pow

class DrawingView(ctx: Context, private val vm: DrawingViewModel) : View(ctx) {
    private val renderer = CanvasRenderer()
    private enum class Mode { NONE, DRAW, NAVIGATE }
    private var mode = Mode.NONE
    private var stylusDown = false
    private var lastCount = -1
    private var lastMx = 0f; private var lastMy = 0f; private var lastDist = 0f
    private var lastAngle = 0.0                  // Step 24: two-finger twist angle
    private var lastWasOneFingerPan = false   // true only when the last navigate was a 1-finger pan
    private var navDirty = false              // testing fix: camera moved, needs an autosave on gesture end

    // ---- Step 19: swipe momentum (fling) ----
    private var tracker: VelocityTracker? = null
    private var flingRunnable: Runnable? = null
    private var flingVx = 0f; private var flingVy = 0f
    private var flingLastNs = 0L

    init { vm.redraw = { postInvalidate() } }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        vm.viewW = w; vm.viewH = h   // testing fix: reliable size for export/import placement
    }
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
        // U10: the S Pen side button held, or the pen flipped to its eraser end —
        // erase for THIS stroke only (the raster eraser when a raster tool is
        // active). vm.tool keeps showing the real selected tool.
        if (isEraserTool(e, i) || (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)
            vm.overrideTool = if (vm.tool.isRaster()) Tool.RERASER else Tool.ERASER
        vm.beginStroke(e.getX(i), e.getY(i), e.getPressure(i), width, height)
    }
    private fun startNavigate(e: MotionEvent) {
        stopFling()                               // any touch down cancels the fling at once
        mode = Mode.NAVIGATE; lastCount = -1; lastAngle = 0.0
        tracker?.recycle()
        tracker = VelocityTracker.obtain()
        tracker?.addMovement(e)
        navigate(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopFling()
                vm.dismissHint()   // U6: any tap dismisses the first-run hint
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
                saveCameraIfDirty()
                cleanupTracker()
                stylusDown = false; mode = Mode.NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                // Some tablets send ACTION_CANCEL (palm-rejection stream flush) when the
                // stylus lifts instead of ACTION_UP. If a stroke is in progress, commit it
                // rather than discarding it — otherwise drawings vanish on lift.
                if (mode == Mode.DRAW) vm.endStroke()
                saveCameraIfDirty()
                cleanupTracker()
                stylusDown = false; mode = Mode.NONE
            }
        }
        invalidate()
        return true
    }

    /** U10 (optional): hovering the pen shows a thin circle the size of the brush. */
    override fun onHoverEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER ->
                vm.hover = e.x to e.y
            else ->   // HOVER_EXIT (pen touched or left range) and anything else
                vm.hover = null
        }
        vm.redraw?.invoke()
        return true
    }

    private fun navigate(e: MotionEvent) {
        val n = e.pointerCount
        var mx = 0f; var my = 0f
        for (i in 0 until n) { mx += e.getX(i); my += e.getY(i) }
        mx /= n; my /= n
        val dist = if (n >= 2) hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)) else 0f
        // Step 24: two-finger twist — the angle between the fingers rotates the world.
        val angle = if (n >= 2)
            kotlin.math.atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble()) else 0.0
        if (n == lastCount) {
            vm.engine.panBy(mx - lastMx, my - lastMy)
            navDirty = true
            if (n >= 2 && lastDist > 0f) {
                val factor = (dist / lastDist).toDouble().coerceIn(0.5, 2.0)
                var dth = angle - lastAngle
                while (dth > Math.PI) dth -= 2 * Math.PI
                while (dth < -Math.PI) dth += 2 * Math.PI
                if (factor != 1.0 || dth != 0.0)
                    vm.engine.transformAt(factor, dth, mx, my, width, height)
            }
        }
        lastWasOneFingerPan = n == 1 && lastCount == 1   // momentum only after a pure 1-finger pan
        lastCount = n; lastMx = mx; lastMy = my; lastDist = dist; lastAngle = angle
        vm.tickCamera()   // U6: keep the ZoomChip in sync while panning/zooming
    }

    /** Testing fix: persist camera moves (plan Step 18: force-stop + reopen must be identical). */
    private fun saveCameraIfDirty() {
        if (navDirty) { navDirty = false; vm.autosave.schedule() }
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
                navDirty = true
                vm.tickCamera()   // U6: the ZoomChip follows the fling too
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
        saveCameraIfDirty()   // testing fix: fling moved the camera; persist it
    }

    private fun cleanupTracker() {
        tracker?.recycle(); tracker = null
    }
}
