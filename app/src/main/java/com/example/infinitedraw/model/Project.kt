package com.example.infinitedraw.model

class Project(var name: String, val layers: MutableList<Layer>, val paper: Paper = Paper()) {
    /** Testing fix: the active layer must survive save/load (plan Step 16 "reopen: identical"). */
    var activeLayerId: Long = if (layers.isNotEmpty()) layers.first().id else 1L

    /**
     * U8 bugfix: derive new layer ids from the max existing one. A counter that
     * resets on relaunch re-issues ids that already exist in a restored project,
     * which collides LazyColumn keys and silently drops rows.
     */
    fun nextLayerId(): Long = (layers.maxOfOrNull { it.id } ?: 0L) + 1L
}
