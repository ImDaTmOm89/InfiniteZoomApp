# Infinite Zoom Drawing App - Build Plan (Steps 3-18)

Steps 1 and 2 (Gradle, manifest, hello-world) are DONE and the project builds. Steps 3 onward are below.

## 0. Rules for the agent (read first, obey always)

```
SITUATION
Steps 1-2 are done and the project builds. Never redo project setup.
Memory of earlier chats may be gone. At the start of every session read, in order:
AGENTS.md, PROGRESS.md (if it exists), then this file. Run git status and git log
to see what really exists. Never assume a step is done without checking the files.

STOP RULE (most important)
Do ONE step at a time. After finishing a step, STOP and wait for the user to say "go".
Starting the next step without "go" is a failure. Never write code from a later step early.

LOCKED FILES - DO NOT EDIT, REPLACE OR REGENERATE
settings.gradle.kts, build.gradle.kts (root), gradle.properties, local.properties,
gradle/wrapper/*, gradlew, gradlew.bat, app/src/main/AndroidManifest.xml
If one of these seems to need a change, STOP and ask.

LOCKED VERSIONS
Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, compileSdk 35, minSdk 29,
buildToolsVersion 36.0.0, Java 17. Gradle stays whatever gradle-wrapper.properties says.

DEPENDENCIES
Only what is already in app/build.gradle.kts. Do NOT add Coil, accompanist, Room,
documentfile, material-icons-extended, appcompat or anything else. If a step seems to
need one, STOP and ask.

ENVIRONMENT: Windows, PowerShell 5.1
- PowerShell syntax only. No &&, ||, 2>/dev/null, head, which, ls -la.
- In each new terminal first run:
    $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
- Build: .\gradlew.bat assembleDebug     Test: .\gradlew.bat test
- Never create or use another SDK folder. Never run sdkmanager. Never install Gradle/JDK/SDK.

KOTLIN 2.0.21 GOTCHAS
- Never use `_` as a catch parameter. Write catch (e: Exception).
- Coroutines in a ViewModel need: import androidx.lifecycle.viewModelScope,
  import kotlinx.coroutines.launch, import kotlinx.coroutines.delay.

WORKFLOW
1. Steps strictly in order. A step is finished only when its "Done when" passes AND
   assembleDebug says BUILD SUCCESSFUL.
2. After each step update PROGRESS.md and reply in EXACTLY this format, then STOP:
     STEP DONE: <number and name>
     FILES CREATED: <list>
     FILES EDITED: <list>
     BUILD: <last 3 lines of assembleDebug>
     TESTS: <last 3 lines of test>
     MY TEST: <what the user should do on the tablet to check it>
     NEXT STEP (NOT STARTED): <number and name>
3. Do not edit earlier-step files unless the current step says so. Do not delete files
   you did not create.
4. If a build fails, fix only the code you just wrote. If the cause is a locked file, a
   missing dependency or a version, STOP and show the error.
5. If the plan is ambiguous, pick the simplest option, log it in DECISIONS.md, continue.

CODE RULES
- Package com.example.infinitedraw. Folders under app/src/main/java/com/example/infinitedraw/:
  model/ engine/ render/ input/ ui/ ui/panels/ storage/
- Camera and level origins are Double. Stroke points are Float.
- The drawing surface is a custom android.view.View, NOT a Compose Canvas.
- BrushType is exactly: PEN, MARKER, AIRBRUSH. The eraser is a separate Tool value, never a BrushType.
```

## 1. How infinite zoom works (memorize this)

The world is a **tree of ZoomLevels**. Each level has its own local coordinates. A child point `(u, v)` sits at `parent = origin + (u, v) / F`, with `F = 100`.

The camera is always expressed in the **current level**: `(cx, cy)` = local point at screen center, `zoom` = screen pixels per local unit. Screen to local: `lx = cx + (sx - w/2) / zoom`.

