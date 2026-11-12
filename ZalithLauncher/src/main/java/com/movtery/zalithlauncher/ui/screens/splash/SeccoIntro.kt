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

package com.movtery.zalithlauncher.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

//Timeline, in milliseconds
private const val T_DROP_START = 250f
private const val T_BOOM = 1100f
private const val T_WIND_START = 1500f
private const val T_TEXT_START = 2000f
private const val T_FADE_START = 3900f
private const val T_TOTAL = 4400f

/** Blocky S, 7 columns x 9 rows */
private val S_GRID = listOf(
    ".#####.",
    "##...##",
    "##.....",
    "##.....",
    ".#####.",
    ".....##",
    ".....##",
    "##...##",
    ".#####."
)

private fun glyph(vararg rows: String): List<String> = rows.toList()

/** Lowercase letters are 5 rows tall, so pad two empty rows on top to share the 7 row cell */
private fun lowGlyph(vararg rows: String): List<String> =
    List(2) { " ".repeat(rows[0].length) } + rows.toList()

/** Pixel letters in the style of the Minecraft default font */
private val TEXT_GLYPHS: Map<Char, List<String>> = mapOf(
    'S' to glyph(".###.", "#...#", "#....", ".###.", "....#", "#...#", ".###."),
    'L' to glyph("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
    'h' to glyph("#....", "#....", "####.", "#...#", "#...#", "#...#", "#...#"),
    'e' to lowGlyph(".###.", "#...#", "#####", "#....", ".###."),
    'c' to lowGlyph(".###.", "#...#", "#....", "#...#", ".###."),
    'o' to lowGlyph(".###.", "#...#", "#...#", "#...#", ".###."),
    'a' to lowGlyph(".###.", "....#", ".####", "#...#", ".####"),
    'u' to lowGlyph("#...#", "#...#", "#...#", "#...#", ".####"),
    'n' to lowGlyph("####.", "#...#", "#...#", "#...#", "#...#"),
    'r' to lowGlyph("#.##", "##..", "#...", "#...", "#..."),
    ' ' to glyph("   ", "   ", "   ", "   ", "   ", "   ", "   ")
)

private val TNT_LETTERS = listOf(
    listOf("###", ".#.", ".#.", ".#.", ".#."),
    listOf("#.#", "###", "###", "#.#", "#.#"),
    listOf("###", ".#.", ".#.", ".#.", ".#.")
)

private class Particle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val life: Float
)

private class Streak(
    val lane: Float,
    val delay: Float,
    val duration: Float,
    val tint: Color
)

private class Star(val x: Float, val y: Float, val phase: Float, val size: Float)

private class IntroData(
    val particles: List<Particle>,
    val streaks: List<Streak>,
    val stars: List<Star>
)

private fun makeIntroData(): IntroData {
    val r = Random(7L)
    val palette = listOf(
        Color.White,
        Color(0xFFFFE08A),
        Color(0xFFFF9A3C),
        Color(0xFFB8A4FF),
        Color(0xFF9E9E9E),
        Color(0xFF555555)
    )
    val particles = List(72) {
        Particle(
            angle = r.nextFloat() * (2.0 * PI).toFloat(),
            speed = 3f + r.nextFloat() * 14f,
            size = 0.12f + r.nextFloat() * 0.4f,
            color = palette[r.nextInt(palette.size)],
            life = 0.55f + r.nextFloat() * 0.8f
        )
    }
    val streakCount = 18
    val streaks = List(streakCount) { i ->
        Streak(
            lane = -1.8f + i * (3.6f / (streakCount - 1)),
            delay = r.nextFloat() * 900f,
            duration = 1100f + r.nextFloat() * 500f,
            tint = if (i % 2 == 0) Color(0xFFEDE5FF) else Color(0xFFB69CFF)
        )
    }
    val stars = List(40) {
        Star(r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f, 1f + r.nextFloat() * 1.4f)
    }
    return IntroData(particles, streaks, stars)
}

/**
 * Secco intro: a TNT drops to the screen center and blows up, the blocky S pops out of the blast,
 * wind streaks flow around it and the name is typed underneath. Tap to skip.
 */
@Composable
fun SeccoIntro(onFinished: () -> Unit) {
    val data = remember { makeIntroData() }
    val clock = remember { Animatable(0f) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (!finished) {
            finished = true
            currentOnFinished()
        }
    }

    LaunchedEffect(Unit) {
        clock.animateTo(T_TOTAL, tween(T_TOTAL.toInt(), easing = LinearEasing))
        finish()
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - ((clock.value - T_FADE_START) / (T_TOTAL - T_FADE_START)).coerceIn(0f, 1f)
            }
            .pointerInput(Unit) { detectTapGestures { finish() } }
    ) {
        drawIntro(clock.value, data)
    }
}

