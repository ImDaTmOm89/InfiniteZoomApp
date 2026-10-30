# UI Fixes and Brush System Plan (Steps F0-F9)

This file is SELF-CONTAINED. Do not read old chat history, old logs or other plan files to understand it. The code on disk and `PROGRESS.md` are the memory. If `UI_MASTER_PLAN.md` exists you may read its Part 2 (Rules), but everything you need is below.

## Your role

You are the lead Android engineer on a hand-drawn infinite-zoom drawing app (Kotlin, Jetpack Compose UI, a custom Android `View` for the canvas, vector strokes). The owner is not a programmer, works on Windows (VS Code + Android Studio) and tests every step on a **Samsung Galaxy Tab S9 FE with an S Pen**. You cannot run the app on the tablet; the owner does. Build exactly the step you are given. Be honest: never claim something works unless the build output proves it, and never say you tested on the tablet. Plain language, short messages. If something is unclear, ask ONE question.

## Rules (obey always)

```text
STOP RULE: do ONE step at a time. After a step, update PROGRESS.md, give the report, and STOP.
Do not start the next step until the owner says "go". F0 is plan-only: stop for approval.
SESSION START: read this file, PROGRESS.md, run git status and git log --oneline.
SAFETY: never delete files unless the owner names them (only build/, app/build/, .gradle/ are
ok). Git: status, diff and log are read-only and always fine; never reset, clean or checkout.
COMMIT + PUSH: after EVERY finished step, git commit that step's files and then git push —
no step is finished until the push has gone through. If the same build error happens twice,
STOP and show it.
LOCKED: settings.gradle.kts, build.gradle.kts (root), gradle.properties, local.properties,
gradle/wrapper/*, gradlew, gradlew.bat, app/src/main/AndroidManifest.xml. Versions are locked
(Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, compileSdk 35, minSdk 29, Java 17).
DEPENDENCIES: only what is in app/build.gradle.kts. No new libraries, no new permissions.
ENVIRONMENT: Windows PowerShell 5.1 only (no &&, ||, 2>/dev/null, head, which). Before building:
  $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
  $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Build: .\gradlew.bat assembleDebug    Test: .\gradlew.bat test
KOTLIN 2.0.21: never use `_` as a catch parameter (write catch (e: Exception)). ViewModel
coroutines need imports for viewModelScope, launch, delay.
UI RULES: compose overlays occupy only their own bounds (never a fillMaxSize clickable/pointerInput
over the canvas); every tappable item is at least 48 dp with a contentDescription; user-facing
words are "world", "place", "picture" (never file paths).
REPORT after each step, then STOP:
  STEP DONE: <id and name>
  FILES CREATED / FILES EDITED: <lists>
  BUILD: <last 3 lines of assembleDebug>      TESTS: <last 3 lines of test>
  MY TEST: <exactly what the owner should do on the tablet>
  NEXT STEP (NOT STARTED): <id and name>
PROGRESS.md: keep CURRENT STEP, NEXT ACTION and one line per step (F0-F9) up to date.
REMEMBER: git commit AND git push after every step (before you give the report). If your
context is getting full, stop at a safe point (build passing), update PROGRESS.md and tell the
owner to start a new session.
```

## What the owner reported (tested at the end of step U11)

1. The color picker is bars; the owner wants a **color wheel**.
2. Every brush has its own button; the owner wants **one Brush button** that opens a brush picker (more brushes will be added later).
3. The **eraser deposits color** and **acts like Undo** (removes the last thing) instead of erasing what the owner drags over.
4. There is **no stabilizer** for drawn lines.
5. **Long lines get cut off**: after a certain length the line stops drawing until the pen is lifted.
6. Add a **settings panel for every brush** (details in F8), stored per brush, with migration.
7. In the top pill the **Settings button must sit to the RIGHT of the Layers button** (currently it is on the left).

## Step F0 - Audit and plan (NO CODE CHANGES)