- `zoom >= 100` -> **descend** into a child (reuse one whose center is within 500 child units, else create one at the camera center). Camera becomes `cx=(cx-origin)*F`, `zoom=zoom/F`.
- `zoom < 0.5` -> **ascend** to the parent (create a new root above if none). Camera becomes `cx=origin+cx/F`, `zoom=zoom*F`.
- Different thresholds (100 down, 0.5 up) on purpose, so it never flickers.

Every number stays small, so `Float` strokes stay precise at any depth.

## Step 3 - Data models

File `model/Models.kt`. Copy exactly:

```kotlin
package com.example.infinitedraw.model
import kotlinx.serialization.Serializable

const val F = 100.0   // zoom factor between levels

@Serializable data class StrokePoint(val x: Float, val y: Float, val p: Float)
@Serializable enum class BrushType { PEN, MARKER, AIRBRUSH }
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
```

**Done when:** builds.

## Step 4 - The zoom engine (most important step)

File `engine/InfiniteZoomEngine.kt`:

```kotlin
package com.example.infinitedraw.engine
import com.example.infinitedraw.model.*
import kotlin.math.abs

class Camera(var level: ZoomLevel, var cx: Double = 0.0, var cy: Double = 0.0, var zoom: Double = 1.0)

class InfiniteZoomEngine {
    companion object { const val DESCEND_AT = 100.0; const val ASCEND_BELOW = 0.5; const val CHILD_REACH = 500.0 }
    var root = ZoomLevel(0, null, 0.0, 0.0)
    val levels = HashMap<Int, ZoomLevel>().also { it[0] = root }
    val camera = Camera(root)
    var nextLevelId = 1
    var nextStrokeId = 1L

    fun screenToLocal(sx: Float, sy: Float, w: Int, h: Int): DoubleArray = doubleArrayOf(
        camera.cx + (sx - w / 2.0) / camera.zoom, camera.cy + (sy - h / 2.0) / camera.zoom)

    fun panBy(dxPx: Float, dyPx: Float) { camera.cx -= dxPx / camera.zoom; camera.cy -= dyPx / camera.zoom }

    /** factor > 1 zooms in. Keeps the local point under (sx,sy) fixed. */
    fun zoomAt(factor: Double, sx: Float, sy: Float, w: Int, h: Int) {
        val p = screenToLocal(sx, sy, w, h)
        camera.zoom *= factor
        camera.cx = p[0] - (sx - w / 2.0) / camera.zoom
        camera.cy = p[1] - (sy - h / 2.0) / camera.zoom
        normalize()
    }

    fun normalize() {
        var guard = 0
        while (guard++ < 64) {
            if (camera.zoom >= DESCEND_AT) descend()
            else if (camera.zoom < ASCEND_BELOW) ascend()
            else break
        }
    }

    private fun descend() {
        val c = camera; val lvl = c.level
        val child = lvl.children.firstOrNull {
            abs((c.cx - it.originX) * F) < CHILD_REACH && abs((c.cy - it.originY) * F) < CHILD_REACH
        } ?: ZoomLevel(nextLevelId++, lvl, c.cx, c.cy).also { lvl.children.add(it); levels[it.id] = it }
        c.cx = (c.cx - child.originX) * F; c.cy = (c.cy - child.originY) * F
        c.zoom /= F; c.level = child
    }

    private fun ascend() {
        val c = camera; val lvl = c.level
        val p = lvl.parent ?: createRootParent()
        c.cx = lvl.originX + c.cx / F; c.cy = lvl.originY + c.cy / F
        c.zoom *= F; c.level = p
    }

    private fun createRootParent(): ZoomLevel {
        val np = ZoomLevel(nextLevelId++, null, 0.0, 0.0)
        root.parent = np; root.originX = 0.0; root.originY = 0.0
        np.children.add(root); levels[np.id] = np; root = np
        return np
    }

    /** Camera center in ROOT units (tests / persistence checks). */
    fun centerInRoot(): DoubleArray {
        var x = camera.cx; var y = camera.cy; var l = camera.level
        while (l.parent != null) { x = l.originX + x / F; y = l.originY + y / F; l = l.parent!! }
        return doubleArrayOf(x, y)
    }
    /** Pixels per ROOT unit: zoom * F^depth. */
    fun zoomInRoot(): Double { var z = camera.zoom; var l = camera.level; while (l.parent != null) { z *= F; l = l.parent!! }; return z }
}
```

