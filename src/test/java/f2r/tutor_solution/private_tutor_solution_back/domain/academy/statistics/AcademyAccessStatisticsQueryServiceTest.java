package f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AcademyAccessStatisticsQueryServiceTest {

  @Test
  void givenSparseDailyRows_whenOwnerQueries_thenTotalAndZeroFilledDailySeriesAreReturned() {
    // given
    AcademyService academyService = mock(AcademyService.class);
    AcademyAccessStatisticsStore store = mock(AcademyAccessStatisticsStore.class);
    Academy academy = Academy.builder().handle("math-one").name("Math").slug("math")
        .logoStorageKey("logo").intro("intro").build();
    ReflectionTestUtils.setField(academy, "academyId", 7L);
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(1L, "tutor", "TUTOR");
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    when(academyService.requireMyAcademy(principal)).thenReturn(academy);
    when(store.summary(7L)).thenReturn(new AcademyAccessStatisticsStore.SummaryRow(
        12, Instant.parse("2026-08-03T01:00:00Z")));
    when(store.daily(7L, today.minusDays(2))).thenReturn(List.of(
        new AcademyAccessStatisticsStore.DailyRow(today.minusDays(1), 4)));
    AcademyAccessStatisticsQueryService service =
        new AcademyAccessStatisticsQueryService(academyService, store);

    // when
    var response = service.getMine(principal, 3);

    // then
    assertThat(response.total()).isEqualTo(12);
    assertThat(response.daily()).extracting(day -> day.count()).containsExactly(0L, 4L, 0L);
    assertThat(response.metric()).isEqualTo("alb_successful_root_requests");
  }
}
