# PROGRESS.md

Last updated: 2026-10-30 (F0 audit and plan delivered; F1-F9 await owner approval)

## Finished steps
- On-device test session — 2026-10-26 — **ALL 12 TEST GROUPS PASS after 10 bug fixes**
  Device: Samsung Galaxy Tab S9 FE (SM-X510, Android 16, 1440x2304), driven via adb
  (install, taps/swipes, uiautomator dumps, screenshots + pixel diffs, logcat crash
  buffer, run-as file access). Verified: cold launch (818ms, zero crashes), project
  persistence/restore, pan + fling momentum, finger-draws guard, undo/redo, all 6
  brushes, color picker end-to-end (HSV->stroke RGB match), stabilizer/gamma
  persisted to SharedPreferences, layer system (add/hide/lock/toast-guard/opacity/
  blend/delete+undo/rename), select tool (pick, dashed outline, move, scale,
  delete, undo/redo byte-exact restore, deselect), raster tools (oil/mix/smudge/
  erase + raster undo/redo), PNG export (99.998% screen match), .uzw export
  (project.json + images + tiles), .uzw import + " (2)" dedup + project switch +
  pref update, force-stop persistence (camera / active layer / layer props / tiles).
  Stylus, pinch-zoom, twist rotation and palm rejection remain manual-only (adb
  cannot inject two pointers or a stylus); rotation was incidentally confirmed
  working (overlay rot=-15 with coherent rendering after an on-device twist).

  Bugs found and FIXED during testing (each fix verified on-device after rebuild):
  1. Toolbar overflowed short/windowed layouts (buttons off-screen, slider
     overlap) -> compact scrollable toolbar + Size slider to bottom-start (9f4490a).
  2. addImage could place a degenerate 0x0 image (view measured 0 in Samsung
     pop-up windows) -> guarded + sane fallback sizes.
  3. Airbrush deviated from plan Step 8 (0.35 blur / 80% alpha) -> restored
     0.6 width blur + 50% alpha + <=300px guard.
  4. LayersPanel: lock toggle + rename missing; eye/opacity/blend edits bypassed
     commands (not undoable); panel never recomposed (no Compose state) ->
     lock/rename added, all edits via SetLayerProps, layersVersion reactive key,
     reactive activeLayerId, ignored-drawing Toast implemented (plan Step 10).
  5. Layer opacity/blend never rendered for vector strokes (Step 10's saveLayer
     was never implemented; only tiles/images got the alpha) -> saveLayer wrap
     when opacity < 1 or blend != NORMAL with BlendMode-mapped layer paint.
  6. Active layer, camera moves and layer edits did not survive force-stop (no
     autosave on them) -> Project.activeLayerId persisted in ProjectDto, view
     gestures/Reset buttons schedule autosave, every layer edit bumps + saves.
  7. CRITICAL: raster dab/erase/snapshot passed x0 as the getPixels/setPixels
     OFFSET argument -> ArrayIndexOutOfBoundsException on any dab not touching the
     tile's left edge; Oil/Mix/Smudge/RasterErase crashed the app on the first
     interior dab. Fixed at all 7 call sites (offset must be 0).
  8. Export PNG crashed the app: its success Toast fired from the Dispatchers.IO
     coroutine ("Can't toast on a thread that has not called
     Looper.prepare()") -> all 4 async callbacks (exportPng/exportUzw/importUzw/
     addImage) hop to Dispatchers.Main before invoking onDone.
  9. Export PNG sized its bitmap from Compose's AndroidView.update, which fires
     pre-layout with 0 and may never re-run -> createBitmap(0,0) IAE. DrawingView
     now reports real size via onSizeChanged into the ViewModel.
  10. CRITICAL: loaded tile PNGs are immutable -> Canvas.setBitmap threw a
     messageless IllegalStateException inside load(); load() returned false AFTER
     deserialize() (partial load: strokes/camera restored, tiles+images silently
     skipped) and a later save could overwrite the real project with the default
     empty one. Fixed: decode -> copy(ARGB_8888, mutable) in loadTiles AND the
     renderer's evicted-tile loadTile; per-tile try/catch; tiles/images load
     failures are non-fatal. Tiles now survive restart (oil band ink 170 -> 7306
     px after reload) and the .uzw import works (it previously failed on load).
  JVM tests after all fixes: 15/15 pass. Final session: zero load failures,
  zero new crashes.
- Step 24 — Rotation — 2026-10-23 — **DONE, awaiting on-device verification** (last planned step)
  The spec's "given formulas" were never recorded in the repo, so the transforms
  were re-derived from first principles and logged in DECISIONS.md: screen =
  center + R(theta) * (world - camera) * zoom. Engine: Camera.rotation (radians);
  screenToLocal and panBy now apply R(-theta); new transformAt(factor, delta,
  sx, sy) does zoom + rotate in ONE anchored step (the world point under the
  anchor stays fixed); zoomAt is a thin wrapper over transformAt (existing call
  sites unchanged); rotation wrapped to [-pi, pi]. DrawingView navigate:
  two-finger twist (angle between fingers) applied together with the pinch
  factor at the finger midpoint; single-finger pan never rotates; fling
  (Step 19) works rotated automatically because panBy is rotation-aware.
  Renderer: shared worldToScreen helper (exact inverse of screenToLocal) used
  by drawStroke/drawDabs/drawImage/drawRasterTiles/drawSelectionOutline; ALL
  rect culls replaced by circle culling (bounds center + half-diagonal radius;
  pencil/charcoal jitter pad kept; the drawLevelContent pre-cull removed);
  paper grid drawn via canvas.rotate about the screen center with ranges
  extended by the screen diagonal; images and raster tiles drawn via
  canvas.rotate about their own screen center; the selection outline is now a
  dashed quad through the 4 rotated bounds corners. DrawingViewModel.selectMove
  derives the move preview from two screenToLocal calls (rotation-aware).
  Toolbar: "Reset Rotation" button; debug overlay shows rot in degrees.
  Persistence: CameraDto.rotation with default 0.0 (old saves load unrotated).
  New RotationTest (4 tests: screen center anchor, pan direction at 90 deg,
  transformAt anchor invariance, rotation stays wrapped). Build + tests pass
  (15 tests).
