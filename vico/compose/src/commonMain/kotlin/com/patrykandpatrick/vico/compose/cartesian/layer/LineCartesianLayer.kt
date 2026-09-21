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

package com.patrykandpatrick.vico.compose.cartesian.layer

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.ColorScale
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartRanges
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerDrawingModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.MutableCartesianChartRanges
import com.patrykandpatrick.vico.compose.cartesian.data.ScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.forEachIn
import com.patrykandpatrick.vico.compose.cartesian.getVisibleXRange
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.Interpolator.Companion.catmullRom
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.Line
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer.PointConnector
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.MutableLineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.common.Defaults
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import com.patrykandpatrick.vico.compose.common.EmptyPaint
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.ValueWrapper
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.data.CacheStore
import com.patrykandpatrick.vico.compose.common.data.CartesianLayerDrawingModelInterpolator
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.data.MutableExtraStore
import com.patrykandpatrick.vico.compose.common.doubled
import com.patrykandpatrick.vico.compose.common.getBitmap
import com.patrykandpatrick.vico.compose.common.getPixel
import com.patrykandpatrick.vico.compose.common.getRepeating
import com.patrykandpatrick.vico.compose.common.getStart
import com.patrykandpatrick.vico.compose.common.getValue
import com.patrykandpatrick.vico.compose.common.half
import com.patrykandpatrick.vico.compose.common.inBounds
import com.patrykandpatrick.vico.compose.common.orZero
import com.patrykandpatrick.vico.compose.common.saveLayer
import com.patrykandpatrick.vico.compose.common.setValue
import com.patrykandpatrick.vico.compose.common.vicoTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the content of line charts.
 *
 * @property lineProvider provides the [Line]s.
 * @property pointSpacing the point spacing.
 * @property rangeProvider overrides the _x_ and _y_ ranges.
 * @property verticalAxisPosition the position of the [VerticalAxis] with which the
 *   [LineCartesianLayer] should be associated. Use this for independent [CartesianLayer] scaling.
 * @property drawingModelInterpolator interpolates the [LineCartesianLayerDrawingModel]s.
 */
