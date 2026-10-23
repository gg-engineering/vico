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

import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.data.MutableExtraStore
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock

/** Creates [CartesianChartModel]s and handles difference animations. */
public class CartesianChartModelProducer {
  private var lastPartials = emptyList<CartesianLayerModel.Partial>()
  private var lastTransactionExtraStore = MutableExtraStore()
  private var cachedModel: CartesianChartModel? = null
  private var cachedModelPartialHashCode: Int? = null
  private val mutex = Mutex()
  private val updateReceivers = mutableMapOf<Any, UpdateReceiver>()

  private val _isReady = MutableStateFlow(false)

  /**
   * True once the first successful [runTransaction] has produced a non-null model, and true from
   * then on.
   *
   * A latch, not a current state: it never returns to false, so a later transaction that is still
   * in flight does not report the chart as unready. Consumers use it to dismiss a first-paint
   * skeleton, which is a thing that happens once. Reading it as "a model is ready right now" and
   * bringing a skeleton back on every reload is what it will not support.
   */
  public val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

  private suspend fun update(
    partials: List<CartesianLayerModel.Partial>,
    transactionExtraStore: MutableExtraStore,
    animate: Boolean = true,
  ) {
    coroutineScope {
      mutex.withLock {
        val immutablePartials = partials.toList()
        if (
          immutablePartials == this@CartesianChartModelProducer.lastPartials &&
            transactionExtraStore == this@CartesianChartModelProducer.lastTransactionExtraStore
        ) {
          return@coroutineScope
        }
        updateReceivers.values
          .map { launch { it.handleUpdate(immutablePartials, transactionExtraStore, animate = animate) } }
          .joinAll()
        lastPartials = immutablePartials
        lastTransactionExtraStore = transactionExtraStore
      }
    }
  }

  private fun getModel(partials: List<CartesianLayerModel.Partial>, extraStore: ExtraStore) =
    if (partials.isNotEmpty()) {
        CartesianChartModel(partials.map { it.complete(extraStore) }, extraStore)
      } else {
        null
      }
      .also { model ->
        cachedModel = model
        cachedModelPartialHashCode = partials.hashCode()
        if (model != null && !_isReady.value) _isReady.value = true
      }

  private suspend fun transform(
    key: Any,
    fraction: Float,
    model: CartesianChartModel?,
    ranges: CartesianChartRanges,
  ) {
    with(updateReceivers[key] ?: return) {
      withContext(getDispatcher()) {
        transform(hostExtraStore, fraction)
        currentCoroutineContext().ensureActive()
        onUpdate(model, ranges, hostExtraStore.copy())
      }
    }
  }

  internal suspend fun registerForUpdates(
    key: Any,
    restoredModel: CartesianChartModel?,
    cancelAnimation: suspend () -> Unit,
    startAnimation: (transformModel: suspend (key: Any, fraction: Float) -> Unit) -> Unit,
    prepareForTransformation:
      (CartesianChartModel?, MutableExtraStore, CartesianChartRanges) -> Unit,
    transform: suspend (MutableExtraStore, Float) -> Unit,
    hostExtraStore: MutableExtraStore,
    updateRanges: (CartesianChartModel?) -> CartesianChartRanges,
    onUpdate: (CartesianChartModel?, CartesianChartRanges, ExtraStore) -> Unit,
  ) {
    withContext(getDispatcher()) {
      val receiver =
        UpdateReceiver(
          cancelAnimation,
          startAnimation,
          onUpdate,
          hostExtraStore,
          prepareForTransformation,
          transform,
          updateRanges,
        )
      mutex.withLock {
        updateReceivers[key] = receiver
        receiver.handleUpdate(lastPartials, lastTransactionExtraStore, restoredModel, animate = false)
      }
    }
  }

  internal fun isRegistered(key: Any): Boolean = updateReceivers.containsKey(key)

  internal fun unregisterFromUpdates(key: Any) {
    updateReceivers.remove(key)
  }

  internal fun getCachedData(
    updateRanges: (CartesianChartModel?) -> CartesianChartRanges,
    hostExtraStore: ExtraStore,
  ) =
    if (mutex.tryLock()) {
      try {
        cachedModel?.let { model -> CachedData(model, updateRanges(model), hostExtraStore.copy()) }
      } catch (_: Exception) {
        null
      } finally {
        mutex.unlock()
      }
    } else {
      null
    }

