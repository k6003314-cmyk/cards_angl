package ru.linguacards

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import ru.linguacards.data.CardEntity
import ru.linguacards.data.DeckEntity
import ru.linguacards.data.DeckSummary
import ru.linguacards.data.FolderEntity
import ru.linguacards.data.ImportedWord

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(
                primary = Color(0xFF315E55), secondary = Color(0xFFB86A4D), background = Color(0xFFF7F8F4), surface = Color(0xFFF7F8F4)
            )) { Surface(Modifier.fillMaxSize()) { LinguaApp() } }
        }
    }
}

private enum class Page { HOME, STATS, DECK, SESSION, IMPORT }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LinguaApp(vm: AppViewModel = viewModel()) {
    val folders by vm.folders.collectAsState(initial = emptyList())
    val total by vm.total.collectAsState(initial = 0)
    val learned by vm.learned.collectAsState(initial = 0)
    val due by vm.due.collectAsState(initial = 0)
    val studiedToday by vm.studiedToday.collectAsState(initial = 0)
    val session by vm.session.collectAsState()
    var page by remember { mutableStateOf(Page.HOME) }
    var folderId by remember { mutableStateOf<String?>(null) }
    var deckId by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var dialogName by remember { mutableStateOf("") }
    var dialogObjectId by remember { mutableStateOf("") }
    var dialogFolderId by remember { mutableStateOf<String?>(null) }
    var isFlipped by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val import by vm.import.collectAsState()
    val currentDeckId = deckId
    val selectedDeck by (currentDeckId?.let(vm::deck) ?: remember { kotlinx.coroutines.flow.flowOf(null) }).collectAsState(initial = null)
    val decksFlow = remember(folderId) { vm.decks(folderId) }
    val decks by decksFlow.collectAsState(initial = emptyList())
    val cardsFlow = remember(currentDeckId) { currentDeckId?.let(vm::cards) }
    val deckCards by (cardsFlow ?: remember { kotlinx.coroutines.flow.flowOf(emptyList()) }).collectAsState(initial = emptyList())

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.readWorkbook(uri) { message -> scope.launch { snack.showSnackbar(message) } }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(when (page) {
                    Page.HOME -> if (folderId == null) "Лексика" else folders.firstOrNull { it.id == folderId }?.name ?: "Папка"
                    Page.STATS -> "Статистика"
                    Page.DECK -> selectedDeck?.name ?: "Колода"
                    Page.SESSION -> "Занятие"
                    Page.IMPORT -> "Импорт слов"
                }, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (page == Page.DECK || page == Page.IMPORT || page == Page.SESSION || folderId != null) {
                        IconButton(onClick = {
                            when (page) {
                                Page.SESSION -> { page = Page.DECK; isFlipped = false; vm.clearSession() }
                                Page.DECK -> { page = Page.HOME; deckId = null }
                                Page.IMPORT -> { page = Page.HOME; vm.clearImport() }
                                else -> folderId = null
                            }
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                    }
                },
                actions = {
                    if (page == Page.HOME && folderId == null) {
                        IconButton(onClick = { page = Page.IMPORT; filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) }) {
                            Icon(Icons.Default.UploadFile, "Импорт XLSX")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (page == Page.HOME || page == Page.STATS) NavigationBar {
                NavigationBarItem(selected = page == Page.HOME, onClick = { page = Page.HOME; folderId = null }, icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text("Колоды") })
                NavigationBarItem(selected = page == Page.STATS, onClick = { page = Page.STATS }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Статистика") })
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                Page.HOME -> HomeScreen(
                    folders = folders, decks = decks, folderId = folderId, studiedToday = studiedToday, due = due,
                    onOpenFolder = { folderId = it }, onOpenDeck = { deckId = it; page = Page.DECK },
                    onNewDeck = { dialogFolderId = folderId; dialog = DialogKind.NEW_DECK; dialogName = "" },
                    onNewFolder = { dialog = DialogKind.NEW_FOLDER; dialogName = "" },
                    onRenameFolder = { folder -> dialogObjectId = folder.id; dialogName = folder.name; dialog = DialogKind.RENAME_FOLDER },
                    onDeleteFolder = { folder -> dialogObjectId = folder.id; dialogName = folder.name; dialog = DialogKind.DELETE_FOLDER },
                    onRenameDeck = { deck -> dialogObjectId = deck.id; dialogName = deck.name; dialog = DialogKind.RENAME_DECK },
                    onDeleteDeck = { deck -> dialogObjectId = deck.id; dialogName = deck.name; dialog = DialogKind.DELETE_DECK },
                    onMoveDeck = { deck -> dialogObjectId = deck.id; dialogName = deck.name; dialog = DialogKind.MOVE_DECK },
                    onImport = { page = Page.IMPORT; filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) }
                )
                Page.STATS -> StatsScreen(total, learned, due, studiedToday)
                Page.DECK -> if (selectedDeck != null && currentDeckId != null) DeckScreen(
                    deck = selectedDeck!!, cards = deckCards,
                    onStudy = { onlyDue -> vm.startSession(selectedDeck!!.id, onlyDue); isFlipped = false; page = Page.SESSION },
                    onAddCard = { dialog = DialogKind.ADD_CARD; dialogName = "" }
                ) else EmptyMessage("Колода не найдена")
                Page.SESSION -> SessionScreen(
                    card = session.firstOrNull(), index = session.size,
                    flipped = isFlipped, onFlip = { isFlipped = !isFlipped },
                    onAnswer = { card, knew -> vm.answer(card, knew); isFlipped = false },
                    onFinish = { page = Page.DECK; isFlipped = false; vm.clearSession() }
                )
                Page.IMPORT -> ImportScreen(
                    vm = vm, state = import, folders = folders, onPick = { filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                    onComplete = { count -> page = Page.HOME; vm.clearImport(); scope.launch { snack.showSnackbar("Импортировано карточек: $count") } },
                    onError = { message -> scope.launch { snack.showSnackbar(message) } }
                )
            }
        }
    }

    when (dialog) {
        DialogKind.NEW_FOLDER, DialogKind.NEW_DECK, DialogKind.RENAME_FOLDER, DialogKind.RENAME_DECK, DialogKind.ADD_CARD ->
            EditDialog(dialog!!, dialogName, onNameChange = { dialogName = it }, onDismiss = { dialog = null }) { english, trans, translation ->
                when (dialog) {
                    DialogKind.NEW_FOLDER -> vm.createFolder(english)
                    DialogKind.RENAME_FOLDER -> vm.renameFolder(dialogObjectId, english)
                    DialogKind.NEW_DECK -> vm.createDeck(english, dialogFolderId)
                    DialogKind.RENAME_DECK -> vm.renameDeck(dialogObjectId, english)
                    DialogKind.ADD_CARD -> currentDeckId?.let { vm.addCard(it, ImportedWord(english, trans, translation)) }
                    else -> Unit
                }; dialog = null
            }
        DialogKind.DELETE_FOLDER, DialogKind.DELETE_DECK -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Удалить «$dialogName»?") },
            text = { Text(if (dialog == DialogKind.DELETE_FOLDER) "Колоды останутся, но будут перемещены без папки." else "Все карточки этой колоды тоже будут удалены.") },
            confirmButton = { TextButton(onClick = { if (dialog == DialogKind.DELETE_FOLDER) vm.deleteFolder(dialogObjectId) else vm.deleteDeck(dialogObjectId); dialog = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } }
        )
        DialogKind.MOVE_DECK -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("Переместить колоду") },
            text = { Column { TextButton(onClick = { vm.moveDeck(dialogObjectId, null); dialog = null }) { Text("Без папки") }; folders.forEach { folder -> TextButton(onClick = { vm.moveDeck(dialogObjectId, folder.id); dialog = null }) { Text(folder.name) } } } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Готово") } }
        )
        null -> Unit
    }
}

private enum class DialogKind { NEW_FOLDER, NEW_DECK, RENAME_FOLDER, RENAME_DECK, DELETE_FOLDER, DELETE_DECK, MOVE_DECK, ADD_CARD }

@Composable
private fun HomeScreen(
    folders: List<FolderEntity>, decks: List<DeckSummary>, folderId: String?, studiedToday: Int, due: Int,
    onOpenFolder: (String) -> Unit, onOpenDeck: (String) -> Unit, onNewDeck: () -> Unit, onNewFolder: () -> Unit,
    onRenameFolder: (FolderEntity) -> Unit, onDeleteFolder: (FolderEntity) -> Unit,
    onRenameDeck: (DeckEntity) -> Unit, onDeleteDeck: (DeckEntity) -> Unit, onMoveDeck: (DeckEntity) -> Unit, onImport: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (folderId == null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Пять минут сегодня — новые слова завтра", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("$due карточек ждут повторения · сегодня изучено $studiedToday")
                        FilledTonalButton(onClick = onImport) { Icon(Icons.Default.ImportExport, null); Spacer(Modifier.width(8.dp)); Text("Импортировать XLSX") }
                    }
                }
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Папки", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = onNewFolder) { Icon(Icons.Default.Add, null); Text("Новая папка") } } }
            if (folders.isEmpty()) item { Text("Создайте папку или начните с колоды без папки.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(folders, key = { "f-${it.id}" }) { folder -> FolderRow(folder, onClick = { onOpenFolder(folder.id) }, onRename = { onRenameFolder(folder) }, onDelete = { onDeleteFolder(folder) }) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Мои колоды", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = onNewDeck) { Icon(Icons.Default.Add, null); Text("Новая") } } }
        } else {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Колоды", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = onNewDeck) { Icon(Icons.Default.Add, null); Text("Новая колода") } } }
        }
        if (decks.isEmpty()) item { EmptyMessage(if (folderId == null) "Пока нет колод. Создайте первую или загрузите слова из XLSX." else "В этой папке пока нет колод.") }
        items(decks, key = { "d-${it.deck.id}" }) { DeckRow(it, onClick = { onOpenDeck(it.deck.id) }, onRename = { onRenameDeck(it.deck) }, onDelete = { onDeleteDeck(it.deck) }, onMove = { onMoveDeck(it.deck) }) }
    }
}

