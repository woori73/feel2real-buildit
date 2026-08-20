package f2r.tutor_solution.private_tutor_solution_back.infrastructure.video;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultProcessRunnerProgressTest {

  @Test
  void givenRunningProcess_whenElapsedMovesFrom599999To600000_thenExactBoundaryKillsIt() {
    SequencedNanos time = new SequencedNanos(0, 599_999, 600_000);
    DefaultProcessRunner runner = new DefaultProcessRunner(time);

    assertThatThrownBy(() -> runner.runProgressAware(
        List.of("sh", "-c", "sleep 10"),
        Duration.ofMillis(600_000), Duration.ZERO, () -> false))
        .isInstanceOf(ProcessDeadlineExceededException.class);
  }

  @Test
  void givenNoMediaProgress_whenSilenceMovesFrom89999To90000_thenExactBoundaryStalls() {
    SequencedNanos time = new SequencedNanos(0, 89_999, 90_000);
    DefaultProcessRunner runner = new DefaultProcessRunner(time);

    assertThatThrownBy(() -> runner.runProgressAware(
        List.of("sh", "-c", "echo log-only >&2; sleep 10", ProcessRunner.PROGRESS_FILE_TOKEN),
        Duration.ofMillis(600_000), Duration.ofMillis(90_000), () -> false))
        .isInstanceOf(ProcessStalledException.class);
  }

  @Test
  void givenLeaseCancellation_whenProcessRuns_thenItIsCancelledBeforeDeadline() {
    DefaultProcessRunner runner = new DefaultProcessRunner();

    assertThatThrownBy(() -> runner.runProgressAware(
        List.of("sh", "-c", "sleep 10 & wait"),
        Duration.ofMillis(600_000), Duration.ZERO, () -> true))
        .isInstanceOf(ProcessCancelledException.class);
  }

  private static final class SequencedNanos implements java.util.function.LongSupplier {
    private final long[] millis;
    private final AtomicInteger index = new AtomicInteger();

    private SequencedNanos(long... millis) {
      this.millis = millis;
    }

    @Override
    public long getAsLong() {
      int current = Math.min(index.getAndIncrement(), millis.length - 1);
      return Duration.ofMillis(millis[current]).toNanos();
    }
  }
}
