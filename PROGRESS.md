# PROGRESS.md

Last updated: 2026-10-20 (Step 18 committed)

## Known issues

- `PLAN.md` restored this session; the uncommitted work maps to Steps 7-17.
- No project-picker UI found calling `ProjectStorage.listProjects()` — either still
  missing or part of a later Menu item not yet wired.
- Step 18 on-device checklist items (palm rejection, pinch-spam, 3000 strokes,
  rotation) still need a manual run on the tablet.

## Finished steps
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

- (none) Steps 3-17 complete; Step 18 commit done 2026-10-20 (`e2defeb`).
  Remaining: the six manual on-device checks from PLAN.md Step 18, then Phase 2
  (raster brushes) is next.

## Next steps / Context (resume cold)

- Steps 3-17 are DONE and committed. Latest commit: `e2defeb` on master.
  Files: `model/Models.kt`, `model/Project.kt`, `engine/InfiniteZoomEngine.kt`,
  `engine/Commands.kt`, `storage/ProjectStorage.kt`, `render/CanvasRenderer.kt`,
  `input/DrawingView.kt`, `MainActivity.kt`, `ui/DrawingViewModel.kt`,
  `ui/panels/{ColorPickerPanel,BrushSettingsPanel,LayersPanel,PaperPanel}.kt`.
- Git history note: commit `c371ac0` (labelled "Step 7") actually bundled the
  Step 8-16 files (Commands, ProjectStorage, panels, PLAN.md); `e2defeb` finished
  it with Step 17 PNG export + tool-aware live stroke preview + Kotlin 2.0
  catch-underscore fix. Everything is now clean (`git status` empty except ignored).
- Verified: `gradlew assembleDebug` BUILD SUCCESSFUL, `gradlew test` BUILD
  SUCCESSFUL (EngineTest: testZoomIn + testZoomOut).
- Export PNG writes to `getExternalFilesDir(null)/Pictures/export_<ts>.png` with a
  Toast (deviations from plan: plan said CreateDocument SAF; simplest option chosen,
  log in DECISIONS.md).
- Known issue from audit: no project-picker UI calls `ProjectStorage.listProjects()`
  found; open-project dialog (Step 16 menu) may be missing — check MainActivity.
- Next: run the Step 18 manual checklist on the tablet, then decide whether to
  start Phase 2 (raster tile brushes).

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
