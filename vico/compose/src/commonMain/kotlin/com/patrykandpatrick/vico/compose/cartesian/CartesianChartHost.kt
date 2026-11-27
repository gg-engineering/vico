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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartRanges
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.MutableCartesianChartRanges
import com.patrykandpatrick.vico.compose.cartesian.data.ScrollAwareRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.collectAsState
import com.patrykandpatrick.vico.compose.cartesian.data.component1
import com.patrykandpatrick.vico.compose.cartesian.data.component2
import com.patrykandpatrick.vico.compose.cartesian.data.component3
import com.patrykandpatrick.vico.compose.cartesian.data.component4
import com.patrykandpatrick.vico.compose.cartesian.data.defaultCartesianDiffAnimationSpec
import com.patrykandpatrick.vico.compose.cartesian.data.toImmutable
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.MutableCartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController.Lock
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.ScrubMarkerController
import com.patrykandpatrick.vico.compose.common.ChartHostBox
import com.patrykandpatrick.vico.compose.common.Defaults
import com.patrykandpatrick.vico.compose.common.MutableDrawScope
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.ValueWrapper
import com.patrykandpatrick.vico.compose.common.component1
import com.patrykandpatrick.vico.compose.common.component2
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.getValue
import com.patrykandpatrick.vico.compose.common.setValue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

private const val MIN_RANGE_LENGTH = 1e-6

/**
 * The maximum horizontal distance, in pixels, within which two [CartesianMarker.Target]s are
 * considered to share a position. [CartesianMarker.Target]s for the same _x_ can have marginally
 * different [CartesianMarker.Target.canvasX] values across layers because of floating-point error,
 * so an exact comparison would spuriously treat them as separate positions. This is well below the
 * spacing between genuinely distinct positions (e.g., grouped columns).
 */
private const val MARKER_POSITION_TOLERANCE = 0.5f

/**
 * Narrows [targets] to the one closest to [pointX] when they span more than one horizontal
 * position, returning it together with its index. When all [targets] share a position (within
 * [MARKER_POSITION_TOLERANCE]), they’re returned unchanged with a `null` index. This keeps the
 * tapped column selected in grouped-column charts while preserving every target in, e.g., a column–
 * line combo chart, where the column and line share an _x_.
 */
internal fun narrowMarkerTargets(
  targets: List<CartesianMarker.Target>,
  pointX: Float,
): Pair<List<CartesianMarker.Target>, Int?> {
  if (targets.isEmpty()) return targets to null
  if (targets.maxOf { it.canvasX } - targets.minOf { it.canvasX } <= MARKER_POSITION_TOLERANCE) {
    return targets to null
  }
  val closestIndex = targets.indices.minBy { abs(targets[it].canvasX - pointX) }
  return listOf(targets[closestIndex]) to closestIndex
}

/**
 * Displays a [CartesianChart].
 *
 * @param chart the [CartesianChart].
 * @param modelProducer creates and updates the [CartesianChartModel].
 * @param modifier the modifier to be applied to the chart.
 * @param scrollState houses information on the [CartesianChart]’s scroll value. Allows for scroll
 *   customization and programmatic scrolling.
 * @param zoomState houses information on the [CartesianChart]’s zoom factor. Allows for zoom
 *   customization.
 * @param animationSpec the [AnimationSpec] for difference animations.
 * @param initialAnimationSpec the [AnimationSpec] for the initial (reveal) animation. Defaults to
 *   [animationSpec]. Set to `null` to skip the initial animation.
 * @param chartAreaHeight the default height of the coordinate system, to which the heights of the
 *   legend, the marker, and other components are added. Used only when the height isn’t otherwise
 *   constrained (e.g., via [Modifier.height]).
 * @param placeholder shown when no [CartesianChartModel] is available.
 */
