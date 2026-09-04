package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.entity.WikiDailyVisit;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.repository.WikiDailyVisitRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("WikiVisitService — 세션/일 단위 중복 방지 + 최근 7일 요약")
class WikiVisitServiceTest {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 4);

  private WikiDailyVisitRepository repository;
  private WikiVisitService service;

  @BeforeEach
  void setUp() {
    repository = mock(WikiDailyVisitRepository.class);
    Clock clock = Clock.fixed(Instant.parse("2026-09-04T04:00:00Z"), SEOUL);  // 13:00 KST
    service = new WikiVisitService(repository, clock);
  }

  @Test
  @DisplayName("같은 세션·같은 날 반복 방문은 1회만 집계한다(새로고침 무시)")
  void recordVisit_dedupsWithinSameSessionAndDay() {
    MockHttpSession session = new MockHttpSession();

    service.recordVisit(session);
    service.recordVisit(session);
    service.recordVisit(session);

    verify(repository, times(1)).increment(TODAY);
  }

  @Test
  @DisplayName("세션이 다르면 각각 집계된다")
  void recordVisit_countsDistinctSessions() {
    service.recordVisit(new MockHttpSession());
    service.recordVisit(new MockHttpSession());

    verify(repository, times(2)).increment(TODAY);
  }

  @Test
  @DisplayName("summary: 최근 7일 채우고 오늘/누적 반환")
  void summary_fillsRecentDays() {
    WikiDailyVisit today = daily(TODAY, 12);
    WikiDailyVisit d2 = daily(TODAY.minusDays(2), 5);
    when(repository.findRecent(TODAY.minusDays(6), TODAY)).thenReturn(List.of(d2, today));
    when(repository.sumAll()).thenReturn(300L);

    WikiVisitService.VisitSummary s = service.summary();

    assertThat(s.today()).isEqualTo(12);
    assertThat(s.total()).isEqualTo(300L);
    assertThat(s.recentDays()).hasSize(7);
    assertThat(s.recentDays().get(6).date()).isEqualTo(TODAY);
    assertThat(s.recentDays().get(6).count()).isEqualTo(12);
    assertThat(s.recentDays().get(4).count()).isEqualTo(5);   // TODAY-2
    assertThat(s.recentDays().get(0).count()).isEqualTo(0);   // 데이터 없는 날
  }

  private WikiDailyVisit daily(LocalDate date, long count) {
    WikiDailyVisit v = new WikiDailyVisit();
    ReflectionTestUtils.setField(v, "visitDate", date);
    ReflectionTestUtils.setField(v, "visitCount", count);
    return v;
  }
}
