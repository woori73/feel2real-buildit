package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;
import java.util.Map;

/**
 * P1 AI 홈페이지 최초 생성 DTO.
 *
 * <p>자연어 요청 → 홈페이지 초안 생성.
 * 사용자 승인 전에는 운영 홈페이지에 반영하지 않는다.
 */
public class AcademyAiCreateDtos {

  /** 생성 요청. */
  public record CreateRequest(
      String message,
      String academyName,
      String templateId) {}

  /** 생성된 초안. */
  public record CreateDraft(
      String designPreset,
      Map<String, String> builderDraft,
      String summary,
      List<String> warnings) {}

  /** 생성 응답. */
  public record CreateResponse(
      String reply,
      CreateDraft draft,
      String sessionId,
      boolean needsApproval) {}
}
