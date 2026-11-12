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

package com.movtery.zalithlauncher.ui.screens.content

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.filemanager.FileManagerLauncher
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.CrashAnalyzerDialog
import com.movtery.zalithlauncher.ui.components.PerformanceModeDialog
import com.movtery.zalithlauncher.ui.components.PixelText
import com.movtery.zalithlauncher.ui.components.WorldBackupDialog
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Brand purple used for the main buttons and the selected rail icon */
private val SeccoAccent = Color(0xFF7C5CE6)

/** Optional social links. A button is only shown when its link is not empty. */
private object SeccoLinks {
    const val DISCORD = ""
    const val WEBSITE = ""
}

/**
 * Everything the left icon rail can open. Features without a screen yet show a "coming soon" dialog.
 */
private enum class RailFeature(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    @StringRes val desc: Int
) {
    PROFILE(R.drawable.ic_person_filled, R.string.secco_rail_profile, R.string.secco_rail_profile_desc),
    HOME(R.drawable.ic_home_filled, R.string.secco_rail_home, R.string.secco_rail_home_desc),
    P2P(R.drawable.ic_group_filled, R.string.secco_rail_p2p, R.string.secco_rail_p2p_desc),
    CHAT(R.drawable.ic_chat_info, R.string.secco_rail_chat, R.string.secco_rail_chat_desc),
    VOICE(R.drawable.ic_mic_filled, R.string.secco_rail_voice, R.string.secco_rail_voice_desc),
    SCREEN(R.drawable.ic_screen_share_filled, R.string.secco_rail_screen, R.string.secco_rail_screen_desc),
    QUESTS(R.drawable.ic_trophy_filled, R.string.secco_rail_quests, R.string.secco_rail_quests_desc),
    BACKGROUND(R.drawable.ic_image_filled, R.string.secco_rail_background, R.string.secco_rail_background_desc),
    SKINS(R.drawable.ic_checkroom, R.string.secco_rail_skins, R.string.secco_rail_skins_desc),
    MODPACKS(R.drawable.ic_extension_outlined, R.string.secco_rail_modpacks, R.string.secco_rail_modpacks_desc),
    ADVANCED(R.drawable.ic_build_filled, R.string.secco_rail_advanced, R.string.secco_rail_advanced_desc),
    OPTIMIZE(R.drawable.ic_rocket_launch_filled, R.string.secco_rail_optimize, R.string.secco_rail_optimize_desc),
    CRASH(R.drawable.ic_bug_report_filled, R.string.secco_rail_crash, R.string.secco_rail_crash_desc),
    WEATHER(R.drawable.ic_cloud_filled, R.string.secco_rail_weather, R.string.secco_rail_weather_desc),
    BACKUP(R.drawable.ic_cloud_upload_filled, R.string.secco_rail_backup, R.string.secco_rail_backup_desc),
    ACCOUNTS(R.drawable.ic_key_filled, R.string.secco_rail_accounts, R.string.secco_rail_accounts_desc),
    MODSCAN(R.drawable.ic_security_filled, R.string.secco_rail_modscan, R.string.secco_rail_modscan_desc),
    STREAMER(R.drawable.ic_sensors_filled, R.string.secco_rail_streamer, R.string.secco_rail_streamer_desc),
    MODERATION(R.drawable.ic_gavel_filled, R.string.secco_rail_moderation, R.string.secco_rail_moderation_desc),
    HISTORY(R.drawable.ic_history_filled, R.string.secco_rail_history, R.string.secco_rail_history_desc),
    FILES(R.drawable.ic_folder_filled, R.string.secco_rail_files, R.string.secco_rail_files_desc),
    SETTINGS(R.drawable.ic_settings_filled, R.string.secco_rail_settings, R.string.secco_rail_settings_desc),
    SERVERS(R.drawable.ic_public, R.string.secco_rail_servers, R.string.secco_rail_servers_desc)
}

private val RAIL_GROUPS: List<List<RailFeature>> = listOf(
    listOf(RailFeature.PROFILE, RailFeature.HOME),
    listOf(RailFeature.P2P, RailFeature.CHAT, RailFeature.VOICE, RailFeature.SCREEN, RailFeature.QUESTS),
    listOf(
        RailFeature.BACKGROUND, RailFeature.SKINS, RailFeature.MODPACKS,
        RailFeature.ADVANCED, RailFeature.OPTIMIZE, RailFeature.CRASH
    ),
    listOf(
        RailFeature.WEATHER, RailFeature.BACKUP, RailFeature.ACCOUNTS, RailFeature.MODSCAN,
        RailFeature.STREAMER, RailFeature.MODERATION, RailFeature.HISTORY
    )
)

