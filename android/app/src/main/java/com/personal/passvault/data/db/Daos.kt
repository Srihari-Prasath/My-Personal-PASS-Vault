package com.personal.passvault.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observe(id: String): Flow<EntryEntity?>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: String): EntryEntity?

    @Query("SELECT * FROM entries")
    suspend fun getAll(): List<EntryEntity>

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE entries SET favorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("SELECT id, title FROM entries WHERE password = :password AND password != '' AND id != :excludeId")
    suspend fun entriesUsingPassword(password: String, excludeId: String): List<EntryTitle>

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    /** Children are removed by ON DELETE CASCADE. */
    @Query("DELETE FROM entries")
    suspend fun deleteAll()
}

data class EntryTitle(val id: String, val title: String)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM password_history WHERE entryId = :entryId ORDER BY replacedAt DESC")
    fun observe(entryId: String): Flow<List<PasswordHistoryEntity>>

    @Query("SELECT * FROM password_history WHERE entryId = :entryId ORDER BY replacedAt DESC")
    suspend fun get(entryId: String): List<PasswordHistoryEntity>

    @Query("SELECT * FROM password_history WHERE id = :id")
    suspend fun getById(id: Long): PasswordHistoryEntity?

    @Insert
    suspend fun insert(item: PasswordHistoryEntity)

    @Insert
    suspend fun insertAll(items: List<PasswordHistoryEntity>)

    @Query("SELECT * FROM password_history")
    suspend fun getAll(): List<PasswordHistoryEntity>

    @Query("DELETE FROM password_history WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecoveryCodeDao {
    @Query("SELECT * FROM recovery_codes ORDER BY entryId, position")
    fun observeAll(): Flow<List<RecoveryCodeEntity>>

    @Query("SELECT * FROM recovery_codes WHERE entryId = :entryId ORDER BY position")
    fun observe(entryId: String): Flow<List<RecoveryCodeEntity>>

    @Query("SELECT * FROM recovery_codes WHERE entryId = :entryId AND setReplacedAt IS NULL ORDER BY position")
    suspend fun currentSet(entryId: String): List<RecoveryCodeEntity>

    @Insert
    suspend fun insertAll(codes: List<RecoveryCodeEntity>)

    @Query("SELECT * FROM recovery_codes")
    suspend fun getAll(): List<RecoveryCodeEntity>

    @Query("DELETE FROM recovery_codes WHERE entryId = :entryId")
    suspend fun deleteAllFor(entryId: String)

    @Query("DELETE FROM recovery_codes WHERE entryId = :entryId AND setReplacedAt IS NULL")
    suspend fun deleteCurrentSet(entryId: String)

    @Query("UPDATE recovery_codes SET setReplacedAt = :at WHERE entryId = :entryId AND setReplacedAt IS NULL")
    suspend fun archiveCurrentSet(entryId: String, at: Long)

    @Query("UPDATE recovery_codes SET used = :used, usedAt = :usedAt WHERE id = :id")
    suspend fun setUsed(id: Long, used: Boolean, usedAt: Long?)
}

@Dao
interface LinkedTargetDao {
    @Query("SELECT * FROM linked_targets ORDER BY entryId, position")
    fun observeAll(): Flow<List<LinkedTargetEntity>>

    @Query("SELECT * FROM linked_targets WHERE entryId = :entryId ORDER BY position")
    fun observe(entryId: String): Flow<List<LinkedTargetEntity>>

    @Query("SELECT * FROM linked_targets WHERE entryId = :entryId ORDER BY position")
    suspend fun get(entryId: String): List<LinkedTargetEntity>

    @Query("SELECT DISTINCT domain FROM linked_targets WHERE kind = 'web' AND domain IS NOT NULL")
    suspend fun allDomains(): List<String>

    @Query("SELECT * FROM linked_targets")
    suspend fun getAll(): List<LinkedTargetEntity>

    @Query("DELETE FROM linked_targets WHERE entryId = :entryId")
    suspend fun clear(entryId: String)

    @Insert
    suspend fun insertAll(targets: List<LinkedTargetEntity>)
}

@Dao
interface WebIconDao {
    @Query("SELECT * FROM web_icons WHERE domain = :domain")
    suspend fun get(domain: String): WebIconEntity?

    @Upsert
    suspend fun upsert(icon: WebIconEntity)

    @Query("DELETE FROM web_icons")
    suspend fun clear()
}