@Composable
private fun FolderRow(folder: FolderEntity, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(folder.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Действия") }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Удалить папку") }, onClick = { menu = false; onDelete() })
            } }
        }
    }
}

@Composable
private fun DeckRow(summary: DeckSummary, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onMove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(summary.deck.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("${summary.cardCount} слов · ${summary.dueCount} к повторению", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Действия колоды") }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Переместить в папку") }, onClick = { menu = false; onMove() })
                    DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                } }
            }
            LinearProgressIndicator(progress = { if (summary.cardCount == 0) 0f else summary.learnedCount.toFloat() / summary.cardCount }, modifier = Modifier.fillMaxWidth())
            Text("${summary.learnedCount} выучено из ${summary.cardCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeckScreen(deck: DeckEntity, cards: List<CardEntity>, onStudy: (Boolean) -> Unit, onAddCard: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onStudy(false) }, modifier = Modifier.weight(1f), enabled = cards.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null); Text("Все слова") }
            OutlinedButton(onClick = { onStudy(true) }, modifier = Modifier.weight(1f), enabled = cards.any { it.dueAt <= System.currentTimeMillis() }) { Text("К повторению") }
        }
        Row(Modifier.padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("Карточки · ${cards.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); TextButton(onClick = onAddCard) { Icon(Icons.Default.Add, null); Text("Добавить") } }
        if (cards.isEmpty()) EmptyMessage("Добавьте слово или импортируйте таблицу XLSX.", Modifier.weight(1f))
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(cards, key = { it.id }) { card -> Card(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) { Text(card.english, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium); if (card.transcription.isNotBlank()) Text(card.transcription, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(card.translation, color = MaterialTheme.colorScheme.primary) }
            } }
        }
    }
}

