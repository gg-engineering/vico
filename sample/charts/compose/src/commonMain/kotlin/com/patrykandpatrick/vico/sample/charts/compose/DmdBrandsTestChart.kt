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

package com.patrykandpatrick.vico.sample.charts.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.VicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.ListItemPlacer
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.ScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.data.rememberScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberScrubMarkerController
import com.patrykandpatrick.vico.compose.cartesian.SnapBehaviorConfig
import com.patrykandpatrick.vico.compose.cartesian.rememberChartSnapFlingBehavior
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberFadingEdges
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Test chart for DMD Brands custom vico features.
 * Each section below tests a specific feature.
 *
 * **Feature 1: ScrollAwareRangeProvider**
 * The chart shows 200 data points with 3 distinct regions:
 * - Points 0-60: values around 150-170 (low weight region)
 * - Points 60-130: values around 175-195 (high weight region)
 * - Points 130-200: values around 155-165 (medium weight region)
 *
 * As you scroll through the chart, the Y-axis range dynamically adjusts
 * to the visible data points with animated transitions and nice tick labels.
 */
@Composable
fun DmdBrandsTestChart(modifier: Modifier = Modifier) {
  val modelProducer = remember { CartesianChartModelProducer() }
  // Feature 5: xWithPadding — start at X=80 with 2 xStep padding from left edge
  val startPaddingXStep = 02.5
  val scrollState = rememberVicoScrollState(
    initialScroll = Scroll.Absolute.xWithPadding(80.0, startPaddingXStep),
  )

  // ScrollAwareRangeProvider with simple nice-scale callback
  val rangeProvider = rememberScrollAwareRangeProvider(
    paddingEntries = 3,
    debounceMs = 150,
    animDurationMs = 250,
  ) { visibleEntries ->
    // Debug: log visible entries to verify correctness
    println("RangeProvider: entries=${visibleEntries.size}, " +
      "xRange=[${visibleEntries.firstOrNull()?.first?.toLong()}..${visibleEntries.lastOrNull()?.first?.toLong()}], " +
      "yRange=[${visibleEntries.minOfOrNull { it.second }?.toInt()}..${visibleEntries.maxOfOrNull { it.second }?.toInt()}]"
    )
    val visibleMinY = visibleEntries.minOf { it.second }
    val visibleMaxY = visibleEntries.maxOf { it.second }
    val step = niceStep(visibleMaxY - visibleMinY)
    val niceMin = floor(visibleMinY / step) * step
    val niceMax = ceil(visibleMaxY / step) * step
    val ticks = buildList {
      var tick = niceMin
      while (tick <= niceMax + step * 0.01) {
        add(tick)
        tick += step
      }
    }
    (niceMin..niceMax) to ticks
  }

  // Generate sample data with NON-SEQUENTIAL X values (gaps in X axis).
  // Tests: marker on X with no data point, interpolation across gaps.
  LaunchedEffect(Unit) {
    val entries = generateNonSequentialData()
    modelProducer.runTransaction {
      lineSeries {
        series(x = entries.map { it.first }, y = entries.map { it.second })
      }
    }
  }

  // Feature 8: Marker at any X — consumer decides where marker lands
  val scrubController = rememberScrubMarkerController(
    scrollState = scrollState,
    delayMs = 200L,
    onMarkerIndexChanged = { clickX, targets ->
      if (clickX == null) {
        null // dismiss
      } else {
        // Snap to nearest visible axis label (interpolated Y if not a data point)
        val visibleLabels = scrollState.getVisibleAxisLabels()
        val nearest = visibleLabels.minByOrNull { kotlin.math.abs(it - clickX) } ?: clickX
        println("Marker: clickX=${clickX.toInt()} → nearest label=${nearest.toInt()}")
        nearest
      }
    },
  )

  // Feature 4: Snap — drag snaps to nearest label, fling jumps to next/prev window
  val snapFling = rememberChartSnapFlingBehavior(
    scrollState = scrollState,
    config = SnapBehaviorConfig(
      scrollPaddingXStep = startPaddingXStep,
      snapToLabel = { currentXLabel, projectedXLabel, isDrag, isForward ->
        val windowSize = 8.0
        val maxWindowsPerFling = 3  // Cap: never jump more than 3 windows
        val x = currentXLabel ?: 0.0
        val projected = projectedXLabel ?: x
        // minX of data — snapping below this should show the content padding area
        val dataMinX = 0.0
        val target = if (isDrag) {
          kotlin.math.round(x).coerceAtLeast(dataMinX)
        } else {
          val currentWindow = kotlin.math.floor(x / windowSize)
          val projectedWindow = kotlin.math.round(projected / windowSize)
          val rawWindowDelta = (projectedWindow - currentWindow).toInt()
          val clampedDelta = if (isForward) {
            rawWindowDelta.coerceIn(1, maxWindowsPerFling)
          } else {
            rawWindowDelta.coerceIn(-maxWindowsPerFling, -1)
          }
          ((currentWindow + clampedDelta) * windowSize).coerceAtLeast(dataMinX)
        }
        println("Snap: x=${x.toInt()} projected=${projected.toInt()} isDrag=$isDrag fwd=$isForward → target=${target.toInt()}")
        target
      },
    ),
  )

  CartesianChartHost(
    chart = rememberCartesianChart(
      rememberLineCartesianLayer(
        rangeProvider = rangeProvider,
        lineProvider = LineCartesianLayer.LineProvider.series(
          LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(Fill(Color(0xFF6750A4))),
            interpolator = LineCartesianLayer.Interpolator.monotone(),
            pointProvider = LineCartesianLayer.PointProvider.single(
              LineCartesianLayer.Point(
                ShapeComponent(Fill(Color(0xFF6750A4)), CircleShape),
                size = 4.dp,
              ),
            ),
          ),
        ),
      ),
      startAxis = VerticalAxis.rememberStart(
        guideline = null,
        label = null,
        tick = null,
        size = BaseAxis.Size.Scroll(8.dp , isLabelsScrollable = true)
      ),
      endAxis = VerticalAxis.rememberEnd(
        itemPlacer = ListItemPlacer(ticks = { rangeProvider.currentTicks }),
        markerDecoration = VerticalAxis.MarkerDecoration(
          y = { 170.0 },
          markerComponent = rememberTextComponent(
            style = TextStyle(
              color = Color.White,
              fontSize = 12.sp,
            ),
            padding = Insets(horizontal = 8.dp, vertical = 2.dp),
            background = rememberShapeComponent(
              Fill(Color(0xFF458239)),
              CircleShape,
            ),
          ),
          label = { "170" },
          outsideRangeOffset = 10.dp,
        )
      ),
      // Feature 6B: Separators at data boundaries and every 50 X units
      bottomAxis = HorizontalAxis.rememberBottom(
        separators = HorizontalAxis.Separators(
          values = listOf(0.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0),
          line = rememberLineComponent(Fill(Color(0x33000000)), thickness = 2.dp , strokeThickness = 2.dp),
        ),
      ),
      marker = rememberMarker(),
      markerController = scrubController,
      visibleLabelsCount = 8.0,  // Show ~8 entries in visible window
    ),
    modelProducer = modelProducer,
    scrollState = scrollState,
    flingBehavior = snapFling,
    modifier = modifier.fillMaxWidth().height(300.dp),
  )
}

