package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.infrastructure.video.ProcessDeadlineExceededException;

import java.time.Duration;
import java.util.function.LongSupplier;

/** One monotonic deadline shared by every blocking stage of an optimization attempt. */
public final class OptimizationAttemptBudget {
  public static final Duration REQUIRED_BUDGET = Duration.ofMillis(600_000);

  private final LongSupplier nanoTime;
  private final long startedNanos;
  private final long budgetNanos;

  private OptimizationAttemptBudget(Duration budget, LongSupplier nanoTime) {
    this.nanoTime = nanoTime;
    this.startedNanos = nanoTime.getAsLong();
    this.budgetNanos = budget.toNanos();
  }

  public static OptimizationAttemptBudget start() {
    return new OptimizationAttemptBudget(REQUIRED_BUDGET, System::nanoTime);
  }

  public static OptimizationAttemptBudget start(Duration budget, LongSupplier nanoTime) {
    return new OptimizationAttemptBudget(budget, nanoTime);
  }

  public Duration remaining() {
    long elapsed = nanoTime.getAsLong() - startedNanos;
    if (elapsed >= budgetNanos) {
      throw new ProcessDeadlineExceededException("ATTEMPT_DEADLINE_EXCEEDED");
    }
    return Duration.ofNanos(budgetNanos - Math.max(0, elapsed));
  }

  public long elapsedMillis() {
    return Math.max(0, nanoTime.getAsLong() - startedNanos) / 1_000_000;
  }
}
