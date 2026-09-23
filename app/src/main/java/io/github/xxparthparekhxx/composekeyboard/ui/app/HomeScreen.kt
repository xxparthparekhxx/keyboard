package io.github.xxparthparekhxx.composekeyboard.ui.app

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.xxparthparekhxx.composekeyboard.R

private enum class PlaygroundInputType(
    val labelRes: Int,
    val keyboardType: KeyboardType?,
    val placeholder: String,
    val placeholderRes: Int?,
    val hintRes: Int
) {
    TEXT(
        labelRes = R.string.pt_text,
        keyboardType = KeyboardType.Text,
        placeholder = "",
        placeholderRes = R.string.pt_ph_text,
        hintRes = R.string.pt_hint_text
    ),
    NUMBER(
        labelRes = R.string.pt_number,
        keyboardType = KeyboardType.Number,
        placeholder = "",
        placeholderRes = R.string.pt_ph_number,
        hintRes = R.string.pt_hint_number
    ),
    PHONE(
        labelRes = R.string.pt_phone,
        keyboardType = KeyboardType.Phone,
        placeholder = "",
        placeholderRes = R.string.pt_ph_phone,
        hintRes = R.string.pt_hint_phone
    ),
    DECIMAL(
        labelRes = R.string.pt_decimal,
        keyboardType = KeyboardType.Decimal,
        placeholder = "",
        placeholderRes = R.string.pt_ph_decimal,
        hintRes = R.string.pt_hint_decimal
    ),
    EMAIL(
        labelRes = R.string.pt_email,
        keyboardType = KeyboardType.Email,
        placeholder = "name@example.com",
        placeholderRes = null,
        hintRes = R.string.pt_hint_email
    ),
    URI(
        labelRes = R.string.pt_url,
        keyboardType = KeyboardType.Uri,
        placeholder = "https://…",
        placeholderRes = null,
        hintRes = R.string.pt_hint_uri
    ),
    PASSWORD(
        labelRes = R.string.pt_password,
        keyboardType = KeyboardType.Password,
        placeholder = "",
        placeholderRes = R.string.pt_ph_password,
        hintRes = R.string.pt_hint_password
    ),
    NUMBER_PASSWORD(
        labelRes = R.string.pt_pin,
        keyboardType = KeyboardType.NumberPassword,
        placeholder = "",
        placeholderRes = R.string.pt_ph_pin,
        hintRes = R.string.pt_hint_pin
    ),
    ASCII(
        labelRes = R.string.pt_ascii,
        keyboardType = KeyboardType.Ascii,
        placeholder = "",
        placeholderRes = R.string.pt_ph_ascii,
        hintRes = R.string.pt_hint_ascii
    ),
    DATETIME(
        labelRes = R.string.pt_datetime,
        keyboardType = null,
        placeholder = "2026-09-15 14:30",
        placeholderRes = null,
        hintRes = R.string.pt_hint_datetime
    );

    val usesPlatformDatetimeField: Boolean
        get() = keyboardType == null

    val masksInput: Boolean
        get() = this == PASSWORD || this == NUMBER_PASSWORD
}

private enum class PlaygroundImeAction(
    val labelRes: Int,
    val imeAction: ImeAction
) {
    DEFAULT(R.string.ime_action_enter, ImeAction.Default),
    SEARCH(R.string.ime_action_search, ImeAction.Search),
    SEND(R.string.ime_action_send, ImeAction.Send),
    DONE(R.string.ime_action_done, ImeAction.Done),
    GO(R.string.ime_action_go, ImeAction.Go),
    NEXT(R.string.ime_action_next, ImeAction.Next),
    PREVIOUS(R.string.ime_action_previous, ImeAction.Previous);

    fun toEditorImeAction(): Int = when (imeAction) {
        ImeAction.Search -> EditorInfo.IME_ACTION_SEARCH
        ImeAction.Send -> EditorInfo.IME_ACTION_SEND
        ImeAction.Done -> EditorInfo.IME_ACTION_DONE
        ImeAction.Go -> EditorInfo.IME_ACTION_GO
        ImeAction.Next -> EditorInfo.IME_ACTION_NEXT
        ImeAction.Previous -> EditorInfo.IME_ACTION_PREVIOUS
        else -> EditorInfo.IME_ACTION_UNSPECIFIED
    }
}