@Composable
public fun CartesianChartHost(
  chart: CartesianChart,
  modelProducer: CartesianChartModelProducer,
  modifier: Modifier = Modifier,
  scrollState: VicoScrollState = rememberVicoScrollState(),
  zoomState: VicoZoomState = rememberDefaultVicoZoomState(scrollState.scrollEnabled),
  animationSpec: AnimationSpec<Float>? = defaultCartesianDiffAnimationSpec,
  initialAnimationSpec: AnimationSpec<Float>? = animationSpec,
  chartAreaHeight: Dp = Defaults.CARTESIAN_CHART_AREA_HEIGHT.dp,
  placeholder: @Composable BoxScope.() -> Unit = {},
  flingBehavior: FlingBehavior? = null,
  initialMarkerX: Double? = null,
) {
  val mutableRanges = remember { MutableCartesianChartRanges() }
  val modelWrapper by
    modelProducer.collectAsState(chart, animationSpec, initialAnimationSpec, mutableRanges)
  // The producer's ranges are deliberately discarded — see below.
  val (model, previousModel) = modelWrapper
  val extraStore = modelWrapper.extraStore

  // The producer computes ranges only on a push, so a window arriving after one would never reach
  // the chart. Keyed on `chart` because a provider change arrives as a new instance.
  val hostRanges = remember { MutableCartesianChartRanges() }
  val initialRanges =
    remember(chart, model) {
      if (model == null) {
        CartesianChartRanges.Empty
      } else {
        // Seeded before updateRanges, or the chart opens on the whole dataset and re-letters a
        // frame later. Paired by position: indexOf matched on equals, so alike layers collided.
        chart.layers.forEachIndexed { index, layer ->
          val provider =
            (layer as? LineCartesianLayer)?.internalRangeProvider as? ScrollAwareRangeProvider
              ?: return@forEachIndexed
          val series =
            (model.models.getOrNull(index) as? LineCartesianLayerModel)?.series
              ?: return@forEachIndexed
          // Per model, not per chart: rebuilding resets the ticks and discards a scrolled-to range.
          if (provider.cachedSeries !== series) {
            provider.cachedSeries = series
            provider.buildCache(series)
          }
          // Only when the window moved: the seed is the opening window, so re-seeding on any push
          // dragged the axis back from wherever the user had scrolled.
          val window = provider.openingWindow(model.extraStore)
          if (provider.cachedWindow != window || provider.currentTicks.isEmpty()) {
            provider.cachedWindow = window
            provider.seedFromWindow(model.extraStore)
          }
        }
        hostRanges.reset()
        chart.updateRanges(hostRanges, model)
        hostRanges.toImmutable()
      }
    }

  // Two scalars rather than a Pair, so an animation frame allocates nothing.
  var animatedMinY by remember { mutableStateOf(Double.NaN) }
  var animatedMaxY by remember { mutableStateOf(Double.NaN) }
  var targetMinY by remember { mutableStateOf(Double.NaN) }
  var targetMaxY by remember { mutableStateOf(Double.NaN) }

  val firstScrollAwareProvider =
    remember(chart) {
      chart.layers
        .mapNotNull {
          (it as? LineCartesianLayer)?.internalRangeProvider as? ScrollAwareRangeProvider
        }
        .firstOrNull()
    }

  // Before the animated range exists — the first frame carrying data — fall back to the seed the
  // provider set synchronously, rather than flashing the whole model's Y range.
  val effectiveMinY =
    if (!animatedMinY.isNaN()) animatedMinY
    else firstScrollAwareProvider?.seedMinY?.takeIf { !it.isNaN() } ?: Double.NaN
  val effectiveMaxY =
    if (!animatedMaxY.isNaN()) animatedMaxY
    else firstScrollAwareProvider?.seedMaxY?.takeIf { !it.isNaN() } ?: Double.NaN

  // Remembered, and the wrapper carries equality. Rebuilt inline it was a new identity every
  // frame of the y animation, and it is a key of the measuring context and of the interaction
  // callback — which is in turn a key of the gesture modifier, so a press and hold could not
  // survive its own hold delay while anything on screen was animating.
  val ranges =
    remember(initialRanges, effectiveMinY, effectiveMaxY, targetMinY, targetMaxY) {
      if (
        !effectiveMinY.isNaN() &&
          !effectiveMaxY.isNaN() &&
          initialRanges !== CartesianChartRanges.Empty
      ) {
        AnimatedYCartesianChartRanges(
          initialRanges,
          effectiveMinY,
          effectiveMaxY,
          if (!targetMinY.isNaN()) targetMinY else effectiveMinY,
          if (!targetMaxY.isNaN()) targetMaxY else effectiveMaxY,
        )
      } else {
        initialRanges
      }
    }

  if (model != null) {
    ScrollAwareRangeEffect(
      chart,
      model,
      onAnimatedRange = { minY, maxY ->
        animatedMinY = minY
        animatedMaxY = maxY
      },
      onTargetRange = { minY, maxY ->
        targetMinY = minY
        targetMaxY = maxY
      },
    )
    CartesianChartHostImpl(
      chart,
      model,
      modifier,
      scrollState,
      zoomState,
      ranges,
      chartAreaHeight,
      previousModel,
      extraStore,
      flingBehavior,
      initialMarkerX,
    )
  } else {
    ChartHostBox(modifier, chartAreaHeight, measureExtras = null) { placeholder() }
  }
}

