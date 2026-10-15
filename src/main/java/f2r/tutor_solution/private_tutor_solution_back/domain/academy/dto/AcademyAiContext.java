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
 *
 * <p>Phase 5A: 전체 페이지 진단(ACADEMY_COMPOSE)을 위해 {@link Composition}을 추가했다 —
 * 섹션 구성/순서, 콘텐츠 존재 여부, 완성도 요약처럼 "페이지를 통째로 보고 판단"하는 데 필요한
 * 메타데이터만 담고, 개인정보/키/ID는 여전히 넣지 않는다.
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
    List<LessonSummary> lessons,
    Composition composition) {

  /** 수업 1건의 AI-safe 요약. 가격/일정/정원 등은 포함하지 않는다. */
  public record LessonSummary(String name, String kicker, String learnerNeed, String highlight) {}

  /**
   * Phase 5A 전체 페이지 진단용 추가 컨텍스트. 개인정보/API key/internal ID는 포함하지 않는다.
   *
   * @param sections 현재 노출 순서대로의 섹션 상태
   * @param hasDirectorVideo 대표 원장 영상 존재 여부
   * @param lessonVideoCount 수업 영상 개수
   * @param miniLectureCount 미니 강의 개수
   * @param programCardCount 대표 수업(Program) 카드 개수
   * @param programCardWithMediaCount 이미지/영상이 붙은 대표 수업 카드 개수
   * @param ctaLabel Featured CTA 문구(없으면 빈 문자열)
   * @param editableTextTargets AI가 문구 개선을 제안할 수 있는 builderDraft key 목록(allowlist)
   * @param completionScorePercent deterministic 완성도 점수
   * @param completionMissing 아직 비어 있는 완성도 항목 라벨
   */
  public record Composition(
      List<SectionState> sections,
      boolean hasDirectorVideo,
      int lessonVideoCount,
      int miniLectureCount,
      int programCardCount,
      int programCardWithMediaCount,
      String ctaLabel,
      List<String> editableTextTargets,
      int completionScorePercent,
      List<String> completionMissing) {

    /**
     * 섹션 1개의 상태.
     *
     * @param key sectionOrder key (예: {@code sec-principal})
     * @param label 사람이 읽는 섹션 이름
     * @param visible 현재 공개 페이지에 노출되는지(opt-in 섹션은 명시적 ON일 때만 true)
     * @param editable AI가 이 섹션의 순서/문구 변경을 제안해도 되는지(미구현 섹션은 false)
     * @param hasContent 이 섹션에 실제 콘텐츠가 채워져 있는지
     */
    public record SectionState(
        String key, String label, boolean visible, boolean editable, boolean hasContent) {}
  }
}
