/*
 * Secco Launcher
 * Derived from Zalith Launcher 2 (Copyright (C) 2025-2026 MovTery and contributors)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content.versions

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.CardTitleLayout
import com.movtery.zalithlauncher.ui.components.ScalingLabel
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.components.animated
import com.movtery.zalithlauncher.ui.components.rememberAnimatedContext
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.ImportMultipleFileButton
import com.movtery.zalithlauncher.ui.screens.content.elements.rememberMultipleUriImportTaskBuilder
import com.movtery.zalithlauncher.ui.screens.content.versions.layouts.VersionChunkBackground
import com.movtery.zalithlauncher.ui.theme.itemColor
import com.movtery.zalithlauncher.ui.theme.onItemColor
import com.movtery.zalithlauncher.utils.file.formatFileSize
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

private const val DISABLED_SUFFIX = ".disabled"

/**
 * One datapack inside a world's `datapacks` folder.
 * A `.zip.disabled` file is ignored by Minecraft, which is how a pack is switched off.
 */
private data class DatapackInfo(
    val file: File,
    val displayName: String,
    val enabled: Boolean,
    val isFolder: Boolean,
    val size: Long
)

private fun loadWorlds(savesDir: File): List<File> =
    savesDir.listFiles()
        ?.filter { it.isDirectory && File(it, "level.dat").exists() }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

private fun loadDatapacks(datapacksDir: File): List<DatapackInfo> =
    datapacksDir.listFiles()?.mapNotNull { f ->
        when {
            f.isFile && f.name.endsWith(".zip", ignoreCase = true) ->
                DatapackInfo(f, f.name.dropLast(".zip".length), true, false, f.length())
            f.isFile && f.name.endsWith(".zip$DISABLED_SUFFIX", ignoreCase = true) ->
                DatapackInfo(f, f.name.dropLast(".zip$DISABLED_SUFFIX".length), false, false, f.length())
            f.isDirectory && File(f, "pack.mcmeta").exists() ->
                DatapackInfo(f, f.name, true, true, FileUtils.sizeOfDirectory(f))
            else -> null
        }
    }?.sortedBy { it.displayName.lowercase() } ?: emptyList()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatapacksManagerScreen(
    mainScreenKey: TitledNavKey?,
    versionsScreenKey: TitledNavKey?,
    version: Version,
    backToMainScreen: () -> Unit,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    if (!version.isValid()) {
        backToMainScreen()
        return
    }

    val savesDir = remember(version) {
        VersionFolders.SAVES.getDir(version.getGameDir())
    }

    BaseScreen(
        levels1 = listOf(
            Pair(NestedNavKey.VersionSettings::class.java, mainScreenKey)
        ),
        Triple(NormalNavKey.Versions.DatapacksManager, versionsScreenKey, false),
    ) { isVisible ->
        val ctx = rememberAnimatedContext(isVisible = isVisible)
        val scope = rememberCoroutineScope()

        var worlds by remember { mutableStateOf<List<File>>(emptyList()) }
        var selectedWorld by remember { mutableStateOf<File?>(null) }
        var packs by remember { mutableStateOf<List<DatapackInfo>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var pendingDelete by remember { mutableStateOf<DatapackInfo?>(null) }

        fun refresh() {
            scope.launch {
                loading = true
                withContext(Dispatchers.IO) {
                    val foundWorlds = loadWorlds(savesDir)
                    val current = selectedWorld?.takeIf { foundWorlds.contains(it) } ?: foundWorlds.firstOrNull()
                    val foundPacks = current?.let { loadDatapacks(File(it, "datapacks")) } ?: emptyList()
                    worlds = foundWorlds
                    selectedWorld = current
                    packs = foundPacks
                }
                loading = false
            }
        }

        fun selectWorld(world: File) {
            selectedWorld = world
            refresh()
        }

        fun toggle(info: DatapackInfo) {
            scope.launch(Dispatchers.IO) {
                val f = info.file
                val target = if (info.enabled) {
                    File(f.parentFile, f.name + DISABLED_SUFFIX)
                } else {
                    File(f.parentFile, f.name.removeSuffix(DISABLED_SUFFIX))
                }
                if (!target.exists()) f.renameTo(target)
                refresh()
            }
        }

        LaunchedEffect(version) { refresh() }

        pendingDelete?.let { info ->
            SimpleAlertDialog(
                title = stringResource(R.string.generic_warning),
                text = stringResource(R.string.datapack_manage_delete_warning, info.displayName),
                onDismiss = { pendingDelete = null },
                onConfirm = {
                    pendingDelete = null
                    scope.launch(Dispatchers.IO) {
                        FileUtils.deleteQuietly(info.file)
                        refresh()
                    }
                }
            )
        }

        VersionChunkBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(all = 12.dp)
                .animated(ctx),
            paddingValues = PaddingValues()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CardTitleLayout(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            worlds.forEach { world ->
                                FilterChip(
                                    selected = world == selectedWorld,
                                    onClick = { selectWorld(world) },
                                    label = {
                                        Text(
                                            text = world.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }

                        selectedWorld?.let { world ->
                            val datapacksDir = File(world, "datapacks")
                            val taskBuilder = rememberMultipleUriImportTaskBuilder(
                                id = "ContentManager.Datapacks.Import.${world.name}",
                                targetDir = datapacksDir,
                                checkExtension = listOf("zip"),
                                submitError = submitError,
                                onImported = { refresh() }
                            )
                            ImportMultipleFileButton(
                                extension = "zip",
                                progressUris = { uris ->
                                    datapacksDir.mkdirs()
                                    TaskSystem.submitTask(taskBuilder(uris))
                                }
                            )
                        }

                        IconButton(onClick = { refresh() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = stringResource(R.string.generic_refresh)
                            )
                        }
                    }
                }

                when {
                    loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                    worlds.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScalingLabel(
                                modifier = Modifier.align(Alignment.Center),
                                text = stringResource(R.string.datapack_manage_no_worlds)
                            )
                        }
                    }
                    packs.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScalingLabel(
                                modifier = Modifier.align(Alignment.Center),
                                text = stringResource(R.string.datapack_manage_no_packs)
                            )
                        }
                    }
                    else -> {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .nonInteractiveScrollbar(
                                    state = listState.scrollIndicatorState!!,
                                    orientation = Orientation.Vertical,
                                ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            state = listState,
                        ) {
                            items(packs, key = { it.file.absolutePath }) { info ->
                                DatapackItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    info = info,
                                    onToggle = { toggle(info) },
                                    onDelete = { pendingDelete = info }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DatapackItem(
    modifier: Modifier = Modifier,
    info: DatapackInfo,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = itemColor(),
        contentColor = onItemColor()
    ) {
        Row(
            modifier = Modifier.padding(all = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = buildString {
                        append(stringResource(R.string.generic_file_size, formatFileSize(info.size)))
                        if (!info.enabled) {
                            append(" · ")
                            append(stringResource(R.string.datapack_manage_disabled))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            //Only zip packs can be switched off; a folder pack with pack.mcmeta is always loaded
            if (!info.isFolder) {
                Switch(
                    checked = info.enabled,
                    onCheckedChange = { onToggle() }
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_outlined),
                    contentDescription = stringResource(R.string.generic_delete)
                )
            }
        }
    }
}
