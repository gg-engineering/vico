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


package com.patrykandpatrick.vico.compose.pie

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.captureWindow
import com.patrykandpatrick.vico.compose.cartesian.createChartRule
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.LegendItem
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.rememberVerticalLegend
import com.patrykandpatrick.vico.compose.pie.data.PieChartModelProducer
import com.patrykandpatrick.vico.compose.pie.data.PieValueFormatter
import com.patrykandpatrick.vico.compose.pie.data.pieSeries
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PieChartFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = PieChartModelProducer()
  private val labels = CopyOnWriteArrayList<Pair<Int, Float>>()
  private var bounds = android.graphics.Rect()

  private val recordingFormatter = PieValueFormatter { context, value, index ->
    labels += index to value
    PieValueFormatter.Value.format(context, value, index)
  }

  private fun slices(label: PieChart.SliceLabel? = null) =
    PieChart.SliceProvider.series(
      PieChart.Slice(fill = Fill(RED), label = label),
      PieChart.Slice(fill = Fill(GREEN), label = label),
      PieChart.Slice(fill = Fill(BLUE), label = label),
    )

  private fun setPie(
    innerSize: () -> PieSize.Inner = { PieSize.Inner.Zero },
    spacing: () -> Int = { 0 },
    label: () -> PieChart.SliceLabel? = { null },
    withLegend: Boolean = false,
  ) {
    composeRule.setContent {
      val legend =
        rememberVerticalLegend<PieChartMeasuringContext, PieChartDrawingContext>(
          items = { add(LegendItem(ShapeComponent(Fill(RED)), TextComponent(), "Weight")) }
        )
      PieChartHost(
        chart =
          rememberPieChart(
            sliceProvider = slices(label()),
            innerSize = innerSize(),
            spacing = spacing().dp,
            valueFormatter = recordingFormatter,
            legend = if (withLegend) legend else null,
          ),
        modelProducer = producer,
        modifier = Modifier.size(SIZE_DP.dp).testTag(TAG),
        animationSpec = null,
        placeholder = { BasicText(PLACEHOLDER) },
      )
    }
  }

  private fun push(vararg values: Number) {
    runBlocking { producer.runTransaction { pieSeries { series(*values) } } }
    composeRule.mainClock.advanceTimeByFrame()
  }

  private fun capture(): Bitmap {
    val bitmap = composeRule.captureWindow()
    val node = composeRule.onNodeWithTag(TAG).fetchSemanticsNode().boundsInWindow
    bounds = android.graphics.Rect(node.left.toInt(), node.top.toInt(), node.right.toInt(), node.bottom.toInt())
    return bitmap
  }

  private fun Bitmap.at(dx: Float, dy: Float): Int =
    getPixel((bounds.exactCenterX() + dx * bounds.width() / 2).toInt(), (bounds.exactCenterY() + dy * bounds.height() / 2).toInt())

  @Test
  fun `slices take their share of the circle clockwise from the top`() {
    setPie()
    push(2, 1, 1)

    val pie = capture()

    assertEquals(RED.toArgb(), pie.at(0.5f, 0f))
    assertEquals(GREEN.toArgb(), pie.at(-0.4f, 0.4f))
    assertEquals(BLUE.toArgb(), pie.at(-0.4f, -0.4f))
  }

  @Test
  fun `new values redraw the slices in the new proportions`() {
    setPie()
    push(2, 1, 1)
    capture()

    push(1, 1, 2)
    val pie = capture()

    assertEquals(RED.toArgb(), pie.at(0.4f, -0.4f))
    assertEquals(GREEN.toArgb(), pie.at(0.4f, 0.4f))
    assertEquals(BLUE.toArgb(), pie.at(-0.5f, 0f))
  }

  @Test
  fun `a donut leaves its centre empty`() {
    setPie(innerSize = { PieSize.Inner.fixed(40.dp) })
    push(1, 1, 1)

    val pie = capture()

    assertNotEquals(RED.toArgb(), pie.at(0f, 0f))
    assertNotEquals(GREEN.toArgb(), pie.at(0f, 0f))
    assertNotEquals(BLUE.toArgb(), pie.at(0f, 0f))
    assertEquals(RED.toArgb(), pie.at(0.6f, -0.1f))
  }

  @Test
  fun `spaced slices leave a gap on every boundary`() {
    setPie(spacing = { 12 })
    push(1, 1)

    val pie = capture()

    assertNotEquals(RED.toArgb(), pie.at(0f, -0.6f))
    assertNotEquals(GREEN.toArgb(), pie.at(0f, -0.6f))
    assertEquals(RED.toArgb(), pie.at(0.5f, 0f))
  }

  @Test
  fun `spaced donut slices keep parallel edges`() {
    setPie(innerSize = { PieSize.Inner.fixed(40.dp) }, spacing = { 8 })
    push(3, 1)

    val pie = capture()

    assertEquals(RED.toArgb(), pie.at(0.6f, 0f))
    assertEquals(GREEN.toArgb(), pie.at(-0.55f, -0.3f))
  }

  @Test
  fun `inside labels format every slice's value`() {
    setPie(label = { PieChart.SliceLabel.Inside() })
    push(5, 3, 2)

    capture()

    assertEquals(listOf(0 to 5f, 1 to 3f, 2 to 2f), labels.distinct().sortedBy { it.first })
  }

  @Test
  fun `outside labels format every slice and leave the slices drawn`() {
    setPie(label = { PieChart.SliceLabel.Outside() })
    push(5, 3, 2)

    val pie = capture()

    assertEquals(listOf(0, 1, 2), labels.map { it.first }.distinct().sorted())
    assertEquals(RED.toArgb(), pie.at(0.3f, 0f))
  }

  @Test
  fun `a pie with no model shows the placeholder`() {
    setPie()

    composeRule.onNodeWithText(PLACEHOLDER).assertExists()

    push(1, 2)
    capture()
    composeRule.onNodeWithText(PLACEHOLDER).assertDoesNotExist()
  }

  @Test
  fun `a legend shrinks the pie to make room`() {
    setPie(withLegend = true)
    push(1, 1, 1)

    val pie = capture()

    assertEquals(BLUE.toArgb(), pie.at(-0.3f, -0.3f))
    assertTrue(pie.at(0f, 0.98f) != RED.toArgb() || pie.at(0f, -0.98f) != RED.toArgb())
  }

  @Test
  fun `a fixed model renders without a producer`() {
    composeRule.setContent {
      Box(Modifier.wrapContentSize()) {
        PieChartHost(
          chart = rememberPieChart(sliceProvider = slices()),
          model = com.patrykandpatrick.vico.compose.pie.data.PieChartModel.build(1, 3),
          modifier = Modifier.size(SIZE_DP.dp).testTag(TAG),
        )
      }
    }

    val pie = capture()

    assertEquals(GREEN.toArgb(), pie.at(-0.5f, 0f))
  }

  @Test
  fun `negative spacing is rejected`() {
    val error = runCatching { setPie(spacing = { -1 }) }.exceptionOrNull()

    assertTrue("$error", error is IllegalArgumentException)
  }

  private companion object {
    const val TAG = "pie"
    const val PLACEHOLDER = "No data"
    const val SIZE_DP = 200
    val RED = Color(0xFFFF0000)
    val GREEN = Color(0xFF00FF00)
    val BLUE = Color(0xFF0000FF)
  }
}
