package io.github.xxparthparekhxx.composekeyboard.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.xxparthparekhxx.composekeyboard.R
import io.github.xxparthparekhxx.composekeyboard.data.CustomThemeColors
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardPreferences
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardSettings
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardThemeType
import io.github.xxparthparekhxx.composekeyboard.ui.theme.CustomThemeEditorCard

/** Themes tab: preset gallery, plus an entry into the custom theme editor. */
@Composable
fun AppearanceScreen(
    settings: KeyboardSettings,
    onUpdateSettings: ((KeyboardPreferences) -> Unit) -> Unit,
    onOpenCustomTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(3) }) {
            Text(
                text = stringResource(R.string.appearance_choose),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
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
            SettingsGroup(
                title = stringResource(R.string.appearance_custom),
                modifier = Modifier.padding(top = 14.dp)
            ) {
                SettingsNavItem(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.appearance_custom_open),
                    summary = stringResource(R.string.appearance_custom_body),
                    onClick = onOpenCustomTheme
                )
            }
        }
    }
}

/** Detail page hosting the custom palette editor. */
@Composable
fun CustomThemeScreen(
    settings: KeyboardSettings,
    onSaveCustomTheme: (CustomThemeColors) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.appearance_custom_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            CustomThemeEditorCard(
                initialColors = settings.customColors,
                onSaveAndApply = onSaveCustomTheme
            )
        }
    }
}
