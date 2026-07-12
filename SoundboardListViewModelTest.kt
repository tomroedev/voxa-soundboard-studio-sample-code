package com.voxasoundboard.app.ui.features.soundboardlist

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.voxasoundboard.app.analytics.AnalyticsTracker
import com.voxasoundboard.app.audio.DeleteSoundboardUseCase
import com.voxasoundboard.app.audio.FakeSoundPlayer
import com.voxasoundboard.app.audio.SetupStarterBoardUseCase
import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.data.repositories.FakeGeneralUiSettingsRepository
import com.voxasoundboard.app.data.repositories.FakeProLocalDataSource
import com.voxasoundboard.app.data.repositories.FakeSoundRepository
import com.voxasoundboard.app.data.repositories.FakeSoundboardRepository
import com.voxasoundboard.app.data.repositories.ProRepository
import com.voxasoundboard.app.monitoring.FakeCrashReporter
import com.voxasoundboard.app.sync.RestoreAudioUseCase
import com.voxasoundboard.app.sync.RestoreResult
import com.voxasoundboard.app.ui.features.soundboardlist.model.SoundboardListScreenUserMessage
import com.voxasoundboard.app.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SoundboardListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeRepo = FakeSoundboardRepository()
    private val fakeSoundRepo = FakeSoundRepository()
    private val fakeGeneralUiSettingsRepo = FakeGeneralUiSettingsRepository()
    private val fakeProRepo = ProRepository(FakeProLocalDataSource())
    private val fakeSoundPlayer = FakeSoundPlayer()
    private val fakeAnalyticsTracker = AnalyticsTracker { _, _ -> }
    private val fakeCrashReporter = FakeCrashReporter()

    private val soundboardOne = Soundboard(id = 0L, name = "A", position = 0)
    private val soundboardTwo = Soundboard(id = 1L, name = "B", position = 1)
    private val soundboardThree = Soundboard(id = 2L, name = "C", position = 2)
    private val listOfThreeSoundboards = listOf(soundboardOne, soundboardTwo, soundboardThree)

    private lateinit var viewModel: SoundboardListViewModel

    @Before
    fun setUp() {
        viewModel = SoundboardListViewModel(
            setupStarterBoardUseCase = SetupStarterBoardUseCase { },
            deleteSoundboardUseCase = DeleteSoundboardUseCase { id -> fakeRepo.deleteSoundboard(id) },
            soundboardRepo = fakeRepo,
            soundRepo = fakeSoundRepo,
            ioDispatcher = mainDispatcherRule.testDispatcher,
            generalUiSettingsRepo = fakeGeneralUiSettingsRepo,
            proRepo = fakeProRepo,
            soundPlayer = fakeSoundPlayer,
            restoreAudioUseCase = RestoreAudioUseCase { RestoreResult(0, 0) },
            analyticsTracker = fakeAnalyticsTracker,
            crashReporter = fakeCrashReporter
        )
    }

    // Initial state

    @Test
    fun `soundboards initial state is empty`() {
        assertThat(viewModel.soundboards.value).isEmpty()
    }

    @Test
    fun `editingSoundboardId initial state is null`() {
        assertThat(viewModel.editingSoundboardId.value).isNull()
    }

    // Soundboards flow

    @Test
    fun `soundboards reflects repository emissions`() = runTest {
      viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()
            fakeRepo.soundboardsFlow.emit(listOfThreeSoundboards)
            assertThat(awaitItem()).isEqualTo(listOfThreeSoundboards)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `soundboards updates when a board is added`() = runTest {
        viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()
            viewModel.addSoundboard()
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `soundboards updates when a board is deleted`() = runTest {
        viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()
            fakeRepo.soundboardsFlow.emit(listOf(soundboardOne))
            assertThat(awaitItem()).containsExactly(soundboardOne)
            viewModel.deleteSoundboard(soundboardOne.id)
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // setEditingSoundboardId

    @Test
    fun `setEditingSoundboardId updates state`() = runTest {
        viewModel.editingSoundboardId.test {
            assertThat(awaitItem()).isNull()
            viewModel.setEditingSoundboardId(42L)
            assertThat(awaitItem()).isEqualTo(42L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setEditingSoundboardId can be cleared back to null`() = runTest {
        viewModel.setEditingSoundboardId(1L)
        viewModel.editingSoundboardId.test {
            assertThat(awaitItem()).isEqualTo(1L)
            viewModel.setEditingSoundboardId(null)
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // addSoundboard

    @Test
    fun `addSoundboard inserts into the repo`() = runTest {
        viewModel.addSoundboard()
        assertThat(fakeRepo.lastInsertedId).isNotNull()
    }

    @Test
    fun `addSoundboard sets editingSoundboardId to the new board id`() = runTest {
        viewModel.addSoundboard()
        assertThat(viewModel.editingSoundboardId.value).isEqualTo(fakeRepo.lastInsertedId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `addSoundboard ignores further calls while the first insert is still in flight`() = runTest {
        val gate = CompletableDeferred<Unit>()
        fakeRepo.insertGate = gate

        viewModel.addSoundboard() // suspends inside the repo insert, guard flag now true
        viewModel.addSoundboard() // should be dropped by the guard, not queued
        viewModel.addSoundboard() // should be dropped by the guard, not queued

        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(fakeRepo.soundboardsFlow.value).hasSize(1)
    }

    @Test
    fun `addSoundboard allows a new call once the previous one has completed`() = runTest {
        viewModel.addSoundboard()
        viewModel.addSoundboard()

        assertThat(fakeRepo.soundboardsFlow.value).hasSize(2)
    }

    @Test
    fun `addSoundboard records the exception via crashReporter and emits ADD_FAILED on failure`() = runTest {
        fakeRepo.insertGate = CompletableDeferred<Unit>().apply {
            completeExceptionally(IllegalStateException("simulated insert failure"))
        }

        viewModel.showMessageToTheUser.test {
            viewModel.addSoundboard()
            assertThat(awaitItem()).isEqualTo(SoundboardListScreenUserMessage.ADD_FAILED)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(fakeCrashReporter.recordedExceptions).hasSize(1)
    }

    // deleteSoundboard

    @Test
    fun `deleteSoundboard removes the board from the repo`() = runTest {
        fakeRepo.soundboardsFlow.emit(listOf(soundboardOne))

        viewModel.deleteSoundboard(soundboardOne.id)

        assertThat(fakeRepo.lastDeletedId).isEqualTo(soundboardOne.id)
        assertThat(fakeRepo.soundboardsFlow.value).isEmpty()
    }

    // updateSoundboardName

    @Test
    fun `updateSoundboardName updates the name in the repo`() = runTest {
        viewModel.updateSoundboardName(1L, "New Name")
        assertThat(fakeRepo.lastUpdatedName).isEqualTo(1L to "New Name")
    }

    @Test
    fun `updateSoundboardName clears editingSoundboardId`() = runTest {
        viewModel.setEditingSoundboardId(1L)
        viewModel.updateSoundboardName(1L, "New Name")
        assertThat(viewModel.editingSoundboardId.value).isNull()
    }

    @Test
    fun `updateSoundboardName keeps editingSoundboardId when the repo write fails`() = runTest {
        fakeRepo.exceptionToThrow = IllegalStateException("simulated repo failure")
        viewModel.setEditingSoundboardId(1L)

        viewModel.updateSoundboardName(1L, "New Name")

        // Row stays in edit mode rather than reverting to a stale read-only display.
        assertThat(viewModel.editingSoundboardId.value).isEqualTo(1L)
    }

    @Test
    fun `updateSoundboardName records the exception via crashReporter and emits RENAME_FAILED on failure`() = runTest {
        val exception = IllegalStateException("simulated repo failure")
        fakeRepo.exceptionToThrow = exception

        viewModel.showMessageToTheUser.test {
            viewModel.updateSoundboardName(1L, "New Name")
            assertThat(awaitItem()).isEqualTo(SoundboardListScreenUserMessage.RENAME_FAILED)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(fakeCrashReporter.recordedExceptions).containsExactly(exception)
    }

    // moveSoundboard
    //
    // These tests subscribe to soundboards first so that the WhileSubscribed
    // chain activates and allSoundboards.value reflects the repo before
    // moveSoundboard reads from it.

    @Test
    fun `moveSoundboard reorders the list`() = runTest {
        viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()

            fakeRepo.soundboardsFlow.emit(listOfThreeSoundboards)

            assertThat(awaitItem().map { it.name })
                .containsExactly(soundboardOne.name, soundboardTwo.name, soundboardThree.name).inOrder()

            viewModel.moveSoundboard(fromIndex = 0, toIndex = 2)

            assertThat(awaitItem().map { it.name })
                .containsExactly(soundboardTwo.name, soundboardThree.name, soundboardOne.name).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moveSoundboard with same fromIndex and toIndex is a no-op`() = runTest {
        viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()
            fakeRepo.soundboardsFlow.emit(listOf(soundboardOne, soundboardTwo))
            assertThat(awaitItem().map { it.name }).containsExactly(soundboardOne.name, soundboardTwo.name).inOrder()

            viewModel.moveSoundboard(fromIndex = 1, toIndex = 1)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moveSoundboard with out-of-bounds toIndex is a no-op`() = runTest {
        viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()
            fakeRepo.soundboardsFlow.emit(listOf(soundboardOne))
            assertThat(awaitItem().map { it.name }).containsExactly(soundboardOne.name)

            viewModel.moveSoundboard(fromIndex = 0, toIndex = 99)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moveSoundboard preserves subsequent moves within a drag session`() = runTest {
        viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()
            fakeRepo.soundboardsFlow.emit(listOfThreeSoundboards)
            assertThat(awaitItem().map { it.name })
                .containsExactly(soundboardOne.name, soundboardTwo.name, soundboardThree.name).inOrder()

            viewModel.moveSoundboard(fromIndex = 0, toIndex = 1)
            assertThat(awaitItem().map { it.name })
                .containsExactly(soundboardTwo.name, soundboardOne.name, soundboardThree.name).inOrder()
            viewModel.moveSoundboard(fromIndex = 2, toIndex = 0)
            assertThat(awaitItem().map { it.name })
                .containsExactly(soundboardThree.name, soundboardTwo.name, soundboardOne.name).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // persistSoundboardOrder

    @Test
    fun `persistSoundboardOrder sends reordered ids to the repo`() = runTest {
        viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()
            fakeRepo.soundboardsFlow.emit(listOf(soundboardOne, soundboardTwo))
            assertThat(awaitItem().map { it.name }).containsExactly(soundboardOne.name, soundboardTwo.name).inOrder()
            viewModel.moveSoundboard(fromIndex = 0, toIndex = 1)
            assertThat(awaitItem().map { it.name }).containsExactly(soundboardTwo.name, soundboardOne.name).inOrder()

            viewModel.persistSoundboardOrder()

            assertThat(fakeRepo.lastPositionOrder).isEqualTo(listOf(soundboardTwo.id, soundboardOne.id))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persistSoundboardOrder clears drag state`() = runTest {
        val boards = listOf(soundboardOne, soundboardTwo)

        viewModel.soundboards.test {
            assertThat(awaitItem()).isEmpty()
            fakeRepo.soundboardsFlow.emit(boards)
            assertThat(awaitItem().map { it.name }).containsExactly(soundboardOne.name, soundboardTwo.name).inOrder()
            viewModel.moveSoundboard(fromIndex = 0, toIndex = 1)
            assertThat(awaitItem().map { it.name }).containsExactly(soundboardTwo.name, soundboardOne.name).inOrder()

            viewModel.persistSoundboardOrder()

            // drag state cleared - reverts to repo order
            assertThat(awaitItem()).isEqualTo(boards)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `persistSoundboardOrder does nothing when no drag is in progress`() = runTest {
        viewModel.persistSoundboardOrder()
        assertThat(fakeRepo.lastPositionOrder).isNull()
    }
}
