package com.example.composekeyboard

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.composekeyboard.data.ClipboardHistoryManager
import com.example.composekeyboard.data.ClipboardItem
import com.example.composekeyboard.data.CustomThemeColors
import com.example.composekeyboard.data.KeyboardPreferences
import com.example.composekeyboard.data.KeyboardSettings
import com.example.composekeyboard.data.KeyboardThemeType
import com.example.composekeyboard.theme.ComposeKeyboardTheme
import com.example.composekeyboard.theme.getKeyboardColors
import com.example.composekeyboard.ui.theme.CustomThemeEditorCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var preferences: KeyboardPreferences
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private val isImeEnabledState = mutableStateOf(false)
    private val isImeSelectedState = mutableStateOf(false)

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            refreshImeStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferences = KeyboardPreferences.getInstance(this)
        clipboardHistoryManager = ClipboardHistoryManager.getInstance(this)

        refreshImeStatus()

        try {
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
                false,
                settingsObserver
            )
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_INPUT_METHODS),
                false,
                settingsObserver
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            val settings by preferences.settings.collectAsState()
            val clipboardItems by clipboardHistoryManager.history.collectAsState()

            ComposeKeyboardTheme(
                themeType = settings.theme,
                customColors = settings.customColors
            ) {
                MainScreen(
                    settings = settings,
                    isImeEnabled = isImeEnabledState.value,
                    isImeSelected = isImeSelectedState.value,
                    clipboardItems = clipboardItems,
                    onRefreshStatus = { refreshImeStatus() },
                    onTogglePinClip = { id -> clipboardHistoryManager.togglePin(id) },
                    onDeleteClip = { id -> clipboardHistoryManager.deleteClip(id) },
                    onClearAllClips = { clipboardHistoryManager.clearAllUnpinned() },
                    onSaveCustomTheme = { customColors ->
                        preferences.setCustomColors(customColors)
                    },
                    onUpdateSettings = { updateAction ->
                        updateAction(preferences)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshImeStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshImeStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            contentResolver.unregisterContentObserver(settingsObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun refreshImeStatus() {
        isImeEnabledState.value = checkIsImeEnabled(this)
        isImeSelectedState.value = checkIsImeSelected(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    settings: KeyboardSettings,
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    clipboardItems: List<ClipboardItem>,
    onRefreshStatus: () -> Unit,
    onTogglePinClip: (String) -> Unit,
    onDeleteClip: (String) -> Unit,
    onClearAllClips: () -> Unit,
    onSaveCustomTheme: (CustomThemeColors) -> Unit,
    onUpdateSettings: ((KeyboardPreferences) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Compose Keyboard",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Setup Steps Card
            item {
                SetupWizardCard(
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onEnableClick = {
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        context.startActivity(intent)
                        scope.launch {
                            delay(500)
                            onRefreshStatus()
                        }
                    },
                    onSelectClick = {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.showInputMethodPicker()
                        scope.launch {
                            repeat(10) {
                                delay(300)
                                onRefreshStatus()
                            }
                        }
                    }
                )
            }

            // Interactive Test Typing Box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Test Your Keyboard",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the field below to test typing, cursor gestures, custom themes, and clipboard history!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            placeholder = { Text("Type something here...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Themes Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ColorLens,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keyboard Themes",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(KeyboardThemeType.values()) { theme ->
                                ThemePreviewCard(
                                    theme = theme,
                                    customColors = settings.customColors,
                                    isSelected = settings.theme == theme,
                                    onClick = {
                                        onUpdateSettings { prefs -> prefs.setTheme(theme) }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Custom Theme Creator Card
            item {
                CustomThemeEditorCard(
                    initialColors = settings.customColors,
                    onSaveAndApply = onSaveCustomTheme
                )
            }

            // Clipboard History Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Saved Clipboard Items (${clipboardItems.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            if (clipboardItems.isNotEmpty()) {
                                Text(
                                    text = "Clear All",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { onClearAllClips() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (clipboardItems.isEmpty()) {
                            Text(
                                text = "No clips stored yet. Copy any text on your phone and it will be saved here automatically.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                clipboardItems.take(5).forEach { clip ->
                                    val formattedTime = remember(clip.timestamp) {
                                        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                            .format(Date(clip.timestamp))
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = clip.text,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = formattedTime,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Icon(
                                            imageVector = if (clip.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                            contentDescription = "Pin",
                                            tint = if (clip.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable { onTogglePinClip(clip.id) }
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clickable { onDeleteClip(clip.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Settings & Preferences Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keyboard Preferences & Layout",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Dedicated Number Row
                        SettingSwitchRow(
                            icon = Icons.Default.Keyboard,
                            title = "Dedicated Number Row",
                            subtitle = "Show a permanent 1-0 number row above the keyboard. When off, numbers remain accessible via long-press.",
                            checked = settings.showNumberRow,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.setShowNumberRow(checked) }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Glide (swipe) typing
                        SettingSwitchRow(
                            icon = Icons.Default.Gesture,
                            title = "Neural Glide Typing",
                            subtitle = "Swipe across letters to type words with on-device neural CTC decoding and 150k-word autocompletion.",
                            checked = settings.swipeTypingEnabled,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.setSwipeTypingEnabled(checked) }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Haptic Feedback
                        SettingSwitchRow(
                            icon = Icons.Default.Vibration,
                            title = "Haptic Vibration",
                            subtitle = "Vibrate lightly on key presses, swipe completions, and clipboard actions for tactile feedback.",
                            checked = settings.hapticFeedback,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.setHapticFeedback(checked) }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Key Press Sounds
                        SettingSwitchRow(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            title = "Key Click Sounds",
                            subtitle = "Play auditory click feedback on key presses and spacebar.",
                            checked = settings.soundFeedback,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.setSoundFeedback(checked) }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Auto-Capitalization
                        SettingSwitchRow(
                            icon = Icons.Default.Settings,
                            title = "Auto-Capitalization",
                            subtitle = "Automatically capitalize the first letter of sentences and after punctuation.",
                            checked = settings.autoCapitalization,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.setAutoCapitalization(checked) }
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Key Press Popups
                        SettingSwitchRow(
                            icon = Icons.Default.Edit,
                            title = "Key Press Popups",
                            subtitle = "Display an enlarged preview bubble above each key as you press it.",
                            checked = settings.showKeyPopups,
                            onCheckedChange = { checked ->
                                onUpdateSettings { it.setShowKeyPopups(checked) }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Keyboard Height Scale
                        Text(
                            text = "Keyboard Height Scale",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Adjust the vertical scale of the keyboard keys and panels.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val options = listOf(
                                "Compact (85%)" to 0.85f,
                                "Standard (100%)" to 1.0f,
                                "Tall (115%)" to 1.15f,
                                "Extra Tall (130%)" to 1.30f
                            )
                            items(options) { (label, multiplier) ->
                                val isSelected = settings.heightMultiplier == multiplier
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        onUpdateSettings { it.setHeightMultiplier(multiplier) }
                                    },
                                    label = { Text(label, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }

            // About Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Compose Keyboard v1.0.0",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A modern soft keyboard written in Kotlin & Jetpack Compose. Includes local clipboard history, custom color theme creator, spacebar cursor navigation, and tactile haptics.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupWizardCard(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onEnableClick: () -> Unit,
    onSelectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Setup Steps",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Step 1: Enable
            SetupStepItem(
                stepNumber = "1",
                title = "Enable Compose Keyboard",
                description = "Activate Compose Keyboard in Android System Settings",
                isCompleted = isImeEnabled,
                actionLabel = "Enable in Settings",
                onActionClick = onEnableClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Step 2: Select
            SetupStepItem(
                stepNumber = "2",
                title = "Select Input Method",
                description = "Set Compose Keyboard as your active default keyboard",
                isCompleted = isImeSelected,
                actionLabel = "Choose Keyboard",
                onActionClick = onSelectClick
            )
        }
    }
}

@Composable
fun SetupStepItem(
    stepNumber: String,
    title: String,
    description: String,
    isCompleted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (!isCompleted) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = actionLabel, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ThemePreviewCard(
    theme: KeyboardThemeType,
    customColors: CustomThemeColors,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val themeColors = getKeyboardColors(theme, customColors)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        // Mini Keyboard Visual Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(themeColors.background)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(themeColors.keyBackground)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(themeColors.accentKeyBackground)
                    )
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(themeColors.keyBackground)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(themeColors.actionKeyBackground)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = theme.displayName,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/** The settings string is colon-delimited "id/subtype" entries; match whole components. */
private fun settingContainsIme(setting: String, packageName: String): Boolean =
    setting.split(':').any { entry -> entry.substringBefore('/') == packageName }

private fun checkIsImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val enabledList = imm?.enabledInputMethodList
    val packageName = context.packageName
    if (enabledList?.any { it.packageName == packageName } == true) {
        return true
    }
    val enabledSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_INPUT_METHODS
    ) ?: ""
    return settingContainsIme(enabledSetting, packageName)
}

private fun checkIsImeSelected(context: Context): Boolean {
    val currentIme = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.DEFAULT_INPUT_METHOD
    ) ?: ""
    return settingContainsIme(currentIme, context.packageName)
}
