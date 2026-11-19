package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * AI 코디네이터 말 명령 묶음(학생·반, 수강료 …)의 공통 모양. 초안(prepare) → 사용자 확인 후 실행(execute) → 10분 안 되돌리기(undo).
 * 실행은 기존 서비스만 부르고, 되돌리기는 실행 뒤 상태가 그대로일 때만 한다.
 */
public interface CoordActions {

  /** 초안 계획. plan에는 실명이 들어가고(승인 화면용), aiTitle은 가린 이름만 쓴다. */
  record Draft(String kind, Map<String, Object> plan, String title, String aiTitle) {}

  /** requestChange의 area 값(STUDENT·CLASS·TUITION …). */
  Set<String> areas();

  /** 이 묶음이 실행·되돌리는 coord_jobs.kind 값. */
  Set<String> kinds();

  Draft prepare(String area, Long businessId, String action, JsonNode args);

  /** 실행하고 되돌리기에 필요한 값(이전 상태)을 돌려준다. 결과에 실명을 넣지 않는다. label은 완료 문구. */
  Map<String, Object> execute(String kind, JsonNode plan);

  void undo(String kind, JsonNode plan, JsonNode result);
}
