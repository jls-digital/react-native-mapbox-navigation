package com.margelo.nitro.jlsdigital.reactnativemapboxnavigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ── NavigationInstanceCounter ─────────────────────────────────────────────
// Off-device guard for the "disable the SDK singleton only on the LAST
// instance" contract — the Android analogue of iOS's refcounted ProviderCache.
// release() returning true means "disable MapboxNavigationApp now".
class NavigationInstanceCounterTest {

  @Test
  fun singleInstanceDisablesOnRelease() {
    val counter = NavigationInstanceCounter()
    val m = counter.newMembership()
    m.acquire()
    assertEquals(1, counter.count())
    assertTrue("last instance release must signal disable", m.release())
    assertEquals(0, counter.count())
  }

  @Test
  fun siblingReleaseDoesNotDisableWhileAnotherIsLive() {
    val counter = NavigationInstanceCounter()
    val a = counter.newMembership().apply { acquire() }
    val b = counter.newMembership().apply { acquire() }
    assertEquals(2, counter.count())
    // First sibling detaches — must NOT disable, the other is still mounted.
    assertFalse(a.release())
    assertEquals(1, counter.count())
    // Last one detaches — now disable.
    assertTrue(b.release())
    assertEquals(0, counter.count())
  }

  @Test
  fun acquireIsIdempotent() {
    val counter = NavigationInstanceCounter()
    val m = counter.newMembership()
    m.acquire()
    m.acquire() // setup ran twice — still counts once
    assertEquals(1, counter.count())
    assertTrue(m.release())
    assertEquals(0, counter.count())
  }

  @Test
  fun releaseWithoutAcquireDoesNotDecrementButReportsZeroWhenIdle() {
    val counter = NavigationInstanceCounter()
    val m = counter.newMembership()
    // Detached before setup ever ran: no decrement, and with nothing live the
    // count is already 0 → reports disable (harmless; SDK already disabled).
    assertTrue(m.release())
    assertEquals(0, counter.count())
  }

  @Test
  fun uncountedReleaseDoesNotDisableWhileAnotherIsLive() {
    val counter = NavigationInstanceCounter()
    val live = counter.newMembership().apply { acquire() }
    val neverSetUp = counter.newMembership()
    // The view that never set up detaches: must not disable the live sibling.
    assertFalse(neverSetUp.release())
    assertEquals(1, counter.count())
    assertTrue(live.release())
  }

  @Test
  fun doubleReleaseIsBalanced() {
    val counter = NavigationInstanceCounter()
    val m = counter.newMembership().apply { acquire() }
    assertTrue(m.release())
    assertEquals(0, counter.count())
    // A second release must not drive the count negative.
    assertTrue(m.release())
    assertEquals(0, counter.count())
  }
}
