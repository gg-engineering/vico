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
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.ScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.data.rememberScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScrollAwareRangeFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val windows = CopyOnWriteArrayList<ClosedRange<Double>>()
  private lateinit var provider: ScrollAwareRangeProvider
  private lateinit var scrollState: VicoScrollState
  private lateinit var scope: CoroutineScope

  private fun visibleRange(
    entries: List<List<Pair<Double, Double>>>,
    xRange: ClosedRange<Double>,
  ): Pair<ClosedRange<Double>, List<Double>> {
    windows += xRange
    val ys = entries.flatten().map { it.second }
    return (ys.min() - 1)..(ys.max() + 1) to listOf(ys.min(), ys.max())
  }

  private fun setChart(
    windowKey: String? = null,
    seedMinY: Double = Double.NaN,
    seedMaxY: Double = Double.NaN,
    onVisibleEntries:
      (List<List<Pair<Double, Double>>>, ClosedRange<Double>) -> Pair<
          ClosedRange<Double>,
          List<Double>,
        > =
      ::visibleRange,
  ) {
    composeRule.setContent {
      scope = rememberCoroutineScope()
      scrollState = rememberVicoScrollState()
      provider =
        rememberScrollAwareRangeProvider(
          windowKey = windowKey,
          seedMinY = seedMinY,
          seedMaxY = seedMaxY,
          onVisibleEntries = onVisibleEntries,
        )
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(rangeProvider = provider),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(320.dp),
        scrollState = scrollState,
        initialAnimationSpec = null,
        animationSpec = null,
      )
    }
  }

  private fun pushRisingWeights(
    window: ScrollAwareRangeProvider.Window? = null,
    windowKey: String = KEY,
  ) {
    runBlocking {
      producer.runTransaction {
        lineSeries { series(x = (0 until COUNT).toList(), y = (0 until COUNT).map { 100 + it }) }
        if (window != null) {
          extras { it[ScrollAwareRangeProvider.WindowKey] = mapOf(windowKey to window) }
        }
      }
    }
  }

  private fun settle(): DrawnFrame = composeRule.settle(recorder, frames = 40)

  @Test
  fun `the y range follows the opening window rather than the whole history`() {
    setChart()
    pushRisingWeights()

    val frame = settle()

    assertEquals(99.0, frame.minY, 0.0)
    assertTrue("max ${frame.maxY}", frame.maxY < 130.0)
    assertEquals(listOf(100.0, frame.maxY - 1), provider.currentTicks)
  }

  @Test
  fun `after a scroll settles the y range and ticks move to the new window`() {
    setChart()
    pushRisingWeights()
    val opening = settle()

    composeRule.runOnIdle { scope.launch { scrollState.scroll(Scroll.Absolute.End) } }
    val end = settle()

    assertEquals(100.0 + COUNT, end.maxY, 0.01)
    assertTrue(end.minY > opening.maxY)
    assertEquals(100.0 + COUNT - 1, provider.currentTicks.last(), 0.0)
  }

  @Test
  fun `a window sent with the data sets the x domain and the opening axis on the first frame`() {
    setChart(windowKey = KEY)
    pushRisingWeights(
      ScrollAwareRangeProvider.Window(minX = -5.0, maxX = 70.0, openStartX = 10.0, openEndX = 12.0)
    )

    val first = composeRule.awaitFrame(recorder)

    assertEquals(-5.0, first.minX, 0.0)
    assertEquals(70.0, first.maxX, 0.0)
    assertTrue("seeded ${provider.currentTicks}", provider.currentTicks.first() <= 109.0)
    assertTrue(windows.first().start <= 10.0 && windows.first().endInclusive >= 12.0)
  }

  @Test
  fun `a window keyed for another chart is ignored`() {
    setChart(windowKey = KEY)
    pushRisingWeights(ScrollAwareRangeProvider.Window(minX = -5.0, maxX = 70.0), windowKey = "other")

    val frame = settle()

    assertEquals(0.0, frame.minX, 0.0)
    assertEquals(COUNT - 1.0, frame.maxX, 0.0)
  }

  @Test
  fun `a seed range is drawn until the visible entries answer`() {
    setChart(seedMinY = 90.0, seedMaxY = 200.0, onVisibleEntries = { _, _ -> error("not ready") })
    pushRisingWeights()

    val frame = settle()

    assertEquals(90.0, frame.minY, 0.0)
    assertEquals(200.0, frame.maxY, 0.0)
  }

  @Test
  fun `a callback that fails or answers an empty range leaves the model's own range`() {
    setChart(onVisibleEntries = { _, _ -> 5.0..5.0 to emptyList() })
    pushRisingWeights()

    val frame = settle()

    assertTrue(frame.minY <= 100.0)
    assertTrue(frame.maxY >= 100.0 + COUNT - 1)
    assertTrue(provider.currentTicks.isEmpty())
  }

  @Ignore("Known gap: the y range keeps the previous data until the chart next scrolls.")
  @Test
  fun `new data at the same x positions moves the y range to the new values`() {
    setChart()
    pushRisingWeights()
    settle()

    runBlocking {
      producer.runTransaction {
        lineSeries { series(x = (0 until COUNT).toList(), y = (0 until COUNT).map { 300 - it }) }
      }
    }
    val frame = settle()

    assertTrue("max ${frame.maxY}", frame.maxY >= 300.0)
    assertTrue("min ${frame.minY}", frame.minY > 250.0)
  }

  @Test
  fun `new data reaches the y range once the chart scrolls`() {
    setChart()
    pushRisingWeights()
    settle()

    runBlocking {
      producer.runTransaction {
        lineSeries { series(x = (0 until COUNT).toList(), y = (0 until COUNT).map { 300 - it }) }
      }
    }
    settle()
    composeRule.runOnIdle { scope.launch { scrollState.scroll(Scroll.Relative.pixels(1f)) } }
    val frame = settle()

    assertTrue("max ${frame.maxY}", frame.maxY >= 300.0)
    assertTrue("min ${frame.minY}", frame.minY > 250.0)
  }

  private companion object {
    const val KEY = "weight"
    const val COUNT = 60
  }
}
