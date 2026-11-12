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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Digits and a few symbols in a 5x7 pixel font that looks like the Minecraft default font */
private val PIXEL_GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
    '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
    '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
    '3' to listOf(".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."),
    '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
    '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
    '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
    '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
    '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
    '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
    ':' to listOf(" ", "#", " ", " ", " ", "#", " "),
    ' ' to listOf("   ", "   ", "   ", "   ", "   ", "   ", "   ")
)

/**
 * Text drawn with blocky pixel glyphs and a one pixel drop shadow.
 * Only digits, ':' and space are supported; other characters are skipped.
 */
@Composable
fun PixelText(
    text: String,
    pixel: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    shadow: Color = Color(0xFF3B3B3B)
) {
    val glyphs = remember(text) { text.mapNotNull { PIXEL_GLYPHS[it] } }
    if (glyphs.isEmpty()) return
    val columns = glyphs.sumOf { it[0].length + 1 } - 1

    Canvas(modifier = modifier.size(width = pixel * (columns + 1), height = pixel * 8)) {
        val ps = pixel.toPx()
        for (pass in 0..1) {
            var pen = 0f
            glyphs.forEach { glyph ->
                glyph.forEachIndexed { row, line ->
                    line.forEachIndexed { col, ch ->
                        if (ch == '#') {
                            val off = if (pass == 0) ps else 0f
                            drawRect(
                                color = if (pass == 0) shadow else color,
                                topLeft = Offset(pen + col * ps + off, row * ps + off),
                                size = Size(ps, ps)
                            )
                        }
                    }
                }
                pen += (glyph[0].length + 1) * ps
            }
        }
    }
}