Read-only. Look at the real code and report in this exact structure, then STOP and wait for the owner's approval:
1. **Brush structure today:** where brushes are defined (enums `BrushType`/`Tool`, the ViewModel, `drawStroke`), where brush settings live (`BrushSettingsPanel`, the brush flyout, SharedPreferences keys), and whether any brush settings are ALREADY saved anywhere (file or prefs). Name the files and functions.
2. **Eraser path:** which code runs when the eraser tool is active, from `DrawingView.onTouchEvent` through `beginStroke/addPoint/endStroke` to the renderer. State why it deposits color and why it behaves like undo (quote the lines).
3. **Long-line bug suspects:** list every place that could limit a stroke: caps or limits on point count, fixed-size or grow-only arrays in `drawStroke`, try/catch around drawing, early `endStroke`, handling of `ACTION_CANCEL`, `ACTION_POINTER_UP`, palm rejection, `historySize` loops, and the Compose chrome auto-hide. Say which you think is the cause and why.
4. **Color picker and top pill:** which files, and the current order of the buttons in the top pill.
5. **Your plan:** the files you will create and edit for F1-F9, in order, plus any risk or question (including anything in this file that conflicts with the real code).
Then STOP. Do not change any file.

## Step F1 - Fix the stroke cut-off (long lines stop drawing)

This blocks drawing, so fix it first.
1. **Find the cause** using F0 item 3. Then add a temporary diagnostic that shows on screen when `vm.showDebug` is true (default false; the owner enables it in Settings): `pts=<points in the active stroke> ev=<last MotionEvent action name>`. This tells the owner whether input stops (pts stops growing) or only drawing stops (pts keeps growing but the line does not).
2. **Remove every cap**: no maximum point count, no fixed-size arrays that cannot grow (reuse arrays only if they grow automatically to the point count: `if (xs.size < n) xs = FloatArray(n * 2)`), no try/catch that swallows drawing errors (log with `Log.e("STROKE", ...)` and rethrow while developing), no `take(n)` or `coerceAtMost` on points.
3. **Input:** only `ACTION_UP` (of the stylus pointer) and `ACTION_CANCEL` may end a stroke. A finger or palm `ACTION_POINTER_UP`/`ACTION_POINTER_DOWN` must never end or cancel a stylus stroke. Log every `ACTION_CANCEL` with `Log.w("TOUCH", "cancel flags=${e.flags}")`.
4. Keep the active-stroke renderer and the committed-stroke renderer the SAME function (`drawStroke`).

**Done when:** the build passes and the owner can draw very long strokes (across the whole screen, long zigzags, 10 seconds without lifting) with no cut-off. If it still stops, the owner reads `pts=` and `ev=` and tells you what they show.

## Step F2 - Rewrite the eraser (never draws, erases what the pen touches)

**Behavior:**
- The eraser NEVER creates a `Stroke`, never uses the current color and never draws a filled shape. While the pen is down it shows only a thin outline circle (white 2 px ring with a black 1 px ring inside, diameter = `brushSizePx`) at the pen position, drawn on top of the canvas.
- Two modes, a two-segment toggle shown in the eraser's flyout, saved in `SharedPreferences("ui")` key `eraserMode`: **Part of stroke** (default: removes only the parts under the circle, splitting strokes) and **Whole stroke** (removes the entire stroke touched).
- Only strokes on the CURRENT zoom level, on layers that are visible and unlocked, are affected.
- One drag = ONE undo step. Undo restores exactly what that drag erased, and nothing else.

**Implementation:**

