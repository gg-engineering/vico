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
  fun tryUpdateXWidensTheDomainWithoutTouchingTheAxis() {
    val ranges = MutableCartesianChartRanges()
    ranges.tryUpdate(minX = 10.0, maxX = 20.0, minY = 150.0, maxY = 190.0, axisPosition = end)

    // What a layer drawing a second metric in the primary's range contributes: it reaches further
    // in time, and says nothing about the axis the primary owns.
    ranges.tryUpdateX(minX = 5.0, maxX = 25.0)

    assertEquals(5.0, ranges.minX)
    assertEquals(25.0, ranges.maxX)
    assertEquals(150.0, ranges.getYRange(end).minY)
    assertEquals(190.0, ranges.getYRange(end).maxY)
  }

  @Test
  fun tryUpdateWouldHaveWidenedTheAxisToCoverTheOtherMetric() {
    // The behaviour `tryUpdateX` exists to avoid, pinned so the difference stays visible: a pulse
    // of 60..90 unioned into a weight axis of 150..190 opens it at zero, because the auto range
    // provider coerces a minimum to at most zero and the union only ever widens.
    val ranges = MutableCartesianChartRanges()
    ranges.tryUpdate(minX = 10.0, maxX = 20.0, minY = 150.0, maxY = 190.0, axisPosition = end)
    ranges.tryUpdate(minX = 10.0, maxX = 20.0, minY = 0.0, maxY = 90.0, axisPosition = end)

    assertEquals(0.0, ranges.getYRange(end).minY)
  }

  @Test
  fun anAxisNoLayerClaimedFallsBackToTheChartWideRange() {
    val ranges = MutableCartesianChartRanges()
    ranges.tryUpdate(minX = 0.0, maxX = 1.0, minY = 2.0, maxY = 8.0, axisPosition = null)

    assertEquals(2.0, ranges.getYRange(end).minY)
    assertEquals(8.0, ranges.getYRange(end).maxY)
  }

  @Test
  fun aChartNoLayerOfferedARangeForIsNeutralRatherThanFatal() {
    // Reachable once every layer transforms its y values into someone else's range. It used to
    // throw out of a map lookup.
    val ranges = MutableCartesianChartRanges()
    ranges.tryUpdateX(minX = 0.0, maxX = 1.0)

    assertEquals(0.0, ranges.getYRange(end).minY)
    assertEquals(1.0, ranges.getYRange(end).maxY)
  }
}
