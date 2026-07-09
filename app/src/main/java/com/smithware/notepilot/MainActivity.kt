@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.smithware.notepilot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smithware.notepilot.data.CaptureEntity
import com.smithware.notepilot.data.CaptureType
import com.smithware.notepilot.data.FormattedCapture
import com.smithware.notepilot.data.Section
import com.smithware.notepilot.format.formatReminderTime
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NotePilotRoot() }
    }
}

private enum class Screen(val label: String, val icon: ImageVector) {
    Capture("Capture", Icons.Default.Mic),
    Inbox("Inbox", Icons.Default.Inbox),
    Notes("Notes", Icons.Default.Note),
    Tasks("Tasks", Icons.Default.Task),
    Lists("Lists", Icons.Default.List),
    Archive("Archive", Icons.Default.Archive),
    Settings("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotePilotRoot(viewModel: NotePilotViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.Capture) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(colorScheme = if (state.settings.darkMode) darkScheme() else lightScheme()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (screen == Screen.Capture) "NotePilot" else screen.label, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label, maxLines = 1) }
                        )
                    }
                }
            }
        ) { padding ->
            Surface(Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.Capture -> CaptureScreen(state, viewModel, snackbar)
                    Screen.Inbox -> CaptureListScreen("Inbox", state.filtered.filter { it.section == Section.Inbox && !it.archived }, state.query, viewModel)
                    Screen.Notes -> CaptureListScreen("Notes", state.filtered.filter { it.section == Section.Notes || it.type in setOf(CaptureType.PlainNote, CaptureType.IdeaNote) && !it.archived }, state.query, viewModel)
                    Screen.Tasks -> CaptureListScreen("Tasks", state.filtered.filter { it.type in setOf(CaptureType.TodoList, CaptureType.ReminderDraft) && !it.archived }, state.query, viewModel)
                    Screen.Lists -> CaptureListScreen("Lists", state.filtered.filter { it.type in setOf(CaptureType.Checklist, CaptureType.ShoppingList) && !it.archived }, state.query, viewModel)
                    Screen.Archive -> CaptureListScreen("Archive", state.filtered.filter { it.archived || it.section == Section.Archive }, state.query, viewModel)
                    Screen.Settings -> SettingsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun CaptureScreen(state: NotePilotState, viewModel: NotePilotViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    var liveText by remember { mutableStateOf("") }
    var speechError by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<FormattedCapture?>(null) }
    var typedDialog by remember { mutableStateOf(false) }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snackbar.showSnackbar("Microphone permission denied. Typed capture is available.") }
    }
    val recognizerAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember {
        if (recognizerAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { speechError = null }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) {
                listening = false
                speechError = speechErrorMessage(error)
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                liveText = text
                if (text.isNotBlank()) draft = viewModel.format(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                liveText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer?.destroy() }
    }

    LaunchedEffect(state.settings.draftTranscript) {
        if (liveText.isBlank() && state.settings.draftTranscript.isNotBlank()) {
            liveText = state.settings.draftTranscript
        }
    }

    LaunchedEffect(liveText) {
        if (liveText.isNotBlank()) viewModel.saveDraftTranscript(liveText)
    }

    fun startListening() {
        if (!recognizerAvailable || recognizer == null) {
            speechError = "Speech recognition is not available on this device. Use typed capture."
            typedDialog = true
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        liveText = ""
        speechError = null
        draft = null
        listening = true
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak naturally")
        })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Say it. NotePilot organizes it.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                LargeFloatingActionButton(
                    onClick = { if (listening) { recognizer?.stopListening(); listening = false } else startListening() },
                    shape = CircleShape,
                    modifier = Modifier.size(128.dp),
                    containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, "Voice capture", modifier = Modifier.size(56.dp))
                }
                Text(if (listening) "Listening..." else "Tap to capture", modifier = Modifier.padding(top = 10.dp))
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { startListening() }) { Icon(Icons.Default.Mic, null); Text(" Voice note") }
                Button(onClick = { typedDialog = true }) { Icon(Icons.Default.Edit, null); Text(" Typed note") }
                Button(onClick = { typedDialog = true }) { Icon(Icons.Default.List, null); Text(" Checklist") }
                Button(onClick = { typedDialog = true }) { Icon(Icons.Default.Notifications, null); Text(" Reminder") }
            }
        }
        if (liveText.isNotBlank() || speechError != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Live transcript", fontWeight = FontWeight.Bold)
                        Text(liveText.ifBlank { speechError.orEmpty() })
                        if (speechError != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { startListening() }) { Text("Retry") }
                                TextButton(onClick = { typedDialog = true }) { Text("Type instead") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("Recent captures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (state.recent.isEmpty()) {
            item { EmptyState("No notes yet", "Capture a voice note or type a quick thought.") }
        } else {
            items(state.recent, key = { it.id }) { CaptureCard(it, viewModel) }
        }
    }

    draft?.let { ReviewDialog(it, viewModel, onDismiss = { draft = null }) }
    if (typedDialog) TypedDialog(viewModel, onDismiss = { typedDialog = false })
}

