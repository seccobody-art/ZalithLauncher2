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

package com.movtery.zalithlauncher.game.backup

import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class WorldInfo(val dir: File, val name: String, val sizeBytes: Long)

data class BackupInfo(val file: File, val worldName: String, val createdAt: Long, val sizeBytes: Long)

/**
 * Local .zip backups of singleplayer worlds. Backups go to the game's own `backups` folder
 * with the same file name style Minecraft uses.
 */
object WorldBackup {
    private val STAMP_PREFIX = Regex("""^\d{4}-\d{2}-\d{2}_\d{2}\.\d{2}\.\d{2}_""")

    fun backupsDir(version: Version): File = File(version.getGameDir(), "backups")

    fun savesDir(version: Version): File = VersionFolders.SAVES.getDir(version.getGameDir())

    fun listWorlds(version: Version): List<WorldInfo> =
        savesDir(version).listFiles()
            ?.filter { it.isDirectory && File(it, "level.dat").exists() }
            ?.sortedBy { it.name.lowercase() }
            ?.map { WorldInfo(it, it.name, FileUtils.sizeOfDirectory(it)) }
            ?: emptyList()

    fun listBackups(version: Version): List<BackupInfo> =
        backupsDir(version).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                BackupInfo(
                    file = it,
                    worldName = it.name.removeSuffix(".zip").replaceFirst(STAMP_PREFIX, ""),
                    createdAt = it.lastModified(),
                    sizeBytes = it.length()
                )
            }
            ?: emptyList()

    /**
     * Zips [world] into the backups folder. The archive is written under a temporary name and
     * only renamed when it is complete, so an interrupted backup never looks like a good one.
     */
    fun backup(version: Version, world: WorldInfo): File {
        val dir = backupsDir(version).also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.US).format(Date())
        val target = File(dir, "${stamp}_${world.name}.zip")
        val temp = File(dir, target.name + ".part")
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                world.dir.walkTopDown()
                    .filter { it.isFile && it.name != "session.lock" }
                    .forEach { file ->
                        val relative = file.relativeTo(world.dir).path.replace(File.separatorChar, '/')
                        zip.putNextEntry(ZipEntry("${world.name}/$relative"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
            if (!temp.renameTo(target)) throw IOException("Could not finish ${target.name}")
        } finally {
            if (temp.exists()) temp.delete()
        }
        return target
    }

    /**
     * Extracts [backup] next to the existing worlds. It never overwrites a world: when the name
     * is taken, the copy gets a "_restored" suffix.
     * @return the folder of the restored world
     */
    fun restore(version: Version, backup: BackupInfo): File {
        val saves = savesDir(version).also { it.mkdirs() }
        ZipFile(backup.file).use { zip ->
            val names = zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
            val roots = names.map { it.substringBefore('/') }.toSet()
            val singleRoot = roots.size == 1 && names.all { it.contains('/') }
            val rootName = if (singleRoot) roots.first() else backup.worldName

            val destination = uniqueDir(saves, rootName)
            val destinationPath = destination.canonicalPath + File.separator
            try {
                zip.entries().asSequence().filter { !it.isDirectory }.forEach { entry ->
                    val relative = if (singleRoot) entry.name.substringAfter('/') else entry.name
                    val out = File(destination, relative)
                    //Zip slip guard: never write outside the world folder
                    if (!out.canonicalPath.startsWith(destinationPath)) {
                        throw IOException("Illegal entry: ${entry.name}")
                    }
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().buffered().use { input.copyTo(it) }
                    }
                }
            } catch (e: Exception) {
                FileUtils.deleteQuietly(destination)
                throw e
            }
            return destination
        }
    }

    fun delete(backup: BackupInfo): Boolean = backup.file.delete()

    private fun uniqueDir(parent: File, name: String): File {
        var candidate = File(parent, name)
        if (!candidate.exists()) return candidate
        candidate = File(parent, "${name}_restored")
        var index = 2
        while (candidate.exists()) {
            candidate = File(parent, "${name}_restored_$index")
            index++
        }
        return candidate
    }
}