/**
 * Displays a [CartesianChart].
 *
 * @param chart the [CartesianChart].
 * @param modelProducer creates and updates the [CartesianChartModel].
 * @param modifier the modifier to be applied to the chart.
 * @param scrollState houses information on the [CartesianChart]’s scroll value. Allows for scroll
 *   customization and programmatic scrolling.
 * @param zoomState houses information on the [CartesianChart]’s zoom factor. Allows for zoom
 *   customization.
 * @param animationSpec the [AnimationSpec] for difference animations.
 * @param animateIn whether to run an initial animation when the [CartesianChartHost] enters
 *   composition. The animation is skipped for previews.
 * @param chartAreaHeight the default height of the coordinate system, to which the heights of the
 *   legend, the marker, and other components are added. Used only when the height isn’t otherwise
 *   constrained (e.g., via [Modifier.height]).
 * @param placeholder shown when no [CartesianChartModel] is available.
 */
@Deprecated("Set `initialAnimationSpec` to `null` to skip the initial animation.")
@Composable
public fun CartesianChartHost(
  chart: CartesianChart,
  modelProducer: CartesianChartModelProducer,
  modifier: Modifier = Modifier,
  scrollState: VicoScrollState = rememberVicoScrollState(),
  zoomState: VicoZoomState = rememberDefaultVicoZoomState(scrollState.scrollEnabled),
  animationSpec: AnimationSpec<Float>? = defaultCartesianDiffAnimationSpec,
  animateIn: Boolean,
  chartAreaHeight: Dp = Defaults.CARTESIAN_CHART_AREA_HEIGHT.dp,
  placeholder: @Composable BoxScope.() -> Unit = {},
) {
  CartesianChartHost(
    chart = chart,
    modelProducer = modelProducer,
    modifier = modifier,
    scrollState = scrollState,
    zoomState = zoomState,
    animationSpec = animationSpec,
    initialAnimationSpec = if (animateIn) animationSpec else null,
    chartAreaHeight = chartAreaHeight,
    placeholder = placeholder,
  )
}

/**
 * Displays a [CartesianChart]. This function accepts a [CartesianChartModel]. For dynamic data, use
 * the function overload that accepts a [CartesianChartModelProducer] instance.
 *
 * @param chart the [CartesianChart].
 * @param model the [CartesianChartModel].
 * @param modifier the modifier to be applied to the chart.
 * @param scrollState houses information on the [CartesianChart]’s scroll value. Allows for scroll
 *   customization and programmatic scrolling.
 * @param zoomState houses information on the [CartesianChart]’s zoom factor. Allows for zoom
 *   customization.
 * @param chartAreaHeight the default height of the coordinate system, to which the heights of the
 *   legend, the marker, and other components are added. Used only when the height isn’t otherwise
 *   constrained (e.g., via [Modifier.height]).
 */
