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

package com.patrykandpatrick.vico.compose.cartesian.marker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerMargins
import com.patrykandpatrick.vico.compose.common.*
import com.patrykandpatrick.vico.compose.common.component.Component
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.data.CacheStore
import kotlin.math.ceil
import kotlin.math.min

/**
 * The default [CartesianMarker] implementation.
 *
 * @property label the [TextComponent] for the label.
 * @property valueFormatter formats the values.
 * @property labelPosition specifies the position of the label.
 * @property indicator returns a [Component] to be drawn at points with the given color.
 * @property indicatorSize the indicator size.
 * @property guideline drawn vertically through the marked points (also used as the
 *   horizontal crosshair line when [horizontalLabelPosition] is non-null).
 * @property horizontalLabelPosition when non-null, the marker additionally draws a
 *   horizontal crosshair line across the chart at the primary target's canvas-Y and
 *   places a label anchored at this horizontal position (Start / Center / End).
 *   Reuses [guideline] for the line and [label] for the text. Null (default) = the
 *   marker behaves exactly as before — no crosshair.
 * @property horizontalLabelFormatter formats the crosshair label. Fires with the same
 *   per-series Y values passed to [yLabelCallback] plus the hovered X in data
 *   coordinates. Return `null` to skip the label for this frame (line still draws).
 * @property restingLabel the [TextComponent] for the text that stands on the label's line while
 *   nothing is marked, drawn from the plot's start. Null (default) = the line stays blank until a
 *   marker appears.
 * @property restingLabelFormatter formats it, from the _x_ range currently in view. Return
 *   `null` to leave the line blank for this frame.
 * @property labelGap the space between the plot's edge and the label on the line [label] and
 *   [restingLabel] share — the gap alone. The labels' own height is measured and reserved on top
 *   of it, so this is the one figure a design actually states.
 */
