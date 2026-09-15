package com.example.composekeyboard.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composekeyboard.data.CustomThemeColors
import com.example.composekeyboard.data.KeyboardPreferences
import com.example.composekeyboard.data.KeyboardSettings
import com.example.composekeyboard.data.KeyboardThemeType
import com.example.composekeyboard.ui.theme.CustomThemeEditorCard

@Composable
fun AppearanceScreen(
    settings: KeyboardSettings,
    onSaveCustomTheme: (CustomThemeColors) -> Unit,
    onUpdateSettings: ((KeyboardPreferences) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(3) }) {
            Text(
                text = "Choose a look that applies to the keyboard everywhere it appears.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
        item(span = { GridItemSpan(3) }) {
            SectionHeader("Presets")
        }
        items(KeyboardThemeType.entries) { theme ->
            ThemePreviewCard(
                theme = theme,
                customColors = settings.customColors,
                isSelected = settings.theme == theme,
                onClick = { onUpdateSettings { prefs -> prefs.setTheme(theme) } }
            )
        }
        item(span = { GridItemSpan(3) }) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Custom")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Build your own palette, then save it to use as the active theme.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            CustomThemeEditorCard(
                initialColors = settings.customColors,
                onSaveAndApply = onSaveCustomTheme
            )
        }
    }
}