JUnit test `app/src/test/java/com/example/infinitedraw/EngineTest.kt`:

- (a) At zoom 1, call `zoomAt(150.0, 500f, 500f, 1000, 1000)`. Expect `camera.level.id != 0`, `camera.zoom` between 1 and 2, `zoomInRoot()` = 150 x its old value, `centerInRoot()` unchanged (relative tolerance 1e-9).
- (b) From a fresh engine call `zoomAt(0.001, ...)`. Expect a new root above the old one (old root is a descendant of `engine.root`), `centerInRoot()` unchanged, and `camera.zoom` between 0.5 and 100.

**Done when:** `.\gradlew.bat test` passes.

## Step 5 - Drawing view, navigation only

Three files.

`ui/DrawingViewModel.kt`:

```kotlin
package com.example.infinitedraw.ui
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.infinitedraw.engine.InfiniteZoomEngine

class DrawingViewModel(app: Application) : AndroidViewModel(app) {
    val engine = InfiniteZoomEngine()
    var fingerDraws = false
    var redraw: (() -> Unit)? = null
    // stubs, filled in Step 6
    fun beginStroke(sx: Float, sy: Float, p: Float, w: Int, h: Int) {}
    fun addPoint(sx: Float, sy: Float, p: Float, w: Int, h: Int) {}
    fun endStroke() {}
    fun cancelStroke() {}
}
```

`render/CanvasRenderer.kt`: class `CanvasRenderer` with `fun render(canvas: Canvas, vm: DrawingViewModel, w: Int, h: Int)`. For now it calls `canvas.drawColor(Color.WHITE)` and draws the debug text `level=<id> zoom=<zoom>` at (20, 60) in black, textSize 40f.

`input/DrawingView.kt`. **Gesture rules:** stylus (`TOOL_TYPE_STYLUS` or `TOOL_TYPE_ERASER`) draws; fingers navigate (1 finger pans, 2 pan+pinch); with `fingerDraws` one finger draws and a second finger cancels the stroke; while a stylus is down ALL finger pointers are ignored (palm rejection). Copy:

```kotlin
package com.example.infinitedraw.input
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.example.infinitedraw.render.CanvasRenderer
import com.example.infinitedraw.ui.DrawingViewModel
import kotlin.math.hypot

class DrawingView(ctx: Context, private val vm: DrawingViewModel) : View(ctx) {
    private val renderer = CanvasRenderer()
    private enum class Mode { NONE, DRAW, NAVIGATE }
    private var mode = Mode.NONE
    private var stylusDown = false
    private var lastCount = -1
    private var lastMx = 0f; private var lastMy = 0f; private var lastDist = 0f

    init { vm.redraw = { postInvalidate() } }
    override fun onDraw(canvas: Canvas) { renderer.render(canvas, vm, width, height) }

    private fun isStylus(e: MotionEvent, i: Int) =
        e.getToolType(i).let { it == MotionEvent.TOOL_TYPE_STYLUS || it == MotionEvent.TOOL_TYPE_ERASER }
    private fun stylusIndex(e: MotionEvent): Int {
        for (i in 0 until e.pointerCount) if (isStylus(e, i)) return i
        return -1
    }
    private fun startDraw(e: MotionEvent, i: Int) {
        mode = Mode.DRAW
        vm.beginStroke(e.getX(i), e.getY(i), e.getPressure(i), width, height)
    }
    private fun startNavigate(e: MotionEvent) { mode = Mode.NAVIGATE; lastCount = -1; navigate(e) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
                Mode.NAVIGATE -> if (!stylusDown) navigate(e)
                Mode.NONE -> {}
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (mode == Mode.NAVIGATE) lastCount = -1
                else if (mode == Mode.DRAW && stylusDown && e.actionIndex == stylusIndex(e)) {
                    vm.endStroke(); stylusDown = false; mode = Mode.NONE
                }
            }
            MotionEvent.ACTION_UP -> { if (mode == Mode.DRAW) vm.endStroke(); stylusDown = false; mode = Mode.NONE }
            MotionEvent.ACTION_CANCEL -> { if (mode == Mode.DRAW) vm.cancelStroke(); stylusDown = false; mode = Mode.NONE }
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
        lastCount = n; lastMx = mx; lastMy = my; lastDist = dist
    }
}
```

