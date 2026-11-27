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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.ColumnCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarkerFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val points = PointRecorder()
  private val markerListener = MarkerRecorder()
  private val markerLabels = CopyOnWriteArrayList<String>()
  private val markerGuideline = RecordingLineComponent()
  private val columns = RecordingLineComponent(thicknessDp = 8f)

  private fun setLineChart(scrollEnabled: Boolean = true) {
    composeRule.setContent {
      val formatter = remember {
        val default = DefaultCartesianMarker.ValueFormatter.default(colorCode = false)
        DefaultCartesianMarker.ValueFormatter { context, targets ->
          default.format(context, targets).also { markerLabels += it.toString() }
        }
      }
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                  pointProvider =
                    LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(points))
                )
              )
            ),
            marker =
              rememberDefaultCartesianMarker(
                label = rememberTextComponent(),
                valueFormatter = formatter,
                guideline = markerGuideline,
              ),
            markerVisibilityListener = markerListener,
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(320.dp).testTag(TAG),
        scrollState = rememberVicoScrollState(scrollEnabled = scrollEnabled),
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
  }

  private fun push(block: CartesianChartModelProducer.Transaction.() -> Unit) {
    runBlocking { producer.runTransaction(block = block) }
    composeRule.awaitFrame(recorder)
  }

  private fun pointCenter(index: Int): Offset =
    points.centers.distinct()[index].let { (x, y) -> Offset(x, y) }

  @Test
  fun `pressing a point shows the marker on that entry with its value`() {
    setLineChart()
    push { lineSeries { series(x = listOf(0, 1, 2, 3), y = listOf(181.2, 180.4, 179.9, 180.1)) } }
    val target = pointCenter(2)

    composeRule.onNodeWithTag(TAG).performTouchInput { down(target) }
    composeRule.drawChart()

    val shown = markerListener.shown!!.single() as LineCartesianLayerMarkerTarget
    assertEquals(2.0, shown.x, 0.0)
    assertEquals(target.x, shown.canvasX, 0.5f)
    assertEquals(179.9, shown.points.single().entry.y, 0.0)
    assertEquals("179.9", markerLabels.last())
    assertTrue(markerGuideline.verticals.any { kotlin.math.abs(it.x - target.x) < 0.5f })
  }

  @Test
  fun `releasing the press hides the marker`() {
    setLineChart()
    push { lineSeries { series(70, 71, 72) } }
    val chart = composeRule.onNodeWithTag(TAG)

    chart.performTouchInput { down(pointCenter(1)) }
    composeRule.drawChart()
    assertEquals(1.0, markerListener.shown!!.single().x, 0.0)

    chart.performTouchInput { up() }
    composeRule.drawChart()
    assertTrue(markerListener.hidden)
  }

  @Test
  fun `pressing between two points picks the nearer one`() {
    setLineChart()
    push { lineSeries { series(x = listOf(0, 1, 2), y = listOf(60, 61, 62)) } }
    val first = pointCenter(0)
    val second = pointCenter(1)
    val nearSecond = Offset(first.x + (second.x - first.x) * 0.8f, first.y)

    composeRule.onNodeWithTag(TAG).performTouchInput { down(nearSecond) }
    composeRule.drawChart()

    assertEquals(1.0, markerListener.shown!!.single().x, 0.0)
  }

  @Test
  fun `dragging with scrolling disabled moves the marker to the next entry`() {
    setLineChart(scrollEnabled = false)
    push { lineSeries { series(x = listOf(0, 1, 2), y = listOf(60, 61, 62)) } }

    val start = pointCenter(0)
    val end = pointCenter(2)
    composeRule.onNodeWithTag(TAG).performTouchInput { down(start) }
    composeRule.drawChart()
    assertEquals(0.0, markerListener.shown!!.single().x, 0.0)
    composeRule.onNodeWithTag(TAG).performTouchInput { moveTo(end) }
    composeRule.drawChart()

    assertEquals(2.0, markerListener.shown!!.single().x, 0.0)
    assertEquals("62", markerLabels.last())
  }

  @Test
  fun `no press means no marker`() {
    setLineChart()
    push { lineSeries { series(70, 71, 72) } }

    assertNull(markerListener.shown)
  }

  @Test
  fun `pressing a column shows its entry`() {
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberColumnCartesianLayer(ColumnCartesianLayer.ColumnProvider.series(columns)),
            marker = rememberDefaultCartesianMarker(label = rememberTextComponent()),
            markerVisibilityListener = markerListener,
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(320.dp).testTag(TAG),
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    push { columnSeries { series(x = listOf(0, 1, 2), y = listOf(5, 9, 4)) } }
    val column = columns.verticals.distinctBy { it.x }[1]

    composeRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(column.x, (column.top + column.bottom) / 2))
    }
    composeRule.drawChart()

    val target = markerListener.shown!!.single() as ColumnCartesianLayerMarkerTarget
    assertEquals(1.0, target.x, 0.0)
    assertEquals(9.0, target.columns.single().entry.y, 0.0)
  }

  @Test
  fun `pressing the second column of a group marks that column, not the first`() {
    val second = RecordingLineComponent(thicknessDp = 8f)
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberColumnCartesianLayer(
              ColumnCartesianLayer.ColumnProvider.series(columns, second)
            ),
            marker = rememberDefaultCartesianMarker(label = rememberTextComponent()),
            markerVisibilityListener = markerListener,
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(320.dp).testTag(TAG),
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    push {
      columnSeries {
        series(x = listOf(0, 1), y = listOf(5, 9))
        series(x = listOf(0, 1), y = listOf(3, 7))
      }
    }
    val column = second.verticals.distinctBy { it.x }[1]

    composeRule.onNodeWithTag(TAG).performTouchInput {
      down(Offset(column.x, (column.top + column.bottom) / 2))
    }
    composeRule.drawChart()

    val target = markerListener.shown!!.single() as ColumnCartesianLayerMarkerTarget
    assertEquals(1.0, target.x, 0.0)
    assertEquals(column.x, target.canvasX, 1f)
    assertEquals(1.0, recorder.last!!.markerX!!, 0.0)
  }

  private companion object {
    const val TAG = "chart"
  }
}