```kotlin
/** Erase a circle (cx,cy,r in level-local units). null = stroke untouched; empty list = fully erased. */
fun eraseCircle(s: Stroke, cx: Float, cy: Float, r: Float, newId: () -> Long, wholeStroke: Boolean): List<Stroke>? {
    val b = s.bounds
    if (cx + r < b[0] || cx - r > b[2] || cy + r < b[1] || cy - r > b[3]) return null
    val reach = r + s.width / 2f
    val pts = s.points
    if (wholeStroke) {
        val hit = if (pts.size == 1) hypot(pts[0].x - cx, pts[0].y - cy) < reach
                  else (0 until pts.size - 1).any { distPointSeg(cx, cy, pts[it].x, pts[it].y, pts[it + 1].x, pts[it + 1].y) < reach }
        return if (hit) emptyList() else null
    }
    val step = maxOf(r / 3f, s.width / 4f, 1e-6f)            // resample so gaps are never longer than step
    val dense = ArrayList<StrokePoint>()
    for (i in pts.indices) {
        dense.add(pts[i])
        if (i < pts.size - 1) {
            val a = pts[i]; val c = pts[i + 1]
            val k = minOf((hypot(c.x - a.x, c.y - a.y) / step).toInt(), 64)
            for (j in 1..k) { val t = j / (k + 1f); dense.add(StrokePoint(a.x + (c.x - a.x) * t, a.y + (c.y - a.y) * t, a.p + (c.p - a.p) * t)) }
        }
    }
    val runs = ArrayList<ArrayList<StrokePoint>>(); var cur: ArrayList<StrokePoint>? = null; var touched = false
    for (q in dense) {
        if (hypot(q.x - cx, q.y - cy) < reach) { touched = true; cur = null }
        else { if (cur == null) { cur = ArrayList(); runs.add(cur) }; cur.add(q) }
    }
    if (!touched) return null
    return runs.filter { it.size >= 2 }.map { s.withPoints(newId(), it) }
}
```

Add `fun withPoints(newId: Long, pts: List<StrokePoint>) = Stroke(newId, layerId, brush, color, width, usePressure, pts)` to `Stroke` (keep every other field the same; step F5 will extend it with the new fields).

**Gesture** (in the ViewModel, for `Tool.ERASER`): on begin, remember `before = ArrayList(level.strokes)`. On every added point (use the raw, unsmoothed pen position): convert to local, `r = brushSizePx / zoom / 2`, and for each stroke on an eligible layer run `eraseCircle`; apply the result DIRECTLY to `level.strokes` (replace the stroke at its index by the pieces, or remove it) so the owner sees it live. On end, if the list changed, push ONE command and discard nothing else:

```kotlin
class SetLevelStrokes(val levelId: Int, val before: List<Stroke>, val after: List<Stroke>) : Command {
    override fun apply(e: InfiniteZoomEngine, p: Project) { val l = e.levels[levelId]!!.strokes; l.clear(); l.addAll(after) }
    override fun revert(e: InfiniteZoomEngine, p: Project) { val l = e.levels[levelId]!!.strokes; l.clear(); l.addAll(before) }
}
```

Add `UndoStack.pushApplied(cmd)` that records the command without calling `apply` again (the change is already live). On `cancelStroke`, restore `before`. Remove any old eraser code that created strokes, set paint colors, or called undo. `pendingRemoved` logic from the old eraser must be deleted.

**Done when:** the eraser shows only an outline ring, removes exactly the parts the owner drags over (and whole strokes in Whole mode), never leaves color, and one Undo restores everything from that single drag.

## Step F3 - Top pill order

In the top pill the order must be: **Undo, Redo, Layers, Settings, Menu** (Settings immediately to the RIGHT of Layers). Change nothing else. In the report, state the final order of the buttons.

**Done when:** Settings is right of Layers.

## Step F4 - Color wheel

Rebuild the Color panel around a **color wheel** (hue around the circle, saturation from the center out) plus a Brightness bar and an Opacity bar. Remove the Hue and Saturation bars. Keep the preview swatch with the hex value, the Recent row (last 8) and the Basics row, and the close button. No new dependencies.

