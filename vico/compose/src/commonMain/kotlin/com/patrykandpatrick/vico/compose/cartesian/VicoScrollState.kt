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

import androidx.compose.animation.core.*
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.MonotoneInterpolator
import com.patrykandpatrick.vico.compose.common.rangeWith
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

/**
 * Houses information on a [CartesianChart]’s scroll value. Allows for scroll customization and
 * programmatic scrolling.
 */
public class VicoScrollState {
  private val initialScroll: Scroll.Absolute
  private val autoScroll: Scroll
  private val autoScrollCondition: AutoScrollCondition
  private val autoScrollAnimationSpec: AnimationSpec<Float>
  internal val snapAnimationSpec: AnimationSpec<Float>
  private val _value: MutableFloatState
  private val _maxValue = mutableFloatStateOf(0f)
  private var initialScrollHandled: Boolean
  private var context: CartesianMeasuringContext? = null
  private var layerDimensions: CartesianLayerDimensions? = null
  private var bounds: Rect? = null
  internal var scrollEnabled by mutableStateOf(true)
  internal val xSnapStep: Double?
  internal val consumedXDeltas = MutableSharedFlow<Float>(extraBufferCapacity = 1)
  internal val unconsumedXDeltas = MutableSharedFlow<Float>(extraBufferCapacity = 1)
  private var previousScrollLayout: ScrollLayout? = null
  internal var drawingContext: CartesianDrawingContext? = null

  /** Holds the scroll position still while a scrub is in progress. */
  internal var isScrollFrozen: Boolean = false

  internal val scrollableState = ScrollableState { delta ->
    if (isScrollFrozen || !scrollEnabled) {
      // Claim the delta without moving, so nestedScroll reads it as consumed and a parent list
      // cannot steal a scrub. A non-scrollable chart keeps the scrollable modifier for this.
      delta
    } else {
      val oldValue = value
      value += delta
      val consumedValue = value - oldValue
      if (oldValue + delta == value) {
        delta
      } else {
        unconsumedXDeltas.tryEmit(consumedValue - delta)
        consumedValue
      }
    }
  }

  private val isScrollInProgress = snapshotFlow { scrollableState.isScrollInProgress }

  /** The current scroll value (in pixels). */
  public var value: Float
    get() = _value.floatValue
    private set(newValue) {
      val oldValue = value
      _value.floatValue = newValue.coerceIn(0f.rangeWith(maxValue))
      if (value != oldValue) consumedXDeltas.tryEmit(oldValue - value)
    }

  /** The maximum scroll value (in pixels). */
  public var maxValue: Float
    get() = _maxValue.floatValue
    internal set(newMaxValue) {
      if (newMaxValue == maxValue) return
      _maxValue.floatValue = newMaxValue
      value = value
    }

  internal constructor(
    scrollEnabled: Boolean,
    initialScroll: Scroll.Absolute,
    autoScroll: Scroll,
    autoScrollCondition: AutoScrollCondition,
    autoScrollAnimationSpec: AnimationSpec<Float>,
    xSnapStep: Double?,
    snapAnimationSpec: AnimationSpec<Float>,
    value: Float,
    initialScrollHandled: Boolean,
  ) {
    this.scrollEnabled = scrollEnabled
    this.initialScroll = initialScroll
    this.autoScroll = autoScroll
    this.autoScrollCondition = autoScrollCondition
    this.autoScrollAnimationSpec = autoScrollAnimationSpec
    this.xSnapStep = xSnapStep
    this.snapAnimationSpec = snapAnimationSpec
    _value = mutableFloatStateOf(value)
    this.initialScrollHandled = initialScrollHandled
  }

