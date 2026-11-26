package com.patrykandpatrick.vico.compose.cartesian

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DataToChartFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val xLabels = RecordingFormatter()

  private fun setLineChart(rangeProvider: CartesianLayerRangeProvider? = null) {
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            if (rangeProvider == null) rememberLineCartesianLayer()
            else rememberLineCartesianLayer(rangeProvider = rangeProvider),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = xLabels),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(300.dp),
        animationSpec = null,
        animateIn = false,
        placeholder = { BasicText(PLACEHOLDER) },
      )
    }
  }

  private fun push(block: CartesianChartModelProducer.Transaction.() -> Unit) {
    recorder.frames.clear()
    runBlocking { producer.runTransaction(block) }
  }

  private fun awaitFrame(predicate: (DrawnFrame) -> Boolean): DrawnFrame =
    composeRule.awaitFrame(recorder, predicate)

  @Test
  fun `adding a series draws the range of its data`() {
    setLineChart()
    push { lineSeries { series(x = listOf(0, 1, 2, 3), y = listOf(182.4, 181.9, 180.6, 181.2)) } }

    val frame = awaitFrame { it.maxX == 3.0 }

    assertEquals(0.0, frame.minX, 0.0)
    assertEquals(1.0, frame.xStep, 0.0)
    assertEquals(0.0, frame.minY, 0.0)
    assertEquals(190.0, frame.maxY, 0.0)
  }

  @Test
  fun `replacing the series redraws with the new range`() {
    setLineChart()
    push { lineSeries { series(x = listOf(0, 1, 2), y = listOf(70.1, 70.4, 69.8)) } }
    awaitFrame { it.maxX == 2.0 }

    push { lineSeries { series(x = listOf(10, 12, 14, 16), y = listOf(68.2, 68.0, 67.9, 67.5)) } }
    val frame = awaitFrame { it.maxX == 16.0 }

    assertEquals(10.0, frame.minX, 0.0)
    assertEquals(2.0, frame.xStep, 0.0)
    assertEquals(69.0, frame.maxY, 0.0)
  }

  @Test
  fun `adding a second series widens the range to cover both`() {
    setLineChart()
    push {
      lineSeries {
        series(x = listOf(0, 1, 2), y = listOf(150, 151, 152))
        series(x = listOf(1, 2, 5), y = listOf(140, 160, 155))
      }
    }

    val frame = awaitFrame { it.maxX == 5.0 }

    assertEquals(0.0, frame.minX, 0.0)
    assertEquals(160.0, frame.maxY, 0.0)
  }

  @Test
  fun `an empty series is rejected and the chart keeps the last good data`() {
    setLineChart()
    push { lineSeries { series(70.2, 70.4) } }
    awaitFrame { it.maxX == 1.0 }

    val error =
      runCatching { push { lineSeries { series(emptyList<Number>()) } } }.exceptionOrNull()
    recorder.frames.clear()
    composeRule.drawChart()

    assertTrue("$error", error is IllegalArgumentException)
    assertEquals(1.0, recorder.last!!.maxX, 0.0)
  }

  @Test
  fun `clearing the data shows the placeholder instead of a chart`() {
    setLineChart()
    push { lineSeries { series(1, 2, 3) } }
    awaitFrame { it.maxX == 2.0 }

    push {}

    composeRule.waitUntil(10_000L) {
      composeRule.onAllNodesWithText(PLACEHOLDER).fetchSemanticsNodes().size == 1
    }
    composeRule.onNodeWithText(PLACEHOLDER).assertExists()
  }

  @Test
  fun `a week window of seven days draws exactly seven day labels`() {
    setLineChart(CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = 6.0))
    push { lineSeries { series(x = listOf(0, 2, 3, 6), y = listOf(80.2, 80.0, 79.6, 79.9)) } }

    val frame = awaitFrame { it.maxX == 6.0 }

    assertEquals(6.0, frame.maxX - frame.minX, 0.0)
    assertEquals((0..6).map(Int::toDouble), xLabels.values)
  }

  @Test
  fun `a week window keeps its seven days when the data covers only part of it`() {
    setLineChart(CartesianLayerRangeProvider.fixed(minX = 7.0, maxX = 13.0))
    push { lineSeries { series(x = listOf(9, 10), y = listOf(80.2, 80.0)) } }

    val frame = awaitFrame { it.maxX == 13.0 }

    assertEquals(7.0, frame.minX, 0.0)
    assertEquals((7..13).map(Int::toDouble), xLabels.values)
  }

  @Test
  fun `a month window of thirty days does not spill into the next month`() {
    setLineChart(CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = 29.0))
    push { lineSeries { series(x = (0..29 step 3).toList(), y = (0..29 step 3).map { 70 }) } }

    val frame = awaitFrame { it.maxX == 29.0 }

    assertEquals(29.0, frame.maxX - frame.minX, 0.0)
    assertTrue(xLabels.values.all { it in 0.0..29.0 })
  }

  @Test
  fun `a column layer and a line layer share one x range`() {
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberColumnCartesianLayer(),
            rememberLineCartesianLayer(),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = null,
        animateIn = false,
      )
    }
    push {
      columnSeries { series(x = listOf(0, 1, 2), y = listOf(3, 4, 5)) }
      lineSeries { series(x = listOf(2, 3, 4), y = listOf(8, 9, 10)) }
    }

    val frame = awaitFrame { it.maxX == 4.0 }

    assertEquals(0.0, frame.minX, 0.0)
    assertEquals(10.0, frame.maxY, 0.0)
  }

  private companion object {
    const val PLACEHOLDER = "no data"
  }
}
