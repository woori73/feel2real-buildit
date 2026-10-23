package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class McpUserLimiterTest {
  @Test
  void concurrentAcquireAndCleanupNeverExceedUserLimit() throws Exception {
    var limiter = new McpUserLimiter(32, 2);
    var active = new java.util.concurrent.atomic.AtomicInteger();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 8; i++)
        futures.add(
            pool.submit(
                () -> {
                  for (int j = 0; j < 2000; j++) {
                    try (var permit = limiter.acquire(1)) {
                      if (permit == null) continue;
                      int count = active.incrementAndGet();
                      try {
                        assertTrue(count <= 2);
                        Thread.yield();
                      } finally {
                        active.decrementAndGet();
                      }
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                  }
                }));
      for (var future : futures) future.get();
    }
    assertEquals(0, active.get());
    var first = limiter.acquire(1);
    var second = limiter.acquire(1);
    assertNotNull(first);
    assertNotNull(second);
    assertNull(limiter.acquire(1));
    first.close();
    first.close();
    var third = limiter.acquire(1);
    assertNotNull(third);
    assertNull(limiter.acquire(1));
    second.close();
    third.close();
  }

  @Test
  void oneUserCannotExceedPerUserLimitButOthersStillProceed() throws Exception {
    // given
    var limiter = new McpUserLimiter(6, 2);
    var a1 = limiter.acquire(1);
    var a2 = limiter.acquire(1);
    // when
    var a3 = limiter.acquire(1);
    var other = limiter.acquire(2);
    // then
    assertNotNull(a1);
    assertNotNull(a2);
    assertNull(a3);
    assertNotNull(other);
    a1.close();
    assertNotNull(limiter.acquire(1));
  }

  @Test
  void globalLimitReleasesPerUserPermitWhenExceeded() throws Exception {
    // given
    var limiter = new McpUserLimiter(1, 2);
    var first = limiter.acquire(1);
    // when
    var second = limiter.acquire(2);
    // then
    assertNull(second);
    first.close();
    assertNotNull(limiter.acquire(2));
  }
}
