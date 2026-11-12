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

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.performance.DeviceProfile
import com.movtery.zalithlauncher.game.performance.DeviceProfiler
import com.movtery.zalithlauncher.game.performance.PerformanceGoal
import com.movtery.zalithlauncher.game.performance.PerformanceTier
import com.movtery.zalithlauncher.game.performance.PerformanceTuner
import com.movtery.zalithlauncher.game.renderer.Renderers
import com.movtery.zalithlauncher.game.version.installed.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects the device, proposes the best settings for it and applies them on confirmation.
 */
@Composable
fun PerformanceModeDialog(
    version: Version?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<DeviceProfile?>(null) }
    var goal by remember { mutableStateOf(PerformanceGoal.BALANCED) }

    LaunchedEffect(Unit) {
        profile = withContext(Dispatchers.Default) { DeviceProfiler.read(context) }
    }

    val minecraftVersion = version?.getVersionInfo()?.minecraftVersion
    val plan = remember(profile, goal, minecraftVersion) {
        profile?.let {
            PerformanceTuner.recommend(it, goal, minecraftVersion, Renderers.getRenderers())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.performance_mode_title)) },
        text = {
            val p = profile
            if (p == null || plan == null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.performance_mode_device,
                            p.socName.ifBlank { "?" },
                            p.gpuRenderer ?: "?"
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = stringResource(
                            R.string.performance_mode_specs,
                            p.totalRamMb / 1024.0,
                            p.cpuCores
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.performance_mode_tier, tierName(plan.tier)),
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = goal == PerformanceGoal.BALANCED,
                            onClick = { goal = PerformanceGoal.BALANCED },
                            label = { Text(stringResource(R.string.performance_goal_balanced)) }
                        )
                        FilterChip(
                            selected = goal == PerformanceGoal.MAX_FPS,
                            onClick = { goal = PerformanceGoal.MAX_FPS },
                            label = { Text(stringResource(R.string.performance_goal_max_fps)) }
                        )
                    }

                    val rendererName = plan.rendererId?.let { id ->
                        Renderers.getRenderers().firstOrNull { it.getUniqueIdentifier() == id }?.getRendererName()
                    } ?: stringResource(R.string.performance_plan_renderer_keep)
                    val onOff = stringResource(
                        if (plan.sustainedPerformance) R.string.performance_on else R.string.performance_off
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.performance_plan_renderer, rendererName))
                        Text(stringResource(R.string.performance_plan_ram, plan.ramMb))
                        Text(stringResource(R.string.performance_plan_resolution, plan.resolutionPercent))
                        Text(stringResource(R.string.performance_plan_sustained, onOff))
                    }

                    Text(
                        modifier = Modifier.alpha(0.7f),
                        text = stringResource(R.string.performance_mode_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = plan != null,
                onClick = {
                    plan?.let {
                        PerformanceTuner.apply(it, version)
                        Toast.makeText(
                            context,
                            context.getString(R.string.performance_mode_applied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.performance_mode_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.generic_cancel))
            }
        }
    )
}

@Composable
private fun tierName(tier: PerformanceTier): String = stringResource(
    when (tier) {
        PerformanceTier.LOW -> R.string.performance_tier_low
        PerformanceTier.MID -> R.string.performance_tier_mid
        PerformanceTier.HIGH -> R.string.performance_tier_high
        PerformanceTier.FLAGSHIP -> R.string.performance_tier_flagship
    }
)
