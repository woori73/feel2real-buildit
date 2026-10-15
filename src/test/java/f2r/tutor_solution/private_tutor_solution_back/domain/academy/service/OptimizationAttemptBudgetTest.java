package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.ProcessDeadlineExceededException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimizationAttemptBudgetTest {

  @Test
  void givenOneBudget_whenStagesConsumeTime_thenTheyShareTheSameDeadline() {
    AtomicLong now = new AtomicLong();
    OptimizationAttemptBudget budget =
        OptimizationAttemptBudget.start(Duration.ofMillis(600_000), now::get);

    now.set(Duration.ofMillis(599_999).toNanos());
    assertThat(budget.remaining()).isEqualTo(Duration.ofMillis(1));

    now.set(Duration.ofMillis(600_000).toNanos());
    assertThatThrownBy(budget::remaining)
        .isInstanceOf(ProcessDeadlineExceededException.class);
  }
}
