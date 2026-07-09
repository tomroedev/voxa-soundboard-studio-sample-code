package com.voxasoundboard.app.ui.features.soundboardlist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.voxasoundboard.app.AboutSettingsRoute
import com.voxasoundboard.app.FreeTierLimits
import com.voxasoundboard.app.GeneralUiSettingsRoute
import com.voxasoundboard.app.PlaybackSettingsRoute
import com.voxasoundboard.app.ProSettingsRoute
import com.voxasoundboard.app.R
import com.voxasoundboard.app.data.db.entities.Soundboard
import com.voxasoundboard.app.ui.components.ConfirmDialog
import com.voxasoundboard.app.ui.components.SoundboardListItemAddNew
import com.voxasoundboard.app.ui.components.SoundboardListItemEditable
import com.voxasoundboard.app.ui.components.SoundboardListItemExisting
import com.voxasoundboard.app.ui.components.SoundboardListMenuModal
import com.voxasoundboard.app.ui.components.SoundboardListMissingSoundsDialog
import com.voxasoundboard.app.ui.components.SoundboardListOptionsSheet
import com.voxasoundboard.app.ui.components.VoxaPage
import com.voxasoundboard.app.ui.components.rememberAccessibleMessenger
import com.voxasoundboard.app.ui.features.appsettings.model.AppSettingKey
import com.voxasoundboard.app.ui.features.soundboardlist.model.SoundboardListScreenUserMessage
import com.voxasoundboard.app.ui.models.SettingsUiItem
import com.voxasoundboard.app.ui.theme.Dimens
import com.voxasoundboard.app.ui.toolbar.LocalToolbarController
import com.voxasoundboard.app.ui.toolbar.ToolbarState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SoundboardListScreen(
    onSoundboardClick: (Long) -> Unit,
    onNavigate: (Any) -> Unit,
    viewModel: SoundboardListViewModel = hiltViewModel()
) {
    // Ambient
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val toolbar = LocalToolbarController.current

    // Accessible status messages (replaces Toast, which TalkBack doesn't reliably announce)
    val showMessage = rememberAccessibleMessenger()
    val addSoundboardFailedMessage = stringResource(R.string.message_add_soundboard_failed)
    val deleteSoundboardFailedMessage = stringResource(R.string.message_delete_soundboard_failed)
    val renameSoundboardFailedMessage = stringResource(R.string.message_rename_soundboard_failed)
    val reorderSoundboardsFailedMessage = stringResource(R.string.message_reorder_soundboards_failed)
    val restoreSoundsFailedMessage = stringResource(R.string.message_restore_sounds_failed)
    val setupFailedMessage = stringResource(R.string.message_setup_failed)

    // ViewModel state
    val editingSoundboardId by viewModel.editingSoundboardId.collectAsStateWithLifecycle()
    val generalUiSettings by viewModel.generalUiSettings.collectAsStateWithLifecycle()
    val isRestoring by viewModel.isRestoring.collectAsStateWithLifecycle()
    val missingSoundCount by viewModel.missingSoundCount.collectAsStateWithLifecycle()
    val playingCountBySoundboard by viewModel.playingCountBySoundboard.collectAsStateWithLifecycle()
    val soundboards by viewModel.soundboards.collectAsStateWithLifecycle()

    // Local state
    var dismissedRestoreDialog by remember { mutableStateOf(false) }
    var selectedSoundboardForMenu by remember { mutableStateOf<Soundboard?>(null) }
    var showBoardLimitDialog by remember { mutableStateOf(false) }
    var showRestorePermissionRationale by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var soundboardPendingDelete by remember { mutableStateOf<Soundboard?>(null) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.restoreSounds()
        else showRestorePermissionRationale = true
    }

    val onRestoreSoundsClick: () -> Unit = {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, audioPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) viewModel.restoreSounds()
        else permissionLauncher.launch(audioPermission)
    }

    val aboutVoxaTitle = stringResource(R.string.settings_about_voxa)
    val customUiTitle = stringResource(R.string.settings_custom_ui)
    val playbackTitle = stringResource(R.string.settings_playback)
    val proMembershipTitle = stringResource(R.string.settings_pro_membership)
    val stopAllSounds = stringResource(R.string.settings_stop_all_sounds)
    val voxaTitle = stringResource(R.string.voxa)
    val settingsItems = remember {
        listOf(
            SettingsUiItem.Navigation(title = playbackTitle, icon = Icons.Default.PlayArrow, destination = PlaybackSettingsRoute, key = AppSettingKey.PLAYBACK),
            SettingsUiItem.Navigation(title = customUiTitle, icon = Icons.Default.PhotoAlbum, destination = GeneralUiSettingsRoute, key = AppSettingKey.GENERAL_UI),
            SettingsUiItem.Navigation(title = proMembershipTitle, icon = Icons.Default.Star, destination = ProSettingsRoute, key = AppSettingKey.PRO),
            SettingsUiItem.Navigation(
                title = aboutVoxaTitle,
                icon = Icons.Default.FileOpen,
                destination = AboutSettingsRoute,
                key = AppSettingKey.ABOUT
            ),
            SettingsUiItem.Action(title = stopAllSounds, icon = Icons.Default.Stop, key = AppSettingKey.STOP_ALL_SOUNDS)
        )
    }

    LaunchedEffect(editingSoundboardId) {
        toolbar.update(
            ToolbarState(
                title = voxaTitle,
                showMenu = true,
                onMenuClick = if (editingSoundboardId == null) ({ showSettingsSheet = true }) else null
            )
        )
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.showProRequired.collect { showBoardLimitDialog = true }
        }
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.showSetupError.collect {
                showMessage(setupFailedMessage)
            }
        }
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.showMessageToTheUser.collect { message ->
                when (message) {
                    SoundboardListScreenUserMessage.ADD_FAILED ->
                        showMessage(addSoundboardFailedMessage)
                    SoundboardListScreenUserMessage.DELETE_FAILED ->
                        showMessage(deleteSoundboardFailedMessage)
                    SoundboardListScreenUserMessage.RENAME_FAILED ->
                        showMessage(renameSoundboardFailedMessage)
                    SoundboardListScreenUserMessage.REORDER_FAILED ->
                        showMessage(reorderSoundboardsFailedMessage)
                    SoundboardListScreenUserMessage.RESTORE_FAILED ->
                        showMessage(restoreSoundsFailedMessage)
                }
            }
        }
    }

    if (showBoardLimitDialog) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_title_upgrade_to_pro),
            body = stringResource(R.string.dialog_msg_board_limit, FreeTierLimits.FREE_BOARD_LIMIT),
            confirmLabel = stringResource(R.string.btn_upgrade),
            onConfirm = {
                showBoardLimitDialog = false
                onNavigate(ProSettingsRoute)
            },
            onDismiss = { showBoardLimitDialog = false },
            dismissLabel = stringResource(R.string.btn_cancel)
        )
    }

    // Permission denied rationale — shown when the user taps Restore but denies the permission.
    if (showRestorePermissionRationale) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_title_permission_needed),
            body = stringResource(R.string.dialog_msg_restore_permission),
            confirmLabel = stringResource(R.string.btn_open_settings),
            onConfirm = {
                showRestorePermissionRationale = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onDismiss = { showRestorePermissionRationale = false },
            dismissLabel = stringResource(R.string.btn_cancel)
        )
    }

    // Missing sounds Dialog — shown on launch when the DB has sounds that aren't on disk.
    if (missingSoundCount > 0 && !dismissedRestoreDialog) {
        SoundboardListMissingSoundsDialog(
            missingSoundCount = missingSoundCount,
            isRestoring = isRestoring,
            onRestoreClick = onRestoreSoundsClick,
            onDismiss = { dismissedRestoreDialog = true }
        )
    }

    // Message shown after a restore scan completes.
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.restoreResult.collect { result ->
                if (result.restored == 0 && result.notFound == 0) return@collect
                val message = when {
                    result.notFound == 0 ->
                        context.resources.getQuantityString(
                            R.plurals.message_sounds_restored_all, result.restored, result.restored
                        )
                    result.restored == 0 ->
                        context.resources.getQuantityString(
                            R.plurals.message_sounds_not_found, result.notFound, result.notFound
                        )
                    else ->
                        context.resources.getQuantityString(
                            R.plurals.message_sounds_restored_partial, result.restored, result.restored, result.notFound
                        )
                }
                showMessage(message)
            }
        }
    }

    SoundboardListContents(
        soundboards,
        onSoundboardClick,
        onSoundboardMenuClick = { soundboard ->
            selectedSoundboardForMenu = soundboard
        },
        onAddNewSoundboardClick = viewModel::addSoundboard,
        editingSoundboardId = editingSoundboardId,
        updateSoundboardName = viewModel::updateSoundboardName,
        moveSound = viewModel::moveSoundboard,
        onReorderFinished = viewModel::persistSoundboardOrder,
        playingCountBySoundboard = playingCountBySoundboard
    )

    selectedSoundboardForMenu?.let { soundboard ->
        SoundboardListOptionsSheet(
            soundboard = soundboard,
            darkMode = generalUiSettings.darkMode,
            onRenameClick = {
                selectedSoundboardForMenu = null
                viewModel.setEditingSoundboardId(soundboard.id)
            },
            onDeleteClick = {
                selectedSoundboardForMenu = null
                soundboardPendingDelete = soundboard
            },
            onDismiss = { selectedSoundboardForMenu = null }
        )
    }

    soundboardPendingDelete?.let { soundboard ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_title_delete_soundboard),
            body = stringResource(R.string.dialog_msg_delete_soundboard, soundboard.name.ifBlank { stringResource(R.string.placeholder_untitled_soundboard) }),
            confirmLabel = stringResource(R.string.btn_delete),
            onConfirm = {
                viewModel.deleteSoundboard(soundboard.id)
                soundboardPendingDelete = null
            },
            onDismiss = { soundboardPendingDelete = null },
            dismissLabel = stringResource(R.string.btn_cancel)
        )
    }

    if (showSettingsSheet) {
        SoundboardListMenuModal(
            items = settingsItems,
            darkMode = generalUiSettings.darkMode,
            onItemClick = { destination ->
                showSettingsSheet = false
                onNavigate(destination)
            },
            onActionClick = { key ->
                when (key) {
                    AppSettingKey.STOP_ALL_SOUNDS -> {
                        viewModel.stopAllSounds()
                        showSettingsSheet = false
                    }
                    else -> Unit
                }
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

}

@Composable
fun SoundboardListContents(
    soundboards: List<Soundboard>,
    onSoundboardClick: (Long) -> Unit,
    onSoundboardMenuClick: (Soundboard) -> Unit,
    onAddNewSoundboardClick: () -> Unit,
    updateSoundboardName: (Long, String) -> Unit,
    moveSound: (Int, Int) -> Unit,
    onReorderFinished: () -> Unit,
    editingSoundboardId: Long? = null,
    playingCountBySoundboard: Map<Long, Int> = emptyMap()
) {
    val lazyListState = rememberLazyListState()

    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        moveSound(
            from.index,
            to.index
        )
    }

    VoxaPage {
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacerTiny)
        ) {
            items(
                items = soundboards,
                key = { it.id }
            ) { soundboard ->
                val isEditing = soundboard.id == editingSoundboardId
                if (isEditing) {
                    SoundboardListItemEditable(
                        initialText = soundboard.name,
                        onDone = { newName ->
                            updateSoundboardName(soundboard.id, newName)
                        })
                } else {
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = soundboard.id
                    ) {
                        Box {
                            SoundboardListItemExisting(
                                text = soundboard.name,
                                onBodyClick = { onSoundboardClick(soundboard.id) },
                                onSoundboardMenuClick = { onSoundboardMenuClick(soundboard) },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStopped = {
                                        onReorderFinished()
                                    }
                                ),
                                playingCount = playingCountBySoundboard[soundboard.id] ?: 0
                            )
                            if (editingSoundboardId != null) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitPointerEvent(PointerEventPass.Initial)
                                                        .changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
            if (editingSoundboardId == null) {
                item {
                    SoundboardListItemAddNew(onAddNewSoundboardClick)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SoundboardListContentsThreeBoardsPreview() {
    SoundboardListContentsPreviewHarness(
        soundboards = SoundboardListPreviewFixtures.threeSoundboards
    )
}

@Preview(showBackground = true)
@Composable
fun SoundboardListContentsPlayingSoundsPreview() {
    SoundboardListContentsPreviewHarness(
        soundboards = SoundboardListPreviewFixtures.twoSoundboards,
        playingCountBySoundboard = SoundboardListPreviewFixtures.playingCountsWithOverflow
    )
}

@Preview(showBackground = true)
@Composable
fun SoundboardListContentsEditingSoundboardPreview() {
    SoundboardListContentsPreviewHarness(
        soundboards = SoundboardListPreviewFixtures.threeSoundboards,
        editingSoundboardId = 1
    )
}

@Preview(showBackground = true)
@Composable
fun SoundboardListContentsNoSoundboardsPreview() {
    SoundboardListContentsPreviewHarness(soundboards = emptyList())
}

@Preview(showBackground = true)
@Composable
fun SoundboardListContentsLightModePreview() {
    SoundboardListContentsPreviewHarness(
        soundboards = SoundboardListPreviewFixtures.threeSoundboards,
        darkMode = false
    )
}

@Preview(showBackground = true)
@Composable
fun SoundboardListContentsNameTruncatedWithEllipsisPreview() {
    SoundboardListContentsPreviewHarness(
        soundboards = SoundboardListPreviewFixtures.singleSoundboardWithLongName,
        playingCountBySoundboard = SoundboardListPreviewFixtures.singleBoardPlayingCount
    )
}
