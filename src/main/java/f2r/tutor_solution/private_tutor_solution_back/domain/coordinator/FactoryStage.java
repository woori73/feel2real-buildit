package f2r.tutor_solution.private_tutor_solution_back.domain.coordinator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 제작 공정 6단계 판정(대표 지시 2026-11-22). 새 저장 칸 없이 작업 상태({@code coord_jobs.status})와 기록({@code coord_job_events})만 읽는다.
 *
 * <pre>
 * ① 기출 분석 → ② 시험범위 자료 → ③ 변형·유사 문제 제작 → ④ 검수(다른 세션 3곳) → ⑤ 검수 후 수정 → ⑥ 게시
 * </pre>
 *
 * <ul>
 *   <li>대기(queued) = ① 앞에서 기다림. 처리 중(running)은 <b>마지막 기록</b>으로 정한다: 제작 세션의 단계 보고(actor STAGE:
 *       SCOPE·DRAFT·REVIEW)면 그 단계, 검수 FIX면 ⑤, 검수 PASS면 ④. 기록이 없으면 ①.</li>
 *   <li>검수 PASS가 필요한 수만큼 모이면 ⑥(원장 확인·저장 대기), 완료(completed)면 ⑥에서 게시 기록(actor PUBLISH)으로
 *       게시 대기 → 승인됨(탑재 대기) → 게시됨.</li>
 *   <li>취소·실패 등은 공정 밖(stage 없음).</li>
 * </ul>
 */
final class FactoryStage {

  private FactoryStage() {}

  static final Map<String, String> LABEL = labels();
  static final String STAGE_ACTOR = "STAGE";
  static final String PUBLISH_ACTOR = "PUBLISH";
  /** 제작 세션이 보고할 수 있는 단계(①은 가져간 순간 시작, ⑤·⑥은 검수·완료 기록으로 정해진다). */
  static final List<String> REPORTABLE = List.of("SCOPE", "DRAFT", "REVIEW");

  /** stage: 공정 단계 코드(공정 밖이면 null) · state: 세부 상태 코드 · label: 화면에 보일 한 줄. */
  record Result(String stage, String state, String label) {
    int no() {
      return stage == null ? 0 : List.copyOf(LABEL.keySet()).indexOf(stage) + 1;
    }
  }

  static Result of(String status, boolean reviewed, List<CoordJobEvent> history) {
    if (CoordJob.QUEUED.equals(status)) return new Result("ANALYSIS", "WAITING", "대기 중 — 아직 맡은 세션이 없습니다");
    if (CoordJob.COMPLETED.equals(status)) {
      return switch (publishState(history)) {
        case "PUBLISHED" -> new Result("PUBLISH", "PUBLISHED", "게시됨 — 학생 풀이 페이지에 올라감");
        case "APPROVED" -> new Result("PUBLISH", "APPROVED", "게시 승인됨 — 탑재 대기");
        default -> new Result("PUBLISH", "READY", "게시 대기 — 원장 승인 필요");
      };
    }
    if (!CoordJob.RUNNING.equals(status)) {
      return new Result(null, "CLOSED", CoordJob.CANCELLED.equals(status) ? "취소됨" : CoordJob.FAILED.equals(status) ? "실패" : "종료");
    }
    int passed = CoordPcTaskService.passedReviewers(history).size();
    if (reviewed && passed >= CoordPcTaskService.REQUIRED_REVIEWS) {
      return new Result("PUBLISH", "OWNER_CONFIRM", "검수 통과 — 원장 확인·저장 대기");
    }
    String stage = "ANALYSIS";
    for (CoordJobEvent e : history) {
      String head = head(e);
      if (STAGE_ACTOR.equals(e.getActor()) && REPORTABLE.contains(head)) stage = head;
      else if (CoordPcTaskService.REVIEW_ACTOR.equals(e.getActor())) stage = "FIX".equals(head) ? "REVISE" : "PASS".equals(head) ? "REVIEW" : stage;
    }
    return new Result(stage, "WORKING", switch (stage) {
      case "SCOPE" -> "시험범위 자료 확인 중";
      case "DRAFT" -> "문항 제작 중";
      case "REVIEW" -> reviewed ? "검수 " + passed + "/" + CoordPcTaskService.REQUIRED_REVIEWS : "제작 마무리";
      case "REVISE" -> "검수 의견 반영 중";
      default -> "기출 분석 중";
    });
  }

  /** 마지막 게시 기록: APPROVED·PUBLISHED, 없거나 내렸으면 READY. */
  static String publishState(List<CoordJobEvent> history) {
    String state = "READY";
    for (CoordJobEvent e : history) {
      if (!PUBLISH_ACTOR.equals(e.getActor())) continue;
      String head = head(e);
      state = "APPROVED".equals(head) || "PUBLISHED".equals(head) ? head : "READY";
    }
    return state;
  }

  /** 기록 글 「머리 · 나머지」의 머리. */
  static String head(CoordJobEvent e) {
    return e.getMessage() == null ? "" : e.getMessage().split(" · ", 2)[0];
  }

  /** 기록 글 「머리 · 나머지」의 나머지(없으면 빈 글). */
  static String tail(CoordJobEvent e) {
    String[] parts = e.getMessage() == null ? new String[0] : e.getMessage().split(" · ", 2);
    return parts.length < 2 ? "" : parts[1];
  }

  private static Map<String, String> labels() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("ANALYSIS", "기출 분석");
    m.put("SCOPE", "시험범위 자료");
    m.put("DRAFT", "변형·유사 문제 제작");
    m.put("REVIEW", "검수");
    m.put("REVISE", "검수 후 수정");
    m.put("PUBLISH", "게시");
    return java.util.Collections.unmodifiableMap(m);
  }
}
