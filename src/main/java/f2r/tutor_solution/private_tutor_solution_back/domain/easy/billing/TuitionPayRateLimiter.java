package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 공개 청구 링크 주소의 IP별 분당 요청 제한(메모리). 기록하는 IP 수에 상한을 두어 메모리가 늘지 않게 한다. */
@Component
public class TuitionPayRateLimiter {

  static final int PER_MINUTE = 30;
  static final int MAX_TRACKED = 5000;
  private static final long WINDOW_MS = 60_000L;

  private final LinkedHashMap<String, Deque<Long>> buckets =
      new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Deque<Long>> eldest) {
          return size() > MAX_TRACKED;
        }
      };

  public boolean tryAcquire(String ip) {
    return tryAcquire(ip, System.currentTimeMillis());
  }

  synchronized boolean tryAcquire(String ip, long nowMs) {
    if (ip == null) {
      return false;
    }
    Deque<Long> times = buckets.computeIfAbsent(ip, k -> new ArrayDeque<>());
    while (!times.isEmpty() && nowMs - times.peekFirst() >= WINDOW_MS) {
      times.pollFirst();
    }
    if (times.size() >= PER_MINUTE) {
      return false;
    }
    times.addLast(nowMs);
    return true;
  }
}