- Step 23 — Project export and import (.uzw) — 2026-10-23 — **DONE, awaiting on-device verification**
  Toolbar: "Export .uzw" (CreateDocument("application/zip"), suggested name
  "<name>.uzw") and "Import .uzw" (OpenDocument, */*). DrawingViewModel.
  exportUzw/importUzw run on Dispatchers.IO with Toast feedback; all failures
  produce a message, never a crash. ProjectStorage.exportUzw: flushes first
  (autosave.flushNow) so the archive matches the on-screen state, then zips
  the whole projects/<name>/ folder (project.json, images/, tiles/) to the
  SAF stream. ProjectStorage.unzipImport: display-name-derived folder
  (extension stripped, unsafe chars -> "_"), " (2)"/" (3)" dedup,
  zip-slip canonical-path check, 10,000-entry cap, 500 MB uncompressed cap,
  project.json required at the root; on any failure the partial folder is
  deleted. Import then switches projects on the MAIN thread (same as the
  startup load), sets project.name to the new folder (the DTO name would
  otherwise point autosave back at the ORIGINAL project folder) and
  flushes; undo stack, selection and pendingRemoved cleared on switch.
  ProjectStorage.load gained an optional bitmaps map + loadImages():
  placed-image bitmaps were only ever populated by addImage, so images
  were missing after an app restart — startup and import now pass
  vm.bitmaps, fixing that pre-existing Step 16 gap. New UzwImportTest
  (2 tests: name sanitizing, folder dedup). Build + tests pass (11 tests).
- Step 22 — Pencil and charcoal brushes — 2026-10-23 — **DONE, awaiting on-device verification**
  (This step OVERRIDES the plan's "BrushType is exactly PEN/MARKER/AIRBRUSH"
  rule for itself, per the step spec.) BrushType and Tool gain PENCIL and
  CHARCOAL (Models.kt, DrawingViewModel). endStroke maps the new tools and
  records pressure for PEN/PENCIL/CHARCOAL alike (honors the Pressure-
  sensitive switch). CanvasRenderer.drawDabs stamps small dabs along the
  polyline in LEVEL-LOCAL space — spacing 0.35 x local (pressure-interpolated)
  radius, start + end dabs guaranteed — so the dab set is fixed by the stroke
  geometry and the grain cannot swim while panning/zooming. Each dab's center
  jitter, size and alpha come from dabHash(strokeId, dabIndex), a pure
  top-level hash (no Random, no image assets); offscreen dabs still consume
  an index so panning cannot shift the grain. Capped at 3000 dabs/stroke.
  Pencil character: dab size 0.55-0.95r, alpha 0.60-1.00, jitter 0.30r;
  charcoal: 0.45-0.90r, alpha 0.28-0.55, jitter 0.45r (first-pass values;
  tune on device). Bounds cull widened by 0.6*width*zoom for these brushes
  (jittered dabs reach outside the padded bounds). BrushSettingsPanel: second
  tool row (Pencil, Charcoal; active highlighted). New DabHashTest (3 tests:
  determinism, variation, non-degenerate output). Build + tests pass
  (9 tests). Old saved projects unaffected (enum serialization is by name).
- Step 21 — Select, move and scale — 2026-10-23 — **DONE, awaiting on-device verification**
  Consolidated the duplicated half-finished Step-21 code that had been committed
  in `058eee5` ("Before Hermes") into ONE implementation (that commit had the
  selection block pasted twice into DrawingViewModel.kt and a wrong
  ScaleSelection constructor call — it could not compile). `Tool.SELECT` +
  toolbar "Select" button (● when active). Single-item
  `Selection(levelId, strokeId|imageId)`; PICK vs MOVE gestures in
  DrawingViewModel.selectBegin/Move/End/Cancel hooked into
  beginStroke/addPoint/endStroke/cancelStroke (press inside the selected item's
  bounds +8px/zoom margin -> MOVE with live previewDx/Dy preview; otherwise PICK
  if the tap moved <10px; tapping empty space deselects; second finger cancels
  with no command; SELECT is exempt from the active-layer guard). hitTest via
  distPointSeg, tolerance 12px/zoom + width/2, topmost first, visible+unlocked
  layers only, current level only. Commands: MoveStrokes/MoveImage (immutable
  copies, id-stable so the selection survives undo; lateinit hardened to
  nullable) and ScaleSelection (around the item's own bounds center; stroke
  points+width scale, image w/h scale around center). CanvasRenderer: selected
  item drawn offset by previewDx/Dy (offset threaded through
  drawStroke/cull/drawImage) + dashed rectangle around its bounds (blue,
  DashPathEffect 14/8) while SELECT is the active tool. New
  ui/panels/SelectionPanel.kt: scale slider 25-400% commits ONE ScaleSelection
  on release then snaps back to 100%, Delete, Deselect. Panel auto-opens on
  pick and auto-closes on deselect (LaunchedEffect in MainScreen). New test
  class SelectCommandsTest (4 tests: move/scale + undo for strokes and images).
  Build + tests pass (6 tests). Decisions logged in DECISIONS.md (new file).
- Step 20 — Stabilizer + pressure gamma — 2026-10-23 — DONE, awaiting on-device verification.
  `DrawingViewModel`: `stabilizer` (0..1, default 0.3) and `pressureGamma`
  (0.5..2.0, default 1.0) persisted in SharedPreferences("settings") and
  loaded in init. `beginStroke` sets smoothingActive for vector tools
  (PEN/MARKER/AIRBRUSH) only — eraser, raster tools, SELECT exempt. `addPoint`
  smooths in SCREEN space: pressure is gamma-curved (`p.pow(gamma)`) then
  exponential-smoothed with alpha = 1 - 0.8*stabilizer along with x/y;
  smoothing happens before screenToLocal. `endStroke` appends the raw final
  point (converted) when the pen lifted >1px away from the last smoothed
  point, so strokes end where the pen did. View size captured (w0/h0) for the
  conversion. `BrushSettingsPanel`: two new sliders (Stabilizer,
  Pressure gamma) write straight to the persisted vm fields.
  Build + tests pass.
- UI toolbar rework — 2026-10-20 (commit `0830f56`, user request). Toolbar
  moved from a top Row to a left Column aligned CenterStart with top padding
  110.dp so it sits below the debug level/zoom overlay. All buttons are full
  words now: Undo, Redo, Color, Brush, Layers, Paper (open panel gets ●),
  Finger: On/Off, Reset View, Oil, Mix, Smudge, Raster Erase (active tool ●),
  Insert Image, Export PNG. Shared `toolbarButton` composable. Export now
  also toasts on failure. Build passes.
- Bug-fix pass — 2026-10-20 (commit `1083b4c`). Four bugs fixed:
  (1) Drawing was allowed on hidden/locked layers — `beginStroke` now checks
  `activeLayer()` visible && unlocked and returns early (plan Step 10 rule).
  (2) Paper GRID drew each vertical line once per row (O(cols x rows) drawLine
  calls); now one pass for verticals, one for horizontals, DOTS unchanged.
  (3) Hardware stylus eraser never activated the raster eraser —
  `DrawingView.startDraw` now sets `rasterEraserMode` when a raster tool is
  active, else `eraserMode`; both setters remember `preEraserTool` and restore
  it when the eraser tip lifts (previously they always snapped back to PEN).
  (4) `vm.tool` was a plain var so the toolbar's active-tool bullet never
  recomposed — now `by mutableStateOf`.
- Phase 2 fix — tile-cache thread safety — 2026-10-20 (commit `b156f28`).
  RasterTiles now guards `tiles`/`evictedKeys` with a lock: tileFor, clearAll,
  evict synchronized; new tilesSnapshot() gives render/save loops a stable
  copy. CanvasRenderer and ProjectStorage.saveTiles iterate the snapshot. The
  flagged CME risk (autosave IO thread evicting while UI draws) is resolved.
  Build + tests pass.
- Phase 2 polish — tile memory cap — 2026-10-20 (commit `721a48b`).
  `RasterTiles`: Tile.lastUse, touch(), MAX_TILES=64 (~16 MB), evict() drops
  only NON-DIRTY tiles (recycles bitmaps, keys to evictedKeys set),
  loadTile() reloads a single tile PNG on demand, isEvicted(). ProjectStorage
  save() calls evict() after flushing. CanvasRenderer.drawRasterTiles touches
  tiles on draw and reloads recycled/evicted tiles from disk in-view.
  In-progress raster preview already tile-only (dabs apply live). Mix/smudge
  strength tuning remains on-device. Build + tests pass.
- Phase 2b — raster undo/redo — 2026-10-20 (commit `ea964c1`). Raster dabs are
  now undoable: `RasterTiles.TileSnapshot` (key, cx/cy/r rect, pixels) captures
  pre-dab pixels into `vm.rasterBefore` during OIL/MIX/RERASER strokes;
  `endStroke` recaptures the same rects as 'after' and pushes
  `engine.RasterCommand(before, after)` onto the UndoStack — undo restores
  before, redo restores after (no re-stamping, robust for overlaps). Fresh
  tiles get a full-tile empty snapshot. SMUDGE stays exempt (snapshots drift
  under neighboring dabs); cancelStroke rolls raster tiles back. Smudge
  strength kept at MIX/SMUDGE/SMUDGE_PICK = 0.65/0.30/0.55 (unchanged; tune on
  device). Build + tests pass.
- Phase 2a — raster tile brushes — 2026-10-20 (commit `4056d6d`). New
  `render/RasterTiles.kt`: 256x256 ARGB tiles keyed (levelId, layerId, tx, ty),
  created lazily where painted. Tools OIL/MIX (mixing dabs, coverage-weighted
  blend, pressure-scaled radius), SMUDGE (picks up average color under brush,
  drags it), RERASER (clears pixels in circle). Raster dabs stamp in
  beginStroke/addPoint; raster tools never create vector strokes; autosave
  schedules after raster strokes. Renderer draws visible tiles per layer with
  opacity/blend; tiles painted in LEVEL-LOCAL pixels (1 unit = 1 px) so they are
  sharp near the painted zoom level. Persistence: tiles saved as
  `projects/<name>/tiles/<level>_<layer>_<tx>_<ty>.png` on every save, loaded on
  project load (store cleared first). MainActivity toolbar adds O/M/S/RE buttons
  (active marked ●). Build + tests pass.

- Step 18 — final checklist/commit — 2026-10-20. Commit `e2defeb` finishes the
  uncommitted Steps 8-17 work (PNG export button, tool-aware live stroke preview,
  Kotlin 2.0 catch fix). assembleDebug + test BUILD SUCCESSFUL. On-device manual
  checks still pending (user). Git history quirk: `c371ac0` bundled Steps 8-16.

- Step 7 (brush tools) — 2026-10-19. `BrushType.PEN/MARKER/AIRBRUSH` all render
  distinctly: PEN = opaque ribbon w/ pressure; MARKER = flat, 70% alpha, pressure off;
  AIRBRUSH = blurred edge (BlurMaskFilter), 80% alpha. Eraser is now a tool
  (`Tool.ERASER`) in the brush panel; hardware stylus eraser still works via
  `vm.eraserMode` setter. Files: `CanvasRenderer.kt`, `BrushSettingsPanel.kt`,
  `DrawingViewModel.kt`. Build + tests pass, committed.

- Steps 1–2 — committed (`f80b1b5 Steps 1-2 working`).
- Step 3 — Data models: `model/Models.kt` (BrushType = PEN, MARKER, AIRBRUSH).
- Step 4 — Zoom engine: `engine/InfiniteZoomEngine.kt` verified against the plan
  (descend/ascend math, thresholds 100/0.5, CHILD_REACH 500). `EngineTest` re-run
  with `--rerun-tasks` passes (testZoomIn + testZoomOut).
- Step 5 — Navigation view: `input/DrawingView.kt`, `render/CanvasRenderer.kt` verified
  against the plan (stylus draws / fingers navigate / palm rejection / cancel on second
  finger when fingerDraws). Note: DrawingView also sets `vm.eraserMode` from
  `TOOL_TYPE_ERASER`, which belongs to Step 8 — the file is shared between steps.
- Step 6 — Strokes with ribbon `drawStroke` — committed (`03d7906 Step 6 verified`).
  Re-verified this session: ViewModel stroke lifecycle matches plan (local coords,
  1.5/zoom min-distance filter, width in level-local units, recentColors max 8,
  in-progress stroke drawn as temp Stroke with BrushType.PEN).
- Step 7 — `renderWorld` level-tree rendering verified in CanvasRenderer.kt
  (ancestors underneath, current + descendants on top, exclude-subtree, sub-pixel
  culling at cz < 0.02, debug text retained). Uncommitted diff. Re-verified:
  `render` = drawPaper → renderWorld → drawInProgress; `drawPaper` pattern repeat
  clamps screen pitch to 20-160 px (sp /= 5 / sp *= 5); layer blend modes NORMAL /
  MULTIPLY / SCREEN / OVERLAY / DARKEN / LIGHTEN via PorterDuffXfermode.
- Step 8 — Brush/Tool variants (marker alpha, airbrush blur, eraser) in ViewModel/renderer.
- Step 9 — Undo/redo system moved to `engine/Commands.kt` (untracked); `model/Commands.kt`
  deleted in working tree.
- Step 10 — Layer logic & rendering (opacity/blend/lock) wired into ViewModel/renderer.
- Step 11 — Compose UI shell overlays in `MainActivity.kt`.
- Steps 12–15 — panels: ColorPickerPanel, BrushSettingsPanel, LayersPanel, PaperPanel.
- Step 16 — Persistence: `storage/ProjectStorage.kt` (untracked), autosave debounce,
  restore-last-project, save on ON_PAUSE/dispose.

- Step 19 — swipe momentum — 2026-10-22 — **DONE, awaiting on-device verification**
  `input/DrawingView.kt`: VelocityTracker fed on every NAVIGATE event (obtained
  on startNavigate, recycled on UP/CANCEL). On UP after a pure 1-finger pan
  (`lastWasOneFingerPan`, only true when n==1 for two consecutive navigate
  calls) with velocity >= 300 px/s, a postOnAnimation fling loop pans by
  v*dt, decays 0.92^(dt*60), stops below 30 px/s. Any ACTION_DOWN, POINTER
  DOWN (startNavigate) or fling being active stops it instantly (touch
  cancels fling). Pinch-zoom (2-finger) never starts a fling; drawing mode
  unaffected. Build + tests pass.

## UI master plan (U1-U18) — first read 2026-10-26

From here on the UI_MASTER_PLAN.md Part 2 rules govern UI work (one step at a time,
STOP after each, and git commit + git push after every step).

CURRENT STEP: F0 - Audit and plan (UI_FIXES_AND_BRUSHES_PLAN.md) - DONE (report delivered; nothing but docs changed, waiting for owner approval)
NEXT ACTION: on "go" begin Step F1 (fix the stroke cut-off: long lines stop drawing).

- Step U1 - Theme and full-screen immersive mode — WAITING FOR OWNER TEST
  (ui/theme/Color.kt + Theme.kt with the design tokens; edge-to-edge + hidden
  system bars in MainActivity; debug text gated behind vm.showDebug, default off.
  Agent-verified on device: no status/nav bar, edge-to-edge canvas, no debug
  text, toolbar intact. Transitional quirk: toolbar text now renders light on
  the white canvas (dark theme text color) — fixed in U4/U5 when the toolbar
  becomes dark floating panels.)
- Step U2 - Icon set (no icon library) — DONE (agent-verified)
  (ui/components/UiIcon.kt: 22 icons hand-drawn on a 24-unit grid per the plan's
  coordinates — PEN MARKER AIRBRUSH PENCIL CHARCOAL ERASER SELECT UNDO REDO
  LAYERS EYE EYE_OFF LOCK UNLOCK MENU CHEVRON_LEFT CHEVRON_RIGHT PLUS TRASH
  CLOSE SETTINGS IMAGE. All 22 drawn at once in a temporary row on the tablet:
  no crash, none malformed (vision-checked); row then removed per the plan.
  contentDescription is attached via semantics when non-null.)
- Step U3 - Reusable components — DONE (build-verified)
  (ui/components/PanelSurface.kt, ToolButton.kt, GradientSlider.kt, SectionTitle.kt
  per the plan's exact specs: 20dp corners + 12dp shadow + outline border panel;
  48dp ToolButton with accentSoft/accent selected state and bounded ripple;
  GradientSlider 28dp bar + white-ringed 24dp thumb with tap and horizontal-drag
  input via rememberUpdatedState; SectionTitle 12sp uppercase textSecondary.)
- Step U4 - The floating tool panel (drag, snap, collapse, flyout) — DONE (agent-verified)
  (ui/components/FloatingToolPanel.kt + ViewModel state panelFracX/Y/collapsed
  persisted in SharedPreferences("ui") + flyoutOpen (not persisted; closes when a
  stroke begins via beginStroke). Panel: drag handle pill, 7 ToolButtons in a
  PanelSurface (PEN MARKER AIRBRUSH PENCIL CHARCOAL ERASER SELECT; tap selected
  toggles flyout), 40x1dp divider, 36dp color circle -> color panel, size dot
  (6-40dp), collapse chevron toward the nearest edge. Drag math exactly per the
  plan (fractions of free space, spring snap to the nearest edge on release,
  rememberUpdatedState in the drag lambda). Flyout: 240dp PanelSurface opposite
  the snapped side with Size slider + live preview circle + Pressure switch for
  PEN/PENCIL/CHARCOAL. Old left toolbar + size slider removed from MainScreen
  (root is now BoxWithConstraints). Agent-verified on the tablet: panel renders
  as a narrow 64dp floating panel, vertical drag position matches the math
  exactly (fracY 0.72 -> y=873), drag past center snaps RIGHT (x1290-1413),
  sub-center drags snap LEFT, position + state survive force-stop/relaunch,
  flyout opens (SIZE + Pressure nodes in the UI dump), collapse leaves one
  button, expand restores, zero crashes. Fixed during testing: the divider used
  fillMaxWidth which stretched the whole panel to screen width -> 40dp fixed.)
- Step U5 - Top action pill and menu — DONE (agent-verified)
  (ui/components/TopActionPill.kt: PanelSurface Row at TopCenter with UNDO
  (38% alpha + no ripple when disabled), REDO (same with canRedo), LAYERS
  (highlights while the layers panel is open), MENU with a dark DropdownMenu
  holding the EXISTING actions in world vocabulary: Insert image, Paper,
  Export picture, Export world file, Import world file. SKIPPED per the plan
  (features not built yet): the SETTINGS button (needs U9) and the My worlds /
  New world / Places menu items (need U12/U18). Engine: UndoStack gained an
  onChanged hook so the VM can hold canUndo/canRedo as reactive Compose state;
  ToolButton gained an enabled param (alpha + ripple off). The old letter-label
  row was already removed in U4. Agent-verified on the tablet: pill renders
  top-center with dimmed UNDO/REDO on an empty stack (vision-confirmed); the
  Add Layer -> pill UNDO cycle removes the layer and the Layers panel
  recomposes; the menu opens with all 5 items; "Paper" opens the paper panel;
  no crashes. Bug found and fixed during the step: vm.undo()/redo() did not
  bump layersVersion, so the Layers panel showed stale rows after an undo.)
- Step U6 - Auto-hiding chrome, zoom indicator, first-run hint — DONE (partial agent-verify)
  (ViewModel: uiVisible + 600ms show-timer (beginStroke hides + cancels,
  endStroke/cancelStroke restart); hintVisible + dismissHint persisted via
  SharedPreferences("ui") "hintShown"; cameraTick bumped by DrawingView on every
  navigate/fling frame. Engine: depth() added. ui/components/ZoomChip.kt:
  BottomCenter 16dp PanelSurface pill "Depth n - x<mag>" (mag < 1000 as %.1f,
  else %.1e normalized to 1.2e6), shows on depth/zoomBucket change (bucket =
  log10(zoomInRoot)*20), fades 1.5s after the last change, suppresses the
  launch-time show, hides while drawing. ui/components/FirstRunHint.kt:
  centered card "Pen draws / One finger pans / Two fingers zoom", dismisses on
  any tap (DrawingView ACTION_DOWN + card click) or 6s. MainScreen: tool panel,
  flyout, top pill and right panels all wrapped in AnimatedVisibility
  (fadeIn/fadeOut 150ms) — hidden chrome leaves composition so it can never
  swallow a touch. Agent-verified on the tablet: hint renders with the exact
  three lines, disappears on tap, never returns after relaunch; chrome + panel
  interactions still work through the visibility wrap (flyout opens/closes).
  NOT adb-testable (need stylus/pinch): controls fading during a pen stroke +
  returning 0.6s later, and the ZoomChip appearing during a pinch — those are
  the owner's MY TEST items. Zero crashes.)
- Step U7 - Color panel redesign — DONE (agent-verified)
  (ui/panels/ColorPickerPanel.kt fully rebuilt: own 300dp PanelSurface; 56dp
  rounded preview over an 8dp two-grey checkerboard (transparency visible) +
  live hex (#RRGGBB, with % when opacity < 100) + CLOSE icon top-right; four
  GradientSliders with SectionTitles: Hue (7 rainbow stops), Saturation
  (white -> hue), Brightness (black -> hue), Opacity (transparent -> color);
  Recent (vm.recentColors, hidden while empty) + Basics (8 presets) as 32dp
  circles, 6dp gaps, 2dp accent ring on the selected. vm.color is now
  reactive (mutableStateOf) so the tool panel's color circle and the flyout
  preview update live. The panel is positioned beside the floating tool panel
  with the same math as the flyout (opposite side from the snapped edge),
  rendered from FloatingToolPanel(colorPanelOpen, onCloseColor); opening it
  closes the flyout; the old right-side Card no longer hosts COLOR. Agent-
  verified on the tablet: panel renders exactly per spec (vision-confirmed
  gradients + checkerboard), tapping the blue Basics swatch moved the hex
  #000000 -> #2196F3, dragging the hue slider moved it to #7821F3, the tool
  panel's color circle and preview all matched the live color, and the X
  closed the panel cleanly. Recent filling in as you draw needs the pen
  (mechanism unchanged from the old panel: endStroke). Zero crashes.)
- Step U8 - Layers panel redesign — DONE (agent-verified)
  (ui/panels/LayersPanel.kt rebuilt: own 300dp PanelSurface, rows area capped
  at 60% of screen height and scrollable; 56dp rows with eye + lock
  ToolButtons, name (tap = active, long-press = rename dialog) with a 2dp
  accent outline + accentSoft background on the active row, an opacity %
  readout on the right that expands one row at a time into the opacity slider
  + blend dropdown; footer ToolButtons: PLUS add, TRASH delete (confirm
  dialog only when the active layer has strokes), CHEVRON_RIGHT rotated
  -90/+90 for move up/down. MainScreen renders LAYERS outside the old Card.
  THREE real bugs found and fixed during on-device testing:
  (1) PRE-EXISTING engine bug: DrawingViewModel's layer-id counter was
  hard-coded and reset on relaunch, so addLayer() after a restart re-issued
  an existing id (project.json got two layers with id 2 — the panel dropped a
  row and both rows looked active). Fixed by deriving ids from the project
  itself (Project.nextLayerId(), model layer) + LayerIdTest regression test
  (JVM); the corrupted on-disk project was repaired (duplicate layer removed
  via sed; the owner's strokes/blend state kept).
  (2) The spec's LazyColumn misbehaved on this device: asReversed() made it
  drop rows (fixed with .reversed()), and even with id keys or
  version-suffixed keys it never re-flowed rows on reorder (moves worked in
  the engine but the list showed the old order until relaunch). Replaced with
  a verticalScroll Column + key(layersVersion) full rebuild per edit — the
  old panel's proven pattern; live reorder/move verified working.
  (3) The opacity slider drag was killed by that same rebuild: every drag
  frame disposed the Slider mid-gesture (thumb jumped to the press point,
  then froze). setLayerOpacity() no longer bumpLayers() during a drag — the
  canvas still updates live via redraw, the % readout + undoable command
  land in commitLayerOpacity() on release. Drag verified working end to end.
  Agent-verified on the tablet: active-row highlight follows taps, eye/lock
  toggles update live, ADD gives a unique id and appears at the top live,
  TRASH deletes an empty layer immediately, TRASH on a strokes layer shows
  the "Delete layer? / This layer has strokes." dialog (Cancel preserves
  data), move up/down reorders the visible list live, long-press rename
  dialog works (typed name appears in the row live), blend dropdown offers
  all 6 modes, opacity drags smoothly. Device testing was done in landscape
  after the tablet auto-rotated mid-session (activity recreation reset panel
  state — not a bug). Owner's project restored to [Layer 1, Layer 2] with
  Normal blend and opacity 98% (was 23% before testing; strokes intact).
  Zero crashes.)
- Step U9 - Settings panel and canvas-first polish — DONE (agent-verified)
  (NEW ui/panels/SettingsPanel.kt: 300dp PanelSurface with SectionTitle +
  five 48dp-high switch rows — Finger draws, Pressure sensitivity, Auto-hide
  controls while drawing, Show zoom indicator, Show debug text — plus a
  Reset-tool-panel-position button (fracX=0, fracY=0.25, expanded). VM: those
  five became reactive + persisted in SharedPreferences("settings") with an
  init reload (fingerDraws/usePressure/showDebug moved into the block);
  showDebug's setter redraws the canvas; turning autoHideUi off cancels the
  show-timer and forces uiVisible=true; beginStroke hides chrome only when
  autoHideUi; MainScreen gates the ZoomChip on showZoomChip; panel state is
  rememberSaveable so an open panel survives rotation. TopActionPill gained
  the SETTINGS gear between LAYERS and MENU (icon already existed); PanelType
  gained SETTINGS (own PanelSurface slot, no Card). Polish checklist: color
  panel close button 32dp -> 48dp (rule 1); no text under 12sp (rule 2);
  panels are overlays so the canvas never moves (rule 3); panel dragged by
  its handle to the right edge live + left edge via Reset, on-screen in both
  orientations (rule 4); open Settings panel survived a forced rotation both
  ways and the tool panel kept its spot (rule 5, rememberSaveable + persisted
  fracs); Settings->Layers swap verified one-panel-at-a-time with the tool
  panel staying (rule 6); dialogs unchanged-dark (rule 7). Agent-verified on
  the tablet: switch defaults render correctly, Show debug text toggles the
  level/zoom/rot overlay on/off instantly, Finger draws ON makes a finger
  swipe lay down a real PEN stroke (seen in project.json) and OFF stops it,
  all five settings survived force-stop+relaunch, Reset jumped the tool panel
  right->left edge, the handle drag moved it back to the right edge. Forced
  rotation used settings put user_rotation (auto-rotate restored to 1 after).
  Pen-only owner tests: auto-hide-off while pen-drawing, ZoomChip switch
  during a pinch. Zero crashes.)
- Step U10 - S Pen extras (optional) — DONE (code-complete; pen behaviors owner-tested)
  (The plan's overrideTool design: DrawingView.startDraw sets
  vm.overrideTool = RERASER/ERASER when the pen's eraser end touches OR the S Pen
  side button (BUTTON_STYLUS_PRIMARY) is held at DOWN; beginStroke captures it
  into strokeTool and consumes it; the whole stroke lifecycle (beginStroke/
  addPoint/endStroke: SELECT checks, smoothing decision, raster dab vs erase
  branches, vector-stroke creation, pressure flags) now reads strokeTool, so
  the override applies for exactly one stroke; endStroke/cancelStroke reset
  strokeTool = tool and clear overrideTool. The old eraserMode/rasterEraserMode/
  preEraserTool mechanism (which visibly swapped vm.tool, making the panel
  show the eraser mid-stroke) was removed — the panel keeps showing the real
  selected tool per the plan. Hover (optional extra): DrawingView.onHoverEvent
  feeds vm.hover (screen px); CanvasRenderer.render draws a thin 2px circle
  outline (90-alpha gray) with diameter brushSizePx at the hover point. Agent-
  verified on the tablet: finger drawing regression through the new lifecycle
  (logcat-proven: beginStroke t=PEN layer=Layer 2 -> endStroke with 80 points
  committed), app stable, zero crashes. adb cannot inject stylus events, so
  the three pen behaviors are owner tests: (1) hold the S Pen side button and
  draw — that stroke erases and the tool strip still shows the pen; (2) flip
  the pen to its eraser end and draw — erases (raster eraser if a raster tool
  is active); (3) hover the pen above the glass — a thin circle the size of
  the brush follows the tip.)
- Step U11 - Places: naming levels, place card, breadcrumb — DONE (owner-tested 2026-10-30)
- Step U12 - Fly-to and history — NOT STARTED
- Step U13 - Search — NOT STARTED
- Step U14 - Minimap — NOT STARTED
- Step U15 - Orientation: compass and Home — NOT STARTED
- Step U16 - Procreate-style gestures and edge sliders — NOT STARTED
- Step U17 - Zoom ergonomics — NOT STARTED
- Step U18 - Gallery: "My worlds" — NOT STARTED

## UI fixes and brushes plan (F0-F9) — added 2026-10-30

Governed by UI_FIXES_AND_BRUSHES_PLAN.md (self-contained). One step at a time;
after EVERY step: update this file, then git commit AND git push.

- Step F0 - Audit and plan (no code changes) — DONE 2026-10-30 (plan-only; no
  app code touched; waiting for owner approval to start F1)
- Step F1 - Fix the stroke cut-off (long lines stop drawing) — NOT STARTED
- Step F2 - Rewrite the eraser — NOT STARTED
- Step F3 - Top pill order — NOT STARTED
- Step F4 - Color wheel — NOT STARTED
- Step F5 - Brush model, registry, storage and migration — NOT STARTED
- Step F6 - One Brush button and the brush picker — NOT STARTED
- Step F7 - Apply the brush properties (renderer and input) — NOT STARTED
- Step F8 - Shared brush settings panel — NOT STARTED
- Step F9 - Final checks and cleanup — NOT STARTED

After F9, resume UI_MASTER_PLAN.md at Step U12.

Prerequisite check (2026-10-26): core app built AND verified on-device — the
on-device test session above is exactly that verification. All numbered core
features the UI steps reference (panels, undo, layers, paper, images,
persistence, PNG export, momentum, smoothing, select, pencil/charcoal, world
files, rotation) exist and pass.

## Steps 19-24 (added 2026-10-22) — ALL DONE

Order was fixed as listed; every entry below is finished. One step at a time,
standard report format, PLAN.md section 0 rules applied throughout.

- Step 19 - Swipe momentum — DONE 2026-10-22
  (VelocityTracker in DrawingView NAVIGATE; fling > 300 px/s on UP,
  postOnAnimation loop, decay 0.92^(dt*60), stop < 30 px/s, touch cancels
  instantly, no momentum for pinch-zoom or drawing.)
- Step 20 - Stroke smoothing and pressure curve — DONE 2026-10-23
  (details in the Finished steps entry above; commit `4132f63`)
- Step 21 - Select, move and scale — DONE 2026-10-23
  (details in the Finished steps entry above; decisions in DECISIONS.md)
- Step 22 - Pencil and charcoal brushes — DONE 2026-10-23 (overrode the
  BrushType rule for this step only, per spec)
  (details in the Finished steps entry above; decisions in DECISIONS.md)
- Step 23 - Project export and import (.uzw) — DONE 2026-10-23
  (details in the Finished steps entry above; decisions in DECISIONS.md)
- Step 24 - Rotation — DONE 2026-10-23
  (details in the Finished steps entry above; decisions in DECISIONS.md)

After EACH of these steps: update PROGRESS.md, report in the standard
format, stop and wait for "go".

## Step in progress

- (none — Step 24 finished 2026-10-23. ALL planned steps are now implemented:
  Steps 1-18, Phase 2, and Steps 19-24. Every step from 19 on is awaiting
  on-device verification. Any further work needs new user direction
  (bug reports, tuning feedback, or new features).

## Next steps / Context (resume cold)

- Toolbar positioning follow-up (`7fb3c1e`): column is now anchored
  `Alignment.TopStart` with `padding(start = 12.dp, top = 96.dp)` — sits just
  under the debug level/zoom text (drawn at y=60 with 40px font) and no longer
  vertically centered, so it can't cover the center-left size slider. If the
  column still reaches the slider on short screens, move the slider to
  bottom-start.
- Toolbar rework `0830f56` touched only `MainActivity.kt`: Column at
  `Alignment.CenterStart`, `padding(start = 12.dp, top = 110.dp)` — the 110.dp
  offset must clear the 40px debug text; if the debug overlay is removed later,
  reduce it. `toolbarButton(label, onClick)` helper defined above MainScreen.
- If the left strip's size slider (still at CenterStart) overlaps the long
  button column on narrow screens, move the slider to bottom-start.
- Bug-fix commit `1083b4c`: files `ui/DrawingViewModel.kt` (visible/locked
  guard in beginStroke; `preEraserTool` + reworked eraser setters; tool is
  now `by androidx.compose.runtime.mutableStateOf` — getValue/setValue
  imports added), `input/DrawingView.kt` (startDraw picks raster vs vector
  eraser; imports `com.example.infinitedraw.ui.isRaster`),
  `render/CanvasRenderer.kt` (grid loop restructured).
- Note: DrawingView calls vm.beginStroke but has no way to show the plan's
  "Toast when drawing is ignored" — the guard silently no-ops instead. If the
  user reports "can't draw", first check active layer visibility/lock.
  c281e32 (polish progress), 721a48b (LRU cap), 1d46099 (2b progress),
  ea964c1 (2b undo), 24e559e (2a progress), 4056d6d (2a), f215b46/e2defeb
  (Step 18), 71adc27/c371ac0 (Step 7-16 bundle).
- Outstanding on-device items (user): all Step 18 manual checks; Phase 2
  tests (OIL/MIX/SMUDGE/RERASER painting, undo/redo of raster dabs, >64-tile
  eviction, save/reopen tile restore); mix/smudge strength tuning feedback.
- Known technical edge cases (documented, low priority): undo restore onto an
  evicted tile is a no-op if the tile was evicted between stroke and undo;
  PNG export writes to getExternalFilesDir/Pictures (SAF deviation); no
  project-picker UI exists (ProjectStorage.listProjects unused).
- If the user says "go" with no other context, ask what they want next — the
  plan has no remaining steps.

## Verification (this audit)

- `gradlew assembleDebug` → BUILD SUCCESSFUL
- `gradlew test` → BUILD SUCCESSFUL (EngineTest passes)
- `BrushType` = PEN, MARKER, AIRBRUSH ✔
- `drawStroke` = ribbon version ✔ (no fix applied)

## File-by-file plan status

| File                            | Status                          |
| ------------------------------- | ------------------------------- |
| MainActivity.kt                 | in plan (Steps 11, 16, 17)      |
| DrawingViewModel.kt             | in plan (Steps 8-10, 16)        |
| CanvasRenderer.kt               | in plan (Steps 6, 7, 10, 14)    |
| engine/Commands.kt              | in plan (Step 9)                |
| storage/ProjectStorage.kt       | in plan (Step 16)               |
| engine/InfiniteZoomEngine.kt    | in plan (Step 4)                |
| model/Models.kt                 | in plan (Step 3)                |
| model/Project.kt                | in plan (Step 9)                |
| input/DrawingView.kt            | in plan (Step 5)                |
| ui/panels/BrushSettingsPanel.kt | in plan (Step 13)               |
| ui/panels/ColorPickerPanel.kt   | in plan (Step 12)               |
| ui/panels/LayersPanel.kt        | in plan (Step 13)               |
| ui/panels/PaperPanel.kt         | in plan (Step 14)               |
