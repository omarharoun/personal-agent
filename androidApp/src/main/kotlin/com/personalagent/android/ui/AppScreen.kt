package com.personalagent.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalagent.android.AppContainer
import com.personalagent.android.ui.theme.ThemeMode
import com.personalagent.shared.cloud.CloudProvider
import kotlinx.coroutines.launch

/** Which surface is showing inside the drawer host. */
private enum class Surface { CONVERSATION, SETTINGS, SUPPORT, NOTES }

/**
 * The app shell — an Open-WebUI-style chat surface: a slide-out navigation drawer
 * (New chat · chat history · Notes / Settings / Support), a top bar with a
 * model/provider selector, a markdown-rendered transcript, and a rounded composer.
 *
 * All prior capabilities are intact: notes/reminders/planning are still invoked
 * behind the scenes by the shared IntentRouter, BYO cloud keys + the on-device
 * model live in Settings, and the 🔒 consent-first crisis-safety "Support" surface
 * is still reachable (now from the drawer). The 18+ gate + onboarding still run in
 * MainActivity before this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(
    vm: AppViewModel,
    safetyVm: SafetyViewModel,
    container: AppContainer,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val convoVm: ConversationViewModel =
        viewModel(factory = ConversationViewModel.Factory(container, vm))

    var surface by remember { mutableStateOf(Surface.CONVERSATION) }
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val appState by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(appState.message) {
        appState.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    fun closeDrawer() = scope.launch { drawerState.close() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                convoVm = convoVm,
                currentSurface = surface,
                onNewChat = { convoVm.newChat(); surface = Surface.CONVERSATION; closeDrawer() },
                onSelectChat = { id -> convoVm.selectChat(id); surface = Surface.CONVERSATION; closeDrawer() },
                onOpenNotes = { surface = Surface.NOTES; closeDrawer() },
                onOpenSettings = { surface = Surface.SETTINGS; closeDrawer() },
                onOpenSupport = { surface = Surface.SUPPORT; closeDrawer() },
            )
        },
    ) {
        when (surface) {
            Surface.SETTINGS -> SubScreen("Settings", { surface = Surface.CONVERSATION }, snackbar) {
                SettingsScreen(container, themeMode, onThemeModeChange)
            }
            Surface.SUPPORT -> SubScreen("Support", { surface = Surface.CONVERSATION }, snackbar) {
                SafetyScreen(safetyVm, snackbar)
            }
            Surface.NOTES -> SubScreen("Notes", { surface = Surface.CONVERSATION }, snackbar) {
                NotesScreen(appState, vm)
            }
            Surface.CONVERSATION -> ConversationContent(
                convoVm = convoVm,
                container = container,
                snackbar = snackbar,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                onOpenSettings = { surface = Surface.SETTINGS },
            )
        }
    }
}

// --- Navigation drawer -------------------------------------------------------
@Composable
private fun AppDrawer(
    convoVm: ConversationViewModel,
    currentSurface: Surface,
    onNewChat: () -> Unit,
    onSelectChat: (Long) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    val sessions by convoVm.sessions.collectAsStateWithLifecycle()
    val currentId by convoVm.currentChatId.collectAsStateWithLifecycle()

    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight().widthIn(max = 320.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(
                "Personal Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 12.dp),
            )

            // New chat — primary, pill-shaped.
            Surface(
                onClick = onNewChat,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onSurface)
                    Text("New chat", color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Recent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )

            // Chat history — most recent first; the empty placeholder chat is hidden.
            val history = sessions.filter { it.messages.isNotEmpty() || it.id == currentId }.reversed()
            LazyColumn(Modifier.weight(1f)) {
                items(history, key = { it.id }) { s ->
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.ChatBubbleOutline, null) },
                        label = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = s.id == currentId && currentSurface == Surface.CONVERSATION,
                        onClick = { onSelectChat(s.id) },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Description, null) },
                label = { Text("Notes") },
                selected = currentSurface == Surface.NOTES,
                onClick = onOpenNotes,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.FavoriteBorder, null) },
                label = { Text("Support") },
                selected = currentSurface == Surface.SUPPORT,
                onClick = onOpenSupport,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Settings, null) },
                label = { Text("Settings") },
                selected = currentSurface == Surface.SETTINGS,
                onClick = onOpenSettings,
            )
        }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
            content()
        }
    }
}

// --- Conversation surface ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationContent(
    convoVm: ConversationViewModel,
    container: AppContainer,
    snackbar: SnackbarHostState,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val messages by convoVm.messages.collectAsStateWithLifecycle()
    val sending by convoVm.sending.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, sending) {
        val count = messages.size + if (sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                title = { ModelSelector(container, onAddKey = onOpenSettings) },
                actions = {
                    IconButton(onClick = { convoVm.newChat() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        // Scaffold owns ONLY the top inset (status bar); the composer owns the
        // bottom (ime ∪ navigation bar). Otherwise the default systemBars insets
        // would re-apply the nav-bar inset to this Column AND the composer.
        contentWindowInsets = WindowInsets.statusBars,
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (messages.isEmpty()) {
                HomeEmptyState(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onPrompt = { convoVm.send(it) },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp, vertical = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(messages, key = { it.id }) { msg -> MessageRow(msg) }
                    if (sending) item("typing") { TypingIndicator() }
                }
            }

            Composer(
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

/** Top-bar model/provider selector. Reflects + sets the active route (on-device
 *  vs a BYO-key cloud provider). Picking a provider with no key opens Settings. */