```kotlin
@Composable
fun ColorWheel(hue: Float, sat: Float, value: Float, onPick: (hue: Float, sat: Float) -> Unit, modifier: Modifier = Modifier) {
    val pick = rememberUpdatedState(onPick)
    Canvas(
        modifier.size(240.dp)
            .pointerInput(Unit) { detectTapGestures { o -> pickAt(o, size, pick.value) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { o -> pickAt(o, size, pick.value) }) { c, _ -> c.consume(); pickAt(c.position, size, pick.value) } }
    ) {
        val c = center; val r = size.minDimension / 2f
        val stops = (0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }
        drawCircle(brush = Brush.sweepGradient(stops, c), radius = r, center = c)                       // hue, 0 degrees at 3 o'clock, clockwise
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), c, r), radius = r, center = c)  // saturation
        drawCircle(color = Color.Black.copy(alpha = 1f - value), radius = r, center = c)               // brightness
        val a = Math.toRadians(hue.toDouble()); val d = sat * r
        val k = Offset(c.x + (cos(a) * d).toFloat(), c.y + (sin(a) * d).toFloat())
        drawCircle(Color.White, 11.dp.toPx(), k, style = Stroke(3.dp.toPx())); drawCircle(Color.Black, 12.5.dp.toPx(), k, style = Stroke(1.dp.toPx()))
    }
}
private fun pickAt(o: Offset, size: IntSize, f: (Float, Float) -> Unit) {
    val dx = o.x - size.width / 2f; val dy = o.y - size.height / 2f; val r = size.width / 2f
    val hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
    f(hue, (hypot(dx, dy) / r).coerceIn(0f, 1f))
}
```

State: keep `hue (0..360)`, `sat (0..1)`, `value (0..1)`, `alpha (0..1)` in the panel; whenever any changes set `vm.color = android.graphics.Color.HSVToColor((alpha * 255).toInt(), floatArrayOf(hue, sat, value))`. When the panel opens, initialise the four values from the current `vm.color` using `Color.colorToHSV`. The Brightness bar goes from black to the current hue/sat color at full brightness; the Opacity bar from transparent to the current color (reuse `GradientSlider`). Wheel size 240 dp; the panel stays 300 dp wide.

**Done when:** dragging on the wheel changes hue and saturation live with a visible knob, brightness and opacity bars work, recents and basics still work, and the color shown in the preview is the color used for new strokes.

## Step F5 - Brush model, registry, storage and migration (no UI yet)

**Per-brush settings** (`model/BrushSettings.kt`):

```kotlin
@Serializable data class CurvePoint(val x: Float, val y: Float)
@Serializable data class SizeRange(val min: Float = 0.15f, val max: Float = 1f)   // fractions of the chosen brush size
@Serializable data class BrushSettings(
    val id: String,
    val opacity: Float = 1f,                                   // 0..1
    val sizeRange: SizeRange = SizeRange(),                    // size at zero pressure .. full pressure
    val curvePoints: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f)),   // pressure curve, 2..5 points, x increasing, first x=0, last x=1
    val taper: Float = 0f,                                     // 0..100
    val calligraphy: Float = 0f,                               // 0..100
    val stabilizer: Float = 20f,                               // 0..100
) { fun mapPressure(p: Float): Float = evalCurve(curvePoints, p.coerceIn(0f, 1f)) }

fun evalCurve(pts: List<CurvePoint>, x: Float): Float {
    if (x <= pts.first().x) return pts.first().y
    for (i in 1 until pts.size) {
        val a = pts[i - 1]; val b = pts[i]
        if (x <= b.x) { val t = if (b.x == a.x) 0f else (x - a.x) / (b.x - a.x); return a.y + (b.y - a.y) * t }
    }
    return pts.last().y
}
```

