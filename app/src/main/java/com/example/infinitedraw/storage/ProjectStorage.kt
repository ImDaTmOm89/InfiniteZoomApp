package com.example.infinitedraw.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.infinitedraw.engine.Camera
import com.example.infinitedraw.engine.InfiniteZoomEngine
import com.example.infinitedraw.model.F
import com.example.infinitedraw.model.Layer
import com.example.infinitedraw.model.Paper
import com.example.infinitedraw.model.PlacedImage
import com.example.infinitedraw.model.Project
import com.example.infinitedraw.model.Stroke
import com.example.infinitedraw.model.ZoomLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
class LevelDto(
    val id: Int, val parentId: Int?, val originX: Double, val originY: Double,
    val strokes: List<Stroke>, val images: List<PlacedImage>
)

@Serializable
class CameraDto(val levelId: Int, val cx: Double, val cy: Double, val zoom: Double,
                 val rotation: Double = 0.0)   // Step 24; default keeps old saves loading

@Serializable
class ProjectDto(
    val version: Int = 1, val name: String, val layers: List<Layer>, val paper: Paper,
    val levels: List<LevelDto>, val camera: CameraDto,
    val nextLevelId: Int, val nextStrokeId: Long
)

private val json = Json { ignoreUnknownKeys = true }

object ProjectStorage {
    const val TAG = "ProjectStorage"
    private const val PREFS = "infinitedraw_prefs"
    private const val KEY_LAST = "last_project"

    fun projectsDir(ctx: Context): File = File(ctx.filesDir, "projects")

    fun projectDir(ctx: Context, name: String): File = File(projectsDir(ctx), name)

    fun listProjects(ctx: Context): List<String> =
        projectsDir(ctx).listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun lastProjectName(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun setLastProjectName(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST, name).apply()
    }

    fun toDto(p: Project, e: InfiniteZoomEngine): ProjectDto {
        val dtos = e.levels.values.map { lvl ->
            LevelDto(lvl.id, lvl.parent?.id, lvl.originX, lvl.originY, lvl.strokes.toList(), lvl.images.toList())
        }
        val c = e.camera
        return ProjectDto(
            name = p.name, layers = p.layers.toList(), paper = p.paper,
            levels = dtos,
            camera = CameraDto(c.level.id, c.cx, c.cy, c.zoom, c.rotation),
            nextLevelId = e.nextLevelId, nextStrokeId = e.nextStrokeId
        )
    }

    fun serialize(p: Project, e: InfiniteZoomEngine): String = json.encodeToString(ProjectDto.serializer(), toDto(p, e))

    fun apply(dto: ProjectDto, p: Project, e: InfiniteZoomEngine) {
        // Rebuild engine state
        e.levels.clear()
        e.root.parent = null
        e.root.children.clear()
        e.root.strokes.clear()
        e.root.images.clear()

        val byId = HashMap<Int, ZoomLevel>()
        // Create all ZoomLevels first
        for (ld in dto.levels) {
            if (ld.id == 0) continue
            byId[ld.id] = ZoomLevel(ld.id, null, ld.originX, ld.originY)
        }
        byId[0] = e.root
        // Link parents/children, fill strokes/images
        for (ld in dto.levels) {
            val lvl = byId[ld.id] ?: continue
            lvl.parent = ld.parentId?.let { byId[it] }
            if (ld.parentId != null) lvl.parent?.children?.add(lvl)
            lvl.strokes.addAll(ld.strokes)
            lvl.images.addAll(ld.images)
            e.levels[lvl.id] = lvl
        }
        // root = level with parentId == null
        e.root = byId.values.first { it.parent == null }
        e.nextLevelId = dto.nextLevelId
        e.nextStrokeId = dto.nextStrokeId

        // Project layers / paper
        p.name = dto.name
        p.layers.clear()
        p.layers.addAll(dto.layers)
        p.paper.color = dto.paper.color
        p.paper.pattern = dto.paper.pattern

        // Camera restore
        val cl = e.levels[dto.camera.levelId] ?: e.root
        e.camera.level = cl
        e.camera.cx = dto.camera.cx
        e.camera.cy = dto.camera.cy
        e.camera.zoom = dto.camera.zoom
        e.camera.rotation = dto.camera.rotation   // Step 24
    }