public open class DefaultCartesianMarker(
  protected val label: TextComponent,
  protected val valueFormatter: ValueFormatter = ValueFormatter.default(),
  protected val labelPosition: LabelPosition = LabelPosition.Top,
  protected val indicator: ((Color) -> Component)? = null,
  protected val indicatorSize: Dp = Defaults.MARKER_INDICATOR_SIZE.dp,
  protected val guideline: LineComponent? = null,
  protected val contentPadding: Insets = Insets.Zero,
  /** Callback invoked with interpolated Y values per series when marker is drawn. Used for chart header updates. */
  protected val yLabelCallback: ((List<List<Double>>) -> Unit)? = null,
  protected val horizontalLabelPosition: Position.Horizontal? = null,
  protected val horizontalLabelFormatter: ((List<List<Double>>, Double) -> CharSequence?)? = null,
  protected val restingLabel: TextComponent? = null,
  protected val restingLabelFormatter:
    ((ClosedFloatingPointRange<Double>) -> CharSequence?)? = null,
  protected val labelGap: Dp = 0.dp,
) : CartesianMarker {

  protected val markerCornerBasedShape: MarkerCornerBasedShape? =
    (label.background as? ShapeComponent)?.shape as? MarkerCornerBasedShape

  protected val tickSize: Dp = markerCornerBasedShape?.tickSize.orZero

  /**
   * The resting text, formatted once per settle and drawn as-is until the next one.
   *
   * Formatting per frame would run the caller's formatter — date arithmetic, here — on every
   * frame of a scroll, and would name each window the scroll passed over rather than the one it
   * came to rest at. Compose state, so the draw that reads it runs again when it changes.
   */
  private var restingText: CharSequence? by mutableStateOf(null)

  override fun onScrollSettled(visibleXRange: ClosedFloatingPointRange<Double>) {
    restingText = restingLabelFormatter?.invoke(visibleXRange)
  }

  override fun drawOverLayers(
    context: CartesianDrawingContext,
    targets: List<CartesianMarker.Target>,
  ) {
    with(context) {
      // Per-series Y values — shared by yLabelCallback and the horizontal crosshair formatter.
      val yValuesPerSeries: List<List<Double>> = targets.map { target ->
        if (target is LineCartesianLayerMarkerTarget) target.points.map { it.entry.y }
        else emptyList()
      }
      // Draw both crosshair lines first so indicators (dots) paint on top of the
      // intersection — matches the vertical guideline's visual ordering so the
      // hovered data point is never obscured by the crosshair.
      drawGuideline(targets)
      drawHorizontalCrosshair(context, targets, yValuesPerSeries)

      val halfIndicatorSize = indicatorSize.pixels.half
      targets.forEach { target ->
        when (target) {
          is CandlestickCartesianLayerMarkerTarget -> {
            drawIndicator(
              target.canvasX,
              target.openingCanvasY,
              target.openingColor,
              halfIndicatorSize,
            )
            drawIndicator(
              target.canvasX,
              target.closingCanvasY,
              target.closingColor,
              halfIndicatorSize,
            )
            drawIndicator(target.canvasX, target.lowCanvasY, target.lowColor, halfIndicatorSize)
            drawIndicator(target.canvasX, target.highCanvasY, target.highColor, halfIndicatorSize)
          }

          is ColumnCartesianLayerMarkerTarget -> {
            target.columns.forEach { column ->
              drawIndicator(target.canvasX, column.canvasY, column.color, halfIndicatorSize)
            }
          }

          is LineCartesianLayerMarkerTarget -> {
            target.points.forEach { point ->
              if (!point.isInterpolated) {
                drawIndicator(target.canvasX, point.canvasY, point.color, halfIndicatorSize)
              }
            }
          }
        }
      }
      // Invoke Y label callback with Y values per target (for chart header / metric info)
      yLabelCallback?.invoke(yValuesPerSeries)
      drawLabel(context, targets)
    }
  }

  /**
   * Draws a horizontal crosshair line across the chart at the primary target's
   * canvas-Y (first point of the first `LineCartesianLayerMarkerTarget` in [targets])
   * and, optionally, a label at [horizontalLabelPosition]. No-op when
   * [horizontalLabelPosition] is null, [guideline] is null, or no line target is
   * present.
   *
   * The label text is supplied by [horizontalLabelFormatter] — callers may return
   * `null` to suppress the label for that frame while still showing the line.
   */
  protected fun drawHorizontalCrosshair(
    context: CartesianDrawingContext,
    targets: List<CartesianMarker.Target>,
    yValuesPerSeries: List<List<Double>>,
  ): Unit = with(context) {
    val labelPositionHorizontal = horizontalLabelPosition ?: return
    val lineComponent = guideline ?: return
    // Pick the *last* line layer's last point as the crosshair anchor — charts with
    // background percentile bands (e.g. baby growth) push the bands as earlier layers
    // and the real data line as the final one. `target.points[0]` would snap the line
    // onto the lowest percentile band; `lastOrNull()` tracks the actual data line.
    val primaryTarget = targets.lastOrNull { it is LineCartesianLayerMarkerTarget }
      as? LineCartesianLayerMarkerTarget ?: return
    val primaryY = primaryTarget.points.lastOrNull()?.canvasY ?: return

    lineComponent.drawHorizontal(this, layerBounds.left, layerBounds.right, primaryY)

    val text = horizontalLabelFormatter?.invoke(yValuesPerSeries, primaryTarget.x) ?: return
    val labelBounds = label.getBounds(context, text, layerBounds.width.toInt())
    // Anchor the label above the horizontal line by default. If it would clip the
    // top of the chart, fall back to below the line so it stays visible.
    val clippingFreeVerticalLabelPosition =
      Position.Vertical.Top.inBounds(
        bounds = layerBounds,
        componentHeight = labelBounds.height,
        referenceY = primaryY,
        referenceDistance = lineComponent.thickness.pixels.half,
      )
    val labelY =
      when (clippingFreeVerticalLabelPosition) {
        Position.Vertical.Top -> primaryY - lineComponent.thickness.pixels.half
        Position.Vertical.Center -> primaryY
        Position.Vertical.Bottom -> primaryY + lineComponent.thickness.pixels.half
      }
    label.draw(
      context = context,
      text = text,
      x =
        when (labelPositionHorizontal) {
          Position.Horizontal.Start -> layerBounds.getStart(isLtr)
          Position.Horizontal.Center -> layerBounds.center.x
          Position.Horizontal.End -> layerBounds.getEnd(isLtr)
        },
      y = labelY,
      horizontalPosition = -labelPositionHorizontal,
      verticalPosition = clippingFreeVerticalLabelPosition,
      maxWidth = layerBounds.width.toInt(),
    )
  }

  protected open fun CartesianDrawingContext.drawIndicator(
    x: Float,
    y: Float,
    color: Color,
    halfIndicatorSize: Float,
  ) {
    val indicator = indicator ?: return
    cacheStore
      .getOrSet(keyNamespace, indicator, color) { indicator.invoke(color) }
      .draw(
        this,
        x - halfIndicatorSize,
        y - halfIndicatorSize,
        x + halfIndicatorSize,
        y + halfIndicatorSize,
      )
  }

  protected fun drawLabel(
    context: CartesianDrawingContext,
    targets: List<CartesianMarker.Target>,
  ): Unit =
    with(context) {
      val text = valueFormatter.format(context, targets)
      val targetX = targets.averageOf { it.canvasX }
      val labelBounds = label.getBounds(context, text, layerBounds.width.toInt())
      val halfOfTextWidth = labelBounds.width.half
      val x = overrideXPositionToFit(targetX, layerBounds, halfOfTextWidth)
      markerCornerBasedShape?.tickX = targetX - x
      val tickPosition: MarkerCornerBasedShape.TickPosition
      val y: Float
      val verticalPosition: Position.Vertical
      when (labelPosition) {
        LabelPosition.Top -> {
          tickPosition = MarkerCornerBasedShape.TickPosition.Bottom
          y = context.layerBounds.top - tickSize.pixels - contentPadding.bottom.pixels
          verticalPosition = Position.Vertical.Top
        }

        LabelPosition.Bottom -> {
          tickPosition = MarkerCornerBasedShape.TickPosition.Top
          y = context.layerBounds.bottom + tickSize.pixels + contentPadding.top.pixels
          verticalPosition = Position.Vertical.Bottom
        }

        LabelPosition.AroundPoint,
        LabelPosition.AbovePoint -> {
          val topPointY =
            targets.minOf { target ->
              when (target) {
                is CandlestickCartesianLayerMarkerTarget -> target.highCanvasY
                is ColumnCartesianLayerMarkerTarget ->
                  target.columns.minOf(ColumnCartesianLayerMarkerTarget.Column::canvasY)

                is LineCartesianLayerMarkerTarget ->
                  target.points.minOf(LineCartesianLayerMarkerTarget.Point::canvasY)

                else -> error("Unexpected `CartesianMarker.Target` implementation.")
              }
            }
          val flip =
            labelPosition == LabelPosition.AroundPoint &&
              topPointY - labelBounds.height - tickSize.pixels < context.layerBounds.top
          tickPosition =
            if (flip) {
              MarkerCornerBasedShape.TickPosition.Top
            } else {
              MarkerCornerBasedShape.TickPosition.Bottom
            }
          y = topPointY + (if (flip) 1 else -1) * tickSize.pixels
          verticalPosition = if (flip) Position.Vertical.Bottom else Position.Vertical.Top
        }
        LabelPosition.BelowPoint -> {
          val bottomPointY =
            targets.maxOf { target ->
              when (target) {
                is CandlestickCartesianLayerMarkerTarget -> target.lowCanvasY
                is ColumnCartesianLayerMarkerTarget ->
                  target.columns.maxOf(ColumnCartesianLayerMarkerTarget.Column::canvasY)
                is LineCartesianLayerMarkerTarget ->
                  target.points.maxOf(LineCartesianLayerMarkerTarget.Point::canvasY)
                else -> error("Unexpected `CartesianMarker.Target` implementation.")
              }
            }
          tickPosition = MarkerCornerBasedShape.TickPosition.Top
          y = bottomPointY + tickSize.pixels
          verticalPosition = Position.Vertical.Bottom
        }
      }
      markerCornerBasedShape?.tickPosition = tickPosition

      label.draw(
        context = context,
        text = text,
        x = x,
        y = y,
        verticalPosition = verticalPosition,
        maxWidth = ceil(min(layerBounds.right - x, x - layerBounds.left).doubled).toInt(),
      )
    }

  /**
   * Draws [restingLabel] on the line [drawLabel] uses, which is free while nothing is marked.
   *
   * The text itself is whatever the last [onScrollSettled] produced; this only places it. The
   * same split [ScrollAwareRangeProvider] uses, where the callback runs on settle and the draw
   * reads what it left behind. (MOB-2953)
   */
  override fun drawResting(context: CartesianDrawingContext): Unit =
    with(context) {
      val component = restingLabel ?: return
      val text = restingText ?: return
      val (y, verticalPosition) = restingLabelAnchor()
      component.draw(
        context = this,
        text = text,
        x = layerBounds.getStart(isLtr),
        y = y,
        horizontalPosition = -Position.Horizontal.Start,
        verticalPosition = verticalPosition,
        maxWidth = layerBounds.width.toInt(),
      )
    }

  /**
   * Where the resting text sits: the same line [drawLabel] puts a Top or Bottom marker label on.
   *
   * The point-anchored label positions follow the marked point rather than a line, so there is no
   * line to share and the plot's top edge stands in.
   */
  protected open fun CartesianDrawingContext.restingLabelAnchor(): Pair<Float, Position.Vertical> =
    when (labelPosition) {
      LabelPosition.Bottom ->
        layerBounds.bottom + tickSize.pixels + contentPadding.top.pixels to Position.Vertical.Bottom
      else ->
        layerBounds.top - tickSize.pixels - contentPadding.bottom.pixels to Position.Vertical.Top
    }

  protected fun overrideXPositionToFit(
    xPosition: Float,
    bounds: Rect,
    halfOfTextWidth: Float,
  ): Float =
    when {
      xPosition - halfOfTextWidth < bounds.left -> bounds.left + halfOfTextWidth
      xPosition + halfOfTextWidth > bounds.right -> bounds.right - halfOfTextWidth
      else -> xPosition
    }

  protected fun CartesianDrawingContext.drawGuideline(targets: List<CartesianMarker.Target>) {
    val guidelineTop = when (labelPosition) {
      LabelPosition.Top -> layerBounds.top - tickSize.pixels - contentPadding.bottom.pixels
      LabelPosition.AbovePoint, LabelPosition.AroundPoint -> {
        val topPointY = targets.minOf { target ->
          when (target) {
            is LineCartesianLayerMarkerTarget -> target.points.minOf { it.canvasY }
            is ColumnCartesianLayerMarkerTarget -> target.columns.minOf { it.canvasY }
            is CandlestickCartesianLayerMarkerTarget -> target.highCanvasY
            else -> layerBounds.top
          }
        }
        topPointY - tickSize.pixels - contentPadding.top.pixels
      }
      else -> layerBounds.top
    }
    val guidelineBottom = when (labelPosition) {
      LabelPosition.Bottom -> layerBounds.bottom + tickSize.pixels + contentPadding.bottom.pixels
      LabelPosition.BelowPoint -> {
        val bottomPointY = targets.maxOf { target ->
          when (target) {
            is LineCartesianLayerMarkerTarget -> target.points.maxOf { it.canvasY }
            is ColumnCartesianLayerMarkerTarget -> target.columns.maxOf { it.canvasY }
            is CandlestickCartesianLayerMarkerTarget -> target.lowCanvasY
            else -> layerBounds.bottom
          }
        }
        bottomPointY + tickSize.pixels + contentPadding.bottom.pixels
      }
      else -> layerBounds.bottom
    }
    targets
      .map { it.canvasX }
      .toSet()
      .forEach { x -> guideline?.drawVertical(this, x, guidelineTop, guidelineBottom) }
  }

  override fun updateLayerMargins(
    context: CartesianMeasuringContext,
    layerMargins: CartesianLayerMargins,
    layerDimensions: CartesianLayerDimensions,
    model: CartesianChartModel,
  ) {
    with(context) {
      // Measured with no text, which pads the layout out to the component's own line count: the
      // height of a line of this style, whatever it ends up saying. So the room follows the type
      // and a caller states only the gap. A hardcoded 24dp stood here, halved on the way in, and
      // kept 12dp for a label drawn 16dp out — which is how the label came to overlap whatever
      // sat beyond the chart.
      //
      // Text-independent on purpose, so the chart does not jump as the marker shows and hides or
      // its text changes (matching v3). Both labels share the line and only one is ever up, so
      // the taller of the two settles it.
      //
      // contentPadding is deliberately not added: it moves where the label draws, not how much
      // room is kept for it.
      val labelHeight = maxOf(label.getHeight(this), restingLabel?.getHeight(this) ?: 0f)
      val reserved = labelGap.pixels + labelHeight + tickSize.pixels
      when (labelPosition) {
        LabelPosition.Top,
        LabelPosition.AbovePoint -> layerMargins.ensureVerticalValuesAtLeast(top = reserved)

        LabelPosition.Bottom,
        LabelPosition.BelowPoint -> layerMargins.ensureVerticalValuesAtLeast(bottom = reserved)

        LabelPosition.AroundPoint -> Unit
      }
    }
  }

  override fun equals(other: Any?): Boolean =
    this === other ||
      other is DefaultCartesianMarker &&
        label == other.label &&
        valueFormatter == other.valueFormatter &&
        labelPosition == other.labelPosition &&
        indicator == other.indicator &&
        indicatorSize == other.indicatorSize &&
        guideline == other.guideline &&
        horizontalLabelPosition == other.horizontalLabelPosition

  override fun hashCode(): Int {
    var result = label.hashCode()
    result = 31 * result + valueFormatter.hashCode()
    result = 31 * result + labelPosition.hashCode()
    result = 31 * result + indicator.hashCode()
    result = 31 * result + indicatorSize.hashCode()
    result = 31 * result + guideline.hashCode()
    result = 31 * result + horizontalLabelPosition.hashCode()
    return result
  }

  /** Specifies the position of a [DefaultCartesianMarker]’s label. */
  public enum class LabelPosition {
    /** Positions the label at the top of the [CartesianChart]. Sufficient room is made. */
    Top,

    /** Positions the label at the bottom of the [CartesianChart]. Sufficient room is made. */
    Bottom,

    /**
     * Positions the label above the topmost marked point or, if there isn’t enough room, below it.
     */
    AroundPoint,

    /**
     * Positions the label above the topmost marked point. Sufficient room is made at the top of the
     * [CartesianChart].
     */
    AbovePoint,

    /**
     * Positions the label below the bottommost marked point. Sufficient room is made at the bottom
     * of the [CartesianChart].
     */
    BelowPoint,
  }

  /** Formats [CartesianMarker] values for display. */
  public fun interface ValueFormatter {
    /** Returns a label for the given [CartesianMarker.Target]s. */
    public fun format(
      context: CartesianDrawingContext,
      targets: List<CartesianMarker.Target>,
    ): CharSequence

    /** Houses a [ValueFormatter] factory function. */
    public companion object {
      /**
       * Creates an instance of the default [ValueFormatter] implementation. The labels produced
       * include the [CartesianLayerModel.Entry] _y_ values, which are formatted to include up to
       * [decimalCount] decimal digits and, if [colorCode] is true, color-coded. Trailing zeros are
       * skipped.
       */
      public fun default(
        decimalCount: Int = 2,
        decimalSeparator: String = ".",
        thousandsSeparator: String = "",
        prefix: String = "",
        suffix: String = "",
        colorCode: Boolean = true,
      ): ValueFormatter =
        DefaultValueFormatter(
          decimalCount,
          decimalSeparator,
          thousandsSeparator,
          prefix,
          suffix,
          colorCode,
        )
    }
  }

  protected companion object {
    public val keyNamespace: CacheStore.KeyNamespace = CacheStore.KeyNamespace()
  }
}

