package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

/** Stable, non-sensitive provider failure classification for collection runs. */
public enum EasyCollectionErrorCode {
  AUTH_REQUIRED,
  PROVIDER_TEMPORARY,
  INVALID_DATA,
  CONFIGURATION_ERROR,
  EXECUTION_TIMEOUT,
  /** 기관 응답은 받았지만 중앙 장부 저장이 실패함 — 커서는 전진하지 않고 다음 실행에서 같은 구간을 다시 처리한다. */
  LEDGER_SAVE_FAILED
}
