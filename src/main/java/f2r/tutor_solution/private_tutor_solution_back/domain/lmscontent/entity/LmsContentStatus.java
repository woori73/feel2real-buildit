package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity;

/**
 * {@code UPLOADING}·{@code FAILED}은 향후 비동기 AI 가공 파이프라인(작업지시서 §9)이 쓸 자리다.
 * Phase 1은 업로드가 요청 스레드에서 동기로 끝나므로 {@code READY}에서 시작해
 * {@code PUBLISHED}/{@code HIDDEN} 사이만 오간다.
 */
public enum LmsContentStatus {
  UPLOADING,
  READY,
  PUBLISHED,
  HIDDEN,
  FAILED
}