  internal class CachedData(
    val model: CartesianChartModel,
    val ranges: CartesianChartRanges,
    val extraStore: ExtraStore,
  ) {
    operator fun component1(): CartesianChartModel = model

    operator fun component2(): CartesianChartRanges = ranges

    operator fun component3(): ExtraStore = extraStore
  }

  /**
   * (1) Creates a [Transaction], (2) invokes [block], and (3) runs a data update, returning once
   * the update is complete. Between steps 2 and 3, if there’s already an update in progress, the
   * current coroutine is suspended until the ongoing update’s completion.
   *
   * @param animate Whether to animate the difference between old and new data. When `false`,
   *   the new data is applied instantly without interpolation.
   */
  public suspend fun runTransaction(animate: Boolean = true, block: Transaction.() -> Unit) {
    withContext(getDispatcher()) { Transaction(animate).also(block).commit() }
  }

  /** Handles data updates. This is used via [runTransaction]. */
  public inner class Transaction internal constructor(private val animate: Boolean = true) {
    private val newPartials = mutableListOf<CartesianLayerModel.Partial>()
    private val newExtraStore = MutableExtraStore()

    /** Adds a [CartesianLayerModel.Partial]. */
    public fun add(partial: CartesianLayerModel.Partial) {
      newPartials.add(partial)
    }

    /**
     * Allows for adding auxiliary values, which can later be retrieved via
     * [CartesianChartModel.extraStore].
     */
    public fun extras(block: (MutableExtraStore) -> Unit) {
      block(newExtraStore)
    }

    internal suspend fun commit() {
      update(newPartials, newExtraStore, animate)
    }
  }

  private inner class UpdateReceiver(
    val cancelAnimation: suspend () -> Unit,
    val startAnimation: (transformModel: suspend (key: Any, fraction: Float) -> Unit) -> Unit,
    val onUpdate: (CartesianChartModel?, CartesianChartRanges, ExtraStore) -> Unit,
    val hostExtraStore: MutableExtraStore,
    val prepareForTransformation:
      (CartesianChartModel?, MutableExtraStore, CartesianChartRanges) -> Unit,
    val transform: suspend (MutableExtraStore, Float) -> Unit,
    val updateRanges: (CartesianChartModel?) -> CartesianChartRanges,
  ) {
    suspend fun handleUpdate(
      partials: List<CartesianLayerModel.Partial>,
      transactionExtraStore: ExtraStore,
      restoredModel: CartesianChartModel? = null,
      animate: Boolean = true,
    ) {
      cancelAnimation()
      val model =
        if (partials.hashCode() == cachedModelPartialHashCode) {
          cachedModel?.copy(transactionExtraStore)?.takeUnless { it == restoredModel } ?: return
        } else {
          getModel(partials, transactionExtraStore)
        }
      val ranges = updateRanges(model)
      if (animate) {
        prepareForTransformation(model, hostExtraStore, ranges)
        startAnimation { key, fraction -> transform(key, fraction, model, ranges) }
      } else {
        prepareForTransformation(model, hostExtraStore, ranges)
        transform(hostExtraStore, 1f)
        onUpdate(model, ranges, hostExtraStore.copy())
      }
    }
  }

  private suspend fun getDispatcher(): CoroutineDispatcher {
    val context = currentCoroutineContext()
    return if (context[InlineUpdateContextKey] != null) Dispatchers.Unconfined
    else Dispatchers.Default
  }
}

internal object InlineUpdateContextKey : CoroutineContext.Key<InlineUpdateContext>

/**
 * Runs data updates on the calling coroutine rather than [Dispatchers.Default].
 *
 * Previews use it because they have no frame clock to await. Tests use it because a virtual-time
 * scheduler cannot drain a real dispatcher: without it, a test that advances until idle resumes
 * while an update is still in flight on the default pool, and whether it observes the result comes
 * down to how fast the machine is.
 */
public object InlineUpdateContext : AbstractCoroutineContextElement(InlineUpdateContextKey)
