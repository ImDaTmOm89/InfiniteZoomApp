# INFINITE ZOOM DRAWING APP - UI / UX MASTER PLAN (role + rules + all UI steps in one file)

This single file contains the agent's ROLE, ALL RULES, the BUILD ORDER and every UI/UX step (U1-U18). It covers only the user interface. The core app (zoom engine, drawing, layers, saving and so on) is already built from a separate plan; do not rebuild it.

## HOW TO READ THIS FILE

Every session read ONLY: Part 1 (Role), Part 2 (Rules), Part 3 (Build order), `PROGRESS.md`, and the section of the ONE step you are working on (search for its heading, for example `## Step U4 `). Do not read the whole file each session; that wastes your context.

---

# PART 1 - YOUR ROLE

You are the **lead Android UI engineer** on this project, pair-programming with its owner.

**The owner:** not a programmer. Works on a Windows PC with VS Code (where you run) and Android Studio (used only to run the app). Tests every step on a **Samsung Galaxy Tab S9 FE with an S Pen**. You cannot run the app on the tablet; the owner does.

**The product:** a hand-drawn infinite zoom drawing app. Think **Google Earth for hand-drawn worlds** with **Procreate's drawing feel**: the user draws something, zooms into it forever, and draws a new world inside it. The canvas must always feel like a place, not a document. Stack: Kotlin, Jetpack Compose for the interface, a custom Android `View` for the canvas.

**Your job here:** build the interface around the canvas: dark minimal theme, a floating movable tool panel, panels, navigation and exploration features. You build exactly the step you are given, nothing more. You do not redesign, add features, or refactor unrelated code. You do not change the drawing engine unless a step says so.

**How you work:**
- Prefer simple, boring, readable code over clever code. Small functions, clear names.
- Verify your work by building. Never claim something works unless the build or test output proves it.
- Be honest. If a build fails, say so and show the real error. If you could not run or test something, say so. Never invent output and never say you tested on the tablet.
- If something is unclear or the plan seems wrong, ask ONE clear question instead of guessing.

**How you talk:** plain language, short, no jargon dumps. Say what you did, what happened, and what the owner should do next. Always finish with the report format in Part 2.

---

# PART 2 - RULES (obey always)

```text
SESSION START (every session, even if you think you remember)
Memory of earlier chats may be gone. Read Parts 1-3 of this file, then PROGRESS.md, then run
git status and git log --oneline, and check the real files. Never assume a step is done.
If the owner asks for an audit, do ONLY the audit and stop.

STOP RULE (most important)
Do ONE step at a time. After finishing a step, update PROGRESS.md, give the report, and STOP.
Do not start the next step until the owner says "go". Never write code from a later step early.
If you notice you started a step without "go", stop and tell the owner what you changed.

CONTEXT HANDOFF
Your context can run out at any time. Keep PROGRESS.md current: if work is mid-step, update
CURRENT STEP and NEXT ACTION so a fresh session can continue exactly where you stopped. If your
context is getting full, stop at a safe point (build passing), update PROGRESS.md, and tell the
owner to start a new session.

SAFETY
- Never delete files or folders unless the owner names them. The only things you may delete on
  your own are the generated folders build/, app/build/ and .gradle/.
- If asked to clean up, LIST candidates and reasons first and wait for approval.
- Git: only read-only commands (status, diff, log). Never run commit, reset, clean, checkout,
  stash, rebase or push unless the owner explicitly asks.
- If the same build error happens twice after your fixes, STOP and show the owner the error.

LOCKED FILES - DO NOT EDIT, REPLACE OR REGENERATE
settings.gradle.kts, build.gradle.kts (root), gradle.properties, local.properties,
gradle/wrapper/*, gradlew, gradlew.bat, app/src/main/AndroidManifest.xml
If one of these seems to need a change, STOP and ask.

LOCKED VERSIONS
Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01, compileSdk 35, minSdk 29,
buildToolsVersion 36.0.0, Java 17. Gradle stays whatever gradle-wrapper.properties says.

DEPENDENCIES
Only what is already in app/build.gradle.kts. Do NOT add icon libraries, Coil, accompanist,
Room, Hilt, appcompat or anything else. If a step seems to need one, STOP and ask.
Do not add any permission.

ENVIRONMENT: Windows, PowerShell 5.1
- PowerShell syntax only. No &&, ||, 2>/dev/null, head, which, ls -la.
- If your shell turns out to be bash or WSL, STOP and tell the owner before running anything.
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
1. Steps strictly in the order U1, U2, ... U18. A step is finished only when its "Done when" is
   met AND assembleDebug says BUILD SUCCESSFUL (and test passes where the step says so).
2. After each step update PROGRESS.md and reply in EXACTLY this format, then STOP:
     STEP DONE: <id and name>
     FILES CREATED: <list>
     FILES EDITED: <list>
     BUILD: <last 3 lines of assembleDebug>
     TESTS: <last 3 lines of test>
     MY TEST: <exactly what the owner should do on the tablet to check it>
     NEXT STEP (NOT STARTED): <id and name>
3. Do not edit files outside the step's scope. Do not delete files you did not create.
4. If a build fails, fix only the code you just wrote. If the cause is a locked file, a missing
   dependency or a version, STOP and show the error.
5. If the plan is ambiguous, pick the simplest option, log it in DECISIONS.md, continue.

CODE RULES
- Package com.example.infinitedraw. UI code goes under ui/, ui/components/, ui/panels/,
  ui/theme/. Engine changes only where a step says so.
- Camera and level origins are Double. Stroke points are Float.
- The drawing surface stays a custom android.view.View, NOT a Compose Canvas.
- Do not change BrushType, Tool, the stroke data model or the renderer, except where a step
  explicitly says so.
- There are NO portals and NO separate worlds inside a project. Navigation is built on the
  tree of zoom levels only.

UI RULES
- Compose overlays must only occupy their own bounds. Never put a fillMaxSize container with
  pointer input or clickable over the canvas, or touches stop reaching it.
- Every tappable item is at least 48 dp and has a contentDescription.
- In text shown to the user say "world" (not project/file/document), "place" (a named zoom
  level) and "picture" (a PNG export). Never show file paths.

PROGRESS.md FORMAT (keep exactly this shape; keep the lines for the core steps already there)
  # Progress
  CURRENT STEP: <id and name> - <NOT STARTED | IN PROGRESS | WAITING FOR OWNER TEST | DONE>
  NEXT ACTION: <one exact sentence>
  ## Steps   (one line per step: id, name, DONE / IN PROGRESS / NOT STARTED, owner-tested yes/no)
  ## Known issues
```

