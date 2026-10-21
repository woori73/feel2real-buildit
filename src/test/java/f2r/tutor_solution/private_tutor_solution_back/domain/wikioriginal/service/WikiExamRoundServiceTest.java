package f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.dto.WikiExamRoundResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.entity.WikiExamRound;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikioriginal.repository.WikiExamRoundRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("WikiExamRoundService — 회차 생성·중복 방지·메인 지정 단일성")
class WikiExamRoundServiceTest {

  private WikiExamRoundRepository repository;
  private WikiExamRoundService service;

  @BeforeEach
  void setUp() {
    repository = mock(WikiExamRoundRepository.class);
    service = new WikiExamRoundService(repository);
  }

  @Test
  @DisplayName("given 신규 회차 when 생성 then 저장되고 응답에 회차 정보가 담긴다")
  void create_ok() {
    when(repository.findByExamYearAndExamMonthAndDeletedFalse(2026, 11)).thenReturn(Optional.empty());
    when(repository.save(any(WikiExamRound.class))).thenAnswer(inv -> {
      WikiExamRound saved = inv.getArgument(0);
      ReflectionTestUtils.setField(saved, "wikiExamRoundId", 3L);
      return saved;
    });

    WikiExamRoundResponse res = service.create(2026, 11, "2026년 11월 모의고사", null, null, true, false);

    assertThat(res.examYear()).isEqualTo(2026);
    assertThat(res.examMonth()).isEqualTo(11);
    assertThat(res.title()).isEqualTo("2026년 11월 모의고사");
    assertThat(res.isPublished()).isTrue();
    assertThat(res.isCurrent()).isFalse();
  }

  @Test
  @DisplayName("given 같은 연도·월 회차가 이미 있으면 생성은 409")
  void create_duplicate_conflict() {
    when(repository.findByExamYearAndExamMonthAndDeletedFalse(2026, 10))
        .thenReturn(Optional.of(round(1L, 2026, 10, false)));

    assertThatThrownBy(() -> service.create(2026, 10, "중복", null, null, true, false))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("이미 존재하는 회차");
  }

  @Test
  @DisplayName("given 메인 지정 when 다른 회차가 메인이면 해제되고 대상만 메인이 된다")
  void setCurrent_keepsSingleCurrent() {
    WikiExamRound september = round(1L, 2026, 9, false);
    WikiExamRound october = round(2L, 2026, 10, true);
    when(repository.findAdminById(1L)).thenReturn(Optional.of(september));
    when(repository.findAllCurrent()).thenReturn(List.of(october));

    WikiExamRoundResponse res = service.setCurrent(1L);

    assertThat(res.isCurrent()).isTrue();
    assertThat(september.isCurrent()).isTrue();
    assertThat(october.isCurrent()).isFalse();
  }

  @Test
  @DisplayName("given 메인 지정이 없으면 현재 회차는 최신 공개 회차로 폴백한다")
  void currentPublic_fallsBackToLatestPublished() {
    WikiExamRound september = round(1L, 2026, 9, false);
    WikiExamRound october = round(2L, 2026, 10, false);
    when(repository.findCurrentPublic()).thenReturn(Optional.empty());
    when(repository.findPublicRounds()).thenReturn(List.of(october, september));

    WikiExamRoundResponse res = service.currentPublic();

    assertThat(res.examMonth()).isEqualTo(10);
  }

  @Test
  @DisplayName("given 잘못된 월(0) when 생성 then 400")
  void create_rejectsBadMonth() {
    assertThatThrownBy(() -> service.create(2026, 0, "t", null, null, true, false))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("월");
  }

  private WikiExamRound round(Long id, int year, int month, boolean current) {
    WikiExamRound r = WikiExamRound.builder()
        .examYear(year)
        .examMonth(month)
        .title(year + "년 " + month + "월 모의고사")
        .current(current)
        .published(true)
        .build();
    ReflectionTestUtils.setField(r, "wikiExamRoundId", id);
    return r;
  }
}
