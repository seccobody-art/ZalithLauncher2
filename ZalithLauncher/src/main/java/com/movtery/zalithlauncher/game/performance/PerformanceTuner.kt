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

package com.movtery.zalithlauncher.game.performance

import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.game.renderer.renderers.GL4ESRenderer
import com.movtery.zalithlauncher.game.renderer.renderers.MobileGluesRenderer
import com.movtery.zalithlauncher.game.renderer.renderers.NGGL4ESRenderer
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.utils.isBiggerVer
import com.movtery.zalithlauncher.game.version.installed.utils.isLowerVer
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.ResolutionRule
import com.movtery.zalithlauncher.utils.logging.Logger

private const val TAG = "PerformanceTuner"

enum class PerformanceTier { LOW, MID, HIGH, FLAGSHIP }

enum class PerformanceGoal { BALANCED, MAX_FPS }

/**
 * The settings the tuner wants to apply. A null [rendererId] keeps the current renderer.
 */
data class PerformancePlan(
    val tier: PerformanceTier,
    val goal: PerformanceGoal,
    val rendererId: String?,
    val ramMb: Int,
    val resolutionPercent: Int,
    val sustainedPerformance: Boolean,
    val requestMaxRefreshRate: Boolean
)

/**
 * Rule based tuner: it classifies the device from its RAM, CPU and GPU, then derives
 * renderer, memory and resolution settings. It runs fully on-device.
 */
object PerformanceTuner {
    fun classify(profile: DeviceProfile): PerformanceTier {
        //reported total memory is a bit below the marketed size (an 8 GB phone shows ~7.5 GB)
        var score = when {
            profile.totalRamMb < 3300 -> 0
            profile.totalRamMb < 6500 -> 1
            profile.totalRamMb < 10500 -> 2
            else -> 3
        }

        val n = profile.gpuModelNumber
        score += when (profile.gpuFamily) {
            GpuFamily.ADRENO -> when {
                n == null -> 0
                n >= 730 -> 1
                n >= 640 -> 0
                else -> -1
            }
            GpuFamily.MALI -> when {
                n == null -> 0
                n >= 715 -> 1
                n >= 610 || n in 76..78 -> 0
                else -> -1
            }
            GpuFamily.POWERVR -> -1
            GpuFamily.XCLIPSE, GpuFamily.OTHER -> 0
        }
        if (profile.cpuCores <= 4) score -= 1

        return PerformanceTier.entries[score.coerceIn(0, PerformanceTier.entries.lastIndex)]
    }

    fun recommend(
        profile: DeviceProfile,
        goal: PerformanceGoal,
        minecraftVersion: String?,
        renderers: List<RendererInterface>
    ): PerformancePlan {
        val tier = classify(profile)

        //Leave plenty of room for Android itself; the game needs far less than the whole RAM
        val ramMb = ((profile.totalRamMb * 0.40).toInt().coerceIn(768, 5120) / 256) * 256

        var resolution = when (tier) {
            PerformanceTier.LOW -> 70
            PerformanceTier.MID -> 80
            PerformanceTier.HIGH -> 95
            PerformanceTier.FLAGSHIP -> 100
        }
        if (goal == PerformanceGoal.MAX_FPS) resolution -= 15
        //Very dense screens cost a lot of fill rate on weaker GPUs
        if (profile.screenWidthPx.toLong() * profile.screenHeightPx > 3_000_000L && tier <= PerformanceTier.MID) {
            resolution -= 10
        }
        resolution = (resolution.coerceIn(50, 100) / 5.0).let { Math.round(it).toInt() * 5 }

        val preferred = when {
            goal == PerformanceGoal.MAX_FPS -> listOf(
                GL4ESRenderer.getUniqueIdentifier(),
                NGGL4ESRenderer.getUniqueIdentifier(),
                MobileGluesRenderer.getUniqueIdentifier()
            )
            tier >= PerformanceTier.HIGH -> listOf(
                MobileGluesRenderer.getUniqueIdentifier(),
                NGGL4ESRenderer.getUniqueIdentifier(),
                GL4ESRenderer.getUniqueIdentifier()
            )
            else -> listOf(
                NGGL4ESRenderer.getUniqueIdentifier(),
                GL4ESRenderer.getUniqueIdentifier(),
                MobileGluesRenderer.getUniqueIdentifier()
            )
        }
        val rendererId = preferred.firstOrNull { id ->
            renderers.firstOrNull { it.getUniqueIdentifier() == id }
                ?.let { supportsVersion(it, minecraftVersion) } == true
        }

        return PerformancePlan(
            tier = tier,
            goal = goal,
            rendererId = rendererId,
            ramMb = ramMb,
            resolutionPercent = resolution,
            sustainedPerformance = !(tier == PerformanceTier.FLAGSHIP && goal == PerformanceGoal.MAX_FPS),
            requestMaxRefreshRate = true
        )
    }

    private fun supportsVersion(renderer: RendererInterface, minecraftVersion: String?): Boolean {
        if (minecraftVersion == null) return true
        val minOk = renderer.getMinMCVersion()?.let { !minecraftVersion.isLowerVer(it) } ?: true
        val maxOk = renderer.getMaxMCVersion()?.let { !minecraftVersion.isBiggerVer(it) } ?: true
        return minOk && maxOk
    }

    /**
     * Saves the plan into the global settings. Per-version overrides of the renderer and memory
     * on [version] are cleared, otherwise they would win over the tuned global values.
     */
    fun apply(plan: PerformancePlan, version: Version?) {
        plan.rendererId?.let { AllSettings.renderer.save(it) }
        AllSettings.ramAllocation.save(plan.ramMb)
        AllSettings.resolutionRule.save(ResolutionRule.PERCENTAGE)
        AllSettings.resolutionRatio.save(plan.resolutionPercent)
        AllSettings.sustainedPerformance.save(plan.sustainedPerformance)
        AllSettings.requestMaxRefreshRate.save(plan.requestMaxRefreshRate)

        version?.getVersionConfig()?.let { config ->
            var changed = false
            if (config.renderer.isNotEmpty()) {
                config.renderer = ""
                changed = true
            }
            if (config.ramAllocation >= 0) {
                config.ramAllocation = -1
                changed = true
            }
            if (changed) {
                runCatching { config.saveWithThrowable() }
                    .onFailure { Logger.error(TAG, "Failed to clear version overrides", it) }
            }
        }
    }
}
