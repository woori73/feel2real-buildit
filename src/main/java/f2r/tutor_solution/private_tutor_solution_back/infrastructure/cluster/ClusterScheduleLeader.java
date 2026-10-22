package f2r.tutor_solution.private_tutor_solution_back.infrastructure.cluster;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Stable leader across cron invocations, rather than reacquiring per invocation. */
@Component
public class ClusterScheduleLeader {
  private final MysqlNamedLocks locks;
  private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "cluster-schedule-leader"); t.setDaemon(true); return t;
  });
  private MysqlNamedLocks.Session session;
  private long acquiredAt;
  private boolean stopped;
  private int active;

  public ClusterScheduleLeader(MysqlNamedLocks locks) { this.locks = locks; }

  @EventListener(ApplicationReadyEvent.class)
  public void start() { monitor.scheduleWithFixedDelay(this::tick, 0, 2, TimeUnit.SECONDS); }

  synchronized void tick() {
    if (stopped) return;
    if (session != null && !session.valid()) { session.close(); session = null; }
    if (session == null) {
      try {
        session = locks.tryAcquire("scheduled-jobs");
        if (session != null) acquiredAt = System.nanoTime();
      } catch (java.sql.SQLException unavailable) { /* fail closed, no vendor message */ }
    }
  }

  @FunctionalInterface public interface Work { Object run() throws Throwable; }

  public Object execute(Work work) throws Throwable {
    synchronized (this) {
      if (stopped || session == null || System.nanoTime() - acquiredAt < TimeUnit.SECONDS.toNanos(10) || !session.valid()) return null;
      active++;
    }
    try { return work.run(); }
    finally {
      synchronized (this) {
        active--;
        if (stopped && active == 0) release();
      }
    }
  }

  private void release() {
    if (session != null) { session.close(); session = null; }
  }

  @PreDestroy public synchronized void stop() {
    stopped = true;
    monitor.shutdownNow();
    // Normal shutdown retains leadership until in-flight jobs finish.
    if (active == 0) release();
  }
}