@Composable
private fun ReviewDialog(formatted: FormattedCapture, viewModel: NotePilotViewModel, onDismiss: () -> Unit) {
    var title by remember(formatted.rawTranscript) { mutableStateOf(formatted.title) }
    var content by remember(formatted.rawTranscript) { mutableStateOf(formatted.cleanedText) }
    var section by remember { mutableStateOf(Section.Inbox) }
    var dueAt by remember(formatted.detectedDateTime) { mutableStateOf(formatted.detectedDateTime) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review before saving") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Suggested: ${formatted.type}", fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(content, { content = it }, label = { Text("Cleaned note") }, modifier = Modifier.fillMaxWidth(), minLines = 4) }
                item {
                    Text("Raw transcript", fontWeight = FontWeight.Bold)
                    Text(formatted.rawTranscript, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (formatted.type == CaptureType.ReminderDraft) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Due ${dueAt?.formatReminderTime() ?: "not set"}", fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = { dueAt = System.currentTimeMillis() + 30 * 60_000L }, label = { Text("30 min") })
                                AssistChip(onClick = { dueAt = System.currentTimeMillis() + 2 * 60 * 60_000L }, label = { Text("2 hours") })
                                AssistChip(onClick = {
                                    dueAt = Calendar.getInstance().apply {
                                        add(Calendar.DAY_OF_YEAR, 1)
                                        set(Calendar.HOUR_OF_DAY, 9)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                }, label = { Text("Tomorrow 9") })
                                AssistChip(onClick = { dueAt = null }, label = { Text("No reminder") })
                            }
                        }
                    }
                }
                if (formatted.warnings.isNotEmpty()) item { Text(formatted.warnings.joinToString("\n"), color = MaterialTheme.colorScheme.error) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Section.entries.filter { it != Section.Archive }.forEach {
                            FilterChip(selected = section == it, onClick = { section = it }, label = { Text(it.name) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.saveFormatted(formatted, title, content, section, dueAt)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TypedDialog(viewModel: NotePilotViewModel, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CaptureType.PlainNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New typed capture") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CaptureType.PlainNote, CaptureType.Checklist, CaptureType.ReminderDraft).forEach {
                        FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.name) })
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = body.isNotBlank(), onClick = {
                viewModel.saveTyped(title, body, type)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CaptureListScreen(title: String, captures: List<CaptureEntity>, query: String, viewModel: NotePilotViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (captures.isEmpty()) {
            item { EmptyState("Nothing here", "Saved captures stay searchable and can be moved from the Inbox.") }
        } else {
            items(captures, key = { it.id }) { CaptureCard(it, viewModel) }
        }
    }
}

@Composable
private fun CaptureCard(capture: CaptureEntity, viewModel: NotePilotViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(capture.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${capture.type}  ${dateLabel(capture.updatedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (capture.pinned) Icon(Icons.Default.PushPin, "Pinned")
            }
            Text(capture.cleanedContent.ifBlank { capture.rawTranscript }, maxLines = 5, overflow = TextOverflow.Ellipsis)
            if (capture.detectedDateTime != null) AssistChip(onClick = {}, label = { Text("Due ${capture.detectedDateTime.formatReminderTime()}") }, leadingIcon = { Icon(Icons.Default.Notifications, null) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { viewModel.pin(capture) }) { Icon(Icons.Default.PushPin, "Pin") }
                IconButton(onClick = { viewModel.archive(capture) }) { Icon(Icons.Default.Archive, "Archive") }
                IconButton(onClick = { viewModel.delete(capture) }) { Icon(Icons.Default.Delete, "Delete") }
                IconButton(onClick = { viewModel.complete(capture) }) { Icon(Icons.Default.CheckCircle, "Complete") }
                TextButton(onClick = { viewModel.convertToChecklist(capture) }) { Text("Checklist") }
                TextButton(onClick = { viewModel.convertTranscriptToTasks(capture) }) { Text("Tasks") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Section.Notes, Section.Tasks, Section.Lists, Section.Ideas, Section.Archive).forEach {
                    AssistChip(onClick = { viewModel.move(capture, it) }, label = { Text(it.name) })
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: NotePilotState, viewModel: NotePilotViewModel) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SettingsRow("Dark mode", state.settings.darkMode) { viewModel.setDarkMode(it) } }
        item { SettingsRow("Keep screen on during capture", state.settings.keepScreenOnCapture) { viewModel.setKeepScreenOnCapture(it) } }
        item { SettingsRow("Mock premium flag", state.settings.mockPremium) { viewModel.setMockPremium(it) } }
        item {
            ElevatedButton(onClick = { viewModel.testNotification() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Notifications, null)
                Text(" Test notification")
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Free now", fontWeight = FontWeight.Bold)
                    Text("Voice capture, typed notes, checklist conversion, reminders, Inbox, search, and local storage.")
                    Text("Premium later", fontWeight = FontWeight.Bold)
                    Text("Advanced cleanup, smart folders, widgets, exports, backup/restore, style packs, and future AI summaries.")
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun speechErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "Audio capture failed. Retry or type the note."
    SpeechRecognizer.ERROR_CLIENT -> "Speech capture was cancelled."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice capture."
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service needs a working recognizer. Try offline keyboard voice typing if available or type the note."
    SpeechRecognizer.ERROR_NO_MATCH -> "No clear speech was detected. Retry or type the note."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
    SpeechRecognizer.ERROR_SERVER -> "Speech service failed. The transcript was not saved; type it instead."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
    else -> "Speech recognition failed with code $error."
}

private fun dateLabel(time: Long): String = SimpleDateFormat("MMM d h:mm a", Locale.getDefault()).format(Date(time))

@Composable
private fun lightScheme() = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF0F766E),
    secondary = Color(0xFF625B71),
    tertiary = Color(0xFF7D5260),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EC),
    secondaryContainer = Color(0xFFD9EDE9)
)

@Composable
private fun darkScheme() = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF5EEAD4),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF2A2A2A),
    secondaryContainer = Color(0xFF173B37)
)