---

# PART 3 - BUILD ORDER AND PREREQUISITES

**Prerequisite:** the core app is built and verified (zoom engine, drawing, undo, layers, the basic Compose shell with color/brush/layers panels, paper, images, persistence, PNG export). In this file, "Step N" with a plain number always means a step of that earlier core plan; check `PROGRESS.md` to see which ones are done. Features from the later numbered steps (momentum, smoothing, select, pencil/charcoal, world files, rotation) may or may not exist yet; where a UI step depends on one that is missing, skip that part and note it in `PROGRESS.md`.

| Phase | Steps | What it builds |
|---|---|---|
| A. UI foundation | U1, U2, U3, U4, U5, U6, U7, U8, U9, U10 | Dark theme, icons, floating movable tool panel, top pill, auto-hiding controls, color and layer panels, settings, S Pen extras |
| B. Exploration | U11, U12, U13, U14, U15, U16, U17, U18 | Places, fly-to, search, minimap, compass, Procreate gestures, zoom ergonomics, gallery |

Do U1-U10 first, then U11-U18, in order.

If a name used in a step (for example `vm.tool`, `vm.brushSizePx`, `vm.color`, `vm.panel`) differs from the real code, use the real name. Never rename existing code just to match this file.

---

# PART 4 - UI / UX STEPS

## Design brief

- **Canvas first:** the canvas fills 100% of the screen. All controls float above it, are small, and fade away while the pen is drawing. Nothing ever resizes or pushes the canvas.
- **Dark and minimal:** dark translucent surfaces, one soft accent color, no clutter, no text where an icon is clear.
- **The tools live in one floating panel the user can drag anywhere.** It remembers its position, snaps to the nearest left or right edge, and can collapse to a tiny handle.
- **Touch friendly on a Galaxy Tab S9 FE:** every tappable item is at least 48 dp, every icon has a `contentDescription`, text is at least 12 sp.

## Design inspiration (the feeling to build)

**Google Earth for hand-drawn worlds.** The user must feel like they are traveling through an infinitely deep universe they drew themselves. The experience is **Google Earth's sense of scale and exploration + Procreate's drawing experience**. The canvas must always feel like a **place**, not a document.

| Borrow from | What to take | Where it is built |
|---|---|---|
| Google Earth | Flying between places, a sense of altitude/scale, place names appearing as you arrive, a Home button | U6 (depth chip), U11, U12, U15 |
| Procreate | Gallery of worlds as thumbnail cards, edge sliders for size and opacity, finger-tap gestures (2 fingers = undo, 3 = redo, 4 = hide controls), eyedropper, calm floating controls | U4, U16, U18 |
| Google Maps | Minimap, search, history (back/forward), orientation (compass), breadcrumb of where you are | U11-U15 |
| Infinite Painter | Android drawing workflow, brush interaction, layer controls | U4, U8 |
| Figma | Infinite-canvas feel, zoom ergonomics (double-tap zoom, fit view), minimal floating controls | U17, U4 |

**Do NOT copy:** Photoshop, Illustrator, Microsoft Office, Notion, desktop productivity software or file-explorer interfaces. In practice:
- No menu bar, no title bar, no tabs, no toolbars docked to the screen edges, no file paths or file lists shown to the user.
- No "Save" button (the app autosaves). No "Open file" dialog (use the Gallery, U18).
- Words: say **world** instead of project/file/document, **place** for a named zoom level, **picture** for a PNG export. Menu labels: "My worlds", "New world", "Export picture", "Export world file", "Import world file".
- Motion is always smooth: fade, glide and fly. Never a hard cut between two views if it can be avoided.

### Design tokens (use these exact values; put them in `ui/theme/`)

| Token | Value |
|---|---|
| surface (panels) | `Color(0xE61A1B1F)` (90% opaque near-black) |
| surfaceRaised (buttons, tracks) | `Color(0xFF26272C)` |
| outline | `Color(0x1FFFFFFF)` |
| textPrimary | `Color(0xFFECECF1)` |
| textSecondary | `Color(0xFF9A9BA6)` |
| accent | `Color(0xFF8AB4FF)` |
| accentSoft | accent at 18% alpha |
| danger | `Color(0xFFFF7A7A)` |
| panel corner radius | 20 dp |
| button corner radius | 14 dp |
| spacing unit | 4 dp (use multiples: 4, 8, 12, 16) |
| panel shadow | 12 dp elevation |
| fade duration | 150 ms |
| collapse / expand | 200 ms |
| snap animation | `spring(dampingRatio = 0.8f, stiffness = 400f)` |
| minimum touch target | 48 dp |

## Step U1 - Theme and full-screen immersive mode

