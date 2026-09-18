package io.github.xxparthparekhxx.composekeyboard.ui.app

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.CustomThemeColors
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardPreferences
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardSettings
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardThemeType
import io.github.xxparthparekhxx.composekeyboard.ui.theme.CustomThemeEditorCard

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
                text = stringResource(R.string.appearance_choose),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
        item(span = { GridItemSpan(3) }) {
            SectionHeader(stringResource(R.string.appearance_presets))
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
            SectionHeader(stringResource(R.string.appearance_custom))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.appearance_custom_body),
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
