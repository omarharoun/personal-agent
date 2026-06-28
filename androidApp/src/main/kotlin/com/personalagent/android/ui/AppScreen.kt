package com.personalagent.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.AppContainer

/** Which surface is showing. The home surface is the single conversation. */
private enum class Surface { CONVERSATION, SETTINGS, SUPPORT }

/**
 * UX Stream 1 — the single Claude-style conversational surface.
 *
 * Replaces the old 5-tab NavigationBar. The whole app is now one scrollable
 * transcript + a bottom input box; notes, reminders, and planning are capabilities
 * the agent invokes behind the scenes (see [ConversationViewModel] + the shared
 * IntentRouter). Settings lives behind a gear icon in the top bar, and the
 * crisis-safety "Support" surface stays reachable through the overflow menu —
 * 🔒 it is moved, never deleted, and the consent-first behaviour is unchanged.
 *
 * The age-gate + onboarding still run BEFORE this screen (in MainActivity); this is
 * only what shows AFTER onboarding completes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(vm: AppViewModel, safetyVm: SafetyViewModel, container: AppContainer) {
    var surface by remember { mutableStateOf(Surface.CONVERSATION) }
    val snackbar = remember { SnackbarHostState() }

    // Surface AppViewModel messages (e.g. reminder rejected) as snackbars, as before.
    val appState by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(appState.message) {
        appState.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    when (surface) {
        Surface.SETTINGS -> SubScreen(
            title = "Settings",
            onBack = { surface = Surface.CONVERSATION },
            snackbar = snackbar,
        ) { SettingsScreen(container) }

        // 🔒 Crisis-safety surface — still reachable, behaviour intact.
        Surface.SUPPORT -> SubScreen(
            title = "Support",
            onBack = { surface = Surface.CONVERSATION },
            snackbar = snackbar,
        ) { SafetyScreen(safetyVm, snackbar) }

        Surface.CONVERSATION -> ConversationSurface(
            container = container,
            appVm = vm,
            snackbar = snackbar,
            onOpenSettings = { surface = Surface.SETTINGS },
            onOpenSupport = { surface = Surface.SUPPORT },
        )
    }
}

/** A simple back-navigable wrapper hosting one of the secondary screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationSurface(
    container: AppContainer,
    appVm: AppViewModel,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    val convoVm: ConversationViewModel =
        viewModel(factory = ConversationViewModel.Factory(container, appVm))
    val messages by convoVm.messages.collectAsStateWithLifecycle()
    val sending by convoVm.sending.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Keep the newest message in view as the transcript grows.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Agent") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        // 🔒 Crisis-safety support — moved here from the old tab bar.
                        DropdownMenuItem(
                            text = { Text("Support") },
                            leadingIcon = {
                                Icon(Icons.Filled.Favorite, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onOpenSupport()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onOpenSettings()
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (messages.isEmpty()) {
                // Claude-style home: warm greeting + tappable example prompts that
                // demonstrate what the agent can actually do.
                HomeEmptyState(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onPrompt = { convoVm.send(it) },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
                }
            }

            InputBar(
                draft = draft,
                onDraftChange = { draft = it },
                sending = sending,
                onSend = {
                    val toSend = draft
                    draft = ""
                    convoVm.send(toSend)
                },
            )
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val alignment = when (msg.role) {
        Message.Role.USER -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }
    val container = when (msg.role) {
        Message.Role.USER -> MaterialTheme.colorScheme.primary
        Message.Role.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
        Message.Role.ASSISTANT -> MaterialTheme.colorScheme.surface
    }
    val content = when (msg.role) {
        Message.Role.USER -> MaterialTheme.colorScheme.onPrimary
        Message.Role.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
        Message.Role.ASSISTANT -> MaterialTheme.colorScheme.onSurface
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = container,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = if (msg.role == Message.Role.ASSISTANT) 2.dp else 0.dp,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = msg.text,
                color = content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message your agent…") },
                maxLines = 6,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = { if (draft.isNotBlank()) onSend() },
                ),
            )
            FilledIconButton(
                onClick = onSend,
                enabled = draft.isNotBlank() && !sending,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

/**
 * Claude-style home shown when the conversation is empty: a warm greeting and a
 * few tappable example prompts that demonstrate what the agent can actually do
 * (notes, reminders, planning, thinking-through). Tapping one sends it.
 */
@Composable
private fun HomeEmptyState(
    onPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // (label shown on the card, prompt text that gets sent)
    val examples = listOf(
        "📝  Take a note" to "Take a note: weekend trip ideas",
        "⏰  Set a reminder" to "Remind me to call mom in 2 hours",
        "✅  Plan something" to "Add to my plan: finish the budget",
        "💭  Think it through" to "Help me think through a decision",
    )

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "What's on your mind?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Just talk to me. I can take notes, set reminders, track your plan, and " +
                "think things through — all on your device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        examples.forEach { (label, prompt) ->
            Surface(
                onClick = { onPrompt(prompt) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "“$prompt”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Tip: an AI model isn't required for notes, reminders, and plans — those " +
                "work right away. Install a model (gear, top-right) or add an API key to chat.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
        )
    }
}
