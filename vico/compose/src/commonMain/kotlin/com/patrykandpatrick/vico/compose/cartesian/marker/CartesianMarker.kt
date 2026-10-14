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

package com.patrykandpatrick.vico.compose.cartesian.marker

import androidx.compose.runtime.Immutable
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerMarginUpdater

/** Marks [CartesianChart] objects. */
@Immutable
public interface CartesianMarker : CartesianLayerMarginUpdater<CartesianChartModel> {
  /** Draws content under the [CartesianLayer]s. */
  public fun drawUnderLayers(context: CartesianDrawingContext, targets: List<Target>) {}

  /** Draws content over the [CartesianLayer]s. */
  public fun drawOverLayers(context: CartesianDrawingContext, targets: List<Target>) {}

  /**
   * Draws what stands on the marker's line while nothing is marked.
   *
   * The marker's label occupies a line of its own outside the plot, and the chart is the only
   * thing that knows where that line is. A caller wanting to put its own text there — a date range
   * describing the window, say — otherwise has to guess the position from outside and blank its
   * text whenever a marker appears, because the two would collide. Drawing it here means the
   * chart hands the line to whichever of the two is live. (MOB-2953)
   */
  public fun drawResting(context: CartesianDrawingContext) {}

  /**
   * Reports the _x_ range in view once scrolling has stopped.
   *
   * [drawResting] describes the window rather than the data, and a window read per frame churns
   * through every one the scroll passes over on its way to the one the user stops at. The chart
   * calls this when the scroll settles, so the resting content can hold still until then.
   * (MOB-2953)
   */
  public fun onScrollSettled(visibleXRange: ClosedFloatingPointRange<Double>) {}

  /** Houses information on an object to be marked. */
  public interface Target {
    /** The _x_ value. */
    public val x: Double

    /** The pixel _x_ coordinate. */
    public val canvasX: Float
  }
}
