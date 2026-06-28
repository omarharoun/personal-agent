package com.personalagent.android.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personalagent.shared.age.CalendarDate
import com.personalagent.shared.age.MINIMUM_AGE_YEARS
import com.personalagent.shared.age.meetsMinimumAge
import java.time.LocalDate

/**
 * 🔞 18+ age gate — the FIRST onboarding step, before Welcome/Recovery.
 *
 * Collects a date of birth and uses the shared, unit-tested
 * [meetsMinimumAge] to decide eligibility:
 *  - 18 or older → [onConfirmed] (the caller persists the confirmation + proceeds).
 *  - under 18 (or the explicit "I'm under 18" choice) → a polite blocking screen.
 *    There is no path forward from there — the app does not open for under-18s.
 *
 * The decision is purely local and on-device; the date of birth is NOT stored
 * (only the boolean "confirmed 18+" is persisted by the caller).
 */
@Composable
fun AgeGateScreen(onConfirmed: () -> Unit) {
    var blocked by remember { mutableStateOf(false) }

    if (blocked) {
        UnderageBlockedScreen(onBack = { blocked = false })
        return
    }

    var day by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var showInvalid by remember { mutableStateOf(false) }

    fun parsedDob(): CalendarDate? {
        val d = day.toIntOrNull() ?: return null
        val m = month.toIntOrNull() ?: return null
        val y = year.toIntOrNull() ?: return null
        val dob = CalendarDate(year = y, month = m, day = d)
        return if (dob.isPlausible()) dob else null
    }

    val canSubmit = day.isNotBlank() && month.isNotBlank() && year.length == 4

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "You must be 18 or older to use this app",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Personal Agent is intended for adults. Please confirm your date of birth " +
                "to continue. This is checked on your device and your date of birth is " +
                "not stored.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(24.dp))
        Text("Date of birth", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = day,
                onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { day = it; showInvalid = false } },
                label = { Text("Day") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(88.dp),
            )
            OutlinedTextField(
                value = month,
                onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { month = it; showInvalid = false } },
                label = { Text("Month") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
            OutlinedTextField(
                value = year,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { year = it; showInvalid = false } },
                label = { Text("Year") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
            )
        }

        if (showInvalid) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Please enter a valid date of birth.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val dob = parsedDob()
                val now = LocalDate.now()
                val today = CalendarDate(now.year, now.monthValue, now.dayOfMonth)
                when {
                    dob == null -> showInvalid = true
                    meetsMinimumAge(dob, today, MINIMUM_AGE_YEARS) -> onConfirmed()
                    else -> blocked = true
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { blocked = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("I'm under 18") }
    }
}

@Composable
private fun UnderageBlockedScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Personal Agent isn't available to you yet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "This app is intended for people who are 18 or older, so we can't let you " +
                "continue right now. Thank you for your understanding — please come back " +
                "when you're 18.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        // Lets someone correct a mistyped date — there is still no way into the app
        // without a date of birth that meets the 18+ requirement.
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("I entered the wrong date")
        }
    }
}
