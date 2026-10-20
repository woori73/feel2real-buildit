package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HelpRateLimiterTest {

  @Test
  void tryAcquire_overLimit_blocksUntilWindowPasses() {
    // given
    HelpRateLimiter l = new HelpRateLimiter(2, 1000);
    // when / then
    assertThat(l.tryAcquire("u1", 0)).isTrue();
    assertThat(l.tryAcquire("u1", 10)).isTrue();
    assertThat(l.tryAcquire("u1", 20)).isFalse();
    assertThat(l.tryAcquire("u2", 20)).isTrue();
    assertThat(l.tryAcquire("u1", 1001)).isTrue();
  }
}
