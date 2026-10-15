package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/**
 * NewStart 회원 채팅 도배 방지 — 최소한의 서버측 보호.
 *
 * <p>사용자당 sliding window(기본 15초 안에 10건)로 연속 전송을 제한한다. 완전한
 * moderation 시스템이 아니라 beta 서비스의 flood gate다. in-memory라 서버 재시작 시
 * 초기화되며(단일 인스턴스 전제 — 현 배포 구조) 인프라를 새로 붙이지 않는다.
 *
 * <p>테스트를 위해 {@link Clock}을 주입받는다.
 */
@Component
public class NewStartChatRateLimiter {

  private final Clock clock;
  private final int maxMessages;
  private final Duration window;
  private final Map<Long, Deque<Instant>> recentByUser = new ConcurrentHashMap<>();

  public NewStartChatRateLimiter() {
    this(Clock.systemUTC(), 10, Duration.ofSeconds(15));
  }

  /** 테스트용 — 고정 Clock/한도 주입. */
  public NewStartChatRateLimiter(Clock clock, int maxMessages, Duration window) {
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