- Create `ui/theme/Color.kt` (the tokens above as `val`s) and `ui/theme/Theme.kt` with `@Composable fun InfiniteDrawTheme(content: @Composable () -> Unit)`. Use `MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = Color.Black, surface = surfaceRaised, onSurface = textPrimary, onBackground = textPrimary, error = danger), content = content)`. Wrap everything inside `setContent { ... }` with it.
- Go edge-to-edge and hide the system bars: in `MainActivity.onCreate` call `WindowCompat.setDecorFitsSystemWindows(window, false)` and `WindowInsetsControllerCompat(window, window.decorView).apply { systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; hide(WindowInsetsCompat.Type.systemBars()) }`. (If `androidx.core` classes do not resolve, STOP and ask; do not add a dependency.)
- Remove the debug text (`level=... zoom=...`) from the canvas renderer, but keep the code behind a `vm.showDebug` flag (default false) because it is useful later.

**Done when:** the app is full screen with no system bars (swipe from an edge shows them briefly) and no debug text on the canvas. Build passes.

## Step U2 - Icon set (no icon library)

Create `ui/components/UiIcon.kt` with `enum class UiIcon` and `@Composable fun UiIcon(icon: UiIcon, tint: Color, size: Dp = 24.dp, contentDescription: String?)`. It draws on a `Canvas` with a 24-unit grid: set `val k = this.size.width / 24f` and multiply every coordinate by `k`. Use `Stroke(width = w * k, cap = StrokeCap.Round, join = StrokeJoin.Round)` for lines. Icons (coordinates are x,y in the 24 grid):

- **PEN:** line (6,18)-(17,7) width 3; filled circle at (5,19) radius 1.6.
- **MARKER:** line (8,16)-(17,7) width 6, butt cap; line (5,19)-(8,16) width 2.5.
- **AIRBRUSH:** filled circles (radius): (6,7,1.2) (10,5,1.0) (14,8,1.4) (8,12,1.6) (13,13,1.2) (17,12,1.0) (11,18,1.4) (18,18,1.1).
- **PENCIL:** line (6,18)-(17,7) width 2.2; filled triangle (4,20) (5.5,15.5) (8.5,18.5); circles (19,6,0.9) (20,10,0.9).
- **CHARCOAL:** line (7,17)-(17,7) width 5; circles (5,20,1.0) (9,21,0.9) (4,16,0.9).
- **ERASER:** filled polygon (4,15) (12,7) (19,14) (11,22) closed, drawn as outline width 2; line (8,11)-(15,18) width 2.
- **SELECT:** dashed rectangle outline (5,5)-(19,19) (`PathEffect.dashPathEffect(floatArrayOf(3f*k, 2.5f*k))`), width 2; filled triangle (13,13) (21,16) (16,18) closed.
- **UNDO:** arc from (9,8) curving left and down to (6,13) then right along the bottom to (17,17) (use `Path` with `quadraticBezierTo`) with an arrowhead at (9,8): lines (9,8)-(5,8) and (9,8)-(9,12), width 2.
- **REDO:** the horizontal mirror of UNDO (`x -> 24 - x`).
- **LAYERS:** three stacked rhombus outlines: (12,4) (20,8) (12,12) (4,8); (4,12)-(12,16)-(20,12); (4,16)-(12,20)-(20,16), width 2.
- **EYE / EYE_OFF:** EYE = ellipse outline (3,8)-(21,16) plus filled circle (12,12,2.6). EYE_OFF = the same plus a line (5,19)-(19,5) width 2.
- **LOCK / UNLOCK:** rounded rectangle outline (6,11)-(18,20), plus an arch path above it from (8,11) up to (12,5) and down to (16,11) (closed for LOCK; for UNLOCK end the right side at (16,8)).
- **MENU:** three filled circles at (12,5), (12,12), (12,19), radius 1.8.
- **CHEVRON_LEFT / CHEVRON_RIGHT:** polyline (14,6)-(8,12)-(14,18) / mirror, width 2.4.
- **PLUS:** lines (12,5)-(12,19) and (5,12)-(19,12), width 2.4.
- **TRASH:** rectangle outline (7,8)-(17,20); line (5,8)-(19,8); lines (10,5)-(14,5); lines (10,11)-(10,17) and (14,11)-(14,17), width 2.
- **CLOSE:** lines (6,6)-(18,18) and (18,6)-(6,18), width 2.4.
- **SETTINGS:** circle outline (12,12,4); eight short lines from radius 6.5 to 8.5 around the center at 45-degree steps, width 2.2.
- **IMAGE:** rounded rectangle outline (4,5)-(20,19); filled circle (9,10,1.6); polyline (5,18)-(10,13)-(14,17)-(17,14)-(19,16).

Add a tiny `@Preview`-free test screen is NOT needed; the icons are verified in later steps.

**Done when:** builds, and `UiIcon` can be called with every enum value without crashing (draw all of them once in a throwaway Row inside the settings screen, then remove it).

## Step U3 - Reusable components

All in `ui/components/`. No new dependencies; Material3 and Compose foundation only.

