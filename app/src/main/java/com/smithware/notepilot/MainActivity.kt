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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
                    Screen.Inbox -> CaptureListScreen("Inbox", state.filtered.filter { it.section == Section.Inbox && !it.archived }, state.query, viewModel, showWorkFilter = true)
                    Screen.Notes -> CaptureListScreen("Notes", state.filtered.filter { it.section == Section.Notes || it.type in setOf(CaptureType.PlainNote, CaptureType.IdeaNote) && !it.archived }, state.query, viewModel, showWorkFilter = true)
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
    var thoughtDumpMode by remember { mutableStateOf(false) }
    var thoughtDumpProcessing by remember { mutableStateOf(false) }
    var thoughtDumpDrafts by remember { mutableStateOf<List<FormattedCapture>?>(null) }
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
                if (text.isBlank()) return
                if (thoughtDumpMode) {
                    thoughtDumpMode = false
                    thoughtDumpProcessing = true
                    scope.launch {
                        thoughtDumpDrafts = viewModel.formatThoughtDump(text)
                        thoughtDumpProcessing = false
                    }
                } else {
                    draft = viewModel.format(text)
                }
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

    fun startListening(forThoughtDump: Boolean = false) {
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
        thoughtDumpMode = forThoughtDump
        listening = true
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (forThoughtDump) "Dump your thoughts -- I'll sort them out" else "Speak naturally")
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
                Button(onClick = { startListening(forThoughtDump = true) }) {
                    Icon(Icons.Default.Lightbulb, null)
                    Text(" Thought dump")
                }
            }
        }
        if (thoughtDumpProcessing) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text("Sorting your thought dump into notes…")
                    }
                }
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
    thoughtDumpDrafts?.let { ThoughtDumpReviewDialog(it, viewModel, onDismiss = { thoughtDumpDrafts = null }) }
}

private data class ThoughtDumpItemState(
    val formatted: FormattedCapture,
    val title: String,
    val content: String,
    val accepted: Boolean
)