  /**
   * Houses information on a [CartesianChart]’s scroll value. Allows for scroll customization and
   * programmatic scrolling.
   *
   * @param scrollEnabled whether scroll is enabled.
   * @param initialScroll represents the initial scroll value.
   * @param autoScroll represents the scroll value or delta for automatic scrolling.
   * @param autoScrollCondition defines when an automatic scroll should occur.
   * @param autoScrollAnimationSpec the [AnimationSpec] for automatic scrolling.
   * @param xSnapStep if not null, the scroll will snap to multiples of this _x_-axis step after the
   *   user stops scrolling.
   * @param snapAnimationSpec the [AnimationSpec] for snap scrolling.
   */
  public constructor(
    scrollEnabled: Boolean = true,
    initialScroll: Scroll.Absolute = Scroll.Absolute.Start,
    autoScroll: Scroll = initialScroll,
    autoScrollCondition: AutoScrollCondition = AutoScrollCondition.Never,
    autoScrollAnimationSpec: AnimationSpec<Float> = spring(),
    xSnapStep: Double? = null,
    snapAnimationSpec: AnimationSpec<Float> = spring(),
  ) : this(
    scrollEnabled = scrollEnabled,
    initialScroll = initialScroll,
    autoScroll = autoScroll,
    autoScrollCondition = autoScrollCondition,
    autoScrollAnimationSpec = autoScrollAnimationSpec,
    xSnapStep = xSnapStep,
    snapAnimationSpec = snapAnimationSpec,
    value = 0f,
    initialScrollHandled = false,
  )

  public val isScrolling: Boolean
    get() = scrollableState.isScrollInProgress

  /**
   * True only while the user is moving the chart. [isScrolling] is also true during a scrub,
   * because the scrollable state stays active to block a parent list even though the position is
   * frozen.
   */
  public val isUserScrolling: Boolean
    get() = isScrolling && !isScrollFrozen

  /** The visible x data range, or null before the chart has been measured. */
  public val visibleXRange: ClosedFloatingPointRange<Double>?
    get() {
      val ctx = context ?: return null
      val dims = layerDimensions ?: return null
      val b = bounds ?: return null
      if (dims.xSpacing == 0f) return null
      val start = ctx.ranges.minX + (value - dims.startPadding) / dims.xSpacing * ctx.ranges.xStep
      return start..(start + b.width / dims.xSpacing * ctx.ranges.xStep)
    }

  /** The scroll value that puts [x] at the start of the visible window. */
  public fun xToScrollValue(x: Double): Float? {
    val ctx = context ?: return null
    val dims = layerDimensions ?: return null
    return dims.startPadding + ((x - ctx.ranges.minX) / ctx.ranges.xStep).toFloat() * dims.xSpacing
  }

  /**
   * As [xToScrollValue], but behind the space the layer reserves before its first entry.
   *
   * That space is [CartesianLayerDimensions.startPadding], which the chart sets itself, so there is
   * nothing for a caller to pass — and being told it separately only made it possible to be told a
   * different figure from the one actually reserved.
   */
  public fun xToScrollValueBehindStartInset(x: Double): Float? {
    val ctx = context ?: return null
    val dims = layerDimensions ?: return null
    return ((x - ctx.ranges.minX) / ctx.ranges.xStep).toFloat() * dims.xSpacing
  }

  /** The data x value at [scrollPixels], or at the current scroll when null. */
  public fun scrollValueToX(scrollPixels: Float? = null): Double? {
    val ctx = context ?: return null
    val dims = layerDimensions ?: return null
    if (dims.xSpacing == 0f) return null
    return ctx.ranges.minX +
      ((scrollPixels ?: value) - dims.startPadding) / dims.xSpacing * ctx.ranges.xStep
  }

  /**
   * The axis label x values inside the current scroll window.
   *
   * [labelProvider] takes precedence, for placement a step cannot express — month boundaries, or
   * Sundays. Otherwise [itemPlacer] is asked, and failing both the labels fall on xStep intervals.
   */
  public fun getVisibleAxisLabels(
    itemPlacer: HorizontalAxis.ItemPlacer? = null,
    labelProvider:
      ((
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
      ) -> List<Double>)? =
      null,
  ): List<Double> {
    val ctx = context ?: return emptyList()
    val range = visibleXRange ?: return emptyList()
    val fullRange = ctx.ranges.minX..ctx.ranges.maxX
    if (labelProvider != null) return labelProvider(range, fullRange)
    val context = drawingContext
    if (itemPlacer != null && context != null) {
      return itemPlacer.getLabelValues(context, range, fullRange, 0f)
    }
    val xStep = ctx.ranges.xStep
    if (xStep <= 0.0) return emptyList()
    val first = ceil((range.start - ctx.ranges.minX) / xStep).toLong()
    val last = floor((range.endInclusive - ctx.ranges.minX) / xStep).toLong()
    return (first..last).map { ctx.ranges.minX + it * xStep }
  }

  public fun getInterpolatedYValues(xValues: List<Double>): List<List<Double?>> {
    val ctx = context ?: return emptyList()
    val results = mutableListOf<List<Double?>>()
    for (layerModel in ctx.model.models) {
      if (layerModel is LineCartesianLayerModel) {
        for (series in layerModel.series) {
          results.add(xValues.map { x -> MonotoneInterpolator.getYAtXFromEntries(x, series) })
        }
      }
    }
    return results
  }


