package com.example.infinitedraw.model

class Project(var name: String, val layers: MutableList<Layer>, val paper: Paper = Paper()) {
    /** Testing fix: the active layer must survive save/load (plan Step 16 "reopen: identical"). */
    var activeLayerId: Long = if (layers.isNotEmpty()) layers.first().id else 1L
}
