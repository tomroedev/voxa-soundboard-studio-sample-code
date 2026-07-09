package com.voxasoundboard.app.ui.features.soundboardlist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SoundboardListContentsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun showsPlayingCount() {
        paparazzi.snapshot {
            SoundboardListContentsPreviewHarness(
                soundboards = SoundboardListPreviewFixtures.twoSoundboards,
                playingCountBySoundboard = SoundboardListPreviewFixtures.playingCountsWithOverflow
            )
        }
    }

    @Test
    fun showsMultipleSoundboards() {
        paparazzi.snapshot {
            SoundboardListContentsPreviewHarness(soundboards = SoundboardListPreviewFixtures.threeSoundboards)
        }
    }

    @Test
    fun showsNoSoundboards() {
        paparazzi.snapshot {
            SoundboardListContentsPreviewHarness(soundboards = emptyList())
        }
    }

    @Test
    fun showsEditingSoundboard() {
        paparazzi.snapshot {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                SoundboardListContentsPreviewHarness(
                    soundboards = SoundboardListPreviewFixtures.threeSoundboards,
                    editingSoundboardId = 1
                )
            }
        }
    }

    @Test
    fun showsMultipleSoundboardsInLightMode() {
        paparazzi.snapshot {
            SoundboardListContentsPreviewHarness(
                soundboards = SoundboardListPreviewFixtures.threeSoundboards,
                darkMode = false
            )
        }
    }

    @Test
    fun showsLongSoundboardNameTruncatedWithEllipsis() {
        paparazzi.snapshot {
            SoundboardListContentsPreviewHarness(
                soundboards = SoundboardListPreviewFixtures.singleSoundboardWithLongName,
                playingCountBySoundboard = SoundboardListPreviewFixtures.singleBoardPlayingCount
            )
        }
    }

}
