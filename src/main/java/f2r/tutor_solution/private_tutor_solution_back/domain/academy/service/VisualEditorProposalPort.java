package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import java.util.List;

/**
 * 시각 편집기 저장(PUT draft)과 AI Proposal 적용을 한 트랜잭션으로 묶기 위한 계약.
 *
 * <p>이 인터페이스는 편집기(저장 API) 쪽이 소유하고, 구현체는 Proposal 도메인(뮤즈)이 제공한다.
 * 구현체가 없는 환경에서는 {@code appliedProposalId}가 있는 저장 요청이 400
 * {@code PROPOSAL_NOT_AVAILABLE}로 거부되고, 없는 요청은 기존 동작과 완전히 같다.
 *
 * <p>설계: docs/BUILDIT_AI_AGENT_PROPOSAL_FINAL_DESIGN.md §1.2, §2.2
 */
public interface VisualEditorProposalPort {

  /**
   * 저장 트랜잭션 안에서 Proposal 적용을 검증하고 APPLIED로 전이한다.
   *
   * <p>구현체 규칙:
   * <ul>
   *   <li>{@code @Transactional(propagation = MANDATORY)} — 저장 트랜잭션 밖에서는 호출되면 안 된다.</li>
   *   <li>제안 조회는 반드시 {@code academyId}를 조건에 포함한다({@code FOR UPDATE}).
   *       없거나 다른 학원 것이면 {@link ProposalException#NOT_FOUND}.</li>
   *   <li>PENDING이 아니면 {@link ProposalException#NOT_PENDING}.</li>
   *   <li>검증 실패(대상 없음·값 불일치·비교 대상 누락 포함)는 반드시
   *       {@link ProposalException#VERIFICATION_FAILED}. 성공을 기본값으로 반환하지 않는다.</li>
   *   <li>{@code appliedRevision}은 {@code savedRevision}(DB에서 읽은 값)만 기록한다.</li>
   * </ul>
   * 모든 실패는 런타임 예외이므로 저장(draft 갱신)도 함께 롤백된다.
   *
   * @param academyId 서버가 확정한 학원(요청값 아님)
   * @param userId 저장하는 사용자
   * @param proposalId 요청의 {@code appliedProposalId}
   * @param savedRevision flush 후 draft 엔티티의 revision
   * @param sanitizedProjectData 정제 후 저장되는 projectData(JSON 문자열)
   * @return 반드시 status가 APPLIED인 결과
   */
  ProposalApplyResult applyInSaveTransaction(
      long academyId,
      long userId,
      long proposalId,
      long savedRevision,
      String sanitizedProjectData);

  /**
   * 적용 결과. 저장 응답의 {@code appliedProposal}로 그대로 노출된다.
   *
   * @param proposalId 제안 ID
   * @param status 항상 "APPLIED"
   * @param appliedRevision 기록된 revision
   */
  record ProposalApplyResult(long proposalId, String status, long appliedRevision) {
  }

  /**
   * 제안 적용 실패. 런타임 예외이므로 저장 트랜잭션이 롤백된다.
   */
  class ProposalException extends RuntimeException {

    public static final String NOT_FOUND = "PROPOSAL_NOT_FOUND";
    public static final String NOT_PENDING = "PROPOSAL_NOT_PENDING";
    public static final String VERIFICATION_FAILED = "PROPOSAL_VERIFICATION_FAILED";
    public static final String NOT_AVAILABLE = "PROPOSAL_NOT_AVAILABLE";

    private final String code;
    private final int httpStatus;
    private final List<String> details;

    public ProposalException(String code, int httpStatus, String message, List<String> details) {
      super(message);
      this.code = code;
      this.httpStatus = httpStatus;
      this.details = details == null ? List.of() : List.copyOf(details);
    }

    public static ProposalException notFound() {
      return new ProposalException(NOT_FOUND, 404, "제안을 찾을 수 없습니다.", null);
    }

    public static ProposalException notPending() {
      return new ProposalException(NOT_PENDING, 409, "이미 처리되었거나 만료된 제안입니다.", null);
    }

    public static ProposalException verificationFailed(List<String> details) {
      return new ProposalException(VERIFICATION_FAILED, 422,
          "제안 내용이 저장된 초안과 일치하지 않습니다.", details);
    }

    public static ProposalException notAvailable() {
      return new ProposalException(NOT_AVAILABLE, 400, "AI 제안 적용을 사용할 수 없습니다.", null);
    }

    public String getCode() {
      return code;
    }

    public int getHttpStatus() {
      return httpStatus;
    }

    public List<String> getDetails() {
      return details;
    }
  }
}
