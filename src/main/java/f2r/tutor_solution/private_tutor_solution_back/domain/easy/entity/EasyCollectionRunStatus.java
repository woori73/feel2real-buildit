package f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity;

/** 수집 실행 상태. PHASE 1 트리거는 MANUAL뿐(AUTO는 스케줄러 도입 시). */
public enum EasyCollectionRunStatus {
  RUNNING,
  SUCCESS,
  PARTIAL,
  FAILED
}
