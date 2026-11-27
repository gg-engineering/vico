/*
 * Copyright 2026 by Patryk Goworowski and Patrick Michalik.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.patrykandpatrick.vico.compose.common

private const val COLOR_SPACE_MASK = 0x3fL

private const val SRGB_ALPHA_SHIFT = 56

private const val MAX_8_BIT = 255.0f

private const val WIDE_ALPHA_SHIFT = 6

private const val WIDE_ALPHA_MASK = 0x3ffL

private const val MAX_10_BIT = 1023.0f

private const val ALPHA_BIT_SHIFT = 24
private const val COLOR_MASK = 0xff

internal val Int.alpha: Int
  get() = extractColorChannel(ALPHA_BIT_SHIFT)

private fun Int.extractColorChannel(bitShift: Int): Int = this shr bitShift and COLOR_MASK

internal val Long.alpha: Float
  get() =
    if (this and COLOR_SPACE_MASK == 0L) {
      (this shr SRGB_ALPHA_SHIFT and COLOR_MASK.toLong()) / MAX_8_BIT
    } else {
      (this shr WIDE_ALPHA_SHIFT and WIDE_ALPHA_MASK) / MAX_10_BIT
    }
