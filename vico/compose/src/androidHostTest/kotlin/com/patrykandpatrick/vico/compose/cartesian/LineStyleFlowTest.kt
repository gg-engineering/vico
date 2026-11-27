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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LayeredComponent
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.data.PieChartModel
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import com.patrykandpatrick.vico.compose.pie.renderToImageBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LineStyleFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val points = PointRecorder()
  private lateinit var scrollState: VicoScrollState

  private fun setLine(
    interpolator: () -> LineCartesianLayer.Interpolator = { LineCartesianLayer.Interpolator.Sharp },
    fill: LineCartesianLayer.LineFill = LineCartesianLayer.LineFill.single(Fill(LINE)),
    areaFill: LineCartesianLayer.AreaFill? = null,
    fadingEdges: FadingEdges? = null,
    point: LineCartesianLayer.Point = LineCartesianLayer.Point(points),
    animate: Boolean = false,
  ) {
    composeRule.setContent {
      scrollState = rememberVicoScrollState()
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                  fill = fill,
                  stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 4.dp),
                  areaFill = areaFill,
                  pointProvider = LineCartesianLayer.PointProvider.single(point),
                  interpolator = interpolator(),
                )
              ),
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = MIN_Y, maxY = MAX_Y),
            ),
            fadingEdges = fadingEdges,
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.size(320.dp, 200.dp).testTag(TAG),
        scrollState = scrollState,
        animationSpec = if (animate) tween(1_000, easing = LinearEasing) else null,
        initialAnimationSpec = null,
      )
    }
  }

  private fun push(vararg y: Number) {
    runBlocking { producer.runTransaction { lineSeries { series(y.toList()) } } }
    composeRule.awaitFrame(recorder) { it.maxX == y.size - 1.0 }
  }

  private fun capture(): Bitmap {
    val bitmap = composeRule.captureWindow()
    return bitmap
  }

  private fun chartOrigin(): Pair<Int, Int> {
    val bounds = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().boundsInWindow
    return bounds.left.toInt() to bounds.top.toInt()
  }

  private fun Bitmap.pixelsOf(color: Color): List<Pair<Int, Int>> {
    val argb = color.toArgb()
    return buildList {
      for (x in 0 until width) for (y in 0 until height) if (getPixel(x, y) == argb) add(x to y)
    }
  }

  private fun canvasY(value: Double): Float {
    val frame = recorder.last!!
    return frame.layerBottom -
      ((value - MIN_Y) / (MAX_Y - MIN_Y)).toFloat() * (frame.layerBottom - frame.layerTop)
  }

  @Test
  fun `a monotone curve never overshoots a peak or a valley`() {
    setLine(interpolator = { LineCartesianLayer.Interpolator.monotone() })
    push(0, 20, 0, 20, 10)

    val (_, top) = chartOrigin()
    val ys = capture().pixelsOf(LINE).map { it.second - top }

    assertTrue(ys.isNotEmpty())
    assertTrue("top ${ys.min()} vs peak ${canvasY(20.0)}", ys.min() >= canvasY(20.0) - 3)
    assertTrue("bottom ${ys.max()} vs valley ${canvasY(0.0)}", ys.max() <= canvasY(0.0) + 3)
  }

  @Test
  fun `curved interpolators still pass through every reading`() {
    var interpolator by mutableStateOf(LineCartesianLayer.Interpolator.cubic())
    setLine(interpolator = { interpolator })
    push(2, 18, 6, 14, 10)

    for (next in
      listOf(
        LineCartesianLayer.Interpolator.cubic(),
        LineCartesianLayer.Interpolator.catmullRom(),
        LineCartesianLayer.Interpolator.catmullRom(alpha = 0.5f),
      )) {
      interpolator = next
      points.centers.clear()
      val (left, top) = chartOrigin()
      val line = capture().pixelsOf(LINE).toSet()

      points.centers.distinct().forEach { (x, y) ->
        val near =
          (-2..2).any { dx ->
            (-2..2).any { dy -> (x.toInt() + left + dx) to (y.toInt() + top + dy) in line }
          }
        assertTrue("$next misses ($x, $y)", near)
      }
    }
  }

  @Test
  fun `interpolated readings between two entries follow the curve`() {
    setLine()
    push(100, 110, 120)
    composeRule.drawChart()

    val (exact, between, outside) =
      scrollState.getInterpolatedYValues(listOf(1.0, 1.5, 9.0)).single()

    assertEquals(110.0, exact!!, 0.001)
    assertTrue("$between", between!! in 110.0..120.0)
    assertNull(outside)
  }

  @Test
  fun `a color scale paints high values in the high color and low values in the low color`() {
    setLine(
      fill =
        LineCartesianLayer.LineFill.colorScale {
          HIGH at MAX_Y
          LOW at MIN_Y
        },
      areaFill =
        LineCartesianLayer.AreaFill.colorScale {
          HIGH.copy(alpha = 0.3f) at MAX_Y
          LOW.copy(alpha = 0.3f) at MIN_Y
        },
    )
    push(19, 1, 19, 1)

    val bitmap = capture()
    val (_, top) = chartOrigin()
    val reds = bitmap.reddish().map { it.second - top }
    val blues = bitmap.bluish().map { it.second - top }

    assertTrue(reds.isNotEmpty() && blues.isNotEmpty())
    assertTrue(
      "red ${reds.average()} above blue ${blues.average()}",
      reds.average() < blues.average(),
    )
  }

  private fun Bitmap.reddish() = pixelsWhere { r, g, b -> r > 200 && g < 60 && b < 60 }

  private fun Bitmap.bluish() = pixelsWhere { r, g, b -> b > 200 && r < 60 && g < 60 }

  private fun Bitmap.pixelsWhere(match: (Int, Int, Int) -> Boolean): List<Pair<Int, Int>> =
    buildList {
      for (x in 0 until width) for (y in 0 until height) {
        val c = getPixel(x, y)
        if (match((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)) add(x to y)
      }
    }

  @Test
  fun `fading edges soften the line where more data is scrolled off`() {
    setLine(fadingEdges = FadingEdges(startWidth = 40.dp, endWidth = 40.dp))
    push(*Array(60) { 10 })
    val (left, _) = chartOrigin()
    composeRule.runOnIdle {}

    val atStart = capture().pixelsOf(LINE).count { it.first - left > 300 }
    val solidEnd = capture().pixelsOf(LINE).count { it.first - left in 150..200 }

    assertTrue("end $atStart vs middle $solidEnd", atStart < solidEnd)
  }

  @Test
  fun `a layered point draws its front inside the back by the padding`() {
    val back = BoxRecorder()
    val front = PointRecorder()
    setLine(
      point =
        LineCartesianLayer.Point(
          LayeredComponent(back = back, front = front, padding = Insets(all = 2.dp)),
          size = 12.dp,
        )
    )
    push(5, 10, 15)

    composeRule.drawChart()

    val (l, t, r, b) = back.boxes.last()
    assertEquals(((l + r) / 2) to ((t + b) / 2), front.centers.last())
    assertTrue(r - l > 0f)
  }

  @Test
  fun `a diff animation moves the line from the old values to the new ones`() {
    setLine(animate = true)
    push(10, 10, 10)
    composeRule.mainClock.advanceTimeBy(1_100)
    composeRule.drawChart()
    val before = points.centers.distinct().last().second
    composeRule.mainClock.autoAdvance = false

    runBlocking { producer.runTransaction { lineSeries { series(18, 18, 18) } } }
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.mainClock.advanceTimeBy(500)
    points.centers.clear()
    composeRule.drawChart()
    val midway = points.centers.last().second
    composeRule.mainClock.advanceTimeBy(1_000)
    points.centers.clear()
    composeRule.drawChart()
    val after = points.centers.last().second

    assertTrue("$after < $midway < $before", after < midway && midway < before)
  }

  @Test
  fun `a chart and a pie render to a bitmap off screen`() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val chart =
      CartesianChart(
        LineCartesianLayer(
          LineCartesianLayer.LineProvider.series(
            LineCartesianLayer.Line(
              LineCartesianLayer.LineFill.single(Fill(LINE)),
              stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 4.dp),
            )
          )
        )
      )
    val model = CartesianChartModel(LineCartesianLayerModel.build { series(4, 8, 6) })
    lateinit var pie: PieChart
    composeRule.setContent {
      pie = rememberPieChart(PieChart.SliceProvider.series(PieChart.Slice(fill = Fill(LINE))))
    }
    composeRule.waitForIdle()

    val chartBitmap =
      chart.renderToImageBitmap(model, 300, 200, createFontFamilyResolver(context), Density(1f))
    val pieBitmap =
      pie.renderToImageBitmap(
        PieChartModel.build(1, 2),
        200,
        200,
        createFontFamilyResolver(context),
        Density(1f),
      )

    val chartPixels = chartBitmap.toPixelMap()
    assertTrue((0 until 300).any { x -> (0 until 200).any { y -> chartPixels[x, y] == LINE } })
    assertEquals(LINE.copy(alpha = 1f), pieBitmap.toPixelMap()[100, 100].copy(alpha = 1f))
  }

  private companion object {
    const val TAG = "chart"
    const val MIN_Y = 0.0
    const val MAX_Y = 20.0
    val LINE = Color(0xFFFF00FF)
    val HIGH = Color(0xFFFF0000)
    val LOW = Color(0xFF0000FF)
  }
}