  private inline fun withUpdated(
    block: (CartesianMeasuringContext, CartesianLayerDimensions, Rect) -> Unit
  ) {
    val context = this.context
    val layerDimensions = this.layerDimensions
    val bounds = this.bounds
    if (context != null && layerDimensions != null && bounds != null) {
      block(context, layerDimensions, bounds)
    }
  }

  internal fun update(
    context: CartesianMeasuringContext,
    bounds: Rect,
    layerDimensions: CartesianLayerDimensions,
  ) {
    this.context = context
    this.layerDimensions = layerDimensions
    this.bounds = bounds
    // Capture the scroll value before updating `maxValue`. The `maxValue` setter clamps `value` to
    // the new maximum, so when the content shrinks on the start side (e.g., the data window was
    // trimmed) while the scroll is pinned to the end (`value == maxValue`), the clamp shifts
    // `value` back by the trimmed amount before the repositioning below runs. The repositioning
    // would then compute `startEdgeX` from the already-clamped value and roll the viewport toward
    // the start by the size of the trim.
    val preClampValue = value
    maxValue = context.getMaxScrollDistance(bounds.width, layerDimensions)
    val ranges = context.ranges
    val previous = previousScrollLayout
    if (!initialScrollHandled) {
      value = initialScroll.getValue(context, layerDimensions, bounds, maxValue)
      initialScrollHandled = true
    } else if (
      previous != null &&
        previous.xSpacing != 0f &&
        ranges.xStep != 0.0 &&
        (ranges.minX != previous.minX || ranges.xStep != previous.xStep)
    ) {
      val startEdgeX =
        previous.minX +
          (previous.layoutDirectionMultiplier * preClampValue - previous.startPadding) /
            previous.xSpacing * previous.xStep
      value =
        context.layoutDirectionMultiplier *
          (layerDimensions.startPadding +
            ((startEdgeX - ranges.minX) / ranges.xStep).toFloat() * layerDimensions.xSpacing)
    }
    previousScrollLayout =
      ScrollLayout(
        minX = ranges.minX,
        xStep = ranges.xStep,
        xSpacing = layerDimensions.xSpacing,
        startPadding = layerDimensions.startPadding,
        layoutDirectionMultiplier = context.layoutDirectionMultiplier,
      )
  }

  internal suspend fun autoScroll(model: CartesianChartModel, oldModel: CartesianChartModel?) {
    if (!autoScrollCondition.shouldScroll(oldModel, model)) return
    if (scrollableState.isScrollInProgress)
      scrollableState.stopScroll(MutatePriority.PreventUserInput)
    animateScroll(autoScroll, autoScrollAnimationSpec)
  }

  internal fun clearUpdated() {
    context = null
    layerDimensions = null
    bounds = null
  }

  /** Triggers a scroll. */
  public suspend fun scroll(scroll: Scroll) {
    isScrollInProgress.first { !it }
    withUpdated { context, layerDimensions, bounds ->
      scrollableState.scrollBy(scroll.getDelta(context, layerDimensions, bounds, maxValue, value))
    }
  }

  internal suspend fun scroll(scroll: Scroll, maxScroll: Float) {
    // This receives the scroll compensation for a zoom gesture—an absolute target computed
    // from the scroll value and the content width at the time of the zoom event. It’s valid
    // only while the user isn’t scrolling: if a pan or fling is already in progress (e.g., the
    // pinch turned into a pan without the scroll ever becoming idle for a frame), then by the
    // time the scroll stops, the target and `maxScroll` are stale, and applying them would
    // roll the viewport back to the position at the time of the zoom. Drop the stale
    // compensation; the zoom handler emits a fresh one on every zoom event.
    if (scrollableState.isScrollInProgress) return
    maxValue = maxScroll
    withUpdated { context, layerDimensions, bounds ->
      scrollableState.scrollBy(scroll.getDelta(context, layerDimensions, bounds, maxValue, value))
    }
  }

