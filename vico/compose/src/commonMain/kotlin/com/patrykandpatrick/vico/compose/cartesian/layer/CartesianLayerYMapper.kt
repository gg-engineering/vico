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

import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartRanges
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.common.data.ExtraStore

/**
 * Restates a series' _y_ values against the axis the chart is drawing, so a line in one unit can
 * share a plot with a line in another.
 *
 * Called on the draw path, so an implementation is expected to cache: it is handed the whole series
 * and the range it is being drawn against, and should return quickly when neither has moved. The
 * library deliberately holds no cache of its own — only the implementation knows what makes its
 * answer stale.
 */
public interface CartesianLayerYMapper {
  /**
   * The _y_ values to draw instead of the series' own, in series order, or null to draw the series
   * unchanged.
   */
  public fun map(
    series: List<LineCartesianLayerModel.Entry>,
    targetYRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
    extraStore: ExtraStore,
  ): DoubleArray?

  /** Opacity for the mapped line, for fading one unit out as another fades in. */
  public fun opacity(): Float = 1f
}
