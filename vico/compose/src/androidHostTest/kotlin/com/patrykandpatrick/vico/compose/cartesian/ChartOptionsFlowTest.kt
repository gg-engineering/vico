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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.layer.absolute
import com.patrykandpatrick.vico.compose.cartesian.layer.absoluteRelative
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberCandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.BaseAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.candlestickSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.CandlestickCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CandlestickCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.DashedShape
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChartOptionsFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val points = PointRecorder()

  private fun pushWeights(vararg y: Number) {
    runBlocking { producer.runTransaction { lineSeries { series(y.toList()) } } }
    composeRule.awaitFrame(recorder)
  }

  private fun layerEdgesFor(content: @Composable (Int) -> Unit, variants: Int): List<DrawnFrame> {
    var variant by mutableStateOf(0)
    composeRule.setContent { content(variant) }
    pushWeights(181.2, 180.4, 179.9, 180.1)
    return (0 until variants).map { index ->
      variant = index
      composeRule.drawChart()
      recorder.last!!
    }
  }

  @Composable
  private fun WeightChart(
    startAxis: VerticalAxis<Axis.Position.Vertical.Start>? = null,
    bottomAxis: HorizontalAxis<Axis.Position.Horizontal.Bottom>? = null,
    marker: DefaultCartesianMarker? = null,
  ) {
    CartesianChartHost(
      chart =
        rememberCartesianChart(
          rememberLineCartesianLayer(),
          startAxis = startAxis,
          bottomAxis = bottomAxis,
          marker = marker,
          decorations = listOf(recorder),
          persistentMarkers = marker?.let { m -> { m at 1 } },
        ),
      modelProducer = producer,
      modifier = Modifier.width(WIDTH_DP.dp).height(300.dp),
      animationSpec = null,
      initialAnimationSpec = null,
    )
  }

  @Test
  fun `a fixed or fractional axis size sets the room the axis takes`() {
    val (fixed, fraction) =
      layerEdgesFor(
        { variant ->
          WeightChart(
            startAxis =
              VerticalAxis.rememberStart(
                size =
                  if (variant == 0) BaseAxis.Size.Fixed(60.dp) else BaseAxis.Size.Fraction(0.25f)
              )
          )
        },
        variants = 2,
      )

    assertEquals(60f, fixed.layerLeft, 1f)
    assertEquals(WIDTH_DP * 0.25f, fraction.layerLeft, 1f)
  }

  @Test
  fun `a text-sized axis is as wide as its widest sample label`() {
    val (short, long) =
      layerEdgesFor(
        { variant ->
          WeightChart(
            startAxis =
              VerticalAxis.rememberStart(
                size = BaseAxis.Size.Text(if (variant == 0) "9" else "188.88 lb")
              )
          )
        },
        variants = 2,
      )

    assertTrue(long.layerLeft > short.layerLeft)
  }

  @Test
  fun `inside labels and no title take less room than outside labels with a title`() {
    val (inside, outside) =
      layerEdgesFor(
        { variant ->
          WeightChart(
            startAxis =
              VerticalAxis.rememberStart(
                horizontalLabelPosition =
                  if (variant == 0) VerticalAxis.HorizontalLabelPosition.Inside
                  else VerticalAxis.HorizontalLabelPosition.Outside,
                titleComponent = if (variant == 0) null else rememberTextComponent(),
                title = { if (variant == 0) null else "Weight (lb)" },
              )
          )
        },
        variants = 2,
      )

    assertTrue(outside.layerLeft > inside.layerLeft)
  }

  @Test
  fun `rotated bottom labels and a title take more height`() {
    val (flat, rotated) =
      layerEdgesFor(
        { variant ->
          WeightChart(
            bottomAxis =
              HorizontalAxis.rememberBottom(
                labelRotationDegrees = if (variant == 0) 0f else 90f,
                valueFormatter = { _, value, _ -> "Day ${value.toInt()}" },
                titleComponent = if (variant == 0) null else rememberTextComponent(),
                title = { if (variant == 0) null else "Date" },
              )
          )
        },
        variants = 2,
      )

    assertTrue(rotated.layerBottom < flat.layerBottom)
  }

  @Test
  fun `aligned spacing and offset pick which days get a tick and label`() {
    val ticks = RecordingLineComponent()
    var offset by mutableStateOf(0)
    composeRule.setContent {
      val placer =
        remember(offset) { HorizontalAxis.ItemPlacer.aligned(spacing = { 2 }, offset = { offset }) }
      WeightChart(bottomAxis = HorizontalAxis.rememberBottom(tick = ticks, itemPlacer = placer))
    }
    pushWeights(1, 2, 3, 4, 5, 6, 7)
    val xSpacing = recorder.last!!.xSpacing
    val everyOther = ticks.verticals.map { it.x }.distinct().sorted()

    ticks.clear()
    offset = 1
    composeRule.drawChart()
    val shifted = ticks.verticals.map { it.x }.distinct().sorted()

    assertEquals(4, everyOther.size)
    everyOther.zipWithNext { a, b -> assertEquals(2 * xSpacing, b - a, 1f) }
    assertEquals(3, shifted.size)
    assertEquals(everyOther[0] + xSpacing, shifted[0], 1f)
  }

  @Test
  fun `segmented placement puts a guideline on each segment edge`() {
    val guideline = RecordingLineComponent()
    composeRule.setContent {
      WeightChart(
        bottomAxis =
          HorizontalAxis.rememberBottom(
            guideline = guideline,
            itemPlacer = remember { HorizontalAxis.ItemPlacer.segmented() },
          )
      )
    }
    pushWeights(1, 2, 3, 4)

    assertEquals(5, guideline.verticals.map { it.x }.distinct().size)
  }

  @Test
  fun `a count placer draws the requested number of y labels`() {
    val labels = RecordingFormatter()
    composeRule.setContent {
      WeightChart(
        startAxis =
          VerticalAxis.rememberStart(
            valueFormatter = labels,
            itemPlacer = remember { VerticalAxis.ItemPlacer.count({ 3 }) },
          )
      )
    }
    pushWeights(150, 170, 190)

    assertEquals(3, labels.values.size)
  }

  @Test
  fun `a chart given a model directly draws that model's range`() {
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(rememberLineCartesianLayer(), decorations = listOf(recorder)),
        model =
          CartesianChartModel(
            LineCartesianLayerModel.build { series(x = listOf(3, 4, 5), y = listOf(7, 8, 9)) }
          ),
      )
    }
    composeRule.drawChart()

    assertEquals(3.0, recorder.last!!.minX, 0.0)
    assertEquals(5.0, recorder.last!!.maxX, 0.0)
  }

  @Test
  fun `a model update animates points from the old values to the new ones`() {
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
              ),
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0),
            ),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing),
        initialAnimationSpec = null,
      )
    }
    pushWeights(20, 20)
    composeRule.mainClock.advanceTimeBy(1_000)
    composeRule.drawChart()
    val startY = points.centers.last().second

    composeRule.mainClock.autoAdvance = false
    runBlocking { producer.runTransaction { lineSeries { series(80, 80) } } }
    composeRule.mainClock.advanceTimeBy(200)
    points.centers.clear()
    composeRule.drawChart()
    val midY = points.centers.last().second
    composeRule.mainClock.advanceTimeBy(1_000)
    points.centers.clear()
    composeRule.drawChart()
    val endY = points.centers.last().second

    assertTrue("$startY > $midY > $endY", startY > midY && midY > endY)
  }

  @Test
  fun `a right-to-left layout draws the first entry on the right`() {
    composeRule.setContent {
      CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
              startAxis = VerticalAxis.rememberStart(),
              bottomAxis = HorizontalAxis.rememberBottom(),
              decorations = listOf(recorder),
            ),
          modelProducer = producer,
          animationSpec = null,
          initialAnimationSpec = null,
        )
      }
    }
    runBlocking {
      producer.runTransaction { lineSeries { series(x = listOf(0, 1, 2), y = listOf(1, 2, 3)) } }
    }
    composeRule.awaitFrame(recorder)

    val drawn = points.centers.distinct()
    val first = drawn.first { it.second == drawn.maxOf { p -> p.second } }
    val last = drawn.first { it.second == drawn.minOf { p -> p.second } }
    assertTrue(first.first > last.first)
  }

  @Test
  fun `line styling never moves the points`() {
    var styled by mutableStateOf(false)
    composeRule.setContent {
      val plain =
        LineCartesianLayer.rememberLine(
          pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(points))
        )
      val fancy =
        LineCartesianLayer.rememberLine(
          fill =
            LineCartesianLayer.LineFill.double(
              topFill = Fill(Color.Green),
              bottomFill =
                Fill(Brush.verticalGradient(listOf(Color.Blue, Color.Blue.copy(alpha = 0.2f)))),
            ),
          stroke = LineCartesianLayer.LineStroke.Dashed(),
          areaFill =
            LineCartesianLayer.AreaFill.single(
              Fill(Brush.verticalGradient(listOf(Color.Blue, Color.Transparent)))
            ),
          pointProvider = LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(points)),
          pointConnector = LineCartesianLayer.PointConnector.cubic(),
          dataLabel = rememberTextComponent(),
        )
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              LineCartesianLayer.LineProvider.series(if (styled) fancy else plain),
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = -10.0, maxY = 10.0),
            ),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    pushWeights(-5, 3, 8, -2)
    val plainPoints = points.centers.distinct().takeLast(4)

    styled = true
    points.centers.clear()
    composeRule.drawChart()

    assertEquals(plainPoints, points.centers.distinct().takeLast(4))
  }

  @Test
  fun `marker labels above the chart make room at the top`() {
    val (around, top) =
      layerEdgesFor(
        { variant ->
          WeightChart(
            marker =
              rememberDefaultCartesianMarker(
                label =
                  rememberTextComponent(
                    background =
                      rememberShapeComponent(
                        shape = MarkerCornerBasedShape(RoundedCornerShape(25))
                      )
                  ),
                labelPosition =
                  if (variant == 0) DefaultCartesianMarker.LabelPosition.AroundPoint
                  else DefaultCartesianMarker.LabelPosition.Top,
                guideline = rememberLineComponent(shape = DashedShape()),
              )
          )
        },
        variants = 2,
      )

    assertTrue(top.layerTop > around.layerTop)
  }

  @Test
  fun `every marker label position draws on the marked entry`() {
    val labels = CopyOnWriteArrayList<String>()
    var position by mutableStateOf(DefaultCartesianMarker.LabelPosition.Top)
    composeRule.setContent {
      WeightChart(
        marker =
          rememberDefaultCartesianMarker(
            label = rememberTextComponent(),
            labelPosition = position,
            valueFormatter =
              remember {
                DefaultCartesianMarker.ValueFormatter { context, targets ->
                  DefaultCartesianMarker.ValueFormatter.default().format(context, targets).also {
                    labels += it.toString()
                  }
                }
              },
          )
      )
    }
    pushWeights(181.2, 180.4, 179.9, 180.1)
    DefaultCartesianMarker.LabelPosition.entries.forEach {
      labels.clear()
      position = it
      composeRule.drawChart()
      assertEquals("$it", "180.4", labels.last())
    }
  }

  @Test
  fun `candles are drawn bullish or bearish from their open and close`() {
    val bullish = RecordingLineComponent(thicknessDp = 6f)
    val bearish = RecordingLineComponent(thicknessDp = 6f)
    val neutral = RecordingLineComponent(thicknessDp = 6f)
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberCandlestickCartesianLayer(
              CandlestickCartesianLayer.CandleProvider.absolute(
                bullish = CandlestickCartesianLayer.Candle(bullish),
                neutral = CandlestickCartesianLayer.Candle(neutral),
                bearish = CandlestickCartesianLayer.Candle(bearish),
              )
            ),
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    runBlocking {
      producer.runTransaction {
        candlestickSeries(
          opening = listOf(10, 14, 12),
          closing = listOf(14, 11, 12),
          low = listOf(9, 10, 11),
          high = listOf(15, 15, 13),
        )
      }
    }
    composeRule.awaitFrame(recorder)

    assertEquals(1, bullish.verticals.map { it.x }.distinct().size)
    assertEquals(1, bearish.verticals.map { it.x }.distinct().size)
    assertEquals(1, neutral.verticals.map { it.x }.distinct().size)
    assertEquals(15.0, recorder.last!!.maxY, 0.0)
  }

  @Test
  fun `pressing a candle marks its opening and closing prices`() {
    val listener = MarkerRecorder()
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberCandlestickCartesianLayer(
              CandlestickCartesianLayer.CandleProvider.absoluteRelative(),
              scaleCandleWicks = true,
            ),
            marker = rememberDefaultCartesianMarker(label = rememberTextComponent()),
            markerVisibilityListener = listener,
            decorations = listOf(recorder),
          ),
        modelProducer = producer,
        modifier = Modifier.width(WIDTH_DP.dp).testTag(TAG),
        animationSpec = null,
        initialAnimationSpec = null,
      )
    }
    runBlocking {
      producer.runTransaction {
        candlestickSeries(
          opening = listOf(10, 14, 12, 12),
          closing = listOf(14, 11, 12, 15),
          low = listOf(9, 10, 11, 11),
          high = listOf(15, 15, 13, 16),
        )
      }
    }
    val frame = composeRule.awaitFrame(recorder)
    val secondCandleX = frame.layerLeft + frame.xSpacing * 1.5f

    composeRule.onNodeWithTag(TAG).performTouchInput { down(Offset(secondCandleX, centerY)) }
    composeRule.drawChart()

    val target = listener.shown!!.single() as CandlestickCartesianLayerMarkerTarget
    assertEquals(1.0, target.x, 0.0)
    assertEquals(14.0, target.entry.opening, 0.0)
    assertEquals(11.0, target.entry.closing, 0.0)
    assertTrue(target.openingCanvasY < target.closingCanvasY)
  }

  private companion object {
    const val WIDTH_DP = 300
    const val TAG = "chart"
  }
}