**Registry** (`model/BrushRegistry.kt`): `class BrushDef(val id: String, val name: String, val icon: UiIcon, val type: BrushType, val usesPressure: Boolean, val defaults: BrushSettings)` and `object BrushRegistry { val all: List<BrushDef>; fun get(id: String): BrushDef }`. One entry per brush that exists in `BrushType`. Defaults (a later brush is added by adding ONE entry here plus its render branch):
- `pen`: opacity 1, sizeRange 0.15-1, linear curve, stabilizer 20, uses pressure.
- `marker`: opacity 0.4, sizeRange 1-1, no pressure, stabilizer 20.
- `airbrush`: opacity 0.5, sizeRange 1-1, no pressure, stabilizer 0.
- `pencil` and `charcoal` (only if they exist): opacity 1, sizeRange 0.3-1 and 0.4-1, uses pressure, stabilizer 10.

**Storage and migration** (`storage/BrushStore.kt`): global file `filesDir/brushes.json` (not per project):

```kotlin
@Serializable class BrushFile(val version: Int = 1, val brushes: List<BrushSettings> = emptyList())
```
- `Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }`.
- `load()`: if the file is missing, use all defaults. If parsing throws, rename the file to `brushes.json.bad` and use defaults. Otherwise run `migrate`.
- `migrate(file)`: for every `BrushDef` in the registry take the saved entry if any (else its defaults), then **sanitize**: opacity 0..1; `sizeRange` min and max 0.01..1 with min <= max; taper, calligraphy, stabilizer 0..100; the curve must have 2..5 points, strictly increasing x, first x = 0, last x = 1 and y in 0..1, otherwise use the linear default. Saved ids that are not in the registry are dropped. Missing new brushes get their defaults, so adding a brush later never breaks old files. Include a `when (file.version)` hook for future upgrades.
- If F0 found brush settings already stored somewhere (prefs or file), read those values once as the starting point and delete nothing.
- `save(settings)` writes atomically (`brushes.json.tmp`, then rename). `reset(id)` restores `BrushRegistry.get(id).defaults`.
- The ViewModel loads the store at start and exposes `brushSettings(id)`.

**Stroke migration:** extend `Stroke` with defaults so OLD save files still load unchanged:
`val opacity: Float = -1f, val sizeMin: Float = -1f, val sizeMax: Float = 1f, val taper: Float = 0f, val calligraphy: Float = 0f`. `-1` means "legacy stroke": the renderer then uses opacity 0.4 for MARKER, 0.5 for AIRBRUSH, 1 otherwise, and `sizeMin` = 0.15 if `usePressure` else 1. Update `withPoints` (F2) to copy the new fields. Add unit tests: an old-format JSON stroke (without the new fields) still decodes; a corrupt `brushes.json` falls back to defaults; `sanitize` repairs a bad curve.

**Done when:** build and tests pass, an old project still opens, and no UI changed yet.

## Step F6 - One Brush button and the brush picker

