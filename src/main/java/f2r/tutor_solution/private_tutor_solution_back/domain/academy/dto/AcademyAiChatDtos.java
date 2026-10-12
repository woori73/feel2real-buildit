package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;

/**
 * P1 AI 채팅 공통 인터페이스 DTO.
 *
 * <p>Muse(백엔드/API)와 OpenCode(AI 로직)의 경계 계약이다.
 * OpenCode는 {@code AcademyAiChatService}를 구현하고, Muse는 이 DTO로 API를 제공한다.
 */
public class AcademyAiChatDtos {

  /** 단일 필드 변경. */
  public record FieldChange(String key, String before, String after) {}

  /** AI 수정안 제안. */
  public record ChatProposal(List<FieldChange> changes, String summary, String sessionId) {}

  /** 제안 검증 결과. */
  public record ValidationResult(boolean valid, List<String> errors) {}

  /** 채팅 요청. */
  public record ChatRequest(String message, String sessionId) {}

  /** 채팅 응답. */
  public record ChatResponse(
      String reply, ChatProposal proposal, String sessionId, boolean needsApproval) {}

  /** 적용 요청. */
  public record ApplyRequest(String sessionId, List<FieldChange> confirmedChanges) {}

  /** 적용 응답. */
  public record ApplyResponse(Long versionId, String appliedAt) {}

  /** 버전 정보. */
  public record VersionInfo(
      Long id, Integer versionNo, String createdBy, String createdAt, String note) {}
}
