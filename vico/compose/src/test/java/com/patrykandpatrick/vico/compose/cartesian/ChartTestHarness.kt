package com.patrykandpatrick.vico.compose.cartesian

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.core.common.DrawingContext
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.component.Component
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.component.ShapeComponent
import java.util.concurrent.CopyOnWriteArrayList

internal typealias ChartRule = AndroidComposeTestRule<*, ComponentActivity>

internal fun createChartRule(): ChartRule = createAndroidComposeRule<ComponentActivity>()

private const val TIMEOUT_MS = 30_000L

/** Robolectric does not run draw passes on its own, so the window is drawn into a bitmap. */
internal fun ChartRule.drawChart() {
  waitForIdle()
  runOnUiThread {
    val view = activity.window.decorView
    view.draw(Canvas(Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)))
  }
  waitForIdle()
}

internal fun ChartRule.awaitFrame(
  recorder: FrameRecorder,
  predicate: (DrawnFrame) -> Boolean = { true },
): DrawnFrame {
  waitUntil(TIMEOUT_MS) {
    mainClock.advanceTimeByFrame()
    drawChart()
    recorder.last?.let(predicate) == true
  }
  return recorder.last!!
}

internal data class DrawnFrame(
  val minX: Double,
  val maxX: Double,
  val xStep: Double,
  val minY: Double,
  val maxY: Double,
  val layerLeft: Float,
  val layerRight: Float,
  val layerTop: Float,
  val layerBottom: Float,
  val xSpacing: Float,
  val scroll: Float,
  val zoom: Float,
)

internal class FrameRecorder : Decoration {
  val frames = CopyOnWriteArrayList<DrawnFrame>()

  val last: DrawnFrame?
    get() = frames.lastOrNull()

  override fun drawOverLayers(context: CartesianDrawingContext) {
    with(context) {
      val yRange = ranges.getYRange(null)
      frames +=
        DrawnFrame(
          minX = ranges.minX,
          maxX = ranges.maxX,
          xStep = ranges.xStep,
          minY = yRange.minY,
          maxY = yRange.maxY,
          layerLeft = layerBounds.left,
          layerRight = layerBounds.right,
          layerTop = layerBounds.top,
          layerBottom = layerBounds.bottom,
          xSpacing = layerDimensions.xSpacing,
          scroll = scroll,
          zoom = zoom,
        )
    }
  }
}

internal class RecordingFormatter(
  private val delegate: CartesianValueFormatter = CartesianValueFormatter.decimal()
) : CartesianValueFormatter {
  val labels = CopyOnWriteArrayList<Pair<Double, String>>()
  val drawnLabels = CopyOnWriteArrayList<Pair<Double, String>>()

  val values: List<Double>
    get() = labels.map { it.first }.distinct().sorted()

  val drawnValues: List<Double>
    get() = drawnLabels.map { it.first }.distinct().sorted()

  fun clear() {
    labels.clear()
    drawnLabels.clear()
  }

  override fun format(
    context: CartesianMeasuringContext,
    value: Double,
    verticalAxisPosition: Axis.Position.Vertical?,
  ): CharSequence =
    delegate.format(context, value, verticalAxisPosition).also {
      labels += value to it.toString()
      if (context is CartesianDrawingContext) drawnLabels += value to it.toString()
    }
}

internal class RecordingLineComponent(thicknessDp: Float = 2f) :
  LineComponent(Fill.Black, thicknessDp) {
  val horizontalYs = CopyOnWriteArrayList<Float>()
  val verticals = CopyOnWriteArrayList<VerticalStroke>()

  data class VerticalStroke(val x: Float, val top: Float, val bottom: Float, val factor: Float)

  fun clear() {
    horizontalYs.clear()
    verticals.clear()
  }

  override fun drawHorizontal(
    context: DrawingContext,
    left: Float,
    right: Float,
    y: Float,
    thicknessFactor: Float,
  ) {
    horizontalYs += y
    super.drawHorizontal(context, left, right, y, thicknessFactor)
  }

  override fun drawVertical(
    context: DrawingContext,
    x: Float,
    top: Float,
    bottom: Float,
    thicknessFactor: Float,
  ) {
    verticals += VerticalStroke(x, top, bottom, thicknessFactor)
    super.drawVertical(context, x, top, bottom, thicknessFactor)
  }

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = System.identityHashCode(this)
}

internal class BoxRecorder : ShapeComponent() {
  val boxes = CopyOnWriteArrayList<List<Float>>()

  override fun draw(context: DrawingContext, left: Float, top: Float, right: Float, bottom: Float) {
    boxes += listOf(left, top, right, bottom)
  }

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = System.identityHashCode(this)
}

internal class PointRecorder : Component {
  val centers = CopyOnWriteArrayList<Pair<Float, Float>>()

  override fun draw(context: DrawingContext, left: Float, top: Float, right: Float, bottom: Float) {
    centers += (left + right) / 2 to (top + bottom) / 2
  }
}

internal class MarkerRecorder : CartesianMarkerVisibilityListener {
  @Volatile var shown: List<CartesianMarker.Target>? = null
  @Volatile var hidden = false

  override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
    shown = targets
    hidden = false
  }

  override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
    shown = targets
  }

  override fun onHidden(marker: CartesianMarker) {
    hidden = true
  }
}
