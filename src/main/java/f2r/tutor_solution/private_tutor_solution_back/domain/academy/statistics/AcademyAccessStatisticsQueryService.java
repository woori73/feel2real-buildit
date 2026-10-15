package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAccessStatisticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AcademyAccessStatisticsQueryService {
  private static final ZoneId STATISTICS_ZONE = ZoneId.of("Asia/Seoul");
  private final AcademyService academyService;
  private final AcademyAccessStatisticsStore store;

  public AcademyAccessStatisticsResponse getMine(
      JwtTokenProvider.AuthenticatedPrincipal principal, int days) {
    if (days < 1 || days > 90) throw new IllegalArgumentException("days는 1~90이어야 합니다.");
    Academy academy = academyService.requireMyAcademy(principal);
    LocalDate today = LocalDate.now(STATISTICS_ZONE);
    LocalDate from = today.minusDays(days - 1L);
    Map<LocalDate, Long> counts = store.daily(academy.getAcademyId(), from).stream()
        .collect(Collectors.toMap(AcademyAccessStatisticsStore.DailyRow::date,
            AcademyAccessStatisticsStore.DailyRow::requestCount));
    var daily = IntStream.range(0, days)
        .mapToObj(offset -> from.plusDays(offset))
        .map(date -> new AcademyAccessStatisticsResponse.Daily(date, counts.getOrDefault(date, 0L)))
        .toList();
    var summary = store.summary(academy.getAcademyId());
    return new AcademyAccessStatisticsResponse(
        "alb_successful_root_requests", summary.totalRequestCount(), daily, summary.lastLogAt());
  }
}
