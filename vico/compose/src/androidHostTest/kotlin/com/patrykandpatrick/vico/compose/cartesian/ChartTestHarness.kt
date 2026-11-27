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


package com.patrykandpatrick.vico.compose.cartesian

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.common.DrawingContext
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import androidx.compose.ui.unit.dp
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.StandardTestDispatcher

internal typealias ChartRule = AndroidComposeTestRule<*, ComponentActivity>

/**
 * Effects run on a queued dispatcher: `CartesianChartHost` registers with the producer from an
 * effect, and an immediate dispatcher lets that registration race its own disposal.
 */
@OptIn(ExperimentalTestApi::class)
internal fun createChartRule(): ChartRule =
  createAndroidComposeRule(ComponentActivity::class.java, StandardTestDispatcher())

private const val TIMEOUT_MS = 10_000L

/** Robolectric does not run draw passes on its own, so the window is drawn into a bitmap. */
internal fun ChartRule.drawChart() {
  waitForIdle()
  runOnUiThread {
    val view = activity.window.decorView
    view.draw(Canvas(Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)))
  }
  waitForIdle()
}

internal fun ChartRule.awaitFrame(
  recorder: FrameRecorder,
  predicate: (DrawnFrame) -> Boolean = { true },
): DrawnFrame {
  waitUntil(TIMEOUT_MS) {
    mainClock.advanceTimeByFrame()
    drawChart()
    recorder.last?.let(predicate) == true
  }
  return recorder.last!!
}

internal fun ChartRule.settle(recorder: FrameRecorder, frames: Int = 60): DrawnFrame {
  repeat(frames) {
    mainClock.advanceTimeByFrame()
    drawChart()
  }
  return awaitFrame(recorder)
}

internal data class DrawnFrame(
  val minX: Double,
  val maxX: Double,
  val xStep: Double,
  val minY: Double,
  val maxY: Double,
  val layerLeft: Float,
  val layerRight: Float,
  val layerTop: Float,
  val layerBottom: Float,
  val xSpacing: Float,
  val scroll: Float,
  val zoom: Float,
  val markerX: Double?,
)

internal class FrameRecorder : Decoration {
  val frames = CopyOnWriteArrayList<DrawnFrame>()

  val last: DrawnFrame?
    get() = frames.lastOrNull()

  override fun drawOverLayers(context: CartesianDrawingContext) {
    with(context) {
      val yRange = ranges.getYRange(null)
      frames +=
        DrawnFrame(
          minX = ranges.minX,
          maxX = ranges.maxX,
          xStep = ranges.xStep,
          minY = yRange.minY,
          maxY = yRange.maxY,
          layerLeft = layerBounds.left,
          layerRight = layerBounds.right,
          layerTop = layerBounds.top,
          layerBottom = layerBounds.bottom,
          xSpacing = layerDimensions.xSpacing,
          scroll = scroll,
          zoom = zoom,
          markerX = markerX,
        )
    }
  }
}

internal class RecordingFormatter(
  private val delegate: CartesianValueFormatter = CartesianValueFormatter.decimal()
) : CartesianValueFormatter {
  val labels = CopyOnWriteArrayList<Pair<Double, String>>()
  val drawnLabels = CopyOnWriteArrayList<Pair<Double, String>>()

  val values: List<Double>
    get() = labels.map { it.first }.distinct().sorted()

  val drawnValues: List<Double>
    get() = drawnLabels.map { it.first }.distinct().sorted()

  fun clear() {
    labels.clear()
    drawnLabels.clear()
  }

  override fun format(
    context: CartesianMeasuringContext,
    value: Double,
    verticalAxisPosition: Axis.Position.Vertical?,
  ): CharSequence =
    delegate.format(context, value, verticalAxisPosition).also {
      labels += value to it.toString()
      if (context is CartesianDrawingContext) drawnLabels += value to it.toString()
    }
}

internal class RecordingLineComponent(thicknessDp: Float = 2f) :
  LineComponent(Fill.Black, thicknessDp.dp) {
  val horizontalYs = CopyOnWriteArrayList<Float>()
  val verticals = CopyOnWriteArrayList<VerticalStroke>()

  data class VerticalStroke(val x: Float, val top: Float, val bottom: Float, val factor: Float)

  fun clear() {
    horizontalYs.clear()
    verticals.clear()
  }

  override fun drawHorizontal(
    context: DrawingContext,
    left: Float,
    right: Float,
    y: Float,
    thicknessFactor: Float,
  ) {
    horizontalYs += y
    super.drawHorizontal(context, left, right, y, thicknessFactor)
  }

  override fun drawVertical(
    context: DrawingContext,
    x: Float,
    top: Float,
    bottom: Float,
    thicknessFactor: Float,
  ) {
    verticals += VerticalStroke(x, top, bottom, thicknessFactor)
    super.drawVertical(context, x, top, bottom, thicknessFactor)
  }

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = System.identityHashCode(this)
}

internal class BoxRecorder : ShapeComponent() {
  val boxes = CopyOnWriteArrayList<List<Float>>()

  override fun draw(context: DrawingContext, left: Float, top: Float, right: Float, bottom: Float) {
    boxes += listOf(left, top, right, bottom)
  }

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = System.identityHashCode(this)
}

internal class PointRecorder : Component {
  val centers = CopyOnWriteArrayList<Pair<Float, Float>>()

  override fun draw(context: DrawingContext, left: Float, top: Float, right: Float, bottom: Float) {
    centers += (left + right) / 2 to (top + bottom) / 2
  }
}

internal class MarkerRecorder : CartesianMarkerVisibilityListener {
  @Volatile var shown: List<CartesianMarker.Target>? = null
  @Volatile var hidden = false

  override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
    shown = targets
    hidden = false
  }

  override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
    shown = targets
  }

  override fun onHidden(marker: CartesianMarker) {
    hidden = true
  }
}
