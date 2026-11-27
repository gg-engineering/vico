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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScrollZoomFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private lateinit var scrollState: VicoScrollState
  private lateinit var zoomState: VicoZoomState
  private lateinit var scope: CoroutineScope

  private fun setChart(
    initialScroll: Scroll.Absolute = Scroll.Absolute.Start,
    autoScrollCondition: AutoScrollCondition = AutoScrollCondition.Never,
    scrollEnabled: Boolean = true,
    initialZoom: Zoom = Zoom.fixed(),
    minZoom: Zoom = Zoom.fixed(MIN_ZOOM),
    maxZoom: Zoom = Zoom.fixed(MAX_ZOOM),
    restorationTester: StateRestorationTester? = null,
  ) {
    val content: @androidx.compose.runtime.Composable () -> Unit = {
      scope = rememberCoroutineScope()
      scrollState =
        rememberVicoScrollState(
          scrollEnabled = scrollEnabled,
          initialScroll = initialScroll,
          autoScrollCondition = autoScrollCondition,
          autoScrollAnimationSpec = androidx.compose.animation.core.snap(),
        )
      zoomState =
        rememberVicoZoomState(initialZoom = initialZoom, minZoom = minZoom, maxZoom = maxZoom)
      CartesianChartHost(
        chart =
          rememberCartesianChart(rememberLineCartesianLayer(), decorations = listOf(recorder)),
        modelProducer = producer,
        modifier = Modifier.width(WIDTH_DP.dp).testTag(TAG),
        scrollState = scrollState,
        zoomState = zoomState,
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    if (restorationTester != null) restorationTester.setContent(content)
    else composeRule.setContent(content)
  }

  private fun pushPoints(count: Int) {
    runBlocking {
      producer.runTransaction { lineSeries { series((1..count).map { 70 + it % 5 }) } }
    }
    composeRule.awaitFrame(recorder) { it.maxX == count - 1.0 }
    composeRule.drawChart()
  }

  private fun onMain(block: suspend () -> Unit) {
    composeRule.runOnIdle { scope.launch { block() } }
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.drawChart()
  }

  @Test
  fun `a long series starts at the first point and can scroll to the last`() {
    setChart()
    pushPoints(LONG)

    assertEquals(0f, scrollState.value, 0f)
    assertTrue(scrollState.maxValue > 0f)
    assertEquals(0f, recorder.last!!.scroll, 0f)
  }

  @Test
  fun `an end initial scroll opens on the latest point`() {
    setChart(initialScroll = Scroll.Absolute.End)
    pushPoints(LONG)

    assertTrue(scrollState.maxValue > 0f)
    assertEquals(scrollState.maxValue, scrollState.value, 0f)
  }

  @Test
  fun `scrolling to an x value lands that point at the start of the chart`() {
    setChart()
    pushPoints(LONG)

    onMain { scrollState.scroll(Scroll.Absolute.x(10.0)) }

    val frame = recorder.last!!
    assertEquals(10 * frame.xSpacing, scrollState.value, 1f)
  }

  @Test
  fun `scrolling past either end stops at the edge`() {
    setChart()
    pushPoints(LONG)

    onMain { scrollState.scroll(Scroll.Relative.pixels(1_000_000f)) }
    assertEquals(scrollState.maxValue, scrollState.value, 0f)

    onMain { scrollState.scroll(Scroll.Relative.pixels(-1_000_000f)) }
    assertEquals(0f, scrollState.value, 0f)
  }

  @Test
  fun `an animated relative scroll moves by the requested x distance`() {
    setChart()
    pushPoints(LONG)

    onMain { scrollState.animateScroll(Scroll.Relative.x(3.0)) }
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.drawChart()

    assertEquals(3 * recorder.last!!.xSpacing, scrollState.value, 1f)
  }

  @Test
  fun `new data scrolls to the latest point when the model grows`() {
    setChart(
      autoScrollCondition = AutoScrollCondition.OnModelGrowth,
      initialScroll = Scroll.Absolute.End,
    )
    pushPoints(LONG)
    val firstMax = scrollState.maxValue

    pushPoints(LONG + 20)
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.drawChart()

    assertTrue(scrollState.maxValue > firstMax)
    assertEquals(scrollState.maxValue, scrollState.value, 1f)
  }

  @Test
  fun `new data does not move the chart when auto scroll is off`() {
    setChart()
    pushPoints(LONG)
    pushPoints(LONG + 20)
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.drawChart()

    assertEquals(0f, scrollState.value, 0f)
  }

  @Test
  fun `a short series fits without scrolling`() {
    setChart()
    pushPoints(3)

    assertEquals(0f, scrollState.maxValue, 0f)
  }

  @Test
  fun `a swipe scrolls the chart towards later points`() {
    setChart()
    pushPoints(LONG)

    composeRule.onNodeWithTag(TAG).performTouchInput { swipeLeft() }
    composeRule.drawChart()

    assertTrue(scrollState.value > 0f)
  }

  @Test
  fun `a swipe does nothing when scrolling is disabled`() {
    setChart(scrollEnabled = false)
    pushPoints(LONG)

    composeRule.onNodeWithTag(TAG).performTouchInput { swipeLeft() }
    composeRule.drawChart()

    assertEquals(0f, scrollState.value, 0f)
  }

  @Test
  fun `zoom stays inside its limits`() {
    setChart()
    pushPoints(LONG)
    assertEquals(MIN_ZOOM..MAX_ZOOM, zoomState.valueRange)
    val baseSpacing = recorder.last!!.xSpacing

    onMain { zoomState.zoom(Zoom.fixed(10f)) }
    assertEquals(MAX_ZOOM, zoomState.value, 0f)
    assertEquals(baseSpacing * MAX_ZOOM, recorder.last!!.xSpacing, 0.5f)

    onMain { zoomState.zoom(Zoom.fixed(0.01f)) }
    assertEquals(MIN_ZOOM, zoomState.value, 0f)
  }

  @Test
  fun `content zoom fits every point on screen`() {
    setChart(initialZoom = Zoom.Content, minZoom = Zoom.Content, maxZoom = Zoom.fixed(MAX_ZOOM))
    pushPoints(LONG)

    assertTrue(zoomState.value < 1f)
    assertEquals(0f, scrollState.maxValue, 0.5f)
  }

  @Test
  fun `a pinch zooms the chart in`() {
    setChart()
    pushPoints(LONG)
    val before = zoomState.value

    composeRule.onNodeWithTag(TAG).performTouchInput {
      pinch(
        start0 = center - Offset(20f, 0f),
        end0 = center - Offset(120f, 0f),
        start1 = center + Offset(20f, 0f),
        end1 = center + Offset(120f, 0f),
      )
    }
    composeRule.drawChart()

    assertNotEquals(before, zoomState.value)
    assertTrue(zoomState.value > before)
  }

  @Test
  fun `scroll and zoom are restored after the screen is recreated`() {
    val tester = StateRestorationTester(composeRule)
    setChart(restorationTester = tester)
    pushPoints(LONG)
    onMain { scrollState.scroll(Scroll.Relative.pixels(120f)) }
    onMain { zoomState.zoom(Zoom.fixed(1.5f)) }
    val scroll = scrollState.value
    val zoom = zoomState.value

    tester.emulateSavedInstanceStateRestore()
    composeRule.drawChart()

    assertEquals(scroll, scrollState.value, 0f)
    assertEquals(zoom, zoomState.value, 0f)
  }

  private companion object {
    const val TAG = "chart"
    const val WIDTH_DP = 300
    const val LONG = 60
    const val MIN_ZOOM = 0.5f
    const val MAX_ZOOM = 2f
  }
}
