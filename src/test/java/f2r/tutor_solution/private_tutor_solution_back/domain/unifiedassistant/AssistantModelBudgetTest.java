package f2r.tutor_solution.private_tutor_solution_back.domain.unifiedassistant;

import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AssistantModelBudgetTest {
  final AtomicLong now=new AtomicLong(1_000_000L);
  @Test void givenPerMinuteLimit_whenExceeded_thenDeniedUntilWindowPasses() {
    var budget=new AssistantModelBudget(2,100,3,now::get);
    assertThat(budget.tryAcquire(1L)).isTrue();assertThat(budget.tryAcquire(1L)).isTrue();assertThat(budget.tryAcquire(1L)).isFalse();
    now.addAndGet(61_000);
    assertThat(budget.tryAcquire(1L)).isTrue();
  }
  @Test void givenPerDayLimit_whenExceeded_thenDeniedUntilDayPasses() {
    var budget=new AssistantModelBudget(100,2,3,now::get);
    budget.tryAcquire(1L);now.addAndGet(120_000);budget.tryAcquire(1L);now.addAndGet(120_000);
    assertThat(budget.tryAcquire(1L)).isFalse();
    now.addAndGet(86_400_000L);
    assertThat(budget.tryAcquire(1L)).isTrue();
  }
  @Test void givenTwoUsers_whenOneExhausted_thenOtherUnaffected() {
    var budget=new AssistantModelBudget(1,10,3,now::get);
    budget.tryAcquire(1L);
    assertThat(budget.tryAcquire(1L)).isFalse();assertThat(budget.tryAcquire(2L)).isTrue();
  }
}
