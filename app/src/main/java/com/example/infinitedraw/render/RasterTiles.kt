package com.example.infinitedraw.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.example.infinitedraw.model.ZoomLevel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Phase 2: tile-based raster painting.
 *
 * Raster paint lives in 256x256 ARGB_8888 Bitmap tiles keyed by
 * (levelId, layerId, tileX, tileY). A tile is created lazily the first time a
 * dab lands in it, so tiles "exist only where painted". Tiles are painted in
 * LEVEL-LOCAL pixel coordinates (1 level unit = 1 tile pixel), so they look
 * sharp near the zoom level they were painted at, exactly as PLAN.md Phase 2
 * describes.
 *
 * Mixing: each dab blends the brush color into existing pixels with weight
 * proportional to existing coverage (wet paint feel).
 * Smudge: each dab picks up the average color under the brush, then paints
 * that color with low alpha, dragging colors along.
 */
object RasterTiles {
    const val TILE = 256
    private const val MIX_ALPHA = 0.65f      // oil/mix dab base alpha
    private const val SMUDGE_ALPHA = 0.30f   // smudge dab alpha
    private const val SMUDGE_PICK = 0.55f    // how strongly smudge picks up color

    class TileKey(val levelId: Int, val layerId: Long, val tx: Int, val ty: Int) {
        override fun equals(other: Any?) =
            other is TileKey && other.levelId == levelId && other.layerId == layerId &&
                other.tx == tx && other.ty == ty
        override fun hashCode() = (levelId * 31 + layerId.hashCode()) * 4096 + (tx and 0xFFF) * 64 + (ty and 0x3F)
    }

    class Tile(val key: TileKey, var bitmap: Bitmap, val canvas: Canvas) {
        var dirty = true
        /** Phase 2 polish: last time this tile was touched (drawn or painted), for LRU eviction. */
        var lastUse = System.currentTimeMillis()
    }

    /** All live tiles; key -> tile. Held by the ViewModel. */
    val tiles = HashMap<TileKey, Tile>()

    fun tileFor(key: TileKey): Tile =
        tiles.getOrPut(key) {
            val bmp = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
            Tile(key, bmp, Canvas(bmp))
        }.also { it.lastUse = System.currentTimeMillis() }

    /** Touch a tile on draw so LRU eviction keeps recently-seen tiles. */
    fun touch(t: Tile) { t.lastUse = System.currentTimeMillis() }

    /** Tiles of a given level+layer that intersect the given level-local rect. */
    fun tilesInRect(levelId: Int, layerId: Long, x0: Float, y0: Float, x1: Float, y1: Float): List<Tile> {
        val out = ArrayList<Tile>()
        val tx0 = Math.floor((min(x0, x1) / TILE).toDouble()).toInt()
        val tx1 = Math.floor((max(x0, x1) / TILE).toDouble()).toInt()
        val ty0 = Math.floor((min(y0, y1) / TILE).toDouble()).toInt()
        val ty1 = Math.floor((max(y0, y1) / TILE).toDouble()).toInt()
        for ((k, t) in tiles) {
            if (k.levelId != levelId || k.layerId != layerId) continue
            if (k.tx in tx0..tx1 && k.ty in ty0..ty1) out.add(t)
        }
        return out
    }

    /** Remove every tile belonging to a level (e.g. after a level is deleted/reset). */
    fun clearLevel(levelId: Int) {
        tiles.entries.removeAll { it.key.levelId == levelId }
    }

    fun clearAll() = tiles.clear()