internal class DefaultValueFormatter(
  private val decimalCount: Int,
  private val decimalSeparator: String,
  private val thousandsSeparator: String,
  private val prefix: String,
  private val suffix: String,
  private val colorCode: Boolean,
) : DefaultCartesianMarker.ValueFormatter {
  private fun AnnotatedString.Builder.append(y: Double, color: Color? = null) {
    if (colorCode && color != null) {
      withStyle(SpanStyle(color = color)) {
        append(y.format(decimalCount, decimalSeparator, thousandsSeparator, prefix, suffix))
      }
    } else {
      append(y.format(decimalCount, decimalSeparator, thousandsSeparator, prefix, suffix))
    }
  }

  private fun AnnotatedString.Builder.append(target: CartesianMarker.Target, shorten: Boolean) {
    when (target) {
      is CandlestickCartesianLayerMarkerTarget -> {
        if (shorten) {
          append(target.entry.closing, target.closingColor)
        } else {
          append("O ")
          append(target.entry.opening, target.openingColor)
          append(", C ")
          append(target.entry.closing, target.closingColor)
          append(", L ")
          append(target.entry.low, target.lowColor)
          append(", H ")
          append(target.entry.high, target.highColor)
        }
      }

      is ColumnCartesianLayerMarkerTarget -> {
        val includeSum = target.columns.size > 1
        if (includeSum) {
          append(target.columns.sumOf { it.entry.y })
          append(" (")
        }
        target.columns.forEachIndexed { index, column ->
          append(column.entry.y, column.color)
          if (index != target.columns.lastIndex) append(", ")
        }
        if (includeSum) append(")")
      }

      is LineCartesianLayerMarkerTarget -> {
        target.points.forEachIndexed { index, point ->
          append(point.entry.y, point.color)
          if (index != target.points.lastIndex) append(", ")
        }
      }

      else -> throw IllegalArgumentException("Unexpected `CartesianMarker.Target` implementation.")
    }
  }

  override fun format(
    context: CartesianDrawingContext,
    targets: List<CartesianMarker.Target>,
  ): CharSequence = buildAnnotatedString {
    targets.forEachIndexed { index, target ->
      append(target = target, shorten = targets.size > 1)
      if (index != targets.lastIndex) append(", ")
    }
  }

  override fun equals(other: Any?): Boolean =
    this === other ||
      other is DefaultValueFormatter &&
        decimalCount == other.decimalCount &&
        colorCode == other.colorCode

  override fun hashCode(): Int = 31 * decimalCount.hashCode() + colorCode.hashCode()
}

