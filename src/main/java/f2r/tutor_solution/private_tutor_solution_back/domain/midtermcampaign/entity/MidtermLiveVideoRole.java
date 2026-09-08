package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity;

/**
 * LIVE FRAME scene 순서를 위한 내부 역할. 화면에 표시하는 marketing label 이 아니다(Phase 4c §9).
 *
 * <ul>
 *   <li>{@code STUDENT_STUDY} — 학생이 문제를 풀고 필기하고 워크북/프린트를 사용하는 실제 장면.</li>
 *   <li>{@code LESSON_INTERACTION} — 박원장이 학생에게 설명하거나 풀이를 확인하거나 자료를 함께 보는 장면.</li>
 * </ul>
 *
 * 이 두 개 외 taxonomy(CLASS/TEACHER/PROMO/FACILITY/...)를 추가하지 않는다.
 */
public enum MidtermLiveVideoRole {
  STUDENT_STUDY,
  LESSON_INTERACTION;

  public static MidtermLiveVideoRole from(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("역할(STUDENT_STUDY 또는 LESSON_INTERACTION)을 선택해 주세요.");
    }
    try {
      return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("역할은 STUDENT_STUDY 또는 LESSON_INTERACTION 만 가능합니다.");
    }
  }
}
