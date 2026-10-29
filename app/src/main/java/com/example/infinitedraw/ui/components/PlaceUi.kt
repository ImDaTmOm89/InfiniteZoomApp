package com.example.infinitedraw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.theme.surface
import com.example.infinitedraw.ui.theme.textPrimary
import com.example.infinitedraw.ui.theme.textSecondary

/**
 * U11: the place card (top-left) — the current level's name (or "Unnamed
 * place") on line 1, "Depth n" on line 2. Tapping opens the rename dialog.
 */
@Composable
fun PlaceCard(vm: DrawingViewModel, onRename: () -> Unit, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.cameraTick + vm.placeTick   // subscribe to camera + renames
    val lvl = vm.engine.camera.level
    val named = lvl.name.isNotBlank()
    PanelSurface(modifier = modifier) {
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onRename)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                lvl.name.ifBlank { "Unnamed place" },
                fontSize = 16.sp,
                color = if (named) textPrimary else textSecondary,
                maxLines = 1,
            )
            Text("Depth ${vm.engine.depth()}", fontSize = 12.sp, color = textSecondary)
        }
    }
}

/**
 * U11: the breadcrumb — a horizontally scrolling chip row under the place
 * card: ancestors from the top down to the current level, at most the last 4
 * (plus a "..." chip for the rest). Tapping does nothing until U12 (fly-to).
 */
@Composable
fun Breadcrumb(vm: DrawingViewModel, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = vm.cameraTick + vm.placeTick
    val lvl = vm.engine.camera.level
    // Top down to the current level.
    val chain = ArrayList<Any>(vm.engine.ancestors().size + 1)
    val anc = vm.engine.ancestors().asReversed()   // top-most first
    chain.addAll(anc)
    val crumbs = chain.toList()
    val shown = if (crumbs.size > 4) crumbs.subList(crumbs.size - 4, crumbs.size) else crumbs
    val hidden = crumbs.size - shown.size

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (hidden > 0) Crumb("...", current = false)
        shown.forEachIndexed { i, c ->
            val f = c as com.example.infinitedraw.engine.InfiniteZoomEngine.AncestorFrame
            Crumb(
                f.level.name.ifBlank { "Depth ${vm.engine.depth(f.level)}" },
                current = false,
            )
        }
        Crumb(
            lvl.name.ifBlank { "Depth ${vm.engine.depth()}" },
            current = true,
        )
    }
}

@Composable
private fun Crumb(label: String, current: Boolean) {
    Text(
        label,
        fontSize = 12.sp,
        color = if (current) textPrimary else textSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
