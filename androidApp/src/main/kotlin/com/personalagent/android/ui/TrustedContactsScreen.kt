// 🔒 CRISIS-CRITICAL (Step 7) — consent-first only; autonomous action disabled; NOT-FOR-REAL-USERS until human + crisis-expert review.
package com.personalagent.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.personalagent.shared.safety.TrustedContact

/**
 * 🔒 CRISIS-CRITICAL (Step 7) — trusted-contacts setup. 🔒
 *
 * Lets the user add/remove the people they choose **in advance** as ones they might
 * want help reaching during a hard moment. Consent is captured up front: the "Add"
 * button stays disabled until the user ticks an explicit acknowledgement. Copy is
 * plain and reassuring — these are the user's own choices, and the app never reaches
 * out to anyone on its own.
 */
@Composable
fun TrustedContactsScreen(
    state: SafetyUiState,
    vm: SafetyViewModel,
) {
    Column(Modifier.fillMaxWidth()) {
        Text("People you trust", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose people you might want help reaching if you're ever going through " +
                "a hard time — like a friend or family member. You're in control: the " +
                "app will only ever open a call or message for you to send yourself, and " +
                "never contacts anyone on its own.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        AddTrustedContactForm(onAdd = vm::addContact)

        Spacer(Modifier.height(20.dp))

        if (state.contacts.isEmpty()) {
            Text(
                "You haven't added anyone yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            state.contacts.forEach { contact ->
                SavedContactRow(contact = contact, onRemove = { vm.removeContact(contact.id) })
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AddTrustedContactForm(
    onAdd: (name: String, phone: String, relationship: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Add someone", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = relationship,
                onValueChange = { relationship = it },
                label = { Text("Who they are to you (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            // 🔒 Explicit, up-front consent. The Add button is disabled until ticked.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = consent, onCheckedChange = { consent = it })
                Spacer(Modifier.width(8.dp))
                Text(
                    "I'm choosing this person myself, and I understand the app will only " +
                        "help me reach them when I tap to — it won't contact them on its own.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    onAdd(name, phone, relationship)
                    name = ""; phone = ""; relationship = ""; consent = false
                },
                enabled = consent && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add to my trusted people")
            }
        }
    }
}

@Composable
private fun SavedContactRow(
    contact: TrustedContact,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.bodyLarge)
            val detail = listOfNotNull(contact.relationship, contact.phone).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}