@Composable
public fun CartesianChartHost(
  chart: CartesianChart,
  model: CartesianChartModel,
  modifier: Modifier = Modifier,
  scrollState: VicoScrollState = rememberVicoScrollState(),
  zoomState: VicoZoomState = rememberDefaultVicoZoomState(scrollState.scrollEnabled),
  chartAreaHeight: Dp = Defaults.CARTESIAN_CHART_AREA_HEIGHT.dp,
) {
  val mutableRanges = remember { MutableCartesianChartRanges() }
  val ranges =
    remember(chart, model) {
      mutableRanges.reset()
      chart.updateRanges(mutableRanges, model)
      mutableRanges.toImmutable()
    }
  CartesianChartHostImpl(
    chart,
    model,
    modifier,
    scrollState,
    zoomState,
    ranges,
    chartAreaHeight,
  )
}

@Composable
internal fun CartesianChartHostImpl(
  chart: CartesianChart,
  model: CartesianChartModel,
  modifier: Modifier,
  scrollState: VicoScrollState,
  zoomState: VicoZoomState,
  ranges: CartesianChartRanges,
  chartAreaHeight: Dp,
  previousModel: CartesianChartModel? = null,
  extraStore: ExtraStore = ExtraStore.Empty,
  flingBehavior: FlingBehavior? = null,
  initialMarkerX: Double? = null,
) {
  var markerX by rememberSaveable { mutableStateOf<Double?>(null) }
  var markerSeriesIndex by rememberSaveable { mutableStateOf<Int?>(null) }
  var lastAcceptedInteraction by
    rememberSaveable(saver = Interaction.Saver) { mutableStateOf(null) }

  val scrubController = chart.markerController as? ScrubMarkerController

  // Keyed on the value, not Unit, so a caller clearing its marker reaches the chart. Only the
  // clear is forced: during a drag the local value leads the echoed one.
  if (scrubController != null) {
    LaunchedEffect(initialMarkerX) {
      if (initialMarkerX != null) {
        if (markerX == null) markerX = initialMarkerX
        scrubController.hasActiveMarker = true
      } else {
        markerX = null
        markerSeriesIndex = null
        lastAcceptedInteraction = null
        scrubController.hasActiveMarker = false
      }
    }
  }

  val measuringContext =
    rememberCartesianMeasuringContext(
      extraStore = extraStore,
      model = model,
      ranges = ranges,
      scrollEnabled = scrollState.scrollEnabled,
      zoomEnabled = scrollState.scrollEnabled && zoomState.zoomEnabled,
      layerPadding =
        remember(chart.layerPadding, model.extraStore) { chart.layerPadding(model.extraStore) },
      markerX = markerX,
      markerSeriesIndex = markerSeriesIndex,
    )

  val coroutineScope = rememberCoroutineScope()
  var lastHandledModel by remember { ValueWrapper(model) }
  val layerDimensions = remember { MutableCartesianLayerDimensions() }
  val scrollAwareProviders =
    remember(chart) {
      chart.layers.mapNotNull { layer ->
        (layer as? LineCartesianLayer)?.internalRangeProvider as? ScrollAwareRangeProvider
      }
    }
  // Kept apart from the providers' feed: a resting label is drawn whatever the layers are.
  val visibleXRanges = remember {
    MutableSharedFlow<ClosedFloatingPointRange<Double>>(replay = 1, extraBufferCapacity = 8)
  }

  // Keyed on the model too: the text is formatted from the window and the caller's state, and new
  // data can change the second alone.
  LaunchedEffect(chart.marker, visibleXRanges, model) {
    val marker = chart.marker ?: return@LaunchedEffect
    // The first window as soon as there is one, so the resting text is on the opening frame rather
    // than a debounce late; thereafter only once the scroll has come to rest.
    marker.onScrollSettled(visibleXRanges.first())
    visibleXRanges.debounce(MARKER_SETTLE_MS).collect(marker::onScrollSettled)
  }

  var lastEmittedScroll by remember { ValueWrapper(Float.NaN) }
  var lastEmittedXSpacing by remember { ValueWrapper(Float.NaN) }
  var lastEmittedChartWidth by remember { ValueWrapper(Float.NaN) }
  val measureLayerDimensions = remember { MutableCartesianLayerDimensions() }
  val measureExtras =
    remember(chart, measuringContext, measureLayerDimensions, chartAreaHeight) {
      { widthPx: Int ->
        val context = measuringContext.value
        val width = widthPx.toFloat()
        context.canvasWidth = width
        context.canvasSize = Size(width, with(context) { chartAreaHeight.pixels })
        measureLayerDimensions.clear()
        chart.getVerticalExtras(context, measureLayerDimensions)
      }
    }

  val onInteraction =
    remember(chart, layerDimensions, scrollState, ranges) {
      if (chart.marker != null) {
        { interaction: Interaction ->
          val x =
            measuringContext.value.pointerPositionToX(
              interaction.point,
              layerDimensions,
              chart.layerBounds,
              scrollState.value,
              ranges,
            )
          val targets =
            chart.getMarkerTargets(
              x,
              measuringContext.value.getVisibleXRange(
                layerDimensions,
                chart.layerBounds,
                scrollState.value,
              ),
            )
          val (narrowedTargets, seriesIndex) = narrowMarkerTargets(targets, interaction.point.x)
          if (chart.markerController.shouldAcceptInteraction(interaction, narrowedTargets)) {
            val shouldShow = chart.markerController.shouldShowMarker(interaction, narrowedTargets)
            lastAcceptedInteraction = interaction
            // Handed every markable x, not just the nearest, so it can snap to a label in a window
            // holding no reading.
            val scrubCallback = scrubController?.onMarkerIndexChanged
            if (scrubCallback != null) {
              if (shouldShow) {
                val chosen = scrubCallback(x, chart.markerTargetXValues, interaction)
                markerX = chosen
                markerSeriesIndex = if (chosen != null) seriesIndex else null
              } else {
                scrubCallback(null, emptyList(), interaction)
                markerX = null
                markerSeriesIndex = null
              }
            } else if (shouldShow && narrowedTargets.isNotEmpty()) {
              markerX = narrowedTargets.first().x
              markerSeriesIndex = seriesIndex
            } else {
              markerX = null
              markerSeriesIndex = null
            }
            true
          } else {
            false
          }
        }
      } else {
        null
      }
    }

  fun onViewportChange() {
    // A scrub controller drops its marker on scroll, so there is no position to re-track; replaying
    // the last interaction per scroll frame only spammed the consumer with dismissals.
    if (chart.markerController is ScrubMarkerController) return
    lastAcceptedInteraction
      ?.takeIf { chart.markerController.lock == Lock.Position }
      ?.let { onInteraction?.invoke(it) }
  }

  LaunchedEffect(model) { onViewportChange() }

  LaunchedEffect(scrollState.consumedXDeltas, scrollState.unconsumedXDeltas) {
    merge(scrollState.consumedXDeltas, scrollState.unconsumedXDeltas).collect { onViewportChange() }
  }

  LaunchedEffect(zoomState, scrollState) {
    zoomState.pendingScroll.collect { (scroll, maxValue) ->
      scrollState.scroll(scroll, maxValue)
      onViewportChange()
    }
  }

  DisposableEffect(scrollState) { onDispose { scrollState.clearUpdated() } }

  ChartHostBox(modifier, chartAreaHeight, measureExtras) {
    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(
            scrollState = scrollState,
            consumeMoveEvents = chart.markerController.consumeMoveEvents,
            onInteraction = onInteraction,
            onZoom =
              remember(zoomState, scrollState, coroutineScope) {
                if (zoomState.zoomEnabled) {
                  { factor, centroid ->
                    coroutineScope.launch {
                      zoomState.zoom(factor, centroid.x) { scrollState.value }
                    }
                  }
                } else {
                  null
                }
              },
            longPressEnabled = chart.markerController.acceptsLongPress,
            markerController = chart.markerController,
            flingBehavior = flingBehavior,
          )
    ) {
      if (size.isEmpty()) return@Canvas
      measuringContext.value.canvasWidth = size.width
      measuringContext.value.canvasSize = size

      layerDimensions.clear()
      chart.prepare(measuringContext.value, layerDimensions, size)

      if (chart.layerBounds.isEmpty) return@Canvas

      zoomState.update(
        measuringContext.value,
        layerDimensions,
        chart.layerBounds,
        scrollState.value,
      )
      scrollState.update(measuringContext.value, chart.layerBounds, layerDimensions)

      if (model != lastHandledModel) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
          scrollState.autoScroll(model, previousModel)
        }
        lastHandledModel = model
      }

      val drawingContext =
        CartesianDrawingContext(
          measuringContext.value,
          drawContext.canvas,
          layerDimensions,
          chart.layerBounds,
          scrollState.value,
          zoomState.value,
          MutableDrawScope(this),
        )

      scrollState.drawingContext = drawingContext

      // The window the layers settle their Y range off. Computed once, and only when the viewport
      // actually moved — the draw pass runs for reasons that leave it untouched.
      if (
        (scrollAwareProviders.isNotEmpty() || chart.marker != null) && layerDimensions.xSpacing > 0f
      ) {
        val scrollPixels = scrollState.value
        val xSpacing = layerDimensions.xSpacing
        val chartWidth = chart.layerBounds.width
        if (
          scrollPixels != lastEmittedScroll ||
            xSpacing != lastEmittedXSpacing ||
            chartWidth != lastEmittedChartWidth
        ) {
          lastEmittedScroll = scrollPixels
          lastEmittedXSpacing = xSpacing
          lastEmittedChartWidth = chartWidth
          val visibleRange =
            measuringContext.value.getVisibleXRange(
              layerDimensions,
              chart.layerBounds,
              scrollPixels,
            )
          if (chart.marker != null) visibleXRanges.tryEmit(visibleRange)
          val scrollInfo =
            ScrollAwareRangeProvider.ScrollInfo(
              model,
              scrollPixels,
              xSpacing,
              chartWidth,
              visibleRange.start,
              visibleRange.endInclusive,
            )
          if (scrollAwareProviders.isNotEmpty()) {
            scrollAwareProviders.forEach { it.scrollUpdates.tryEmit(scrollInfo) }
          }
        }
      }

      chart.draw(drawingContext)
      measuringContext.value.cacheStore.purge()
    }
  }
}

