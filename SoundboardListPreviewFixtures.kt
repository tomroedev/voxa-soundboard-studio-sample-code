package com.voxasoundboard.app.ui.features.soundboardlist

import androidx.compose.runtime.Composable
import com.voxasoundboard.app.data.db.entities.GeneralUiSettings
import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.ui.theme.VoxaTheme

internal object SoundboardListPreviewFixtures {
    val playingCountsWithOverflow = mapOf(1L to 3, 2L to 999)
    val singleBoardPlayingCount = mapOf(1L to 3)
    val singleSoundboardWithLongName = listOf(
        Soundboard(id = 1, name = "Crowd Going Wild with Lots of Applause and Cheering", position = 0)
    )
    val threeSoundboards = listOf(
        Soundboard(id = 1, name = "Applause", position = 0),
        Soundboard(id = 2, name = "Cheering", position = 1),
        Soundboard(id = 3, name = "Drum roll", position = 2)
    )
    val twoSoundboards = listOf(
        Soundboard(id = 1, name = "Applause", position = 0),
        Soundboard(id = 2, name = "Cheering", position = 1)
    )
}

@Composable
internal fun SoundboardListContentsPreviewHarness(
    soundboards: List<Soundboard>,
    playingCountBySoundboard: Map<Long, Int> = emptyMap(),
    editingSoundboardId: Long? = null,
    darkMode: Boolean = true
) {
    VoxaTheme(GeneralUiSettings(darkMode = darkMode)) {
        SoundboardListContents(
            soundboards = soundboards,
            onSoundboardClick = {},
            onSoundboardMenuClick = {},
            onAddNewSoundboardClick = {},
            editingSoundboardId = editingSoundboardId,
            updateSoundboardName = { _, _ -> },
            moveSound = { _, _ -> },
            onReorderFinished = {},
            playingCountBySoundboard = playingCountBySoundboard
        )
    }
}
