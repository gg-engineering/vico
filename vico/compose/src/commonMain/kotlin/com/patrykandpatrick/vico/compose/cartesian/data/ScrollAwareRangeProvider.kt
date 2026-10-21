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

package com.patrykandpatrick.vico.compose.cartesian.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.patrykandpatrick.vico.compose.common.Animation
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * A [CartesianLayerRangeProvider] that dynamically adjusts the Y range based on the currently
 * visible data points. Follows iOS-like animation: new ticks appear instantly (approximating
 * cross-fade), while chart content animates positionally via range interpolation.
 *
 * Uses binary search on sorted X values for O(log n) visible-window lookup — accurate for
 * both sequential and non-sequential X data.
 *
 * @param paddingEntries extra entries to include before and after the visible window
 *   for stable range computation. Default 1.
 * @param debounceMs milliseconds to wait after scroll settles before updating the range.
 * @param animDurationMs duration of the range animation in milliseconds.
 * @param onVisibleEntries callback receiving the visible entries per series as List<List<(x, y)>>.
 *   For single-series charts this is a list of one list. For multi-series (e.g. BP with 3 lines)
 *   this is a list of 3 lists. Returns a [Pair] of the display range and tick label values.
 */
public class ScrollAwareRangeProvider(
  private val paddingEntries: Int = 1,
  internal val debounceMs: Long = 100L,
  internal val animDurationMs: Int = Animation.RANGE_ANIM_DURATION,
  private val windowKey: String? = null,
  private val onVisibleEntries: (visibleEntries: List<List<Pair<Double, Double>>>, visibleXRange: ClosedRange<Double>) -> Pair<ClosedRange<Double>, List<Double>>,
) : CartesianLayerRangeProvider {

  /**
   * A chart's bounds, as supplied alongside the data rather than beside it.
   *
   * Any value may be [Double.NaN], meaning "no opinion" — the provider then falls through to the
   * model's own range for that bound.
   */
  public data class Window(
    val minX: Double = Double.NaN,
    val maxX: Double = Double.NaN,
    val seedMinY: Double = Double.NaN,
    val seedMaxY: Double = Double.NaN,
    /**
     * The slice the chart opens on — not [minX]..[maxX], which is the whole scrollable domain.
     *
     * Supplied so the opening axis can be derived here, from the entries that first frame will
     * show. The caller does not send an axis: it would have to compute one, and a second
     * implementation of that is what made the axis re-letter itself once the data landed.
     *
     * Err wide rather than narrow. The measured viewport opens behind a reserved inset whose
     * width is pixels, so no caller can know it exactly; a point drawn inside the opening window
     * but left out of its range runs off the plot, while an extra point only loosens the range
     * until the first scroll.
     */
    val openStartX: Double = Double.NaN,
    val openEndX: Double = Double.NaN,
  )

  public companion object {
    /**
     * The bounds to draw a model in, keyed by whatever the producer's consumers call themselves.
     *
     * Bounds are derived from the data they apply to, so sending them separately — through state
     * the chart reads on a later frame — lets a model arrive before its own window. The chart then
     * draws that data against the previous window, which on first load is no window at all.
     * Travelling in the transaction makes the two inseparable.
     *
     * Keyed because one producer can feed several charts that share a model and differ only in the
     * span they show; each looks itself up by the key it was built with.
     */
    public val WindowKey: ExtraStore.Key<Map<String, Window>> = ExtraStore.Key()
  }

  private fun windowFromExtras(extraStore: ExtraStore): Window? =
    windowKey?.let { key -> extraStore.getOrNull(WindowKey)?.get(key) }

  /** The window this provider's data arrived with, for the host to tell a move from a redraw. */
  internal fun openingWindow(extraStore: ExtraStore): Window? = windowFromExtras(extraStore)

  init {
    require(paddingEntries >= 0) { "paddingEntries must be >= 0" }
  }

  // Per-series entries sorted by X. allSeriesEntries[seriesIndex] = list of (x, y).
  private var allSeriesEntries: List<List<Pair<Double, Double>>> = emptyList()

  // Sorted unique X values across all series for binary search.
  private var sortedXValues: DoubleArray = DoubleArray(0)

  // Current animated range values — set by ScrollAwareRangeEffect animation.
  internal var currentMinY: Double = Double.NaN
  internal var currentMaxY: Double = Double.NaN

  // Animation target — the final yRange the animation is heading toward.
  // Set before animateTo(). Secondary layers read this to recompute immediately.
  public var targetMinY: Double = Double.NaN
    internal set
  public var targetMaxY: Double = Double.NaN
    internal set

  // X range override — set during composition via direct property access.
  public var xRangeMin: Double = Double.NaN
  public var xRangeMax: Double = Double.NaN

  // Seed Y range hint — used in getMinY/getMaxY before isCacheReady, eliminating frame-0
  // flash on first load and segment switches. Supplied by the caller from the last settled
  // range persisted in SegmentState, or from a synchronous initial-window computation.
  // Falls through to Vico intrinsic range if NaN (default).
  public var seedMinY: Double = Double.NaN
  public var seedMaxY: Double = Double.NaN

  // Whether entries have been loaded at least once.
  internal var isCacheReady: Boolean = false

  // What the cache and the opening range were built from. The chart's identity changes on
  // recompositions that have nothing to do with the data, and rebuilding on those would throw
  // away a range the user had scrolled to and replace it with the opening one. These are kept
  // apart because a model copied for a new ExtraStore carries the same series instance: the
  // cache can stand while the window moves, and the window can stand while the data changes.
  internal var cachedSeries: Any? = null
  internal var cachedWindow: Window? = null

  // Current tick labels — read by ListItemPlacer through [ticks].
  public var currentTicks: List<Double> = emptyList()
    internal set

  /**
   * Derives the opening range and gridlines from the window the data arrived with, before
   * anything has been drawn.
   *
   * This is the whole reason the range has one producer. The chart cannot wait for its own first
   * draw to decide what to draw — the entry cache is filled here but the visible window is only
   * reported from inside the Canvas — so without this a caller had to compute an opening axis
   * itself, and two implementations of the same arithmetic over slightly different entries is
   * exactly how the axis came to re-letter itself a moment after the data appeared. The callback
   * that answers every later scroll answers the first one too.
   *
   * Leaves the range untouched when there is no opening window or nothing falls inside it, so
   * the chart measures it the way it did before.
   */
  internal fun seedFromWindow(extraStore: ExtraStore) {
    val window = windowFromExtras(extraStore) ?: return
    val start = window.openStartX
    val end = window.openEndX
    if (start.isNaN() || end.isNaN() || end <= start) return
    val info = ScrollInfo(
      scrollPixels = 0f,
      xSpacing = 1f,
      chartWidth = 0f,
      visibleXStart = start,
      visibleXEnd = end,
    )
    val entries = computeVisibleEntries(info) ?: return
    val (range, ticks) = computeDisplayRange(entries, start..end) ?: return
    currentMinY = range.start
    currentMaxY = range.endInclusive
    currentTicks = ticks
  }

  // Flow for scroll updates — CartesianChartHost emits to this.
  internal val scrollUpdates = MutableSharedFlow<ScrollInfo>(replay = 1, extraBufferCapacity = 10)

  // Cache: avoid recomputing if visible window hasn't changed.
  private var lastVisibleStartIndex: Int = -1
  private var lastVisibleEndIndex: Int = -1
  private var lastVisibleEntries: List<List<Pair<Double, Double>>> = emptyList()

  // The window that came with the model is preferred over the one set during composition: it is
  // the one derived from this very data, where the field may still describe the previous model.
  override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore): Double =
    (windowFromExtras(extraStore)?.minX ?: xRangeMin).takeIf { !it.isNaN() } ?: minX

  override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore): Double =
    (windowFromExtras(extraStore)?.maxX ?: xRangeMax).takeIf { !it.isNaN() } ?: maxX

  // Priority: animated current → seed hint (frame-0, segment switch) → Vico intrinsic (full dataset)
  // The seed reverses the X precedence above: the field can be refreshed after a scroll settles,
  // by a caller that is not pushing a model, so it is the fresher of the two whenever it is set.
  override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
    if (isCacheReady && !currentMinY.isNaN()) currentMinY
    else (seedMinY.takeIf { !it.isNaN() } ?: windowFromExtras(extraStore)?.seedMinY)
      ?.takeIf { !it.isNaN() } ?: minY

  override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
    if (isCacheReady && !currentMaxY.isNaN()) currentMaxY
    else (seedMaxY.takeIf { !it.isNaN() } ?: windowFromExtras(extraStore)?.seedMaxY)
      ?.takeIf { !it.isNaN() } ?: maxY

  /**
   * Builds per-series entry lists + a merged sorted X array for binary search.
   * Called when the model structure changes.
   */
  internal fun buildCache(series: List<List<LineCartesianLayerModel.Entry>>) {
    // Cleared first, on every path: this model's own ticks win until it has settled ones of its
    // own, and an empty push must not leave the previous model's drawn against the new data.
    currentTicks = emptyList()
    lastVisibleStartIndex = -1
    lastVisibleEndIndex = -1
    lastVisibleEntries = emptyList()
    if (series.isEmpty() || series.first().isEmpty()) {
      allSeriesEntries = emptyList()
      sortedXValues = DoubleArray(0)
      isCacheReady = false
      return
    }
    // Store each series separately, sorted by X
    allSeriesEntries = series.map { s ->
      s.sortedBy { it.x }.map { it.x to it.y }
    }
    // Merged unique sorted X values for binary search
    val allX = mutableSetOf<Double>()
    for (s in allSeriesEntries) {
      for ((x, _) in s) allX.add(x)
    }
    sortedXValues = allX.toDoubleArray().also { it.sort() }
    isCacheReady = true
  }

  /**
   * Computes the visible entries per series based on scroll info.
   * Uses binary search on merged X values — O(log n).
   * Returns null if not ready or no entries in visible range.
   */
  internal fun computeVisibleEntries(info: ScrollInfo): List<List<Pair<Double, Double>>>? {
    if (!isCacheReady || allSeriesEntries.isEmpty() || info.xSpacing <= 0f) return null

    val visibleXStart = info.visibleXStart
    val visibleXEnd = info.visibleXEnd

    // Binary search on merged X values for window bounds
    var startIndex = sortedXValues.binarySearchInsertionPoint(visibleXStart)
    var endIndex = sortedXValues.binarySearchInsertionPoint(visibleXEnd)

    startIndex = startIndex.coerceIn(0, sortedXValues.lastIndex)
    endIndex = endIndex.coerceIn(0, sortedXValues.lastIndex)

    val paddedStart = (startIndex - paddingEntries).coerceAtLeast(0)
    val paddedEnd = (endIndex + paddingEntries).coerceAtMost(sortedXValues.lastIndex)

    if (paddedStart > paddedEnd) return null

    // Cache check
    if (paddedStart == lastVisibleStartIndex && paddedEnd == lastVisibleEndIndex) {
      return lastVisibleEntries
    }

    val xMin = sortedXValues[paddedStart]
    val xMax = sortedXValues[paddedEnd]

    // Filter each series to the visible X window
    val result = allSeriesEntries.map { seriesEntries ->
      seriesEntries.filter { (x, _) -> x in xMin..xMax }
    }

    lastVisibleStartIndex = paddedStart
    lastVisibleEndIndex = paddedEnd
    lastVisibleEntries = result
    return result
  }

  /**
   * Calls the consumer's callback with visible entries per series.
   * Returns null if the callback returns a zero-length range.
   */
  internal fun computeDisplayRange(
    visibleEntries: List<List<Pair<Double, Double>>>,
    visibleXRange: ClosedRange<Double>,
  ): Pair<ClosedRange<Double>, List<Double>>? {
    if (visibleEntries.all { it.isEmpty() }) return null
    val result = try {
      onVisibleEntries(visibleEntries, visibleXRange)
    } catch (_: Exception) {
      return null
    }
    val range = result.first
    val length = range.endInclusive - range.start
    if (length <= 0.0 || length.isNaN() || length.isInfinite()) return null
    return result
  }

  internal data class ScrollInfo(
    val scrollPixels: Float,
    val xSpacing: Float,
    val chartWidth: Float,
    val visibleXStart: Double,
    val visibleXEnd: Double,
  )
}