@Composable
fun HomeScreen(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onEnableClick: () -> Unit,
    onSelectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by rememberSaveable { mutableStateOf(PlaygroundInputType.TEXT.name) }
    var selectedAction by rememberSaveable { mutableStateOf(PlaygroundImeAction.DEFAULT.name) }
    var textValue by rememberSaveable { mutableStateOf("") }
    var numberValue by rememberSaveable { mutableStateOf("") }
    var phoneValue by rememberSaveable { mutableStateOf("") }
    var decimalValue by rememberSaveable { mutableStateOf("") }
    var emailValue by rememberSaveable { mutableStateOf("") }
    var uriValue by rememberSaveable { mutableStateOf("") }
    var passwordValue by rememberSaveable { mutableStateOf("") }
    var pinValue by rememberSaveable { mutableStateOf("") }
    var asciiValue by rememberSaveable { mutableStateOf("") }
    var datetimeValue by rememberSaveable { mutableStateOf("") }
    var nextFieldValue by rememberSaveable { mutableStateOf("") }

    val inputType = PlaygroundInputType.entries.firstOrNull { it.name == selectedType }
        ?: PlaygroundInputType.TEXT
    val imeAction = PlaygroundImeAction.entries.firstOrNull { it.name == selectedAction }
        ?: PlaygroundImeAction.DEFAULT
    val fieldValue = when (inputType) {
        PlaygroundInputType.TEXT -> textValue
        PlaygroundInputType.NUMBER -> numberValue
        PlaygroundInputType.PHONE -> phoneValue
        PlaygroundInputType.DECIMAL -> decimalValue
        PlaygroundInputType.EMAIL -> emailValue
        PlaygroundInputType.URI -> uriValue
        PlaygroundInputType.PASSWORD -> passwordValue
        PlaygroundInputType.NUMBER_PASSWORD -> pinValue
        PlaygroundInputType.ASCII -> asciiValue
        PlaygroundInputType.DATETIME -> datetimeValue
    }
    val focusManager = LocalFocusManager.current
    val keyboardActions = KeyboardActions(
        onSearch = { focusManager.clearFocus() },
        onSend = { focusManager.clearFocus() },
        onDone = { focusManager.clearFocus() },
        onGo = { focusManager.clearFocus() },
        onNext = { focusManager.moveFocus(FocusDirection.Down) },
        onPrevious = { focusManager.moveFocus(FocusDirection.Up) }
    )
    val isReady = isImeEnabled && isImeSelected
    val setValue: (String) -> Unit = { value ->
        when (inputType) {
            PlaygroundInputType.TEXT -> textValue = value
            PlaygroundInputType.NUMBER -> numberValue = value
            PlaygroundInputType.PHONE -> phoneValue = value
            PlaygroundInputType.DECIMAL -> decimalValue = value
            PlaygroundInputType.EMAIL -> emailValue = value
            PlaygroundInputType.URI -> uriValue = value
            PlaygroundInputType.PASSWORD -> passwordValue = value
            PlaygroundInputType.NUMBER_PASSWORD -> pinValue = value
            PlaygroundInputType.ASCII -> asciiValue = value
            PlaygroundInputType.DATETIME -> datetimeValue = value
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            if (isReady) {
                ReadyBanner()
            } else {
                SetupWizard(
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onEnableClick = onEnableClick,
                    onSelectClick = onSelectClick
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.home_try_it))
            CompanionCard {
                Text(
                    text = stringResource(R.string.home_playground),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_playground_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (inputType.usesPlatformDatetimeField) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DatetimePlaygroundField(
                            value = fieldValue,
                            onValueChange = setValue,
                            placeholder = inputType.placeholderRes?.let { stringResource(it) } ?: inputType.placeholder,
                            imeAction = imeAction.toEditorImeAction(),
                            modifier = Modifier.weight(1f)
                        )
                        if (fieldValue.isNotEmpty()) {
                            TextButton(onClick = { setValue("") }) {
                                Text(stringResource(R.string.action_clear))
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = setValue,
                        placeholder = { Text(inputType.placeholderRes?.let { stringResource(it) } ?: inputType.placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = requireNotNull(inputType.keyboardType),
                            imeAction = imeAction.imeAction
                        ),
                        keyboardActions = keyboardActions,
                        visualTransformation = if (inputType.masksInput) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        singleLine = inputType != PlaygroundInputType.TEXT,
                        minLines = if (inputType == PlaygroundInputType.TEXT) 3 else 1,
                        supportingText = { Text(stringResource(inputType.hintRes)) },
                        trailingIcon = {
                            if (fieldValue.isNotEmpty()) {
                                TextButton(onClick = { setValue("") }) {
                                    Text(stringResource(R.string.action_clear))
                                }
                            }
                        }
                    )
                }
                if (inputType.usesPlatformDatetimeField) {
                    Text(
                        text = stringResource(inputType.hintRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
                if (imeAction == PlaygroundImeAction.NEXT || imeAction == PlaygroundImeAction.PREVIOUS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nextFieldValue,
                        onValueChange = { nextFieldValue = it },
                        placeholder = { Text(stringResource(R.string.home_next_field)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Previous
                        ),
                        keyboardActions = keyboardActions,
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                ChipRow(
                    label = stringResource(R.string.home_field_type),
                    options = PlaygroundInputType.entries,
                    selected = inputType,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = { selectedType = it.name }
                )
                Spacer(modifier = Modifier.height(12.dp))
                ChipRow(
                    label = stringResource(R.string.home_enter_key),
                    options = PlaygroundImeAction.entries,
                    selected = imeAction,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = { selectedAction = it.name }
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.home_while_typing)) {
                SettingsItem(
                    icon = Icons.Default.Swipe,
                    title = stringResource(R.string.tip_swipe_title),
                    summary = stringResource(R.string.tip_swipe_body)
                )
                SettingsItem(
                    icon = Icons.Default.Keyboard,
                    title = stringResource(R.string.tip_longpress_title),
                    summary = stringResource(R.string.tip_longpress_body)
                )
                SettingsItem(
                    icon = Icons.Default.SpaceBar,
                    title = stringResource(R.string.tip_space_title),
                    summary = stringResource(R.string.tip_space_body)
                )
                SettingsItem(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.tip_dictate_title),
                    summary = stringResource(R.string.tip_dictate_body)
                )
                SettingsItem(
                    icon = Icons.Default.Gesture,
                    title = stringResource(R.string.tip_themes_title),
                    summary = stringResource(R.string.tip_themes_body)
                )
            }
        }
    }
}

/** A labelled, horizontally scrolling single-choice chip row. */
@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(optionLabel(option)) }
            )
        }
    }
}

@Composable
private fun ReadyBanner() {
    CompanionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(
                icon = Icons.Default.CheckCircle,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.home_ready),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.home_ready_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun SetupWizard(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onEnableClick: () -> Unit,
    onSelectClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CompanionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                text = stringResource(R.string.home_get_started),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_get_started_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
        SettingsGroup {
            SetupStepTile(
                icon = Icons.Default.ToggleOn,
                title = stringResource(R.string.setup_enable_title),
                body = stringResource(R.string.setup_enable_desc),
                done = isImeEnabled,
                action = if (isImeEnabled) null else {
                    { Button(onClick = onEnableClick) { Text(stringResource(R.string.setup_enable_action)) } }
                }
            )
            SetupStepTile(
                icon = Icons.Default.Keyboard,
                title = stringResource(R.string.setup_select_title),
                body = stringResource(R.string.setup_select_desc),
                done = isImeSelected,
                // Selecting only works once the keyboard is enabled.
                action = if (isImeSelected || !isImeEnabled) null else {
                    { Button(onClick = onSelectClick) { Text(stringResource(R.string.setup_select_action)) } }
                }
            )
        }
    }
}

/**
 * Compose [KeyboardType] has no datetime variant. A platform [EditText] is the
 * only way for the playground to request [InputType.TYPE_CLASS_DATETIME].
 */
@Composable
private fun DatetimePlaygroundField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: Int,
    modifier: Modifier = Modifier
) {
    val onValueChangeState = rememberUpdatedState(onValueChange)
    val outline = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_DATETIME
                    isSingleLine = true
                    background = null
                    setPadding(0, 0, 0, 0)
                    textSize = 16f
                    hint = placeholder
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) {
                            onValueChangeState.value(s?.toString().orEmpty())
                        }
                    })
                }
            },
            update = { view ->
                view.imeOptions = imeAction
                view.setTextColor(textColor.toArgb())
                view.setHintTextColor(hintColor.toArgb())
                if (view.text.toString() != value) {
                    view.setText(value)
                    view.setSelection(value.length)
                }
            }
        )
    }
}
