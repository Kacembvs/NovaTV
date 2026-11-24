package com.nova.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import com.nova.iptv.ui.components.NovaTextButton as TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.iptv.data.PinHasher
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.launch

/**
 * PIN entry. [onSubmit] returns null on success or an error message; with [confirm] the PIN must be typed twice
 * (used when creating it).
 */
@Composable
fun PinDialog(
    title: String,
    message: String?,
    confirm: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> String?,
) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val canSubmit = PinHasher.isValidPin(pin) && (!confirm || PinHasher.isValidPin(again))

    fun submit() {
        error = when {
            !PinHasher.isValidPin(pin) -> "Le code doit contenir 4 à 8 chiffres."
            confirm && pin != again -> "Les deux codes ne correspondent pas."
            else -> null
        }
        if (error == null) scope.launch {
            error = onSubmit(pin)
            if (error != null) pin = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = NovaColors.Cyan) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                message?.let { Text(it, fontSize = 13.sp) }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> if (v.length <= 8 && v.all(Char::isDigit)) pin = v },
                    label = { Text("Code PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = if (confirm) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { confirmFocus.requestFocus() },
                        onDone = {
                            keyboard?.hide()
                            if (canSubmit) submitFocus.requestFocus()
                        },
                    ),
                    modifier = Modifier.focusRequester(focus)
                        .focusProperties { if (!confirm && canSubmit) down = submitFocus },
                )
                if (confirm) {
                    OutlinedTextField(
                        value = again,
                        onValueChange = { v -> if (v.length <= 8 && v.all(Char::isDigit)) again = v },
                        label = { Text("Confirmer le code") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            if (canSubmit) submitFocus.requestFocus()
                        }),
                        modifier = Modifier.focusRequester(confirmFocus)
                            .focusProperties { if (canSubmit) down = submitFocus },
                    )
                }
                error?.let { Text(it, color = NovaColors.Error, fontSize = 13.sp) }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    focus.requestFocus()
                }
            }
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = canSubmit, modifier = Modifier.focusRequester(submitFocus)) { Text("Valider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** "N catégorie(s) masquée(s)" button that unlocks them with the PIN. */
@Composable
fun HiddenCategoriesButton(count: Int, onUnlock: suspend (String) -> Boolean, modifier: Modifier = Modifier) {
    if (count == 0) return
    var asking by remember { mutableStateOf(false) }
    TextButton(onClick = { asking = true }, modifier = modifier) {
        Icon(Icons.Rounded.Lock, contentDescription = null)
        Text(" $count catégorie(s) masquée(s) — déverrouiller", fontSize = 13.sp)
    }
    if (asking) {
        PinDialog(
            title = "Contrôle parental",
            message = "Saisissez le code PIN pour afficher les catégories protégées.",
            onDismiss = { asking = false },
        ) { pin ->
            if (onUnlock(pin)) {
                asking = false
                null
            } else {
                "Code incorrect."
            }
        }
    }
}

