package com.example.infinitedraw.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.infinitedraw.model.PaperPattern
import com.example.infinitedraw.ui.DrawingViewModel

@Composable
fun PaperPanel(vm: DrawingViewModel) {
    val paper = vm.project.paper
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Paper", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaperPattern.entries.forEach { p ->
                val selected = paper.pattern == p
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0x332196F3) else Color.Transparent)
                        .clickable { paper.pattern = p; vm.redraw?.invoke() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }
        Text("Paper color")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-1, 0xFFFFFFFF.toInt(), 0xFFF5F5DC.toInt(), 0xFF2B2B2B.toInt()).forEach { c ->
                val selected = paper.color == c
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (c == -1) Color.White else Color(c))
                        .clickable { paper.color = c; vm.redraw?.invoke() }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
