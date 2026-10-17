package f2r.tutor_solution.private_tutor_solution_back.domain.examinsight.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 사진 인식(OCR) 호출량 통제: 사용자별·서비스 전체 하루 장수 한도(한국 시간 기준 날짜).
 *
 * <p>분석 횟수 한도({@link ExamInsightRateLimiter})와 별개다. 사진은 한 장마다 모델을 한 번 부르므로 장수로 센다.
 * 메모리 안에서만 센다(재시작하면 초기화). 요청 전체를 한 번에 확보하고(전부 아니면 전혀), 실패한 장수는 돌려준다.
 */
@Component
public class ExamInsightImageBudget {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final int perUserDaily;
  private final int globalDaily;
  private final Clock clock;
  private final Map<Long, Integer> perUser = new HashMap<>();
  private int global;
  private LocalDate day;

  @Autowired
  public ExamInsightImageBudget(
      @Value("${app.exam-insight.image-daily-limit-per-user:60}") int perUserDaily,
      @Value("${app.exam-insight.image-daily-limit-global:600}") int globalDaily) {
    this(perUserDaily, globalDaily, Clock.system(KST));
  }

  ExamInsightImageBudget(int perUserDaily, int globalDaily, Clock clock) {
    this.perUserDaily = perUserDaily;
    this.globalDaily = globalDaily;
    this.clock = clock;
  }

  public enum Result { OK, USER_LIMIT, GLOBAL_LIMIT }

  public synchronized Result tryAcquire(long userId, int count) {
    roll();
    if (global + count > globalDaily) return Result.GLOBAL_LIMIT;
    int used = perUser.getOrDefault(userId, 0);
    if (used + count > perUserDaily) return Result.USER_LIMIT;
    perUser.put(userId, used + count);
    global += count;
    return Result.OK;
  }

  public synchronized void refund(long userId, int count) {
    roll();
    int used = perUser.getOrDefault(userId, 0);
    int give = Math.min(count, used);
    perUser.put(userId, used - give);
    global = Math.max(0, global - give);
  }

  public synchronized int remaining(long userId) {
    roll();
    return Math.max(0, Math.min(perUserDaily - perUser.getOrDefault(userId, 0), globalDaily - global));
  }

  public int perUserDailyLimit() {
    return perUserDaily;
  }

  private void roll() {
    LocalDate today = LocalDate.now(clock);
    if (!today.equals(day)) {
      day = today;
      perUser.clear();
      global = 0;
    }
  }
}