- **`PanelSurface(modifier, content)`:** a `Surface` with `shape = RoundedCornerShape(20.dp)`, `color = surface`, `tonalElevation = 0.dp`, `shadowElevation = 12.dp`, border `BorderStroke(1.dp, outline)`, content padding 8 dp.
- **`ToolButton(icon, label, selected, onClick, modifier)`:** a 48 x 48 dp box with `RoundedCornerShape(14.dp)`. Background `accentSoft` when selected, transparent otherwise. Icon tint `accent` when selected, `textPrimary` otherwise. `contentDescription = label`. Use `clickable` with a bounded ripple.
- **`GradientSlider(value: Float, onChange: (Float) -> Unit, brush: Brush, modifier)`:** a 28 dp high bar with rounded ends drawn with `drawBehind { drawRoundRect(brush = brush, cornerRadius = ...) }` and a white-bordered circular thumb 24 dp wide at `value * (width - thumbWidth)`. Input: `pointerInput(Unit) { detectTapGestures { onChange(it.x / size.width) } }` and a second `pointerInput` with `detectHorizontalDragGestures` mapping `change.position.x / size.width` coerced to 0..1 (use `rememberUpdatedState` for `onChange`). Used by the color panel.
- **`SectionTitle(text)`:** `Text` with 12 sp, `textSecondary`, letter spacing 0.5 sp, uppercase.

**Done when:** builds. (They are exercised in the next steps.)

## Step U4 - The floating tool panel (the main piece)

Create `ui/components/FloatingToolPanel.kt` and use it in `MainScreen` (replace the old left strip). The root of `MainScreen` becomes a `BoxWithConstraints`; pass `containerW` and `containerH` (in px, from `constraints.maxWidth/maxHeight`) into the panel.

**State** in the ViewModel (persisted in `SharedPreferences("ui")`, loaded in `init`, saved when a drag ends or on collapse toggle): `panelFracX: Float` (default 0f), `panelFracY: Float` (default 0.25f), `panelCollapsed: Boolean` (default false), `flyoutOpen: Boolean` (default false, not persisted). The position is stored as FRACTIONS 0..1 of the free space so it still fits after rotating the tablet.

**Layout** (a `PanelSurface` containing a `Column` with 4 dp spacing, `horizontalAlignment = CenterHorizontally`):
1. **Drag handle:** a 36 x 6 dp pill (`outline`-colored, lighter), inside a 48 x 20 dp touch area.
2. **Tool buttons** from this list, in this order, showing only tools that exist in the `Tool` enum: PEN, MARKER, AIRBRUSH, PENCIL, CHARCOAL, ERASER, SELECT. Tapping a non-selected tool selects it. Tapping the already-selected tool toggles `flyoutOpen`.
3. A 1 dp divider (`outline`).
4. **Color button:** a 36 dp circle filled with `vm.color` (with a checkerboard-free 2 dp white-ish `outline` ring). Tapping opens the Color panel (`vm.panel = PanelType.COLOR`, or the existing equivalent).
5. **Size dot:** a circle centered in a 48 dp box whose diameter is `brushSizePx` coerced to 6..40 dp, filled `textPrimary`. Tapping toggles `flyoutOpen`.
6. **Collapse button:** `CHEVRON_LEFT` if the panel is on the right half of the screen, else `CHEVRON_RIGHT`, 48 x 32 dp. Toggles `panelCollapsed`.

When `panelCollapsed` is true show only the handle and ONE button with the current tool's icon (tapping it expands). Wrap the extra items in `AnimatedVisibility` (fade + `expandVertically`, 200 ms).

**Position and dragging (follow this math exactly):**

```kotlin
val density = LocalDensity.current
val marginPx = with(density) { 12.dp.toPx() }
var panelSize by remember { mutableStateOf(IntSize.Zero) }
val freeW = (containerW - panelSize.width - 2 * marginPx).coerceAtLeast(1f)
val freeH = (containerH - panelSize.height - 2 * marginPx).coerceAtLeast(1f)
var dragging by remember { mutableStateOf(false) }
val shownX by animateFloatAsState(vm.panelFracX, spring(dampingRatio = 0.8f, stiffness = 400f), label = "px")
val fx = if (dragging) vm.panelFracX else shownX
val offsetX = marginPx + fx * freeW
val offsetY = marginPx + vm.panelFracY * freeH
// on the PanelSurface:  Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }.onSizeChanged { panelSize = it }
```

The drag handle uses `pointerInput(Unit) { detectDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false; vm.panelFracX = if (vm.panelFracX < 0.5f) 0f else 1f; vm.savePanelPrefs() }, onDragCancel = { dragging = false }) { change, drag -> change.consume(); vm.panelFracX = (vm.panelFracX + drag.x / freeW).coerceIn(0f, 1f); vm.panelFracY = (vm.panelFracY + drag.y / freeH).coerceIn(0f, 1f) } }`. Read `freeW` and `freeH` through `rememberUpdatedState` so the lambda never uses stale values. Horizontal snap goes to the nearest edge (0 or 1) with the animated spring; vertical stays where it was dropped.

**Flyout** (when `flyoutOpen`): a `PanelSurface` 240 dp wide placed next to the panel: if the panel's center is in the left half of the screen put it at `offsetX + panelSize.width + 8.dp`, otherwise at `offsetX - 240.dp - 8.dp`; same top as the panel. Contents: `SectionTitle("Size")`, a `Slider` 1f..80f bound to `brushSizePx`, a live preview circle (diameter `brushSizePx` in dp, max 80 dp) in `vm.color`, and for pen-like tools a "Pressure" `Switch` bound to `usePressure`. Close the flyout when a stroke begins and when the user taps the same tool again.

**Touch rule:** the panel and flyout must only occupy their own bounds. Never wrap them in a `fillMaxSize` container that has pointer input or `clickable`, or touches stop reaching the canvas.

**Done when:** on the tablet you can drag the panel anywhere, it snaps to the left or right edge with a spring, the position survives closing and reopening the app and rotating the tablet, collapse/expand works, tool selection works, and drawing with the pen right next to the panel still works.

## Step U5 - Top action pill and menu

