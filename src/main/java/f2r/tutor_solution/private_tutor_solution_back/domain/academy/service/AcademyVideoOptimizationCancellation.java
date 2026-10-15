package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AcademyVideoOptimizationCancellation {
  private final ConcurrentHashMap<Long, ActiveAttempt> active = new ConcurrentHashMap<>();

  public Handle register(Long jobId, String token) {
    ActiveAttempt attempt =
        new ActiveAttempt(token, new AtomicBoolean(), new AtomicBoolean());
    if (active.putIfAbsent(jobId, attempt) != null) {
      throw new IllegalStateException("job already active locally");
    }
    return new Handle(jobId, attempt);
  }

  public java.util.Map<Long, ActiveAttempt> snapshot() {
    return java.util.Map.copyOf(active);
  }

  public void signal(Long jobId, String token) {
    ActiveAttempt attempt = active.get(jobId);
    if (attempt != null && attempt.token().equals(token)) attempt.cancelled().set(true);
  }

  public record ActiveAttempt(
      String token, AtomicBoolean cancelled, AtomicBoolean mediaProgress) {}

  public final class Handle implements AutoCloseable {
    private final Long jobId;
    private final ActiveAttempt attempt;

    private Handle(Long jobId, ActiveAttempt attempt) {
      this.jobId = jobId;
      this.attempt = attempt;
    }

    public boolean cancelled() {
      return attempt.cancelled().get();
    }

    public void recordMediaProgress() {
      attempt.mediaProgress().set(true);
    }

    @Override
    public void close() {
      active.remove(jobId, attempt);
    }
  }
}
