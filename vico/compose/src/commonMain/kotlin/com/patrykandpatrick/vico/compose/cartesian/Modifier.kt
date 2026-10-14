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

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.ScrubMarkerController
import com.patrykandpatrick.vico.compose.common.Point
import com.patrykandpatrick.vico.compose.common.detectZoomGestures
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val BASE_SCROLL_ZOOM_DELTA = 0.1f
private const val MOVEMENT_THRESHOLD = 20f
private const val TAP_SLOP = 5f

private fun Offset.toPoint() = Point(x, y)

@Composable internal expect fun Modifier.extraPointerInput(scrollState: VicoScrollState): Modifier

@Composable
internal fun Modifier.pointerInput(
  scrollState: VicoScrollState,
  onInteraction: ((Interaction) -> Unit)?,
  onZoom: ((Float, Offset) -> Unit)?,
  consumeMoveEvents: Boolean,
  longPressEnabled: Boolean,
  markerController: CartesianMarkerController? = null,
  flingBehavior: FlingBehavior? = null,
) =
  scrollable(
      state = scrollState.scrollableState,
      orientation = Orientation.Horizontal,
      flingBehavior = flingBehavior,
      // Always enabled so nestedScroll blocks parent scroll during scrubbing.
      // Actual scroll is frozen via ScrollableState when scrollEnabled=false or during scrub.
      enabled = true,
      reverseDirection = true,
    )
    .pointerInput(onZoom, onInteraction, markerController) {
      // --- SCRUB STATE MACHINE ---
      // When a ScrubMarkerController is present, intercept gestures BEFORE scrollable
      // to distinguish scroll (big horizontal movement) from scrub (hold 200ms + drag).
      val scrubController = markerController as? ScrubMarkerController

      if (scrubController != null && onInteraction != null) {
        // Scrub-aware gesture handling
        val scope = CoroutineScope(currentCoroutineContext())
        var interactionMode = InteractionMode.NONE
        var initialPressPosition: Offset? = null
        var delayJob: Job? = null

        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            val position = event.changes.first().position
            val pointerPosition = position.toPoint()

            when {
              // Zoom via scroll wheel
              event.type == PointerEventType.Scroll && scrollState.scrollEnabled && onZoom != null -> {
                onZoom(
                  1 - event.changes.first().scrollDelta.y * BASE_SCROLL_ZOOM_DELTA,
                  event.changes.first().position,
                )
              }

              // Press: enter DECIDING state, start delay timer
              event.type == PointerEventType.Press && event.changes.size == 1 -> {
                initialPressPosition = position
                interactionMode = InteractionMode.DECIDING

                // Start delay timer
                delayJob?.cancel()
                delayJob = scope.launch {
                  delay(scrubController.delayMs)
                  if (interactionMode == InteractionMode.DECIDING) {
                    // Timer fired — enter marker selection / scrub mode
                    interactionMode = InteractionMode.MARKER_SELECTION
                    scrubController.isScrubbing = true
                    scrollState.isScrollFrozen = true
                    onInteraction(Interaction.LongPress(pointerPosition))
                  }
                }

                onInteraction(Interaction.Press(pointerPosition))
              }

              // Move: decide scroll vs scrub based on movement and state
              event.type == PointerEventType.Move -> {
                val movement = initialPressPosition?.let {
                  abs((position - it).getDistance())
                } ?: 0f

                when (interactionMode) {
                  InteractionMode.DECIDING -> {
                    // Check if movement is primarily horizontal
                    val dx = initialPressPosition?.let { abs(position.x - it.x) } ?: 0f
                    val dy = initialPressPosition?.let { abs(position.y - it.y) } ?: 0f

                    if (movement > MOVEMENT_THRESHOLD) {
                      if (dx > dy) {
                        // Primarily horizontal — enter scroll mode
                        interactionMode = InteractionMode.SCROLLING
                        delayJob?.cancel()
                        delayJob = null
                        scrubController.isScrubbing = false
                        scrollState.isScrollFrozen = false
                        if (scrubController.hasActiveMarker) {
                          scrubController.onDismiss()
                          onInteraction(Interaction.Release(pointerPosition))
                        }
                      } else {
                        // Primarily vertical — let parent LazyColumn handle it
                        interactionMode = InteractionMode.NONE
                        delayJob?.cancel()
                        delayJob = null
                        scrollState.isScrollFrozen = false
                      }
                    }
                    // Don't consume small movements — let scrollable handle them.
                    // This allows scrollable to cancel ongoing fling/snap animations.
                  }

                  InteractionMode.MARKER_SELECTION -> {
                    if (movement > MOVEMENT_THRESHOLD) {
                      // Movement after timer fired — enter scrubbing
                      interactionMode = InteractionMode.MARKER_SCRUBBING
                    }
                    // Pass position for marker update
                    event.changes.forEach { it.consume() }
                    onInteraction(Interaction.Move(pointerPosition))
                  }

                  InteractionMode.MARKER_SCRUBBING -> {
                    // Consume ALL events to prevent parent LazyColumn scroll
                    event.changes.forEach { it.consume() }
                    onInteraction(Interaction.Move(pointerPosition))
                  }

                  InteractionMode.SCROLLING -> {
                    // Do nothing — let events flow to scrollable modifier naturally.
                    // Back gesture prevention is handled by scrollable's nestedScroll.
                  }

                  InteractionMode.NONE -> {
                    onInteraction(Interaction.Move(pointerPosition))
                  }
                }
              }

              // Release: finalize state
              event.type == PointerEventType.Release -> {
                delayJob?.cancel()
                delayJob = null

                // Calculate total movement distance for tap detection
                val totalMovement = initialPressPosition?.let {
                  abs((position - it).getDistance())
                } ?: 0f

                when (interactionMode) {
                  InteractionMode.DECIDING -> {
                    if (totalMovement < TAP_SLOP) {
                      // Finger barely moved — this is a tap, not a scroll attempt
                      onInteraction(Interaction.Tap(pointerPosition))
                    } else {
                      // Finger moved but < MOVEMENT_THRESHOLD — ambiguous, treat as no-op
                      onInteraction(Interaction.Release(pointerPosition))
                    }
                  }

                  InteractionMode.MARKER_SELECTION,
                  InteractionMode.MARKER_SCRUBBING -> {
                    // End scrub — consume release to prevent parent scroll
                    event.changes.forEach { it.consume() }
                    scrubController.isScrubbing = false
                    scrollState.isScrollFrozen = false
                    onInteraction(Interaction.Release(pointerPosition))
                  }

                  InteractionMode.SCROLLING -> {
                    // Don't emit to marker controller during scroll
                  }

                  InteractionMode.NONE -> {
                    onInteraction(Interaction.Release(pointerPosition))
                  }
                }

                interactionMode = InteractionMode.NONE
                initialPressPosition = null
              }

              // Multi-touch press (release)
              event.type == PointerEventType.Press -> {
                onInteraction(Interaction.Release(pointerPosition))
              }

              // Enter/Exit for hover
              event.type == PointerEventType.Enter -> {
                onInteraction(Interaction.Enter(pointerPosition))
              }

              event.type == PointerEventType.Exit -> {
                val isInsideChartBounds = position.fits(size)
                onInteraction(Interaction.Exit(pointerPosition, isInsideChartBounds))
              }
            }
          }
        }
      } else {
        // --- DEFAULT UPSTREAM BEHAVIOR (no scrub controller) ---
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            val position = event.changes.first().position
            val pointerPosition = position.toPoint()
            when {
              event.type == PointerEventType.Scroll && scrollState.scrollEnabled && onZoom != null ->
                onZoom(
                  1 - event.changes.first().scrollDelta.y * BASE_SCROLL_ZOOM_DELTA,
                  event.changes.first().position,
                )
              onInteraction == null -> continue
              event.type == PointerEventType.Press && event.changes.size == 1 ->
                onInteraction(Interaction.Press(pointerPosition))
              event.type == PointerEventType.Release || event.type == PointerEventType.Press ->
                onInteraction(Interaction.Release(pointerPosition))
              event.type == PointerEventType.Move -> {
                if (consumeMoveEvents && !scrollState.scrollEnabled) event.changes.first().consume()
                onInteraction(Interaction.Move(pointerPosition))
              }
              event.type == PointerEventType.Enter ->
                onInteraction(Interaction.Enter(pointerPosition))
              event.type == PointerEventType.Exit -> {
                val isInsideChartBounds = position.fits(size)
                onInteraction(Interaction.Exit(pointerPosition, isInsideChartBounds))
              }
            }
          }
        }
      }
    }
    .then(
      // Only add detectTapGestures when NOT using ScrubMarkerController
      // (ScrubMarkerController handles tap/long-press in the state machine above), and only for a
      // controller that wants the gesture — detectTapGestures consumes the tap, so a chart that
      // never marks anything would otherwise swallow it on its way to whatever surrounds it.
      if (
        onInteraction != null &&
          markerController !is ScrubMarkerController &&
          markerController?.acceptsGestures != false
      ) {
        Modifier.pointerInput(onInteraction, longPressEnabled) {
          detectTapGestures(
            onLongPress =
              if (longPressEnabled) {
                { onInteraction(Interaction.LongPress(it.toPoint())) }
              } else {
                null
              },
            onTap = { onInteraction(Interaction.Tap(it.toPoint())) },
          )
        }
      } else {
        Modifier
      }
    )
    .then(
      if (scrollState.scrollEnabled && onZoom != null) {
        Modifier.pointerInput(onInteraction, onZoom) {
          detectZoomGestures { centroid, zoom ->
            onInteraction?.invoke(Interaction.Zoom(centroid.toPoint()))
            onZoom(zoom, centroid)
          }
        }
      } else {
        Modifier
      }
    )
    .extraPointerInput(scrollState)

/** Interaction mode for the scrub state machine. */
private enum class InteractionMode {
  NONE,
  DECIDING,
  MARKER_SELECTION,
  MARKER_SCRUBBING,
  SCROLLING,
}

private fun Offset.fits(size: IntSize) = x >= 0f && x <= size.width && y >= 0f && y <= size.height
