package com.voxasoundboard.app.ui.features.soundboardlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voxasoundboard.app.FreeTierLimits
import com.voxasoundboard.app.analytics.Analytics
import com.voxasoundboard.app.analytics.AnalyticsTracker
import com.voxasoundboard.app.audio.DeleteSoundboardUseCase
import com.voxasoundboard.app.audio.SetupStarterBoardUseCase
import com.voxasoundboard.app.audio.SoundKey
import com.voxasoundboard.app.audio.SoundPlayer
import com.voxasoundboard.app.data.db.entities.GeneralUiSettings
import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.data.repositories.GeneralUiSettingsRepository
import com.voxasoundboard.app.data.repositories.ProRepository
import com.voxasoundboard.app.data.repositories.SoundboardRepository
import com.voxasoundboard.app.monitoring.CrashReporter
import com.voxasoundboard.app.sync.DetectMissingAudioUseCase
import com.voxasoundboard.app.sync.RestoreAudioUseCase
import com.voxasoundboard.app.sync.RestoreResult
import com.voxasoundboard.app.ui.features.soundboardlist.model.SoundboardListScreenUserMessage
import com.voxasoundboard.app.ui.models.ProTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoundboardListViewModel @Inject constructor(
    generalUiSettingsRepo: GeneralUiSettingsRepository,
    proRepo: ProRepository,
    private val setupStarterBoardUseCase: SetupStarterBoardUseCase,
    private val deleteSoundboardUseCase: DeleteSoundboardUseCase,
    private val soundboardRepo: SoundboardRepository,
    private val soundPlayer: SoundPlayer,
    private val detectMissingAudioUseCase: DetectMissingAudioUseCase,
    private val restoreAudioUseCase: RestoreAudioUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val crashReporter: CrashReporter
) : ViewModel() {
    private val _editingSoundboardId = MutableStateFlow<Long?>(null)
    val editingSoundboardId: StateFlow<Long?> = _editingSoundboardId.asStateFlow()

    private val _dragState = MutableStateFlow<List<Soundboard>?>(null)

    private val _showProRequired = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val showProRequired: SharedFlow<Unit> = _showProRequired.asSharedFlow()

    private val _showSetupError = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val showSetupError: SharedFlow<Unit> = _showSetupError.asSharedFlow()

    private val _showMessageToTheUser = MutableSharedFlow<SoundboardListScreenUserMessage>(extraBufferCapacity = 1)
    val showMessageToTheUser: SharedFlow<SoundboardListScreenUserMessage> = _showMessageToTheUser.asSharedFlow()

    private val _missingSoundCount = MutableStateFlow(0)
    val missingSoundCount: StateFlow<Int> = _missingSoundCount.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _restoreResult = MutableSharedFlow<RestoreResult>(extraBufferCapacity = 1)
    val restoreResult: SharedFlow<RestoreResult> = _restoreResult.asSharedFlow()

    private val _isAddingSoundboard = MutableStateFlow(false)

    val allSoundboards: StateFlow<List<Soundboard>> =
        soundboardRepo.getAllSoundboards()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    val soundboards: StateFlow<List<Soundboard>> =
        combine(allSoundboards, _dragState) { db, drag ->
            drag ?: db
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val generalUiSettings: StateFlow<GeneralUiSettings> = generalUiSettingsRepo.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GeneralUiSettings()
        )

    val isPro: StateFlow<Boolean> = proRepo.proSettings
        .map { it.tier == ProTier.PRO }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val playingCountBySoundboard: StateFlow<Map<Long, Int>> =
        combine(
            soundboardRepo.getAllSoundboardsWithSounds(),
            soundPlayer.playbackStates
        ) { boards, playbackStates ->
            val playingKeys = playbackStates.keys
            if (playingKeys.isEmpty()) return@combine emptyMap()
            boards
                .mapNotNull { board ->
                    val count = board.sounds.count { SoundKey(board.soundboard.id, it.id) in playingKeys }
                    if (count > 0) board.soundboard.id to count else null
                }
                .toMap()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    init {
        setupStarterBoard()
        detectMissingAudio()
    }

    private fun detectMissingAudio() {
        viewModelScope.launch {
            try {
                _missingSoundCount.value = detectMissingAudioUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(e)
            }
        }
    }

    fun restoreSounds() {
        if (_isRestoring.value) return
        viewModelScope.launch {
            _isRestoring.value = true
            try {
                val result = restoreAudioUseCase()
                _restoreResult.tryEmit(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(e)
                _showMessageToTheUser.tryEmit(SoundboardListScreenUserMessage.RESTORE_FAILED)
            } finally {
                // Re-check so the Dialog reflects whatever is still missing after the scan.
                try {
                    _missingSoundCount.value = detectMissingAudioUseCase()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    crashReporter.recordException(e)
                }
                _isRestoring.value = false
            }
        }
    }

    private fun setupStarterBoard() {
        viewModelScope.launch {
            try {
                setupStarterBoardUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(e)
                _showSetupError.tryEmit(Unit)
            }
        }
    }

    fun stopAllSounds() {
        soundPlayer.stopAll()
    }

    fun setEditingSoundboardId(id: Long?) {
        _editingSoundboardId.value = id
    }

    fun addSoundboard() {
        if (_isAddingSoundboard.value) return
        viewModelScope.launch {
            _isAddingSoundboard.value = true
            try {
                val soundboardCount = soundboardRepo.getAllSoundboardsCount()
                if (!isPro.value && soundboardCount >= FreeTierLimits.FREE_BOARD_LIMIT) {
                    _showProRequired.tryEmit(Unit)
                    analyticsTracker.logEvent(
                        Analytics.EVENT_PRO_PAYWALL_SHOWN,
                        mapOf(Analytics.PARAM_TRIGGER to Analytics.TRIGGER_ADD_SOUNDBOARD)
                    )
                    return@launch
                }
                val soundboardId = soundboardRepo.insertSoundboardAtNextPosition()
                setEditingSoundboardId(soundboardId)
                analyticsTracker.logEvent(Analytics.EVENT_SOUNDBOARD_CREATED, emptyMap())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(e)
                _showMessageToTheUser.tryEmit(SoundboardListScreenUserMessage.ADD_FAILED)
            } finally {
                _isAddingSoundboard.value = false
            }
        }
    }

    fun deleteSoundboard(id: Long) {
        soundPlayer.playbackStates.value.keys
            .filter { it.soundboardId == id }
            .forEach { soundPlayer.stop(it, restart = false) }
        viewModelScope.launch {
            try {
                deleteSoundboardUseCase(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(e)
                _showMessageToTheUser.tryEmit(SoundboardListScreenUserMessage.DELETE_FAILED)
            }
        }
    }

    fun updateSoundboardName(id: Long, name: String) {
        viewModelScope.launch {
            try {
                soundboardRepo.updateSoundboardName(id, name)
                setEditingSoundboardId(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(e)
                _showMessageToTheUser.tryEmit(SoundboardListScreenUserMessage.RENAME_FAILED)
            }
        }
    }

    fun moveSoundboard(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val current = (_dragState.value ?: allSoundboards.value).toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        _dragState.value = current
    }

    fun persistSoundboardOrder() {
        val current = _dragState.value ?: return
        viewModelScope.launch {
            try {
                soundboardRepo.updateSoundboardPositions(soundboardIdsInOrder = current.map { it.id })
                _dragState.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                crashReporter.recordException(e)
                _showMessageToTheUser.tryEmit(SoundboardListScreenUserMessage.REORDER_FAILED)
            }
        }
    }

}
