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
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import com.personalagent.android.AppContainer
import com.personalagent.android.safety.ContactIntents
import com.personalagent.android.ui.theme.HermesText
import com.personalagent.android.ui.theme.ThemeMode
import com.personalagent.android.ui.voice.rememberVoiceController
import com.personalagent.shared.cloud.CloudProvider
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which surface is showing inside the drawer host. */
private enum class Surface { DASHBOARD, CONVERSATION, HISTORY, KNOWLEDGE, SETTINGS, SUPPORT, SUPPORT_RESOURCES, NOTES, REMINDERS, GOALS, LEARNING, REFLECTION, TASKS, RUN_TASK, SKILLS }

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
    onDisconnect: () -> Unit,
    pendingDestination: String? = null,
    onDestinationHandled: () -> Unit = {},
) {
    val convoVm: ConversationViewModel =
        viewModel(factory = ConversationViewModel.Factory(container))
    val notesVm: NotesViewModel =
        viewModel(factory = NotesViewModel.Factory(container))
    val remindersVm: RemindersViewModel =
        viewModel(factory = RemindersViewModel.Factory(container))
    val goalsVm: GoalsViewModel =
        viewModel(factory = GoalsViewModel.Factory(container))
    val learningVm: LearningViewModel =
        viewModel(factory = LearningViewModel.Factory(container))
    val reflectionVm: ReflectionViewModel =
        viewModel(factory = ReflectionViewModel.Factory(container))
    val dashboardVm: DashboardViewModel =
        viewModel(factory = DashboardViewModel.Factory(container))
    val taskRunVm: TaskRunViewModel =
        viewModel(factory = TaskRunViewModel.Factory(container))
    val tasksVm: TasksViewModel =
        viewModel(factory = TasksViewModel.Factory(container))
    val skillsVm: SkillsViewModel =
        viewModel(factory = SkillsViewModel.Factory(container))
    val knowledgeVm: KnowledgeGraphViewModel =
        viewModel(factory = KnowledgeGraphViewModel.Factory(container))

    var surface by remember { mutableStateOf(Surface.DASHBOARD) }
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

    // Deep-link from a notification tap → open the requested surface.
    LaunchedEffect(pendingDestination) {
        when (pendingDestination) {
            "reminders" -> { remindersVm.refresh(); surface = Surface.REMINDERS; onDestinationHandled() }
            "reflection" -> { surface = Surface.REFLECTION; onDestinationHandled() }
        }
    }

    fun closeDrawer() = scope.launch { drawerState.close() }
    // Returning to the home refreshes it so task check-offs, new memos, and new
    // reminders made on a sub-screen show up immediately in the previews.
    fun backHome() { dashboardVm.refresh(); surface = Surface.DASHBOARD }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                convoVm = convoVm,
                currentSurface = surface,
                onOpenDashboard = { dashboardVm.refresh(); surface = Surface.DASHBOARD; closeDrawer() },
                onNewChat = { convoVm.newChat(); surface = Surface.CONVERSATION; closeDrawer() },
                onSelectChat = { id -> convoVm.selectChat(id); surface = Surface.CONVERSATION; closeDrawer() },
                onOpenHistory = { surface = Surface.HISTORY; closeDrawer() },
                onOpenKnowledge = { surface = Surface.KNOWLEDGE; closeDrawer() },
                onOpenNotes = { surface = Surface.NOTES; closeDrawer() },
                onOpenTasks = { tasksVm.refresh(); surface = Surface.TASKS; closeDrawer() },
                onOpenReminders = { remindersVm.refresh(); surface = Surface.REMINDERS; closeDrawer() },
                onOpenGoals = { surface = Surface.GOALS; closeDrawer() },
                onOpenLearning = { learningVm.reload(); learningVm.checkWebAvailability(); surface = Surface.LEARNING; closeDrawer() },
                onOpenReflection = { surface = Surface.REFLECTION; closeDrawer() },
                onOpenSkills = { surface = Surface.SKILLS; closeDrawer() },
                onOpenSettings = { surface = Surface.SETTINGS; closeDrawer() },
                onOpenSupport = { surface = Surface.SUPPORT; closeDrawer() },
            )
        },
    ) {
        when (surface) {
            Surface.DASHBOARD -> DashboardScreen(
                vm = dashboardVm,
                onOpenDrawer = { scope.launch { drawerState.open() } },
                nav = DashboardNav(
                    onChat = { surface = Surface.CONVERSATION },
                    onReminders = { remindersVm.refresh(); surface = Surface.REMINDERS },
                    onGoals = { surface = Surface.GOALS },
                    onReflection = { surface = Surface.REFLECTION },
                    onNotes = { notesVm.refresh(); surface = Surface.NOTES },
                    onTasks = { tasksVm.refresh(); surface = Surface.TASKS },
                    onRunTask = { surface = Surface.RUN_TASK },
                    onSkills = { surface = Surface.SKILLS },
                ),
            )
            Surface.SETTINGS -> SubScreen("Settings", { backHome() }, snackbar) {
                SettingsScreen(container, themeMode, onThemeModeChange, onDisconnect = onDisconnect)
            }
            Surface.SUPPORT -> SubScreen("Support", { backHome() }, snackbar) {
                SafetyScreen(safetyVm, snackbar, onFindSupport = { surface = Surface.SUPPORT_RESOURCES })
            }
            Surface.SUPPORT_RESOURCES -> SubScreen(
                "Support resources",
                { safetyVm.dismissSupport(); surface = Surface.SUPPORT },
                snackbar,
            ) {
                SupportResourcesScreen(
                    safetyVm,
                    snackbar,
                    onClose = { safetyVm.dismissSupport(); surface = Surface.SUPPORT },
                )
            }
            Surface.NOTES -> SubScreen("Memos", { backHome() }, snackbar) {
                NotesScreen(notesVm)
            }
            Surface.REMINDERS -> SubScreen("Reminders", { backHome() }, snackbar) {
                RemindersScreen(remindersVm)
            }
            Surface.GOALS -> SubScreen("Goals", { backHome() }, snackbar) {
                GoalsScreen(goalsVm, onOpenLearning = {
                    learningVm.reload(); learningVm.checkWebAvailability(); surface = Surface.LEARNING
                })
            }
            Surface.LEARNING -> SubScreen("Learning", { backHome() }, snackbar) {
                // 🔒 REVIEW REQUIRED — web-derived links open ONLY in the system
                // browser (ACTION_VIEW), never an in-app WebView of arbitrary HTML.
                val ctx = LocalContext.current
                LearningScreen(learningVm, onOpenUrl = { url -> ContactIntents.openUrl(ctx, url) })
            }
            Surface.REFLECTION -> SubScreen("Reflection", { backHome() }, snackbar) {
                ReflectionScreen(reflectionVm)
            }
            Surface.TASKS -> SubScreen("Tasks", { backHome() }, snackbar) {
                TasksScreen(tasksVm)
            }
            Surface.RUN_TASK -> SubScreen("Run a task", { backHome() }, snackbar) {
                TaskRunScreen(taskRunVm)
            }
            Surface.SKILLS -> SubScreen("Skills", { backHome() }, snackbar) {
                SkillsScreen(skillsVm)
            }
            Surface.HISTORY -> SubScreen("History", { backHome() }, snackbar) {
                ChatHistoryScreen(
                    vm = convoVm,
                    onOpenChat = { id -> convoVm.selectChat(id); surface = Surface.CONVERSATION },
                )
            }
            Surface.KNOWLEDGE -> SubScreen("Knowledge", { backHome() }, snackbar) {
                KnowledgeGraphScreen(knowledgeVm)
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
    onOpenDashboard: () -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenLearning: () -> Unit,
    onOpenReflection: () -> Unit,
    onOpenSkills: () -> Unit,
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
                "LIFE AGENT",
                style = HermesText.displayLarge,
                color = MaterialTheme.colorScheme.primary,
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
                "RECENT",
                style = HermesText.displayLabel,
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
                icon = { Icon(Icons.Filled.Dashboard, null) },
                label = { Text("Dashboard") },
                selected = currentSurface == Surface.DASHBOARD,
                onClick = onOpenDashboard,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.History, null) },
                label = { Text("History") },
                selected = currentSurface == Surface.HISTORY,
                onClick = onOpenHistory,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.AccountTree, null) },
                label = { Text("Knowledge") },
                selected = currentSurface == Surface.KNOWLEDGE,
                onClick = onOpenKnowledge,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Flag, null) },
                label = { Text("Goals") },
                selected = currentSurface == Surface.GOALS,
                onClick = onOpenGoals,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.School, null) },
                label = { Text("Learning") },
                selected = currentSurface == Surface.LEARNING,
                onClick = onOpenLearning,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.CheckCircle, null) },
                label = { Text("Tasks") },
                selected = currentSurface == Surface.TASKS,
                onClick = onOpenTasks,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Description, null) },
                label = { Text("Memos") },
                selected = currentSurface == Surface.NOTES,
                onClick = onOpenNotes,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Notifications, null) },
                label = { Text("Reminders") },
                selected = currentSurface == Surface.REMINDERS,
                onClick = onOpenReminders,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.SelfImprovement, null) },
                label = { Text("Reflection") },
                selected = currentSurface == Surface.REFLECTION,
                onClick = onOpenReflection,
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.AutoAwesome, null) },
                label = { Text("Skills") },
                selected = currentSurface == Surface.SKILLS,
                onClick = onOpenSkills,
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
                title = {
                    Text(
                        title.uppercase(),
                        style = HermesText.displayLabel.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
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
        // Edge-to-edge: the sub-screens (Notes/Tasks/Reminders/Goals/Settings) have
        // text fields, so fold the IME inset into the content insets — the bottom
        // padding grows with the keyboard and keeps fields reachable, without the
        // window resizing under us.
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
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
    // 🔒 Consent-first crisis surface (Gate 2). Non-null → show the support card.
    val activeCrisis by convoVm.activeCrisis.collectAsStateWithLifecycle()
    val trustedContacts = remember { mutableStateOf(emptyList<com.personalagent.shared.safety.TrustedContact>()) }
    LaunchedEffect(activeCrisis) {
        if (activeCrisis != null) trustedContacts.value = container.trustedContactsStore.all()
    }

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
                title = {
                    Text(
                        "LIFE AGENT",
                        style = HermesText.displayLabel.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
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
        // The transcript fills the surface; the composer FLOATS over it (no opaque
        // band under it) — the last messages scroll up behind the pill, messenger-style.
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (messages.isEmpty()) {
                HomeEmptyState(
                    modifier = Modifier.fillMaxSize(),
                    onPrompt = { convoVm.send(it) },
                )
            } else {
                // Select + copy anywhere in the transcript (long-press to select).
                SelectionContainer(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Bottom pad clears the floating composer so nothing hides behind it.
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(messages, key = { it.id }) { msg -> MessageRow(msg) }
                        if (sending) item("typing") { TypingIndicator() }
                    }
                }
            }

            // Crisis card + composer both float at the bottom (crisis above the pill).
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                // 🔒 Crisis support card (consent-first; contacts NO ONE automatically).
                activeCrisis?.let { crisis ->
                    SupportResponseCard(
                        response = crisis,
                        contacts = trustedContacts.value,
                        onDismiss = { convoVm.dismissCrisis() },
                        onContactMissingApp = { },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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
                    onVoiceFinal = { spoken -> convoVm.send(spoken) },
                    onAttach = { marker -> draft = (draft.trimEnd() + " " + marker).trim() },
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
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                Text(
                    text = msg.text,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
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

        // Assistant: full-width, no bubble, markdown-rendered, with save/copy actions
        // so any document the agent writes can be copied or shared out of the app.
        Message.Role.ASSISTANT -> Column(Modifier.fillMaxWidth()) {
            MarkdownText(
                text = msg.text,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (msg.text.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                MessageActions(msg.text)
            }
        }
    }
}

/** Copy / share affordances under an assistant reply — the way a created document
 *  leaves the app (share sheet → Files, Docs, Keep, email, …). No network of ours. */
@Composable
private fun MessageActions(text: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // Inside a SelectionContainer, buttons must opt out of text selection.
    DisableSelection {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Copy", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, "Share / save").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        }
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

/** One entry in the attachment dock. */
private data class AttachItem(val label: String, val icon: ImageVector)

/**
 * A polished, FLOATING messenger-style composer that sits over the transcript (no
 * opaque band under it) and docks flush above the keyboard. It carries:
 *  • a left **"+" attachment dock** — press-and-hold and slide up to a magnifying,
 *    macOS-Dock-style stack of options (or tap to open and tap an option); and
 *  • a trailing **send / hold-to-talk** control — tap to send typed text, or (when
 *    the field is empty) press-and-hold to record a voice message that the device
 *    transcribes on-device and sends as text. Slide away while holding to cancel.
 */
@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    onVoiceFinal: (String) -> Unit,
    onAttach: (String) -> Unit,
) {
    val density = LocalDensity.current

    // --- Voice (hold-to-talk) -------------------------------------------------
    var voiceCancelled by remember { mutableStateOf(false) }
    val voice = rememberVoiceController(onFinal = { text -> if (!voiceCancelled) onVoiceFinal(text) })

    // The recording indicator is driven PURELY by this touch state — set the
    // instant the finger presses the mic — NOT by the SpeechRecognizer. That way
    // the user ALWAYS sees immediate feedback on hold, even if the recognizer is
    // still starting, unavailable, or later fails. (Root cause of "nothing at all"
    // in v2.3.0: the indicator was gated on the recognizer's `listening` flag,
    // which never flipped visibly when permission was ungranted or the offline
    // recognizer errored instantly.)
    var micPressed by remember { mutableStateOf(false) }

    // Elapsed recording time (WhatsApp-style) — ticks the moment the finger presses.
    var elapsedSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(micPressed) {
        elapsedSec = 0
        if (micPressed) while (true) { delay(1000); elapsedSec++ }
    }
    // Voice status/error messages self-dismiss so they never linger.
    LaunchedEffect(voice.state.error) {
        if (voice.state.error != null) { delay(3500); voice.clearError() }
    }

    // --- Attachment dock state ------------------------------------------------
    var menuOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var plusWinTop by remember { mutableFloatStateOf(Float.NaN) }
    var fingerWinY by remember { mutableFloatStateOf(Float.NaN) }
    val optionCenters = remember { mutableStateListOf(Float.NaN, Float.NaN, Float.NaN) }
    val options = remember {
        listOf(
            AttachItem("Camera", Icons.Filled.PhotoCamera),
            AttachItem("Photo", Icons.Filled.Image),
            AttachItem("File", Icons.Filled.AttachFile),
        )
    }

    // System pickers. Callbacks are kept fresh (rememberUpdatedState) so they don't
    // capture a stale draft when a result arrives later.
    val attach by rememberUpdatedState(onAttach)
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) attach("[📷 photo]")
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attach("[🖼 ${uri.lastPathSegment ?: "image"}]")
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) attach("[📎 ${uri.lastPathSegment ?: "file"}]")
    }
    fun runOption(index: Int) {
        menuOpen = false
        when (index) {
            0 -> takePhoto.launch(null)
            1 -> pickPhoto.launch("image/*")
            2 -> pickFile.launch("*/*")
        }
    }

    val selectRadiusPx = with(density) { 44.dp.toPx() }
    val magnifyRadiusPx = with(density) { 130.dp.toPx() }

    fun nearestOption(): Int? {
        if (fingerWinY.isNaN()) return null
        var best = -1
        var bestDist = Float.MAX_VALUE
        optionCenters.forEachIndexed { i, c ->
            if (!c.isNaN()) {
                val d = abs(fingerWinY - c)
                if (d < bestDist) { bestDist = d; best = i }
            }
        }
        return if (best >= 0 && bestDist <= selectRadiusPx) best else null
    }

    // Backdrop is transparent — the pill floats over the page background.
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
    ) {
        // Dock options — a compact vertical column that grows straight UP from just
        // above the "+" button (anchored bottom-left), magnifying toward the finger.
        // Explicit bottom-anchored transition so it never reads as a left-edge drawer.
        AnimatedVisibility(
            visible = menuOpen || dragging,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
            ) {
                options.forEachIndexed { i, opt ->
                    val center = optionCenters.getOrNull(i) ?: Float.NaN
                    val scale = if (dragging && !fingerWinY.isNaN() && !center.isNaN()) {
                        val d = abs(fingerWinY - center)
                        1f + 0.55f * exp(-(d * d) / (2f * magnifyRadiusPx / 3f * (magnifyRadiusPx / 3f)))
                    } else 1f
                    val highlighted = dragging && nearestOption() == i
                    DockOption(
                        item = opt,
                        scale = scale,
                        highlighted = highlighted,
                        onClick = { runOption(i) },
                        modifier = Modifier.onGloballyPositioned {
                            optionCenters[i] = it.positionInWindow().y + it.size.height / 2f
                        },
                    )
                }
            }
        }

        // FLOATING composer — no pill/surface behind the text (per the user's ask);
        // the "+" and send controls keep their own circular backgrounds so they stay
        // legible, while the field itself floats directly over the page.
        run {
            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // The "+" attachment trigger (hold-and-slide, or tap to toggle).
                PlusButton(
                    open = menuOpen || dragging,
                    modifier = Modifier
                        .onGloballyPositioned { plusWinTop = it.positionInWindow().y }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val wasOpen = menuOpen
                                val down = awaitFirstDown()
                                dragging = true
                                menuOpen = true
                                var moved = false
                                fingerWinY = plusWinTop + down.position.y
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val ch = event.changes.firstOrNull() ?: break
                                    fingerWinY = plusWinTop + ch.position.y
                                    if (abs(ch.position.y - down.position.y) > 24f) moved = true
                                    if (!ch.pressed) break
                                }
                                dragging = false
                                val idx = nearestOption()
                                fingerWinY = Float.NaN
                                if (moved) {
                                    if (idx != null) runOption(idx)
                                    menuOpen = false
                                } else {
                                    // A tap toggles the persistent (tap-to-select) menu.
                                    menuOpen = !wasOpen
                                }
                            }
                        },
                )

                Box(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = 8.dp)) {
                    val voiceError = voice.state.error
                    if (voice.state.downloading) {
                        // One-time offline-model setup — show real progress so the
                        // "model not downloaded yet" state is never a silent no-op.
                        val pct = (voice.state.downloadProgress * 100).toInt()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                "Setting up offline voice… $pct%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (micPressed) {
                        // Recording indicator — shown purely because the finger is
                        // down on the mic (independent of the recognizer). Pulsing
                        // red dot + mm:ss elapsed, then the live transcript if/when
                        // words come through.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(9.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "%d:%02d".format(elapsedSec / 60, elapsedSec % 60),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                voice.state.partial.ifBlank { "release to send · slide away to cancel" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (voice.state.partial.isBlank())
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else if (voiceError != null && draft.isEmpty()) {
                        // Never fail silently — tell the user why voice didn't record.
                        Text(
                            voiceError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        if (draft.isEmpty()) {
                            Text(
                                "Message your Life Agent…",
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
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank()) onSend() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
                SendOrMicButton(
                    hasText = draft.isNotBlank(),
                    sending = sending,
                    recording = micPressed,
                    onSend = onSend,
                    onHoldStart = {
                        // Flip the visible state FIRST — feedback is guaranteed even
                        // if voice.start() requests permission or the recognizer fails.
                        micPressed = true
                        voiceCancelled = false
                        voice.start()
                    },
                    onHoldEnd = { cancelled ->
                        micPressed = false
                        voiceCancelled = cancelled
                        voice.stop()
                    },
                )
            }
        }
    }
}

/** The circular "+" trigger; rotates to an × while the dock is open. */
@Composable
private fun PlusButton(open: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (open) Icons.Filled.Close else Icons.Filled.Add,
            contentDescription = if (open) "Close attachments" else "Add attachment",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A single dock option pill — magnifies via [scale], lights up when [highlighted]. */
@Composable
private fun DockOption(
    item: AttachItem,
    scale: Float,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        shadowElevation = if (highlighted) 8.dp else 3.dp,
        modifier = modifier
            .wrapContentWidth()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f),
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val fg = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Text(item.label, style = MaterialTheme.typography.bodyLarge, color = fg)
        }
    }
}

