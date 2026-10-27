package com.example.infinitedraw.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.ui.DrawingViewModel
import com.example.infinitedraw.ui.components.PanelSurface
import com.example.infinitedraw.ui.components.SectionTitle
import com.example.infinitedraw.ui.theme.accent
import com.example.infinitedraw.ui.theme.accentSoft
import com.example.infinitedraw.ui.theme.textPrimary

/**
 * U9: the settings panel — one switch per row, every change takes effect
 * immediately and persists in SharedPreferences("settings"). Opened from the
 * top pill's SETTINGS button; one panel at a time.
 */
@Composable
fun SettingsPanel(vm: DrawingViewModel) {
    val maxHeight = 800.dp   // tall enough for six rows even in landscape

    PanelSurface(modifier = Modifier.width(300.dp)) {
        Column(
            Modifier.width(280.dp).heightIn(max = maxHeight),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle("Settings")

            SettingRow("Finger draws", vm.fingerDraws) { vm.fingerDraws = it }
            SettingRow("Pressure sensitivity", vm.usePressure) { vm.usePressure = it }
            SettingRow("Auto-hide controls while drawing", vm.autoHideUi) { vm.autoHideUi = it }
            SettingRow("Show zoom indicator", vm.showZoomChip) { vm.showZoomChip = it }
            SettingRow("Show debug text", vm.showDebug) { vm.showDebug = it }

            Spacer(Modifier.height(8.dp))

            // Reset tool panel position (the plan's default spot: left, 25% down).
            Text(
                "Reset tool panel position",
                fontSize = 14.sp,
                color = textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentSoft)
                    .border(1.dp, accent, RoundedCornerShape(10.dp))
                    .clickable { vm.resetPanelPosition() }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = textPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
