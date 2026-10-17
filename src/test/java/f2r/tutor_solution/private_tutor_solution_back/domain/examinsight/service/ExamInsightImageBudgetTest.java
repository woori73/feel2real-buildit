package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("사진 인식 일일 한도")
class ExamInsightImageBudgetTest {

  @Test
  @DisplayName("사용자별 한도를 넘으면 거절하고, 한 번에 여러 장도 전부 아니면 전혀 확보하지 않는다")
  void perUserLimit_isAllOrNothing() {
    // given
    var b = new ExamInsightImageBudget(3, 100);

    // when / then
    assertThat(b.tryAcquire(1, 2)).isEqualTo(ExamInsightImageBudget.Result.OK);
    assertThat(b.tryAcquire(1, 2)).isEqualTo(ExamInsightImageBudget.Result.USER_LIMIT);
    assertThat(b.remaining(1)).isEqualTo(1);
    assertThat(b.tryAcquire(1, 1)).isEqualTo(ExamInsightImageBudget.Result.OK);
    assertThat(b.remaining(1)).isZero();
    assertThat(b.tryAcquire(2, 1)).isEqualTo(ExamInsightImageBudget.Result.OK); // 다른 사용자는 별개
  }

  @Test
  @DisplayName("전체 한도에 닿으면 사용자 한도가 남아 있어도 거절한다")
  void globalLimit_blocksEveryone() {
    // given
    var b = new ExamInsightImageBudget(10, 2);

    // when
    b.tryAcquire(1, 2);

    // then
    assertThat(b.tryAcquire(2, 1)).isEqualTo(ExamInsightImageBudget.Result.GLOBAL_LIMIT);
    assertThat(b.remaining(2)).isZero();
  }

  @Test
  @DisplayName("실패한 장수는 돌려주고, 확보한 것보다 많이 돌려주지는 않는다")
  void refund_returnsOnlyWhatWasAcquired() {
    // given
    var b = new ExamInsightImageBudget(5, 50);
    b.tryAcquire(1, 2);

    // when
    b.refund(1, 1);
    b.refund(1, 99);

    // then
    assertThat(b.remaining(1)).isEqualTo(5);
  }

  @Test
  @DisplayName("한국 시간 날짜가 바뀌면 초기화된다")
  void newDay_resets() {
    // given: 15:00 UTC = 다음 날 00:00 KST
    var day1 = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
    var b = new ExamInsightImageBudget(2, 10, day1);
    b.tryAcquire(1, 2);
    assertThat(b.remaining(1)).isZero();

    // when
    day1.set(Instant.parse("2026-10-17T15:00:01Z"));

    // then
    assertThat(b.remaining(1)).isEqualTo(2);
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void set(Instant i) {
      now = i;
    }

    @Override public ZoneId getZone() { return ZoneId.of("Asia/Seoul"); }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }
}
