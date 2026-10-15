package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.time.Duration;

public final class UploadWorkBudget {
  private final long deadlineNanos;

  private UploadWorkBudget(Duration remaining) {
    long nanos = Math.max(0, remaining.toNanos());
    this.deadlineNanos = System.nanoTime() + nanos;
  }

  public static UploadWorkBudget of(Duration remaining) {
    return new UploadWorkBudget(remaining);
  }

  public Duration remaining() {
    long nanos = deadlineNanos - System.nanoTime();
    return Duration.ofNanos(Math.max(0, nanos));
  }

  public boolean expired() {
    return remaining().isZero();
  }
}
