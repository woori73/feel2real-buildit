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
 * AI 분석 호출량 통제: 사용자별 하루 한도와 서비스 전체 하루 한도(한국 시간 기준 날짜).
 *
 * <p>메모리 안에서만 센다(재시작하면 초기화). 비용을 거칠게 묶는 안전장치이며, 정밀한 과금 집계는 아니다.
 * AI 호출이 실패하면 {@link #refund}로 횟수를 돌려준다.
 */
@Component
public class ExamInsightRateLimiter {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public enum Result { OK, USER_LIMIT, GLOBAL_LIMIT }

  private final int perUserDaily;
  private final int globalDaily;
  private final Clock clock;
  private final Map<Long, Integer> perUser = new HashMap<>();
  private int global;
  private LocalDate day;

  @Autowired
  public ExamInsightRateLimiter(
      @Value("${app.exam-insight.per-user-daily-limit:3}") int perUserDaily,
      @Value("${app.exam-insight.global-daily-limit:100}") int globalDaily) {
    this(perUserDaily, globalDaily, Clock.system(KST));
  }

  ExamInsightRateLimiter(int perUserDaily, int globalDaily, Clock clock) {
    this.perUserDaily = perUserDaily;
    this.globalDaily = globalDaily;
    this.clock = clock;
  }

  public synchronized Result tryAcquire(long userId) {
    rollIfNewDay();
    if (global >= globalDaily) return Result.GLOBAL_LIMIT;
    int used = perUser.getOrDefault(userId, 0);
    if (used >= perUserDaily) return Result.USER_LIMIT;
    perUser.put(userId, used + 1);
    global++;
    return Result.OK;
  }

  public synchronized void refund(long userId) {
    rollIfNewDay();
    int used = perUser.getOrDefault(userId, 0);
    if (used > 0) {
      perUser.put(userId, used - 1);
      if (global > 0) global--;
    }
  }

  public synchronized int remaining(long userId) {
    rollIfNewDay();
    return Math.max(0, Math.min(perUserDaily - perUser.getOrDefault(userId, 0), globalDaily - global));
  }

  public int perUserDailyLimit() {
    return perUserDaily;
  }

  private void rollIfNewDay() {
    LocalDate today = LocalDate.now(clock);
    if (!today.equals(day)) {
      day = today;
      perUser.clear();
      global = 0;
    }
  }
}
