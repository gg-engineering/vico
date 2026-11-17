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

package com.patrykandpatrick.vico.compose.cartesian.data

import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import kotlin.test.Test
import kotlin.test.assertEquals

class MutableCartesianChartRangesTest {

  private val end = Axis.Position.Vertical.End

  @Test
  fun anAxisNoLayerClaimedFallsBackToTheChartWideRange() {
    val ranges = MutableCartesianChartRanges()
    ranges.tryUpdate(minX = 0.0, maxX = 1.0, minY = 2.0, maxY = 8.0, axisPosition = null)

    assertEquals(2.0, ranges.getYRange(end).minY)
    assertEquals(8.0, ranges.getYRange(end).maxY)
  }

  @Test
  fun aChartNoLayerOfferedARangeForIsNeutralRatherThanFatal() {
    // Reachable once every layer transforms its y values into someone else's range, which
    // `contributesYRange` makes routine. It used to throw out of a map lookup.
    val ranges = MutableCartesianChartRanges()

    assertEquals(0.0, ranges.getYRange(end).minY)
    assertEquals(1.0, ranges.getYRange(end).maxY)
  }
}
