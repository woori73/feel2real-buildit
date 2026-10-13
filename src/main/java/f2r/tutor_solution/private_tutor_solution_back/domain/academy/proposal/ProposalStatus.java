package f2r.tutor_solution.private_tutor_solution_back.domain.academy.proposal;

/** AI 제안 상태. APPLYING은 서버 상태가 아니다(저장과 APPLIED 전이가 한 트랜잭션). */
public enum ProposalStatus {
  PENDING,
  APPLIED,
  REJECTED,
  EXPIRED,
  STALE
}
