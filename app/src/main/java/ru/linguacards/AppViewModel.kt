package ru.linguacards

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.linguacards.data.CardEntity
import ru.linguacards.data.ImportedWord
import ru.linguacards.data.StudyRepository
import ru.linguacards.importer.SpreadsheetReader
import ru.linguacards.importer.WorkbookSheet
import ru.linguacards.importer.XlsxReader

data class ImportPreview(
    val sheets: List<WorkbookSheet>, val sheetIndex: Int, val hasHeader: Boolean,
    val englishColumn: Int, val transcriptionColumn: Int?, val translationColumn: Int,
    val words: List<ImportedWord>, val invalidCount: Int, val duplicateCount: Int, val error: String? = null
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repository = StudyRepository.get(app)
    val folders = repository.folders()
    val total = repository.total()
    val learned = repository.learned()
    val due = repository.due()
    val studiedToday = repository.studiedToday()
    private val _session = MutableStateFlow<List<CardEntity>>(emptyList())
    val session: StateFlow<List<CardEntity>> = _session
    private val _import = MutableStateFlow<ImportPreview?>(null)
    val import: StateFlow<ImportPreview?> = _import
    private var reader: SpreadsheetReader = XlsxReader()

    fun decks(folderId: String?) = repository.decks(folderId)
    fun deck(id: String) = repository.deck(id)
    fun cards(deckId: String) = repository.cards(deckId)
    fun createFolder(name: String) = viewModelScope.launch { repository.createFolder(name) }
    fun renameFolder(folderId: String, name: String) = viewModelScope.launch {
        val folder = repository.folderSnapshot(folderId)
        if (folder != null) repository.saveFolder(folder.copy(name = name))
    }
    fun deleteFolder(id: String) = viewModelScope.launch { repository.removeFolder(id) }
    fun createDeck(name: String, folderId: String?, onCreated: (String) -> Unit = {}) = viewModelScope.launch { onCreated(repository.createDeck(name, folderId)) }
    fun renameDeck(id: String, name: String) = viewModelScope.launch {
        val deck = repository.deckSnapshot(id)
        if (deck != null) repository.saveDeck(deck.copy(name = name))
    }
    fun moveDeck(id: String, folderId: String?) = viewModelScope.launch { repository.moveDeck(id, folderId) }
    fun deleteDeck(id: String) = viewModelScope.launch { repository.removeDeck(id) }
    fun addCard(deckId: String, word: ImportedWord) = viewModelScope.launch { repository.addCard(deckId, word.english, word.transcription, word.translation) }

    fun startSession(deckId: String, dueOnly: Boolean) = viewModelScope.launch {
        _session.value = if (dueOnly) repository.dueCards(deckId) else repository.allCards(deckId)
    }
    fun answer(card: CardEntity, knew: Boolean) = viewModelScope.launch {
        val updated = repository.review(card, knew)
        val rest = _session.value.drop(1)
        _session.value = if (knew) rest else rest + updated
    }
    fun clearSession() { _session.value = emptyList() }

    fun readWorkbook(uri: android.net.Uri, onError: (String) -> Unit) = viewModelScope.launch {
        try {
            val sheets = withContext(Dispatchers.IO) { reader.read(getApplication<Application>().contentResolver, uri) }
            _import.value = buildPreview(sheets, 0, null)
        } catch (e: Exception) { onError(e.message ?: "Не удалось прочитать файл") }
    }
    fun selectSheet(index: Int) { _import.value?.let { _import.value = buildPreview(it.sheets, index, null) } }
    fun mapColumns(header: Boolean, english: Int, transcription: Int?, translation: Int, existing: List<CardEntity>) {
        _import.value?.let { _import.value = preview(it.copy(hasHeader = header, englishColumn = english, transcriptionColumn = transcription, translationColumn = translation), existing) }
    }
    fun refreshDuplicates(existing: List<CardEntity>) { _import.value?.let { _import.value = preview(it, existing) } }
    fun completeImport(deckId: String, skipDuplicates: Boolean, onDone: (Int) -> Unit) = viewModelScope.launch {
        val result = _import.value ?: return@launch
        val existing = repository.cardsSnapshot(deckId).map { it.english.trim().lowercase() to it.translation.trim().lowercase() }.toSet()
        val rows = if (skipDuplicates) result.words.distinctBy { it.english.trim().lowercase() to it.translation.trim().lowercase() }
            .filterNot { it.english.trim().lowercase() to it.translation.trim().lowercase() in existing } else result.words
        onDone(repository.importCards(deckId, rows)); _import.value = null
    }
    fun clearImport() { _import.value = null }

    private fun buildPreview(sheets: List<WorkbookSheet>, index: Int, existing: List<CardEntity>?): ImportPreview {
        val rows = sheets[index].rows
        val headerValues = rows.firstOrNull().orEmpty().map(::normalize)
        fun find(vararg values: String) = headerValues.indexOfFirst { h -> values.any { h == normalize(it) || h.contains(normalize(it)) } }
        val english = find("English", "Word", "Слово", "Английский")
        val translation = find("Перевод", "Translation", "Russian", "Русский")
        val transcription = find("Transcription", "Pronunciation", "Транскрипция", "Транскрипция")
        val hasHeader = english >= 0 || translation >= 0
        val count = rows.maxOfOrNull { it.size } ?: 0
        val preview = ImportPreview(sheets, index, hasHeader, english.takeIf { it >= 0 } ?: 0,
            transcription.takeIf { it >= 0 }, translation.takeIf { it >= 0 } ?: (1.takeIf { count > 1 } ?: 0), emptyList(), 0, 0)
        return preview(preview, existing.orEmpty())
    }
    private fun preview(state: ImportPreview, existing: List<CardEntity>): ImportPreview {
        val rows = state.sheets[state.sheetIndex].rows.drop(if (state.hasHeader) 1 else 0)
        var invalid = 0
        val words = rows.mapNotNull { row ->
            val english = row.getOrNull(state.englishColumn).orEmpty().trim()
            val translation = row.getOrNull(state.translationColumn).orEmpty().trim()
            if (english.isEmpty() || translation.isEmpty()) { invalid++; null }
            else ImportedWord(english, state.transcriptionColumn?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty(), translation)
        }
        val known = existing.map { it.english.trim().lowercase() to it.translation.trim().lowercase() }.toSet()
        val duplicates = words.count { it.english.lowercase() to it.translation.lowercase() in known } +
            (words.size - words.distinctBy { it.english.lowercase() to it.translation.lowercase() }.size)
        return state.copy(words = words, invalidCount = invalid, duplicateCount = duplicates)
    }
    private fun normalize(value: String) = value.trim().lowercase().replace("ё", "е").replace(Regex("[\\s_()-]+"), "")
}
