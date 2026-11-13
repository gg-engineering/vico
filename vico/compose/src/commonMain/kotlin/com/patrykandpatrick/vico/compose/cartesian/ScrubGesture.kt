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

/** Past this much travel a press has committed to being a scroll or a scrub. */
private const val MOVEMENT_THRESHOLD = 20f

/** Under this much travel a press and release is a tap. */
private const val TAP_SLOP = 5f

private enum class Mode {
  NONE,
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
 * starts a timer; travelling further than [MOVEMENT_THRESHOLD] before it fires commits to a scroll
 * if the movement is mostly horizontal, or hands the gesture to the parent if it is mostly
 * vertical. Once the timer fires the scroll position is frozen and every event is consumed, which
 * is what stops a parent list stealing the drag.
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
              if (travel > MOVEMENT_THRESHOLD) {
                val dx = pressedAt?.let { abs(position.x - it.x) } ?: 0f
                val dy = pressedAt?.let { abs(position.y - it.y) } ?: 0f
                holdTimer?.cancel()
                holdTimer = null
                scrollState.isScrollFrozen = false
                if (dx > dy) {
                  mode = Mode.SCROLLING
                  controller.isScrubbing = false
                  if (controller.hasActiveMarker) {
                    controller.onDismiss()
                    onInteraction(Interaction.Release(point))
                  }
                } else {
                  // Mostly vertical: the parent list owns this one.
                  mode = Mode.NONE
                }
              }
            // Small movements are left for the scrollable modifier, so it can cancel a fling.

            Mode.MARKER_SELECTION,
            Mode.MARKER_SCRUBBING -> {
              if (travel > MOVEMENT_THRESHOLD) mode = Mode.MARKER_SCRUBBING
              event.changes.forEach { it.consume() }
              onInteraction(Interaction.Move(point))
            }

            // Events flow to the scrollable modifier untouched.
            Mode.SCROLLING -> Unit

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

            Mode.SCROLLING -> Unit
            Mode.NONE -> onInteraction(Interaction.Release(point))
          }
          mode = Mode.NONE
          pressedAt = null
        }
      }
    }
  }
}
