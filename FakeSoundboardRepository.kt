package com.voxasoundboard.app.data.repositories

import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.data.db.relations.SoundboardWithSounds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSoundboardRepository : SoundboardRepository {

    val soundboardsFlow = MutableStateFlow<List<Soundboard>>(emptyList())

    private var nextId = 1L
    var lastInsertedId: Long? = null
    var lastDeletedId: Long? = null
    var lastUpdatedName: Pair<Long, String>? = null
    var lastPositionOrder: List<Long>? = null
    
    var exceptionToThrow: Exception? = null
    var insertGate: CompletableDeferred<Unit>? = null

    override fun getAllSoundboards(): Flow<List<Soundboard>> = soundboardsFlow

    override fun getAllSoundboardsWithSounds(): Flow<List<SoundboardWithSounds>> =
        MutableStateFlow(emptyList())

    override fun getSoundboard(id: Long): Flow<SoundboardWithSounds?> =
        MutableStateFlow(null)

    override suspend fun insertSoundboardAtNextPosition(): Long {
        insertGate?.await()
        val id = nextId++
        lastInsertedId = id
        val newBoard = Soundboard(id = id, position = soundboardsFlow.value.size)
        soundboardsFlow.value += newBoard
        return id
    }

    override suspend fun addSoundToSoundboardWithSounds(soundboardId: Long, soundId: Long) = Unit

    override suspend fun addSoundToSoundboardAtNextPosition(soundboardId: Long, soundId: Long) = Unit

    override suspend fun deleteSoundboardSoundCrossRef(soundId: Long, soundboardId: Long) = Unit

    override suspend fun deleteSoundboard(id: Long) {
        lastDeletedId = id
        soundboardsFlow.value = soundboardsFlow.value.filter { it.id != id }
    }

    override suspend fun updateSoundboardName(id: Long, name: String) {
        exceptionToThrow?.let { throw it }
        lastUpdatedName = id to name
        soundboardsFlow.value = soundboardsFlow.value.map {
            if (it.id == id) it.copy(name = name) else it
        }
    }

    override suspend fun getStarterBoard(): Soundboard? =
        soundboardsFlow.value.firstOrNull { it.isStarterBoard }

    override suspend fun updateSoundboardPositions(soundboardIdsInOrder: List<Long>) {
        lastPositionOrder = soundboardIdsInOrder
    }

    override suspend fun createStarterBoard(): Long {
        val id = nextId++
        val starterBoard = Soundboard(
            id = id,
            name = "Starter Sounds",
            isStarterBoard = true,
            starterBoardVersion = 1,
            position = soundboardsFlow.value.size
        )
        soundboardsFlow.value += starterBoard
        return id
    }

    override suspend fun getSoundIdsInOrder(soundboardId: Long): List<Long> = emptyList()

    override suspend fun updateSoundPositions(soundboardId: Long, soundIdsInOrder: List<Long>) = Unit

    override suspend fun sortSoundsByNameAscending(soundboardId: Long) = Unit

    override suspend fun getSoundboardCountForSound(soundId: Long): Int = 0
}
