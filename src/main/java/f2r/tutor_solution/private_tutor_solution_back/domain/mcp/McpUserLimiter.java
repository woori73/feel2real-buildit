package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** 서버 전체와 사용자별 동시 내부 조회 수를 모두 제한해 한 사용자가 다른 사용자의 조회를 막지 못하게 한다. */
final class McpUserLimiter {
  private final Semaphore global;
  private final int perUser;
  private final ConcurrentHashMap<Long, Integer> users = new ConcurrentHashMap<>();

  McpUserLimiter(int global, int perUser) {
    this.global = new Semaphore(global);
    this.perUser = perUser;
  }

  /** 허가를 얻으면 반환 객체의 close()로 반드시 돌려준다. 얻지 못하면 null. */
  AutoCloseable acquire(long userId) {
    if (!global.tryAcquire()) return null;
    var admitted = new AtomicBoolean();
    users.compute(
        userId,
        (id, active) -> {
          int count = active == null ? 0 : active;
          if (count >= perUser) return count;
          admitted.set(true);
          return count + 1;
        });
    if (!admitted.get()) {
      global.release();
      return null;
    }
    var closed = new AtomicBoolean();
    return () -> {
      if (!closed.compareAndSet(false, true)) return;
      // compute serializes acquire/release for this user, including removal at zero.
      users.compute(
          userId,
          (id, active) -> {
            if (active == 1) return null;
            return active - 1;
          });
      global.release();
    };
  }
}