    /**
     * Phase 2 polish: keep the in-memory tile cache bounded. Tiles are 256 KB
     * each (256x256 ARGB_8888); cap at 64 tiles (~16 MB). Only NON-DIRTY tiles
     * (already flushed to PNG by a save) may be evicted — they are reloaded
     * from disk on demand via loadTile(). Returns number of tiles evicted.
     */
    const val MAX_TILES = 64
    /** Keys of tiles evicted from memory (still on disk as PNGs). */
    private val evictedKeys = HashSet<TileKey>()
    fun evict(): Int {
        if (tiles.size <= MAX_TILES) return 0
        val sorted = tiles.values.sortedBy { it.lastUse }
        var evicted = 0
        for (t in sorted) {
            if (tiles.size <= MAX_TILES) break
            if (t.dirty) continue   // never evict unsaved paint
            t.bitmap.recycle()
            tiles.remove(t.key)
            evictedKeys.add(t.key)
            evicted++
        }
        return evicted
    }

    /**
     * Reload a single tile from its project PNG (called by the renderer when a
     * tile was evicted but is visible again). Returns null if no saved PNG exists.
     */
    fun loadTile(ctx: android.content.Context, projectName: String, key: TileKey): Tile? {
        val dir = java.io.File(
            com.example.infinitedraw.storage.ProjectStorage.projectDir(ctx, projectName), "tiles")
        val f = java.io.File(dir, "${key.levelId}_${key.layerId}_${key.tx}_${key.ty}.png")
        if (!f.exists()) return null
        val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: return null
        if (bmp.width != TILE || bmp.height != TILE) { bmp.recycle(); return null }
        val t = tileFor(key)
        t.canvas.setBitmap(bmp)
        t.bitmap = bmp
        t.dirty = false
        evictedKeys.remove(key)
        return t
    }

    /** True if this tile was evicted (may exist on disk as a PNG). */
    fun isEvicted(key: TileKey) = key in evictedKeys

    // ---- undo/redo snapshots (Phase 2b) --------------------------------------

    /** Snapshot of the touched region of a tile. Taken before dab/erase, used by RasterCommand. */
    class TileSnapshot(val key: TileKey, val cx: Float, val cy: Float, val r: Float,
                       val x0: Int, val y0: Int, val w: Int, val h: Int, val pixels: IntArray) {
        fun restore(t: Tile) {
            t.bitmap.setPixels(pixels, 0, w, x0, y0, w, h)
            t.dirty = true
        }
    }

    /**
     * Capture the affected rect of a tile as pixels. Rect is the level-local
     * circle bounding box (cx, cy, r) in tile-local coords; clipped to the tile.
     */
    fun snapshot(key: TileKey, cx: Float, cy: Float, r: Float): TileSnapshot? {
        val t = tiles[key] ?: return null
        val x0 = max(0, (cx - r).toInt()); val y0 = max(0, (cy - r).toInt())
        val x1 = min(TILE - 1, (cx + r).toInt()); val y1 = min(TILE - 1, (cy + r).toInt())
        if (x1 < x0 || y1 < y0) return null
        val w = x1 - x0 + 1; val h = y1 - y0 + 1
        val px = IntArray(w * h)
        t.bitmap.getPixels(px, x0, w, x0, y0, w, h)
        return TileSnapshot(key, cx, cy, r, x0, y0, w, h, px)
    }

    /** Restore a list of snapshots (used by RasterCommand apply/revert). */
    fun restore(snaps: List<TileSnapshot>) {
        for (s in snaps) tiles[s.key]?.let { s.restore(it) }
    }

