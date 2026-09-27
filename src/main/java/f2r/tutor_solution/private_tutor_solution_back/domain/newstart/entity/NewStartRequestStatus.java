package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity;

import java.util.Set;

/** 요청 검토 상태 흐름(설계 §5). */
public enum NewStartRequestStatus {
  /** 접수됨 — 회원이 작성한 직후 기본값. */
  RECEIVED,
  /** 검토 중 — 운영자가 확인에 들어갔다. */
  REVIEWING,
  /** 답변 완료 — 운영자 답변이 등록됐다(종료가 아닐 수 있다). */
  ANSWERED,
  /** 제안 채택 — 개선 요청이 반영 대상으로 승인됐다. */
  ACCEPTED,
  /** 작업 진행 중 — 반영 개발이 시작됐다. */
  IN_PROGRESS,
  /** 해결 완료 — 반영 종료, resolved_at과 resolved_version이 채워진다. */
  RESOLVED,
  /** 거절 — 사유를 reason으로 남긴다. */
  REJECTED,
  /** 중복 — 같은 요청이 이미 접수됐다. 사유를 reason으로 남긴다. */
  DUPLICATE;

  private static final Set<NewStartRequestStatus> NEED_REASON = Set.of(REJECTED, DUPLICATE);

  /** 거절·중복은 회원에게 설명해야 할 사유가 필수다. */
  public boolean requiresReason() {
    return NEED_REASON.contains(this);
  }
}
