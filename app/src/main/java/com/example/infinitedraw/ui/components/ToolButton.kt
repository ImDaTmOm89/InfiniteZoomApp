package com.example.infinitedraw.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.infinitedraw.ui.theme.accent
import com.example.infinitedraw.ui.theme.accentSoft
import com.example.infinitedraw.ui.theme.textPrimary

/** U3: a 48dp square tool button with a bounded ripple; selected shows accentSoft + accent icon.
 *  U5: `enabled = false` dims the icon to 38% and removes the ripple. */
@Composable
fun ToolButton(
    icon: UiIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accentSoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        UiIcon(icon = icon, tint = if (selected) accent else textPrimary, contentDescription = label)
    }
}
