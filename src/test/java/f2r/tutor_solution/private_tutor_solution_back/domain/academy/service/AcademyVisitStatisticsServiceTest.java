package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyVisitStatisticsResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyDailyVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyDailyVisitRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
class AcademyVisitStatisticsServiceTest {

  @Mock AcademyRepository academyRepository;
  @Mock AcademyDailyVisitRepository visitRepository;

  private AcademyVisitStatisticsService service;
  private Academy academy;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(
        Instant.parse("2026-08-03T11:00:00Z"), ZoneId.of("Asia/Seoul"));
    service = new AcademyVisitStatisticsService(academyRepository, visitRepository, clock);
    academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(17L);
  }

  @Test
  void getPublicSummary_fillsMissingDatesWithZero() {
    // given
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("alpha", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.of(academy));
    when(visitRepository.sumByAcademyId(17L)).thenReturn(41L);
    when(visitRepository.findRecent(17L, LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 3)))
        .thenReturn(List.of(
            AcademyDailyVisit.of(academy, LocalDate.of(2026, 7, 30), 3),
            AcademyDailyVisit.of(academy, LocalDate.of(2026, 8, 3), 11)));

    // when
    AcademyVisitStatisticsResponse response = service.getPublicSummary("alpha");

    // then
    assertThat(response.today()).isEqualTo(11);
    assertThat(response.total()).isEqualTo(41);
    assertThat(response.recentDays()).hasSize(7);
    assertThat(response.recentDays()).extracting(AcademyVisitStatisticsResponse.DailyVisit::count)
        .containsExactly(0L, 0L, 3L, 0L, 0L, 0L, 11L);
  }

  @Test
  void recordPublicVisit_incrementsOnlyOncePerAcademyAndDayInSession() {
    // given
    MockHttpSession session = new MockHttpSession();
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("alpha", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.of(academy));
    when(visitRepository.sumByAcademyId(17L)).thenReturn(1L);
    when(visitRepository.findRecent(17L, LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 3)))
        .thenReturn(List.of(AcademyDailyVisit.of(academy, LocalDate.of(2026, 8, 3), 1)));

    // when
    service.recordPublicVisit("alpha", session);
    service.recordPublicVisit("alpha", session);

    // then
    verify(visitRepository).increment(17L, LocalDate.of(2026, 8, 3));
  }

  @Test
  void getPublicSummary_doesNotIncrementCounter() {
    // given
    when(academyRepository.findBySlugAndStatusAndVisibleTrue("alpha", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.of(academy));
    when(visitRepository.sumByAcademyId(17L)).thenReturn(0L);
    when(visitRepository.findRecent(17L, LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 3)))
        .thenReturn(List.of());

    // when
    service.getPublicSummary("alpha");

    // then
    verify(visitRepository, never()).increment(17L, LocalDate.of(2026, 8, 3));
  }
}