@Composable
private fun ModelSelector(container: AppContainer, onAddKey: () -> Unit) {
    val keyStore = container.cloudKeyStore
    var refresh by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }

    val active = remember(refresh) { keyStore.activeProvider() }
    val label = remember(refresh) {
        active?.takeIf { keyStore.hasKey(it) }?.displayName ?: "On-device"
    }

    Box {
        Row(
            modifier = Modifier
                .clickable { open = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("On-device") },
                onClick = { keyStore.clearActiveProvider(); refresh++; open = false },
            )
            CloudProvider.entries.forEach { p ->
                val has = keyStore.hasKey(p)
                DropdownMenuItem(
                    text = { Text(if (has) p.displayName else "${p.displayName} — add key") },
                    onClick = {
                        open = false
                        if (has) { keyStore.setActiveProvider(p); refresh++ } else onAddKey()
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageRow(msg: Message) {
    when (msg.role) {
        Message.Role.USER -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                Text(
                    text = msg.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        Message.Role.SYSTEM -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = msg.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Assistant: full-width, no bubble, markdown-rendered.
        Message.Role.ASSISTANT -> MarkdownText(
            text = msg.text,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Thinking…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Composer ----------------------------------------------------------------
@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier
                .fillMaxWidth()
                // Sit flush above the keyboard when it's open, and above the nav bar
                // when it's closed — the UNION (max per side) of the IME and
                // navigation-bar insets, NOT their sum. Summing them (the old bug:
                // navigationBarsPadding() + imePadding()) double-counted the nav-bar
                // height on top of the keyboard, leaving a black gap above the IME.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        if (draft.isEmpty()) {
                            Text(
                                "Message Personal Agent…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) onSend() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    FilledIconButton(
                        onClick = onSend,
                        enabled = draft.isNotBlank() && !sending,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

/**
 * Empty state shown for a fresh chat: a centered greeting + tappable suggestion
 * cards that demonstrate what the agent can do (notes, reminders, planning,
 * thinking-through). Tapping one sends it.
 */
@Composable
private fun HomeEmptyState(
    onPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "What's on your mind?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Talk to me. I take notes, set reminders, track your plan, and think things " +
                "through — on your device. Add an API key or install a model to chat.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(28.dp))

        // 2-column suggestion grid.
        examples.chunked(2).forEach { rowItems ->
            Row(
                Modifier.fillMaxWidth().widthIn(max = 520.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { (label, prompt) ->
                    SuggestionCard(label, prompt, Modifier.weight(1f)) { onPrompt(prompt) }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SuggestionCard(label: String, prompt: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(96.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