    // ---- dab stamping -------------------------------------------------------

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Stamp one raster dab at level-local (lx, ly) with radius r (level-local
     * units). mode: 0 = oil/mix, 1 = smudge. `before` collects pre-dab
     * snapshots for undo. `stamp` controls whether the dab is actually applied
     * (false = capture only, used to snapshot freshly-created tiles).
     */
    fun dab(level: ZoomLevel, layerId: Long, lx: Float, ly: Float, r: Float, color: Int, mode: Int,
            before: MutableList<TileSnapshot>? = null) {
        if (r <= 0f) return
        val d = r * 2f
        val txMin = Math.floor((lx - r) / TILE.toDouble()).toInt()
        val txMax = Math.floor((lx + r) / TILE.toDouble()).toInt()
        val tyMin = Math.floor((ly - r) / TILE.toDouble()).toInt()
        val tyMax = Math.floor((ly + r) / TILE.toDouble()).toInt()
        for (tx in txMin..txMax) {
            for (ty in tyMin..tyMax) {
                val key = TileKey(level.id, layerId, tx, ty)
                if (before != null && tiles[key] == null) {
                    // Fresh tile: full-tile empty snapshot so undo can drop it back to empty.
                    tileFor(key)
                    before.add(TileSnapshot(key, 0f, 0f, TILE.toFloat(), 0, 0, TILE, TILE, IntArray(TILE * TILE)))
                    if (mode == 0) {
                        mixDab(tiles[key]!!, lx - tx * TILE, ly - ty * TILE, r, color)
                        tiles[key]!!.dirty = true
                    } else {
                        // smudge on a fresh tile is a no-op, tile stays empty
                    }
                    continue
                }
                val t = tileFor(key)
                if (before != null) snapshot(key, lx - tx * TILE, ly - ty * TILE, r)?.let { before.add(it) }
                val ox = tx * TILE.toFloat()
                val oy = ty * TILE.toFloat()
                if (mode == 1) smudgeDab(t, lx - ox, ly - oy, r)
                else mixDab(t, lx - ox, ly - oy, r, color)
                t.dirty = true
            }
        }
    }

