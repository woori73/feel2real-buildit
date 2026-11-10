package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/**
 * raw 레코드 보존 상태.
 * STORED → (import handoff 성공) IMPORT_LINKED.
 * LEDGER_DUPLICATE_EXCLUDED = 기존 장부 fingerprint와 충돌하여 import에 넘기지 않음.
 * ERROR = 파싱/정규화 실행 자체 보존(원본은 남긴다).
 * LEDGER_SAVED = import 행을 거쳐 중앙 장부(easy_transactions)에 저장 완료.
 */
public enum EasyCollectionRawStatus {
  STORED,
  IMPORT_LINKED,
  LEDGER_DUPLICATE_EXCLUDED,
  ERROR,
  LEDGER_SAVED
}
