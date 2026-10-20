package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** 사용자별 분당 호출 제한(메모리). 남용·재시도 폭주를 막는 최소 장치. */
public class HelpRateLimiter {

  private final int limit;
  private final long windowMillis;
  private final ConcurrentHashMap<String, Deque<Long>> calls = new ConcurrentHashMap<>();

  public HelpRateLimiter(int limit, long windowMillis) {
    this.limit = limit;
    this.windowMillis = windowMillis;
  }

  public boolean tryAcquire(String key, long nowMillis) {
    Deque<Long> q = calls.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && nowMillis - q.peekFirst() >= windowMillis) q.pollFirst();
      if (q.size() >= limit) return false;
      q.addLast(nowMillis);
      return true;
    }
  }
}
