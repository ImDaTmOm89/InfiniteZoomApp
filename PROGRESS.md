# PROGRESS.md

Last updated: 2026-10-20 (Phase 2a committed)

## Known issues

- `PLAN.md` restored this session; the uncommitted work maps to Steps 7-17.
- No project-picker UI found calling `ProjectStorage.listProjects()` — either still
  missing or part of a later Menu item not yet wired.
- Step 18 on-device checklist items (palm rejection, pinch-spam, 3000 strokes,
  rotation) still need a manual run on the tablet.

## Finished steps
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

- (none) Steps 3-17 complete; Step 18 committed; Phase 2a (raster tiles)
  committed. Next: Phase 2b — undo/redo support for raster dabs, tile-only
  in-progress preview, tuning smudge strength, then user testing.

## Next steps / Context (resume cold)

- Phase 2a committed (`4056d6d`). Files: `render/RasterTiles.kt` (new, ~215
  lines: TileKey/Tile, tiles HashMap, dab/erase, mixDab/smudgeDab),
  `ui/DrawingViewModel.kt` (Tool enum now PEN/MARKER/AIRBRUSH/ERASER/OIL/MIX/
  SMUDGE/RERASER + `Tool.isRaster()` extension; rasterDab private fn; raster
  tools skip vector-stroke creation in endStroke), `render/CanvasRenderer.kt`
  (drawRasterTiles per level+layer inside drawLevelContent),
  `storage/ProjectStorage.kt` (saveTiles/loadTiles PNG round-trip,
  RasterTiles.clearAll on load), `MainActivity.kt` (O/M/S/RE toolbar buttons).
- Known limitation: raster dabs are NOT undoable (tiles bypass the UndoStack);
  mixing uses a simple lerp blend with coverage-weighted strength — may need
  tuning. Smudge on empty tiles is a no-op. Tiles are per-layer, so layer
  opacity/blend applies.
- Raster tiles look sharp near the zoom they were painted at (level-local px);
  zooming far out/in scales them (filterBitmap on).
- User-visible test: paint with O/M, drag S over wet colors to smudge, RE to
  erase raster paint only, save/reopen project, check tiles restore.
- Earlier context (still relevant): Step 18 manual on-device checks pending;
  export PNG writes to getExternalFilesDir/Pictures (deviation from SAF plan);
  no project-picker UI calls ProjectStorage.listProjects() found.
- Verified: `gradlew assembleDebug` BUILD SUCCESSFUL, `gradlew test` BUILD
  SUCCESSFUL.

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
