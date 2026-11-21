package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 접속 코드 로그인 시도 제한: 같은 IP에서 10분에 10번까지(코드 추측 방지). 서버 메모리에만 둔다. */
@Component
public class StudentAppLoginThrottle {

  static final int MAX_TRIES = 10;
  static final long WINDOW_MS = 10 * 60 * 1000L;
  private final Map<String, Deque<Long>> tries = new ConcurrentHashMap<>();

  /** 시도를 하나 세고, 허용되면 true. */
  public boolean tryAcquire(String key, long nowMs) {
    if (tries.size() > 10_000) tries.clear(); // 메모리 보호
    Deque<Long> q = tries.computeIfAbsent(key == null ? "?" : key, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && nowMs - q.peekFirst() > WINDOW_MS) q.pollFirst();
      if (q.size() >= MAX_TRIES) return false;
      q.addLast(nowMs);
      return true;
    }
  }
}