In `MainActivity` replace the content with `setContent { val vm: DrawingViewModel = viewModel(); AndroidView(factory = { DrawingView(it, vm) }, modifier = Modifier.fillMaxSize()) }` (imports: `androidx.lifecycle.viewmodel.compose.viewModel`, `androidx.compose.ui.viewinterop.AndroidView`, `androidx.compose.ui.Modifier`, `androidx.compose.foundation.layout.fillMaxSize`).

**Done when:** pinching zooms and panning works; the debug text shows the level id changing when you zoom far in or out, and `zoom` always stays between 0.5 and 100.

## Step 6 - Draw strokes on the current level (pen only)

In `DrawingViewModel` add `brushSizePx = 6f`, `color: Int = android.graphics.Color.BLACK` (use the fully-qualified name), `activeLayerId = 1L`, `usePressure = true`, `active: ArrayList<StrokePoint>?`, and fill in the stubs:

- `beginStroke`: convert to local with `engine.screenToLocal`; start the list; remember `activeLevel = engine.camera.level`.
- `addPoint`: convert to local; **skip it if the distance from the last point is under `1.5 / zoom`**.
- `endStroke`: if there is at least 1 point, build `Stroke(engine.nextStrokeId++, activeLayerId, BrushType.PEN, color, brushSizePx / zoom, usePressure, points)` and add it to `activeLevel.strokes`. **Width is stored in local units**, so strokes scale with zoom while the brush you hold keeps its pixel size.
- `cancelStroke`: drop `active`.

In `CanvasRenderer` add exactly this `drawStroke`. It builds one filled **ribbon** (left edge forward, right edge in REVERSE order). The end caps are drawn with `canvas.drawCircle` and are NOT added to the ribbon path (adding them to the path is what makes strokes look like a lasso):

```kotlin
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
        ys[i] = ((pts[i].y - camY) * zoom + w / 2.0).coerceIn(-100000.0, 100000.0).toFloat()
        val pf = if (s.usePressure) 0.15 + 0.85 * pts[i].p else 1.0
        rs[i] = (0.5 * s.width * zoom * pf).toFloat()
    }
    paint.style = Paint.Style.FILL
    paint.isAntiAlias = true
    paint.color = s.color

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
```

The renderer draws `engine.camera.level.strokes`, then the in-progress stroke (as a temporary `Stroke`), using `camX=cam.cx, camY=cam.cy, zoom=cam.zoom`. Reuse one `Paint` and one `Path` as class fields; allocate nothing else per frame beyond the small arrays above.

**Done when:** you can draw (stylus, or turn finger-draw on), pan and zoom, and the drawing stays attached to the world and stays sharp. A stroke looks like a pen line, not a filled blob.

## Step 7 - Render the whole level tree (this makes it infinite)

Replace the single-level drawing with `renderWorld(canvas, vm, w, h)`:

```kotlin
private class Frame(val level: ZoomLevel, val x: Double, val y: Double, val z: Double, val exclude: ZoomLevel)

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
}

private fun drawLevelAndDown(canvas: Canvas, vm: DrawingViewModel, level: ZoomLevel, camX: Double, camY: Double,
                             zoom: Double, exclude: ZoomLevel?, w: Int, h: Int) {
    drawLevelContent(canvas, vm, level, camX, camY, zoom, w, h)   // strokes (later: layers, images)
    for (c in level.children) {
        if (c === exclude) continue
        val cz = zoom / F
        if (cz < 0.02) continue                                    // would be sub-pixel
        drawLevelAndDown(canvas, vm, c, (camX - c.originX) * F, (camY - c.originY) * F, cz, null, w, h)
    }
}
```

