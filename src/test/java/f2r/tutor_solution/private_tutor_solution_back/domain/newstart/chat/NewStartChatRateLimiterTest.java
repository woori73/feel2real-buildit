package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NewStartChatRateLimiter — sliding window 도배 방지")
class NewStartChatRateLimiterTest {

  /** 테스트용 가변 클럭(java.time에 MutableClock은 없다). */
  private static final class MutableClock extends Clock {
    private Instant now;
    MutableClock(Instant start) { this.now = start; }
    void advance(Duration d) { now = now.plus(d); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }

  @Test
  @DisplayName("given 짧은 시간 연속 전송 when 한도 초과 then 거부한다")
  void rejectsWhenWindowFull() {
    MutableClock clock = new MutableClock(Instant.parse("2026-09-19T00:00:00Z"));
    NewStartChatRateLimiter limiter = new NewStartChatRateLimiter(clock, 3, Duration.ofSeconds(15));

    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse();
  }

  @Test
  @DisplayName("given 창이 지나면 when 다시 전송 then 허용한다")
  void allowsAgainAfterWindowPasses() {
    MutableClock clock = new MutableClock(Instant.parse("2026-09-19T00:00:00Z"));
    NewStartChatRateLimiter limiter = new NewStartChatRateLimiter(clock, 3, Duration.ofSeconds(15));

    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse();

    clock.advance(Duration.ofSeconds(16));

    assertThat(limiter.tryAcquire(1L)).isTrue();
  }

  @Test
  @DisplayName("given 다른 사용자 when 같은 시간 전송 then 서로 독립적으로 판단한다")
  void limitsArePerUser() {
    MutableClock clock = new MutableClock(Instant.parse("2026-09-19T00:00:00Z"));
    NewStartChatRateLimiter limiter = new NewStartChatRateLimiter(clock, 3, Duration.ofSeconds(15));

    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isTrue();
    assertThat(limiter.tryAcquire(1L)).isFalse();
    assertThat(limiter.tryAcquire(2L)).isTrue();
  }

  @Test
  @DisplayName("given null 사용자 when 전송 then 거부한다")
  void rejectsNullUser() {
    MutableClock clock = new MutableClock(Instant.parse("2026-09-19T00:00:00Z"));
    NewStartChatRateLimiter limiter = new NewStartChatRateLimiter(clock, 3, Duration.ofSeconds(15));
    assertThat(limiter.tryAcquire(null)).isFalse();
  }
}