@Composable
fun SeccoHomeScreen(
    backStackViewModel: ScreenBackStackViewModel,
    navigateToVersions: (Version) -> Unit,
    onLaunchGame: (Version?) -> Unit
) {
    BaseScreen(
        screenKey = NormalNavKey.LauncherMain,
        currentKey = backStackViewModel.mainScreen.currentKey
    ) { _ ->
        val context = LocalContext.current
        val version by VersionsManager.currentVersion.collectAsStateWithLifecycle()
        val versions by VersionsManager.versions.collectAsStateWithLifecycle()
        val account by AccountsManager.currentAccountFlow.collectAsStateWithLifecycle()
        val tasks by TaskSystem.tasksFlow.collectAsStateWithLifecycle()
        val streamerMode = AllSettings.launcherStreamerMode.state

        var comingSoon by remember { mutableStateOf<RailFeature?>(null) }
        var showPerformance by remember { mutableStateOf(false) }
        var showCrash by remember { mutableStateOf(false) }
        var showBackup by remember { mutableStateOf(false) }
        var versionMenu by remember { mutableStateOf(false) }
        //Long press on a rail icon shows its name for a moment
        var hint by remember { mutableStateOf<RailFeature?>(null) }
        LaunchedEffect(hint) {
            if (hint != null) {
                delay(1600L)
                hint = null
            }
        }

        val validVersion = version?.takeIf { it.isValid() }

        fun toAccounts() {
            backStackViewModel.mainScreen.navigateTo(
                NormalNavKey.AccountManager(FirstLoginMenu.NONE)
            )
        }

        fun toSettings(page: NormalNavKey.Settings? = null) {
            backStackViewModel.mainScreen.removeAndNavigateTo(
                removes = backStackViewModel.clearBeforeNavKeys,
                screenKey = backStackViewModel.settingsScreen
            )
            page?.let { backStackViewModel.settingsScreen.navigateTo(it) }
        }

        fun toVersionManage() {
            backStackViewModel.mainScreen.removeAndNavigateTo(
                remove = NestedNavKey.VersionSettings::class,
                screenKey = NormalNavKey.VersionsManager
            )
        }

        fun onRail(feature: RailFeature) {
            when (feature) {
                RailFeature.HOME -> Unit
                RailFeature.PROFILE, RailFeature.SKINS, RailFeature.ACCOUNTS -> toAccounts()
                RailFeature.P2P -> backStackViewModel.mainScreen.removeAndNavigateTo(
                    removes = backStackViewModel.clearBeforeNavKeys,
                    screenKey = NormalNavKey.Multiplayer
                )
                RailFeature.BACKGROUND -> toSettings(NormalNavKey.Settings.Launcher)
                RailFeature.ADVANCED -> toSettings(NormalNavKey.Settings.Game)
                RailFeature.MODPACKS -> backStackViewModel.navigateToDownload()
                RailFeature.OPTIMIZE -> showPerformance = true
                RailFeature.CRASH -> showCrash = true
                RailFeature.BACKUP -> showBackup = true
                RailFeature.STREAMER -> AllSettings.launcherStreamerMode.save(!streamerMode)
                RailFeature.FILES -> {
                    val root = PathManager.DIR_FILES_EXTERNAL.absolutePath
                    FileManagerLauncher.launch(
                        context = context,
                        rootPath = root,
                        currentPath = root,
                        logsDir = PathManager.DIR_LAUNCHER_LOGS.absolutePath
                    )
                }
                RailFeature.SETTINGS -> toSettings()
                else -> comingSoon = feature
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            //Scrim: keeps the text readable on top of any custom background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xD9060410), Color(0x80060410)))
                    )
            )

            //Logo + name
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.img_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Secco",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            //Left icon rail with every feature
            SeccoRail(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 64.dp, bottom = 12.dp)
                    .fillMaxHeight(),
                streamerOn = streamerMode,
                onClick = ::onRail,
                onLongPress = { hint = it }
            )

            hint?.let { feature ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 116.dp),
                    shape = CircleShape,
                    color = Color(0xE6000000)
                ) {
                    Text(
                        text = stringResource(feature.label),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 128.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)
            ) {
                //Clock + profile, top right
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    PixelClock()
                    Spacer(modifier = Modifier.height(8.dp))
                    ProfileChip(
                        name = when {
                            account == null -> stringResource(R.string.secco_account_none)
                            streamerMode -> "••••••"
                            else -> account?.username.orEmpty()
                        },
                        onClick = ::toAccounts
                    )
                }

                //Running tasks, top center
                if (tasks.isNotEmpty()) {
                    TaskChip(
                        count = tasks.size,
                        modifier = Modifier.align(Alignment.TopCenter),
                        onClick = {
                            AllSettings.launcherTaskMenuExpanded.save(
                                !AllSettings.launcherTaskMenuExpanded.state
                            )
                        }
                    )
                }

                //Selected version, description and the two main buttons
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .widthIn(max = 520.dp)
                ) {
                    Box {
                        Surface(
                            onClick = { versionMenu = true },
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.10f)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SeccoAccent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.secco_selected_version,
                                        validVersion?.getVersionName() ?: "-"
                                    ),
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = versionMenu,
                            onDismissRequest = { versionMenu = false }
                        ) {
                            versions.filter { it.isValid() }.forEach { v ->
                                DropdownMenuItem(
                                    text = { Text(v.getVersionName()) },
                                    onClick = {
                                        versionMenu = false
                                        VersionsManager.saveVersion(v)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.secco_manage_versions)) },
                                onClick = {
                                    versionMenu = false
                                    toVersionManage()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = validVersion?.let { versionTitle(it) }
                            ?: stringResource(R.string.secco_no_version),
                        color = Color.White,
                        fontSize = 48.sp,
                        lineHeight = 52.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validVersion?.getVersionInfo()?.getInfoString()
                            ?: stringResource(R.string.secco_no_version_desc),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (validVersion != null) onLaunchGame(validVersion)
                                else backStackViewModel.navigateToDownload()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SeccoAccent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play_arrow_filled),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (validVersion != null) R.string.secco_play else R.string.secco_download_version
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                if (validVersion != null) navigateToVersions(validVersion) else toVersionManage()
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = SeccoAccent.copy(alpha = 0.45f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_filled),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.secco_configure),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                //Bottom left: server list
                InfoChip(
                    modifier = Modifier.align(Alignment.BottomStart),
                    icon = RailFeature.SERVERS.icon,
                    text = stringResource(RailFeature.SERVERS.label),
                    onClick = { comingSoon = RailFeature.SERVERS }
                )
            }
        }

        comingSoon?.let { feature ->
            AlertDialog(
                onDismissRequest = { comingSoon = null },
                title = { Text(stringResource(feature.label)) },
                text = {
                    Text(
                        stringResource(R.string.secco_coming_soon) + "\n\n" + stringResource(feature.desc)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { comingSoon = null }) {
                        Text(stringResource(R.string.generic_confirm))
                    }
                }
            )
        }

        if (showPerformance) {
            PerformanceModeDialog(
                version = version,
                onDismiss = { showPerformance = false }
            )
        }

        if (showCrash) {
            CrashAnalyzerDialog(
                version = version,
                onDismiss = { showCrash = false }
            )
        }

        if (showBackup) {
            WorldBackupDialog(
                version = version,
                onDismiss = { showBackup = false }
            )
        }
    }
}

