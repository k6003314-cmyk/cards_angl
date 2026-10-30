package ru.linguacards.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class StudyRepository private constructor(private val db: AppDatabase) {
    private val dao = db.studyDao()
    fun folders() = dao.folders()
    suspend fun folderSnapshot(id: String) = dao.folderOnce(id)
    fun decks(folderId: String?) = dao.deckSummaries(folderId, System.currentTimeMillis())
    fun deck(id: String) = dao.deck(id)
    suspend fun deckSnapshot(id: String) = dao.deckOnce(id)
    fun cards(deckId: String) = dao.cards(deckId)
    suspend fun cardsSnapshot(deckId: String) = dao.cardsOnce(deckId)
    fun total() = dao.totalCards()
    fun learned() = dao.learnedCards()
    fun due() = dao.dueCount(System.currentTimeMillis())
    fun studiedToday(): Flow<Int> {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return dao.reviewedToday(start, end)
    }

    suspend fun createFolder(name: String) = dao.putFolder(FolderEntity(UUID.randomUUID().toString(), name.trim(), System.currentTimeMillis()))
    suspend fun saveFolder(folder: FolderEntity) = dao.putFolder(folder.copy(name = folder.name.trim()))
    suspend fun removeFolder(id: String) = dao.deleteFolder(id)
    suspend fun createDeck(name: String, folderId: String? = null): String {
        val id = UUID.randomUUID().toString()
        dao.putDeck(DeckEntity(id, name.trim(), folderId, System.currentTimeMillis()))
        return id
    }
    suspend fun saveDeck(deck: DeckEntity) = dao.putDeck(deck.copy(name = deck.name.trim()))
    suspend fun moveDeck(id: String, folderId: String?) {
        val deck = deckSnapshot(id) ?: return
        dao.putDeck(deck.copy(folderId = folderId))
    }
    suspend fun removeDeck(id: String) = dao.deleteDeck(id)
    suspend fun addCard(deckId: String, english: String, transcription: String, translation: String) {
        dao.putCard(CardEntity(UUID.randomUUID().toString(), deckId, english.trim(), transcription.trim(), translation.trim(), 0L, 0, 0, System.currentTimeMillis()))
    }
    suspend fun importCards(deckId: String, rows: List<ImportedWord>): Int {
        val now = System.currentTimeMillis()
        val cards = rows.map { CardEntity(UUID.randomUUID().toString(), deckId, it.english.trim(), it.transcription.trim(), it.translation.trim(), now, 0, 0, now) }
        return dao.insertCards(cards).count { it != -1L }
    }
    suspend fun review(card: CardEntity, knew: Boolean): CardEntity {
        val now = System.currentTimeMillis()
        val interval = if (!knew) 0 else when (card.intervalDays) { 0 -> 1; 1 -> 3; 3 -> 7; 7 -> 14; else -> (card.intervalDays * 2).coerceAtMost(365) }
        val dueAt = if (knew) now + interval * 24L * 60 * 60 * 1000 else now + 10 * 60 * 1000L
        return db.withTransaction {
            val updated = card.copy(dueAt = dueAt, intervalDays = interval, repetitions = if (knew) card.repetitions + 1 else 0)
            dao.putCard(updated)
            dao.putLog(ReviewLogEntity(UUID.randomUUID().toString(), card.id, knew, now))
            updated
        }
    }
    suspend fun dueCards(deckId: String) = dao.dueCardsOnce(deckId, System.currentTimeMillis())
    suspend fun allCards(deckId: String) = dao.cardsOnce(deckId)

    companion object {
        @Volatile private var instance: StudyRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: StudyRepository(Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "lingua-cards.db").build()).also { instance = it }
        }
    }
}

data class ImportedWord(val english: String, val transcription: String = "", val translation: String)

