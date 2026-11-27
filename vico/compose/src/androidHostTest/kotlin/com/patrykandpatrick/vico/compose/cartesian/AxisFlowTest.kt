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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.common.Position
import java.text.DecimalFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AxisFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val yLabels = RecordingFormatter(CartesianValueFormatter { _, value, _ -> DecimalFormat("0.0").format(value) })
  private val xLabels = RecordingFormatter()
  private val yGuideline = RecordingLineComponent(thicknessDp = 1f)
  private val xGuideline = RecordingLineComponent(thicknessDp = 1f)

  private fun setChart(
    rangeProvider: CartesianLayerRangeProvider,
    step: Double?,
    verticalAxisPosition: Axis.Position.Vertical? = null,
    xFormatter: CartesianValueFormatter = xLabels,
  ) {
    composeRule.setContent {
      val itemPlacer = VerticalAxis.ItemPlacer.step({ step })
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              rangeProvider = rangeProvider,
              verticalAxisPosition = verticalAxisPosition,
            ),
            startAxis =
              VerticalAxis.rememberStart(
                valueFormatter = yLabels,
                guideline = yGuideline,
                itemPlacer = itemPlacer,
              ),
            bottomAxis =
              HorizontalAxis.rememberBottom(valueFormatter = xFormatter, guideline = xGuideline),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
  }

  private fun pushWeights(vararg y: Number) {
    runBlocking { producer.runTransaction { lineSeries { series(y.toList()) } } }
    composeRule.awaitFrame(recorder)
  }

  @Test
  fun `a pound range labels every ten pounds with a guideline above the bottom axis`() {
    setChart(CartesianLayerRangeProvider.fixed(minY = 150.0, maxY = 190.0), step = 10.0)
    pushWeights(182.4, 181.9, 176.2, 171.0)

    assertEquals(listOf(150.0, 160.0, 170.0, 180.0, 190.0), yLabels.values)
    assertEquals(listOf("150.0", "160.0", "170.0", "180.0", "190.0"), yLabels.distinctLabels())
    val ys = yGuideline.horizontalYs.distinct().sortedDescending()
    assertEquals(4, ys.size)
    val gaps = ys.zipWithNext { a, b -> a - b }
    assertTrue("guidelines evenly spaced: $gaps", gaps.all { kotlin.math.abs(it - gaps[0]) <= 1f })
  }

  @Test
  fun `a kilogram range with half-kilogram steps keeps one decimal`() {
    setChart(CartesianLayerRangeProvider.fixed(minY = 68.0, maxY = 70.0), step = 0.5)
    pushWeights(69.4, 68.8, 69.1)

    assertEquals(listOf("68.0", "68.5", "69.0", "69.5", "70.0"), yLabels.distinctLabels())
  }

  @Test
  fun `an automatic range for positive weights never labels a negative value`() {
    setChart(CartesianLayerRangeProvider.auto(), step = null)
    pushWeights(0.4, 1.6, 2.1)

    assertTrue(yLabels.values.isNotEmpty())
    assertTrue(yLabels.values.all { it >= 0.0 })
    assertEquals(0.0, recorder.last!!.minY, 0.0)
  }

  @Test
  fun `an automatic range for kilograms rounds the top up to the next whole kilogram`() {
    setChart(CartesianLayerRangeProvider.auto(), step = null)
    pushWeights(70.1, 70.4, 69.8)

    assertEquals(71.0, recorder.last!!.maxY, 0.0)
  }

  @Test
  fun `a mixed range labels zero and both signs`() {
    setChart(CartesianLayerRangeProvider.fixed(minY = -4.0, maxY = 6.0), step = 2.0)
    pushWeights(-3, 5, 1)

    assertTrue(0.0 in yLabels.values)
    assertTrue(yLabels.values.any { it < 0 })
    assertTrue(yLabels.values.any { it > 0 })
  }

  @Test
  fun `day indexes become day names with a guideline on every day but the two chart edges`() {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayFormatter = RecordingFormatter { _, value, _ -> days[value.toInt()] }
    setChart(
      CartesianLayerRangeProvider.fixed(minY = 150.0, maxY = 190.0),
      step = 10.0,
      xFormatter = dayFormatter,
    )
    pushWeights(180, 181, 179, 178, 177, 176, 175)

    assertEquals(days, dayFormatter.labels.sortedBy { it.first }.map { it.second }.distinct())
    assertEquals(5, xGuideline.verticals.map { it.x }.distinct().size)
  }

  @Test
  fun `a formatter that returns an empty label is rejected`() {
    val empty = CartesianValueFormatter { _, _, _ -> "" }
    setChart(CartesianLayerRangeProvider.auto(), step = null, xFormatter = empty)

    val error =
      runCatching {
          runBlocking { producer.runTransaction { lineSeries { series(1, 2, 3) } } }
          composeRule.awaitFrame(recorder)
        }
        .exceptionOrNull()

    assertTrue("$error", error is IllegalStateException)
    assertTrue("${error?.message}", error?.message.orEmpty().contains("blank string"))
  }

  @Test
  fun `a below-zero range labels only non-positive values`() {
    setChart(CartesianLayerRangeProvider.fixed(minY = -40.0, maxY = 0.0), step = 10.0)
    pushWeights(-35, -20, -5)

    assertEquals(listOf(-40.0, -30.0, -20.0, -10.0, 0.0), yLabels.drawnValues)
  }

  @Test
  fun `a count placer spreads labels over a range that crosses zero`() {
    val labels = RecordingFormatter()
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = -20.0, maxY = 20.0)
            ),
            startAxis =
              VerticalAxis.rememberStart(
                valueFormatter = labels,
                itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 5 }) },
              ),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    pushWeights(-10, 15)

    assertTrue(0.0 in labels.drawnValues)
    assertTrue(labels.drawnValues.size <= 5)
    assertTrue(labels.drawnValues.all { it in -20.0..20.0 })
  }

  @Test
  fun `top and bottom label positions make room on their own side`() {
    var position by mutableStateOf(Position.Vertical.Top)
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = 150.0, maxY = 190.0)
            ),
            startAxis =
              VerticalAxis.rememberStart(
                valueFormatter = yLabels,
                verticalLabelPosition = position,
                itemPlacer = remember { VerticalAxis.ItemPlacer.step({ 10.0 }) },
              ),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    pushWeights(160, 170, 180)
    val top = recorder.last!!

    position = Position.Vertical.Bottom
    yLabels.clear()
    composeRule.drawChart()
    val bottom = recorder.last!!

    assertEquals(listOf(150.0, 160.0, 170.0, 180.0, 190.0), yLabels.drawnValues)
    assertTrue(top.layerTop > bottom.layerTop)
    assertTrue(bottom.layerBottom < top.layerBottom)
  }

  @Test
  fun `a zero label step is rejected`() {
    setChart(CartesianLayerRangeProvider.fixed(minY = 150.0, maxY = 190.0), step = 0.0)

    val error =
      runCatching {
          runBlocking { producer.runTransaction { lineSeries { series(160, 170) } } }
          composeRule.awaitFrame(recorder)
        }
        .exceptionOrNull()

    assertTrue("$error", error is IllegalArgumentException)
  }

  @Test
  fun `axes on every side share the same y range for their layer`() {
    val endLabels = RecordingFormatter()
    val topLabels = RecordingFormatter()
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 40.0),
              verticalAxisPosition = Axis.Position.Vertical.End,
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = yLabels),
            endAxis = VerticalAxis.rememberEnd(valueFormatter = endLabels),
            topAxis = HorizontalAxis.rememberTop(valueFormatter = topLabels),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xLabels),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    pushWeights(10, 20, 30)

    assertEquals(40.0, endLabels.values.max(), 0.0)
    assertEquals(xLabels.values, topLabels.values)
  }

  private fun RecordingFormatter.distinctLabels(): List<String> =
    labels.sortedBy { it.first }.map { it.second }.distinct()
}
