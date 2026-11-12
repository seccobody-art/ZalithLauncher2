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

package com.movtery.zalithlauncher.game.diagnostics

import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R
import java.io.File
import java.io.RandomAccessFile

/**
 * One problem found in a game log.
 * [detail] is raw technical text taken from the log, so it is never translated.
 */
data class CrashFinding(
    @StringRes val title: Int,
    @StringRes val advice: Int,
    val detail: String? = null
)

data class CrashAnalysis(
    val findings: List<CrashFinding>,
    /** The last "Caused by:" line, usually the real reason of the crash */
    val rootCause: String?
)

/**
 * Rule based crash log reader. It looks for well known error patterns and explains them.
 * Everything runs on the device, nothing is uploaded.
 */
object CrashAnalyzer {
    private const val MAX_DETAIL = 400

    private class Rule(
        @StringRes val title: Int,
        @StringRes val advice: Int,
        val pattern: Regex,
        val detail: (MatchResult, String) -> String? = { _, _ -> null }
    )

    private val BULLET_LINE = Regex("""^\s*-\s+\S.*""")
    private val JAR_NAME = Regex("""[\w.+\-]+\.jar""")
    private val MIXIN_MOD = Regex("""Mixin apply for mod (\S+) failed""")
    private val MIXIN_CONFIG = Regex("""([\w.\-]+)\.mixins?\.json""")

    private val RULES: List<Rule> = listOf(
        Rule(
            R.string.secco_crash_oom_title, R.string.secco_crash_oom_advice,
            Regex("""OutOfMemoryError|Java heap space|GC overhead limit exceeded""")
        ),
        Rule(
            R.string.secco_crash_java_title, R.string.secco_crash_java_advice,
            Regex("""class file version (\d+)\.\d+\), this version of the Java Runtime only recognizes class file versions up to (\d+)\.\d+|Unsupported class file major version (\d+)""")
        ) { m, _ ->
            //class file major version = Java version + 44
            val needed = m.groupValues[1].ifEmpty { m.groupValues[3] }.toIntOrNull()?.minus(44)
            val running = m.groupValues[2].toIntOrNull()?.minus(44)
            when {
                needed == null -> null
                running != null -> "Java $needed required (running: Java $running)"
                else -> "Java $needed required"
            }
        },
        Rule(
            R.string.secco_crash_deps_title, R.string.secco_crash_deps_advice,
            Regex("""Incompatible mods found|Mod resolution failed|Some of your mods are incompatible|Could not resolve (?:mod|dependency)|Error resolving mod dependencies""")
        ) { m, text -> bulletLinesAfter(text, m.range.first) },
        Rule(
            R.string.secco_crash_deps_title, R.string.secco_crash_deps_advice,
            Regex("""Missing or unsupported mandatory dependencies|DuplicateModsFoundException""")
        ) { m, text -> linesContainingAfter(text, m.range.first, "Mod ID") },
        Rule(
            R.string.secco_crash_mixin_title, R.string.secco_crash_mixin_advice,
            Regex("""Mixin apply (?:for mod \S+ )?failed|MixinApplyError|InvalidInjectionException|MixinTransformerError|Critical injection failure|MixinInitialisationError""")
        ) { _, text ->
            MIXIN_MOD.find(text)?.groupValues?.get(1)?.let { "Mod: $it" }
                ?: MIXIN_CONFIG.find(text)?.groupValues?.get(1)?.let { "Mixin config: $it" }
        },
        Rule(
            R.string.secco_crash_class_title, R.string.secco_crash_class_advice,
            Regex("""(?:NoClassDefFoundError|ClassNotFoundException|NoSuchMethodError|NoSuchFieldError): (\S+)""")
        ) { m, _ -> m.groupValues[1] },
        Rule(
            R.string.secco_crash_gl_title, R.string.secco_crash_gl_advice,
            Regex("""GLFW error|Failed to create OpenGL context|GLXBadContext|EGL_BAD_\w+|Could not create (?:an )?OpenGL|Failed to compile shader|Shader compilation failed""")
        ),
        Rule(
            R.string.secco_crash_native_title, R.string.secco_crash_native_advice,
            Regex("""SIGSEGV|SIGABRT|EXCEPTION_ACCESS_VIOLATION|A fatal error has been detected by the Java Runtime Environment|hs_err_pid""")
        ),
        Rule(
            R.string.secco_crash_disk_title, R.string.secco_crash_disk_advice,
            Regex("""No space left on device""")
        ),
        Rule(
            R.string.secco_crash_corrupt_title, R.string.secco_crash_corrupt_advice,
            Regex("""Could not find or load main class|ZipException|zip END header not found|invalid LOC header|Invalid or corrupt jarfile|Error opening zip file""")
        ) { m, text -> JAR_NAME.find(text, maxOf(0, m.range.first - 200))?.value },
        Rule(
            R.string.secco_crash_stack_title, R.string.secco_crash_stack_advice,
            Regex("""StackOverflowError""")
        )
    )

    private val SUSPECTED = Regex("""Suspected Mods?:\s*(.+)""")
    private val CAUSED_BY = Regex("""^\s*Caused by:\s*(.+)""", RegexOption.MULTILINE)

    fun analyze(text: String): CrashAnalysis {
        val findings = RULES.mapNotNull { rule ->
            rule.pattern.find(text)?.let { match ->
                CrashFinding(rule.title, rule.advice, rule.detail(match, text)?.take(MAX_DETAIL))
            }
        }.toMutableList()

        SUSPECTED.find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("None", ignoreCase = true) }
            ?.let {
                findings += CrashFinding(
                    R.string.secco_crash_suspect_title,
                    R.string.secco_crash_suspect_advice,
                    it.take(MAX_DETAIL)
                )
            }

        val root = CAUSED_BY.findAll(text).lastOrNull()?.groupValues?.get(1)?.trim()?.take(MAX_DETAIL)
        return CrashAnalysis(findings, root)
    }

    /** Reads the end of a (possibly huge) log file */
    fun readTail(file: File, maxBytes: Long = 800_000L): String {
        if (!file.isFile) return ""
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val start = maxOf(0L, length - maxBytes)
            raf.seek(start)
            val buffer = ByteArray((length - start).toInt())
            raf.readFully(buffer)
            return String(buffer, Charsets.UTF_8)
        }
    }

    private fun bulletLinesAfter(text: String, from: Int, max: Int = 6): String? =
        text.substring(from, minOf(text.length, from + 2500))
            .lineSequence()
            .filter { BULLET_LINE.matches(it) }
            .map { it.trim() }
            .take(max)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n")

    private fun linesContainingAfter(text: String, from: Int, needle: String, max: Int = 6): String? =
        text.substring(from, minOf(text.length, from + 2500))
            .lineSequence()
            .filter { it.contains(needle) }
            .map { it.trim() }
            .take(max)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
}
