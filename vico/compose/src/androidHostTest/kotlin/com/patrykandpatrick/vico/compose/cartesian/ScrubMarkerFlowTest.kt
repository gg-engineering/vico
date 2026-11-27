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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberScrubMarkerController
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
class ScrubMarkerFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val points = PointRecorder()
  private val markerListener = MarkerRecorder()
  private val calls = CopyOnWriteArrayList<Call>()
  private lateinit var scrollState: VicoScrollState

  private data class Call(val clickX: Double?, val targets: List<Double>, val interaction: Interaction?)

  private fun setChart(
    initialMarkerX: () -> Double? = { null },
    snap: ((Double?, List<Double>) -> Double?)? = null,
    count: Int = 4,
  ) {
    composeRule.setContent {
      scrollState = rememberVicoScrollState()
      val controller =
        rememberScrubMarkerController(
          delayMs = HOLD_MS,
          onMarkerIndexChanged =
            snap?.let { pick ->
              { clickX, targets, interaction ->
                calls += Call(clickX, targets, interaction)
                pick(clickX, targets)
              }
            },
        )
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
            marker = rememberDefaultCartesianMarker(label = rememberTextComponent()),
            markerVisibilityListener = markerListener,
            markerController = controller,
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(320.dp).testTag(TAG),
        scrollState = scrollState,
        initialAnimationSpec = null,
        animationSpec = null,
        initialMarkerX = initialMarkerX(),
      )
    }
    runBlocking {
      producer.runTransaction {
        lineSeries { series(x = (0 until count).toList(), y = (0 until count).map { 150 + it }) }
      }
    }
    composeRule.awaitFrame(recorder) { it.maxX == count - 1.0 }
    composeRule.drawChart()
  }

  private fun pointCenter(index: Int): Offset =
    points.centers.distinct().sortedBy { it.first }[index].let { (x, y) -> Offset(x, y) }

  private fun touch(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
    composeRule.onNodeWithTag(TAG).performTouchInput(block)
    composeRule.drawChart()
  }

  private fun hold() {
    composeRule.mainClock.advanceTimeBy(HOLD_MS * 2)
    composeRule.drawChart()
  }

  @Test
  fun `a tap marks the tapped entry and the mark stays after the finger lifts`() {
    setChart()
    val target = pointCenter(2)

    touch {
      down(target)
      up()
    }

    assertEquals(2.0, recorder.last!!.markerX!!, 0.0)
    assertEquals(2.0, markerListener.shown!!.single().x, 0.0)
  }

  @Test
  fun `holding then dragging moves the mark along the line without scrolling`() {
    setChart(count = 40)
    val start = pointCenter(1)
    val end = pointCenter(4)

    touch { down(start) }
    hold()
    assertEquals(1.0, recorder.last!!.markerX!!, 0.0)
    touch { moveTo(Offset((start.x + end.x) / 2, start.y)) }
    touch { moveTo(end) }
    assertEquals(4.0, recorder.last!!.markerX!!, 0.0)
    assertEquals(0f, scrollState.value, 0f)

    touch { up() }
    assertEquals(4.0, recorder.last!!.markerX!!, 0.0)
    assertEquals(0f, scrollState.value, 0f)
  }

  @Test
  fun `a horizontal swipe scrolls the chart and dismisses a mark already shown`() {
    setChart(count = 40, snap = { clickX, _ -> clickX?.let { Math.round(it).toDouble() } })
    touch {
      down(pointCenter(2))
      up()
    }
    assertEquals(2.0, recorder.last!!.markerX!!, 0.0)

    touch {
      down(Offset(250f, 100f))
      moveTo(Offset(200f, 100f))
      moveTo(Offset(100f, 100f))
      moveTo(Offset(20f, 100f))
      up()
    }
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.drawChart()

    assertTrue(scrollState.value > 0f)
    assertNull(recorder.last!!.markerX)
    assertTrue(Call(null, emptyList(), null) in calls)
    assertNull(calls.last().clickX)
  }

  @Test
  fun `a vertical drag is left to the page and neither scrolls nor marks`() {
    setChart(count = 40)

    touch {
      down(Offset(150f, 20f))
      moveTo(Offset(152f, 80f))
      moveTo(Offset(153f, 150f))
      up()
    }

    assertEquals(0f, scrollState.value, 0f)
    assertNull(recorder.last!!.markerX)
  }

  @Test
  fun `the caller picks where the mark lands from every markable x`() {
    setChart(snap = { _, _ -> 3.0 })

    touch {
      down(pointCenter(0))
      up()
    }

    assertEquals(3.0, recorder.last!!.markerX!!, 0.0)
    val tap = calls.single()
    assertEquals(listOf(0.0, 1.0, 2.0, 3.0), tap.targets)
    assertEquals(0.0, tap.clickX!!, 0.3)
    assertTrue(tap.interaction is Interaction.Tap)
  }

  @Test
  fun `a caller that answers null leaves the chart unmarked`() {
    setChart(snap = { _, _ -> null })

    touch {
      down(pointCenter(1))
      up()
    }

    assertNull(recorder.last!!.markerX)
  }

  @Test
  fun `a press that travels a little before lifting is a release, not a tap`() {
    setChart(snap = { clickX, _ -> clickX })

    touch {
      down(pointCenter(1))
      moveBy(Offset(0f, 6f))
      up()
    }

    assertTrue(calls.none { it.interaction is Interaction.Tap })
  }

  @Test
  fun `the chart opens on the caller's marker and clears when the caller clears it`() {
    var marked by mutableStateOf<Double?>(2.0)
    setChart(initialMarkerX = { marked })
    composeRule.drawChart()
    assertEquals(2.0, recorder.last!!.markerX!!, 0.0)

    marked = null
    composeRule.drawChart()
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.drawChart()

    assertNull(recorder.last!!.markerX)
  }

  private companion object {
    const val TAG = "chart"
    const val HOLD_MS = 200L
  }
}
