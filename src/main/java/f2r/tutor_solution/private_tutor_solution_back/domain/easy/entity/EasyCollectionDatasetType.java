package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * Provider-neutral dataset/event classification. These names do not assert the accounting
 * meaning of any CREFIA dataset; that requires the official provider contract.
 */
public enum EasyCollectionDatasetType {
  APPROVAL,
  PURCHASE,
  DEPOSIT,
  CANCELLATION,
  CORRECTION,
  /** 은행 입출금 — 커서 값은 중앙 장부 저장이 끝난 마지막 조회일(yyyy-MM-dd). */
  BANK_TRANSACTION,
  UNKNOWN
}
