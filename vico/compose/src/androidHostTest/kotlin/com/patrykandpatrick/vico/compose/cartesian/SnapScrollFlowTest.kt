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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapScrollFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val snapCalls = CopyOnWriteArrayList<List<Any?>>()
  private lateinit var scrollState: VicoScrollState

  private fun setChart(xSnapStep: Double? = null, config: SnapBehaviorConfig? = null) {
    composeRule.setContent {
      scrollState = rememberVicoScrollState(xSnapStep = xSnapStep)
      CartesianChartHost(
        chart =
          rememberCartesianChart(rememberLineCartesianLayer(), decorations = listOf(recorder)),
        modelProducer = producer,
        modifier = Modifier.width(320.dp).testTag(TAG),
        scrollState = scrollState,
        initialAnimationSpec = null,
        animationSpec = null,
        flingBehavior = config?.let { rememberChartSnapFlingBehavior(scrollState, it) },
      )
    }
    runBlocking {
      producer.runTransaction { lineSeries { series((0 until 120).map { 70 + it % 7 }) } }
    }
    composeRule.awaitFrame(recorder) { it.maxX == 119.0 }
  }

  private fun swipe(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
    composeRule.onNodeWithTag(TAG).performTouchInput(block)
    composeRule.settle(recorder, frames = 120)
  }

  private fun slowDrag() = swipe {
    down(Offset(250f, 100f))
    repeat(20) {
      advanceEventTime(50)
      moveBy(Offset(-5f, 0f))
    }
    advanceEventTime(200)
    up()
  }

  @Test
  fun `a fling lands on a whole snap step`() {
    setChart(xSnapStep = 7.0)

    swipe { swipeLeft(durationMillis = 100) }

    val x = checkNotNull(scrollState.scrollValueToX())
    assertTrue("scrolled to $x", x > 0.0)
    assertEquals(0.0, x - Math.round(x / 7.0) * 7.0, 0.05)
  }

  @Test
  fun `a slow drag is snapped by the caller as a drag`() {
    setChart(
      config =
        SnapBehaviorConfig(
          snapToLabel = { current, projected, isDrag, isForward ->
            snapCalls += listOf(current, projected, isDrag, isForward)
            Math.ceil(checkNotNull(current) / 7) * 7
          },
          animation = SnapBehaviorConfig.SnapAnimation(snapDurationMillis = 100),
        )
    )

    slowDrag()

    val (current, projected, isDrag, isForward) = snapCalls.single()
    assertEquals(true, isDrag)
    assertEquals(false, isForward)
    assertEquals(current, projected)
    val target = Math.ceil((current as Double) / 7) * 7
    val landed = checkNotNull(scrollState.xToScrollValueBehindStartInset(target))
    assertEquals(landed, scrollState.value, 1f)
  }

  @Test
  fun `a fling projects ahead before the caller picks the boundary`() {
    setChart(
      config =
        SnapBehaviorConfig(
          snapToLabel = { current, projected, isDrag, isForward ->
            snapCalls += listOf(current, projected, isDrag, isForward)
            28.0
          },
          animation =
            SnapBehaviorConfig.SnapAnimation(decayFrictionMultiplier = 0f, snapDurationMillis = 100),
        )
    )

    swipe { swipeLeft(durationMillis = 80) }

    val (current, projected, isDrag) = snapCalls.single()
    assertEquals(false, isDrag)
    assertTrue("$projected > $current", (projected as Double) > (current as Double))
    assertEquals(checkNotNull(scrollState.xToScrollValueBehindStartInset(28.0)), scrollState.value, 1f)
  }

  @Test
  fun `without a snap callback a fling keeps its own momentum`() {
    setChart(config = SnapBehaviorConfig())

    swipe { swipeLeft(durationMillis = 100) }

    assertTrue(scrollState.value > 0f)
  }

  private companion object {
    const val TAG = "chart"
  }
}