`drawLevelContent` is the Step 6 stroke loop (cull + `drawStroke`). The in-progress stroke is drawn last with the current camera. `render()` calls `renderWorld`.

**Done when (manual):** draw a circle, zoom in about 200x, draw a tiny dot, zoom all the way out (the dot vanishes, the circle stays), zoom back in (the dot is exactly where you left it). Zoom out past the starting level repeatedly and draw there too.

## Step 8 - Brushes (marker, airbrush, eraser)

Add `enum class Tool { PEN, MARKER, AIRBRUSH, ERASER }` and a `tool` field in the ViewModel. `endStroke` uses the matching `BrushType` (never ERASER). In `drawStroke`:

- **PEN:** as Step 6.
- **MARKER:** no pressure, paint alpha = 40% of the color's alpha. One path per stroke, so overlaps inside a stroke don't darken.
- **AIRBRUSH:** alpha 50% plus `paint.maskFilter = BlurMaskFilter(max(1f, (0.6 * width * zoom).toFloat()), BlurMaskFilter.Blur.NORMAL)` only if that radius is 300 or less; set `maskFilter = null` afterwards.
- **ERASER (stroke eraser):** creates no stroke. On every added point (local `lx,ly`, `rad = brushSizePx / zoom / 2`), for each stroke in the **current level** on an unlocked, visible layer, mark it for removal if the distance to any **segment** is `< rad + stroke.width/2`. A 1-point stroke uses the point distance. Marked strokes go into `pendingRemoved` and are skipped when drawing; `endStroke` removes them for real and pushes one undo command (Step 9).

```kotlin
fun distPointSeg(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return kotlin.math.hypot(px - (ax + t * dx), py - (ay + t * dy))
}
```

**Done when:** all four tools work; erasing at deep zoom only affects strokes of that level.

## Step 9 - Project and undo / redo

`model/Project.kt`: `class Project(var name: String, val layers: MutableList<Layer>, val paper: Paper = Paper())`. The ViewModel holds `val project = Project("Untitled", mutableListOf(Layer(1, "Layer 1")))`.

`engine/Commands.kt`:

```kotlin
package com.example.infinitedraw.engine
import com.example.infinitedraw.model.*

interface Command { fun apply(e: InfiniteZoomEngine, p: Project); fun revert(e: InfiniteZoomEngine, p: Project) }

class AddStroke(val levelId: Int, val stroke: Stroke) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.strokes.add(stroke) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.strokes.remove(stroke) }
}
class RemoveStrokes(val levelId: Int, val strokes: List<Stroke>) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.strokes.removeAll(strokes.toSet()) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { e.levels[levelId]!!.strokes.addAll(strokes) }
}
class UndoStack {
    private val undo = ArrayDeque<Command>(); private val redo = ArrayDeque<Command>()
    fun exec(c: Command, e: InfiniteZoomEngine, p: Project) { c.apply(e, p); undo.addLast(c); redo.clear() }
    fun undo(e: InfiniteZoomEngine, p: Project) { undo.removeLastOrNull()?.let { it.revert(e, p); redo.addLast(it) } }
    fun redo(e: InfiniteZoomEngine, p: Project) { redo.removeLastOrNull()?.let { it.apply(e, p); undo.addLast(it) } }
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
}
```

`endStroke()` now goes through `undoStack.exec(AddStroke(activeLevel.id, stroke), ...)`; erasing uses `RemoveStrokes`. Undo/redo never move the camera.

**Done when:** undo/redo works for strokes and erasing, including strokes made on other zoom levels.

## Step 10 - Layers (logic + rendering)

