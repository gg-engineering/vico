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
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.axis.Axis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.*
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.MutableCartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController.Lock
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.ScrubMarkerController
import com.patrykandpatrick.vico.compose.common.*
import com.patrykandpatrick.vico.compose.common.Defaults.CHART_HEIGHT
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

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
 * @param placeholder shown when no [CartesianChartModel] is available.
 */
@Composable
public fun CartesianChartHost(
  chart: CartesianChart,
  modelProducer: CartesianChartModelProducer,
  modifier: Modifier = Modifier,
  scrollState: VicoScrollState = rememberVicoScrollState(),
  zoomState: VicoZoomState = rememberDefaultVicoZoomState(scrollState.scrollEnabled),
  flingBehavior: FlingBehavior? = null,
  animationSpec: AnimationSpec<Float>? = defaultCartesianDiffAnimationSpec,
  animateIn: Boolean = true,
  initialMarkerX: Double? = null,
  placeholder: @Composable BoxScope.() -> Unit = {},
) {
  val mutableRanges = remember { MutableCartesianChartRanges() }
  val modelWrapper by modelProducer.collectAsState(chart, animationSpec, animateIn, mutableRanges)
  // The producer's ranges are deliberately ignored — see below.
  val (model, previousModel, _, extraStore) = modelWrapper

  // Ranges are a function of two things — the model, and the chart's range providers — but the
  // producer only ever computes them when a model is pushed. A window that arrives after that
  // push (the common case when it is derived from the data that triggered the push) would then
  // never reach the chart: it would keep serving the domain captured at push time, drawing its
  // data far off-canvas, until some unrelated push happened to recompute them.
  //
  // Recomputing here makes the chart follow its providers. `chart` is the key because a provider
  // change arrives as a new chart instance, and `copy` keeps the id, so nothing else notices.
  val hostRanges = remember { MutableCartesianChartRanges() }
  val initialRanges =
    remember(chart, model) {
      if (model == null) {
        CartesianChartRanges.Empty
      } else {
        // The opening range is derived here, before the first frame, because this is where the
        // ranges that frame draws with are decided. Deriving it a frame later left updateRanges
        // below with no opinion to ask for, so the chart opened on the whole dataset's extent and
        // then replaced it — and deriving it in the caller instead meant two selections of
        // entries that could disagree. One caller, one selection: the provider's own.
        // Paired by position. indexOf matched on equals, so two layers configured alike both
        // resolved to models[0] and the second drew against the first one's range.
        chart.layers.forEachIndexed { index, layer ->
          val provider = (layer as? LineCartesianLayer)?.internalRangeProvider
            as? ScrollAwareRangeProvider ?: return@forEachIndexed
          val series = (model.models.getOrNull(index)
            as? LineCartesianLayerModel)?.series ?: return@forEachIndexed
          // Once per model, not once per chart identity: rebuilding the cache resets the ticks,
          // and re-seeding would discard a range the user had scrolled to. The two are asked
          // separately because a model copied for new extras keeps its series instance — that
          // push moves the window without touching the data, and used to re-sort all of it.
          val seriesChanged = provider.cachedSeries !== series
          if (seriesChanged) {
            provider.cachedSeries = series
            provider.buildCache(series)
          }
          // Seeded when the window moved, or when there is no settled axis to keep — never
          // merely because the series changed. The seed describes the window the chart OPENS on,
          // so re-seeding on any push dragged the axis back there from wherever the user had
          // scrolled: the labels jumped to the opening window's and then back to the real ones a
          // frame later, when the first draw reported the window actually in view. Two tick sets
          // per push, which is what the blink on changing a second metric was — that push moves
          // the series and leaves the window exactly where it was.
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

  // Scroll-aware range: wrap initialRanges with animated Y values.
  // Two separate vars instead of Pair to avoid boxing/allocation per animation frame.
  var animatedMinY by remember { mutableStateOf(Double.NaN) }
  var animatedMaxY by remember { mutableStateOf(Double.NaN) }
  var targetMinY by remember { mutableStateOf(Double.NaN) }
  var targetMaxY by remember { mutableStateOf(Double.NaN) }
  val isInitialRangesReady = initialRanges !== CartesianChartRanges.Empty

  val firstScrollAwareProvider = remember(chart) {
    chart.layers.mapNotNull {
      (it as? LineCartesianLayer)?.internalRangeProvider as? ScrollAwareRangeProvider
    }.firstOrNull()
  }
  val hasScrollAwareProvider = firstScrollAwareProvider != null

  // Seed fallback: when animated range not yet established (first frame with model data),
  // use provider.seedMinY/seedMaxY set synchronously from SegmentState each recomposition.
  // Eliminates the 1-2 frame flash of full-model Y range on initial ViewModel creation.
  val effectiveMinY = if (!animatedMinY.isNaN()) animatedMinY
    else firstScrollAwareProvider?.seedMinY?.takeIf { !it.isNaN() } ?: Double.NaN
  val effectiveMaxY = if (!animatedMaxY.isNaN()) animatedMaxY
    else firstScrollAwareProvider?.seedMaxY?.takeIf { !it.isNaN() } ?: Double.NaN
  val hasValidAnimatedRange = !effectiveMinY.isNaN() && !effectiveMaxY.isNaN()

  val ranges = if (hasValidAnimatedRange && isInitialRangesReady) {
    AnimatedYCartesianChartRanges(
      initialRanges, effectiveMinY, effectiveMaxY,
      if (!targetMinY.isNaN()) targetMinY else effectiveMinY,
      if (!targetMaxY.isNaN()) targetMaxY else effectiveMaxY,
    )
  } else {
    initialRanges
  }

  if (model != null) {
    ScrollAwareRangeEffect(
      chart, model, flingBehavior,
      onAnimatedRange = { minY, maxY ->
        animatedMinY = minY
        animatedMaxY = maxY
      },
      onTargetRange = { minY, maxY ->
        targetMinY = minY
        targetMaxY = maxY
      },
    )
  }

  // No alpha hiding — v3 approach. Chart renders immediately so initialScroll
  // applies with correct context.ranges. Brief flash of default range is acceptable.
  CartesianChartHostBox(modifier) {
    if (model != null) {
      CartesianChartHostImpl(
        chart,
        model,
        scrollState,
        zoomState,
        ranges,
        previousModel,
        extraStore,
        flingBehavior,
        initialMarkerX,
      )
    } else {
      placeholder()
    }
  }
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
 */
@Composable
public fun CartesianChartHost(
  chart: CartesianChart,
  model: CartesianChartModel,
  modifier: Modifier = Modifier,
  scrollState: VicoScrollState = rememberVicoScrollState(),
  zoomState: VicoZoomState = rememberDefaultVicoZoomState(scrollState.scrollEnabled),
  initialMarkerX: Double? = null,
) {
  val ranges = remember { MutableCartesianChartRanges() }
  remember(chart, model) {
    ranges.reset()
    chart.updateRanges(ranges, model)
  }
  CartesianChartHostBox(modifier) {
    CartesianChartHostImpl(chart, model, scrollState, zoomState, ranges.toImmutable(), initialMarkerX = initialMarkerX)
  }
}

@Composable
internal fun CartesianChartHostImpl(
  chart: CartesianChart,
  model: CartesianChartModel,
  scrollState: VicoScrollState,
  zoomState: VicoZoomState,
  ranges: CartesianChartRanges,
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

  // The caller owns whether there is a marker at all. Keyed on initialMarkerX, not Unit: running
  // once per host meant a caller that cleared its marker — because the marked entry was deleted,
  // or fell outside the window — never reached the chart, which went on drawing the dot and its
  // guideline at an x the caller no longer held. Clearing also deactivates the controller now;
  // nulling markerX alone left it believing a marker was still up.
  //
  // Only the clear is forced. A marker is still restored into an empty saveable rather than
  // assigned over whatever is there, because during a drag the local value is ahead of the one
  // the caller has echoed back, and assigning would drag the marker backwards under the finger.
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
    LaunchedEffect(Unit) {
      snapshotFlow { scrubController.hasActiveMarker }
        .collect { active ->
          if (!active && markerX != null) {
            markerX = null
            markerSeriesIndex = null
            lastAcceptedInteraction = null
          }
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

  // Cached ScrollAwareRangeProvider references + dedup state for Canvas emit
  val scrollAwareProviders = remember(chart) {
    chart.layers.mapNotNull { layer ->
      (layer as? LineCartesianLayer)?.internalRangeProvider as? ScrollAwareRangeProvider
    }
  }
  // Every window the draw pass sees, for the marker's resting content. Kept apart from the
  // ScrollAwareRangeProvider feed below, which only exists when a layer has such a provider —
  // a marker's resting label is drawn whatever the layers happen to be configured with.
  val visibleXRanges = remember { MutableSharedFlow<ClosedFloatingPointRange<Double>>(replay = 1, extraBufferCapacity = 8) }

  // Keyed on the model too, not the scroll alone. The window is one of the things the resting
  // text is formatted from and the caller's own state is another, and new data can change the
  // second without touching the first — a card whose text names the week its entries fall in
  // would go on naming the old one. Restarting replays the last window, so it re-formats.
  LaunchedEffect(chart.marker, visibleXRanges, model) {
    val marker = chart.marker ?: return@LaunchedEffect
    // The first window as soon as there is one, so the resting text is on the opening frame
    // rather than a debounce late; thereafter only once the scroll has come to rest. The same
    // first-then-debounced pair ScrollAwareRangeEffect uses.
    marker.onScrollSettled(visibleXRanges.first())
    visibleXRanges.debounce(MARKER_SETTLE_MS).collect(marker::onScrollSettled)
  }

  var lastEmittedScroll by remember { ValueWrapper(Float.NaN) }
  var lastEmittedXSpacing by remember { ValueWrapper(Float.NaN) }
  var lastEmittedChartWidth by remember { ValueWrapper(Float.NaN) }

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
          val narrowedTargets: List<CartesianMarker.Target>
          val seriesIndex: Int?
          if (targets.isNotEmpty()) {
            val closestIndex =
              targets.indices.minBy { abs(targets[it].canvasX - interaction.point.x) }
            if (targets.distinctBy { it.canvasX }.size > 1) {
              narrowedTargets = listOf(targets[closestIndex])
              seriesIndex = closestIndex
            } else {
              narrowedTargets = targets
              seriesIndex = null
            }
          } else {
            narrowedTargets = targets
            seriesIndex = null
          }
          if (chart.markerController.shouldAcceptInteraction(interaction, narrowedTargets)) {
            val shouldShow = chart.markerController.shouldShowMarker(interaction, narrowedTargets)
            lastAcceptedInteraction = interaction

            // If ScrubMarkerController has a callback, let the consumer decide markerX
            val scrubCallback = (chart.markerController as? ScrubMarkerController)?.onMarkerIndexChanged
            if (scrubCallback != null) {
              if (shouldShow) {
                // Pass tap X and ALL marker target X values to consumer.
                // Even when narrowedTargets is empty (no data points in window),
                // consumer can still handle it (e.g., snap to nearest label).
                val allTargetXValues = chart.allMarkerTargetXValues
                val userMarkerX = scrubCallback(x, allTargetXValues, interaction)
                if (userMarkerX != null) {
                  markerX = userMarkerX
                  markerSeriesIndex = seriesIndex
                } else {
                  markerX = null
                  markerSeriesIndex = null
                }
              } else {
                // Dismiss — notify consumer
                scrubCallback(null, emptyList(), interaction)
                markerX = null
                markerSeriesIndex = null
              }
            } else {
              // Fallback: vico's internal nearest-target logic
              if (shouldShow && narrowedTargets.isNotEmpty()) {
                markerX = narrowedTargets.first().x
                markerSeriesIndex = seriesIndex
              } else {
                markerX = null
                markerSeriesIndex = null
              }
            }
          }
        }
      } else {
        null
      }
    }

  fun onViewportChange() {
    // ScrubMarkerController dismisses marker on scroll — no position tracking needed.
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
                  coroutineScope.launch { zoomState.zoom(factor, centroid.x) { scrollState.value } }
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
    measuringContext.value.canvasSize = size

    // Two-pass prepare on the very first draw so VerticalAxis.updateHorizontalLayerMargins
    // sees the post-initialScroll scroll value. Without this, Size.Scroll axes compute
    // effectiveWidth using scrollValue=0 on Frame 1 (initialScroll not yet applied),
    // producing a cutoff that resolves on Frame 2 once the scroll is stable.
    if (!scrollState.initialScrollHandled) {
      layerDimensions.clear()
      (chart.startAxis as? VerticalAxis<*>)?.updateScrollState(scrollState.value, scrollState.maxValue)
      (chart.endAxis as? VerticalAxis<*>)?.updateScrollState(scrollState.value, scrollState.maxValue)
      chart.prepare(measuringContext.value, layerDimensions)
      if (!chart.layerBounds.isEmpty) {
        scrollState.update(measuringContext.value, chart.layerBounds, layerDimensions)
      }
    }

    layerDimensions.clear()
    (chart.startAxis as? VerticalAxis<*>)?.updateScrollState(scrollState.value, scrollState.maxValue)
    (chart.endAxis as? VerticalAxis<*>)?.updateScrollState(scrollState.value, scrollState.maxValue)
    chart.prepare(measuringContext.value, layerDimensions)

    if (chart.layerBounds.isEmpty) return@Canvas

    zoomState.update(measuringContext.value, layerDimensions, chart.layerBounds, scrollState.value)
    scrollState.update(measuringContext.value, chart.layerBounds, layerDimensions)

    // The window, for everything that settles off it: the layers' Y range and the marker's
    // resting text. Computed once, behind the same guard, and only when the viewport has
    // actually moved — the draw pass runs for reasons that leave it untouched.
    // The collector side skips during snap animation.
    val wantsWindow = scrollAwareProviders.isNotEmpty() || chart.marker != null
    if (wantsWindow && layerDimensions.xSpacing > 0f) {
      val sp = scrollState.value
      val xs = layerDimensions.xSpacing
      val cw = chart.layerBounds.width
      if (sp != lastEmittedScroll || xs != lastEmittedXSpacing || cw != lastEmittedChartWidth) {
        lastEmittedScroll = sp
        lastEmittedXSpacing = xs
        lastEmittedChartWidth = cw
        // Computed from real chart ranges (not estimated)
        val visibleRange = measuringContext.value.getVisibleXRange(
          layerDimensions, chart.layerBounds, scrollState.value,
        )
        if (chart.marker != null) visibleXRanges.tryEmit(visibleRange)
        if (scrollAwareProviders.isNotEmpty()) {
          val scrollInfo = ScrollAwareRangeProvider.ScrollInfo(
            sp, xs, cw, visibleRange.start, visibleRange.endInclusive,
          )
          scrollAwareProviders.forEach { it.scrollUpdates.tryEmit(scrollInfo) }
        }
      }
    }

    if (model != lastHandledModel) {
      coroutineScope.launch { scrollState.autoScroll(model, previousModel) }
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
    chart.draw(drawingContext)
    measuringContext.value.cacheStore.purge()
  }
}

@Composable
private fun CartesianChartHostBox(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
  Box(modifier = modifier.fillMaxWidth(), content = content)
}

/**
 * Manages [ScrollAwareRangeProvider] lifecycle following iOS-like animation pattern:
 *
 * **iOS approach**: Labels cross-fade (opacity), chart content animates positionally, ~250ms.
 * **Our approximation**: Labels swap instantly, chart content animates via range interpolation.
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

@OptIn(FlowPreview::class)
@Composable
private fun ScrollAwareRangeEffect(
  chart: CartesianChart,
  model: CartesianChartModel,
  flingBehavior: FlingBehavior? = null,
  onAnimatedRange: (minY: Double, maxY: Double) -> Unit,
  onTargetRange: (minY: Double, maxY: Double) -> Unit = { _, _ -> },
) {
  // Deduplicate by provider identity — same instance shared by multiple layers
  // only processes once (uses the first layer for buildCache)
  val providers = remember(chart) {
    chart.layers.mapNotNull { layer ->
      if (layer is LineCartesianLayer) {
        (layer.internalRangeProvider as? ScrollAwareRangeProvider)?.let { it to layer }
      } else {
        null
      }
    }.distinctBy { it.first }
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

      // Wait for first Canvas draw to provide visible-window scroll info.
      isFirstScrollUpdate = true
      val firstScrollInfo = provider.scrollUpdates.first()
      val visibleEntries = provider.computeVisibleEntries(firstScrollInfo)
      val xRange = firstScrollInfo.visibleXStart..firstScrollInfo.visibleXEnd
      val result = visibleEntries?.let { provider.computeDisplayRange(it, xRange) }
      if (result != null) {
        val (range, ticks) = result
        provider.currentMinY = range.start
        provider.currentMaxY = range.endInclusive
        provider.currentTicks = ticks
        onTargetRange(range.start, range.endInclusive)
        if (animMinY.value.isNaN()) {
          // First ever render — snap (no animation from NaN)
          animMinY.snapTo(range.start.toFloat())
          animMaxY.snapTo(range.endInclusive.toFloat())
          onAnimatedRange(range.start, range.endInclusive)
        } else {
          // Model changed (e.g., metric switch) — animate from old to new
          launch { animMinY.animateTo(range.start.toFloat(), tween(provider.animDurationMs)) }
          launch { animMaxY.animateTo(range.endInclusive.toFloat(), tween(provider.animDurationMs)) }
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
          val result = provider.computeDisplayRange(visibleEntries, xRange) ?: return@collectLatest
          val (range, newTicks) = result
          val targetMinY = range.start.toFloat()
          val targetMaxY = range.endInclusive.toFloat()

          // Announced before the no-movement check below, not after it. A layer that transforms
          // its series against the window has to recompute on every settle, including the ones
          // that leave the y range exactly where it was — scrolling between two windows that
          // happen to share an axis is precisely when it would otherwise keep a scaling that
          // belonged to the window the user has left.
          provider.settleEpoch++

          if (abs(targetMinY - animMinY.value) < 0.01f &&
            abs(targetMaxY - animMaxY.value) < 0.01f) return@collectLatest

          // Swapped as the animation starts, not when it ends: a tick is a data value, drawn at
          // whatever pixel the currently animating range puts it at, so it slides into place
          // rather than appearing there. Held back to the end it would pop instead.
          provider.currentTicks = newTicks
          onTargetRange(targetMinY.toDouble(), targetMaxY.toDouble())

          // Animate range — chart content scales smoothly
          val minJob = launch {
            animMinY.animateTo(targetMinY, tween(provider.animDurationMs))
          }
          val maxJob = launch {
            animMaxY.animateTo(targetMaxY, tween(provider.animDurationMs))
          }
          minJob.join()
          maxJob.join()
        }
    }


    // Push animated values to Compose State on each animation frame
    LaunchedEffect(Unit) {
      snapshotFlow { animMinY.value to animMaxY.value }.collect { (minY, maxY) ->
        // Skip NaN frames (before initialization)
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
 * A lightweight [CartesianChartRanges] wrapper that overrides Y range with animated values
 * but delegates everything else to the original ranges. No reset/rebuild needed per frame.
 */
private class AnimatedYCartesianChartRanges(
  private val delegate: CartesianChartRanges,
  animMinY: Double,
  animMaxY: Double,
  targetMinY: Double = animMinY,
  targetMaxY: Double = animMaxY,
) : CartesianChartRanges {
  override val minX: Double get() = delegate.minX
  override val maxX: Double get() = delegate.maxX
  override val xStep: Double get() = delegate.xStep

  // Single YRange instance — reused for all getYRange() calls in this frame.
  private val yRange = object : CartesianChartRanges.YRange {
    override val minY: Double = animMinY
    override val maxY: Double = animMaxY
    override val length: Double = (animMaxY - animMinY).coerceAtLeast(1e-6)
  }

  private val targetRange = object : CartesianChartRanges.YRange {
    override val minY: Double = targetMinY
    override val maxY: Double = targetMaxY
    override val length: Double = (targetMaxY - targetMinY).coerceAtLeast(1e-6)
  }

  override fun getYRange(axisPosition: Axis.Position.Vertical?): CartesianChartRanges.YRange = yRange
  override fun getTargetYRange(axisPosition: Axis.Position.Vertical?): CartesianChartRanges.YRange = targetRange
}
