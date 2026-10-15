package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyPageSettingsUpdateRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BuildIt AI 채팅 적용 Facade (P1 · B3).
 *
 * <p>AI 채팅 수정안 적용 시 builderDraft 변경과 intro 변경을 하나의 트랜잭션으로
 * 원자적으로 처리한다. 중간 실패 시 전체가 롤백되어 부분 저장이 발생하지 않는다.
 *
 * <p>스냅샷과 작업 로그는 적용 성공 후 별도 단계로 처리한다:
 * 적용이 실패하면 스냅샷이 남지 않고, 스냅샷 실패가 적용을 되돌리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AcademyAiApplyService {

  private final AcademyService academyService;

  /**
   * builderDraft 변경과 intro 변경을 원자적으로 적용한다.
   *
   * @param principal 인증된 사용자
   * @param updatedDraft 적용 후 builderDraft 전체 (삭제는 null 값으로 표시)
   * @param introChange intro 변경값 (없으면 null)
   */
  @Transactional
  public void applyDraftChanges(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Map<String, String> updatedDraft,
      String introChange) {
    // 값 사전 검증 (길이·형식) — 실패 시 롤백되며 스냅샷도 남지 않는다
    academyService.validateAiDraft(updatedDraft);

    // 기존 저장 경로를 경유하여 적용 (우회 경로 금지)
    academyService.updatePageSettings(
        principal,
        new AcademyPageSettingsUpdateRequest(
            null, null, null, null, null, null, null, updatedDraft));

    // intro는 Academy 본문 필드로 (A2)
    if (introChange != null) {
      academyService.updateIntro(principal, introChange);
    }
  }
}
