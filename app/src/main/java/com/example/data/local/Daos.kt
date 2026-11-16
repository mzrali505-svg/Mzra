package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ProxyEntity>>

    @Query("SELECT * FROM proxies WHERE id = :id")
    fun observeById(id: Long): Flow<ProxyEntity?>

    @Query("SELECT * FROM proxies WHERE id = :id")
    suspend fun getById(id: Long): ProxyEntity?

    @Query("SELECT * FROM proxies ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<ProxyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(proxy: ProxyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(proxies: List<ProxyEntity>): List<Long>

    @Update
    suspend fun update(proxy: ProxyEntity)

    @Update
    suspend fun updateAll(proxies: List<ProxyEntity>)

    @Query("DELETE FROM proxies WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY COALESCE(lastLaunchedAt, createdAt) DESC")
    fun observeAll(): Flow<List<BrowserProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: Long): Flow<BrowserProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): BrowserProfileEntity?

    @Query("SELECT * FROM profiles WHERE proxyId = :proxyId")
    suspend fun getByProxyId(proxyId: Long): List<BrowserProfileEntity>

    @Query("SELECT * FROM profiles WHERE proxyId = :proxyId LIMIT 1")
    suspend fun getFirstByProxyId(proxyId: Long): BrowserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: BrowserProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<BrowserProfileEntity>): List<Long>

    @Update
    suspend fun update(profile: BrowserProfileEntity)

    @Update
    suspend fun updateAll(profiles: List<BrowserProfileEntity>)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE profiles SET proxyId = NULL, proxyConnectionType = 'WITHOUT_PROXY' WHERE proxyId = :proxyId")
    suspend fun unbindProxy(proxyId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE profileId = :profileId ORDER BY visitedAt DESC LIMIT 100")
    fun observeHistory(profileId: Long): Flow<List<BrowserHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: BrowserHistoryEntity)

    @Query("DELETE FROM history WHERE profileId = :profileId")
    suspend fun clearHistoryForProfile(profileId: Long)

    @Query("SELECT * FROM bookmarks WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeBookmarks(profileId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutomationFlowEntity>>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: Long): AutomationFlowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flow: AutomationFlowEntity): Long

    @Update
    suspend fun update(flow: AutomationFlowEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
