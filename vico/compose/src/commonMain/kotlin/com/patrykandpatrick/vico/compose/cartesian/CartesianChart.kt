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

@file:OptIn(ExperimentalUuidApi::class)

package com.patrykandpatrick.vico.compose.cartesian

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.patrykandpatrick.vico.compose.cartesian.CartesianChart.PersistentMarkerScope
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.AxisManager
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartRanges
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.MutableCartesianChartRanges
import com.patrykandpatrick.vico.compose.cartesian.decoration.Decoration
import com.patrykandpatrick.vico.compose.cartesian.layer.*
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.marker.MutableLineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.common.*
import com.patrykandpatrick.vico.compose.common.data.CacheStore
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.data.MutableExtraStore
import kotlin.math.abs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A chart based on a Cartesian coordinate plane, composed of [CartesianLayer]s. */
@Stable
public open class CartesianChart
internal constructor(
  vararg layers: CartesianLayer<*>,
  startAxis: Axis<Axis.Position.Vertical.Start>? = null,
  topAxis: Axis<Axis.Position.Horizontal.Top>? = null,
  endAxis: Axis<Axis.Position.Vertical.End>? = null,
  bottomAxis: Axis<Axis.Position.Horizontal.Bottom>? = null,
  internal val marker: CartesianMarker? = null,
  protected val markerVisibilityListener: CartesianMarkerVisibilityListener? = null,
  internal val layerPadding: ((ExtraStore) -> CartesianLayerPadding) = { CartesianLayerPadding() },
  protected val legend: Legend<CartesianMeasuringContext, CartesianDrawingContext>? = null,
  protected val fadingEdges: FadingEdges? = null,
  protected val decorations: List<Decoration> = emptyList(),
  protected val persistentMarkers: (PersistentMarkerScope.(ExtraStore) -> Unit)? = null,
  protected val getXStep: ((CartesianChartModel) -> Double) = { it.getXDeltaGcd() },
  public val visibleLabelsCount: Double = 0.0,
  public val startInsetXStep: Double = 0.0,
  public val markerController: CartesianMarkerController = CartesianMarkerController.showOnPress(),
  internal val id: Uuid = Uuid.random(),
  private var previousMarkerTargetHashCode: Int? = null,
  private val persistentMarkerMap: MutableMap<Double, CartesianMarker> = mutableMapOf(),
  private var previousPersistentMarkerHashCode: Int? = null,
) : CartesianLayerMarginUpdater<CartesianChartModel> {
  private val persistentMarkerScope = PersistentMarkerScope {
    persistentMarkerMap[it.toDouble()] = this
  }

  private val layerMargins = CartesianLayerMargins()
  private val axisManager = AxisManager()
  private val _markerTargets = mutableMapOf<Double, MutableList<CartesianMarker.Target>>()

  private val drawingConsumer =
    object : ModelAndLayerConsumer {
      lateinit var context: CartesianDrawingContext

      override fun <T : CartesianLayerModel> invoke(model: T?, layer: CartesianLayer<T>) {
        layer.draw(context, model ?: return)
        layer.markerTargets.forEach {
          _markerTargets.getOrPut(it.key) { mutableListOf() } += it.value
        }
      }
    }

  private val layerDimensionUpdateConsumer =
    object : ModelAndLayerConsumer {
      lateinit var context: CartesianMeasuringContext
      lateinit var layerDimensions: MutableCartesianLayerDimensions

      override fun <T : CartesianLayerModel> invoke(model: T?, layer: CartesianLayer<T>) {
        layer.updateDimensions(context, layerDimensions, model ?: return)
      }
    }

  private val rangeUpdateConsumer =
    object : ModelAndLayerConsumer {
      lateinit var ranges: MutableCartesianChartRanges

      override fun <T : CartesianLayerModel> invoke(model: T?, layer: CartesianLayer<T>) {
        layer.updateChartRanges(ranges, model ?: return)
      }
    }

  private val layerMarginUpdateConsumer =
    object : ModelAndLayerConsumer {
      lateinit var context: CartesianMeasuringContext
      lateinit var layerDimensions: CartesianLayerDimensions
      lateinit var layerMargins: CartesianLayerMargins

      override fun <T : CartesianLayerModel> invoke(model: T?, layer: CartesianLayer<T>) {
        layer.updateLayerMargins(context, layerMargins, layerDimensions, model ?: return)
      }
    }

  private val horizontalLayerMarginUpdateConsumer =
    object : ModelAndLayerConsumer {
      lateinit var context: CartesianMeasuringContext
      lateinit var horizontalLayerMargins: HorizontalCartesianLayerMargins
      var layerHeight: Float = 0f

      override fun <T : CartesianLayerModel> invoke(model: T?, layer: CartesianLayer<T>) {
        layer.updateHorizontalLayerMargins(
          context,
          horizontalLayerMargins,
          layerHeight,
          model ?: return,
        )
      }
    }

  private val transformationPreparationConsumer =
    object : ModelAndLayerConsumer {
      lateinit var extraStore: MutableExtraStore
      lateinit var ranges: CartesianChartRanges

      override fun <T : CartesianLayerModel> invoke(model: T?, layer: CartesianLayer<T>) {
        layer.prepareForTransformation(model, ranges, extraStore)
      }
    }

  internal var layerBounds: Rect = Rect.Zero

  /** The [CartesianLayer]s of which this [CartesianChart] is composed. */
  public val layers: List<CartesianLayer<*>> = layers.toList()

  /** Links _x_ values to [CartesianMarker.Target]s. */
  protected val markerTargets: Map<Double, List<CartesianMarker.Target>> = _markerTargets

  /** All X values that have marker targets. Cached — only rebuilt when targets change. */
  private var cachedMarkerTargetXValues: List<Double> = emptyList()
  private var cachedMarkerTargetSize: Int = -1
  internal val allMarkerTargetXValues: List<Double>
    get() {
      val currentSize = _markerTargets.size
      if (currentSize != cachedMarkerTargetSize) {
        cachedMarkerTargetXValues = _markerTargets.keys.toList()
        cachedMarkerTargetSize = currentSize
      }
      return cachedMarkerTargetXValues
    }

  /** The start [Axis]. */
  public val startAxis: Axis<Axis.Position.Vertical.Start>? by axisManager::startAxis

  /** The top [Axis]. */
  public val topAxis: Axis<Axis.Position.Horizontal.Top>? by axisManager::topAxis

  /** The end [Axis]. */
  public val endAxis: Axis<Axis.Position.Vertical.End>? by axisManager::endAxis

  /** The bottom [Axis]. */
  public val bottomAxis: Axis<Axis.Position.Horizontal.Bottom>? by axisManager::bottomAxis

  init {
    axisManager.startAxis = startAxis
    axisManager.topAxis = topAxis
    axisManager.endAxis = endAxis
    axisManager.bottomAxis = bottomAxis
  }

  protected constructor(
    vararg layers: CartesianLayer<*>,
    startAxis: Axis<Axis.Position.Vertical.Start>? = null,
    topAxis: Axis<Axis.Position.Horizontal.Top>? = null,
    endAxis: Axis<Axis.Position.Vertical.End>? = null,
    bottomAxis: Axis<Axis.Position.Horizontal.Bottom>? = null,
    marker: CartesianMarker? = null,
    markerVisibilityListener: CartesianMarkerVisibilityListener? = null,
    layerPadding: ((ExtraStore) -> CartesianLayerPadding) = { CartesianLayerPadding() },
    legend: Legend<CartesianMeasuringContext, CartesianDrawingContext>? = null,
    fadingEdges: FadingEdges? = null,
    decorations: List<Decoration> = emptyList(),
    persistentMarkers: (PersistentMarkerScope.(ExtraStore) -> Unit)? = null,
    getXStep: ((CartesianChartModel) -> Double) = { it.getXDeltaGcd() },
    markerController: CartesianMarkerController = CartesianMarkerController.showOnPress(),
  ) : this(
    layers = layers,
    startAxis = startAxis,
    topAxis = topAxis,
    endAxis = endAxis,
    bottomAxis = bottomAxis,
    marker = marker,
    markerVisibilityListener = markerVisibilityListener,
    layerPadding = layerPadding,
    legend = legend,
    fadingEdges = fadingEdges,
    decorations = decorations,
    persistentMarkers = persistentMarkers,
    getXStep = getXStep,
    markerController = markerController,
    id = Uuid.random(),
    previousMarkerTargetHashCode = null,
    persistentMarkerMap = mutableMapOf(),
    previousPersistentMarkerHashCode = null,
  )

  private fun setLayerBounds(left: Float, top: Float, right: Float, bottom: Float) {
    layerBounds = Rect(left, top, right, bottom)
  }

  internal fun prepare(
    context: CartesianMeasuringContext,
    layerDimensions: MutableCartesianLayerDimensions,
  ) {
    with(context) {
      _markerTargets.clear()
      layerMargins.clear()
      val persistentMarkerHashCode = 31 * persistentMarkers.hashCode() + model.extraStore.hashCode()
      if (persistentMarkerHashCode != previousPersistentMarkerHashCode) {
        updatePersistentMarkers(model.extraStore)
        previousPersistentMarkerHashCode = persistentMarkerHashCode
      }
      model.forEachWithLayer(
        layerDimensionUpdateConsumer.apply {
          this.context = context
          this.layerDimensions = layerDimensions
        }
      )
      startAxis?.updateLayerDimensions(context, layerDimensions)
      topAxis?.updateLayerDimensions(context, layerDimensions)
      endAxis?.updateLayerDimensions(context, layerDimensions)
      bottomAxis?.updateLayerDimensions(context, layerDimensions)
      val marginUpdaters = buildList {
        add(this@CartesianChart)
        addAll(axisManager.axisCache)
        marker?.let(::add)
        addAll(persistentMarkerMap.values)
      }
      marginUpdaters.forEach { updater ->
        updater.updateLayerMargins(context, layerMargins, layerDimensions, model)
      }
      val legendHeight = legend?.getHeight(context, canvasSize.width).orZero
      val freeHeight = canvasSize.height - layerMargins.vertical - legendHeight
      marginUpdaters.forEach { updater ->
        updater.updateHorizontalLayerMargins(context, layerMargins, freeHeight, model)
      }
      setLayerBounds(
        layerMargins.getLeft(isLtr),
        layerMargins.top,
        canvasSize.width - layerMargins.getRight(isLtr),
        canvasSize.height - layerMargins.bottom - legendHeight,
      )
      axisManager.setAxesBounds(context, canvasSize, layerBounds, layerMargins)
      legend?.setBounds(
        left = 0,
        top = layerBounds.bottom + layerMargins.bottom,
        right = canvasSize.width,
        bottom = layerBounds.bottom + layerMargins.bottom + legendHeight,
      )

      // Apply spacing adjustment for visible labels if specified (after layerBounds is set)
      if (visibleLabelsCount > 0) {
        val scaleFactor = calculateLabelSpacingScale(layerDimensions)
        if (scaleFactor != 1f) {
          layerDimensions.scale(scaleFactor)
        }
      }

      // Room before the first entry, given in x steps like [visibleLabelsCount] rather than in Dp,
      // so it stays a fixed fraction of a step however the spacing is scaled.
      //
      // getFullXRange extends the scrollable range back by startPadding, so reserving it here is
      // what lets the chart sit half a step before its first entry at the leftmost scroll position.
      // Asking for that gap through the scroll offset instead could not work: the scroll clamps at
      // zero, so at the start of the chart there was nothing behind the first entry to scroll into
      // and the gap collapsed — the first window drew its entry hard against the edge and laid its
      // labels out differently from every other window. Reserved, the space is real at every
      // position, and the region before the first entry is simply empty. (MOB-2953)
      if (startInsetXStep > 0) {
        layerDimensions.ensureValuesAtLeast(
          scalableStartPadding = (startInsetXStep * layerDimensions.xSpacing).toFloat(),
        )
      }
    }
  }

  private fun calculateLabelSpacingScale(
    layerDimensions: MutableCartesianLayerDimensions,
  ): Float {
    val availableWidth = layerBounds.width
    val currentSpacing = layerDimensions.xSpacing
    if (currentSpacing <= 0f || availableWidth <= 0f) return 1f
    // The window holds the labels asked for plus the space reserved before the first entry, since
    // that space is inside it. Callers used to add the two together themselves before passing the
    // sum, which meant the chart was told a total it could have worked out — and left them able to
    // add a different figure from the one they reserved. (MOB-2953)
    val desiredSpacing = availableWidth / (visibleLabelsCount + startInsetXStep)
    return (desiredSpacing / currentSpacing).toFloat()
  }

  private fun updatePersistentMarkers(extraStore: ExtraStore) {
    persistentMarkerMap.clear()
    persistentMarkers?.invoke(persistentMarkerScope, extraStore)
  }

  internal fun draw(context: CartesianDrawingContext) {
    drawingContext = context
    with(context) {
      // Nothing is painted in the reserved strip before the first entry.
      //
      // Held around the whole of this method, not around the layers alone: the line and points go
      // through layerBitmap, but the guidelines, separators, ticks and axis lines are drawn
      // straight onto the canvas by axisManager and the decorations, before and after it. Clipping
      // only the bitmap left all of those painting into the strip.
      //
      // The space itself is untouched — the layer keeps its width and the chart is measured
      // against the same bounds as ever. This governs what is visible there, nothing else. Once
      // scrolled past, the strip sits behind the layer's left edge and none of this applies.
      // (MOB-2953)
      val contentStart = layerBounds.left + layerDimensions.startPadding - scroll
      val hidesStrip = startInsetXStep > 0 && contentStart > layerBounds.left
      if (hidesStrip) {
        canvas.save()
        canvas.clipRect(contentStart, 0f, canvasSize.width, canvasSize.height)
      }
      if (fadingEdges != null) canvas.saveLayer(Rect(Offset.Zero, canvasSize), EmptyPaint)
      decorations.forEach { it.drawUnderLayers(context) }
      axisManager.drawUnderLayers(context)
      val (layerBitmap, layerCanvas) = getBitmap(cacheKeyNamespace)
      withCanvas(layerCanvas) {
        model.forEachWithLayer(drawingConsumer.apply { this.context = context })
      }
      // TreeMap keeps _markerTargets sorted by key — no per-frame sort needed
      forEachPersistentMarker { marker, targets -> marker.drawUnderLayers(context, targets) }
      val markerTargets = getMarkerTargets(markerX, markerSeriesIndex).ifEmpty {
        // If markerX doesn't match a data point, synthesize an interpolated target
        markerX?.let { synthesizeInterpolatedTargets(context, it) } ?: emptyList()
      }
      val drawMarker = markerTargets.isNotEmpty()
      if (drawMarker) marker?.drawUnderLayers(context, markerTargets)
      canvas.drawImage(layerBitmap, Offset.Zero, EmptyPaint)
      fadingEdges?.run {
        draw(context)
        canvas.restore()
      }
      axisManager.drawOverLayers(context)
      decorations.forEach { it.drawOverLayers(context) }
      forEachPersistentMarker { marker, targets -> marker.drawOverLayers(context, targets) }
      legend?.draw(context)
      if (drawMarker) marker?.drawOverLayers(context, markerTargets)
      if (hidesStrip) {
        canvas.restore()
        // Only the start axis's line follows, outside the clip: it marks where the chart begins,
        // so it is the one thing that still belongs there.
        axisManager.drawStartAxisLine(context)
      }
    }
    drawingContext = null
  }

  internal fun updateRanges(ranges: MutableCartesianChartRanges, model: CartesianChartModel) {
    ranges.xStep = getXStep(model)
    model.forEachWithLayer(rangeUpdateConsumer.apply { this.ranges = ranges })
  }

  override fun updateLayerMargins(
    context: CartesianMeasuringContext,
    layerMargins: CartesianLayerMargins,
    layerDimensions: CartesianLayerDimensions,
    model: CartesianChartModel,
  ) {
    context.model.forEachWithLayer(
      layerMarginUpdateConsumer.apply {
        this.context = context
        this.layerDimensions = layerDimensions
        this.layerMargins = layerMargins
      }
    )
  }

  override fun updateHorizontalLayerMargins(
    context: CartesianMeasuringContext,
    horizontalLayerMargins: HorizontalCartesianLayerMargins,
    layerHeight: Float,
    model: CartesianChartModel,
  ) {
    context.model.forEachWithLayer(
      horizontalLayerMarginUpdateConsumer.apply {
        this.context = context
        this.horizontalLayerMargins = horizontalLayerMargins
        this.layerHeight = layerHeight
      }
    )
  }

  internal fun prepareForTransformation(
    model: CartesianChartModel?,
    extraStore: MutableExtraStore,
    ranges: CartesianChartRanges,
  ) {
    model?.forEachWithLayer(
      transformationPreparationConsumer.apply {
        this.extraStore = extraStore
        this.ranges = ranges
      }
    ) ?: layers.forEach { it.prepareForTransformation(null, ranges, extraStore) }
  }

  internal suspend fun transform(extraStore: MutableExtraStore, fraction: Float) {
    layers.forEach { it.transform(extraStore, fraction) }
  }

  protected open fun CartesianChartModel.forEachWithLayer(consumer: ModelAndLayerConsumer) {
    val freeModels = models.toMutableList()
    layers.forEach { layer ->
      when (layer) {
        is ColumnCartesianLayer -> freeModels.consume(layer, consumer)
        is LineCartesianLayer -> freeModels.consume(layer, consumer)
        is CandlestickCartesianLayer -> freeModels.consume(layer, consumer)
        else -> throw IllegalArgumentException("Unexpected `CartesianLayer` implementation.")
      }
    }
  }

  private var drawingContext: CartesianDrawingContext? = null

  private inline fun forEachPersistentMarker(
    block: (CartesianMarker, List<CartesianMarker.Target>) -> Unit
  ) {
    persistentMarkerMap.forEach { (x, marker) ->
      val targets = markerTargets[x]
        ?: drawingContext?.let { synthesizeInterpolatedTargets(it, x) }?.takeIf { it.isNotEmpty() }
      if (targets != null) block(marker, targets)
    }
  }

  /** Returns the `CartesianMarker.Target`s for `x`. */
  public open fun getMarkerTargets(
    x: Double?,
    visibleXRange: ClosedFloatingPointRange<Double>,
  ): List<CartesianMarker.Target> =
    if (x == null || x !in visibleXRange || markerTargets.isEmpty()) {
      emptyList()
    } else {
      var targets = emptyList<CartesianMarker.Target>()
      var previousDelta = Double.POSITIVE_INFINITY
      for ((key, keyTargets) in markerTargets) {
        val delta = abs(key - x)
        if (delta > previousDelta) break
        targets = keyTargets
        previousDelta = delta
      }
      targets
    }

  private fun getMarkerTargets(x: Double?, seriesIndex: Int?): List<CartesianMarker.Target> {
    val marker = marker ?: return emptyList()
    return if (x == null || markerTargets.isEmpty()) {
      if (previousMarkerTargetHashCode != null) markerVisibilityListener?.onHidden(marker)
      previousMarkerTargetHashCode = null
      emptyList()
    } else {
      val allTargets = markerTargets[x] ?: return emptyList()
      val targets =
        if (seriesIndex != null) {
          val target =
            allTargets.getOrNull(seriesIndex)
              ?: return run {
                if (previousMarkerTargetHashCode != null) markerVisibilityListener?.onHidden(marker)
                previousMarkerTargetHashCode = null
                emptyList()
              }
          listOf(target)
        } else {
          allTargets
        }
      val targetHashCode = targets.hashCode()
      if (previousMarkerTargetHashCode == null) {
        markerVisibilityListener?.onShown(marker, targets)
      } else if (targetHashCode != previousMarkerTargetHashCode) {
        markerVisibilityListener?.onUpdated(marker, targets)
      }
      previousMarkerTargetHashCode = targetHashCode
      targets
    }
  }

  /**
   * Synthesizes a marker target at an arbitrary X by interpolating Y from the model data.
   * Used when the consumer's callback returns an X that doesn't match any data point.
   *
   * Performance: zero list allocations — uses [MonotoneInterpolator.getYAtX] overload that
   * works directly on Entry objects with O(log n) binary search per series.
   */
  private fun synthesizeInterpolatedTargets(
    context: CartesianDrawingContext,
    x: Double,
  ): List<CartesianMarker.Target> {
    val ranges = context.ranges
    val layerDimensions = context.layerDimensions
    val layerBounds = context.layerBounds

    // Compute canvasX for the target X
    val drawingStart = layerBounds.getStart(context.isLtr) +
      context.layoutDirectionMultiplier * layerDimensions.startPadding - context.scroll
    val canvasX = drawingStart +
      context.layoutDirectionMultiplier * layerDimensions.xSpacing *
      ((x - ranges.minX) / ranges.xStep).toFloat()

    // Out of visible bounds — don't synthesize
    if (canvasX < layerBounds.left - 1 || canvasX > layerBounds.right + 1) return emptyList()

    // Create one target PER enabled layer — matches the real-data path where each
    // layer adds targets independently via updateMarkerTargets(). Layers with
    // markerTargetsEnabled = false (e.g. CDC percentile bands) are skipped entirely.
    val result = mutableListOf<CartesianMarker.Target>()
    for ((layerIndex, layerModel) in context.model.models.withIndex()) {
      if (layerModel !is LineCartesianLayerModel) continue
      val layer = layers.getOrNull(layerIndex) as? LineCartesianLayer ?: continue
      if (!layer.markerTargetsEnabled) continue

      val yRange = ranges.getYRange(layer.internalVerticalAxisPosition)
      val target = MutableLineCartesianLayerMarkerTarget(x, canvasX)

      for ((_, series) in layerModel.series.withIndex()) {
        val interpolatedY = MonotoneInterpolator.getYAtXFromEntries(x, series) ?: continue
        val canvasY = layerBounds.bottom -
          ((interpolatedY - yRange.minY) / yRange.length).toFloat() * layerBounds.height

        target.points += LineCartesianLayerMarkerTarget.Point(
          entry = LineCartesianLayerModel.Entry(x, interpolatedY),
          canvasY = canvasY.coerceIn(layerBounds.top, layerBounds.bottom),
          color = Color.Transparent,
          isInterpolated = true,
        )
      }
      if (target.points.isNotEmpty()) result += target
    }

    return result
  }

  protected inline fun <reified T : CartesianLayerModel> MutableList<CartesianLayerModel>.consume(
    layer: CartesianLayer<T>,
    consumer: ModelAndLayerConsumer,
  ) {
    val model = filterIsInstance<T>().firstOrNull()
    consumer(model, layer)
    if (model != null) remove(model)
  }

  protected interface ModelAndLayerConsumer {
    public operator fun <T : CartesianLayerModel> invoke(model: T?, layer: CartesianLayer<T>)
  }

  /** Creates a new [CartesianChart] based on this one. */
  public fun copy(
    vararg layers: CartesianLayer<*> = this.layers.toTypedArray(),
    startAxis: Axis<Axis.Position.Vertical.Start>? = this.startAxis,
    topAxis: Axis<Axis.Position.Horizontal.Top>? = this.topAxis,
    endAxis: Axis<Axis.Position.Vertical.End>? = this.endAxis,
    bottomAxis: Axis<Axis.Position.Horizontal.Bottom>? = this.bottomAxis,
    marker: CartesianMarker? = this.marker,
    markerVisibilityListener: CartesianMarkerVisibilityListener? = this.markerVisibilityListener,
    layerPadding: ((ExtraStore) -> CartesianLayerPadding) = this.layerPadding,
    legend: Legend<CartesianMeasuringContext, CartesianDrawingContext>? = this.legend,
    fadingEdges: FadingEdges? = this.fadingEdges,
    decorations: List<Decoration> = this.decorations,
    persistentMarkers: (PersistentMarkerScope.(ExtraStore) -> Unit)? = this.persistentMarkers,
    getXStep: ((CartesianChartModel) -> Double) = this.getXStep,
    visibleLabelsCount: Double = this.visibleLabelsCount,
    startInsetXStep: Double = this.startInsetXStep,
    markerController: CartesianMarkerController = CartesianMarkerController.showOnPress(),
  ): CartesianChart =
    CartesianChart(
      layers = layers,
      startAxis = startAxis,
      topAxis = topAxis,
      endAxis = endAxis,
      bottomAxis = bottomAxis,
      marker = marker,
      markerVisibilityListener = markerVisibilityListener,
      layerPadding = layerPadding,
      legend = legend,
      fadingEdges = fadingEdges,
      decorations = decorations,
      persistentMarkers = persistentMarkers,
      getXStep = getXStep,
      visibleLabelsCount = visibleLabelsCount,
      startInsetXStep = startInsetXStep,
      markerController = markerController,
      id = id,
      previousMarkerTargetHashCode = previousMarkerTargetHashCode,
      persistentMarkerMap = persistentMarkerMap,
      previousPersistentMarkerHashCode = previousPersistentMarkerHashCode,
    )

  override fun equals(other: Any?): Boolean =
    this === other ||
      other is CartesianChart &&
        id == other.id &&
        marker == other.marker &&
        markerVisibilityListener == other.markerVisibilityListener &&
        layerPadding == other.layerPadding &&
        legend == other.legend &&
        fadingEdges == other.fadingEdges &&
        decorations == other.decorations &&
        persistentMarkers == other.persistentMarkers &&
        getXStep == other.getXStep &&
        startInsetXStep == other.startInsetXStep &&
        layers == other.layers &&
        startAxis == other.startAxis &&
        topAxis == other.topAxis &&
        endAxis == other.endAxis &&
        bottomAxis == other.bottomAxis &&
        markerController == other.markerController

  override fun hashCode(): Int {
    var result = marker.hashCode()
    result = 31 * result + markerVisibilityListener.hashCode()
    result = 31 * result + layerPadding.hashCode()
    result = 31 * result + legend.hashCode()
    result = 31 * result + fadingEdges.hashCode()
    result = 31 * result + decorations.hashCode()
    result = 31 * result + persistentMarkers.hashCode()
    result = 31 * result + getXStep.hashCode()
    result = 31 * result + layers.hashCode()
    result = 31 * result + startAxis.hashCode()
    result = 31 * result + topAxis.hashCode()
    result = 31 * result + endAxis.hashCode()
    result = 31 * result + bottomAxis.hashCode()
    result = 31 * result + id.hashCode()
    result = 31 * result + markerController.hashCode()
    return result
  }

  /** Facilitates adding persistent [CartesianMarker]s to [CartesianChart]s. */
  public fun interface PersistentMarkerScope {
    /** Adds this [CartesianMarker] at [x]. */
    public infix fun CartesianMarker.at(x: Number)
  }

  protected companion object {
    public val cacheKeyNamespace: CacheStore.KeyNamespace = CacheStore.KeyNamespace()
  }
}

