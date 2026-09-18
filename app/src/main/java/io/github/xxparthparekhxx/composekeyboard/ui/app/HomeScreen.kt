package io.github.xxparthparekhxx.composekeyboard.ui.app

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalLayoutApi::class)
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            if (isReady) {
                ReadyBanner()
            } else {
                SetupWizardCard(
                    isImeEnabled = isImeEnabled,
                    isImeSelected = isImeSelected,
                    onEnableClick = onEnableClick,
                    onSelectClick = onSelectClick
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.home_try_it))
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard {
                Text(
                    text = stringResource(R.string.home_playground),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_playground_hint),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_field_type),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlaygroundInputType.entries.forEach { type ->
                        FilterChip(
                            selected = inputType == type,
                            onClick = { selectedType = type.name },
                            label = { Text(stringResource(type.labelRes)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_enter_key),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlaygroundImeAction.entries.forEach { action ->
                        FilterChip(
                            selected = imeAction == action,
                            onClick = { selectedAction = action.name },
                            label = { Text(stringResource(action.labelRes)) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (inputType.usesPlatformDatetimeField) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DatetimePlaygroundField(
                            value = fieldValue,
                            onValueChange = { datetimeValue = it },
                            placeholder = inputType.placeholderRes?.let { stringResource(it) } ?: inputType.placeholder,
                            imeAction = imeAction.toEditorImeAction(),
                            modifier = Modifier.weight(1f)
                        )
                        if (fieldValue.isNotEmpty()) {
                            TextButton(onClick = { datetimeValue = "" }) {
                                Text(stringResource(R.string.action_clear))
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = { value ->
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
                        },
                        placeholder = { Text(inputType.placeholderRes?.let { stringResource(it) } ?: inputType.placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
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
                        trailingIcon = {
                            if (fieldValue.isNotEmpty()) {
                                TextButton(onClick = {
                                    when (inputType) {
                                        PlaygroundInputType.TEXT -> textValue = ""
                                        PlaygroundInputType.NUMBER -> numberValue = ""
                                        PlaygroundInputType.PHONE -> phoneValue = ""
                                        PlaygroundInputType.DECIMAL -> decimalValue = ""
                                        PlaygroundInputType.EMAIL -> emailValue = ""
                                        PlaygroundInputType.URI -> uriValue = ""
                                        PlaygroundInputType.PASSWORD -> passwordValue = ""
                                        PlaygroundInputType.NUMBER_PASSWORD -> pinValue = ""
                                        PlaygroundInputType.ASCII -> asciiValue = ""
                                        PlaygroundInputType.DATETIME -> datetimeValue = ""
                                    }
                                }) {
                                    Text(stringResource(R.string.action_clear))
                                }
                            }
                        }
                    )
                }
                if (imeAction == PlaygroundImeAction.NEXT || imeAction == PlaygroundImeAction.PREVIOUS) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nextFieldValue,
                        onValueChange = { nextFieldValue = it },
                        placeholder = { Text(stringResource(R.string.home_next_field)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Previous
                        ),
                        keyboardActions = keyboardActions,
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(inputType.hintRes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.home_while_typing))
            Spacer(modifier = Modifier.height(8.dp))
            CompanionCard {
                TipRow(
                    icon = Icons.Default.Swipe,
                    title = stringResource(R.string.tip_swipe_title),
                    body = stringResource(R.string.tip_swipe_body)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TipRow(
                    icon = Icons.Default.Keyboard,
                    title = stringResource(R.string.tip_longpress_title),
                    body = stringResource(R.string.tip_longpress_body)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TipRow(
                    icon = Icons.Default.SpaceBar,
                    title = stringResource(R.string.tip_space_title),
                    body = stringResource(R.string.tip_space_body)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TipRow(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.tip_dictate_title),
                    body = stringResource(R.string.tip_dictate_body)
                )
                Spacer(modifier = Modifier.height(12.dp))
                TipRow(
                    icon = Icons.Default.Gesture,
                    title = stringResource(R.string.tip_themes_title),
                    body = stringResource(R.string.tip_themes_body)
                )
            }
        }
    }
}

@Composable
private fun ReadyBanner() {
    CompanionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.home_ready),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.home_ready_body),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
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
    CompanionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = stringResource(R.string.home_get_started),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_get_started_body),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        SetupStepItem(
            title = stringResource(R.string.setup_enable_title),
            description = "Turn it on in Android input method settings",
            isCompleted = isImeEnabled,
            actionLabel = stringResource(R.string.setup_enable_action),
            onActionClick = onEnableClick
        )
        Spacer(modifier = Modifier.height(10.dp))
        SetupStepItem(
            title = stringResource(R.string.setup_select_title),
            description = "Pick Compose Keyboard from the system picker",
            isCompleted = isImeSelected,
            actionLabel = stringResource(R.string.setup_select_action),
            onActionClick = onSelectClick
        )
    }
}

@Composable
private fun SetupStepItem(
    title: String,
    description: String,
    isCompleted: Boolean,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (isCompleted) "Completed" else "Not completed",
            tint = if (isCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )
            if (!isCompleted) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = actionLabel, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TipRow(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = body,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
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
