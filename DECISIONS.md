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

## Known limitations (carried over from PLAN.md)

- Strokes with screen coordinates beyond ±100000 are clamped, so huge strokes
  can look slightly bent at extreme close-ups.
- Layer order is guaranteed only within one zoom level; deeper levels always
  draw above shallower ones.
- The eraser only affects strokes on the current level.
- Images cannot be moved or resized in v1. (Partially superseded by Step 21:
  move + uniform scale via the Selection panel now exist; free resize handles
  do not.)