Create `ui/components/TopActionPill.kt`: a small `PanelSurface` at the top-center (`Alignment.TopCenter`, 12 dp from the top, horizontal `Row`), containing 48 dp `ToolButton`s: UNDO (enabled only when `vm.canUndo`, 38% alpha when disabled), REDO (same with `canRedo`), LAYERS (toggles the Layers panel), SETTINGS (opens Settings, Step U9) and MENU (opens a `DropdownMenu`). Menu items (existing actions only, skip any that do not exist yet, and use the "world" wording from the design inspiration section): My worlds (opens the Gallery from U18; until then a simple list), New world, Insert image, Paper, Places (from U12), Export picture, Export world file, Import world file. There is no "Save" item because the app autosaves. Style the menu with `surface` color and `textPrimary` text. Remove the old letter-label top row (U, R, C, B, L, P, F-, Img).

**Done when:** undo/redo/layers/menu all work from the pill; the old text buttons are gone.

## Step U6 - Auto-hiding chrome, zoom indicator and first-run hint

- **Auto-hide:** add `uiVisible` (`mutableStateOf(true)`) to the ViewModel. In `DrawingViewModel.beginStroke` set `uiVisible = false` and cancel any pending show-timer; in `endStroke`/`cancelStroke` start a timer (`viewModelScope.launch { delay(600); uiVisible = true }`, replacing any earlier one). Wrap the tool panel, flyout, top pill and open panels in `AnimatedVisibility(visible = vm.uiVisible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150)))`. Because `AnimatedVisibility` removes hidden content from composition, hidden controls can never swallow a touch.
- **Zoom indicator:** `ZoomChip` at `Alignment.BottomCenter`, 16 dp from the bottom: a small pill (`surface`, 12 sp text) showing `Depth <n> - x<magnification>`. `depth` = the number of parents from `camera.level` up to its root (add `fun depth(): Int` to the engine). `magnification` = `engine.zoomInRoot()` formatted with: under 1000 -> `%.1f`; otherwise scientific like `1.2e6` (`String.format("%.1e", v)`). It appears whenever depth or zoom changes and fades out 1.5 seconds after the last change (use `LaunchedEffect(depth, zoomBucket)` where `zoomBucket = (log10(zoomInRoot) * 20).toInt()`). It does not use `uiVisible`; it hides itself while drawing.
- **First-run hint:** the first time the app runs (flag `hintShown` in `SharedPreferences("ui")`), show a centered `PanelSurface` card with three lines: "Pen draws", "One finger pans", "Two fingers zoom". It disappears on any tap or after 6 seconds, then sets the flag.

**Done when:** the controls fade out the moment the pen touches the screen and return about 0.6 s after lifting; the zoom chip appears while you pinch and fades after; the hint shows only on the very first launch.

## Step U7 - Color panel redesign

Rebuild `ColorPickerPanel` (Step 12) inside a `PanelSurface`, 300 dp wide, placed next to the tool panel the same way as the flyout (opposite side from the screen edge it is snapped to). Contents, top to bottom:
1. A 56 dp high preview swatch (rounded 14 dp) showing the current color over a small checkerboard (draw 8 dp squares in two grays) so transparency is visible; the hex value (`#RRGGBB`, plus alpha if under 100%) in 12 sp to the right.
2. Four `GradientSlider`s labelled with `SectionTitle`: **Hue** (gradient of the 7 hue stops 0, 60, 120, 180, 240, 300, 360 at full saturation and value), **Saturation** (from white to the current hue), **Brightness** (from black to the current hue), **Opacity** (from transparent to the current color).
3. **Recent** (last 8 used colors) and **Basics** (8 preset colors) as rows of 32 dp circles with 6 dp gaps; tapping one sets the color. The selected circle gets a 2 dp `accent` ring.
4. A close button (CLOSE icon) in the top-right corner.

**Done when:** all four sliders drag smoothly, the preview and hex update live, recent colors fill in as you draw, and the panel closes cleanly.

## Step U8 - Layers panel redesign

Rebuild `LayersPanel` (Step 13) inside a `PanelSurface`, 300 dp wide, max height 60% of the screen (`LazyColumn` scrolls). Top of the list is the top layer. Each row is 56 dp high:
- left: EYE / EYE_OFF button; then LOCK / UNLOCK button (48 dp targets);
- middle: the layer name (14 sp; tap = make active; long-press = rename dialog) with a 2 dp `accent` outline and `accentSoft` background on the active row;
- right: a small opacity readout in `textSecondary` (e.g. "80%"). Tapping the row's right side expands it to show the opacity slider and the blend-mode dropdown (only one row expanded at a time).
Footer row of `ToolButton`s: PLUS (add layer), TRASH (delete the active layer, with a confirm dialog if the layer has strokes), chevron up / down (move the layer; use CHEVRON icons rotated 90 degrees with `Modifier.rotate`). Every change still goes through the Step 10 undoable commands.

**Done when:** adding, deleting, renaming, hiding, locking, reordering and changing opacity all work, and the active layer is clearly highlighted.

## Step U9 - Settings panel and canvas-first polish

- **Settings panel** (opened from the top pill): a `PanelSurface` with `SectionTitle`s and these rows, saved in `SharedPreferences`: "Finger draws" (switch), "Pressure sensitivity" (switch), "Auto-hide controls while drawing" (switch, default on; when off `uiVisible` never becomes false), "Show zoom indicator" (switch), "Show debug text" (switch, `vm.showDebug`), "Reset tool panel position" (button: sets `panelFracX = 0f`, `panelFracY = 0.25f`, `panelCollapsed = false`).
- **Polish checklist** (fix each failure):
  1. Every `ToolButton` and icon button is at least 48 x 48 dp and has a `contentDescription`.
  2. No text smaller than 12 sp. Text contrast on `surface` is readable.
  3. Opening any panel never moves or resizes the canvas.
  4. The tool panel can be dragged to the far left, far right, top and bottom without leaving the screen, in both portrait and landscape.
  5. Rotating the device keeps the panel on screen and keeps all state.
  6. Opening a panel while the other is open closes the first (one panel at a time), but the tool panel stays.
  7. Every dialog (rename, confirm) uses the dark theme.