/** Trailing control: a tap-to-send arrow when there's text, else a hold-to-talk mic. */
@Composable
private fun SendOrMicButton(
    hasText: Boolean,
    sending: Boolean,
    recording: Boolean,
    onSend: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: (cancelled: Boolean) -> Unit,
) {
    if (hasText) {
        FilledIconButton(
            onClick = onSend,
            enabled = !sending,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.size(44.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    } else {
        // Empty field → hold the mic to record; release to send, slide away to cancel.
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // requireUnconsumed = false so nothing upstream can swallow
                        // the press; onHoldStart fires immediately on finger-down.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        android.util.Log.d("VoiceMic", "press down → onHoldStart")
                        onHoldStart()
                        var cancel = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull() ?: break
                                val dx = ch.position.x - down.position.x
                                val dy = ch.position.y - down.position.y
                                if (dx < -120f || dy < -120f) cancel = true
                                if (!ch.pressed) { ch.consume(); break }
                            }
                        } finally {
                            // try/finally guarantees release fires even if the gesture
                            // is cancelled (e.g. the permission dialog steals focus),
                            // so the indicator never gets stuck on.
                            android.util.Log.d("VoiceMic", "release → onHoldEnd(cancel=$cancel)")
                            onHoldEnd(cancel)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Hold to record a voice message",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
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
        "📝  Remember something" to "Remember that my sister's birthday is March 3rd",
        "⏰  Set a reminder" to "Remind me to call mom in 2 hours",
        "🧭  Talk it through" to "Help me think through a decision I'm facing",
        "🌱  Reflect" to "What patterns have you noticed in what I've told you?",
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
            "I'm your Life Agent, running on your own Hermes. I remember what matters to " +
                "you, keep your notes and reminders, and help you think things through.",
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
