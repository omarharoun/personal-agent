package com.personalagent.android.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A plain-language guide for the hardest part of a bring-your-own-Hermes app:
 * getting a Hermes running and pointing the app at it. Shown from the Connect
 * screen ("Don't have Hermes yet?"). Deliberately concrete — real commands, the
 * default port, and how to find the address from a phone.
 */
@Composable
fun SetupGuideScreen(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            "Your Life Agent runs on a Hermes agent that you own — on your computer or a " +
                "small always-on server. Your notes, memories, and reminders live there, " +
                "not on anyone else's servers. Here's how to get it running.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Step(1, "Install Hermes on your computer") {
            Mono("pip install hermes-agent\nhermes setup")
            Text(
                "Follow the setup wizard and point it at any model provider it offers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Step(2, "Turn on the API server") {
            Text(
                "Add these to ~/.hermes/.env (pick your own secret key):",
                style = MaterialTheme.typography.bodyMedium,
            )
            Mono("API_SERVER_ENABLED=true\nAPI_SERVER_KEY=choose-a-long-secret\nAPI_SERVER_HOST=0.0.0.0")
            Text(
                "Use 0.0.0.0 only if your phone connects over your home network; keep 127.0.0.1 " +
                    "if you'll use a tunnel/VPN. That secret is the “API key” you enter here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Step(3, "Start it") {
            Mono("hermes gateway run")
            Text(
                "It listens on port 8642 by default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Step(4, "Find the address & connect") {
            Text(
                "On the same computer: http://localhost:8642. From your phone on the same Wi-Fi: " +
                    "use the computer's local IP, e.g. http://192.168.1.20:8642 (find it in your " +
                    "computer's network settings). Enter that address plus your key on the previous " +
                    "screen and tap Test & Connect.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                "Tip: away from home? A free tunnel (e.g. Tailscale or an SSH tunnel) lets your " +
                    "phone reach your Hermes securely without exposing it to the internet.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }
            Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Step(n: Int, title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    "$n",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun Mono(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}
