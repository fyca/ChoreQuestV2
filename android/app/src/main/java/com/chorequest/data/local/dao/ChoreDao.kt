package com.chorequest.data.local.dao

import androidx.room.*
import com.chorequest.data.local.entities.ChoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChoreDao {

    @Query("SELECT * FROM chores ORDER BY createdAt DESC")
    fun getAllChores(): Flow<List<ChoreEntity>>

    @Query("SELECT * FROM chores WHERE id = :choreId")
    suspend fun getChoreById(choreId: String): ChoreEntity?

    @Query("SELECT * FROM chores WHERE id IN (:assignedTo)")
    fun getChoresForUser(assignedTo: String): Flow<List<ChoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChore(chore: ChoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChores(chores: List<ChoreEntity>)

    @Update
    suspend fun updateChore(chore: ChoreEntity)

    @Delete
    suspend fun deleteChore(chore: ChoreEntity)

    @Query("DELETE FROM chores")
    suspend fun deleteAllChores()

    @Query("SELECT * FROM chores WHERE status = :status")
    fun getChoresByStatus(status: String): Flow<List<ChoreEntity>>
}
