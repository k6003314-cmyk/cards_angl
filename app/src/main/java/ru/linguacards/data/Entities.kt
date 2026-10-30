package ru.linguacards.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "folders")
data class FolderEntity(@PrimaryKey val id: String, val name: String, val createdAt: Long)

@Entity(
    tableName = "decks",
    foreignKeys = [ForeignKey(entity = FolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("folderId")]
)
data class DeckEntity(@PrimaryKey val id: String, val name: String, val folderId: String?, val createdAt: Long)

@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(entity = DeckEntity::class, parentColumns = ["id"], childColumns = ["deckId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("deckId"), Index("dueAt")]
)
data class CardEntity(
    @PrimaryKey val id: String, val deckId: String, val english: String, val transcription: String,
    val translation: String, val dueAt: Long, val intervalDays: Int, val repetitions: Int, val createdAt: Long
)

@Entity(tableName = "review_logs", indices = [Index("cardId"), Index("reviewedAt")])
data class ReviewLogEntity(@PrimaryKey val id: String, val cardId: String, val knew: Boolean, val reviewedAt: Long)

data class DeckSummary(
    @Embedded val deck: DeckEntity,
    @ColumnInfo(name = "cardCount") val cardCount: Int,
    @ColumnInfo(name = "learnedCount") val learnedCount: Int,
    @ColumnInfo(name = "dueCount") val dueCount: Int
)

@Dao
interface StudyDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE") fun folders(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folders WHERE id=:id") suspend fun folderOnce(id: String): FolderEntity?
    @Query("SELECT * FROM decks WHERE folderId IS :folderId ORDER BY name COLLATE NOCASE") fun decksInFolder(folderId: String?): Flow<List<DeckEntity>>
    @Query("SELECT * FROM decks WHERE id = :id") fun deck(id: String): Flow<DeckEntity?>
    @Query("SELECT * FROM decks WHERE id = :id") suspend fun deckOnce(id: String): DeckEntity?
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt") fun cards(deckId: String): Flow<List<CardEntity>>
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueAt <= :now ORDER BY dueAt, createdAt") fun dueCards(deckId: String, now: Long): Flow<List<CardEntity>>
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueAt <= :now ORDER BY dueAt, createdAt") suspend fun dueCardsOnce(deckId: String, now: Long): List<CardEntity>
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt") suspend fun cardsOnce(deckId: String): List<CardEntity>
    @Query("SELECT COUNT(*) FROM cards") fun totalCards(): Flow<Int>
    @Query("SELECT COUNT(*) FROM cards WHERE intervalDays >= 21") fun learnedCards(): Flow<Int>
    @Query("SELECT COUNT(*) FROM cards WHERE dueAt <= :now") fun dueCount(now: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :start AND reviewedAt < :end") fun reviewedToday(start: Long, end: Long): Flow<Int>
    @Query("SELECT d.*, (SELECT COUNT(*) FROM cards WHERE deckId=d.id) AS cardCount, (SELECT COUNT(*) FROM cards WHERE deckId=d.id AND intervalDays >= 21) AS learnedCount, (SELECT COUNT(*) FROM cards WHERE deckId=d.id AND dueAt <= :now) AS dueCount FROM decks d WHERE d.folderId IS :folderId ORDER BY d.name COLLATE NOCASE")
    fun deckSummaries(folderId: String?, now: Long): Flow<List<DeckSummary>>
    @Upsert suspend fun putFolder(folder: FolderEntity)
    @Upsert suspend fun putDeck(deck: DeckEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCards(cards: List<CardEntity>): List<Long>
    @Upsert suspend fun putCard(card: CardEntity)
    @Insert suspend fun putLog(log: ReviewLogEntity)
    @Query("DELETE FROM folders WHERE id=:id") suspend fun deleteFolder(id: String)
    @Query("DELETE FROM decks WHERE id=:id") suspend fun deleteDeck(id: String)
}

@Database(entities = [FolderEntity::class, DeckEntity::class, CardEntity::class, ReviewLogEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao
}