- `drawLevelContent`: `for (layer in project.layers) { if (!layer.visible) continue; draw strokes with stroke.layerId == layer.id }`.
- If `layer.opacity < 1f` or `layer.blend != NORMAL`, wrap in `canvas.saveLayer(null, layerPaint)` ... `canvas.restore()`, with `layerPaint.alpha = (opacity*255).toInt()` and `layerPaint.blendMode =` the matching `android.graphics.BlendMode` (SRC_OVER, MULTIPLY, SCREEN, OVERLAY, DARKEN, LIGHTEN). Skip `saveLayer` for plain normal/100% layers (it is slow).
- The active layer must be visible and unlocked, otherwise drawing is ignored and a `Toast` is shown.
- Add commands: `AddLayer(layer, index)`, `DeleteLayer(layer, index, removedStrokes: Map<Int, List<Stroke>>)` (collect from **all** levels), `SetLayerProps(layerId, old, new)` (copies of name/visible/locked/opacity/blend) and `MoveLayer(from, to)`. Each needs a correct `revert`.

**Done when:** two layers work; hiding one hides only its strokes in every level; 50% opacity works; delete then undo restores the strokes. Check blend modes on a real device.

## Step 11 - Compose UI shell

`ui/MainScreen.kt`: a `Box(fillMaxSize)` with the `AndroidView(DrawingView)` and overlays:

- **Top row:** Undo, Redo, Layers, Color, Brush, Menu (Save / Export / Insert image / Paper, wired in later steps). Plain text buttons, no icon library.
- **Left strip:** 4 tool buttons (selected one highlighted) and a vertical size slider (1-80 px) bound to `brushSizePx`.
- Only one panel open at a time (`var panel: PanelType?`), shown in a `Card`, 280.dp wide, on the right.
- Overlays must cover ONLY their own buttons. Never put a fillMaxSize clickable or pointerInput layer over the canvas, or touches stop reaching it.

The ViewModel exposes `mutableStateOf` for `tool`, `brushSizePx`, `color`, `activeLayerId`, `canUndo`, `canRedo`, `panel`. Call `redraw?.invoke()` after any change to layers, paper or undo state.

**Done when:** every button works and tool/size changes affect the next stroke.

## Step 12 - Color picker (no libraries)

`ui/panels/ColorPickerPanel.kt`: four `Slider`s (Hue 0-360, Saturation 0-1, Value 0-1, Alpha 0-1), a preview swatch, 8 preset swatches. Convert with `android.graphics.Color.HSVToColor((alpha*255).toInt(), floatArrayOf(h, s, v))` and write to `vm.color`. Keep the last 8 used colors (updated in `endStroke`).

**Done when:** colors change live and show up in new strokes.

## Step 13 - Brush settings and Layers panels

`BrushSettingsPanel.kt`: size slider (mirrors the strip), `Switch` "Pressure sensitivity" -> `usePressure`, `Switch` "Finger draws" -> `fingerDraws`.

`LayersPanel.kt`: `LazyColumn`, **top of list = top layer** (display the list reversed). Each row: name (tap = make active, long-press = rename dialog), eye toggle, lock toggle, opacity `Slider`, blend-mode dropdown. Footer: Add, Delete, Up, Down. Every edit goes through the Step 10 commands.

**Done when:** everything in the panels changes the canvas live and is undoable.

## Step 14 - Paper

`project.paper` is drawn first, in screen space (cosmetic, follows the current camera). `canvas.drawColor(paper.color)`. For GRID/DOTS: `sp = 20.0 * zoom` px; `while (sp > 160) sp /= 5; while (sp < 20) sp *= 5`; `x0 = w/2 - cx*zoom`; first line at `x0 - floor(x0/sp)*sp`, then every `sp`; same for y. Line/dot color is black or white (opposite of the paper) at alpha 40. Add a Paper section to the Menu (color presets and pattern dropdown).

**Done when:** the grid stays aligned while panning and rescales smoothly while zooming.

## Step 15 - Image layers

