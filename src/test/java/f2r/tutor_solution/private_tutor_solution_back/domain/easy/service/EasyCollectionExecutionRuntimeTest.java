package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EasyCollectionExecutionRuntimeTest {

  @Test
  void executionDeadlineFailsWithoutUnsafeThreadTermination() {
    EasyCollectionExecutionRuntime runtime = new EasyCollectionExecutionRuntime(1, 1);
    try {
      assertThatThrownBy(() -> runtime.execute(() -> {
        Thread.sleep(10_000);
        return "late";
      }, () -> {}))
          .isInstanceOf(EasyCollectionException.class)
          .satisfies(error -> assertThat(((EasyCollectionException) error).errorCode())
              .isEqualTo(EasyCollectionErrorCode.EXECUTION_TIMEOUT))
          .hasMessageContaining("deadline");
    } finally {
      runtime.shutdown();
    }
  }

  @Test
  void longExecutionEmitsHeartbeat() {
    EasyCollectionExecutionRuntime runtime = new EasyCollectionExecutionRuntime(4, 1);
    AtomicInteger heartbeats = new AtomicInteger();
    try {
      String value = runtime.execute(() -> {
        Thread.sleep(1_300);
        return "ok";
      }, heartbeats::incrementAndGet);
      assertThat(value).isEqualTo("ok");
      assertThat(heartbeats.get()).isGreaterThanOrEqualTo(1);
    } finally {
      runtime.shutdown();
    }
  }
}
