package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.entity.WikiDailyVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.repository.WikiDailyVisitRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * /wiki 방문 집계. {@code AcademyVisitStatisticsService} 와 동일한 방식:
 * 날짜당 1행 native upsert + {@link HttpSession} 로 "같은 세션 / 같은 날" 중복 방문을 무시한다.
 * 단순 새로고침으로 방문 수가 늘지 않는다. IP 등 개인정보는 저장하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class WikiVisitService {

  private static final int RECENT_DAY_COUNT = 7;
  private static final String SESSION_KEY_PREFIX = "wiki-visit:";

  private final WikiDailyVisitRepository visitRepository;
  private final Clock clock;

  @Autowired
  public WikiVisitService(WikiDailyVisitRepository visitRepository) {
    this(visitRepository, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  WikiVisitService(WikiDailyVisitRepository visitRepository, Clock clock) {
    this.visitRepository = visitRepository;
    this.clock = clock;
  }

  public record DailyVisit(LocalDate date, long count) {}

  public record VisitSummary(long today, long total, List<DailyVisit> recentDays) {}

  /** /wiki 진입 비컨. 같은 세션·같은 날이면 카운트하지 않는다. */
  @Transactional
  public void recordVisit(HttpSession session) {
    LocalDate today = LocalDate.now(clock);
    String sessionKey = SESSION_KEY_PREFIX + today;
    synchronized (session) {
      if (session.getAttribute(sessionKey) == null) {
        visitRepository.increment(today);
        session.setAttribute(sessionKey, Boolean.TRUE);
      }
    }
  }

  public VisitSummary summary() {
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(RECENT_DAY_COUNT - 1L);
    Map<LocalDate, WikiDailyVisit> byDate = visitRepository.findRecent(from, today).stream()
        .collect(Collectors.toMap(WikiDailyVisit::getVisitDate, Function.identity()));
    List<DailyVisit> recentDays = IntStream.range(0, RECENT_DAY_COUNT)
        .mapToObj(offset -> from.plusDays(offset))
        .map(date -> new DailyVisit(date, byDate.containsKey(date) ? byDate.get(date).getVisitCount() : 0))
        .toList();
    long todayCount = recentDays.get(recentDays.size() - 1).count();
    return new VisitSummary(todayCount, visitRepository.sumAll(), recentDays);
  }
}
