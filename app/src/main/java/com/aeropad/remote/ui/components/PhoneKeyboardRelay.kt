package com.aeropad.remote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.aeropad.remote.domain.TextRelayDiff

/**
 * Type on the phone, see it appear on the host as you type.
 *
 * Round 15 — the reported problem was "cannot type from phone keyboard to
 * dialogue box (like typing in TV)". What existed was a text box with a Send
 * button: fine for pasting a URL, useless for a TV search field or a login
 * dialog, because nothing reached the host until you pressed Send, and the
 * phone's own keyboard never opened on its own.
 *
 * This field:
 *  - opens the phone's IME automatically (that is the "phone keyboard" part),
 *  - streams every committed change to the host while typing, using a diff so
 *    it sends only the new characters (or the right number of Backspaces when
 *    you delete), and
 *  - keeps Enter working as Enter, so it can submit a dialog on the host.
 *
 * Diffing lives in [TextRelayDiff] (pure Kotlin, unit-tested) because IMEs
 * like to rewrite the whole string (autocorrect, suggestions) rather than
 * append to it.
 */
@Composable
fun PhoneKeyboardRelay(
    onType: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type here — goes to your PC",
    autoFocus: Boolean = false
) {
    var text by remember { mutableStateOf("") }
    var lastSent by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun flushTo(host: String) {
        val plan = TextRelayDiff.plan(lastSent, host)
        repeat(plan.backspaces) { onBackspace() }
        if (plan.insert.isNotEmpty()) onType(plan.insert)
        lastSent = host
    }

    // Runs whenever the caller raises the flag too, not only on first
    // composition — the "Open phone keyboard" button would otherwise do
    // nothing on the second press.
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new
                    flushTo(new)
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        // Enter reaches the host, not just the local field.
                        onEnter()
                        flushTo("")
                        text = ""
                        focusManager.clearFocus()
                    }
                )
            )
            IconButton(onClick = {
                onBackspace()
                if (text.isNotEmpty()) {
                    text = text.dropLast(1)
                    lastSent = text
                }
            }) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Backspace on the host")
            }
            IconButton(onClick = {
                onEnter()
                flushTo("")
                text = ""
            }) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send Enter to the host")
            }
        }
        // One short line, no button: the "Open phone keyboard" chip below the
        // field already raises the IME, and a second control here made the
        // block taller than the keyboard it belongs to.
        Text(
            "Live — each character reaches the host as you type.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}
