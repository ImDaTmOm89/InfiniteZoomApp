package com.example.infinitedraw.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.infinitedraw.ui.theme.textSecondary

/** U3: small uppercase section label. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        color = textSecondary,
        fontFamily = FontFamily.Default,
    )
}
