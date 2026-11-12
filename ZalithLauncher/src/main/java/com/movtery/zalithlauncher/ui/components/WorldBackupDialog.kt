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

package com.movtery.zalithlauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.backup.BackupInfo
import com.movtery.zalithlauncher.game.backup.WorldBackup
import com.movtery.zalithlauncher.game.backup.WorldInfo
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.utils.file.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Lists the worlds of [version] and their .zip backups. Restoring never overwrites a world.
 */
@Composable
fun WorldBackupDialog(
    version: Version?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val valid = version?.takeIf { it.isValid() }

    var worlds by remember { mutableStateOf<List<WorldInfo>>(emptyList()) }
    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<BackupInfo?>(null) }

    fun refresh() {
        val v = valid ?: return
        scope.launch {
            loading = true
            withContext(Dispatchers.IO) {
                worlds = WorldBackup.listWorlds(v)
                backups = WorldBackup.listBackups(v)
            }
            loading = false
        }
    }

    fun runOperation(startMessage: String?, work: () -> String) {
        scope.launch {
            busy = true
            startMessage?.let { status = it }
            status = withContext(Dispatchers.IO) {
                runCatching(work).getOrElse {
                    context.getString(
                        R.string.secco_backup_failed,
                        it.message ?: it.javaClass.simpleName
                    )
                }
            }
            busy = false
            refresh()
        }
    }

    LaunchedEffect(valid) {
        if (valid != null) refresh() else loading = false
    }

    pendingDelete?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(backup.file.name) },
            text = { Text(stringResource(R.string.secco_backup_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    runOperation(null) {
                        WorldBackup.delete(backup)
                        ""
                    }
                }) { Text(stringResource(R.string.secco_backup_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.generic_cancel))
                }
            }
        )
    }

    SeccoDialog(
        title = stringResource(R.string.secco_backup_title),
        onDismiss = onDismiss
    ) {
        if (valid == null) {
            Text(stringResource(R.string.secco_select_version_first))
        } else if (loading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.secco_backup_worlds))
                    if (worlds.isEmpty()) {
                        Text(stringResource(R.string.secco_backup_no_worlds), modifier = Modifier.alpha(0.7f))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(worlds, key = { it.dir.absolutePath }) { world ->
                            ItemRow(
                                title = world.name,
                                subtitle = formatFileSize(world.sizeBytes)
                            ) {
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        runOperation(
                                            context.getString(R.string.secco_backup_creating, world.name)
                                        ) {
                                            val file = WorldBackup.backup(valid, world)
                                            context.getString(R.string.secco_backup_done, file.name)
                                        }
                                    }
                                ) { Text(stringResource(R.string.secco_backup_create)) }
                            }
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.secco_backup_backups))
                    if (backups.isEmpty()) {
                        Text(stringResource(R.string.secco_backup_no_backups), modifier = Modifier.alpha(0.7f))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(backups, key = { it.file.absolutePath }) { backup ->
                            ItemRow(
                                title = backup.worldName,
                                subtitle = DateFormat.getDateTimeInstance().format(Date(backup.createdAt)) +
                                    " · " + formatFileSize(backup.sizeBytes)
                            ) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        runOperation(null) {
                                            val dir = WorldBackup.restore(valid, backup)
                                            context.getString(R.string.secco_backup_restored, dir.name)
                                        }
                                    }
                                ) { Text(stringResource(R.string.secco_backup_restore)) }
                                IconButton(
                                    enabled = !busy,
                                    onClick = { pendingDelete = backup }
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete_outlined),
                                        contentDescription = stringResource(R.string.secco_backup_delete)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 6.dp))
            }
            status?.takeIf { it.isNotEmpty() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Text(
                text = stringResource(R.string.secco_backup_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .alpha(0.6f)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun ItemRow(
    title: String,
    subtitle: String,
    actions: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            actions()
        }
    }
}
