package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.fssagent;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Phase 1(C) Guided Agent 세션 — 서버는 "사용자가 몇 단계를 완료했다고 눌렀다"는 사실만
 * 메모리에 잠깐 보관한다. 브라우저 자동화(gateway)는 존재하지 않으며, DB 영속화도 없다.
 *
 * <p>로그 규칙(handoff §8): {@code userId}와 단계 label만 남긴다 — 화면 내용·입력값·
 * 인증 정보는 이 경로에 애초에 들어오지 않는다.
 */
@Service
public class FssGuidedAgentService {

  static final Duration SESSION_TTL = Duration.ofDays(3);

  private static final Logger log = LoggerFactory.getLogger(FssGuidedAgentService.class);

  private record Entry(FssGuidedSession session, Instant touchedAt) {}

  private final Map<Long, Entry> sessions = new ConcurrentHashMap<>();

  public FssGuidedSession session(long userId) {
    pruneExpired();
    Entry entry = sessions.get(userId);
    if (entry == null) {
      FssGuidedSession fresh = FssGuidedSession.start();
      sessions.put(userId, new Entry(fresh, Instant.now()));
      log.info("fss guided agent started userId={} step={}", userId, labelOf(fresh));
      return fresh;
    }
    return entry.session();
  }

  /** 사용자가 "이 단계를 완료했습니다"를 눌렀다 — 서버는 화면을 확인하지 않았고 확인할 수도 없다. */
  public FssGuidedSession completeCurrentStep(long userId) {
    FssGuidedSession next = session(userId).advanced();
    sessions.put(userId, new Entry(next, Instant.now()));
    log.info("fss guided agent advanced userId={} step={}", userId, labelOf(next));
    return next;
  }

  public FssGuidedSession previousStep(long userId) {
    FssGuidedSession prev = session(userId).retreated();
    sessions.put(userId, new Entry(prev, Instant.now()));
    log.info("fss guided agent retreated userId={} step={}", userId, labelOf(prev));
    return prev;
  }

  public FssGuidedSession restart(long userId) {
    FssGuidedSession fresh = FssGuidedSession.start();
    sessions.put(userId, new Entry(fresh, Instant.now()));
    log.info("fss guided agent restarted userId={} step={}", userId, labelOf(fresh));
    return fresh;
  }

  private static String labelOf(FssGuidedSession session) {
    return session.finished()
        ? FssGuidedWorkflow.total() + "-완료"
        : FssGuidedWorkflow.step(session.currentStep()).title();
  }

  private void pruneExpired() {
    Instant cutoff = Instant.now().minus(SESSION_TTL);
    sessions.entrySet().removeIf(e -> e.getValue().touchedAt().isBefore(cutoff));
  }
}
