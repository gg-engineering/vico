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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.ScrubMarkerController
import com.patrykandpatrick.vico.compose.common.Point
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How much more vertical than horizontal a drag must be before the page takes it.
 *
 * The finger is on a chart that scrolls horizontally, so the chart is the specific target and the
 * page the general one. Tested as `dx > dy`, any drag a degree off vertical left the chart — which
 * is every real scrub, because a finger tracking a line is never exactly horizontal.
 */
private const val VERTICAL_BIAS = 2f

/** Under this much travel a press and release is a tap. */
private const val TAP_SLOP = 5f

private enum class Mode {
  NONE,

  /**
   * The page took the gesture.
   *
   * Distinct from [NONE], which is the resting state and still reports movement: a marker already
   * on screen was repositioned to wherever the finger ended after the page had scrolled under it,
   * so it jumped on lift. Once the chart has given a drag away it says nothing more about it.
   */
  ABANDONED,
  DECIDING,
  MARKER_SELECTION,
  MARKER_SCRUBBING,
  SCROLLING,
}

private fun Offset.toPoint() = Point(x, y)

/**
 * Press-and-hold to mark a point, then drag along the chart to move the mark.
 *
 * This runs ahead of the scrollable modifier so one press can still become either gesture. A press
 * starts a timer; travelling past the platform's touch slop before it fires commits to a scroll, or
 * hands the gesture to the parent if the movement is decisively vertical. Once the timer fires the
 * scroll position is frozen and every event is claimed on the initial pass, which is what stops an
 * ancestor stealing the drag.
 */
internal suspend fun PointerInputScope.detectScrubGestures(
  scrollState: VicoScrollState,
  controller: ScrubMarkerController,
  onInteraction: (Interaction) -> Unit,
  onZoom: ((Float, Offset) -> Unit)?,
) {
  val scope = CoroutineScope(coroutineContext)
  var mode = Mode.NONE
  var pressedAt: Offset? = null
  var holdTimer: Job? = null

  awaitPointerEventScope {
    while (true) {
      // Claimed before anyone else sees it, not after. Consuming on the main pass only stops an
      // ancestor that waits for that pass; the page's vertical scroll and the segment pager both
      // sit above this one, and a drag a few degrees off horizontal reached them while the marker
      // was being dragged. A scrub owns the whole gesture or it is not a scrub.
      val scrubbing = mode == Mode.MARKER_SELECTION || mode == Mode.MARKER_SCRUBBING
      val initial = awaitPointerEvent(PointerEventPass.Initial)
      if (scrubbing) initial.changes.forEach { it.consume() }

      val event = awaitPointerEvent()
      val position = event.changes.first().position
      val point = position.toPoint()
      val travel = pressedAt?.let { abs((position - it).getDistance()) } ?: 0f

      when {
        event.type == PointerEventType.Scroll && scrollState.scrollEnabled && onZoom != null ->
          onZoom(1 - event.changes.first().scrollDelta.y * BASE_SCROLL_ZOOM_DELTA, position)

        event.type == PointerEventType.Press && event.changes.size == 1 -> {
          pressedAt = position
          mode = Mode.DECIDING
          holdTimer?.cancel()
          holdTimer = scope.launch {
            delay(controller.delayMs)
            if (mode == Mode.DECIDING) {
              mode = Mode.MARKER_SELECTION
              controller.isScrubbing = true
              scrollState.isScrollFrozen = true
              onInteraction(Interaction.LongPress(point))
            }
          }
          onInteraction(Interaction.Press(point))
        }

        event.type == PointerEventType.Move ->
          when (mode) {
            Mode.DECIDING ->
              // The platform's own slop, not a constant of ours: the parent commits at that
              // distance, and a fixed pixel count wins the race on one density and loses it on
              // the next.
              if (travel > viewConfiguration.touchSlop) {
                val dx = pressedAt?.let { abs(position.x - it.x) } ?: 0f
                val dy = pressedAt?.let { abs(position.y - it.y) } ?: 0f
                holdTimer?.cancel()
                holdTimer = null
                scrollState.isScrollFrozen = false
                if (dy > dx * VERTICAL_BIAS) {
                  // Decisively vertical: the page owns this one.
                  mode = Mode.ABANDONED
                } else {
                  mode = Mode.SCROLLING
                  controller.isScrubbing = false
                  if (controller.hasActiveMarker) {
                    controller.onDismiss()
                    onInteraction(Interaction.Release(point))
                  }
                }
              }
            // Small movements are left for the scrollable modifier, so it can cancel a fling.

            Mode.MARKER_SELECTION,
            Mode.MARKER_SCRUBBING -> {
              if (travel > viewConfiguration.touchSlop) mode = Mode.MARKER_SCRUBBING
              event.changes.forEach { it.consume() }
              onInteraction(Interaction.Move(point))
            }

            // Events flow to the scrollable modifier untouched.
            Mode.SCROLLING -> Unit

            Mode.ABANDONED -> Unit

            Mode.NONE -> onInteraction(Interaction.Move(point))
          }

        event.type == PointerEventType.Release -> {
          holdTimer?.cancel()
          holdTimer = null
          when (mode) {
            Mode.DECIDING ->
              if (travel < TAP_SLOP) {
                onInteraction(Interaction.Tap(point))
              } else {
                onInteraction(Interaction.Release(point))
              }

            Mode.MARKER_SELECTION,
            Mode.MARKER_SCRUBBING -> {
              event.changes.forEach { it.consume() }
              controller.isScrubbing = false
              scrollState.isScrollFrozen = false
              onInteraction(Interaction.Release(point))
            }

            Mode.SCROLLING,
            Mode.ABANDONED -> Unit
            Mode.NONE -> onInteraction(Interaction.Release(point))
          }
          mode = Mode.NONE
          pressedAt = null
        }
      }
    }
  }
}
