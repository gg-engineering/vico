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

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LayerGeometryFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val points = PointRecorder()
  private val columnA = RecordingLineComponent(thicknessDp = COLUMN_DP)
  private val columnB = RecordingLineComponent(thicknessDp = COLUMN_DP)

  private fun setLineChart(pointSpacingDp: Int = 24, withDataLabels: Boolean = false) {
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                  pointProvider =
                    LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(points)),
                  pointConnector = LineCartesianLayer.PointConnector.cubic(),
                  dataLabel = if (withDataLabels) rememberTextComponent() else null,
                )
              ),
              pointSpacing = pointSpacingDp.dp,
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0),
            ),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(WIDTH_DP.dp),
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
  }

  private fun setColumnChart(stacked: Boolean) {
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberColumnCartesianLayer(
              ColumnCartesianLayer.ColumnProvider.series(columnA, columnB),
              mergeMode = { _: ExtraStore ->
                if (stacked) ColumnCartesianLayer.MergeMode.Stacked
                else ColumnCartesianLayer.MergeMode.Grouped()
              },
              dataLabel = rememberTextComponent(),
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 20.0),
            ),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(WIDTH_DP.dp),
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
  }

  private fun push(block: CartesianChartModelProducer.Transaction.() -> Unit) {
    runBlocking { producer.runTransaction(block = block) }
    composeRule.awaitFrame(recorder)
  }

  private fun drawnPoints() = points.centers.distinct().sortedBy { it.first }

  @Test
  fun `a small line set draws one point per entry spaced by the point spacing`() {
    setLineChart()
    push { lineSeries { series(x = listOf(0, 1, 2, 3), y = listOf(25, 50, 75, 100)) } }

    val drawn = drawnPoints()
    assertEquals(4, drawn.size)
    val spacing = recorder.last!!.xSpacing
    drawn.zipWithNext { a, b -> assertEquals(spacing, b.first - a.first, 0.5f) }
  }

  @Test
  fun `point heights follow the y values against the drawn range`() {
    setLineChart()
    push { lineSeries { series(x = listOf(0, 1, 2), y = listOf(0, 50, 100)) } }

    val (bottom, middle, top) = drawnPoints().map { it.second }
    assertTrue(top < middle && middle < bottom)
    assertEquals((bottom + top) / 2, middle, 1f)
  }

  @Test
  fun `gaps in x keep their proportional spacing`() {
    setLineChart()
    push { lineSeries { series(x = listOf(0, 1, 4), y = listOf(10, 20, 30)) } }

    val drawn = drawnPoints()
    val spacing = recorder.last!!.xSpacing
    assertEquals(3 * spacing, drawn[2].first - drawn[1].first, 0.5f)
  }

  @Test
  fun `a large line set draws only the points inside the visible window`() {
    setLineChart()
    push { lineSeries { series((0 until 400).map { 40 + it % 30 }) } }

    val frame = recorder.last!!
    val visible = drawnPoints()
    assertTrue(visible.size in 2 until 400)
    assertTrue(visible.all { it.first >= frame.layerLeft - frame.xSpacing })
    assertTrue(visible.all { it.first <= frame.layerRight + frame.xSpacing })
    val pointSpacingPx = with(composeRule.density) { (POINT_SPACING_DP + POINT_SIZE_DP).dp.toPx() }
    assertEquals(pointSpacingPx * frame.zoom, frame.xSpacing, 0.5f)
  }

  @Test
  fun `data labels do not change point placement`() {
    setLineChart(withDataLabels = true)
    push { lineSeries { series(x = listOf(0, 1, 2), y = listOf(10, 20, 30)) } }

    assertEquals(3, drawnPoints().size)
  }

  @Test
  fun `grouped columns sit side by side at the column thickness`() {
    setColumnChart(stacked = false)
    push {
      columnSeries {
        series(x = listOf(0, 1), y = listOf(5, 10))
        series(x = listOf(0, 1), y = listOf(8, 4))
      }
    }

    val a = columnA.verticals.distinct().sortedBy { it.x }
    val b = columnB.verticals.distinct().sortedBy { it.x }
    assertEquals(2, a.size)
    assertEquals(2, b.size)
    assertTrue(b[0].x > a[0].x)
    assertTrue(abs(b[0].x - a[0].x) >= COLUMN_DP)
    assertTrue(a[1].bottom - a[1].top > a[0].bottom - a[0].top)
  }

  @Test
  fun `stacked columns share an x and stack their heights`() {
    setColumnChart(stacked = true)
    push {
      columnSeries {
        series(x = listOf(0), y = listOf(5))
        series(x = listOf(0), y = listOf(8))
      }
    }

    val a = columnA.verticals.distinct().single()
    val b = columnB.verticals.distinct().single()
    assertEquals(a.x, b.x, 0.01f)
    assertEquals(a.top, b.bottom, 0.5f)
    val unit = (a.bottom - a.top) / 5
    assertEquals(8 * unit, b.bottom - b.top, 0.5f)
  }

  @Test
  fun `negative columns hang below the zero line`() {
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberColumnCartesianLayer(ColumnCartesianLayer.ColumnProvider.series(columnA)),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    push { columnSeries { series(x = listOf(0, 1), y = listOf(-5, 5)) } }

    val (negative, positive) = columnA.verticals.distinct().sortedBy { it.x }
    assertEquals(positive.bottom, negative.top, 0.5f)
  }

  private companion object {
    const val POINT_SPACING_DP = 24f
    const val POINT_SIZE_DP = 8f
    const val WIDTH_DP = 320
    const val COLUMN_DP = 8f
  }
}
