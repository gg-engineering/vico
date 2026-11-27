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

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

private const val DRAG_VELOCITY_THRESHOLD = 500f

private const val DECAY_DISTANCE_DIVISOR = 4.5f

private const val MIN_SNAP_DELTA_PX = 0.5f

/**
 * Configuration for snap behavior on a scrollable chart.
 *
 * @param snapToLabel callback that computes the snap X position. Receives:
 *     - `currentXLabel`: the X value at the visible window start when gesture ends
 *     - `projectedXLabel`: WHERE the scroll would naturally land after decay physics. For drag (low
 *       velocity), this equals currentXLabel. For fling, it's further ahead. Snap to the nearest
 *       window boundary from this projected position.
 *     - `isDrag`: true if slow drag release, false if fling
 *     - `isForward`: scroll direction (true = forward/right) Returns the target X data value to
 *       snap to.
 *
 * @param animation animation configuration for the snap.
 */
public data class SnapBehaviorConfig(
  val snapToLabel:
    ((
      currentXLabel: Double?,
      projectedXLabel: Double?,
      isDrag: Boolean,
      isForward: Boolean,
    ) -> Double)? =
    null,
  val animation: SnapAnimation = SnapAnimation(),
) {
  public data class SnapAnimation(
    val decayFrictionMultiplier: Float = 2f,
    val snapDurationMillis: Int = 800,
    val snapEasing: androidx.compose.animation.core.Easing = LinearOutSlowInEasing,
  )
}

/**
 * A [FlingBehavior] with 2-phase snap:
 *
 * **Phase 1 — Approach (fling only):** Exponential decay carries the scroll with momentum. The
 * decay distance is computed from velocity and window movement config.
 *
 * **Phase 2 — Snap:** After decay settles, the [SnapBehaviorConfig.snapToLabel] callback determines
 * the final X position. A tween animation gently aligns to it.
 *
 * For drags (low velocity), Phase 1 is skipped — goes straight to Phase 2.
 */
internal class ChartSnapFlingBehavior(
  private val scrollState: VicoScrollState,
  private val config: SnapBehaviorConfig,
) : FlingBehavior {

  override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
    val snapToLabel = config.snapToLabel ?: return initialVelocity

    val currentX = scrollState.scrollValueToX()
    val isDrag = abs(initialVelocity) < DRAG_VELOCITY_THRESHOLD
    val isForward = initialVelocity > 0f

    // Compute projected landing position using decay physics
    val projectedX =
      if (isDrag) {
        currentX
      } else {
        // The multiplier comes from a public data class the caller fills in. At zero the
        // projected distance is infinite and reaches the snap as NaN.
        val friction = config.animation.decayFrictionMultiplier.takeIf { it > 0f } ?: 2f
        val decayDistance = initialVelocity / (friction * DECAY_DISTANCE_DIVISOR)
        scrollState.scrollValueToX(scrollState.value + decayDistance)
      }

    val targetDataX = snapToLabel(currentX, projectedX, isDrag, isForward)
    val targetPixels =
      scrollState.xToScrollValueBehindStartInset(targetDataX) ?: return initialVelocity
    val clampedTarget = targetPixels.coerceIn(0f, scrollState.maxValue)
    val delta = clampedTarget - scrollState.value

    if (abs(delta) < MIN_SNAP_DELTA_PX) return 0f

    // Animate within ScrollScope using scrollBy — the only way to scroll
    // from inside performFling without conflicting with scrollableState lock.
    var previous = 0f
    AnimationState(initialValue = 0f).animateTo(
      targetValue = delta,
      animationSpec =
        tween(
          durationMillis = config.animation.snapDurationMillis,
          easing = config.animation.snapEasing,
        ),
    ) {
      val step = value - previous
      previous = value
      scrollBy(step)
    }

    return 0f
  }
}

/** Creates and remembers a [FlingBehavior] for chart snapping. */
@Composable
public fun rememberChartSnapFlingBehavior(
  scrollState: VicoScrollState,
  config: SnapBehaviorConfig,
): FlingBehavior =
  remember(scrollState, config) {
    ChartSnapFlingBehavior(scrollState, config)
  }
