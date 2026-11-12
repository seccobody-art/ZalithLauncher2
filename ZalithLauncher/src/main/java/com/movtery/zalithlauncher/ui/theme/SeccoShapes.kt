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

package com.movtery.zalithlauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import com.movtery.zalithlauncher.setting.enums.CornerStyle

/**
 * Builds the Material shape scale from the user's corner style, so every card, button
 * and dialog that reads `MaterialTheme.shapes` follows it.
 */
fun seccoShapes(style: CornerStyle): Shapes {
    val base = style.baseDp.toFloat()
    return Shapes(
        extraSmall = RoundedCornerShape((base * 0.35f).dp),
        small = RoundedCornerShape((base * 0.6f).dp),
        medium = RoundedCornerShape((base * 0.9f).dp),
        large = RoundedCornerShape(base.dp),
        extraLarge = RoundedCornerShape((base * 1.4f).dp)
    )
}