/**
 * Generates weight-like data with NON-SEQUENTIAL X values.
 * X gaps simulate real-world data (missing days, irregular measurements).
 * Three regions with different value ranges to test scroll-aware range.
 */
private fun generateNonSequentialData(): List<Pair<Double, Double>> {
  val entries = mutableListOf<Pair<Double, Double>>()
  var x = 0.0
  for (i in 0 until 100) {
    val y = when {
      i < 30 -> 160.0 + 8.0 * sin(i * 0.3) + (i % 5) * 0.5   // Low region
      i < 65 -> 185.0 + 6.0 * sin(i * 0.4) + (i % 4) * 0.3   // High region
      else -> 158.0 + 5.0 * sin(i * 0.25) + (i % 6) * 0.4     // Medium region
    }
    entries.add(x to y)
    // Non-sequential: random gaps of 1-5 between X values
    x += 1.0 + (i % 5).toDouble()
  }
  return entries
}

/** Returns a nice step size for the given range. Uses 1-2-5 pattern. */
private fun niceStep(range: Double): Double {
  if (range <= 0) return 1.0
  val magnitude = 10.0.pow(floor(kotlin.math.log10(range / 4.0)))
  val normalized = range / 4.0 / magnitude
  val nice = when {
    normalized <= 1.0 -> 1.0
    normalized <= 2.0 -> 2.0
    normalized <= 5.0 -> 5.0
    else -> 10.0
  }
  return nice * magnitude
}
