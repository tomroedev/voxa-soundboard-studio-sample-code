package com.voxasoundboard.app.data.repositories

import com.voxasoundboard.app.data.db.dao.SoundboardDao
import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.data.db.entities.SoundboardSoundCrossRef
import com.voxasoundboard.app.data.db.relations.SoundboardWithSounds
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SoundboardRepositoryImpl @Inject constructor(
    private val dao: SoundboardDao
) : SoundboardRepository {

    override fun getAllSoundboards(): Flow<List<Soundboard>> = dao.getAllSoundboards()

    override fun getAllSoundboardsWithSounds(): Flow<List<SoundboardWithSounds>> =
        dao.getAllSoundboardsWithSounds()

    override fun getSoundboard(id: Long): Flow<SoundboardWithSounds?> =
        dao.getSoundboardWithSounds(id)

    override suspend fun insertSoundboardAtNextPosition(): Long =
        dao.insertSoundboardAtNextPosition()

    override suspend fun addSoundToSoundboardWithSounds(
        soundboardId: Long,
        soundId: Long
    ) {
        val nextPosition = dao.getMaxSoundPositionForSoundboard(soundboardId) + 1
        dao.insertSoundboardSoundCrossRef(
            SoundboardSoundCrossRef(
                soundboardId = soundboardId,
                soundId = soundId,
                position = nextPosition
            )
        )
    }

    override suspend fun addSoundToSoundboardAtNextPosition(
        soundboardId: Long,
        soundId: Long
    ) = dao.linkSoundToSoundboardAtNextPosition(soundboardId, soundId)

    override suspend fun deleteSoundboardSoundCrossRef(
        soundId: Long,
        soundboardId: Long
    ) {
        dao.deleteSoundboardSoundCrossRef(soundId = soundId, soundboardId = soundboardId)
        dao.deletePerSoundSettings(soundId = soundId, soundboardId = soundboardId)
    }

    override suspend fun deleteSoundboard(id: Long) {
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
    ) {
        soundboardIdsInOrder.forEachIndexed { index, soundboardId ->
            dao.updateSoundboardPosition(soundboardId = soundboardId, position = index)
        }
    }

    override suspend fun createStarterBoard(name: String): Long =
        dao.insertSoundboardAtNextPosition(
            name = name,
            isStarterBoard = true,
            starterBoardVersion = 1
        )

    override suspend fun getSoundIdsInOrder(soundboardId: Long): List<Long> =
        dao.getSoundIdsInOrder(soundboardId)

    override suspend fun updateSoundPositions(
        soundboardId: Long,
        soundIdsInOrder: List<Long>
    ) {
        soundIdsInOrder.forEachIndexed { index, soundId ->
            dao.updateSoundPosition(soundboardId, soundId, index)
        }
    }

    override suspend fun sortSoundsByNameAscending(
        soundboardId: Long
    ) = dao.sortSoundsByNameAscending(soundboardId)

    override suspend fun getSoundboardCountForSound(soundId: Long): Int =
        dao.getSoundboardCountForSound(soundId)

    override suspend fun getAllSoundboardsCount(): Int =
        dao.getSoundboardCount()
}
