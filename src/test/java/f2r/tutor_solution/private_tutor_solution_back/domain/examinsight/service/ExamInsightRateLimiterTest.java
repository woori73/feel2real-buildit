package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ExamInsightRateLimiterTest {

  static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Test
  void 사용자_한도를_넘으면_거절한다() {
    // given limit 2
    ExamInsightRateLimiter l = new ExamInsightRateLimiter(2, 100, Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), KST));
    // when
    l.tryAcquire(1);
    l.tryAcquire(1);
    // then
    assertThat(l.tryAcquire(1)).isEqualTo(ExamInsightRateLimiter.Result.USER_LIMIT);
    assertThat(l.tryAcquire(2)).isEqualTo(ExamInsightRateLimiter.Result.OK);
  }

  @Test
  void 전체_한도를_넘으면_거절한다() {
    // given global 1
    ExamInsightRateLimiter l = new ExamInsightRateLimiter(5, 1, Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), KST));
    // when
    l.tryAcquire(1);
    // then
    assertThat(l.tryAcquire(2)).isEqualTo(ExamInsightRateLimiter.Result.GLOBAL_LIMIT);
  }

  @Test
  void 환불하면_횟수가_돌아온다() {
    // given
    ExamInsightRateLimiter l = new ExamInsightRateLimiter(1, 10, Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), KST));
    l.tryAcquire(1);
    // when
    l.refund(1);
    // then
    assertThat(l.remaining(1)).isEqualTo(1);
    assertThat(l.tryAcquire(1)).isEqualTo(ExamInsightRateLimiter.Result.OK);
  }

  @Test
  void 환불은_0_아래로_내려가지_않는다() {
    // given nothing acquired
    ExamInsightRateLimiter l = new ExamInsightRateLimiter(1, 10, Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), KST));
    // when
    l.refund(1);
    // then
    assertThat(l.remaining(1)).isEqualTo(1);
  }

  @Test
  void 한국시간_자정이_지나면_초기화된다() {
    // given a mutable clock
    Instant[] now = {Instant.parse("2026-10-16T01:00:00Z")};
    Clock clock = new Clock() {
      public ZoneId getZone() { return KST; }
      public Clock withZone(ZoneId z) { return this; }
      public Instant instant() { return now[0]; }
    };
    ExamInsightRateLimiter l = new ExamInsightRateLimiter(1, 10, clock);
    l.tryAcquire(1);
    assertThat(l.tryAcquire(1)).isEqualTo(ExamInsightRateLimiter.Result.USER_LIMIT);
    // when KST next day (UTC 15:00 = KST 00:00)
    now[0] = Instant.parse("2026-10-16T15:01:00Z");
    // then
    assertThat(l.tryAcquire(1)).isEqualTo(ExamInsightRateLimiter.Result.OK);
  }
}