private fun DrawScope.drawIntro(t: Float, data: IntroData) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val bs = min(w * 0.5f / 7f, h * 0.42f / 9f)

    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF05030A), Color(0xFF160A2E), Color(0xFF05030A))
        )
    )
    data.stars.forEach { s ->
        val a = (0.25f + 0.35f * sin(t * 0.004f + s.phase)).coerceIn(0f, 1f)
        val sz = max(2f, bs * 0.08f) * s.size
        drawRect(Color.White.copy(alpha = a), Offset(s.x * w, s.y * h), Size(sz, sz))
    }

    val sinceBoom = t - T_BOOM
    val shake = if (sinceBoom in 0f..450f) (1f - sinceBoom / 450f) * bs * 0.35f else 0f
    translate(sin(t * 0.09f) * shake, cos(t * 0.13f) * shake) {
        drawGlow(t, cx, cy, bs)
        drawWind(t, cx, cy, bs, data)
        drawLetterS(t, cx, cy, bs)
        drawTitle(t, cx, cy, bs)
        drawTnt(t, cx, cy, bs)
        drawExplosion(t, cx, cy, bs, data)
    }

    if (sinceBoom in 0f..380f) {
        drawRect(Color.White.copy(alpha = (1f - sinceBoom / 380f) * 0.9f))
    }
}

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

private fun mix(a: Color, b: Color, f: Float): Color = Color(
    red = a.red + (b.red - a.red) * f,
    green = a.green + (b.green - a.green) * f,
    blue = a.blue + (b.blue - a.blue) * f,
    alpha = 1f
)

private fun DrawScope.drawGlow(t: Float, cx: Float, cy: Float, bs: Float) {
    val g = ((t - T_BOOM) / 600f).coerceIn(0f, 1f)
    if (g <= 0f) return
    val radius = bs * 7f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF8A5CFF).copy(alpha = 0.35f * g), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawLetterS(t: Float, cx: Float, cy: Float, bs: Float) {
    val p = ((t - T_BOOM) / 450f).coerceIn(0f, 1f)
    if (t < T_BOOM) return
    val scale = 0.2f + 0.8f * easeOutBack(p)
    val alpha = (p * 3f).coerceIn(0f, 1f)
    val cell = bs * scale
    val left = cx - 3.5f * cell
    val top = cy - 4.5f * cell
    val light = Color(0xFFF6F1FF)
    val dark = Color(0xFFB7A0F7)
    val edge = cell * 0.14f

    for (pass in 0..1) {
        for (r in S_GRID.indices) {
            for (c in S_GRID[r].indices) {
                if (S_GRID[r][c] != '#') continue
                val x = left + c * cell
                val y = top + r * cell
                if (pass == 0) {
                    //drop shadow
                    drawRect(
                        Color(0xFF2A0F5E).copy(alpha = 0.85f * alpha),
                        Offset(x + cell * 0.3f, y + cell * 0.3f),
                        Size(cell, cell)
                    )
                } else {
                    drawRect(mix(light, dark, r / 8f).copy(alpha = alpha), Offset(x, y), Size(cell, cell))
                    val hi = Color.White.copy(alpha = 0.55f * alpha)
                    val lo = Color(0xFF4A22A8).copy(alpha = 0.5f * alpha)
                    drawRect(hi, Offset(x, y), Size(cell, edge))
                    drawRect(hi, Offset(x, y), Size(edge, cell))
                    drawRect(lo, Offset(x, y + cell - edge), Size(cell, edge))
                    drawRect(lo, Offset(x + cell - edge, y), Size(edge, cell))
                }
            }
        }
    }
}

private fun DrawScope.drawTnt(t: Float, cx: Float, cy: Float, bs: Float) {
    if (t < T_DROP_START || t >= T_BOOM) return
    val size = bs * 4.6f
    val f = ((t - T_DROP_START) / (T_BOOM - T_DROP_START)).coerceIn(0f, 1f)
    val yc = -size + (cy + size) * f * f
    val pulseStart = T_BOOM - 220f
    val pulse = if (t > pulseStart) 1f + 0.18f * ((t - pulseStart) / 220f) else 1f
    val u = size * pulse / 16f
    val left = cx - 8f * u
    val top = yc - 8f * u

    val red = Color(0xFFD0372B)
    val redDark = Color(0xFFA32018)
    val band = Color(0xFFE9E4DA)
    val outline = Color(0xFF3A0B07)

    for (row in 0..15) {
        for (col in 0..15) {
            var color = when {
                row == 0 || row == 15 || col == 0 || col == 15 -> outline
                row in 5..10 -> band
                col % 4 == 0 -> redDark
                else -> red
            }
            //"TNT" label on the white band
            val lr = row - 6
            if (lr in 0..4) {
                var cursor = 2
                for (letter in TNT_LETTERS) {
                    val lc = col - cursor
                    if (lc in 0..2 && letter[lr][lc] == '#') color = Color.Black
                    cursor += 4
                }
            }
            drawRect(color, Offset(left + col * u, top + row * u), Size(u + 0.5f, u + 0.5f))
        }
    }

    //fuse blink
    if (t > 650f && ((t / 90f).toInt() % 2 == 0)) {
        drawRect(Color.White.copy(alpha = 0.8f), Offset(left, top), Size(16f * u, 16f * u))
    }
}

