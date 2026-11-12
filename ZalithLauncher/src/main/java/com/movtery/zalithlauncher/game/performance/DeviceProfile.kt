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

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.view.Display

enum class GpuFamily { ADRENO, MALI, XCLIPSE, POWERVR, OTHER }

/**
 * A snapshot of the hardware that matters for picking game settings.
 */
data class DeviceProfile(
    val totalRamMb: Int,
    val cpuCores: Int,
    val socName: String,
    val gpuVendor: String?,
    val gpuRenderer: String?,
    val gpuFamily: GpuFamily,
    /** Adreno 740 -> 740, Mali-G715 -> 715, unknown -> null */
    val gpuModelNumber: Int?,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val refreshRate: Float
)

object DeviceProfiler {
    /**
     * Reads the device profile. Opens a tiny EGL context to read the GPU strings,
     * so call it off the main thread.
     */
    fun read(context: Context): DeviceProfile {
        val memoryInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memoryInfo)
        val totalRamMb = (memoryInfo.totalMem / (1024L * 1024L)).toInt()

        val metrics = context.resources.displayMetrics
        val refreshRate = runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
        }.getOrNull() ?: 60f

        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                .filter { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
                .joinToString(" ")
        } else ""

        val (vendor, renderer) = readGlStrings()
        val lower = renderer?.lowercase().orEmpty()
        val family = when {
            "adreno" in lower -> GpuFamily.ADRENO
            "mali" in lower -> GpuFamily.MALI
            "xclipse" in lower -> GpuFamily.XCLIPSE
            "powervr" in lower -> GpuFamily.POWERVR
            else -> GpuFamily.OTHER
        }
        val modelNumber = when (family) {
            GpuFamily.ADRENO -> Regex("""adreno[^0-9]*(\d{3})""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            GpuFamily.MALI -> Regex("""mali-?g(\d+)""").find(lower)?.groupValues?.get(1)?.toIntOrNull()
            else -> null
        }

        return DeviceProfile(
            totalRamMb = totalRamMb,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            socName = soc.ifBlank { Build.HARDWARE.orEmpty() },
            gpuVendor = vendor,
            gpuRenderer = renderer,
            gpuFamily = family,
            gpuModelNumber = modelNumber,
            screenWidthPx = maxOf(metrics.widthPixels, metrics.heightPixels),
            screenHeightPx = minOf(metrics.widthPixels, metrics.heightPixels),
            refreshRate = refreshRate
        )
    }

    /**
     * @return (GL_VENDOR, GL_RENDERER), or (null, null) when no EGL context could be created
     */
    private fun readGlStrings(): Pair<String?, String?> {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null to null
        if (!EGL14.eglInitialize(display, null, 0, null, 0)) return null to null

        try {
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            val chosen = EGL14.eglChooseConfig(
                display,
                intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE),
                0, configs, 0, 1, numConfigs, 0
            )
            val config = configs[0]
            if (!chosen || numConfigs[0] == 0 || config == null) return null to null

            val context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            if (context == EGL14.EGL_NO_CONTEXT) return null to null

            try {
                if (!EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context)) {
                    return null to null
                }
                return GLES20.glGetString(GLES20.GL_VENDOR) to GLES20.glGetString(GLES20.GL_RENDERER)
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }
}
