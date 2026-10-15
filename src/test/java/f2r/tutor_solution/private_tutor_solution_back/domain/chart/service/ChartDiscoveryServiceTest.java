package f2r.tutor_solution.private_tutor_solution_back.domain.chart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartCandidateResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartCandidate;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoverySettings;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartDiscoveryZone;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartEducator;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.entity.ChartVerificationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartCandidateActivityRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartCandidateRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartDiscoverySettingsRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.repository.ChartEducatorRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChartDiscoveryServiceTest {

  @Test
  void defaultSettings_areRestrictedToApgujeongSinsaAndDeepSearchEnabled_givenNewSingleton() {
    // given / when
    ChartDiscoverySettings settings = ChartDiscoverySettings.defaults();

    // then
    assertThat(settings.getSearchZone()).isEqualTo(ChartDiscoveryZone.APGUJEONG_SINSA.getId());
    assertThat(settings.getCandidateLimit()).isEqualTo(10);
    assertThat(settings.isDeepSearchEnabled()).isTrue();
    assertThat(settings.getExcludedTargets()).contains("대형학원", "인강 중심 강사");
  }

  @Test
  void approve_createsEducatorOnlyAfterAdminAction_givenDiscoveredCandidate() {
    // given
    ChartDiscoverySettingsRepository settingsRepository = mock(ChartDiscoverySettingsRepository.class);
    ChartCandidateRepository candidateRepository = mock(ChartCandidateRepository.class);
    ChartCandidateActivityRepository activityRepository = mock(ChartCandidateActivityRepository.class);
    ChartEducatorRepository educatorRepository = mock(ChartEducatorRepository.class);
    ChartDiscoveryService service = new ChartDiscoveryService(
        settingsRepository,
        candidateRepository,
        activityRepository,
        educatorRepository,
        new ChartPromptFactory(),
        mock(ChartGeminiClient.class));
    ChartCandidate candidate = ChartCandidate.discovered(
        "apgujeong-sinsa", "홍길동|압구정", "홍길동", "원장", "길동영어", "압구정",
        "교육칼럼", "내신 분석 칼럼", "https://example.com/a", null, "콘텐츠 작성자", "{}",
        "실명 / 역할 / 학원명 / 지역 / Google 근거 링크가 확인됨", ChartVerificationStatus.VERIFIED,
        "prompt", LocalDateTime.now());
    when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
    when(educatorRepository.findByPersonKey("홍길동|압구정")).thenReturn(Optional.empty());
    when(activityRepository.findByCandidate_CandidateIdOrderByActivityIdAsc(candidate.getCandidateId())).thenReturn(List.of());

    // when
    ChartCandidateResponse response = service.approve(1L);

    // then
    assertThat(response.status().name()).isEqualTo("APPROVED");
    verify(educatorRepository).save(any(ChartEducator.class));
  }

  @Test
  void reject_doesNotCreateEducator_givenAdminExcludesCandidate() {
    // given
    ChartCandidateRepository candidateRepository = mock(ChartCandidateRepository.class);
    ChartCandidateActivityRepository activityRepository = mock(ChartCandidateActivityRepository.class);
    ChartEducatorRepository educatorRepository = mock(ChartEducatorRepository.class);
    ChartDiscoveryService service = new ChartDiscoveryService(
        mock(ChartDiscoverySettingsRepository.class),
        candidateRepository,
        activityRepository,
        educatorRepository,
        new ChartPromptFactory(),
        mock(ChartGeminiClient.class));
    ChartCandidate candidate = ChartCandidate.discovered(
        "apgujeong-sinsa", "홍길동|압구정", "홍길동", "원장", "길동영어", "압구정",
        "교육칼럼", "내신 분석 칼럼", "https://example.com/a", null, "콘텐츠 작성자", "{}",
        "관리자 확인 필요", ChartVerificationStatus.REVIEW_REQUIRED, "prompt", LocalDateTime.now());
    when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
    when(activityRepository.findByCandidate_CandidateIdOrderByActivityIdAsc(candidate.getCandidateId())).thenReturn(List.of());

    // when
    ChartCandidateResponse response = service.reject(1L);

    // then
    assertThat(response.status().name()).isEqualTo("REJECTED");
    verify(educatorRepository, never()).save(any());
  }
}
