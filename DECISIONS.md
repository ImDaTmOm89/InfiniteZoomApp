# DECISIONS.md

Ambiguities resolved by the agent, per PLAN.md section 0 rule 5 ("pick the
simplest option, log it in DECISIONS.md, continue").

## Step 21 - Select, move and scale (2026-10-23)

- The uncommitted "Before Hermes" work (commit `058eee5`) contained TWO
  conflicting copies of the Step-21 selection code in DrawingViewModel.kt and a
  ScaleSelection constructor call that did not match the command's signature —
  it could not compile. Consolidated into one implementation; the Commands.kt
  half was kept as-is except lateinit -> nullable hardening in
  MoveStrokes/MoveImage reverts.
- MOVE vs PICK: pressing INSIDE the selected item's bounds (plus an 8px/zoom
  margin) starts a MOVE; any other press is a PICK attempt, which only selects
  if the pen moved < 10 px total (a real tap); tapping empty space deselects.
  Pressing an unselected item picks it but does NOT move it in the same
  gesture. A press on the selection that moves < 3 px commits nothing.
- Scale slider (25-400%): no live geometry preview while dragging the slider —
  the % label is the feedback. One ScaleSelection command per slider release,
  then the slider snaps back to 100%. Each release compounds around the item's
  CURRENT bounds center and is a single undo step.
- The dashed selection outline is a dashed RECTANGLE around the item's bounds
  (not a ribbon-shaped outline for strokes) — simpler, and identical for
  strokes and images. Blue #2196F3, 2 px, dash 14/8. Only drawn while SELECT
  is the active tool (a lingering selection is hidden, not cleared, when the
  user switches tools).
- The Selection panel auto-opens when something is picked (replacing whatever
  panel was open) and auto-closes when the selection is cleared.
- SELECT is a "drawing" gesture like the pens: stylus selects, fingers still
  navigate; with "Finger: On" a finger selects too and a second finger cancels
  the gesture. Selection is bound to its level: moving requires the camera to
  be on that level; picking always tests the current level.
