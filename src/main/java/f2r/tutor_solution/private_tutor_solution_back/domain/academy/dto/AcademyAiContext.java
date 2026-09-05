package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import java.util.List;

/**
 * AI가 Academy 콘텐츠 개선 작업을 수행하는 데 필요한 최소 정보만 담은 normalized context.
 *
 * <p>JPA Entity({@code Academy}, {@code LessonProduct})를 AI에게 직접 넘기지 않는 이유:
 * <ul>
 *   <li>DB 컬럼이 늘어나도(가격/일정/내부 상태 등) AI 계약이 저절로 오염되지 않는다.</li>
 *   <li>{@code creationKey}/{@code authoringRevision} 같은 내부 필드, 사업자 정보(전화/이메일/
 *       사업자등록번호) 같은 민감·불필요 정보를 애초에 옮겨 담지 않는다.</li>
 *   <li>동일 Academy 상태에서는 항상 동일한 구조가 나온다(deterministic) — 필드 순서·개수가
 *       엔티티의 지연 로딩/연관관계 상태에 좌우되지 않는다.</li>
 * </ul>
 *
 * <p>포함 필드는 `docs/academy/ACADEMY_BUILDER_FIELD_INVENTORY.md`의 **A급 필드**로만 구성했다.
 * C/D/E급(가격·일정 상세, 내부 식별자, 사업자 정보 등)은 어떤 Task의 context에도 넣지 않는다.
 */
public record AcademyAiContext(
    String academyName,
    String intro,
    List<String> subjects,
    List<String> targetGroups,
    String principalName,
    String principalRole,
    String principalDesc,
    List<String> philosophyItems,
    List<LessonSummary> lessons) {

  /** 수업 1건의 AI-safe 요약. 가격/일정/정원 등은 포함하지 않는다. */
  public record LessonSummary(String name, String kicker, String learnerNeed, String highlight) {}
}