/**
 * Creates and remembers a [CartesianChart].
 *
 * @param layers the [CartesianLayer]s.
 * @param startAxis the start [Axis].
 * @param topAxis the top [Axis].
 * @param endAxis the end [Axis].
 * @param bottomAxis the bottom [Axis].
 * @param marker appears when the [CartesianChart] is tapped.
 * @param markerVisibilityListener allows for listening to [marker] visibility changes.
 * @param layerPadding returns the [CartesianLayerPadding].
 * @param legend the legend.
 * @param fadingEdges applies a horizontal fade to the edges of the [CartesianChart], provided that
 *   it’s scrollable.
 * @param decorations the [Decoration]s.
 * @param persistentMarkers adds persistent [CartesianMarker]s.
 * @param getXStep defines the _x_ step (the difference between neighboring major _x_ values).
 * @param markerController controls [marker] visibility.
 * @see rememberCandlestickCartesianLayer
 * @see rememberColumnCartesianLayer
 * @see rememberLineCartesianLayer
 */
@Composable
public fun rememberCartesianChart(
  vararg layers: CartesianLayer<*>,
  startAxis: Axis<Axis.Position.Vertical.Start>? = null,
  topAxis: Axis<Axis.Position.Horizontal.Top>? = null,
  endAxis: Axis<Axis.Position.Vertical.End>? = null,
  bottomAxis: Axis<Axis.Position.Horizontal.Bottom>? = null,
  marker: CartesianMarker? = null,
  markerVisibilityListener: CartesianMarkerVisibilityListener? = null,
  layerPadding: ((ExtraStore) -> CartesianLayerPadding) = { CartesianLayerPadding() },
  legend: Legend<CartesianMeasuringContext, CartesianDrawingContext>? = null,
  fadingEdges: FadingEdges? = null,
  decorations: List<Decoration> = emptyList(),
  persistentMarkers: (PersistentMarkerScope.(ExtraStore) -> Unit)? = null,
  getXStep: ((CartesianChartModel) -> Double) = { it.getXDeltaGcd() },
  visibleLabelsCount: Double = 0.0,
  startInsetXStep: Double = 0.0,
  markerController: CartesianMarkerController = CartesianMarkerController.rememberShowOnPress(),
): CartesianChart {
  val wrapper = remember { ValueWrapper<CartesianChart?>(null) }
  return remember(
    *layers,
    startAxis,
    topAxis,
    endAxis,
    bottomAxis,
    marker,
    markerVisibilityListener,
    layerPadding,
    legend,
    fadingEdges,
    decorations,
    persistentMarkers,
    getXStep,
    visibleLabelsCount,
    startInsetXStep,
    markerController,
  ) {
    val cartesianChart =
      wrapper.value?.copy(
        layers = layers,
        startAxis = startAxis,
        topAxis = topAxis,
        endAxis = endAxis,
        bottomAxis = bottomAxis,
        marker = marker,
        markerVisibilityListener = markerVisibilityListener,
        layerPadding = layerPadding,
        legend = legend,
        fadingEdges = fadingEdges,
        decorations = decorations,
        persistentMarkers = persistentMarkers,
        getXStep = getXStep,
        visibleLabelsCount = visibleLabelsCount,
        startInsetXStep = startInsetXStep,
        markerController = markerController,
      )
        ?: CartesianChart(
          layers = layers,
          startAxis = startAxis,
          topAxis = topAxis,
          endAxis = endAxis,
          bottomAxis = bottomAxis,
          marker = marker,
          markerVisibilityListener = markerVisibilityListener,
          layerPadding = layerPadding,
          legend = legend,
          fadingEdges = fadingEdges,
          decorations = decorations,
          persistentMarkers = persistentMarkers,
          getXStep = getXStep,
          visibleLabelsCount = visibleLabelsCount,
          startInsetXStep = startInsetXStep,
          markerController = markerController,
        )
    wrapper.value = cartesianChart
    cartesianChart
  }
}