/** "1.21.11 FABRIC" style title: Minecraft version plus the main mod loader */
private fun versionTitle(version: Version): String {
    val info = version.getVersionInfo()
    val mc = info?.minecraftVersion ?: version.getVersionName()
    val loader = info?.primaryLoader?.loader?.displayName?.takeIf { it.isNotBlank() }
    return listOfNotNull(mc, loader).joinToString(" ").uppercase()
}

@Composable
private fun SeccoRail(
    streamerOn: Boolean,
    onClick: (RailFeature) -> Unit,
    onLongPress: (RailFeature) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(96.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xB3000000)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RAIL_GROUPS.forEach { group ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        //Two icons per row, so all features fit on a phone sized screen
                        group.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                rowItems.forEach { feature ->
                                    RailButton(
                                        feature = feature,
                                        selected = feature == RailFeature.HOME ||
                                            (feature == RailFeature.STREAMER && streamerOn),
                                        onClick = onClick,
                                        onLongPress = onLongPress
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                RailButton(feature = RailFeature.FILES, selected = false, onClick = onClick, onLongPress = onLongPress)
                RailButton(feature = RailFeature.SETTINGS, selected = false, onClick = onClick, onLongPress = onLongPress)
            }
        }
    }
}

@Composable
private fun RailButton(
    feature: RailFeature,
    selected: Boolean,
    onClick: (RailFeature) -> Unit,
    onLongPress: (RailFeature) -> Unit
) {
    val label = stringResource(feature.label)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) SeccoAccent else Color.Transparent)
            .semantics { role = Role.Button }
            .pointerInput(feature) {
                detectTapGestures(
                    onTap = { onClick(feature) },
                    onLongPress = { onLongPress(feature) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(feature.icon),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PixelClock() {
    var time by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTimeText()
            delay(10_000L)
        }
    }
    PixelText(text = time, pixel = 3.dp)
}

private fun currentTimeText(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun ProfileChip(
    name: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.secco_profile, name),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SeccoAccent.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_person_filled),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TaskChip(
    count: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.secco_tasks_running, count),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun InfoChip(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