/**
 * Binary search insertion point — returns the index where [value] would be inserted
 * to maintain sorted order. O(log n).
 */
private fun DoubleArray.binarySearchInsertionPoint(value: Double): Int {
  var low = 0
  var high = size
  while (low < high) {
    val mid = (low + high) ushr 1
    if (this[mid] < value) low = mid + 1 else high = mid
  }
  return low
}

/**
 * Creates and remembers a [ScrollAwareRangeProvider].
 *
 * @param paddingEntries extra entries before/after visible window (default 1)
 * @param debounceMs debounce delay after scroll settles (default 100ms)
 * @param animDurationMs range animation duration (default 300ms)
 * @param onVisibleEntries callback receiving visible entries per series as List<List<(x, y)>>.
 *   Returns display range and tick labels.
 */
@Composable
public fun rememberScrollAwareRangeProvider(
  paddingEntries: Int = 1,
  debounceMs: Long = 100L,
  animDurationMs: Int = 300,
  minX: Double = Double.NaN,
  maxX: Double = Double.NaN,
  seedMinY: Double = Double.NaN,
  seedMaxY: Double = Double.NaN,
  windowKey: String? = null,
  onVisibleEntries: (visibleEntries: List<List<Pair<Double, Double>>>, visibleXRange: ClosedRange<Double>) -> Pair<ClosedRange<Double>, List<Double>>,
): ScrollAwareRangeProvider {
  val callbackRef = rememberUpdatedState(onVisibleEntries)
  val provider = remember(paddingEntries, debounceMs, animDurationMs, windowKey) {
    ScrollAwareRangeProvider(paddingEntries, debounceMs, animDurationMs, windowKey) { entries, xRange ->
      callbackRef.value(entries, xRange)
    }
  }
  provider.xRangeMin = minX
  provider.xRangeMax = maxX
  provider.seedMinY = seedMinY
  provider.seedMaxY = seedMaxY
  return provider
}
