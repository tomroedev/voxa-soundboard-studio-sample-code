package com.voxasoundboard.app.data.repositories

import com.voxasoundboard.app.data.db.dao.SoundboardDao
import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.data.db.entities.SoundboardSoundCrossRef
import com.voxasoundboard.app.data.db.relations.SoundboardWithSounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SoundboardRepositoryImpl @Inject constructor(
    private val dao: SoundboardDao
) : SoundboardRepository {

    override fun getAllSoundboards(): Flow<List<Soundboard>> = dao.getAllSoundboards()

    override fun getAllSoundboardsWithSounds(): Flow<List<SoundboardWithSounds>> =
        dao.getAllSoundboardsWithSounds()

    override fun getSoundboard(id: Long): Flow<SoundboardWithSounds?> =
        dao.getSoundboardWithSounds(id)

    override suspend fun insertSoundboardAtNextPosition(): Long = withContext(Dispatchers.IO) {
        dao.insertSoundboardAtNextPosition()
    }

    override suspend fun addSoundToSoundboardWithSounds(
        soundboardId: Long,
        soundId: Long
    ) = withContext(Dispatchers.IO) {
        val nextPosition = dao.getMaxSoundPositionForSoundboard(soundboardId) + 1
        dao.insertSoundboardSoundCrossRef(
            SoundboardSoundCrossRef(
                soundboardId = soundboardId,
                soundId = soundId,
                position = nextPosition
            )
        )
        Unit
    }

    override suspend fun addSoundToSoundboardAtNextPosition(
        soundboardId: Long,
        soundId: Long
    ) = withContext(Dispatchers.IO) {
        dao.linkSoundToSoundboardAtNextPosition(soundboardId, soundId)
    }

    override suspend fun deleteSoundboardSoundCrossRef(
        soundId: Long,
        soundboardId: Long
    ) = withContext(Dispatchers.IO) {
        dao.deleteSoundboardSoundCrossRef(soundId = soundId, soundboardId = soundboardId)
        dao.deletePerSoundSettings(soundId = soundId, soundboardId = soundboardId)
    }

    override suspend fun deleteSoundboard(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteCrossRefsForSoundboard(id)
        dao.deletePerSoundSettingsForSoundboard(id)
        dao.deleteSoundboard(id)
    }

    override suspend fun updateSoundboardName(id: Long, name: String) {
        dao.updateSoundboardName(id, name)
    }

    override suspend fun getStarterBoard(): Soundboard? = dao.getStarterBoard()

    override suspend fun updateSoundboardPositions(
        soundboardIdsInOrder: List<Long>
    ) = withContext(Dispatchers.IO) {
        soundboardIdsInOrder.forEachIndexed { index, soundboardId ->
            dao.updateSoundboardPosition(soundboardId = soundboardId, position = index)
        }
    }

    override suspend fun createStarterBoard(): Long = withContext(Dispatchers.IO) {
        dao.insertSoundboardAtNextPosition(
            name = "Starter Sounds",
            isStarterBoard = true,
            starterBoardVersion = 1
        )
    }

    override suspend fun getSoundIdsInOrder(soundboardId: Long): List<Long> =
        withContext(Dispatchers.IO) { dao.getSoundIdsInOrder(soundboardId) }

    override suspend fun updateSoundPositions(
        soundboardId: Long,
        soundIdsInOrder: List<Long>
    ) = withContext(Dispatchers.IO) {
        soundIdsInOrder.forEachIndexed { index, soundId ->
            dao.updateSoundPosition(soundboardId, soundId, index)
        }
    }

    override suspend fun sortSoundsByNameAscending(
        soundboardId: Long
    ) = withContext(Dispatchers.IO) {
        dao.sortSoundsByNameAscending(soundboardId)
    }

    override suspend fun getSoundboardCountForSound(soundId: Long): Int =
        withContext(Dispatchers.IO) { dao.getSoundboardCountForSound(soundId) }
}
