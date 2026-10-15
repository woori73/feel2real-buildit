package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/**
 * NewStart 내부 쪽지 도배 방지 — 채팅 rate limiter와 동일 패턴(사용자당 sliding
 * window, in-memory, 단일 인스턴스 전제). 1분 안에 10건으로 제한한다.
 *
 * <p>테스트를 위해 {@link Clock}을 주입받는다.
 */
@Component
public class NewStartDmRateLimiter {

  private final Clock clock;
  private final int maxMessages;
  private final Duration window;
  private final Map<Long, Deque<Instant>> recentByUser = new ConcurrentHashMap<>();

  public NewStartDmRateLimiter() {
    this(Clock.systemUTC(), 10, Duration.ofMinutes(1));
  }

  /** 테스트용 — 고정 Clock/한도 주입. */
  public NewStartDmRateLimiter(Clock clock, int maxMessages, Duration window) {
    this.clock = clock;
    this.maxMessages = maxMessages;
    this.window = window;
  }

  /** 전송 허용 여부. 허용 시 이번 전송을 window에 기록한다. */
  public boolean tryAcquire(Long userId) {
    if (userId == null) {
      return false;
    }
    Instant now = clock.instant();
    Instant floor = now.minus(window);
    Deque<Instant> recent = recentByUser.computeIfAbsent(userId, key -> new ConcurrentLinkedDeque<>());
    Instant oldest;
    while ((oldest = recent.peekFirst()) != null && oldest.isBefore(floor)) {
      recent.pollFirst();
    }
    if (recent.size() >= maxMessages) {
      return false;
    }
    recent.addLast(now);
    return true;
  }
}
