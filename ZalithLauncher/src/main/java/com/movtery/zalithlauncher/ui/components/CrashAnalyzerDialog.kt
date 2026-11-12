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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.diagnostics.CrashAnalysis
import com.movtery.zalithlauncher.game.diagnostics.CrashAnalyzer
import com.movtery.zalithlauncher.game.diagnostics.CrashFinding
import com.movtery.zalithlauncher.game.version.installed.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private sealed interface CrashUiState {
    data object Loading : CrashUiState
    data object NoVersion : CrashUiState
    data object NoLog : CrashUiState
    data class Result(val source: String, val analysis: CrashAnalysis) : CrashUiState
}

/**
 * Reads the newest game log (and crash report) of [version] and explains what went wrong.
 */
@Composable
fun CrashAnalyzerDialog(
    version: Version?,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf<CrashUiState>(CrashUiState.Loading) }

    LaunchedEffect(version) {
        state = withContext(Dispatchers.IO) { loadAnalysis(version) }
    }

    SeccoDialog(
        title = stringResource(R.string.secco_crash_title),
        onDismiss = onDismiss
    ) {
        when (val s = state) {
            CrashUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            CrashUiState.NoVersion -> Text(stringResource(R.string.secco_select_version_first))
            CrashUiState.NoLog -> Text(stringResource(R.string.secco_crash_no_log))
            is CrashUiState.Result -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = stringResource(R.string.secco_crash_source, s.source),
                    style = MaterialTheme.typography.bodySmall
                )
                if (s.analysis.findings.isEmpty()) {
                    Text(stringResource(R.string.secco_crash_nothing))
                }
                s.analysis.findings.forEach { FindingCard(it) }
                s.analysis.rootCause?.let { cause ->
                    Text(
                        text = stringResource(R.string.secco_crash_root),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = cause,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.alpha(0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FindingCard(finding: CrashFinding) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(finding.title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(finding.advice),
                style = MaterialTheme.typography.bodyMedium
            )
            finding.detail?.let {
                Text(
                    text = it,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.alpha(0.85f)
                )
            }
        }
    }
}

private fun loadAnalysis(version: Version?): CrashUiState {
    if (version == null || !version.isValid()) return CrashUiState.NoVersion

    val log = version.getLatestLog()
    val newestCrash = File(version.getGameDir(), "crash-reports")
        .listFiles { f -> f.isFile && f.name.endsWith(".txt") }
        ?.maxByOrNull { it.lastModified() }
    //Only use the crash report when it belongs to the same run as the log
    val crash = newestCrash?.takeIf { !log.exists() || it.lastModified() >= log.lastModified() - 60_000L }
    if (!log.exists() && crash == null) return CrashUiState.NoLog

    val text = buildString {
        crash?.let {
            append(CrashAnalyzer.readTail(it))
            append('\n')
        }
        append(CrashAnalyzer.readTail(log))
    }
    val shown = crash ?: log
    val source = "${shown.name} (${DateFormat.getDateTimeInstance().format(Date(shown.lastModified()))})"
    return CrashUiState.Result(source, CrashAnalyzer.analyze(text))
}