- In the tool panel, REMOVE the individual brush buttons (Pen, Marker, Airbrush, Pencil, Charcoal). The panel now shows: one **Brush** button (its icon is the current brush's icon; highlighted when a brush is active), **Eraser**, **Select** (if it exists), a divider, the color button, the size dot and the collapse button.
- Tapping the Brush button toggles a **brush picker**: a `PanelSurface` next to the panel (same placement rule as the size flyout) with a grid of 3 columns of 72 dp tiles (icon + name). The selected tile is highlighted. Tapping a tile selects that brush and closes the picker. Tapping the already-selected tile, or a small "Brush settings" row at the bottom of the picker, opens the settings panel of that brush (F8; until F8 exists, do nothing).
- Implementation with minimal changes: keep the `Tool` enum; add `vm.currentBrushId` (saved in `SharedPreferences("ui")`) and `vm.selectBrush(id)`, which sets `vm.tool` to the matching tool. The picker lists `BrushRegistry.all`, so a future brush appears automatically.
- The picker and the settings panel hide with the other chrome while drawing, and never block canvas touches outside their bounds.

**Done when:** one Brush button replaces the brush buttons, the picker lists every brush, picking one changes what is drawn, and the choice survives an app restart.

## Step F7 - Apply the brush properties (renderer and input)

**Input (ViewModel `beginStroke/addPoint/endStroke`):** use `settings = brushSettings(currentBrushId)`.
- **Stabilizer:** `a = 1f - 0.8f * settings.stabilizer / 100f`. Smooth in SCREEN coordinates before converting to local: keep `smX, smY, smP`; first point unsmoothed; each later raw point: `smX += a * (rawX - smX)` (same for `y` and pressure); then convert and apply the existing distance filter. On `endStroke`, if the last raw point is more than 1 px from the last smoothed point, append it so the line ends where the pen lifted. The eraser and select tools never smooth. If an older global stabilizer or pressure-gamma setting exists, remove it from the UI and use these per-brush values.
- **Pressure curve:** store `p = settings.mapPressure(smP)` in each `StrokePoint` (so what is drawn live equals what is saved).
- Create the stroke with `opacity = settings.opacity`, `sizeMin = settings.sizeRange.min`, `sizeMax = settings.sizeRange.max`, `taper = settings.taper`, `calligraphy = settings.calligraphy`. The live (active) stroke is built the same way, so the preview while drawing equals the final result. Brushes with `usesPressure = false` set `usePressure = false`.

**Renderer (`drawStroke`):** compute the radius of every point (keep one ribbon path; arrays grow automatically):

```kotlin
val legacyMin = if (s.sizeMin < 0f) (if (s.usePressure) 0.15f else 1f) else s.sizeMin
val sizeF = if (s.usePressure) legacyMin + (s.sizeMax - legacyMin) * p_i else 1f       // size range driven by the (already curved) pressure
// calligraphy: flat angled nib, direction measured in LEVEL-LOCAL coordinates (zoom independent)
val cal = s.calligraphy / 100f
val theta = atan2(pts[c].y - pts[a].y, pts[c].x - pts[a].x)       // a, c = neighbour indices used for the normal
val calF = (1f - cal) + cal * maxOf(0.12f, abs(sin(theta - (PI / 4).toFloat())))   // nib angle 45 degrees; 0 = round nib, 100 = flat nib
// taper: arc length in local units, cumLen[i] precomputed once per stroke (lazy), total = cumLen.last
val tl = (s.taper / 100f) * minOf(0.5f * total, 8f * s.width)
val tF = if (tl <= 0f) 1f else maxOf(0.05f, minOf(smooth(cumLen[i] / tl), smooth((total - cumLen[i]) / tl)))   // smooth(t) = t'*t'*(3-2t') with t' = t clamped to 0..1
r_i = 0.5f * s.width * zoom * sizeF * calF * tF
```
`s.width` is the base brush size in local units (chosen size / zoom). Add `val cumLen: FloatArray by lazy { ... }` to `Stroke` (cumulative distance along the points). Opacity: `paint.alpha = (Color.alpha(s.color) * effOpacity).toInt()` with `effOpacity = if (s.opacity < 0f) legacy value else s.opacity`; REMOVE the old hard-coded 40% (marker) and 50% (airbrush) multipliers from the renderer, because those are now the brushes' default opacity. End caps use the first and last radii.

**Done when:** build passes; strokes smooth with the stabilizer, vary by pressure with the curve and size range, taper at both ends, look like a flat nib with calligraphy 100, respect opacity, and old strokes look exactly as before.

## Step F8 - Shared brush settings panel (one component for every brush)

Create ONE reusable composable `ui/panels/BrushSettingsPanel.kt`:

```kotlin
@Composable
fun BrushSettingsPanel(def: BrushDef, initial: BrushSettings, previewColor: Int,
                       onCancel: () -> Unit, onConfirm: (BrushSettings) -> Unit)
```
It matches the existing brush settings screen (look at the current `BrushSettingsPanel`/flyout and reuse its visuals): a dark `PanelSurface`, 360 dp wide, max height 80% of the screen with the content scrollable. It edits a DRAFT copy (`var draft by remember { mutableStateOf(initial) }`); nothing is saved until the checkmark.

Top bar: **X** (CLOSE icon) at top-left = cancel and discard, the brush name centered, **checkmark** (add a CHECK icon to `UiIcon`: polyline (5,12.5)-(10,17.5)-(19,7), width 2.6) at top-right = confirm. Then, top to bottom:
1. **Live stroke preview:** a 120 dp high rounded box (checkerboard or paper-colored background) drawing a sample S-curve stroke with the SAME `drawStroke` function (`Canvas { drawIntoCanvas { it.nativeCanvas } }`, zoom 1, camera at the box center): 60 points along `x = t * w`, `y = h/2 + sin(2 * PI * t) * h * 0.28`, pressure `p = mapPressure(sin(PI * t))` (rises and falls so the curve, size range and taper are visible), base width 22 px, color `previewColor`, built from the draft settings. It updates instantly when any control changes.
2. **Section title** ("PROPERTIES", via `SectionTitle`).
3. **Opacity:** slider 0-100, the current value (for example "85%") on the right.
4. **Size range:** a `RangeSlider` 1-100 (percent of the brush size, maps to `sizeRange` min and max), value "15% - 100%" on the right. Use `@OptIn(ExperimentalMaterial3Api::class)` if needed.
5. **RESET:** a text button in `danger` color that sets the draft to `BrushRegistry.get(def.id).defaults` (it does not save).
6. **Pressure curve:** a 160 dp square graph (x = pressure, y = size) drawing the curve from `curvePoints`; drag a point to move it (the first and last points keep x = 0 and x = 1 and only move vertically; interior points stay between their neighbours; y stays 0..1); tap an empty spot to add a point (at most 5); long-press an interior point to delete it (at least 2 remain). Three preset chips under it: **Linear** (0,0)(1,1), **Soft** (0,0)(0.25,0.45)(0.5,0.72)(0.75,0.9)(1,1), **Firm** (0,0)(0.25,0.1)(0.5,0.3)(0.75,0.6)(1,1). (The owner's original list skipped item 6; this is the assumed content, matching `curvePoints`.)
7. **Taper:** slider 0-100 (default 0), value on the right.
8. **Calligraphy:** slider 0-100, value on the right. 100 = flat angled nib (width changes with stroke direction), 0 = round nib.
9. **Stabilizer:** slider 0-100, value on the right.
For brushes with `usesPressure = false` show items 4 and 6 greyed out (`alpha = 0.38f`, not interactive) with the note "This brush ignores pressure".

**Wiring:** open it from the brush picker (F6) for the chosen brush. **Checkmark** -> `BrushStore.save(draft)`, update the ViewModel's live settings (the next stroke uses them) and close. **X** -> close without saving. Android Back = X. Only one copy of the panel code exists; it takes the brush as a parameter. Add icon CHECK to the icon enum.

**Done when:** every brush opens the same panel; the preview reacts live to every control; RESET restores defaults; X discards; the checkmark persists the values across an app restart; the next stroke uses the saved values.

## Step F9 - Final checks and cleanup

1. Run the checks below and fix any failure (report each result): long strokes never cut off; the eraser never draws color and one drag = one undo; both eraser modes work; the stabilizer visibly smooths at 100 and does nothing at 0; every brush's panel opens, saves and resets; old projects open and look unchanged; the color wheel sets the stroke color; Settings sits right of Layers; one Brush button.
2. Make sure no temporary logging or debug text remains unless it is behind `vm.showDebug`.
3. Update `PROGRESS.md` and add a short section to `DECISIONS.md` about: the stroke sentinel values (`-1` = legacy), `brushes.json`, the eraser modes, and the assumed item 6 (pressure curve).

**Done when:** the owner has tested everything above on the tablet and confirms.
