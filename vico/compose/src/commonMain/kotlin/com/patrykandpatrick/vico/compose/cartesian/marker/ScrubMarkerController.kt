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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * A [CartesianMarkerController] for iOS Health-like marker scrubbing.
 *
 * Gesture behavior (handled by Modifier.kt's DECIDING state machine):
 * - **Tap** (no movement + release): Toggle marker at tapped data point
 * - **Hold [delayMs]ms + drag**: Scrub marker along data points, all scroll locked
 * - **Horizontal swipe**: Chart scrolls normally (no marker)
 * - **Release after scrub**: Marker stays at last position
 * - **Scroll when marker visible**: Marker dismisses (handled by CartesianChartHostImpl)
 *
 * State ownership:
 * - [isScrubbing]: read by Modifier.kt to disable scrollable modifier
 * - markerX (in CartesianChartHostImpl): single source of truth for marker visibility
 * - NO separate markerVisible flag — markerX != null IS the visibility check
 *
 * @param delayMs hold duration before entering scrub mode (default 200ms)
 * @param onMarkerIndexChanged optional callback fired when marker shows/moves/dismisses. Receives
 *   (clickX, allTargetXValues, interaction). Return the X value for marker, or null to skip. If not
 *   provided, vico's internal nearest-target logic is used. Called with (null, emptyList(), null)
 *   on dismiss.
 *
 *   [interaction] is the gesture that caused this call, so a consumer can tell a tap from the moves
 *   of a scrub and the release that ends it — all three arrive here otherwise indistinguishable. It
 *   is null when nothing the user did caused the call, i.e. a programmatic dismiss such as the one
 *   a scroll triggers.
 */
public class ScrubMarkerController(
  public val delayMs: Long = 200L,
  internal val onMarkerIndexChanged:
    ((clickX: Double?, targets: List<Double>, interaction: Interaction?) -> Double?)? =
    null,
) : CartesianMarkerController {

  /** Whether the user is currently scrubbing. Read by Modifier.kt to disable scroll. */
  public var isScrubbing: Boolean by mutableStateOf(false)
    internal set

  /**
   * Tracks if marker was showing (for shouldShowMarker logic). Set externally by
   * CartesianChartHostImpl.
   */
  internal var hasActiveMarker: Boolean by mutableStateOf(false)

  override val acceptsLongPress: Boolean = false

  override val consumeMoveEvents: Boolean = true

  override val lock: CartesianMarkerController.Lock = CartesianMarkerController.Lock.Position

  override fun shouldAcceptInteraction(
    interaction: Interaction,
    targets: List<CartesianMarker.Target>,
  ): Boolean =
    when (interaction) {
      // Accept taps/long-press even without nearby targets — consumer callback
      // handles empty windows (e.g., snap to nearest label via interpolation)
      is Interaction.Tap -> true
      is Interaction.LongPress -> true
      is Interaction.Move -> isScrubbing
      is Interaction.Press -> false
      is Interaction.Release -> true
      else -> false
    }

  override fun shouldShowMarker(
    interaction: Interaction,
    targets: List<CartesianMarker.Target>,
  ): Boolean =
    when (interaction) {
      is Interaction.Tap -> {
        // Always show/move marker on tap (v3 parity). Dismiss only on scroll.
        hasActiveMarker = true
        true
      }
      is Interaction.LongPress -> {
        hasActiveMarker = true
        isScrubbing = true
        true
      }
      is Interaction.Move -> {
        if (isScrubbing && targets.isNotEmpty()) {
          true
        } else {
          hasActiveMarker
        }
      }
      is Interaction.Release -> {
        isScrubbing = false
        hasActiveMarker // keep marker if it was visible
      }
      else -> hasActiveMarker
    }

  /** Resets internal state on dismiss. Idempotent — safe to call multiple times. */
  internal fun onDismiss() {
    if (!hasActiveMarker && !isScrubbing) return // already dismissed
    isScrubbing = false
    hasActiveMarker = false
    onMarkerIndexChanged?.invoke(null, emptyList(), null)
  }

  override fun hashCode(): Int = 31 * delayMs.hashCode()

  override fun equals(other: Any?): Boolean =
    other === this || (other is ScrubMarkerController && delayMs == other.delayMs)
}

/**
 * Creates and remembers a [ScrubMarkerController].
 *
 * @param delayMs hold duration before entering scrub mode (default 200ms)
 * @param onMarkerIndexChanged optional callback when marker shows/moves/dismisses, told which
 *   gesture caused the call. See [ScrubMarkerController].
 */
@Composable
public fun rememberScrubMarkerController(
  delayMs: Long = 200L,
  onMarkerIndexChanged:
    ((clickX: Double?, targets: List<Double>, interaction: Interaction?) -> Double?)? =
    null,
): ScrubMarkerController {
  // Read through, so a callback closing over changing state does not rebuild the controller.
  // Keyed on whether one was given, not on the lambda: wrapping absence in a non-null lambda
  // made the host take the consumer branch always, and the documented fallback to the nearest
  // target was unreachable through this factory.
  val callbackRef = rememberUpdatedState(onMarkerIndexChanged)
  val hasCallback = onMarkerIndexChanged != null
  return remember(delayMs, hasCallback) {
    ScrubMarkerController(
      delayMs,
      if (!hasCallback) null
      else
        { clickX, targets, interaction -> callbackRef.value?.invoke(clickX, targets, interaction) },
    )
  }
}