    fun deserialize(text: String, p: Project, e: InfiniteZoomEngine) = apply(json.decodeFromString(ProjectDto.serializer(), text), p, e)

    fun save(ctx: Context, p: Project, e: InfiniteZoomEngine) {
        try {
            val dir = projectDir(ctx, p.name).apply { mkdirs() }
            val tmp = File(dir, "project.json.tmp")
            val dst = File(dir, "project.json")
            tmp.writeText(serialize(p, e))
            if (dst.exists()) dst.delete()
            tmp.renameTo(dst)
            saveTiles(ctx, p)
            setLastProjectName(ctx, p.name)
            // Phase 2 polish: keep the tile cache bounded after each flush.
            com.example.infinitedraw.render.RasterTiles.evict()
        } catch (t: Throwable) {
            Log.e(TAG, "save failed", t)
        }
    }

    /** Phase 2: write every dirty raster tile as PNG under projects/<name>/tiles/. */
    private fun saveTiles(ctx: Context, p: Project) {
        val tiles = com.example.infinitedraw.render.RasterTiles
        if (tiles.tiles.isEmpty()) return
        val dir = File(projectDir(ctx, p.name), "tiles").apply { mkdirs() }
        for (t in tiles.tilesSnapshot()) {
            if (!t.dirty) continue
            val k = t.key
            val f = File(dir, "${k.levelId}_${k.layerId}_${k.tx}_${k.ty}.png")
            try {
                val tmp = File(dir, f.name + ".tmp")
                tmp.outputStream().use { t.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                if (f.exists()) f.delete()
                tmp.renameTo(f)
                t.dirty = false
            } catch (t2: Throwable) {
                Log.e(TAG, "tile save failed: ${f.name}", t2)
            }
        }
    }

    /** Phase 2: load raster tile PNGs for a project into the tile store. */
    private fun loadTiles(ctx: Context, name: String) {
        val tiles = com.example.infinitedraw.render.RasterTiles
        val dir = File(projectDir(ctx, name), "tiles")
        val files = dir.listFiles { f -> f.name.endsWith(".png") } ?: return
        for (f in files) {
            val parts = f.nameWithoutExtension.split("_")
            if (parts.size != 4) continue
            val levelId = parts[0].toIntOrNull() ?: continue
            val layerId = parts[1].toLongOrNull() ?: continue
            val tx = parts[2].toIntOrNull() ?: continue
            val ty = parts[3].toIntOrNull() ?: continue
            val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath)
            if (bmp == null || bmp.width != tiles.TILE || bmp.height != tiles.TILE) continue
            val t = tiles.tileFor(com.example.infinitedraw.render.RasterTiles.TileKey(levelId, layerId, tx, ty))
            t.canvas.setBitmap(bmp)
            t.bitmap = bmp
            t.dirty = false
        }
    }

