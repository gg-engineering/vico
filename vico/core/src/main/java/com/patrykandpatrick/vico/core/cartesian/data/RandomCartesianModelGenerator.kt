/*
 * Copyright 2024 by Patryk Goworowski and Patrick Michalik.
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

package com.patrykandpatrick.vico.core.cartesian.data

import androidx.annotation.RestrictTo
import com.patrykandpatrick.vico.core.common.length
import com.patrykandpatrick.vico.core.common.random
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** @suppress */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object RandomCartesianModelGenerator {
  private const val DEFAULT_MAX_X = 96
  private const val DEFAULT_MIN_Y = 2.0
  private const val DEFAULT_MAX_Y = 20.0
  private const val DEFAULT_MIN_OPENING_CLOSING = 5.0
  private const val DEFAULT_MAX_OPENING_CLOSING = 15.0
  private const val DEFAULT_MIN_LOW_HIGH = 0.5
  private const val DEFAULT_MAX_LOW_HIGH = 5.0
  private const val MAX_OPENING_PRICE_DELTA_RATIO = 0.2
  private const val CHANGE_OVERRIDE_THRESHOLD_RATIO = 0.2
  private const val MAX_CLOSING_PRICE_DELTA_RATIO = 0.8

  public val defaultX: IntProgression = 0..DEFAULT_MAX_X
  public val defaultY: ClosedFloatingPointRange<Double> = DEFAULT_MIN_Y..DEFAULT_MAX_Y
  public val defaultOpeningClosingRange: ClosedFloatingPointRange<Double> =
    DEFAULT_MIN_OPENING_CLOSING..DEFAULT_MAX_OPENING_CLOSING
  public val defaultLowHighRange: ClosedFloatingPointRange<Double> =
    DEFAULT_MIN_LOW_HIGH..DEFAULT_MAX_LOW_HIGH

  public fun getRandomColumnLayerModelPartial(
    seriesCount: Int = 1,
    x: IntProgression = defaultX,
    y: ClosedFloatingPointRange<Double> = defaultY,
  ): ColumnCartesianLayerModel.Partial =
    ColumnCartesianLayerModel.partial {
      repeat(seriesCount) { series(x.toList(), x.map { y.random() }) }
    }

  public fun getRandomLineLayerModelPartial(
    seriesCount: Int = 1,
    x: IntProgression = defaultX,
    y: ClosedFloatingPointRange<Double> = defaultY,
  ): LineCartesianLayerModel.Partial =
    LineCartesianLayerModel.partial {
      repeat(seriesCount) { series(x.toList(), x.map { y.random() }) }
    }

  public fun getRandomCandlestickLayerModelPartial(
    x: IntProgression = defaultX,
    openingClosingRange: ClosedFloatingPointRange<Double> = defaultOpeningClosingRange,
    lowHighRange: ClosedFloatingPointRange<Double> = defaultLowHighRange,
  ): CandlestickCartesianLayerModel.Partial {
    var previousClosingPrice: Double? = null
    val opening = mutableListOf<Double>()
    val closing = mutableListOf<Double>()
    val low = mutableListOf<Double>()
    val high = mutableListOf<Double>()
    val maxOpeningPriceDelta = MAX_OPENING_PRICE_DELTA_RATIO * openingClosingRange.length
    val changeOverrideThreshold = CHANGE_OVERRIDE_THRESHOLD_RATIO * openingClosingRange.length
    val maxClosingPriceDelta = MAX_CLOSING_PRICE_DELTA_RATIO * openingClosingRange.length
    repeat(x.count()) {
      val openingPrice =
        if (previousClosingPrice != null) {
          previousClosingPrice +
            Random.nextFloat() *
              if (Random.nextBoolean()) {
                (openingClosingRange.endInclusive - previousClosingPrice).coerceAtMost(
                  maxOpeningPriceDelta
                )
              } else {
                (openingClosingRange.start - previousClosingPrice).coerceAtLeast(
                  -maxOpeningPriceDelta
                )
              }
        } else {
          openingClosingRange.random()
        }
      val isBullish =
        when {
          openingPrice - openingClosingRange.start < changeOverrideThreshold -> true
          openingClosingRange.endInclusive - openingPrice < changeOverrideThreshold -> false
          else -> Random.nextBoolean()
        }
      val closingPrice =
        openingPrice +
          Random.nextFloat() *
            if (isBullish) {
              (openingClosingRange.endInclusive - openingPrice).coerceAtMost(maxClosingPriceDelta)
            } else {
              (openingClosingRange.start - openingPrice).coerceAtLeast(-maxClosingPriceDelta)
            }
      opening += openingPrice
      closing += closingPrice
      low += min(openingPrice, closingPrice) - lowHighRange.random()
      high += max(openingPrice, closingPrice) + lowHighRange.random()
      previousClosingPrice = closingPrice
    }
    return CandlestickCartesianLayerModel.partial(x.toList(), opening, closing, low, high)
  }

  public fun getRandomModel(
    columnSeriesCount: Int = 1,
    lineSeriesCount: Int = 1,
    x: IntProgression = defaultX,
    y: ClosedFloatingPointRange<Double> = defaultY,
  ): CartesianChartModel =
    CartesianChartModel(
      buildList {
        add(getRandomColumnLayerModelPartial(columnSeriesCount, x, y).complete())
        add(getRandomLineLayerModelPartial(lineSeriesCount, x, y).complete())
        add(getRandomCandlestickLayerModelPartial(x).complete())
      }
    )
}
