package com.margelo.nitro.ch.jls.reactnative.mapboxnavigation

import java.util.concurrent.atomic.AtomicInteger

// ── NavigationInstanceCounter ─────────────────────────
// Tracks how many navigation views have completed setup so the process-global
// `MapboxNavigationApp` singleton is only `disable()`d when the LAST one
// detaches — never out from under a still-mounted (or remounting) sibling.
// Extracted from HybridReactNativeMapboxNavigation so the "disable only on the
// last instance" contract is unit-testable without the SDK or an emulator.
//
// Each navigation view holds one [Membership]. `acquire()` is idempotent (a view
// counts itself at most once, no matter how many times setup runs), and
// `release()` reports whether the SDK should now be disabled. This is the
// Android analogue of iOS's refcounted `ProviderCache` (idle-on-last-release).
//
// Production shares a single counter across all views; tests instantiate their
// own for isolation.
class NavigationInstanceCounter {
  private val active = AtomicInteger(0)

  /** Current number of live (counted) instances. */
  fun count(): Int = active.get()

  /** Create a membership token for one navigation view. */
  fun newMembership(): Membership = Membership()

  /** One navigation view's live membership in the shared counter. */
  inner class Membership {
    private var counted = false

    /** Count this view as live, exactly once. Safe to call repeatedly. */
    fun acquire() {
      if (!counted) {
        counted = true
        active.incrementAndGet()
      }
    }

    /**
     * Release this view's membership.
     *
     * @return `true` when no counted instances remain — i.e. the caller should
     *   now `disable()` the SDK singleton. A release that was never preceded by
     *   an [acquire] doesn't decrement; it reports whether the count is already
     *   at zero (matching the previous inline behaviour).
     */
    fun release(): Boolean {
      val remaining = if (counted) {
        counted = false
        active.decrementAndGet()
      } else {
        active.get()
      }
      return remaining <= 0
    }
  }
}
