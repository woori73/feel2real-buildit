package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVisitStatisticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVisitStatisticsResponse.DailyVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyDailyVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyDailyVisitRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AcademyVisitStatisticsService {

  private static final int RECENT_DAY_COUNT = 7;
  private static final String SESSION_KEY_PREFIX = "academy-visit:";

  private final AcademyRepository academyRepository;
  private final AcademyDailyVisitRepository visitRepository;
  private final Clock clock;

  @Autowired
  public AcademyVisitStatisticsService(
      AcademyRepository academyRepository,
      AcademyDailyVisitRepository visitRepository) {
    this(academyRepository, visitRepository, Clock.system(ZoneId.of("Asia/Seoul")));
  }

  AcademyVisitStatisticsService(
      AcademyRepository academyRepository,
      AcademyDailyVisitRepository visitRepository,
      Clock clock) {
    this.academyRepository = academyRepository;
    this.visitRepository = visitRepository;
    this.clock = clock;
  }

  public AcademyVisitStatisticsResponse getPublicSummary(String slug) {
    return summarize(findPublishedAcademy(slug));
  }

  @Transactional
  public AcademyVisitStatisticsResponse recordPublicVisit(String slug, HttpSession session) {
    Academy academy = findPublishedAcademy(slug);
    LocalDate today = LocalDate.now(clock);
    String sessionKey = SESSION_KEY_PREFIX + academy.getAcademyId() + ":" + today;

    synchronized (session) {
      if (session.getAttribute(sessionKey) == null) {
        visitRepository.increment(academy.getAcademyId(), today);
        session.setAttribute(sessionKey, Boolean.TRUE);
      }
    }
    return summarize(academy);
  }

  public AcademyVisitStatisticsResponse getSummary(Academy academy) {
    return summarize(academy);
  }

  private Academy findPublishedAcademy(String slug) {
    return academyRepository.findBySlugAndStatusAndVisibleTrue(slug, AcademyStatus.PUBLISHED)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "공개 Academy를 찾을 수 없습니다."));
  }

  private AcademyVisitStatisticsResponse summarize(Academy academy) {
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(RECENT_DAY_COUNT - 1L);
    Map<LocalDate, AcademyDailyVisit> byDate = visitRepository
        .findRecent(academy.getAcademyId(), from, today)
        .stream()
        .collect(Collectors.toMap(AcademyDailyVisit::getVisitDate, Function.identity()));
    List<DailyVisit> recentDays = IntStream.range(0, RECENT_DAY_COUNT)
        .mapToObj(offset -> from.plusDays(offset))
        .map(date -> new DailyVisit(date, byDate.containsKey(date) ? byDate.get(date).getVisitCount() : 0))
        .toList();
    long todayCount = recentDays.get(recentDays.size() - 1).count();
    return new AcademyVisitStatisticsResponse(
        todayCount,
        visitRepository.sumByAcademyId(academy.getAcademyId()),
        recentDays);
  }
}