**Done when:** all settings take effect immediately and survive an app restart, and every checklist item passes on the tablet.

## Step U10 - S Pen extras (optional)

- **Side button = eraser while held:** in `DrawingView`, on `ACTION_DOWN` with a stylus, if `(e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0` use the ERASER for that stroke only. Implement it with a ViewModel field `overrideTool: Tool?` that `beginStroke` consumes and `endStroke` / `cancelStroke` clear. The panel keeps showing the real selected tool.
- **S Pen eraser end** (`TOOL_TYPE_ERASER`) also uses `overrideTool = ERASER`, instead of drawing with the current brush.
- **Hover (optional):** if the pen hovers (`onHoverEvent`, `ACTION_HOVER_MOVE`), draw a thin circle outline showing the brush size under the pen tip (diameter `brushSizePx`). Skip it if it costs frame rate.

**Done when:** holding the S Pen button turns the pen into an eraser for that stroke, releasing it returns to the selected tool, and flipping the pen to its eraser end erases.

## Exploration steps (inspired by Google Earth, Google Maps, Procreate and Figma)

There are NO portals or separate worlds inside a project. All navigation below is built on the existing tree of zoom levels. U11-U18 need the following earlier work: the core app (and the rotation step only for the compass in U15), plus U1-U6. Do them in order.

### New icons (add to `UiIcon` at the start of U11)

- **SEARCH:** circle outline (10,10) radius 5, line (14,14)-(19,19) width 2.4.
- **PIN:** circle outline (12,9) radius 4.5; polyline (8.2,12.5)-(12,21)-(15.8,12.5), width 2.
- **HOME:** polyline (4,12)-(12,4)-(20,12); rectangle outline (6,12)-(18,20).
- **COMPASS:** circle outline (12,12) radius 9; filled triangle (12,5) (14.5,12) (9.5,12) in the accent color; outline triangle (12,19) (14.5,12) (9.5,12).
- **BACK:** lines (19,12)-(5,12) and polyline (11,6)-(5,12)-(11,18), width 2.4. **FORWARD:** the horizontal mirror.
- **BOOKMARK:** polygon outline (7,4) (17,4) (17,20) (12,16) (7,20), width 2.
- **FIT:** four corner brackets: (4,9)-(4,4)-(9,4); (15,4)-(20,4)-(20,9); (20,15)-(20,20)-(15,20); (9,20)-(4,20)-(4,15), width 2.4.

## Step U11 - Places: naming levels, place card, breadcrumb

A **place** is a zoom level the user has named ("Earth", "House", "Bedroom").

- **Model:** add `var name: String = ""` to `ZoomLevel`. Add `val name: String = ""` to `LevelDto` (old save files must still load). Undoable command `RenameLevel(levelId, old, new)`.
- **Engine:** add `fun depth(level: ZoomLevel): Int` (number of parents up to the root) and
  ```kotlin
  class AncestorFrame(val level: ZoomLevel, val x: Double, val y: Double, val z: Double)
  fun ancestors(): List<AncestorFrame>   // nearest first; (x, y, z) = the current camera expressed in that ancestor
  ```
  using the same math as the ancestor chain in `renderWorld` (`x = l.originX + x / F`, `y = l.originY + y / F`, `z *= F`, walking `l.parent`, at most 150 steps). Add a unit test: for every frame, converting back down gives the same `centerInRoot()`.
- **PlaceCard** (`Alignment.TopStart`, 12 dp margin): a small `PanelSurface`. Line 1: the level's name (16 sp, textPrimary) or "Unnamed place" (textSecondary). Line 2: "Depth n" (12 sp, textSecondary). Tapping it opens a rename dialog ("Name this place", up to 40 characters, dark theme). It hides with the other chrome while drawing.
- **Breadcrumb:** a horizontally scrolling row of chips under the card: ancestors from the top down to the current level, each showing its name or "Depth n". Show at most the last 4 and a "..." chip for the rest. Tapping a chip flies there (U12; until U12 exists, do nothing on tap).
- **Arrival title:** when the camera enters a level with a non-empty name that differs from the previous one, show a large centered title (28 sp, textPrimary at 85% alpha, at 22% of the screen height): fade in 250 ms, hold 1500 ms, fade out 400 ms. Not while drawing.
- **Naming nudge:** if the camera has stayed 1.2 s in a level with an empty name that was created during this session by zooming in, show a small chip at the bottom center, "Name this place?", for 4 seconds; tapping it opens the rename dialog. At most once per level and once per 30 seconds.

**Done when:** you can name levels, the card and breadcrumb always show where you are, names fade in as you arrive, and names survive save and reopen.

## Step U12 - Fly-to and history (the Google Earth feel)

**Snapshots:**
```kotlin
@Serializable class CameraSnapshot(val levelId: Int, val cx: Double, val cy: Double, val zoom: Double)
```
`engine.snapshot()` returns the current camera. `engine.restore(s)` sets level, cx, cy, zoom directly (the snapshot must already have `zoom` in 0.5 until 100; call `normalize()` afterwards).

