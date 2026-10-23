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

/**
 * Tells a layer whose `yTransform` reads the visible window when that window has come to rest.
 *
 * Such a transform scales its series against the values in view, so its result is only valid for
 * the window it was computed in. The y range it shares with the primary layer is the obvious
 * trigger to recompute on, and it is not enough: two windows can hold different values and still
 * round to the same axis, and between those the transform would keep a scaling belonging to a
 * window the user has left.
 *
 * A settle, rather than the window itself. The window moves every frame of a scroll, and a
 * transform that followed it would rescale continuously under the finger — the line dancing
 * instead of being read. This fires once, after the scroll stops, alongside the axis.
 */
public interface WindowSettleSignal {
  /** Incremented once per settled window. Layers use it as a cache key, never for its value. */
  public val settleEpoch: Int
}
