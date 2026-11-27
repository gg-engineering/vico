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
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ToggleMarkerFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val points = PointRecorder()

  private fun setChart() {
    composeRule.setContent {
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
            markerController = CartesianMarkerController.rememberToggleOnTap(),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(320.dp).testTag(TAG),
        initialAnimationSpec = null,
        animationSpec = null,
      )
    }
    runBlocking {
      producer.runTransaction { lineSeries { series(x = listOf(0, 1, 2), y = listOf(60, 61, 62)) } }
    }
    composeRule.awaitFrame(recorder) { it.maxX == 2.0 }
    composeRule.drawChart()
  }

  private fun point(index: Int): Offset =
    points.centers.distinct().sortedBy { it.first }[index].let { (x, y) -> Offset(x, y) }

  private fun touch(block: TouchInjectionScope.() -> Unit) {
    composeRule.onNodeWithTag(TAG).performTouchInput(block)
    composeRule.drawChart()
  }

  @Test
  fun `toggle on tap marks a point and a second tap on it clears the mark`() {
    setChart()
    val target = point(1)

    touch { click(target) }
    assertEquals(1.0, recorder.last!!.markerX!!, 0.0)

    touch { click(target) }
    assertNull(recorder.last!!.markerX)
  }

  private companion object {
    const val TAG = "chart"
  }
}
