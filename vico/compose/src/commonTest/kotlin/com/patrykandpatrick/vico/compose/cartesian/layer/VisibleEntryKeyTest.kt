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

package com.patrykandpatrick.vico.compose.cartesian.layer

import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The cache key that decides whether a `yTransform` is recomputed.
 *
 * A transform that rescales its series against the values in view is correct only while the view
 * holds the same values, so the key has to move with the entry set and stay still otherwise.
 */
class VisibleEntryKeyTest {

  private val series = (0..9).map { LineCartesianLayerModel.Entry(it.toDouble(), it.toDouble()) }

  @Test
  fun aWindowMovingWithinTheGapBetweenTwoEntriesKeepsTheKey() {
    // Both windows hold entries 3..6 — the line must not rescale under a finger that has not yet
    // brought a new point into view.
    assertEquals(
      visibleEntryKey(series, 2.1..6.9),
      visibleEntryKey(series, 2.9..6.1),
    )
  }

  @Test
  fun admittingAnEntryMovesTheKey() {
    assertNotEquals(
      visibleEntryKey(series, 3.0..6.0),
      visibleEntryKey(series, 3.0..7.0),
    )
  }

  @Test
  fun droppingAnEntryMovesTheKey() {
    assertNotEquals(
      visibleEntryKey(series, 3.0..6.0),
      visibleEntryKey(series, 4.0..6.0),
    )
  }

  @Test
  fun bothEndsAreInclusive() {
    // 3.0..6.0 covers entries 3, 4, 5 and 6 — the same slice an open window just inside them gives
    // only if the bounds are counted in.
    assertEquals(
      visibleEntryKey(series, 3.0..6.0),
      visibleEntryKey(series, 2.5..6.5),
    )
  }

  @Test
  fun aWindowPastEveryEntryIsDistinctFromOneBeforeThem() {
    // Both are empty slices, and both must still differ from a window that holds something.
    val beyond = visibleEntryKey(series, 20.0..30.0)
    val before = visibleEntryKey(series, -30.0..-20.0)
    assertNotEquals(beyond, before)
    assertNotEquals(beyond, visibleEntryKey(series, 3.0..6.0))
    assertNotEquals(before, visibleEntryKey(series, 3.0..6.0))
  }

  @Test
  fun anEmptySeriesHasAKeyOfItsOwn() {
    val empty = emptyList<LineCartesianLayerModel.Entry>()
    assertEquals(visibleEntryKey(empty, 0.0..10.0), visibleEntryKey(empty, 50.0..60.0))
  }

  @Test
  fun theWholeDomainMatchesAWindowWiderThanIt() {
    assertEquals(
      visibleEntryKey(series, 0.0..9.0),
      visibleEntryKey(series, -100.0..100.0),
    )
  }
}
