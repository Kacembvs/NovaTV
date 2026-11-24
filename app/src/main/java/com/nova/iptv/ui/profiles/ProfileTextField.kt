package com.nova.iptv.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.nova.iptv.core.isTelevision
import com.nova.iptv.ui.components.TvActionButton

@Composable
internal fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    nextFocus: FocusRequester? = null,
) {
    if (!LocalContext.current.isTelevision()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            trailingIcon = trailingIcon,
            modifier = modifier,
        )
        return
    }

    var editing by remember { mutableStateOf(false) }
    var usedEditor by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var moveToNext by remember { mutableStateOf(false) }
    var restoringFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    fun finish(moveNext: Boolean) {
        keyboard?.hide()
        restoringFocus = true
        moveToNext = moveNext
        editing = false
    }

    Column(modifier) {
        if (editing) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                placeholder = placeholder?.let { { Text(it) } },
                supportingText = supportingText?.let { { Text(it) } },
                singleLine = true,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { finish(moveNext = true) }),
                trailingIcon = trailingIcon,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
                    if (it.hasFocus) hadFocus = true
                    else if (hadFocus && !restoringFocus) {
                        hadFocus = false
                        keyboard?.hide()
                        usedEditor = false
                        editing = false
                    }
                },
            )
        } else {
            val display = if (value.isEmpty()) placeholder ?: "Vide · OK pour saisir"
                else visualTransformation.filter(AnnotatedString(value)).text.text
            TvActionButton(
                label = "$label\n$display",
                onClick = { usedEditor = true; hadFocus = false; editing = true },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            supportingText?.let { Text(it) }
        }
    }

    BackHandler(enabled = editing) { finish(moveNext = false) }
    LaunchedEffect(editing) {
        if (usedEditor) {
            withFrameNanos { }
            if (editing) {
                focus.requestFocus()
                keyboard?.show()
            } else {
                hadFocus = false
                (if (moveToNext) nextFocus ?: focus else focus).requestFocus()
                moveToNext = false
                usedEditor = false
                restoringFocus = false
            }
        }
    }
}
