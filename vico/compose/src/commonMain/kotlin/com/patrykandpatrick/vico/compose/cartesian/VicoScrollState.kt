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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Rect
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.layer.MonotoneInterpolator
import com.patrykandpatrick.vico.compose.common.rangeWith
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
  private val _value: MutableFloatState
  private val _maxValue = mutableFloatStateOf(0f)
  internal var initialScrollHandled: Boolean
  private var context: CartesianMeasuringContext? = null
  internal var drawingContext: CartesianDrawingContext? = null

  /** Whether the chart is currently being scrolled by the user. */
  public val isScrolling: Boolean get() = scrollableState.isScrollInProgress

  /**
   * True only while the user is actively scrolling the chart — the scroll
   * position is changing. Distinct from [isScrolling], which also returns
   * true during a marker scrub (the underlying `ScrollableState` stays
   * active so `nestedScroll` blocks a parent `LazyColumn`, even though the
   * position is frozen). Use this when you want to treat scrolling and
   * scrubbing as separate concerns.
   */
  public val isUserScrolling: Boolean get() = isScrolling && !isScrollFrozen
  private var layerDimensions: CartesianLayerDimensions? = null
  private var bounds: Rect? = null
  /** Converts a data X value to a scroll pixel value. Returns null if context not ready. */
  internal fun xToScrollValue(x: Double): Float? {
    val ctx = context ?: return null
    val dims = layerDimensions ?: return null
    return dims.startPadding +
      ((x - ctx.ranges.minX) / ctx.ranges.xStep).toFloat() * dims.xSpacing
  }

  /** The currently visible X data range, or null if context not ready. */
  public val visibleXRange: ClosedFloatingPointRange<Double>?
    get() {
      val ctx = context ?: return null
      val dims = layerDimensions ?: return null
      val b = bounds ?: return null
      if (dims.xSpacing == 0f) return null
      val start = ctx.ranges.minX + (value - dims.startPadding) / dims.xSpacing * ctx.ranges.xStep
      val end = start + b.width / dims.xSpacing * ctx.ranges.xStep
      return start..end
    }

  /** Like [xToScrollValue] but subtracts [paddingXStep] * xSpacing — positions X at padding offset from edge. */
  /**
   * The scroll value that puts [x] at the start of the visible window, behind the space the layer
   * reserves before its first entry.
   *
   * That space is [CartesianLayerDimensions.startPadding], which the chart sets from its own
   * startInsetXStep, so there is nothing for a caller to pass: adding it and taking the same
   * amount off again leaves the offset alone. Being told it separately only made it possible to
   * be told a different figure from the one the chart reserved. (MOB-2953)
   */
  internal fun xToScrollValueBehindStartInset(x: Double): Float? {
    val ctx = context ?: return null
    val dims = layerDimensions ?: return null
    return ((x - ctx.ranges.minX) / ctx.ranges.xStep).toFloat() * dims.xSpacing
  }

  /** Converts a scroll pixel value to a data X value. Uses current scroll if [scrollPixels] is null. */
  internal fun scrollValueToX(scrollPixels: Float? = null): Double? {
    val ctx = context ?: return null
    val dims = layerDimensions ?: return null
    if (dims.xSpacing == 0f) return null
    val px = scrollPixels ?: value
    return ctx.ranges.minX + (px - dims.startPadding) / dims.xSpacing * ctx.ranges.xStep
  }

  /**
   * Returns the aligned axis label X values visible in the current scroll window.
   * For custom label placement (e.g., month boundaries, Sundays), pass [labelProvider]
   * which receives the visible X range and full X range.
   */
  public fun getVisibleAxisLabels(
    itemPlacer: HorizontalAxis.ItemPlacer? = null,
    labelProvider: ((visibleXRange: ClosedFloatingPointRange<Double>, fullXRange: ClosedFloatingPointRange<Double>) -> List<Double>)? = null,
  ): List<Double> {
    val ctx = context ?: return emptyList()
    val range = visibleXRange ?: return emptyList()
    val fullRange = ctx.ranges.minX..ctx.ranges.maxX

    // If consumer provides a label provider, use it
    if (labelProvider != null) return labelProvider(range, fullRange)

    // If item placer provided, use its label values via drawing context
    if (itemPlacer != null && drawingContext != null) {
      return itemPlacer.getLabelValues(drawingContext!!, range, fullRange, 0f)
    }

    // Default: aligned labels at xStep intervals
    val xStep = ctx.ranges.xStep
    if (xStep <= 0.0) return emptyList()
    val labels = mutableListOf<Double>()
    val startK = kotlin.math.ceil((range.start - ctx.ranges.minX) / xStep).toLong()
    val endK = kotlin.math.floor((range.endInclusive - ctx.ranges.minX) / xStep).toLong()
    for (k in startK..endK) {
      labels.add(ctx.ranges.minX + k * xStep)
    }
    return labels
  }

  /**
   * Computes interpolated Y values for the given [xValues] across all series in the current model.
   * Returns one inner list per series. Each inner list has the same size as [xValues].
   * Uses the current chart model's data — returns empty if model not ready.
   * Zero Pair allocation — works directly on Entry objects.
   */
  public fun getInterpolatedYValues(
    xValues: List<Double>,
    interpolationType: InterpolationType = InterpolationType.MONOTONE,
  ): List<List<Double?>> {
    val ctx = context ?: return emptyList()
    val model = ctx.model
    val results = mutableListOf<List<Double?>>()
    for (layerModel in model.models) {
      if (layerModel is LineCartesianLayerModel) {
        for (series in layerModel.series) {
          val yValues = when (interpolationType) {
            InterpolationType.MONOTONE -> xValues.map { x ->
              MonotoneInterpolator.getYAtXFromEntries(x, series)
            }
            InterpolationType.LINEAR -> xValues.map { x ->
              linearInterpolateFromEntries(x, series)
            }
          }
          results.add(yValues)
        }
      }
    }
    return results
  }

  private fun linearInterpolateFromEntries(
    x: Double,
    entries: List<LineCartesianLayerModel.Entry>,
  ): Double? {
    if (entries.size < 2) return entries.firstOrNull()?.y
    if (x < entries.first().x) return null
    if (x > entries.last().x) return null
    for (i in 0 until entries.lastIndex) {
      val x0 = entries[i].x; val y0 = entries[i].y
      val x1 = entries[i + 1].x; val y1 = entries[i + 1].y
      if (x in x0..x1) {
        val t = (x - x0) / (x1 - x0)
        return y0 + t * (y1 - y0)
      }
    }
    return null
  }

  internal val scrollEnabled: Boolean
  internal val consumedXDeltas = MutableSharedFlow<Float>(extraBufferCapacity = 1)
  internal val unconsumedXDeltas = MutableSharedFlow<Float>(extraBufferCapacity = 1)

  /** When true, scroll position is frozen (scrubbing). ScrollableState still claims deltas
   *  to keep nestedScroll active (blocking parent LazyColumn), but position doesn't change. */
  internal var isScrollFrozen: Boolean = false

  internal val scrollableState = ScrollableState { delta ->
    if (isScrollFrozen || !scrollEnabled) {
      // Claim the delta (nestedScroll sees it as consumed → parent blocked)
      // but don't actually move the scroll position.
      // !scrollEnabled: chart is non-scrollable (TOTAL/single-window) but scrollable modifier
      // stays enabled so nestedScroll blocks parent during scrubbing.
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
    value: Float,
    initialScrollHandled: Boolean,
  ) {
    this.scrollEnabled = scrollEnabled
    this.initialScroll = initialScroll
    this.autoScroll = autoScroll
    this.autoScrollCondition = autoScrollCondition
    this.autoScrollAnimationSpec = autoScrollAnimationSpec
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
   */
  public constructor(
    scrollEnabled: Boolean,
    initialScroll: Scroll.Absolute,
    autoScroll: Scroll,
    autoScrollCondition: AutoScrollCondition,
    autoScrollAnimationSpec: AnimationSpec<Float>,
  ) : this(
    scrollEnabled = scrollEnabled,
    initialScroll = initialScroll,
    autoScroll = autoScroll,
    autoScrollCondition = autoScrollCondition,
    autoScrollAnimationSpec = autoScrollAnimationSpec,
    value = 0f,
    initialScrollHandled = false,
  )

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
    val prevMaxValue = maxValue
    maxValue = context.getMaxScrollDistance(bounds.width, layerDimensions)
    if (!initialScrollHandled || (prevMaxValue != maxValue && prevMaxValue > 0f && !scrollableState.isScrollInProgress)) {
      value = initialScroll.getValue(context, layerDimensions, bounds, maxValue)
      initialScrollHandled = true
    }
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
    isScrollInProgress.first { !it }
    maxValue = maxScroll
    withUpdated { context, layerDimensions, bounds ->
      scrollableState.scrollBy(scroll.getDelta(context, layerDimensions, bounds, maxValue, value))
    }
  }

  /** Triggers an animated scroll. */
  public suspend fun animateScroll(scroll: Scroll, animationSpec: AnimationSpec<Float> = spring()) {
    withUpdated { context, layerDimensions, bounds ->
      scrollableState.animateScrollBy(
        scroll.getDelta(context, layerDimensions, bounds, maxValue, value),
        animationSpec,
      )
    }
  }

  internal companion object {
    fun Saver(
      scrollEnabled: Boolean,
      initialScroll: Scroll.Absolute,
      autoScroll: Scroll,
      autoScrollCondition: AutoScrollCondition,
      autoScrollAnimationSpec: AnimationSpec<Float>,
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
            value,
            initialScrollHandled,
          )
        },
      )
  }
}

/** Creates and remembers a [VicoScrollState] instance. */
@Composable
public fun rememberVicoScrollState(
  scrollEnabled: Boolean = true,
  initialScroll: Scroll.Absolute = Scroll.Absolute.Start,
  autoScroll: Scroll = initialScroll,
  autoScrollCondition: AutoScrollCondition = AutoScrollCondition.Never,
  autoScrollAnimationSpec: AnimationSpec<Float> = spring(),
  key: Any? = null,
): VicoScrollState =
  rememberSaveable(
    key,
    scrollEnabled,
    initialScroll,
    autoScroll,
    autoScrollCondition,
    autoScrollAnimationSpec,
    saver =
      remember(key, scrollEnabled, initialScroll, autoScrollCondition, autoScrollAnimationSpec) {
        VicoScrollState.Saver(
          scrollEnabled,
          initialScroll,
          autoScroll,
          autoScrollCondition,
          autoScrollAnimationSpec,
        )
      },
  ) {
    VicoScrollState(
      scrollEnabled,
      initialScroll,
      autoScroll,
      autoScrollCondition,
      autoScrollAnimationSpec,
    )
  }
