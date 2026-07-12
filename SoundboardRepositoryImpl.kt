package com.voxasoundboard.app.data.repositories

import com.voxasoundboard.app.data.db.dao.SoundboardDao
import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.data.db.entities.SoundboardSoundCrossRef
import com.voxasoundboard.app.data.db.relations.SoundboardWithSounds
import com.voxasoundboard.app.data.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SoundboardRepositoryImpl @Inject constructor(
    private val dao: SoundboardDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SoundboardRepository {

    override fun getAllSoundboards(): Flow<List<Soundboard>> = dao.getAllSoundboards()

    override fun getAllSoundboardsWithSounds(): Flow<List<SoundboardWithSounds>> =
        dao.getAllSoundboardsWithSounds()

    override fun getSoundboard(id: Long): Flow<SoundboardWithSounds?> =
        dao.getSoundboardWithSounds(id)

    override suspend fun insertSoundboardAtNextPosition(): Long = withContext(ioDispatcher) {
        dao.insertSoundboardAtNextPosition()
    }

    override suspend fun addSoundToSoundboardWithSounds(
        soundboardId: Long,
        soundId: Long
    ) = withContext(ioDispatcher) {
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
    ) = withContext(ioDispatcher) {
        dao.linkSoundToSoundboardAtNextPosition(soundboardId, soundId)
    }

    override suspend fun deleteSoundboardSoundCrossRef(
        soundId: Long,
        soundboardId: Long
    ) = withContext(ioDispatcher) {
        dao.deleteSoundboardSoundCrossRef(soundId = soundId, soundboardId = soundboardId)
        dao.deletePerSoundSettings(soundId = soundId, soundboardId = soundboardId)
    }

    override suspend fun deleteSoundboard(id: Long) = withContext(ioDispatcher) {
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
    ) = withContext(ioDispatcher) {
        soundboardIdsInOrder.forEachIndexed { index, soundboardId ->
            dao.updateSoundboardPosition(soundboardId = soundboardId, position = index)
        }
    }

    override suspend fun createStarterBoard(name: String): Long = withContext(ioDispatcher) {
        dao.insertSoundboardAtNextPosition(
            name = name,
            isStarterBoard = true,
            starterBoardVersion = 1
        )
    }

    override suspend fun getSoundIdsInOrder(soundboardId: Long): List<Long> =
        withContext(ioDispatcher) { dao.getSoundIdsInOrder(soundboardId) }

    override suspend fun updateSoundPositions(
        soundboardId: Long,
        soundIdsInOrder: List<Long>
    ) = withContext(ioDispatcher) {
        soundIdsInOrder.forEachIndexed { index, soundId ->
            dao.updateSoundPosition(soundboardId, soundId, index)
        }
    }

    override suspend fun sortSoundsByNameAscending(
        soundboardId: Long
    ) = withContext(ioDispatcher) {
        dao.sortSoundsByNameAscending(soundboardId)
    }

    override suspend fun getSoundboardCountForSound(soundId: Long): Int =
        withContext(ioDispatcher) { dao.getSoundboardCountForSound(soundId) }

    override suspend fun getAllSoundboardsCount(): Int =
        withContext(ioDispatcher) {
            dao.getSoundboardCount()
        }
}
