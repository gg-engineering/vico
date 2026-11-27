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
   * Draws the marker's text, after the axis decorations and the legend.
   *
   * Separate from [drawOverLayers] because the two want different heights in the stack: a label
   * belongs above everything, while a guideline and its indicators belong above the lines but below
   * whatever the axes draw on top. One pass cannot be in both places, and running the same pass
   * twice draws everything in it twice.
   */
  public fun drawLabels(context: CartesianDrawingContext, targets: List<Target>) {}

  /** Draws whatever the marker shows while nothing is marked, on the line [drawLabels] uses. */
  public fun drawResting(context: CartesianDrawingContext) {}

  /**
   * The window has come to rest. Formatting resting text here rather than per frame keeps it off
   * the draw path; the draw then reads what this left behind.
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
