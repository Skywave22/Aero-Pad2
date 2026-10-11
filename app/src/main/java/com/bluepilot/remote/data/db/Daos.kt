package com.bluepilot.remote.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LayoutProfileDao {
    @Query("SELECT * FROM layout_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LayoutProfileEntity>>

    @Query("SELECT * FROM layout_profiles WHERE id = :id")
    suspend fun byId(id: Long): LayoutProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: LayoutProfileEntity): Long

    @Update
    suspend fun update(profile: LayoutProfileEntity)

    @Query("DELETE FROM layout_profiles WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM layout_profiles")
    suspend fun count(): Int
}

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun byId(id: Long): MacroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(macro: MacroEntity): Long

    @Delete
    suspend fun delete(macro: MacroEntity)
}

@Dao
interface SkinDao {
    @Query("SELECT * FROM skins ORDER BY name")
    fun observeAll(): Flow<List<SkinEntity>>

    @Query("SELECT * FROM skins WHERE id = :id")
    suspend fun byId(id: Long): SkinEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skin: SkinEntity): Long

    @Query("DELETE FROM skins WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteById(id: Long)
}

@Dao
interface GamepadProfileDao {
    @Query("SELECT * FROM gamepad_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GamepadProfileEntity>>

    @Query("SELECT * FROM gamepad_profiles WHERE id = :id")
    suspend fun byId(id: Long): GamepadProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: GamepadProfileEntity): Long

    @Query("DELETE FROM gamepad_profiles WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM gamepad_profiles")
    suspend fun count(): Int
}
