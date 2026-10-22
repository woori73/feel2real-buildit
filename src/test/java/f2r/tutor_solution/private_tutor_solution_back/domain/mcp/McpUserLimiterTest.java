package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class McpUserLimiterTest {
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