@Stable
public open class LineCartesianLayer
protected constructor(
  protected val lineProvider: LineProvider,
  protected val pointSpacing: Dp = Defaults.POINT_SPACING.dp,
  protected val rangeProvider: CartesianLayerRangeProvider = CartesianLayerRangeProvider.auto(),
  protected val verticalAxisPosition: Axis.Position.Vertical? = null,
  protected val drawingModelInterpolator:
  CartesianLayerDrawingModelInterpolator<
    LineCartesianLayerDrawingModel.Entry,
    LineCartesianLayerDrawingModel,
    > =
    CartesianLayerDrawingModelInterpolator.default(),
  protected val drawingModelKey: ExtraStore.Key<LineCartesianLayerDrawingModel>,
  /** Transforms Y values at draw time. Receives full series, Y range, and visible X range.
   *  Returns DoubleArray of transformed Y values (same size as series), or null for no transform. */
  protected val yTransform: ((
    series: List<LineCartesianLayerModel.Entry>,
    yRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = null,
) : BaseCartesianLayer<LineCartesianLayerModel>() {
  // Internal accessors for CartesianChartHost scroll-aware range support.
  internal val internalRangeProvider: CartesianLayerRangeProvider get() = rangeProvider
  internal val internalVerticalAxisPosition: Axis.Position.Vertical? get() = verticalAxisPosition

  // yTransform — called ONLY on series change or when animation target changes.
  // Not during scroll. Not during yRange animation frames.
  //
  // Scroll: stale rawY + stable yRange = constant position.
  // Animation start: recompute with TARGET yRange + fade correction.
  // Animation frames: stale rawY + animated yRange = natural animation (synced with primary).
  private var transformCacheResult: DoubleArray? = null
  private var transformIndexMap: Map<Double, Int>? = null
  private var transformLastSeriesKey: Long = 0L
  private var transformLastTargetKey: Long = 0L  // tracks target yRange changes
  // Fade: 0=none, 1=fading-out (old values), 2=fading-in (new values)
  private var transformFadePhase: Int = 0
  private var transformFadeStart: kotlin.time.TimeMark? = null
  internal var transformFadeOpacity: Float = 1f
    private set
  private var transformPendingResult: DoubleArray? = null
  private var transformPendingIndexMap: Map<Double, Int>? = null
  private val fadeOutDuration = 100.milliseconds
  private val fadeInDuration = 150.milliseconds


  /**
   * Defines the appearance of a line in a line chart.
   *
   * @property fill draws the line fill.
   * @property stroke defines the style of the stroke.
   * @property areaFill draws the area fill.
   * @property pointProvider provides the [Point]s.
   * @property interpolator interpolates between the line’s points, defining its shape.
   * @property dataLabel used for the data labels.
   * @property dataLabelPosition the vertical position of the data labels relative to the points.
   * @property dataLabelValueFormatter formats the data-label values.
   * @property dataLabelRotationDegrees the data-label rotation (in degrees).
   */
  public open class Line(
    protected val fill: LineFill,
    public val stroke: LineStroke = LineStroke.Continuous(),
    protected val areaFill: AreaFill? = null,
    public val pointProvider: PointProvider? = null,
    public val interpolator: Interpolator = Interpolator.Sharp,
    public val dataLabel: TextComponent? = null,
    public val dataLabelPosition: Position.Vertical = Position.Vertical.Top,
    public val dataLabelValueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
    public val dataLabelRotationDegrees: Float = 0f,
  ) {
    /** Creates a [Line] with a [PointConnector]. */
    @Suppress("DEPRECATION")
    @Deprecated("Use the constructor with `interpolator`.")
    public constructor(
      fill: LineFill,
      stroke: LineStroke = LineStroke.Continuous(),
      areaFill: AreaFill? = null,
      pointProvider: PointProvider? = null,
      pointConnector: PointConnector,
      dataLabel: TextComponent? = null,
      dataLabelPosition: Position.Vertical = Position.Vertical.Top,
      dataLabelValueFormatter: CartesianValueFormatter = CartesianValueFormatter.decimal(),
      dataLabelRotationDegrees: Float = 0f,
    ) : this(
      fill,
      stroke,
      areaFill,
      pointProvider,
      PointConnectorAdapter(pointConnector),
      dataLabel,
      dataLabelPosition,
      dataLabelValueFormatter,
      dataLabelRotationDegrees,
    )

    /** Connects the line’s points, defining its shape. */
    @Suppress("DEPRECATION")
    @Deprecated("Use `interpolator`.", ReplaceWith("interpolator"))
    public val pointConnector: PointConnector
      get() = (interpolator as? PointConnectorAdapter)?.pointConnector ?: PointConnector.Sharp

    protected val linePaint: Paint = Paint().apply { style = PaintingStyle.Stroke }

    /** Draws the line. */
    public fun draw(
      context: CartesianDrawingContext,
      path: Path,
      lineCanvas: Canvas,
      fillCanvas: Canvas,
      verticalAxisPosition: Axis.Position.Vertical?,
    ) {
      with(context) {
        stroke.apply(this, linePaint)
        val halfThickness = stroke.thickness.pixels.half
        areaFill?.draw(context, path, halfThickness, verticalAxisPosition)
        lineCanvas.drawPath(path, linePaint)
        withCanvas(fillCanvas) { fill.draw(context, halfThickness, verticalAxisPosition) }
      }
    }

    /** The [LineFill]’s solid [Color], or `null` if the [LineFill] has no single solid [Color]. */
    public val fillColor: Color?
      get() = (fill as? SingleLineFill)?.takeIf { it.fill.brush == null }?.fill?.color

    /** Draws the line. */
    public fun draw(
      context: CartesianDrawingContext,
      path: Path,
      color: Color,
      verticalAxisPosition: Axis.Position.Vertical?,
    ) {
      with(context) {
        stroke.apply(this, linePaint)
        val halfThickness = stroke.thickness.pixels.half
        areaFill?.draw(context, path, halfThickness, verticalAxisPosition)
        linePaint.color = color
        canvas.drawPath(path, linePaint)
      }
    }
  }

  /** Draws a [LineCartesianLayer] line’s fill. */
  public interface LineFill {
    /** Draws the line fill. */
    public fun draw(
      context: CartesianDrawingContext,
      halfLineThickness: Float,
      verticalAxisPosition: Axis.Position.Vertical?,
    )

    /** Houses [LineFill] factory functions. */
    public companion object {
      /** Uses a single [Fill]. */
      public fun single(fill: Fill): LineFill = SingleLineFill(fill)

      /** Uses a color scale. */
      public fun colorScale(
        verticalAxisPosition: Axis.Position.Vertical? = null,
        colors: ColorScaleScope.() -> Unit,
      ): LineFill =
        ColorScaleLineFill(
          ColorScale(
            colors = { extraStore -> buildColorScale(extraStore, colors) },
            verticalAxisPosition = verticalAxisPosition,
          ),
        )

      /**
       * Uses [topFill] for the portions of the line that are above the [splitY] line, and
       * analogously for [bottomFill]. (The [splitY] line is an imaginary horizontal line whose _y_
       * value is determined by [splitY].)
       */
      public fun double(
        topFill: Fill,
        bottomFill: Fill,
        splitY: (ExtraStore) -> Number = { 0 },
      ): LineFill = DoubleLineFill(topFill, bottomFill, splitY)
    }
  }

  /** Defines the style of a [LineCartesianLayer] line’s stroke. */
  @Immutable
  public sealed interface LineStroke {

    /** The stroke thickness. */
    public val thickness: Dp

    /** Applies the stroke style to [paint]. */
    public fun apply(context: CartesianDrawingContext, paint: Paint)

    /**
     * Produces a continuous stroke.
     *
     * @property cap the stroke cap.
     */
    public data class Continuous(
      override val thickness: Dp = Defaults.LINE_SPEC_THICKNESS_DP.dp,
      public val cap: StrokeCap = StrokeCap.Butt,
    ) : LineStroke {
      override fun apply(context: CartesianDrawingContext, paint: Paint) {
        with(context) {
          paint.strokeWidth = thickness.pixels
          paint.strokeCap = cap
          paint.pathEffect = null
        }
      }
    }

    /**
     * Produces a dashed stroke.
     *
     * @property cap the stroke cap.
     * @property dashLength the dash length.
     * @property gapLength the gap length.
     */
    public data class Dashed(
      public override val thickness: Dp = Defaults.LINE_SPEC_THICKNESS_DP.dp,
      public val cap: StrokeCap = StrokeCap.Butt,
      public val dashLength: Dp = Defaults.LINE_DASH_LENGTH.dp,
      public val gapLength: Dp = Defaults.LINE_GAP_LENGTH.dp,
    ) : LineStroke {
      override fun apply(context: CartesianDrawingContext, paint: Paint) {
        with(context) {
          paint.strokeWidth = thickness.pixels
          paint.strokeCap = cap
          paint.pathEffect =
            PathEffect.dashPathEffect(floatArrayOf(dashLength.pixels, gapLength.pixels), 0f)
        }
      }
    }
  }

  /** Draws a [LineCartesianLayer] line’s area fill. */
  public interface AreaFill {
    /** Draws the area fill. */
    public fun draw(
      context: CartesianDrawingContext,
      linePath: Path,
      halfLineThickness: Float,
      verticalAxisPosition: Axis.Position.Vertical?,
    )

    /** Houses [AreaFill] factory functions. */
    public companion object {
      /**
       * Uses [fill] for the areas bounded by the [LineCartesianLayer] line and the [splitY] line.
       * (The [splitY] line is an imaginary horizontal line whose _y_ value is determined by
       * [splitY].)
       */
      public fun single(fill: Fill, splitY: (ExtraStore) -> Number = { 0 }): AreaFill =
        SingleAreaFill(fill, splitY)

      /** Uses a color scale for the areas bounded by the [LineCartesianLayer] line and zero. */
      public fun colorScale(
        verticalAxisPosition: Axis.Position.Vertical? = null,
        colors: ColorScaleScope.() -> Unit,
      ): AreaFill =
        ColorScaleAreaFill(
          ColorScale(
            colors = { extraStore -> buildColorScale(extraStore, colors) },
            verticalAxisPosition = verticalAxisPosition,
          ),
        )

      /**
       * Uses [topFill] for those areas bounded by the [LineCartesianLayer] line and the [splitY]
       * line that are above the [splitY] line, and analogously for [bottomFill]. (The [splitY] line
       * is an imaginary horizontal line whose _y_ value is determined by [splitY].)
       */
      public fun double(
        topFill: Fill,
        bottomFill: Fill,
        splitY: (ExtraStore) -> Number = { 0 },
      ): AreaFill = DoubleAreaFill(topFill, bottomFill, splitY)
    }
  }

  /** Connects a [LineCartesianLayer] line’s points, defining its shape. */
  @Suppress("DEPRECATION")
  @Deprecated("Use `Interpolator`.")
  public fun interface PointConnector {
    /** Connects ([x1], [y1]) and ([x2], [y2]). */
    public fun connect(
      context: CartesianDrawingContext,
      path: Path,
      x1: Float,
      y1: Float,
      x2: Float,
      y2: Float,
    )

    /** Houses [PointConnector] singletons and factory functions. */
    public companion object {
      /** Uses line segments. */
      @Deprecated("Use `Interpolator.Sharp`.", ReplaceWith("Interpolator.Sharp"))
      public val Sharp: PointConnector = PointConnector { _, path, _, _, x2, y2 ->
        path.lineTo(x2, y2)
      }

      /**
       * Uses cubic Bézier curves. [curvature], which must be in ([0, 1]], defines their strength.
       */
      @Suppress("DEPRECATION")
      @Deprecated("Use `Interpolator.cubic`.", ReplaceWith("Interpolator.cubic()"))
      public fun cubic(
        @FloatRange(from = 0.0, to = 1.0, fromInclusive = false) curvature: Float = 0.5f,
      ): PointConnector = CubicPointConnector(curvature)
    }
  }

  /** Interpolates between a [LineCartesianLayer] line’s points, defining its shape. */
  public interface Interpolator {
    /**
     * Draws [path] through [points]. Only the points in [visibleIndexRange] need to produce path
     * operations, but the remaining points are available for use in interpolation.
     */
    public fun interpolate(
      context: CartesianDrawingContext,
      path: Path,
      points: List<Offset>,
      visibleIndexRange: IntRange,
    )

    /**
     * Returns the _y_-value range of the interpolated curve for the given [y] values. This may be
     * wider than the range of [y] if the interpolation overshoots (e.g., for splines). The default
     * implementation returns the range of [y].
     */
    public fun getYRange(y: List<Double>): ClosedRange<Double> = y.min()..y.max()

    /** Houses [Interpolator] singletons and factory functions. */
    public companion object {
      /** Uses line segments. */
      public val Sharp: Interpolator =
        object : Interpolator {
          override fun interpolate(
            context: CartesianDrawingContext,
            path: Path,
            points: List<Offset>,
            visibleIndexRange: IntRange,
          ) {
            for (index in visibleIndexRange) {
              val point = points[index]
              if (index == visibleIndexRange.first) {
                path.moveTo(point.x, point.y)
              } else {
                path.lineTo(point.x, point.y)
              }
            }
          }
        }

      /**
       * Uses cubic Bézier curves. [curvature], which must be in ([0, 1]], defines their strength.
       */
      public fun cubic(
        @FloatRange(from = 0.0, to = 1.0, fromInclusive = false) curvature: Float = 0.5f,
      ): Interpolator = CubicInterpolator(curvature)

      /**
       * Uses a Catmull–Rom spline. [alpha], which must be in [[0, 1)], controls the tightness: 0
       * (the default) produces the standard Catmull–Rom spline, and values approaching 1 produce
       * near-straight lines. Catmull–Rom splines pass through all data points and produce straight
       * segments for collinear points.
       */
      public fun catmullRom(
        @FloatRange(from = 0.0, to = 1.0, toInclusive = false) alpha: Float = 0f,
      ): Interpolator = CatmullRomInterpolator(alpha)

      /**
       * Uses monotone cubic interpolation (Fritsch-Carlson algorithm). Matches SwiftUI's
       * `.monotone` interpolation and iOS Health app charts. Unlike [catmullRom], this
       * interpolator **never overshoots** — the curve stays within the Y bounds of the data.
       * Ideal for health data (weight, blood pressure, temperature).
       */
      public fun monotone(): Interpolator = MonotoneInterpolator
    }
  }

  /** Provides [Line]s to [LineCartesianLayer]s. */
  public fun interface LineProvider {
    /** Returns the [Line] for the specified series. */
    public fun getLine(seriesIndex: Int, extraStore: ExtraStore): Line

    /** Houses [LineProvider] factory functions. */
    public companion object {
      private data class Series(private val lines: List<Line>) : LineProvider {
        override fun getLine(seriesIndex: Int, extraStore: ExtraStore) =
          lines.getRepeating(seriesIndex)
      }

      /**
       * Uses the provided [Line]s. The [Line]s and series are associated by index. If there are
       * more series than [Line]s, [lines] is iterated multiple times.
       */
      public fun series(lines: List<Line>): LineProvider = Series(lines)

      /**
       * Uses the provided [Line]s. The [Line]s and series are associated by index. If there are
       * more series than [Line]s, the [Line] list is iterated multiple times.
       */
      public fun series(vararg lines: Line): LineProvider = series(lines.toList())
    }
  }

  /**
   * Defines a point style.
   *
   * @param component the point [Component].
   * @property size the point size.
   */
  @Immutable
  public data class Point(
    private val component: Component,
    public val size: Dp = Defaults.POINT_SIZE.dp,
  ) {
    /** Draws a point at ([x], [y]). */
    public fun draw(context: CartesianDrawingContext, x: Float, y: Float) {
      val halfSize = context.run { size.pixels.half }
      component.draw(
        context = context,
        left = x - halfSize,
        top = y - halfSize,
        right = x + halfSize,
        bottom = y + halfSize,
      )
    }
  }

  /** Provides [Point]s to [LineCartesianLayer]s. */
  @Immutable
  public interface PointProvider {
    /** Returns the [Point] for the point with the given properties. */
    public fun getPoint(
      entry: LineCartesianLayerModel.Entry,
      seriesIndex: Int,
      extraStore: ExtraStore,
    ): Point?

    /** Returns the largest [Point]. */
    public fun getLargestPoint(extraStore: ExtraStore): Point?

    /** Houses a [PointProvider] factory function. */
    public companion object {
      private data class Single(private val point: Point) : PointProvider {
        override fun getPoint(
          entry: LineCartesianLayerModel.Entry,
          seriesIndex: Int,
          extraStore: ExtraStore,
        ) = point

        override fun getLargestPoint(extraStore: ExtraStore) = point
      }

      /** Uses [point] for each point. */
      public fun single(point: Point): PointProvider = Single(point)
    }
  }

  private val _markerTargets = mutableMapOf<Double, List<MutableLineCartesianLayerMarkerTarget>>()

  protected val linePath: Path = Path()

  private val srcInPaint = Paint().apply { blendMode = BlendMode.SrcIn }

  protected val cacheKeyNamespace: CacheStore.KeyNamespace = CacheStore.KeyNamespace()

  override val markerTargets: Map<Double, List<CartesianMarker.Target>> = _markerTargets

  /** When false, this layer does not produce marker targets (e.g., percentile band layers). */
  public var markerTargetsEnabled: Boolean = true

  /** When true, skip the drawing model cache and always compute line positions from the live
   *  animated Y range. Use for layers that share another layer's scroll-aware range. */
  public var alwaysUseLiveRange: Boolean = false

  /** Creates a [LineCartesianLayer]. */
  public constructor(
    lineProvider: LineProvider,
    pointSpacing: Dp = Defaults.POINT_SPACING.dp,
    rangeProvider: CartesianLayerRangeProvider = CartesianLayerRangeProvider.auto(),
    verticalAxisPosition: Axis.Position.Vertical? = null,
    drawingModelInterpolator:
    CartesianLayerDrawingModelInterpolator<
      LineCartesianLayerDrawingModel.Entry,
      LineCartesianLayerDrawingModel,
      > =
      CartesianLayerDrawingModelInterpolator.default(),
    yTransform: ((
      series: List<LineCartesianLayerModel.Entry>,
      yRange: CartesianChartRanges.YRange,
      visibleXRange: ClosedFloatingPointRange<Double>,
    ) -> DoubleArray?)? = null,
    markerTargetsEnabled: Boolean = true,
  ) : this(
    lineProvider,
    pointSpacing,
    rangeProvider,
    verticalAxisPosition,
    drawingModelInterpolator,
    ExtraStore.Key(),
    yTransform,
  ) {
    this.markerTargetsEnabled = markerTargetsEnabled
  }

  override fun drawInternal(context: CartesianDrawingContext, model: LineCartesianLayerModel) {
    with(context) {
      resetTempData()

      // When using ScrollAwareRangeProvider, skip the cached drawing model so that
      // line positions are always computed from the live (animated) yRange.
      val drawingModel = if (rangeProvider is ScrollAwareRangeProvider || alwaysUseLiveRange) {
        null
      } else {
        extraStore.getOrNull(drawingModelKey)
      }

      model.series.forEachIndexed { seriesIndex, series ->
        val pointInfoMap = drawingModel?.getOrNull(seriesIndex)

        linePath.rewind()
        val line = lineProvider.getLine(seriesIndex, model.extraStore)

        val drawingStartAlignmentCorrection =
          layoutDirectionMultiplier * layerDimensions.startPadding

        val drawingStart =
          layerBounds.getStart(isLtr = isLtr) + drawingStartAlignmentCorrection - scroll

        val points = mutableListOf<Offset>()
        val visibleIndexRange =
          collectPointsAndVisibleIndexRange(
            series = series,
            drawingStart = drawingStart,
            pointInfoMap = pointInfoMap,
            drawFullLineLength = line.stroke is LineStroke.Dashed,
            points = points,
          )

        if (points.isNotEmpty() && !visibleIndexRange.isEmpty()) {
          connectPoints(line.interpolator, points, visibleIndexRange)
        }

        saveLayer(opacity = (drawingModel?.opacity ?: 1f) * transformFadeOpacity)

        line.fillColor?.let { color ->
          line.draw(context, linePath, color, verticalAxisPosition)
          forEachPointInBounds(series, drawingStart, pointInfoMap) { entry, x, y, _, _ ->
            updateMarkerTargets(entry, x, y, color)
          }
        }
          ?: run {
            val (lineBitmap, lineCanvas) = getBitmap(cacheKeyNamespace, seriesIndex, "line")
            val (lineFillBitmap, lineFillCanvas) =
              getBitmap(cacheKeyNamespace, seriesIndex, "lineFill")
            line.draw(context, linePath, lineCanvas, lineFillCanvas, verticalAxisPosition)
            lineCanvas.drawImage(lineFillBitmap, Offset.Zero, srcInPaint)
            canvas.drawImage(lineBitmap, Offset.Zero, EmptyPaint)
            forEachPointInBounds(series, drawingStart, pointInfoMap) { entry, x, y, _, _ ->
              updateMarkerTargets(entry, x, y, lineFillBitmap)
            }
          }

        drawPointsAndDataLabels(line, series, seriesIndex, drawingStart, pointInfoMap)

        canvas.restore()
      }
    }
  }

  protected open fun CartesianDrawingContext.updateMarkerTargets(
    entry: LineCartesianLayerModel.Entry,
    canvasX: Float,
    canvasY: Float,
    lineFillBitmap: ImageBitmap,
  ) {
    if (canvasX <= layerBounds.left - 1 || canvasX >= layerBounds.right + 1) return
    val limitedCanvasY = canvasY.coerceIn(layerBounds.top, layerBounds.bottom)
    _markerTargets
      .getOrPut(entry.x) { listOf(MutableLineCartesianLayerMarkerTarget(entry.x, canvasX)) }
      .first()
      .points +=
      LineCartesianLayerMarkerTarget.Point(
        entry,
        limitedCanvasY,
        lineFillBitmap.getPixel(
          canvasX
            .roundToInt()
            .coerceIn(ceil(layerBounds.left).toInt(), layerBounds.right.toInt() - 1),
          limitedCanvasY.roundToInt(),
        ),
      )
  }

  protected open fun CartesianDrawingContext.updateMarkerTargets(
    entry: LineCartesianLayerModel.Entry,
    canvasX: Float,
    canvasY: Float,
    color: Color,
  ) {
    if (!markerTargetsEnabled) return
    if (canvasX <= layerBounds.left - 1 || canvasX >= layerBounds.right + 1) return
    val limitedCanvasY = canvasY.coerceIn(layerBounds.top, layerBounds.bottom)
    _markerTargets
      .getOrPut(entry.x) { listOf(MutableLineCartesianLayerMarkerTarget(entry.x, canvasX)) }
      .first()
      .points += LineCartesianLayerMarkerTarget.Point(entry, limitedCanvasY, color)
  }

  protected open fun CartesianDrawingContext.drawPointsAndDataLabels(
    line: Line,
    series: List<LineCartesianLayerModel.Entry>,
    seriesIndex: Int,
    drawingStart: Float,
    pointInfoMap: Map<Double, LineCartesianLayerDrawingModel.Entry>?,
  ) {
    // Points are clipped to the layer, so one at the edge is cut where the plot is cut instead of
    // being drawn whole outside it.
    //
    // forEachPointInBounds runs its action for a point and only then stops if that point was past
    // the edge, so the first one outside is always drawn — deliberately, because the line has to
    // carry on to the boundary rather than stopping at the last point inside. The line needs that;
    // the dot on top of it does not, and without a clip the dot sat beyond the plot with nothing
    // under it. Clipping rather than skipping keeps a point sliding in under a scroll revealed
    // progressively. (MOB-2953)
    canvas.save()
    canvas.clipRect(layerBounds)
    forEachPointInBounds(
      series = series,
      drawingStart = drawingStart,
      pointInfoMap = pointInfoMap,
    ) { chartEntry, x, y, previousX, nextX ->
      // A point at the edge is left out rather than clipped. Clipping a dot leaves a crescent,
      // which reads as a rendering fault rather than as something sliding in — the same reason
      // separators are excluded instead of clipped. The clip above still stands so nothing can
      // spill past the plot, but the dot itself is skipped once its centre is outside.
      val point = line.pointProvider?.getPoint(chartEntry, seriesIndex, model.extraStore)
      if (x >= layerBounds.left && x <= layerBounds.right) point?.draw(this, x, y)

      line.dataLabel
        .takeIf {
          chartEntry.x != ranges.minX && chartEntry.x != ranges.maxX ||
            chartEntry.x == ranges.minX && layerDimensions.startPadding > 0 ||
            chartEntry.x == ranges.maxX && layerDimensions.endPadding > 0
        }
        ?.let { textComponent ->
          val distanceFromLine = max(line.stroke.thickness.pixels, point?.size.orZero.pixels).half

          val text = line.dataLabelValueFormatter.format(this, chartEntry.y, verticalAxisPosition)
          val maxWidth = getMaxDataLabelWidth(chartEntry, x, previousX, nextX)
          val verticalPosition =
            line.dataLabelPosition.inBounds(
              bounds = layerBounds,
              componentHeight =
                textComponent.getHeight(
                  context = this,
                  text = text,
                  maxWidth = maxWidth,
                  rotationDegrees = line.dataLabelRotationDegrees,
                ),
              referenceY = y,
              referenceDistance = distanceFromLine,
            )
          val dataLabelY =
            y +
              when (verticalPosition) {
                Position.Vertical.Top -> -distanceFromLine
                Position.Vertical.Center -> 0f
                Position.Vertical.Bottom -> distanceFromLine
              }
          textComponent.draw(
            context = this,
            x = x,
            y = dataLabelY,
            text = text,
            verticalPosition = verticalPosition,
            maxWidth = maxWidth,
            rotationDegrees = line.dataLabelRotationDegrees,
          )
        }
    }
    canvas.restore()
  }

  protected fun CartesianDrawingContext.getMaxDataLabelWidth(
    entry: LineCartesianLayerModel.Entry,
    x: Float,
    previousX: Float?,
    nextX: Float?,
  ): Int =
    when {
      previousX != null && nextX != null -> min(abs(x - previousX), abs(nextX - x))
      previousX == null && nextX == null ->
        min(layerDimensions.startPadding, layerDimensions.endPadding).doubled

      nextX != null -> {
        ((entry.x - ranges.minX) / ranges.xStep * layerDimensions.xSpacing +
          layerDimensions.startPadding)
          .doubled
          .toFloat()
          .coerceAtMost(abs(nextX - x))
      }

      else -> {
        ((ranges.maxX - entry.x) / ranges.xStep * layerDimensions.xSpacing +
          layerDimensions.endPadding)
          .doubled
          .toFloat()
          .coerceAtMost(abs(x - previousX!!))
      }
    }.toInt()

  protected fun resetTempData() {
    _markerTargets.clear()
    linePath.rewind()
  }

  protected fun CartesianDrawingContext.collectPointsAndVisibleIndexRange(
    series: List<LineCartesianLayerModel.Entry>,
    drawingStart: Float,
    pointInfoMap: Map<Double, LineCartesianLayerDrawingModel.Entry>?,
    drawFullLineLength: Boolean = false,
    points: MutableList<Offset>,
  ): IntRange {
    val minX = ranges.minX
    val maxX = ranges.maxX
    val xStep = ranges.xStep
    val yRange = ranges.getYRange(verticalAxisPosition)

    // yTransform with fraction caching + animated transitions synced to primary layer.
    //
    // yTransform triggered ONLY on yRange change (syncs with primary layer range animation).
    // Not called during active scrolling — fractions are stable until range updates.

    // yTransform — called on series change, first compute, or target yRange change.
    val transformedY: DoubleArray? = if (yTransform != null) {
      val seriesKey = series.hashCode().toLong()
      val seriesChanged = transformLastSeriesKey != 0L && transformLastSeriesKey != seriesKey

      // Read animation target from chart ranges — works with any provider type
      val targetYRange = ranges.getTargetYRange(verticalAxisPosition)
      val targetKey = targetYRange.minY.toBits() xor (targetYRange.maxY.toBits() * 31)

      // First compute or series changed → snap (no fade)
      if (transformCacheResult == null || seriesChanged) {
        val visibleXRange = getVisibleXRange()
        transformCacheResult = yTransform.invoke(series, yRange, visibleXRange)
        transformIndexMap = series.withIndex().associate { (i, e) -> e.x to i }
        transformLastSeriesKey = seriesKey
        transformLastTargetKey = targetKey
        transformFadePhase = 0
        transformFadeOpacity = 1f
      }

      // Target changed → recompute with TARGET yRange + fade
      if (targetKey != transformLastTargetKey && transformFadePhase == 0) {
        val visibleXRange = getVisibleXRange()
        transformPendingResult = yTransform.invoke(series, targetYRange, visibleXRange)
        transformPendingIndexMap = series.withIndex().associate { (i, e) -> e.x to i }
        transformLastTargetKey = targetKey
        transformFadePhase = 1
        transformFadeStart = TimeSource.Monotonic.markNow()
      }

      // Fade state machine
      when (transformFadePhase) {
        1 -> {
          val elapsed = transformFadeStart?.elapsedNow() ?: fadeOutDuration
          val t = (elapsed / fadeOutDuration).coerceIn(0.0, 1.0)
          transformFadeOpacity = (1.0 - t).toFloat()
          if (t >= 1.0) {
            transformCacheResult = transformPendingResult
            transformIndexMap = transformPendingIndexMap
            transformPendingResult = null
            transformPendingIndexMap = null
            transformFadePhase = 2
            transformFadeStart = TimeSource.Monotonic.markNow()
            transformFadeOpacity = 0f
          }
        }
        2 -> {
          val elapsed = transformFadeStart?.elapsedNow() ?: fadeInDuration
          val t = (elapsed / fadeInDuration).coerceIn(0.0, 1.0)
          transformFadeOpacity = t.toFloat()
          if (t >= 1.0) {
            transformFadePhase = 0
            transformFadeOpacity = 1f
          }
        }
        else -> transformFadeOpacity = 1f
      }

      transformCacheResult
    } else null

    val boundsStart = layerBounds.getStart(isLtr = isLtr)
    val boundsEnd = boundsStart + layoutDirectionMultiplier * layerBounds.width

    fun getDrawX(entry: LineCartesianLayerModel.Entry): Float =
      drawingStart +
        layoutDirectionMultiplier * layerDimensions.xSpacing * ((entry.x - minX) / xStep).toFloat()

    fun getDrawY(entry: LineCartesianLayerModel.Entry): Float {
      val y = pointInfoMap?.get(entry.x)?.y?.let { it * layerBounds.height }
        ?: run {
          val idx = transformIndexMap?.get(entry.x)
          val rawY = if (idx != null && transformedY != null) transformedY[idx] else entry.y
          ((rawY - yRange.minY) / yRange.length).toFloat() * layerBounds.height
        }
      return layerBounds.bottom - y
    }

    var visibleStart = -1
    var visibleEnd = -1

    series.forEachIn(minX = minX, maxX = maxX) { entry, _ ->
      points += Offset(getDrawX(entry), getDrawY(entry))
    }

    for (index in points.indices) {
      val px = points[index].x
      val nextPx = points.getOrNull(index + 1)?.x
      val inBounds =
        drawFullLineLength ||
          nextPx == null ||
          !(isLtr && px < boundsStart || !isLtr && px > boundsStart) ||
          !(isLtr && nextPx < boundsStart || !isLtr && nextPx > boundsStart)
      val pastEnd = isLtr && px > boundsEnd || !isLtr && px < boundsEnd
      if (inBounds && visibleStart == -1) visibleStart = index
      if (inBounds) visibleEnd = index
      if (pastEnd) {
        if (visibleEnd == -1) visibleEnd = index
        break
      }
    }

    return if (visibleStart == -1) IntRange.EMPTY else visibleStart..visibleEnd
  }

  protected fun CartesianDrawingContext.connectPoints(
    interpolator: Interpolator,
    points: List<Offset>,
    visibleIndexRange: IntRange,
  ) {
    interpolator.interpolate(this, linePath, points, visibleIndexRange)
  }

  protected open fun CartesianDrawingContext.forEachPointInBounds(
    series: List<LineCartesianLayerModel.Entry>,
    drawingStart: Float,
    pointInfoMap: Map<Double, LineCartesianLayerDrawingModel.Entry>?,
    drawFullLineLength: Boolean = false,
    action:
      (
      entry: LineCartesianLayerModel.Entry, x: Float, y: Float, previousX: Float?, nextX: Float?,
    ) -> Unit,
  ) {
    val minX = ranges.minX
    val maxX = ranges.maxX
    val xStep = ranges.xStep

    var x: Float? = null
    var nextX: Float? = null

    val boundsStart = layerBounds.getStart(isLtr = isLtr)
    val boundsEnd = boundsStart + layoutDirectionMultiplier * layerBounds.width

    fun getDrawX(entry: LineCartesianLayerModel.Entry): Float =
      drawingStart +
        layoutDirectionMultiplier * layerDimensions.xSpacing * ((entry.x - minX) / xStep).toFloat()

    fun getDrawY(entry: LineCartesianLayerModel.Entry): Float {
      val yRange = ranges.getYRange(verticalAxisPosition)
      val idx = transformIndexMap?.get(entry.x)
      val rawY =
        if (idx != null && transformCacheResult != null) transformCacheResult!![idx] else entry.y
      return layerBounds.bottom -
        (pointInfoMap?.get(entry.x)?.y ?: ((rawY - yRange.minY) / yRange.length).toFloat()) *
        layerBounds.height
    }

    series.forEachIn(minX = minX, maxX = maxX, padding = 1) { entry, next ->
      val previousX = x
      val immutableX = nextX ?: getDrawX(entry)
      val immutableNextX = next?.let(::getDrawX)
      x = immutableX
      nextX = immutableNextX
      if (
        drawFullLineLength.not() &&
        immutableNextX != null &&
        (isLtr && immutableX < boundsStart || !isLtr && immutableX > boundsStart) &&
        (isLtr && immutableNextX < boundsStart || !isLtr && immutableNextX > boundsStart)
      ) {
        return@forEachIn
      }
      action(entry, immutableX, getDrawY(entry), previousX, nextX)
      if (isLtr && immutableX > boundsEnd || isLtr.not() && immutableX < boundsEnd) return
    }
  }

  override fun updateDimensions(
    context: CartesianMeasuringContext,
    dimensions: MutableCartesianLayerDimensions,
    model: LineCartesianLayerModel,
  ) {
    with(context) {
      val maxPointSize =
        (0..<model.series.size)
          .maxOf {
            lineProvider
              .getLine(it, model.extraStore)
              .pointProvider
              ?.getLargestPoint(model.extraStore)
              ?.size
              .orZero
          }
          .pixels
      val xSpacing = maxPointSize + pointSpacing.pixels
      dimensions.ensureValuesAtLeast(
        xSpacing = xSpacing,
        scalableStartPadding = layerPadding.scalableStart.pixels,
        scalableEndPadding = layerPadding.scalableEnd.pixels,
        unscalableStartPadding = layerPadding.unscalableStart.pixels,
        unscalableEndPadding = layerPadding.unscalableEnd.pixels,
      )
    }
  }

  override fun updateChartRanges(
    chartRanges: MutableCartesianChartRanges,
    model: LineCartesianLayerModel,
  ) {
    var minY = model.minY
    var maxY = model.maxY
    model.series.forEachIndexed { seriesIndex, series ->
      val interpolator = lineProvider.getLine(seriesIndex, model.extraStore).interpolator
      val yRange = interpolator.getYRange(series.map { it.y })
      minY = min(minY, yRange.start)
      maxY = max(maxY, yRange.endInclusive)
    }
    chartRanges.tryUpdate(
      rangeProvider.getMinX(model.minX, model.maxX, model.extraStore),
      rangeProvider.getMaxX(model.minX, model.maxX, model.extraStore),
      rangeProvider.getMinY(minY, maxY, model.extraStore),
      rangeProvider.getMaxY(minY, maxY, model.extraStore),
      verticalAxisPosition,
    )
  }

  override fun updateLayerMargins(
    context: CartesianMeasuringContext,
    layerMargins: CartesianLayerMargins,
    layerDimensions: CartesianLayerDimensions,
    model: LineCartesianLayerModel,
  ) {
    with(context) {
      val verticalMargin =
        (0..<model.series.size)
          .mapNotNull { lineProvider.getLine(it, model.extraStore) }
          .maxOf {
            max(
              it.stroke.thickness.pixels,
              it.pointProvider?.getLargestPoint(model.extraStore)?.size.orZero.pixels,
            )
          }
          .half
      layerMargins.ensureValuesAtLeast(top = verticalMargin, bottom = verticalMargin)
    }
  }

  override fun prepareForTransformation(
    model: LineCartesianLayerModel?,
    ranges: CartesianChartRanges,
    extraStore: MutableExtraStore,
  ) {
    drawingModelInterpolator.setModels(
      old = extraStore.getOrNull(drawingModelKey),
      new = model?.toDrawingModel(ranges),
    )
  }

  override suspend fun transform(extraStore: MutableExtraStore, fraction: Float) {
    drawingModelInterpolator.transform(fraction)?.let { extraStore[drawingModelKey] = it }
      ?: extraStore.remove(drawingModelKey)
  }

  private fun LineCartesianLayerModel.toDrawingModel(
    ranges: CartesianChartRanges,
  ): LineCartesianLayerDrawingModel {
    val yRange = ranges.getYRange(verticalAxisPosition)
    return LineCartesianLayerDrawingModel(
      series.map { series ->
        series.associate { entry ->
          entry.x to
            LineCartesianLayerDrawingModel.Entry(
              ((entry.y - yRange.minY) / yRange.length).toFloat(),
            )
        }
      },
    )
  }

  /** Creates a new [LineCartesianLayer] based on this one. */
  public fun copy(
    lineProvider: LineProvider = this.lineProvider,
    pointSpacing: Dp = this.pointSpacing,
    rangeProvider: CartesianLayerRangeProvider = this.rangeProvider,
    verticalAxisPosition: Axis.Position.Vertical? = this.verticalAxisPosition,
    drawingModelInterpolator:
    CartesianLayerDrawingModelInterpolator<
      LineCartesianLayerDrawingModel.Entry,
      LineCartesianLayerDrawingModel,
      > =
      this.drawingModelInterpolator,
    yTransform: ((
      series: List<LineCartesianLayerModel.Entry>,
      yRange: CartesianChartRanges.YRange,
      visibleXRange: ClosedFloatingPointRange<Double>,
    ) -> DoubleArray?)? = this.yTransform,
  ): LineCartesianLayer {
    val newLayer = LineCartesianLayer(
      lineProvider,
      pointSpacing,
      rangeProvider,
      verticalAxisPosition,
      drawingModelInterpolator,
      drawingModelKey,
      yTransform,
    )
    // Carry over yTransform cache so layer recreation doesn't cause a snap
    newLayer.transformCacheResult = this.transformCacheResult
    newLayer.transformIndexMap = this.transformIndexMap
    newLayer.transformLastSeriesKey = this.transformLastSeriesKey
    newLayer.transformLastTargetKey = this.transformLastTargetKey
    newLayer.transformFadeOpacity = this.transformFadeOpacity
    newLayer.transformFadePhase = this.transformFadePhase
    newLayer.transformFadeStart = this.transformFadeStart
    return newLayer
  }

  override fun equals(other: Any?): Boolean =
    this === other ||
      other is LineCartesianLayer &&
      lineProvider == other.lineProvider &&
      pointSpacing == other.pointSpacing &&
      rangeProvider == other.rangeProvider &&
      verticalAxisPosition == other.verticalAxisPosition &&
      drawingModelInterpolator == other.drawingModelInterpolator

  override fun hashCode(): Int {
    var result = lineProvider.hashCode()
    result = 31 * result + pointSpacing.hashCode()
    result = 31 * result + rangeProvider.hashCode()
    result = 31 * result + (verticalAxisPosition?.hashCode() ?: 0)
    result = 31 * result + drawingModelInterpolator.hashCode()
    return result
  }

  /** Provides access to [Line] factory functions. */
  public companion object
}

internal fun CartesianDrawingContext.getCanvasSplitY(
  splitY: (ExtraStore) -> Number,
  halfLineThickness: Float,
  verticalAxisPosition: Axis.Position.Vertical?,
): Float {
  val yRange = ranges.getYRange(verticalAxisPosition)
  val base =
    layerBounds.bottom -
      ((splitY(model.extraStore).toDouble() - yRange.minY) / yRange.length).toFloat() *
      layerBounds.height
  return ceil(base).coerceIn(layerBounds.top..layerBounds.bottom) + ceil(halfLineThickness)
}

/** Creates and remembers a [LineCartesianLayer]. */
@Composable
public fun rememberLineCartesianLayer(
  lineProvider: LineCartesianLayer.LineProvider =
    LineCartesianLayer.LineProvider.series(
      vicoTheme.lineCartesianLayerColors.map { color ->
        LineCartesianLayer.rememberLine(LineCartesianLayer.LineFill.single(Fill(color)))
      },
    ),
  pointSpacing: Dp = Defaults.POINT_SPACING.dp,
  rangeProvider: CartesianLayerRangeProvider = remember { CartesianLayerRangeProvider.auto() },
  verticalAxisPosition: Axis.Position.Vertical? = null,
  drawingModelInterpolator:
  CartesianLayerDrawingModelInterpolator<
    LineCartesianLayerDrawingModel.Entry,
    LineCartesianLayerDrawingModel,
    > =
    remember {
      CartesianLayerDrawingModelInterpolator.default()
    },
  yTransform: ((
    series: List<LineCartesianLayerModel.Entry>,
    yRange: CartesianChartRanges.YRange,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ) -> DoubleArray?)? = null,
  markerTargetsEnabled: Boolean = true,
  alwaysUseLiveRange: Boolean = false,
): LineCartesianLayer {
  var lineCartesianLayerWrapper by remember { ValueWrapper<LineCartesianLayer?>(null) }
  return remember(
    lineProvider,
    pointSpacing,
    rangeProvider,
    verticalAxisPosition,
    drawingModelInterpolator,
  ) {
    val lineCartesianLayer =
      lineCartesianLayerWrapper?.copy(
        lineProvider,
        pointSpacing,
        rangeProvider,
        verticalAxisPosition,
        drawingModelInterpolator,
        yTransform,
      )
        ?: LineCartesianLayer(
          lineProvider,
          pointSpacing,
          rangeProvider,
          verticalAxisPosition,
          drawingModelInterpolator,
          yTransform,
          markerTargetsEnabled,
        )
    lineCartesianLayer.markerTargetsEnabled = markerTargetsEnabled
    lineCartesianLayer.alwaysUseLiveRange = alwaysUseLiveRange
    lineCartesianLayerWrapper = lineCartesianLayer
    lineCartesianLayer
  }
}

/** Creates and remembers a [LineCartesianLayer.Line]. */
@Composable
public fun LineCartesianLayer.Companion.rememberLine(
  fill: LineCartesianLayer.LineFill =
    vicoTheme.lineCartesianLayerColors.first().let { color ->
      remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) }
    },
  stroke: LineCartesianLayer.LineStroke = LineCartesianLayer.LineStroke.Continuous(),
  areaFill: LineCartesianLayer.AreaFill? = null,
  pointProvider: LineCartesianLayer.PointProvider? = null,
  interpolator: LineCartesianLayer.Interpolator = LineCartesianLayer.Interpolator.Sharp,
  dataLabel: TextComponent? = null,
  dataLabelPosition: Position.Vertical = Position.Vertical.Top,
  dataLabelValueFormatter: CartesianValueFormatter = remember { CartesianValueFormatter.decimal() },
  dataLabelRotationDegrees: Float = 0f,
): Line =
  remember(
    fill,
    stroke,
    areaFill,
    pointProvider,
    interpolator,
    dataLabel,
    dataLabelPosition,
    dataLabelRotationDegrees,
    dataLabelRotationDegrees,
  ) {
    Line(
      fill,
      stroke,
      areaFill,
      pointProvider,
      interpolator,
      dataLabel,
      dataLabelPosition,
      dataLabelValueFormatter,
      dataLabelRotationDegrees,
    )
  }

/** Creates and remembers a [LineCartesianLayer.Line]. */
@Suppress("DEPRECATION")
@Deprecated("Use the overload with `interpolator`.")
@Composable
public fun LineCartesianLayer.Companion.rememberLine(
  fill: LineCartesianLayer.LineFill =
    vicoTheme.lineCartesianLayerColors.first().let { color ->
      remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) }
    },
  stroke: LineCartesianLayer.LineStroke = LineCartesianLayer.LineStroke.Continuous(),
  areaFill: LineCartesianLayer.AreaFill? = null,
  pointProvider: LineCartesianLayer.PointProvider? = null,
  pointConnector: PointConnector,
  dataLabel: TextComponent? = null,
  dataLabelPosition: Position.Vertical = Position.Vertical.Top,
  dataLabelValueFormatter: CartesianValueFormatter = remember { CartesianValueFormatter.decimal() },
  dataLabelRotationDegrees: Float = 0f,
): Line =
  rememberLine(
    fill,
    stroke,
    areaFill,
    pointProvider,
    PointConnectorAdapter(pointConnector),
    dataLabel,
    dataLabelPosition,
    dataLabelValueFormatter,
    dataLabelRotationDegrees,
  )
