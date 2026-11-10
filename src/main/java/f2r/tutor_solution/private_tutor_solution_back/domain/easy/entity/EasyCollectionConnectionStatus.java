package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/** 자동수집 연결 상태. */
public enum EasyCollectionConnectionStatus {
  ACTIVE,
  DISABLED,
  NEEDS_REAUTH,
  /** 연결 해제: 조회 중단 + 인증 참조 비활성화. 장부·증빙은 보존. */
  DISCONNECTED
}