/**
 * Manages [ScrollAwareRangeProvider] lifecycle following iOS-like animation pattern:
 *
 * **iOS approach**: Labels cross-fade (opacity), chart content animates positionally, ~250ms. **Our
 * approximation**: Labels swap instantly, chart content animates via range interpolation.
 *
 * Fixes applied:
 * - Animatable initialized AFTER cache build (prevents flash from 0→actual on first load)
 * - Layer index resolved via chart.layers.indexOf (correct multi-layer mapping)
 * - NaN sentinel for uninitialized state (prevents premature animation)
 * - key(provider) ensures correct Compose state tracking per provider identity
 */
/**
 * How long the scroll must be still before the marker's resting content is told the window.
 *
 * The same figure [ScrollAwareRangeProvider] settles its Y range on, so the text beside the chart
 * and the axis it describes come to rest together.
 */
private const val MARKER_SETTLE_MS = 100L

/** Below this the axis has not moved, so animating to it is noise rather than motion. */
private const val RANGE_EPSILON = 0.01f

@OptIn(FlowPreview::class)
@Composable
private fun ScrollAwareRangeEffect(
  chart: CartesianChart,
  model: CartesianChartModel,
  onAnimatedRange: (minY: Double, maxY: Double) -> Unit,
  onTargetRange: (minY: Double, maxY: Double) -> Unit = { _, _ -> },
) {
  // Deduplicate by provider identity — same instance shared by multiple layers
  // only processes once (uses the first layer for buildCache)
  val providers =
    remember(chart) {
      chart.layers
        .mapNotNull { layer ->
          if (layer is LineCartesianLayer) {
            (layer.internalRangeProvider as? ScrollAwareRangeProvider)?.let { it to layer }
          } else {
            null
          }
        }
        .distinctBy { it.first }
    }
  if (providers.isEmpty()) return

  providers.forEach { (provider, layer) ->
    // key(provider) ensures Compose tracks state per provider identity,
    // even if the providers list order changes between recompositions.
    key(provider) {
      val layerIndex = remember(chart, layer) { chart.layers.indexOf(layer) }

      val animMinY = remember { Animatable(Float.NaN) }
      val animMaxY = remember { Animatable(Float.NaN) }
      var isFirstScrollUpdate by remember { mutableStateOf(true) }

      // The cache and the opening range are built during composition, above — this measures the
      // window the chart actually opened on and corrects the range if it differs.
      // Animatables are NOT reset — they animate from old range to new range.
      LaunchedEffect(model) {
        model.models.getOrNull(layerIndex) as? LineCartesianLayerModel ?: return@LaunchedEffect

        // The first measurement taken for THIS model. The flow replays, so a bare first()
        // resumed immediately with the geometry of the model being replaced and the new range
        // was computed from a window that no longer described the data.
        isFirstScrollUpdate = true
        val firstScrollInfo = provider.scrollUpdates.first { it.model === model }
        val visibleEntries = provider.computeVisibleEntries(firstScrollInfo)
        val xRange = firstScrollInfo.visibleXStart..firstScrollInfo.visibleXEnd
        val result = visibleEntries?.let { provider.computeDisplayRange(it, xRange) }
        if (result != null) {
          val (range, ticks) = result
          val targetMinY = range.start.toFloat()
          val targetMaxY = range.endInclusive.toFloat()
          provider.currentTicks = ticks
          onTargetRange(range.start, range.endInclusive)
          if (animMinY.value.isNaN()) {
            // First ever render — snap (no animation from NaN)
            provider.currentMinY = range.start
            provider.currentMaxY = range.endInclusive
            animMinY.snapTo(targetMinY)
            animMaxY.snapTo(targetMaxY)
            onAnimatedRange(range.start, range.endInclusive)
          } else if (
            abs(targetMinY - animMinY.value) >= RANGE_EPSILON ||
              abs(targetMaxY - animMaxY.value) >= RANGE_EPSILON
          ) {
            // Only when the push actually moves the axis. Re-targeting on every push restarted the
            // tween from the previous one's velocity, so a burst of pushes — which choosing a
            // secondary metric produces — shook the top gridline and its label a few pixels.
            // The animated values are not written to the provider here: the frame collector owns
            // them, and setting the target first put the axis there for one frame before the
            // animation dragged it back to where it had started.
            launch { animMinY.animateTo(targetMinY, tween(provider.animDurationMs)) }
            launch { animMaxY.animateTo(targetMaxY, tween(provider.animDurationMs)) }
          }
        }
        isFirstScrollUpdate = false
      }

      // Subsequent scroll events: debounced, so the axis moves once, after the scroll settles.
      //
      // Deliberately not sampled during the scroll. Recomputing as the window moves makes the axis
      // and every line keyed to it shift continuously under the finger, which reads as the chart
      // dancing rather than as it being responsive. One settled correction, animated, is the
      // behaviour this is meant to have.
      LaunchedEffect(provider) {
        provider.scrollUpdates
          .debounce(provider.debounceMs)
          // collectLatest, not collect: the block waits out a 300 ms animation, and a second
          // settle can land inside that. Cancelling leaves the animatables wherever they reached
          // and the next animation continues from there, rather than queueing behind the old one.
          .collectLatest { scrollInfo ->
            if (!provider.isCacheReady || isFirstScrollUpdate) return@collectLatest
            if (animMinY.value.isNaN()) return@collectLatest

            val visibleEntries = provider.computeVisibleEntries(scrollInfo) ?: return@collectLatest
            val xRange = scrollInfo.visibleXStart..scrollInfo.visibleXEnd
            val result =
              provider.computeDisplayRange(visibleEntries, xRange) ?: return@collectLatest
            val (range, newTicks) = result
            val targetMinY = range.start.toFloat()
            val targetMaxY = range.endInclusive.toFloat()

            if (
              abs(targetMinY - animMinY.value) < RANGE_EPSILON &&
                abs(targetMaxY - animMaxY.value) < RANGE_EPSILON
            )
              return@collectLatest

            // Swapped as the animation starts, not when it ends: a tick is a data value, drawn at
            // whatever pixel the currently animating range puts it at, so it slides into place
            // rather than appearing there. Held back to the end it would pop instead.
            provider.currentTicks = newTicks
            onTargetRange(targetMinY.toDouble(), targetMaxY.toDouble())

            // Inside a scope of its own. collectLatest's action is a plain suspend function with
            // no scope receiver, so these resolved against the enclosing effect: cancelling the
            // block abandoned the joins below while the two animations carried on as siblings.
            coroutineScope {
              launch { animMinY.animateTo(targetMinY, tween(provider.animDurationMs)) }
              launch { animMaxY.animateTo(targetMaxY, tween(provider.animDurationMs)) }
            }
          }
      }

      // Push animated values to Compose State on each animation frame
      LaunchedEffect(Unit) {
        // One scalar, read beside it. A Pair here allocated on every animation frame — which the
        // comment beside the two Floats above says this deliberately avoids.
        snapshotFlow { animMinY.value }
          .collect { minY ->
            val maxY = animMaxY.value
            if (minY.isNaN() || maxY.isNaN()) return@collect
            provider.currentMinY = minY.toDouble()
            provider.currentMaxY = maxY.toDouble()
            onAnimatedRange(minY.toDouble(), maxY.toDouble())
          }
      }
    } // end key(provider)
  }
}