private fun DrawScope.drawExplosion(t: Float, cx: Float, cy: Float, bs: Float, data: IntroData) {
    val tp = t - T_BOOM
    if (tp < 0f) return
    val sec = tp / 1000f

    data.particles.forEach { p ->
        if (sec > p.life) return@forEach
        val a = 1f - sec / p.life
        val x = cx + cos(p.angle) * p.speed * sec * bs
        val y = cy + sin(p.angle) * p.speed * sec * bs + 0.5f * 16f * bs * sec * sec
        val s = bs * p.size * (0.6f + 0.4f * a)
        drawRect(p.color.copy(alpha = a), Offset(x - s / 2f, y - s / 2f), Size(s, s))
    }

    //blocky shock ring
    val rp = tp / 500f
    if (rp in 0f..1f) {
        val half = bs * (1.5f + rp * 7f)
        val th = bs * 0.35f * (1f - rp)
        val color = Color(0xFFFFE9B0).copy(alpha = (1f - rp) * 0.8f)
        drawRect(color, Offset(cx - half, cy - half), Size(2f * half, th))
        drawRect(color, Offset(cx - half, cy + half - th), Size(2f * half, th))
        drawRect(color, Offset(cx - half, cy - half), Size(th, 2f * half))
        drawRect(color, Offset(cx + half - th, cy - half), Size(th, 2f * half))
    }
}

private fun DrawScope.drawWind(t: Float, cx: Float, cy: Float, bs: Float, data: IntroData) {
    val w = size.width
    val halfH = 4.5f * bs + bs
    val sigma = bs * 4.2f
    val grid = bs * 0.25f

    data.streaks.forEachIndexed { i, s ->
        val xp = (t - T_WIND_START - s.delay) / s.duration
        if (xp < 0f || xp > 1f) return@forEachIndexed

        val head = -0.1f * w + xp * 1.25f * w
        val baseY = cy + s.lane * (4.5f * bs)
        val b = baseY - cy
        val sign = if (b >= 0f) 1f else -1f
        //lanes crossing the letter are pushed up or down so the wind flows around it
        val push = max(0f, halfH - abs(b))
        val edge = min(1f, min(xp * 6f, (1f - xp) * 6f))

        for (k in 0 until 20) {
            val x = head - k * bs * 0.5f
            val dxn = (x - cx) / sigma
            val y = baseY + sign * push * exp(-dxn * dxn) + sin(x * 0.012f + i) * bs * 0.18f
            val fall = 1f - k / 20f
            val sz = bs * 0.34f * (0.45f + 0.55f * fall)
            val gx = (x / grid).roundToInt() * grid
            val gy = (y / grid).roundToInt() * grid
            drawRect(
                s.tint.copy(alpha = (0.9f * fall * edge).coerceIn(0f, 1f)),
                Offset(gx, gy),
                Size(sz, sz)
            )
        }
    }
}

private fun DrawScope.drawTitle(t: Float, cx: Float, cy: Float, bs: Float) {
    if (t < T_TEXT_START) return
    val text = "Secco Launcher"
    var cols = 0
    text.forEach { cols += TEXT_GLYPHS.getValue(it)[0].length + 1 }
    cols -= 1
    val ps = min(bs * 0.5f, size.width * 0.8f / cols)
    var penX = cx - cols * ps / 2f
    val top = cy + 4.5f * bs + bs * 1.1f
    val shadow = Color(0xFF3B3B3B)

    text.forEachIndexed { j, ch ->
        val g = TEXT_GLYPHS.getValue(ch)
        val gw = g[0].length
        val tj = ((t - T_TEXT_START - j * 70f) / 240f).coerceIn(0f, 1f)
        if (tj > 0f) {
            val yOff = (1f - tj) * ps * 4f
            for (pass in 0..1) {
                for (r in g.indices) {
                    for (c in 0 until gw) {
                        if (g[r][c] != '#') continue
                        val x = penX + c * ps + if (pass == 0) ps else 0f
                        val y = top + r * ps + yOff + if (pass == 0) ps else 0f
                        val color = if (pass == 0) shadow else Color.White
                        drawRect(color.copy(alpha = tj), Offset(x, y), Size(ps, ps))
                    }
                }
            }
        }
        penX += (gw + 1) * ps
    }
}