- Single-item selection only (the spec's "strokeId or imageId"); MoveStrokes
  is named plural for possible future multi-select but moves one id.
- Raster tile content cannot be selected/moved/scaled — Step 21 covers vector
  strokes and placed images only.

## Step 22 - Pencil and charcoal brushes (2026-10-23)

- This step overrides the plan's "BrushType is exactly PEN/MARKER/AIRBRUSH"
  rule for itself (stated in the step spec): BrushType is now
  PEN/MARKER/AIRBRUSH/PENCIL/CHARCOAL. The eraser remains a Tool, never a
  BrushType.
- "spacing 0.35*rPx" is computed in LEVEL-LOCAL units (0.35 x the local,
  pressure-interpolated dab radius), not screen pixels. Local-space spacing is
  the only reading compatible with "grain stable while pan/zoom": the dab set
  is fixed by stroke geometry, so zooming cannot re-shuffle the grain.
- Deterministic hash: dabHash(strokeId, dabIndex) is a pure top-level function
  (murmur-style Int finalizer). No Random, no image assets; the pattern is
  identical every frame and across app runs. Off-screen dabs still consume an
  index so panning cannot shift the pattern.
- 3000-dab cap per stroke: the walk simply stops stamping, so the tail of a
  pathological stroke renders unpainted. At normal sizes this is unreachable.
- A dab is stamped at the start point and at the pen-lift end point so the
  stroke ends are always covered; remaining arclength carries across segment
  boundaries.
- Pressure applies to PENCIL and CHARCOAL (dab radius), honoring the
  Pressure-sensitive switch like PEN; the Step-20 stabilizer/smoothing
  applies to them as vector tools.
- Buttons: a second row in the Brush panel (Pencil / Charcoal boxes). The
  step spec named no placement; the Brush panel is the vector-tool selector.
- Pencil/charcoal size/alpha/jitter constants are first-pass values — expect
  on-device tuning like the raster brush strengths.
- Cull margins widened by 0.6*width*zoom for these brushes because jittered
  dabs reach ~0.5 radius outside the bounds' padding.

## Step 23 - Project export and import (.uzw) (2026-10-23)

- OpenDocument launches with mime arrayOf("*/*"): ".uzw" has no registered
  MIME type, and filtering to zip/octet-stream hides real files in some
  pickers. CreateDocument uses "application/zip" with suggested name
  "<project>.uzw".
- Import folder name derives from the SAF DISPLAY_NAME (extension stripped,
  path-unsafe chars replaced by "_"), deduped with " (2)", " (3)"...; falls
  back to "Imported project".
- After unzip, project.json still carries the ORIGINAL project name, and
  load() would restore it into project.name — the next autosave would then
  write into the original project's folder and merge the two. So import
  sets project.name to the new deduped folder and flushes immediately; the
  flush also fixes the last-project pref (load() had written the DTO name).
- Project switch happens on the MAIN thread (the renderer reads engine/
  project there, like the startup load); unzip runs on Dispatchers.IO.
  Undo stack, selection and pendingRemoved are cleared on switch (they
  referenced the old project).
- ProjectStorage.load gained an optional bitmaps map + loadImages():
  placed-image bitmaps were only ever populated by addImage, so placed
  images were invisible after an app restart (a pre-existing Step 16 gap —
  "kill and reopen: everything identical" never fully held for images).
  Startup and import now pass vm.bitmaps. Fixed here because import shares
  the load path; without it imported images would be invisible.
- Guards, per spec: zip-slip canonical-path prefix check on every entry;
  more than 10,000 entries or 500 MB uncompressed aborts the import; a
  missing project.json rejects the archive as "not an app project"; every
  failure deletes the partial folder and surfaces a Toast. No crash paths.
- Export zips the whole projects/<name>/ folder as-is (project.json,
  images/, tiles/) after an autosave.flushNow, so the archive matches the
  on-screen state.

## Step 24 - Rotation (2026-10-23)

- The step spec said "per given formulas", but no formulas were ever recorded
  in the repo (PLAN.md ends at Step 18). They were re-derived from first
  principles and are captured in code comments + RotationTest:
  world -> screen: [sx, sy] = center + R(theta) * (world - camera) * zoom,
  with R(theta) = [[cos, -sin], [sin, cos]]; screenToLocal applies R(-theta).
- zoomAt became a thin wrapper over the new transformAt(factor, delta, sx, sy)
  so pinch and twist land in ONE anchored operation (the world point under the
  anchor stays fixed for both at once). All existing zoomAt call sites still
  work unchanged.
- transformAt wraps rotation to [-pi, pi) after every step, so endless twist
  streams never lose floating-point precision.
- Twist gesture: the angle between the two fingers (atan2), delta-wrapped to
  [-pi, pi], applied with the pinch factor, anchored at the finger midpoint.
  A single finger never rotates. The engine call is skipped when factor == 1
  and delta == 0 to avoid churn on plain two-finger pans.
- Culling: rect culls are geometrically wrong under rotation. Everything now
  circle-culls: project the bounds center via worldToScreen and compare against
  a radius = half-diagonal * zoom (strokes keep the pencil/charcoal jitter
  pad; images and raster tiles use their own half-diagonals). The separate
  drawLevelContent pre-cull was removed — drawStroke already culls once.
- Paper grid: canvas.rotate(deg(theta)) about the screen center, then the
  ORIGINAL unrotated grid formulas, with line/dot ranges extended by the screen
  diagonal so the rotated grid still covers the viewport corners. The grid
  stays anchored to world x=0 / y=0 and rotates with the camera.
- Images and raster tiles: canvas.rotate about the item's own screen center,
  drawn in the unrotated dst rect (per the step spec: "canvas.rotate").
- Selection outline: a dashed quad through the 4 rotated bounds corners
  (bounds rect no longer maps to a screen rect). Its per-level cull was
  dropped — a single path is negligible.
- Step 21's move preview (previewDx/Dy) is now derived from two
  screenToLocal calls instead of (screen delta)/zoom, so dragging a selection
  under rotation moves it in the direction the pen travels.
- Fling momentum works rotated with no change: panBy applies R(-theta), and
  the fling loop only calls panBy.
- CameraDto gained `rotation` with default 0.0: old project.json files load
  unrotated; new saves round-trip rotation. Camera-descend/ascend is
  unaffected (child frames are axis-aligned, so one theta serves all levels).

## On-device test session (2026-10-26)

- adb test harness: single-finger taps/swipes only — `input` cannot emulate a
  stylus or two pointers, so pinch-zoom, twist rotation and palm rejection stay
  manual. Rotation was incidentally confirmed (overlay rot=-15, coherent render).
- Conditional unlock: only swipe up when the keyguard actually holds focus. A
  swipe on an already-unlocked app pans the canvas — several batches silently
  panned the camera ~1500 units each before this was diagnosed via temporary
  SELECTDBG logs (the Select "failures" were taps on blank paper far away).
- Evidence: screenshot byte/PIL pixel diffs, uiautomator dumps, logcat crash
  buffer, run-as file reads. Three crash-class bugs (raster offset, toast-on-IO,
  immutable tiles) were all caught in logcat -b crash.
- Samsung quirks: a stale freeform pop-up task from a previous session corrupted
  windowing (black screens, off-screen window); fixed by reboot + relaunch.
  "Frecess" repeatedly killed the app once the screen slept; mitigated with the
  doze whitelist + wake-before-batch, and the camera/layer autosave now protects
  state across kills.
- DocumentsUI appends ".zip" to CreateDocument("application/zip") saves, so
  exports land as "<name>.uzw.zip"; importing one derives the folder name
  "Untitled.uzw" (double extension — only the last is stripped). Cosmetic; a
  follow-up could use application/octet-stream as the .uzw MIME.
- Pre-testing device data is backed up at
  C:\Users\tgizz\AppData\Local\hermes\cache\scratch\infinitedraw_data.tar
  (projects/Untitled + 2 image PNGs). The device project now also contains the
  test strokes/layers from this session; restore from the tar or delete the test
  Layer 2 to clean up. Test artifacts on the tablet: Download/Untitled.uzw.zip,
  the app-external Pictures/export_*.png, and imported projects
  "Untitled.uzw" + "Untitled.uzw (2)".

## Known limitations (carried over from PLAN.md)

- Strokes with screen coordinates beyond ±100000 are clamped, so huge strokes
  can look slightly bent at extreme close-ups.
- Layer order is guaranteed only within one zoom level; deeper levels always
  draw above shallower ones.
- The eraser only affects strokes on the current level.
- Images cannot be moved or resized in v1. (Partially superseded by Step 21:
  move + uniform scale via the Selection panel now exist; free resize handles
  do not.)