/**
 * A lightweight [CartesianChartRanges] wrapper that overrides Y range with animated values but
 * delegates everything else to the original ranges. No reset/rebuild needed per frame.
 */
private class AnimatedYCartesianChartRanges(
  private val delegate: CartesianChartRanges,
  private val animMinY: Double,
  private val animMaxY: Double,
  private val targetMinY: Double = animMinY,
  private val targetMaxY: Double = animMaxY,
) : CartesianChartRanges {
  override val minX: Double
    get() = delegate.minX

  override val maxX: Double
    get() = delegate.maxX

  override val xStep: Double
    get() = delegate.xStep

  // Single YRange instance — reused for all getYRange() calls in this frame.
  private val yRange =
    object : CartesianChartRanges.YRange {
      override val minY: Double = animMinY
      override val maxY: Double = animMaxY
      override val length: Double = (animMaxY - animMinY).coerceAtLeast(MIN_RANGE_LENGTH)
    }

  private val targetRange =
    object : CartesianChartRanges.YRange {
      override val minY: Double = targetMinY
      override val maxY: Double = targetMaxY
      override val length: Double = (targetMaxY - targetMinY).coerceAtLeast(MIN_RANGE_LENGTH)
    }

  override fun getYRange(axisPosition: Axis.Position.Vertical?): CartesianChartRanges.YRange =
    yRange

  override fun getTargetYRange(axisPosition: Axis.Position.Vertical?): CartesianChartRanges.YRange =
    targetRange

  // Carried, not inherited from Any: this is a key of the measuring context and of the
  // interaction callback, and a frame of the y animation produces the same four values far more
  // often than it produces new ones.
  override fun equals(other: Any?): Boolean =
    this === other ||
      other is AnimatedYCartesianChartRanges &&
        delegate == other.delegate &&
        animMinY == other.animMinY &&
        animMaxY == other.animMaxY &&
        targetMinY == other.targetMinY &&
        targetMaxY == other.targetMaxY

  override fun hashCode(): Int {
    var result = delegate.hashCode()
    result = 31 * result + animMinY.hashCode()
    result = 31 * result + animMaxY.hashCode()
    result = 31 * result + targetMinY.hashCode()
    result = 31 * result + targetMaxY.hashCode()
    return result
  }
}