**FlyPlanner** (`engine/FlyPlanner.kt`, pure logic, unit tested). `fun plan(from: CameraSnapshot, to: CameraSnapshot): List<Waypoint>` where `Waypoint(levelId, cx, cy, zoom)`. Consecutive waypoints in the SAME level are animated; consecutive waypoints in DIFFERENT levels are an instant hop that looks identical (the conversion below). Algorithm:
1. `start chain` = the start level and its parents up to the root. `target chain` = the same for the target. `A` = the first level of the start chain that is also in the target chain (the common ancestor).
2. Going **up** from the current level `L` to `A`: add waypoint `(L, cx, cy, z)`; if `z > 0.6` add `(L, cx, cy, 0.6)` (zoom out); then hop to the parent: `cx = L.originX + cx / F`, `cy = L.originY + cy / F`, `z = z * F`. Repeat until the level is `A`.
3. Going **down** from `A` to the target level: for each level `C` on the path (child of the current level `X`): add `(X, cx, cy, z)`, then `(X, C.originX, C.originY, 99.5)` (move to the child's origin and zoom in), then hop: `(C, 0.0, 0.0, 99.5 / F)`.
4. Finish in the target level: add `(T, cx, cy, z)` if needed, then `(T, to.cx, to.cy, to.zoom)`.
Add unit tests: the last waypoint equals the target; every hop keeps `centerInRoot()` and `zoomInRoot()` equal to the previous waypoint (tolerance 1e-9 relative); a trip up two levels and back down works.

**FlyAnimator** (in `DrawingView`, `postOnAnimation`). For each pair of consecutive waypoints in the same level, duration `d = (250 + 150 * |ln(z1 / z0)|)` ms, capped at 900 ms; scale all durations down so the whole trip is at most 3500 ms. Per frame, with `t = elapsed / d` and `s = t * t * (3 - 2 * t)`: `zoom = z0 * (z1 / z0).pow(s)`, `cx = cx0 + (cx1 - cx0) * s`, same for `cy`. Set `camera.level`, `cx`, `cy` and `zoom` directly each frame; do NOT call `normalize()` during the flight. Different-level pairs are hops: set the camera at once and continue. Call `normalize()` and `invalidate()` at the end. **Any touch cancels the flight at the current view.** While flying, chrome stays visible and drawing is disabled.

**History:** `HistoryManager` in the ViewModel keeps up to 100 `CameraSnapshot`s and an index. Record a snapshot when `camera.level.id` changes and then stays stable for 1 second (debounced coroutine). Do not record during a flight. BACK and FORWARD buttons (small `ToolButton`s on the right side of the PlaceCard, disabled when not available) fly to the previous/next snapshot without adding new entries.

**Saved views (bookmarks):** `@Serializable class Bookmark(val id: Long, var name: String, val snapshot: CameraSnapshot, val thumb: String)` stored in `ProjectDto` (`bookmarks: List<Bookmark> = emptyList()`). Menu -> "Places" panel: two sections. "Saved views": a grid of 96 dp thumbnail cards (a 256 px PNG in `projects/<name>/bookmarks/<id>.png`, rendered from the current view with the same renderer as Step 17) with a name; tap flies there; long-press renames or deletes. A "+" card saves the current view. "Named places": every level with a name; tap flies there at `(0, 0, zoom 1)`.

Wire the breadcrumb chips from U11: tapping an ancestor chip flies to `(ancestor level, frame.x, frame.y, 1.0)`.

**Done when:** tapping a breadcrumb chip or a saved view makes the camera glide out and in across levels with no jump or flicker; any touch stops the flight; Back/Forward retrace the history; saved views survive a restart.

## Step U13 - Search (Maps style)

Add a SEARCH button to the top pill. Tapping it expands the pill into a text field (keyboard action `ImeAction.Search`) with a result list under it (at most 6 rows, `PanelSurface`, dark). It searches, case-insensitively with `contains`, the names of named places and saved views. Each row: PIN icon (or the bookmark's thumbnail, 40 dp), the name, and "Depth n" in `textSecondary`. Tapping a row closes the search and flies there (U12). Show "No places yet - name a place by tapping the card at the top" when the project has no names. Closes on Back and when the canvas is touched.

**Done when:** typing part of a place or saved-view name lists it, tapping a result flies there, and the keyboard never covers the result list (use `imePadding()`).

## Step U14 - Minimap (Google Maps style)

A 128 dp square `PanelSurface` (rounded 20 dp) at the bottom corner OPPOSITE the side the tool panel is snapped to. It shows where you are in the level above.

- **Frame:** take the first entry of `engine.ancestors()` (the parent frame `(px, py, pz)`). If the camera level has no parent, use the current level and the current camera instead. Let `Wp = w / pz` and `Hp = h / pz` (the visible region in that level's units) and `zm = 0.35 * 256 / max(Wp, Hp)` (the minimap bitmap is 256 x 256 px).
- **Rendering:** add `CanvasRenderer.renderMini(canvas, vm, level, camX, camY, zoom, w, h)`: fill the paper color, then `drawLevelAndDown(canvas, vm, level, px, py, zm, null, 256, 256)`. Render into a 256 x 256 `Bitmap` on the main thread, **at most every 150 ms** and only when the camera, the strokes or the minimap frame changed. Show it with `Image`. Draw on top: a 2 dp accent rectangle of size `Wp * zm` by `Hp * zm` centered in the minimap (the current view), plus a 4 dp center dot.
- **Tap:** converts the tap to the level's coordinates `(tx, ty)` and flies to the normalized state of `(that level, tx, ty, pz)`. Add `engine.normalizedState(level, cx, cy, zoom): CameraSnapshot` which runs the same descend/ascend rules (it may create child levels that do not exist yet; document this in `DECISIONS.md`).
- Setting "Show minimap" in the settings panel (default on). Hidden with the other chrome while drawing.

**Done when:** the minimap shows the surroundings one level up with a rectangle for your view, the rectangle stays about 35% of the map as you zoom, and tapping the map flies to that spot.

## Step U15 - Orientation: compass and Home

- **Home:** the camera state `(level 0, 0.0, 0.0, zoom 1.0)`. A HOME button on the PlaceCard row (right of BACK/FORWARD) flies there.
- **Compass (needs Step 24 rotation):** a 44 dp round `PanelSurface` button at `Alignment.TopEnd`, below the top pill, with the COMPASS icon rotated by `-camera.rotation` degrees. It is visible only while `rotation != 0`. Tapping animates the rotation to 0 over 250 ms about the screen center (use `transformAt` per frame). If Step 24 is not built yet, skip the compass and note it in `PROGRESS.md`.

**Done when:** Home glides back to the start view from anywhere; the compass appears only when rotated and levels the canvas smoothly.

## Step U16 - Procreate-style gestures and edge sliders

**Finger-tap gestures** (in `DrawingView`, finger input only, never during a stylus stroke): record the time and the camera at the first `ACTION_DOWN`, the maximum number of pointers, and each pointer's total movement. If the last pointer lifts within 280 ms and no pointer moved more than 24 px, it is a tap with `n` fingers:
- 2 fingers: undo. 3 fingers: redo. 4 fingers: toggle `vm.chromeHidden` (hides ALL chrome until toggled again; `uiVisible` is ignored while true).
- Restore the camera recorded at the start so a tap never causes a pan or zoom.
- Show a small `ToastChip` ("Undo", "Redo", "Controls hidden") at the bottom center for 800 ms.

**Edge sliders:** two vertical sliders in a `PanelSurface` (40 dp wide, 180 dp tall) at the vertical center of the screen edge OPPOSITE the tool panel: **Size** and **Opacity**, with a 12 dp gap between them.
- Dragging up increases the value. Size maps with `size = 1 + 79 * v * v` (finer control at small sizes). Opacity maps to the alpha of `vm.color` from 5% to 100%.
- While dragging show a live preview bubble next to the slider: a circle of diameter `size` (max 80 dp) in the current color for Size, and the percentage for Opacity.
- Setting "Edge sliders" (default on). Hidden with the other chrome.

**Eyedropper (optional, skip if it hurts frame rate):** holding one finger still for 450 ms (movement under 10 px) while not drawing opens a loupe: render the current view once into a half-resolution `Bitmap`, show a 72 dp circle above the finger with the pixel color under it, update it while the finger moves, and set `vm.color` when the finger lifts.

**Done when:** a quick two-finger tap undoes, three redoes, four hides and shows the controls; the edge sliders change the brush live with a preview bubble; normal pan and pinch still work.

## Step U17 - Zoom ergonomics (Figma style)

- **Double-tap zoom:** two finger taps within 300 ms and 40 px of each other (each tap shorter than 200 ms, not moved) zoom in by 2.5x around the tap point over 200 ms. Implement it per frame: `factor = 2.5.pow(dt / 0.2)` applied through `engine.zoomAt`.
- **Double-tap and drag:** if the second tap stays down and moves more than 10 px, it becomes a one-finger zoom: `factor = exp(-dy / 200)` per movement (dragging up zooms in, down zooms out), anchored at the first tap point. A second tap that does not move is the double-tap zoom above.
- **Zoom menu:** tapping the ZoomChip (U6) opens a small dark dropdown with three items: **Fit view**, **Reset zoom**, **Home**.
  - Fit view: compute the union of the bounds of all strokes and images on visible layers of the CURRENT level. If empty show a `ToastChip` "Nothing to fit here". Otherwise fly (a same-level flight, U12) to `cx, cy` = the bounds center and `zoom = 0.85 * min(w / bw, h / bh)` coerced into 0.5 until 99.
  - Reset zoom: fly to the same center at `zoom = 1.0`.
  - Home: as in U15.
- Finger double-tap actions are disabled when `fingerDraws` is on.

**Done when:** double-tap zooms in smoothly, double-tap-drag zooms with one finger, and the three zoom-menu items work.

## Step U18 - Gallery: "My worlds" (Procreate style, not a file list)

- **Gallery screen:** a full-screen dark composable (`LazyVerticalGrid(GridCells.Adaptive(180.dp))`, 16 dp padding and gaps) opened from Menu -> "My worlds". Cards: 4:3 thumbnail with 20 dp rounded corners, the world name (14 sp) and "Edited 3 days ago" (12 sp, textSecondary). The first card is "+ New world" (dashed outline). A back button returns to the canvas. Never show file paths or file extensions.
- **Thumbnails:** whenever the project is autosaved, also write `projects/<name>/thumb.png` (512 px wide, a render of the current view with the Step 17 renderer, best-effort inside `try/catch`). Cards without a thumbnail show the paper color.
- **Card actions** (long-press -> dropdown): Rename, Duplicate (copy the folder, name + " copy"), Export world file (Step 23), Delete. Delete asks "Delete this world? This cannot be undone." and only removes a folder whose canonical path is inside `filesDir/projects`.
- Tapping a card loads that world with a crossfade (capture the current view, load, fade the old bitmap out over 300 ms). The app still opens straight into the last world on launch.
- Replace every UI string that says project / file / document with the "world" vocabulary from the design inspiration section.

**Done when:** the gallery shows thumbnail cards, you can create, open, rename, duplicate and delete worlds safely, and nothing on screen looks like a file browser.

---

# AFTER EACH STEP

Update `PROGRESS.md`, give the report in the format from Part 2, then STOP and wait for "go". The first time you read this file, add lines for U1-U18 to `PROGRESS.md` marked NOT STARTED (keep the existing lines for the core steps).