/** Creates and remembers a [DefaultCartesianMarker]. */
@Composable
public fun rememberDefaultCartesianMarker(
  label: TextComponent,
  valueFormatter: DefaultCartesianMarker.ValueFormatter = remember {
    DefaultCartesianMarker.ValueFormatter.default()
  },
  labelPosition: DefaultCartesianMarker.LabelPosition = DefaultCartesianMarker.LabelPosition.Top,
  indicator: ((Color) -> Component)? = null,
  indicatorSize: Dp = Defaults.MARKER_INDICATOR_SIZE.dp,
  guideline: LineComponent? = null,
  contentPadding: Insets = Insets.Zero,
  yLabelCallback: ((List<List<Double>>) -> Unit)? = null,
  horizontalLabelPosition: Position.Horizontal? = null,
  horizontalLabelFormatter: ((List<List<Double>>, Double) -> CharSequence?)? = null,
  restingLabel: TextComponent? = null,
  restingLabelFormatter: ((ClosedFloatingPointRange<Double>) -> CharSequence?)? = null,
  labelGap: Dp = 0.dp,
): DefaultCartesianMarker {
  val callbackRef = rememberUpdatedState(yLabelCallback)
  val horizontalFormatterRef = rememberUpdatedState(horizontalLabelFormatter)
  val restingFormatterRef = rememberUpdatedState(restingLabelFormatter)
  return remember(label, valueFormatter, labelPosition, indicator, indicatorSize, guideline, contentPadding, horizontalLabelPosition, restingLabel, labelGap) {
    DefaultCartesianMarker(
      label = label,
      valueFormatter = valueFormatter,
      labelPosition = labelPosition,
      indicator = indicator,
      indicatorSize = indicatorSize,
      guideline = guideline,
      contentPadding = contentPadding,
      yLabelCallback = { values -> callbackRef.value?.invoke(values) },
      horizontalLabelPosition = horizontalLabelPosition,
      horizontalLabelFormatter = { values, x -> horizontalFormatterRef.value?.invoke(values, x) },
      restingLabel = restingLabel,
      // Read through the ref, so a formatter closing over changing state does not re-create the
      // marker — the same treatment the other two callbacks get.
      restingLabelFormatter = { range -> restingFormatterRef.value?.invoke(range) },
      labelGap = labelGap,
    )
  }
}
