package com.example.infinitedraw.storage

import android.content.Context
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
class CameraDto(val levelId: Int, val cx: Double, val cy: Double, val zoom: Double)

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
            camera = CameraDto(c.level.id, c.cx, c.cy, c.zoom),
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
            setLastProjectName(ctx, p.name)
        } catch (t: Throwable) {
            Log.e(TAG, "save failed", t)
        }
    }

    fun load(ctx: Context, name: String, p: Project, e: InfiniteZoomEngine): Boolean {
        return try {
            val f = File(projectDir(ctx, name), "project.json")
            if (!f.exists()) return false
            deserialize(f.readText(), p, e)
            setLastProjectName(ctx, p.name)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "load failed", t)
            false
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