@Composable
private fun ThoughtDumpReviewDialog(drafts: List<FormattedCapture>, viewModel: NotePilotViewModel, onDismiss: () -> Unit) {
    var items by remember(drafts) {
        mutableStateOf(drafts.map { ThoughtDumpItemState(it, it.title, it.cleanedText, accepted = true) })
    }
    val acceptedCount = items.count { it.accepted }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review thought dump (${items.size} note${if (items.size == 1) "" else "s"})") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(items) { index, entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = entry.accepted,
                                    onCheckedChange = { checked ->
                                        items = items.toMutableList().also { it[index] = entry.copy(accepted = checked) }
                                    }
                                )
                                Text("${entry.formatted.type}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                            OutlinedTextField(
                                value = entry.title,
                                onValueChange = { value -> items = items.toMutableList().also { it[index] = entry.copy(title = value) } },
                                label = { Text("Title") },
                                enabled = entry.accepted,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = entry.content,
                                onValueChange = { value -> items = items.toMutableList().also { it[index] = entry.copy(content = value) } },
                                label = { Text("Note") },
                                enabled = entry.accepted,
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (entry.formatted.detectedTags.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    entry.formatted.detectedTags.forEach { tag ->
                                        AssistChip(onClick = {}, label = { Text(tag) })
                                    }
                                }
                            }
                            entry.formatted.detectedDateTime?.let {
                                Text("Due ${it.formatReminderTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (entry.formatted.warnings.isNotEmpty()) {
                                Text(entry.formatted.warnings.joinToString("\n"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = acceptedCount > 0,
                onClick = {
                    items.filter { it.accepted }.forEach { entry ->
                        viewModel.saveFormatted(
                            entry.formatted,
                            entry.title,
                            entry.content,
                            Section.Inbox,
                            entry.formatted.detectedDateTime,
                            entry.formatted.reminderDateTime
                        )
                    }
                    onDismiss()
                }
            ) { Text("Save $acceptedCount note${if (acceptedCount == 1) "" else "s"}") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Discard all") }
        }
    )
}

@Composable
private fun ReviewDialog(formatted: FormattedCapture, viewModel: NotePilotViewModel, onDismiss: () -> Unit) {
    var title by remember(formatted.rawTranscript) { mutableStateOf(formatted.title) }
    var content by remember(formatted.rawTranscript) { mutableStateOf(formatted.cleanedText) }
    var section by remember { mutableStateOf(Section.Inbox) }
    var dueAt by remember(formatted.detectedDateTime) { mutableStateOf(formatted.detectedDateTime) }
    var reminderAt by remember(formatted.reminderDateTime) { mutableStateOf(formatted.reminderDateTime) }
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
                            Text("Deadline ${dueAt?.formatReminderTime() ?: "not set"}", fontWeight = FontWeight.Bold)
                            Text("Reminder ${reminderAt?.formatReminderTime() ?: "off"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {
                                    dueAt = System.currentTimeMillis() + 30 * 60_000L
                                    reminderAt = null
                                }, label = { Text("Due 30 min") })
                                AssistChip(onClick = {
                                    dueAt = System.currentTimeMillis() + 2 * 60 * 60_000L
                                    reminderAt = dueAt?.minus(30 * 60_000L)
                                }, label = { Text("Due 2h") })
                                AssistChip(onClick = {
                                    dueAt = Calendar.getInstance().apply {
                                        add(Calendar.DAY_OF_YEAR, 1)
                                        set(Calendar.HOUR_OF_DAY, 9)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis
                                    reminderAt = dueAt?.minus(30 * 60_000L)
                                }, label = { Text("Tomorrow 9") })
                                AssistChip(onClick = { reminderAt = dueAt?.minus(30 * 60_000L) }, label = { Text("Remind 30 min before") })
                                AssistChip(onClick = { reminderAt = dueAt }, label = { Text("At deadline") })
                                AssistChip(onClick = { reminderAt = null }, label = { Text("No notification") })
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
                viewModel.saveFormatted(formatted, title, content, section, dueAt, reminderAt)
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
private fun CaptureListScreen(
    title: String,
    captures: List<CaptureEntity>,
    query: String,
    viewModel: NotePilotViewModel,
    showWorkFilter: Boolean = false
) {
    var workOnly by remember { mutableStateOf(false) }
    val visible = if (workOnly) captures.filter { it.tagList.any { tag -> tag.equals("work", ignoreCase = true) } } else captures
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
        if (showWorkFilter) {
            item {
                FilterChip(selected = workOnly, onClick = { workOnly = !workOnly }, label = { Text("Work") })
            }
        }
        if (visible.isEmpty()) {
            item {
                EmptyState(
                    if (workOnly) "No work-tagged items" else "Nothing here",
                    if (workOnly) "Items get tagged \"work\" automatically from a thought dump when they sound job-related."
                    else "Saved captures stay searchable and can be moved from the Inbox."
                )
            }
        } else {
            items(visible, key = { it.id }) { CaptureCard(it, viewModel) }
        }
    }
}

@Composable
private fun CaptureCard(capture: CaptureEntity, viewModel: NotePilotViewModel) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var editCapture by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(capture.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${capture.type}  ${dateLabel(capture.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (capture.pinned) Icon(Icons.Default.PushPin, "Pinned")
            }
            Text(capture.cleanedContent.ifBlank { capture.rawTranscript }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val isWorkTagged = capture.tagList.any { it.equals("work", ignoreCase = true) }
            if (capture.detectedDateTime != null || capture.reminderDateTime != null || isWorkTagged) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (isWorkTagged) AssistChip(onClick = {}, label = { Text("Work") })
                    if (capture.detectedDateTime != null) AssistChip(onClick = {}, label = { Text("Due ${capture.detectedDateTime.formatReminderTime()}") }, leadingIcon = { Icon(Icons.Default.Notifications, null) })
                    if (capture.reminderDateTime != null) AssistChip(onClick = {}, label = { Text("Alert ${capture.reminderDateTime.formatReminderTime()}") })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { editCapture = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Text(" Edit")
                }
                Spacer(Modifier.weight(1f))
                Box {
                    Button(onClick = { showMoreMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("More")
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(text = { Text(if (capture.pinned) "Unpin note" else "Pin note") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { showMoreMenu = false; viewModel.pin(capture) })
                        DropdownMenuItem(text = { Text(if (capture.completed) "Mark not done" else "Mark done") }, leadingIcon = { Icon(Icons.Default.CheckCircle, null) }, onClick = { showMoreMenu = false; viewModel.complete(capture) })
                        DropdownMenuItem(text = { Text("Convert to checklist") }, leadingIcon = { Icon(Icons.Default.List, null) }, onClick = { showMoreMenu = false; viewModel.convertToChecklist(capture) })
                        DropdownMenuItem(text = { Text("Convert transcript to tasks") }, leadingIcon = { Icon(Icons.Default.Task, null) }, onClick = { showMoreMenu = false; viewModel.convertTranscriptToTasks(capture) })
                        listOf(Section.Notes, Section.Tasks, Section.Lists, Section.Ideas).forEach {
                            DropdownMenuItem(text = { Text("Move to ${it.name}") }, onClick = { showMoreMenu = false; viewModel.move(capture, it) })
                        }
                        DropdownMenuItem(text = { Text("Archive") }, leadingIcon = { Icon(Icons.Default.Archive, null) }, onClick = { showMoreMenu = false; viewModel.archive(capture) })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { showMoreMenu = false; viewModel.delete(capture) })
                    }
                }
            }
        }
    }
    if (editCapture) EditCaptureDialog(capture, viewModel, onDismiss = { editCapture = false })
}

@Composable
private fun EditCaptureDialog(capture: CaptureEntity, viewModel: NotePilotViewModel, onDismiss: () -> Unit) {
    var title by remember(capture.id) { mutableStateOf(capture.title) }
    var content by remember(capture.id) { mutableStateOf(capture.cleanedContent) }
    var type by remember(capture.id) { mutableStateOf(capture.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit note") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(content, { content = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), minLines = 5) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CaptureType.entries.forEach {
                            FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.name) })
                        }
                    }
                }
                item {
                    Text("Raw transcript is preserved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.updateCapture(capture, title, content, type)
                onDismiss()
            }) { Text("Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsScreen(state: NotePilotState, viewModel: NotePilotViewModel) {
    var apiKeyDraft by remember(state.settings.anthropicApiKey) { mutableStateOf(state.settings.anthropicApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI-powered thought dump", fontWeight = FontWeight.Bold)
                    Text(
                        "Optional. When on, a longer voice dump can be split into several organized notes " +
                            "using the Anthropic API instead of NotePilot's local rules. Requires your own API " +
                            "key and a network connection; without one, thought dumps still work using the " +
                            "same local, offline rules as every other capture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingsRow("Enable AI-powered thought dump", state.settings.aiThoughtDumpEnabled) {
                        viewModel.setAiThoughtDumpEnabled(it)
                    }
                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        label = { Text("Anthropic API key") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None
                            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showApiKey = !showApiKey }) { Text(if (showApiKey) "Hide" else "Show") }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.setAnthropicApiKey(apiKeyDraft) },
                        enabled = apiKeyDraft != state.settings.anthropicApiKey,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save key") }
                }
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
