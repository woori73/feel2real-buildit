package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** 서버 전체와 사용자별 동시 내부 조회 수를 모두 제한해 한 사용자가 다른 사용자의 조회를 막지 못하게 한다. */
final class McpUserLimiter {
  private final Semaphore global;
  private final int perUser;
  private final ConcurrentHashMap<Long, Semaphore> users = new ConcurrentHashMap<>();

  McpUserLimiter(int global, int perUser) {
    this.global = new Semaphore(global);
    this.perUser = perUser;
  }

  /** 허가를 얻으면 반환 객체의 close()로 반드시 돌려준다. 얻지 못하면 null. */
  AutoCloseable acquire(long userId) {
    Semaphore mine = users.computeIfAbsent(userId, id -> new Semaphore(perUser));
    if (!mine.tryAcquire()) return null;
    if (!global.tryAcquire()) {
      mine.release();
      return null;
    }
    return () -> {
      global.release();
      mine.release();
      if (mine.availablePermits() == perUser) users.remove(userId, mine);
    };
  }
}