  /** Triggers an animated scroll. */
  public suspend fun animateScroll(scroll: Scroll, animationSpec: AnimationSpec<Float> = spring()) {
    withUpdated { context, layerDimensions, bounds ->
      val delta = scroll.getDelta(context, layerDimensions, bounds, maxValue, value)
      val duration =
        animationSpec
          .vectorize(Float.VectorConverter)
          .getDurationNanos(
            initialValue = AnimationVector(value),
            targetValue = AnimationVector(value + delta),
            initialVelocity = AnimationVector(0f),
          )
      if (duration == 0L) {
        scrollableState.scrollBy(delta)
      } else {
        scrollableState.animateScrollBy(delta, animationSpec)
      }
    }
  }

  internal fun getSnapDelta(targetValue: Float = value): Float? {
    val context = this.context ?: return null
    val layerDimensions = this.layerDimensions ?: return null
    return getSnapDelta(
      value = value,
      maxValue = maxValue,
      xSnapStep = xSnapStep,
      xStep = context.ranges.xStep,
      xSpacing = layerDimensions.xSpacing,
      startPadding = layerDimensions.startPadding,
      targetValue = targetValue,
    )
  }

  internal suspend fun performSnap(
    targetValue: Float = value,
    initialVelocity: Float = 0f,
    animationSpec: AnimationSpec<Float> = snapAnimationSpec,
  ) {
    val delta = getSnapDelta(targetValue) ?: return
    scrollableState.stopScroll(MutatePriority.PreventUserInput)
    isScrollInProgress.first { !it }
    scrollableState.scroll(MutatePriority.PreventUserInput) {
      var previousValue = 0f
      animate(
        initialValue = 0f,
        targetValue = delta,
        initialVelocity = initialVelocity,
        animationSpec = animationSpec,
      ) { value, _ ->
        scrollBy(value - previousValue)
        previousValue = value
      }
    }
  }

  internal companion object {
    fun Saver(
      scrollEnabled: Boolean,
      initialScroll: Scroll.Absolute,
      autoScroll: Scroll,
      autoScrollCondition: AutoScrollCondition,
      autoScrollAnimationSpec: AnimationSpec<Float>,
      xSnapStep: Double?,
      snapAnimationSpec: AnimationSpec<Float>,
    ) =
      Saver<VicoScrollState, Pair<Float, Boolean>>(
        save = { it.value to it.initialScrollHandled },
        restore = { (value, initialScrollHandled) ->
          VicoScrollState(
            scrollEnabled,
            initialScroll,
            autoScroll,
            autoScrollCondition,
            autoScrollAnimationSpec,
            xSnapStep,
            snapAnimationSpec,
            value,
            initialScrollHandled,
          )
        },
      )
  }
}

/**
 * Creates and remembers a [VicoScrollState] instance.
 *
 * @param xSnapStep if not null, the scroll will snap to multiples of this _x_-axis step after the
 *   user stops scrolling.
 * @param snapAnimationSpec the [AnimationSpec] for snap scrolling.
 */
@Composable
public fun rememberVicoScrollState(
  scrollEnabled: Boolean = true,
  initialScroll: Scroll.Absolute = Scroll.Absolute.Start,
  autoScroll: Scroll = initialScroll,
  autoScrollCondition: AutoScrollCondition = AutoScrollCondition.Never,
  autoScrollAnimationSpec: AnimationSpec<Float> = spring(),
  xSnapStep: Double? = null,
  snapAnimationSpec: AnimationSpec<Float> = spring(),
): VicoScrollState {
  val state =
    rememberSaveable(
      initialScroll,
      autoScroll,
      autoScrollCondition,
      autoScrollAnimationSpec,
      xSnapStep,
      snapAnimationSpec,
      saver =
        remember(
          scrollEnabled,
          initialScroll,
          autoScrollCondition,
          autoScrollAnimationSpec,
          xSnapStep,
          snapAnimationSpec,
        ) {
          VicoScrollState.Saver(
            scrollEnabled,
            initialScroll,
            autoScroll,
            autoScrollCondition,
            autoScrollAnimationSpec,
            xSnapStep,
            snapAnimationSpec,
          )
        },
    ) {
      VicoScrollState(
        scrollEnabled,
        initialScroll,
        autoScroll,
        autoScrollCondition,
        autoScrollAnimationSpec,
        xSnapStep,
        snapAnimationSpec,
      )
    }
  SideEffect { state.scrollEnabled = scrollEnabled }
  return state
}

private data class ScrollLayout(
  val minX: Double,
  val xStep: Double,
  val xSpacing: Float,
  val startPadding: Float,
  val layoutDirectionMultiplier: Int,
)
