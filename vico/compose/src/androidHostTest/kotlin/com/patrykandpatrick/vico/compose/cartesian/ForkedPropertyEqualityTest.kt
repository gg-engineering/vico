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

import android.graphics.BlendMode
import android.graphics.Paint
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartRanges
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerDrawingModelInterpolator
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerYMapper
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkConstructor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Every property this fork added to a class that carries equality.
 *
 * `rememberCartesianChart` keys on its layers and the host keys on the chart, so a property left
 * out of `equals` makes a changed object compare equal to the one it replaces and the chart keeps
 * the old one — a configuration change that silently does nothing. Four such properties were on
 * [LineCartesianLayer] alone, and the only reason it did not show was that the one property that
 * *was* compared happened to change alongside them.
 *
 * A property added later and forgotten here fails this file rather than the screen.
 */
class ForkedPropertyEqualityTest {

  // The components these classes hold build a Paint, which is a stub on the host JVM. Same setup
  // as CartesianChartTest, which is the suite this one sits beside.
  @BeforeTest
  fun setUp() {
    MockKAnnotations.init(this, relaxUnitFun = true)
    mockkConstructor(Paint::class)
    justRun { anyConstructed<Paint>().setColor(any()) }
    justRun { anyConstructed<Paint>().setAlpha(any()) }
    justRun { anyConstructed<Paint>().setStrokeWidth(any()) }
    justRun { anyConstructed<Paint>().setStyle(any()) }
    justRun { anyConstructed<Paint>().setStrokeCap(any()) }
    justRun { anyConstructed<Paint>().setStrokeJoin(any()) }
    justRun { anyConstructed<Paint>().setAntiAlias(any()) }
    every { anyConstructed<Paint>().setXfermode(any()) } returns null
    justRun { anyConstructed<Paint>().setBlendMode(any<BlendMode>()) }
  }

  private val noOpMapper =
    object : CartesianLayerYMapper {
      override fun map(
        series: List<LineCartesianLayerModel.Entry>,
        targetYRange: CartesianChartRanges.YRange,
        visibleXRange: ClosedFloatingPointRange<Double>,
        extraStore: ExtraStore,
      ): DoubleArray? = null
    }

  // One instance, shared by every layer below. Built per call it differed between them on its
  // own, and three of these assertions passed with the equality fix reverted.
  private val lineProvider by lazy {
    LineCartesianLayer.LineProvider.series(
      LineCartesianLayer.Line(LineCartesianLayer.LineFill.single(Fill.Black))
    )
  }

  // Likewise: auto() hands back a new object each call, so defaulting it made every pair of
  // layers unequal before the property under test was even read.
  private val sharedRangeProvider = CartesianLayerRangeProvider.auto()
  private val sharedInterpolator = CartesianLayerDrawingModelInterpolator.line()

  private fun layer(
    yMapper: CartesianLayerYMapper? = null,
    markerTargetsEnabled: Boolean = true,
    contributesYRange: Boolean = true,
    alwaysUseLiveRange: Boolean = false,
  ) =
    LineCartesianLayer(
      lineProvider = lineProvider,
      rangeProvider = sharedRangeProvider,
      drawingModelInterpolator = sharedInterpolator,
      yMapper = yMapper,
      markerTargetsEnabled = markerTargetsEnabled,
      contributesYRange = contributesYRange,
      alwaysUseLiveRange = alwaysUseLiveRange,
    )

  /** Guards the three below: without it they pass whatever `equals` does. */
  @Test
  fun `two layers built the same way are equal`() {
    assertEquals(layer(), layer())
  }

  @Test
  fun `each forked LineCartesianLayer property takes part in equality`() {
    val base = layer()

    assertNotEquals(base, layer(yMapper = noOpMapper))
    assertNotEquals(base, layer(markerTargetsEnabled = false))
    assertNotEquals(base, layer(contributesYRange = false))
    assertNotEquals(base, layer(alwaysUseLiveRange = true))
  }

  /**
   * The two adjacent Booleans are declared in one order on the primary constructor and the other
   * on the secondary, which has already produced one defect. A swap compiles and draws at the
   * wrong scale, so it is pinned by value rather than by reading the declarations.
   */
  @Test
  fun `the two adjacent Booleans do not swap on the way through a constructor`() {
    val built = layer(contributesYRange = false, alwaysUseLiveRange = true)

    assertEquals(false, built.contributesYRange)
    assertEquals(true, built.alwaysUseLiveRange)
    assertEquals(built, built.copy())
    assertNotEquals(built, built.copy(contributesYRange = true))
    assertNotEquals(built, built.copy(alwaysUseLiveRange = false))
  }

  @Test
  fun `visibleLabelsCount takes part in CartesianChart equality`() {
    val chart = CartesianChart(layer(), visibleLabelsCount = 5.0)

    assertNotEquals(chart, chart.copy(visibleLabelsCount = 7.0))
  }

  @Test
  fun `the forked DefaultCartesianMarker properties take part in equality`() {
    val label = TextComponent()
    val resting = TextComponent()
    val marker = com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker(label)

    assertNotEquals(
      marker,
      com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker(
        label,
        restingLabel = resting,
      ),
    )
    assertNotEquals(
      marker,
      com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker(
        label,
        labelGap = 8.dp,
      ),
    )
    assertNotEquals(
      marker,
      com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker(
        label,
        guideline = LineComponent(Fill.Black),
      ),
    )
  }
}
