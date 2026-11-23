package com.nova.iptv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.focusRequester(focus),
                )
                if (confirm) {
                    OutlinedTextField(
                        value = again,
                        onValueChange = { v -> if (v.length <= 8 && v.all(Char::isDigit)) again = v },
                        label = { Text("Confirmer le code") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
                error?.let { Text(it, color = NovaColors.Error, fontSize = 13.sp) }
            }
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = pin.length >= 4) { Text("Valider") } },
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

