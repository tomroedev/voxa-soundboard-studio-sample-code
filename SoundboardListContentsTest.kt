package com.voxasoundboard.app.ui.features.soundboardlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.ui.theme.VoxaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundboardListContentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        soundboards: List<Soundboard> = emptyList(),
        onSoundboardClick: (Long) -> Unit = {},
        onAddNewSoundboardClick: () -> Unit = {},
        editingSoundboardId: Long? = null,
        playingCountBySoundboard: Map<Long, Int> = emptyMap(),
    ) {
        composeTestRule.setContent {
            VoxaTheme {
                SoundboardListContents(
                    soundboards = soundboards,
                    onSoundboardClick = onSoundboardClick,
                    onSoundboardMenuClick = {},
                    onAddNewSoundboardClick = onAddNewSoundboardClick,
                    editingSoundboardId = editingSoundboardId,
                    updateSoundboardName = { _, _ -> },
                    moveSound = { _, _ -> },
                    onReorderFinished = {},
                    playingCountBySoundboard = playingCountBySoundboard,
                )
            }
        }
    }

    // Display

    @Test
    fun showsSoundboardNames() {
        setContent(
            soundboards = listOf(
                Soundboard(id = 1, name = "Applause"),
                Soundboard(id = 2, name = "Stadium Crowd"),
            )
        )

        composeTestRule.onNodeWithText("Applause").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stadium Crowd").assertIsDisplayed()
    }

    // Add new item

    @Test
    fun showsAddNewItem_whenNotEditing() {
        setContent(editingSoundboardId = null)

        composeTestRule.onNodeWithText("Add soundboard").assertIsDisplayed()
    }

    @Test
    fun hidesAddNewItem_whenEditing() {
        setContent(
            soundboards = listOf(Soundboard(id = 1, name = "Applause")),
            editingSoundboardId = 1L,
        )

        composeTestRule.onNodeWithText("Add soundboard").assertDoesNotExist()
    }

    // Editing state

    @Test
    fun showsEditableField_forBoardBeingEdited() {
        setContent(
            soundboards = listOf(Soundboard(id = 1, name = "Applause")),
            editingSoundboardId = 1L,
        )

        composeTestRule.onNodeWithContentDescription("Confirm name").assertExists()
    }

    @Test
    fun showsNormalListItem_forBoardsNotBeingEdited() {
        setContent(
            soundboards = listOf(
                Soundboard(id = 1, name = "Applause"),
                Soundboard(id = 2, name = "Stadium Crowd"),
            ),
            editingSoundboardId = 1L,
        )

        composeTestRule.onNodeWithText("Stadium Crowd").assertIsDisplayed()
    }

    // Playing count

    @Test
    fun showsPlayingCount_whenSoundsAreActive() {
        setContent(
            soundboards = listOf(Soundboard(id = 1, name = "Applause")),
            playingCountBySoundboard = mapOf(1L to 3),
        )

        composeTestRule.onNodeWithContentDescription("3 sounds playing").assertIsDisplayed()
    }

    @Test
    fun hidesPlayingCount_whenNoSoundsAreActive() {
        setContent(
            soundboards = listOf(Soundboard(id = 1, name = "Applause")),
            playingCountBySoundboard = emptyMap(),
        )

        composeTestRule.onNodeWithContentDescription("sounds playing", substring = true).assertDoesNotExist()
    }

    // Interactions

    @Test
    fun callsOnSoundboardClick_withSoundboardId_whenBodyClicked() {
        var clickedId: Long? = null
        setContent(
            soundboards = listOf(Soundboard(id = 42L, name = "Applause")),
            onSoundboardClick = { clickedId = it },
        )

        composeTestRule.onNodeWithText("Applause").performClick()

        assertThat(clickedId).isEqualTo(42L)
    }

    @Test
    fun callsOnAddNewSoundboardClick_whenAddItemClicked() {
        var clicked = false
        setContent(onAddNewSoundboardClick = { clicked = true })

        composeTestRule.onNodeWithText("Add soundboard").performClick()

        assertThat(clicked).isTrue()
    }
}
