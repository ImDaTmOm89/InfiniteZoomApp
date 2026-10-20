# PROGRESS.md

Last updated: 2026-10-20 (bug-fix pass committed)

## Known issues

- `PLAN.md` restored this session; the uncommitted work maps to Steps 7-17.
- No project-picker UI found calling `ProjectStorage.listProjects()` — either still
  missing or part of a later Menu item not yet wired.
- Step 18 on-device checklist items (palm rejection, pinch-spam, 3000 strokes,
  rotation) still need a manual run on the tablet.

## Finished steps
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

## Step in progress

- (none). PLAN.md is fully complete (Steps 1-18 + Phase 2 including undo,
  memory cap, thread safety). No further steps are defined in plan.md. Any
  next work needs new user direction (e.g. smudge tuning results from the
  tablet, bug fixes, or a new feature request).

## Next steps / Context (resume cold)

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