- Menu "Insert image": `ActivityResultContracts.PickVisualMedia()` (image only). Decode via `ContentResolver.openInputStream` and `BitmapFactory` (first `inJustDecodeBounds`, then `inSampleSize` so the long side is at most 2048).
- Save as PNG to `filesDir/projects/<name>/images/<id>.png` and add a `PlacedImage` to the **current level**, centered on screen, width `screenWidth*0.5/zoom` (local units), height by aspect ratio, on a new layer named "Image".
- Render in layer order inside `drawLevelContent`: compute a `RectF` with Double math (`left = (img.x - camX)*zoom + w/2` etc.), cull if off-screen, `canvas.drawBitmap(bmp, null, rect, paint)` with `isFilterBitmap = true`.
- Cache bitmaps in an `android.util.LruCache<String, Bitmap>` sized to 1/8 of `Runtime.getRuntime().maxMemory()` bytes.
- Add `AddImage` / `RemoveImage` commands. No move/resize in v1.

**Done when:** an image can be inserted, undone, hidden, zoomed into and drawn over.

## Step 16 - Persistence

`storage/ProjectStorage.kt`, with `@Serializable` DTOs:

```kotlin
@Serializable class LevelDto(val id: Int, val parentId: Int?, val originX: Double, val originY: Double,
                             val strokes: List<Stroke>, val images: List<PlacedImage>)
@Serializable class CameraDto(val levelId: Int, val cx: Double, val cy: Double, val zoom: Double)
@Serializable class ProjectDto(val version: Int = 1, val name: String, val layers: List<Layer>, val paper: Paper,
                               val levels: List<LevelDto>, val camera: CameraDto,
                               val nextLevelId: Int, val nextStrokeId: Long)
```

- **Save:** serialize on `Dispatchers.IO` to `project.json.tmp`, then rename to `project.json` (atomic). Folder: `filesDir/projects/<name>/`.
- **Load:** read the DTO with `Json { ignoreUnknownKeys = true }`; create all `ZoomLevel`s first, then link parent/children by `parentId`, fill `engine.levels`, set `engine.root` to the level whose `parentId` is null, restore the camera.
- **Autosave:** after any command or stroke end, restart a 2-second debounced `viewModelScope.launch { delay(2000); save() }`; also save in `onPause`.
- Menu: New project, Open project (dialog listing folders in `projects/`). On startup, load the last-opened project (name kept in `SharedPreferences`).

**Done when:** draw across several zoom levels with 2 layers and an image, kill the app, reopen: everything including the camera is identical.

## Step 17 - PNG export

Menu "Export PNG": `ActivityResultContracts.CreateDocument("image/png")`. Render the current view into `Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)` with `renderWorld(Canvas(bitmap), ...)` (no debug text), then `bitmap.compress(PNG, 100, stream)` on `Dispatchers.IO`.

**Done when:** the exported PNG matches the screen.

## Step 18 - Final checklist

1. Zoom in about 10^6x (3 levels) and back out: no stutter, no strokes jumping.
2. Save, force-stop, reopen: identical.
3. Palm rejection: rest a palm while using the stylus; no stray strokes, no accidental pan/zoom.
4. Pinch-spam for 20 seconds: no crash and `zoom` stays within 0.5...100.
5. Rotate the device: view re-renders, camera preserved.
6. Draw about 3,000 strokes: panning stays smooth.

**Performance note (only if #6 fails):** cache each stroke's ribbon `Path` in local coordinates and draw it with `canvas.save(); translate; scale`, only while the level's zoom is between 0.02 and 5000. Not earlier.

## Known limitations (write in DECISIONS.md)

- Strokes with screen coordinates beyond +-100000 are clamped, so huge strokes can look slightly bent at extreme close-ups.
- Layer order is guaranteed only within one zoom level; deeper levels always draw above shallower ones.
- The eraser only affects strokes on the current level.
- Images cannot be moved or resized in v1.

## Phase 2 (only after everything above is done)

Raster brushes (oil painting, color mixing, smudge) need pixel data. Add a per-level **tile-based raster layer** (256x256 `Bitmap` tiles keyed by `(level, tileX, tileY)`) that exists only where painted. Mixing and smudge read neighboring pixels while stamping dabs, and each tile saves as PNG. They only look sharp near the zoom level they were painted at, which is why the vector system is the foundation.