    /**
     * Load a project. Pass a bitmaps map (the ViewModel's image cache) to also
     * reload placed images from projects/<name>/images/ — Step 23 needs this for
     * import, and it also fixes placed images not reappearing after an app restart.
     */
    fun load(ctx: Context, name: String, p: Project, e: InfiniteZoomEngine,
             bitmaps: MutableMap<Long, android.graphics.Bitmap>? = null): Boolean {
        return try {
            val f = File(projectDir(ctx, name), "project.json")
            if (!f.exists()) return false
            deserialize(f.readText(), p, e)
            com.example.infinitedraw.render.RasterTiles.clearAll()
            loadTiles(ctx, name)
            bitmaps?.clear()
            loadImages(ctx, name, bitmaps)
            setLastProjectName(ctx, p.name)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "load failed", t)
            false
        }
    }

    /** Step 23: decode every projects/<name>/images/<id>.png into the bitmap cache. */
    private fun loadImages(ctx: Context, name: String, bitmaps: MutableMap<Long, android.graphics.Bitmap>?) {
        if (bitmaps == null) return
        val dir = File(projectDir(ctx, name), "images")
        val files = dir.listFiles { f -> f.name.endsWith(".png") } ?: return
        for (f in files) {
            val id = f.nameWithoutExtension.toLongOrNull() ?: continue
            val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: continue
            bitmaps[id] = bmp
        }
    }

    // ---- Step 23: .uzw export / import ----

    private const val MAX_ENTRIES = 10000
    private const val MAX_BYTES = 500L * 1024 * 1024

    /** Step 23: SAF display name -> safe project folder base name. */
    fun sanitizeProjectName(display: String): String {
        val stripped = display.substringBeforeLast('.').replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val cleaned = stripped.ifBlank { "Imported project" }
        return if (cleaned == "." || cleaned == "..") "Imported project" else cleaned
    }

    /** Step 23: first unused folder name under dir; appends " (2)", " (3)"... while taken. */
    fun dedupFolderName(dir: File, base: String): String {
        if (!File(dir, base).exists()) return base
        var n = 2
        while (File(dir, "$base ($n)").exists()) n++
        return "$base ($n)"
    }

    /**
     * Step 23: zip the whole project folder (project.json, images/, tiles/) into
     * the SAF target. Returns null on success, a user-facing error message on failure.
     */
    fun exportUzw(ctx: Context, name: String, uri: Uri): String? {
        return try {
            val dir = projectDir(ctx, name)
            if (!dir.isDirectory) return "Nothing to export: project folder '$name' not found"
            ctx.contentResolver.openOutputStream(uri)?.use { raw ->
                java.util.zip.ZipOutputStream(raw.buffered()).use { zos ->
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        zos.putNextEntry(java.util.zip.ZipEntry(f.relativeTo(dir).invariantSeparatorsPath))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            } ?: return "Could not open the export target"
            null
        } catch (t: Throwable) {
            Log.e(TAG, "exportUzw failed", t)
            "Export failed: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    /** Step 23: result of unzipImport — exactly one of folder / error is set. */
    class ImportOutcome(val folder: String? = null, val error: String? = null)

    /**
     * Step 23: unzip a .uzw into a NEW deduped folder under projects/. Guards:
     * zip-slip canonical-path check, 10,000-entry cap, 500 MB total cap, and a
     * required project.json. On failure the partial folder is deleted. Never throws.
     */
    fun unzipImport(ctx: Context, uri: Uri): ImportOutcome {
        var dest: File? = null
        return try {
            var display = "Imported project"
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) {
                    val d = c.getString(i)
                    if (!d.isNullOrBlank()) display = d
                }
            }
            val base = sanitizeProjectName(display)
            val pdir = projectsDir(ctx).apply { mkdirs() }
            val folder = dedupFolderName(pdir, base)
            val target = File(pdir, folder).apply { mkdirs() }
            dest = target
            val destCanon = target.canonicalPath + File.separator
            var entries = 0
            var total = 0L
            var sawManifest = false
            ctx.contentResolver.openInputStream(uri)?.use { raw ->
                java.util.zip.ZipInputStream(raw.buffered()).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (entry.isDirectory) continue
                        entries++
                        if (entries > MAX_ENTRIES) throw IllegalStateException("the archive has more than $MAX_ENTRIES files")
                        if (entry.name == "project.json") sawManifest = true
                        val out = File(target, entry.name)
                        if (!out.canonicalPath.startsWith(destCanon))
                            throw IllegalStateException("unsafe entry '${entry.name}'")
                        out.parentFile?.mkdirs()
                        java.io.FileOutputStream(out).use { fout -> total += zis.copyTo(fout) }
                        if (total > MAX_BYTES) throw IllegalStateException("the archive is larger than 500 MB")
                        zis.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("could not open the selected file")
            if (!sawManifest) throw IllegalStateException("project.json missing - not an app project archive")
            Log.i(TAG, "imported .uzw into $folder ($entries files, $total bytes)")
            ImportOutcome(folder = folder)
        } catch (t: Throwable) {
            Log.e(TAG, "unzipImport failed", t)
            dest?.deleteRecursively()
            ImportOutcome(error = "Import failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }
}

/** Debounced autosave helper held by the ViewModel. */
class Autosave(private val ctx: Context, private val p: Project, private val e: InfiniteZoomEngine) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    fun schedule() {
        job?.cancel()
        job = scope.launch {
            delay(2000)
            ProjectStorage.save(ctx, p, e)
        }
    }
    fun flushNow() {
        job?.cancel()
        ProjectStorage.save(ctx, p, e)
    }
}