@Composable
private fun SessionScreen(card: CardEntity?, index: Int, flipped: Boolean, onFlip: () -> Unit, onAnswer: (CardEntity, Boolean) -> Unit, onFinish: () -> Unit) {
    if (card == null) { Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.School, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(14.dp)); Text("На сегодня всё", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Вы можете вернуться к карточкам позже.", textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp)); Button(onClick = onFinish) { Text("К колоде") }
    }; return }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp)); Text("Осталось в занятии: $index", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp))
        Card(onClick = onFlip, modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxSize().padding(26.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (flipped) "ПЕРЕВОД" else "СЛОВО", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp)); Text(if (flipped) card.translation else card.english, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (!flipped && card.transcription.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(card.transcription, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(20.dp)); Text(if (flipped) "Нажмите ответ ниже" else "Нажмите, чтобы увидеть перевод", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (!flipped) Button(onClick = onFlip, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Показать перевод") }
        else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onAnswer(card, false) }, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) { Text("Повторить") }
            Button(onClick = { onAnswer(card, true) }, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) { Text("Знаю") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StatsScreen(total: Int, learned: Int, due: Int, studiedToday: Int) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ваш прогресс", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        StatTile("Всего карточек", total, "Все слова и выражения в колодах")
        StatTile("Выучено", learned, "Карточки с интервалом от 21 дня")
        StatTile("Ждут повтора", due, "Срок повтора наступил")
        StatTile("Изучено сегодня", studiedToday, "Ответы за сегодня")
    }
}

@Composable
private fun StatTile(title: String, count: Int, hint: String) {
    Card(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ImportScreen(vm: AppViewModel, state: ImportPreview?, folders: List<FolderEntity>, onPick: () -> Unit, onComplete: (Int) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val decksFlow = remember(folders) {
        val sources = listOf(vm.decks(null)) + folders.map { vm.decks(it.id) }
        combine(sources) { groups -> groups.flatMap { it } }
    }
    val allDecks by decksFlow.collectAsState(initial = emptyList())
    var targetDeckId by remember { mutableStateOf("") }
    var newDeckName by remember { mutableStateOf("") }
    var createNew by remember { mutableStateOf(false) }
    var skipDuplicates by remember { mutableStateOf(true) }
    var sheetMenu by remember { mutableStateOf(false) }
    var deckMenu by remember { mutableStateOf(false) }
    var header by remember(state?.sheetIndex) { mutableStateOf(state?.hasHeader ?: true) }
    var engCol by remember(state?.sheetIndex) { mutableIntStateOf(state?.englishColumn ?: 0) }
    var transCol by remember(state?.sheetIndex) { mutableStateOf(state?.transcriptionColumn) }
    var rusCol by remember(state?.sheetIndex) { mutableIntStateOf(state?.translationColumn ?: 1) }
    val currentDeck = allDecks.firstOrNull { it.deck.id == targetDeckId }
    val existing by (currentDeck?.let { vm.cards(it.deck.id) } ?: remember { kotlinx.coroutines.flow.flowOf(emptyList()) }).collectAsState(initial = emptyList())
    LaunchedEffect(targetDeckId, state?.sheetIndex, existing) { if (targetDeckId.isNotBlank()) vm.refreshDuplicates(existing) }
    LaunchedEffect(state?.sheetIndex) { state?.let { engCol = it.englishColumn; transCol = it.transcriptionColumn; rusCol = it.translationColumn; header = it.hasHeader } }
    val rows = state?.sheets?.getOrNull(state.sheetIndex)?.rows.orEmpty()
    val columnCount = maxOf(rows.maxOfOrNull { it.size } ?: 0, 3)
    val headers = if (state?.hasHeader == true) rows.firstOrNull().orEmpty().ifEmpty { List(columnCount) { "" } } else List(columnCount) { "" }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Загрузите книгу Excel .xlsx. Файл остаётся на устройстве.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp)); Button(onClick = onPick, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(8.dp)); Text(if (state == null) "Выбрать файл" else "Выбрать другой файл") }
        }
        if (state == null) item { EmptyMessage("В таблице нужны столбцы со словом по-английски и переводом. Можно импортировать книгу с несколькими листами.") }
        else {
            item {
                ExposedDropdownMenuBox(expanded = sheetMenu, onExpandedChange = { sheetMenu = !sheetMenu }) {
                    OutlinedTextField(value = state.sheets[state.sheetIndex].name, onValueChange = {}, readOnly = true, label = { Text("Лист") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sheetMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = sheetMenu, onDismissRequest = { sheetMenu = false }) { state.sheets.forEachIndexed { index, item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { sheetMenu = false; vm.selectSheet(index) }) } }
                }
            }
            item { Text("Назначение столбцов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = header, onCheckedChange = { header = it; vm.mapColumns(header, engCol, transCol, rusCol, existing) }); Text("Первая строка — заголовки") } }
            item { ColumnSelector("Английское слово *", headers, engCol, false) { engCol = it ?: 0; vm.mapColumns(header, engCol, transCol, rusCol, existing) } }
            item { ColumnSelector("Транскрипция", headers, transCol, true) { transCol = it; vm.mapColumns(header, engCol, transCol, rusCol, existing) } }
            item { ColumnSelector("Перевод на русский *", headers, rusCol, false) { rusCol = it ?: 0; vm.mapColumns(header, engCol, transCol, rusCol, existing) } }
            item {
                Text("Колода", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = !createNew, onClick = { createNew = false }); Text("Существующая"); Spacer(Modifier.width(14.dp)); RadioButton(selected = createNew, onClick = { createNew = true }); Text("Новая") }
                if (createNew) OutlinedTextField(newDeckName, { newDeckName = it }, label = { Text("Название новой колоды") }, modifier = Modifier.fillMaxWidth())
                else ExposedDropdownMenuBox(expanded = deckMenu, onExpandedChange = { deckMenu = !deckMenu }) {
                    OutlinedTextField(value = allDecks.firstOrNull { it.deck.id == targetDeckId }?.deck?.name.orEmpty(), onValueChange = {}, readOnly = true, label = { Text("Выберите колоду") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(deckMenu) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = deckMenu, onDismissRequest = { deckMenu = false }) { allDecks.forEach { item -> DropdownMenuItem(text = { Text(item.deck.name) }, onClick = { targetDeckId = item.deck.id; deckMenu = false }) } }
                }
            }
            item { Text("Предварительный просмотр · ${state.words.size} корректных · ${state.invalidCount} пропущено · ${state.duplicateCount} возможных дублей", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(state.words.take(8)) { word ->
                val duplicate = existing.any { it.english.trim().equals(word.english.trim(), true) && it.translation.trim().equals(word.translation.trim(), true) }
                Card(shape = RoundedCornerShape(14.dp), border = if (duplicate) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) else null) { Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(word.english, fontWeight = FontWeight.SemiBold); if (word.transcription.isNotBlank()) Text(word.transcription, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(word.translation); if (duplicate) Text("Возможный дубль в этой колоде", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary) } }
            }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(skipDuplicates, { skipDuplicates = it }); Text("Пропустить возможные дубликаты") } }
            if (rows.isEmpty()) item { EmptyMessage("Выбранный лист пуст. Выберите другой лист или файл.") }
            item { Button(onClick = {
                val proceed: (String) -> Unit = { id -> vm.completeImport(id, skipDuplicates, onComplete) }
                if (createNew) {
                    if (newDeckName.isBlank()) onError("Введите название новой колоды")
                    else vm.createDeck(newDeckName, null, proceed)
                } else if (targetDeckId.isBlank()) onError("Выберите колоду для импорта")
                else proceed(targetDeckId)
            }, enabled = state.words.isNotEmpty() && (createNew || targetDeckId.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Импортировать ${state.words.size - state.duplicateCount} карточек") } }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ColumnSelector(label: String, headers: List<String>, selected: Int?, optional: Boolean, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        val text = if (selected == null && optional) "Без транскрипции" else headers.getOrNull(selected ?: -1)?.let { value -> "${columnLabel(selected ?: 0)} · ${value.ifBlank { "без заголовка" }}" } ?: "Выберите столбец"
        OutlinedTextField(value = text, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (optional) DropdownMenuItem(text = { Text("Без транскрипции") }, onClick = { onSelect(null); expanded = false })
        headers.forEachIndexed { index, value -> DropdownMenuItem(text = { Text("${columnLabel(index)} · ${value.ifBlank { "без заголовка" }}") }, onClick = { onSelect(index); expanded = false }) }
        }
    }
}

private fun columnLabel(index: Int): String {
    var value = index + 1; var result = ""
    while (value > 0) { val digit = (value - 1) % 26; result = ('A' + digit) + result; value = (value - 1) / 26 }
    return result
}
@Composable
private fun EditDialog(kind: DialogKind, initialName: String, onNameChange: (String) -> Unit, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var transcription by remember(kind) { mutableStateOf("") }
    var translation by remember(kind) { mutableStateOf("") }
    val addCard = kind == DialogKind.ADD_CARD
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (kind) { DialogKind.NEW_FOLDER -> "Новая папка"; DialogKind.NEW_DECK -> "Новая колода"; DialogKind.RENAME_FOLDER, DialogKind.RENAME_DECK -> "Переименовать"; else -> "Новая карточка" }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(initialName, onNameChange, label = { Text(if (addCard) "Слово или выражение" else "Название") }, singleLine = !addCard)
            if (addCard) { OutlinedTextField(transcription, { transcription = it }, label = { Text("Транскрипция (необязательно)") }); OutlinedTextField(translation, { translation = it }, label = { Text("Перевод") }) }
        } },
        confirmButton = { TextButton(onClick = { onSave(initialName, transcription, translation) }, enabled = initialName.isNotBlank() && (!addCard || translation.isNotBlank())) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun EmptyMessage(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)); Spacer(Modifier.height(8.dp)); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