    /** Oil/mix dab: blend brush color into existing pixels, weighted by coverage. */
    private fun mixDab(t: Tile, cx: Float, cy: Float, r: Float, color: Int) {
        val x0 = max(0, (cx - r).toInt()); val x1 = min(TILE - 1, (cx + r).toInt())
        val y0 = max(0, (cy - r).toInt()); val y1 = min(TILE - 1, (cy + r).toInt())
        if (x1 < x0 || y1 < y0) return
        val w = x1 - x0 + 1; val h = y1 - y0 + 1
        val px = IntArray(w * h)
        t.bitmap.getPixels(px, x0, w, x0, y0, w, h)
        val br = (color shr 16) and 0xFF; val bg = (color shr 8) and 0xFF; val bb = color and 0xFF
        val ba = (color ushr 24) and 0xFF
        for (j in 0 until h) {
            val dy = y0 + j - cy
            for (i in 0 until w) {
                val dx = x0 + i - cx
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist > r) continue
                val fall = (1f - dist / r).coerceIn(0f, 1f)   // soft edge
                val src = px[j * w + i]
                val sa = (src ushr 24) and 0xFF
                // target alpha: brush alpha scaled by radial falloff
                val ta = ((MIX_ALPHA * 255 * fall).toInt() * ba / 255).coerceAtMost(255)
                // "mixing": the more paint already there, the stronger the blend
                val cov = sa / 255f
                val mix = (0.35f + 0.65f * cov).coerceIn(0f, 1f)
                val outA = max(ta, sa)
                if (outA == 0) { px[j * w + i] = 0; continue }
                val sr = (src shr 16) and 0xFF; val sg = (src shr 8) and 0xFF; val sb = src and 0xFF
                val k = (mix * ta / outA).coerceIn(0f, 1f)
                // straight lerp between existing pixel color and brush color
                val nr = (sr + (br - sr) * k).toInt().coerceIn(0, 255)
                val ng = (sg + (bg - sg) * k).toInt().coerceIn(0, 255)
                val nb = (sb + (bb - sb) * k).toInt().coerceIn(0, 255)
                px[j * w + i] = (outA shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        t.bitmap.setPixels(px, x0, w, x0, y0, w, h)
    }

    /** Smudge dab: average the color under the brush, then drag it with low alpha. */
    private fun smudgeDab(t: Tile, cx: Float, cy: Float, r: Float) {
        val x0 = max(0, (cx - r).toInt()); val x1 = min(TILE - 1, (cx + r).toInt())
        val y0 = max(0, (cy - r).toInt()); val y1 = min(TILE - 1, (cy + r).toInt())
        if (x1 < x0 || y1 < y0) return
        val w = x1 - x0 + 1; val h = y1 - y0 + 1
        val px = IntArray(w * h)
        t.bitmap.getPixels(px, x0, w, x0, y0, w, h)
        // average existing color weighted by alpha (skip fully empty pixels)
        var sr = 0L; var sg = 0L; var sb = 0L; var sw = 0L
        for (v in px) {
            val a = (v ushr 24) and 0xFF
            if (a == 0) continue
            sr += ((v shr 16) and 0xFF) * a
            sg += ((v shr 8) and 0xFF) * a
            sb += (v and 0xFF) * a
            sw += a
        }
        if (sw == 0L) return   // nothing to smudge on empty paper
        val ar = (sr / sw).toInt(); val ag = (sg / sw).toInt(); val ab = (sb / sw).toInt()
        val pick = (SMUDGE_PICK * 255).toInt()
        val pickCol = (pick shl 24) or (ar shl 16) or (ag shl 8) or ab
        for (j in 0 until h) {
            val dy = y0 + j - cy
            for (i in 0 until w) {
                val dx = x0 + i - cx
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist > r) continue
                val fall = (1f - dist / r).coerceIn(0f, 1f)
                val src = px[j * w + i]
                val sa = (src ushr 24) and 0xFF
                val dabA = (SMUDGE_ALPHA * 255 * fall).toInt()
                val outA = max(dabA, sa)
                if (outA == 0) { px[j * w + i] = 0; continue }
                val k = dabA.toFloat() / outA
                val nr = (ar + (((src shr 16) and 0xFF) - ar) * (1 - k)).toInt().coerceIn(0, 255)
                val ng = (ag + (((src shr 8) and 0xFF) - ag) * (1 - k)).toInt().coerceIn(0, 255)
                val nb = (ab + ((src and 0xFF) - ab) * (1 - k)).toInt().coerceIn(0, 255)
                px[j * w + i] = (outA shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        t.bitmap.setPixels(px, x0, w, x0, y0, w, h)
    }

    /** Erase raster pixels in a level-local circle (raster eraser, mode 2). */
    fun erase(levelId: Int, layerId: Long, lx: Float, ly: Float, r: Float,
              before: MutableList<TileSnapshot>? = null) {
        val txMin = Math.floor((lx - r) / TILE.toDouble()).toInt()
        val txMax = Math.floor((lx + r) / TILE.toDouble()).toInt()
        val tyMin = Math.floor((ly - r) / TILE.toDouble()).toInt()
        val tyMax = Math.floor((ly + r) / TILE.toDouble()).toInt()
        for (tx in txMin..txMax) {
            for (ty in tyMin..tyMax) {
                val t = tiles[TileKey(levelId, layerId, tx, ty)] ?: continue
                val ox = tx * TILE.toFloat(); val oy = ty * TILE.toFloat()
                if (before != null) snapshot(t.key, lx - ox, ly - oy, r)?.let { before.add(it) }
                val x0 = max(0, (lx - ox - r).toInt()); val x1 = min(TILE - 1, (lx - ox + r).toInt())
                val y0 = max(0, (ly - oy - r).toInt()); val y1 = min(TILE - 1, (ly - oy + r).toInt())
                if (x1 < x0 || y1 < y0) continue
                val w = x1 - x0 + 1; val h = y1 - y0 + 1
                val px = IntArray(w * h)
                t.bitmap.getPixels(px, x0, w, x0, y0, w, h)
                for (j in 0 until h) {
                    val dy = y0 + j + oy - ly
                    for (i in 0 until w) {
                        val dx = x0 + i + ox - lx
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist <= r) px[j * w + i] = 0
                    }
                }
                t.bitmap.setPixels(px, x0, w, x0, y0, w, h)
                t.dirty = true
            }
        }
    }
}
