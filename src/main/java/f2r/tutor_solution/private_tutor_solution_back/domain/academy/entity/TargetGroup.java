package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** 수업 대상. 선언 순서가 화면 표기 순서(초 → 중 → 고 → 대 → 성인)의 단일 출처다. */
public enum TargetGroup {
  ELEMENTARY,
  MIDDLE,
  HIGH,
  UNIVERSITY,
  ADULT;

  /**
   * 선언 순서(초 → 중 → 고 → 대 → 성인)로 정렬된 Set을 만든다.
   *
   * <p>엔티티의 targetGroups는 {@code HashSet}이라 순서가 임의다. 응답 DTO에서 한 번 정규화해서
   * 배지 표기 순서가 화면마다 달라지는 것을 막는다({@code EnumSet}은 ordinal 순으로 순회한다).
   */
  public static Set<TargetGroup> canonicalOrder(Collection<TargetGroup> values) {
    if (values == null || values.isEmpty()) return Set.of();
    EnumSet<TargetGroup> ordered = EnumSet.noneOf(TargetGroup.class);
    ordered.addAll(values);
    return ordered;
  }
}
