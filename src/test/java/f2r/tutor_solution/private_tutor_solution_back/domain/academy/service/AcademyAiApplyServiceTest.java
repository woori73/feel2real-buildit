package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AcademyAiApplyService 테스트 (B3).
 *
 * <p>builderDraft 변경과 intro 변경이 원자적으로 처리되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AcademyAiApplyService — 원자적 적용 (B3)")
class AcademyAiApplyServiceTest {

  @Mock
  private AcademyService academyService;

  private AcademyAiApplyService service;
  private JwtTokenProvider.AuthenticatedPrincipal principal;

  @BeforeEach
  void setUp() {
    service = new AcademyAiApplyService(academyService);
    principal = mock(JwtTokenProvider.AuthenticatedPrincipal.class);
  }

  @Test
  @DisplayName("given draft+intro when 적용 then updatePageSettings와 updateIntro가 모두 호출된다")
  void applyDraftChanges_withIntro_callsBoth() {
    Map<String, String> draft = Map.of("monthlyTuition", "15만원");

    service.applyDraftChanges(principal, draft, "새 소개문");

    verify(academyService).validateAiDraft(draft);
    verify(academyService).updatePageSettings(
        eq(principal), any(AcademyPageSettingsUpdateRequest.class));
    verify(academyService).updateIntro(principal, "새 소개문");
  }

  @Test
  @DisplayName("given intro 없음 when 적용 then updateIntro가 호출되지 않는다")
  void applyDraftChanges_withoutIntro_skipsIntroUpdate() {
    Map<String, String> draft = Map.of("monthlyTuition", "15만원");

    service.applyDraftChanges(principal, draft, null);

    verify(academyService).updatePageSettings(
        eq(principal), any(AcademyPageSettingsUpdateRequest.class));
    verify(academyService, never()).updateIntro(any(), any());
  }
}
