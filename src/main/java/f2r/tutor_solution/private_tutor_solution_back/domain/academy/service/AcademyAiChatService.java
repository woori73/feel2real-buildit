package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ChatProposal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiChatDtos.ValidationResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;

/**
 * P1 AI 채팅 서비스 인터페이스.
 *
 * <p>OpenCode가 구현한다. Muse는 이 인터페이스를 통해 AI 로직을 호출하고,
 * API/DB/보안/권한은 Muse가 담당한다. 구현 범위가 겹치지 않도록 경계를 명확히 한다.
 *
 * <p>보안 원칙: 이 인터페이스의 구현은 DB를 직접 수정하지 않는다.
 * 제안 생성과 검증만 수행하고, 실제 적용은 Muse의 컨트롤러가 수행한다.
 */
public interface AcademyAiChatService {

  /**
   * 자연어 메시지를 분석하여 수정안을 생성한다.
   *
   * @param context 학원 컨텍스트 (현재 builderDraft 포함)
   * @param userMessage 고객의 자연어 요청
   * @return 수정안 제안 (DB 수정 없음)
   */
  ChatProposal proposeChanges(AcademyAiContext context, String userMessage);

  /**
   * 수정안을 검증한다 (allowlist, 값 형식, 금지어 등).
   *
   * @param proposal 검증할 수정안
   * @return 검증 결과
   */
  ValidationResult validateProposal(ChatProposal proposal);
}
