# PROGRESS.md

Last updated: 2026-10-23 (Step 24 done — all planned steps implemented)

## Finished steps
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
