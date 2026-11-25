package com.patrykandpatrick.vico.compose.cartesian

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.rememberHorizontalLegend
import com.patrykandpatrick.vico.compose.common.rememberVerticalLegend
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalBox
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.core.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.LegendItem
import com.patrykandpatrick.vico.core.common.Position
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import java.util.concurrent.CopyOnWriteArrayList
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
class DecorationFlowTest {
  @get:Rule val composeRule = createChartRule()

  private val producer = CartesianChartModelProducer()
  private val recorder = FrameRecorder()
  private val goalLine = RecordingLineComponent()
  private val goalBox = BoxRecorder()
  private val goalKey = ExtraStore.Key<Double>()

  private fun setChart(decorations: List<Decoration>) {
    composeRule.setContent {
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = 150.0, maxY = 190.0)
            ),
            decorations = decorations + recorder,
          ),
        modelProducer = producer,
        animationSpec = null,
        animateIn = false,
      )
    }
  }

  private fun push(goal: Double = 175.0) {
    runBlocking {
      producer.runTransaction {
        lineSeries { series(182.4, 181.0, 179.2, 178.8) }
        extras { it[goalKey] = goal }
      }
    }
    composeRule.awaitFrame(recorder)
  }

  private fun DrawnFrame.canvasY(y: Double): Float =
    layerBottom - ((y - minY) / (maxY - minY)).toFloat() * (layerBottom - layerTop)

  @Test
  fun `a goal line is drawn at the goal weight`() {
    setChart(listOf(HorizontalLine(y = { 175.0 }, line = goalLine)))
    push()

    val frame = recorder.last!!
    assertEquals(frame.canvasY(175.0), goalLine.horizontalYs.last(), 0.5f)
  }

  @Test
  fun `a goal line read from the model moves when the goal changes`() {
    setChart(listOf(HorizontalLine(y = { it[goalKey] }, line = goalLine)))
    push(goal = 160.0)
    val first = goalLine.horizontalYs.last()

    goalLine.clear()
    push(goal = 185.0)

    val frame = recorder.last!!
    assertEquals(frame.canvasY(185.0), goalLine.horizontalYs.last(), 0.5f)
    assertTrue(goalLine.horizontalYs.last() < first)
  }

  @Test
  fun `a goal line label sits on the line at every label position`() {
    composeRule.setContent {
      val label = rememberTextComponent()
      val decorations = remember {
        Position.Vertical.entries.flatMap { vertical ->
          Position.Horizontal.entries.map { horizontal ->
            HorizontalLine(
              y = { 189.0 },
              line = goalLine,
              labelComponent = label,
              horizontalLabelPosition = horizontal,
              verticalLabelPosition = vertical,
            )
          }
        }
      }
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = 150.0, maxY = 190.0)
            ),
            decorations = decorations + recorder,
          ),
        modelProducer = producer,
        animationSpec = null,
        animateIn = false,
      )
    }
    push()

    val frame = recorder.last!!
    assertTrue(goalLine.horizontalYs.size >= 9)
    assertTrue(goalLine.horizontalYs.all { kotlin.math.abs(it - frame.canvasY(189.0)) < 0.5f })
  }

  @Test
  fun `a goal range box spans from the low goal to the high goal`() {
    composeRule.setContent {
      val label = rememberTextComponent()
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(
              rangeProvider = CartesianLayerRangeProvider.fixed(minY = 150.0, maxY = 190.0)
            ),
            decorations =
              Position.Vertical.entries.map {
                HorizontalBox(
                  y = { 160.0..170.0 },
                  box = goalBox,
                  labelComponent = label,
                  verticalLabelPosition = it,
                )
              } + recorder,
          ),
        modelProducer = producer,
        animationSpec = null,
        animateIn = false,
      )
    }
    push()

    val frame = recorder.last!!
    val (_, top, _, bottom) = goalBox.boxes.last()
    assertEquals(frame.canvasY(170.0), top, 0.5f)
    assertEquals(frame.canvasY(160.0), bottom, 0.5f)
  }

  @Test
  fun `a persistent marker shows on its entry without a touch`() {
    val labels = CopyOnWriteArrayList<String>()
    composeRule.setContent {
      val marker =
        rememberDefaultCartesianMarker(
          label = rememberTextComponent(),
          valueFormatter =
            remember {
              DefaultCartesianMarker.ValueFormatter { context, targets ->
                DefaultCartesianMarker.ValueFormatter.default().format(context, targets).also {
                  labels += it.toString()
                }
              }
            },
          labelPosition = DefaultCartesianMarker.LabelPosition.AbovePoint,
          indicator = { ShapeComponent(fill = Fill(it.toArgb())) },
        )
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(),
            decorations = listOf(recorder),
            persistentMarkers = { marker at 2 },
          ),
        modelProducer = producer,
        animationSpec = null,
        animateIn = false,
      )
    }
    push()

    assertEquals("179.2", labels.last())
  }

  @Test
  fun `a vertical legend reserves room below the chart`() {
    var showLegend by mutableStateOf(false)
    composeRule.setContent {
      val label = rememberTextComponent()
      val icon = rememberShapeComponent()
      val legend =
        rememberVerticalLegend<CartesianMeasuringContext, CartesianDrawingContext>(
          items = {
            add(LegendItem(icon, label, "Weight"))
            add(LegendItem(icon, label, "Goal"))
          }
        )
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(),
            decorations = listOf(recorder),
            legend = if (showLegend) legend else null,
          ),
        modelProducer = producer,
        animationSpec = null,
        animateIn = false,
      )
    }
    push()
    val withoutLegend = recorder.last!!.layerBottom

    showLegend = true
    composeRule.drawChart()

    assertTrue(recorder.last!!.layerBottom < withoutLegend)
  }

  @Test
  fun `a horizontal legend with more items wraps into more rows`() {
    var itemCount by mutableIntStateOf(1)
    composeRule.setContent {
      val label = rememberTextComponent()
      val icon = rememberShapeComponent()
      val count = itemCount
      CartesianChartHost(
        chart =
          rememberCartesianChart(
            rememberLineCartesianLayer(),
            decorations = listOf(recorder),
            legend =
              rememberHorizontalLegend(
                items = { repeat(count) { add(LegendItem(icon, label, "Series $it")) } }
              ),
          ),
        modelProducer = producer,
        animationSpec = null,
        animateIn = false,
      )
    }
    push()
    val oneRow = recorder.last!!.layerBottom

    itemCount = 12
    composeRule.drawChart()

    assertTrue(recorder.last!!.layerBottom < oneRow)
  }
}
