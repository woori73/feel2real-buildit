package f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TargetGroup.canonicalOrder — 수업대상 표기 순서(초→중→고→대→성인) 정규화")
class TargetGroupOrderTest {

  @Test
  @DisplayName("given 뒤섞인 대상 when canonicalOrder then 선언 순서로 정렬된다")
  void canonicalOrder_sortsByDeclarationOrder() {
    // given — 화면에서 보였던 순서(중/고/초)처럼 뒤섞인 입력
    Set<TargetGroup> shuffled = new LinkedHashSet<>(
        List.of(TargetGroup.MIDDLE, TargetGroup.ADULT, TargetGroup.HIGH, TargetGroup.ELEMENTARY));

    // when
    Set<TargetGroup> ordered = TargetGroup.canonicalOrder(shuffled);

    // then
    assertThat(ordered).containsExactly(
        TargetGroup.ELEMENTARY, TargetGroup.MIDDLE, TargetGroup.HIGH, TargetGroup.ADULT);
  }

  @Test
  @DisplayName("given 전체 대상 when canonicalOrder then 초/중/고/대/성인 순")
  void canonicalOrder_allValues() {
    // given
    Set<TargetGroup> all = new LinkedHashSet<>(List.of(
        TargetGroup.ADULT, TargetGroup.UNIVERSITY, TargetGroup.HIGH,
        TargetGroup.MIDDLE, TargetGroup.ELEMENTARY));

    // when
    Set<TargetGroup> ordered = TargetGroup.canonicalOrder(all);

    // then
    assertThat(ordered).containsExactly(
        TargetGroup.ELEMENTARY, TargetGroup.MIDDLE, TargetGroup.HIGH,
        TargetGroup.UNIVERSITY, TargetGroup.ADULT);
  }

  @Test
  @DisplayName("given 빈 컬렉션 when canonicalOrder then 빈 Set (EnumSet.copyOf 예외 없음)")
  void canonicalOrder_empty() {
    // given
    Set<TargetGroup> empty = Set.of();

    // when
    Set<TargetGroup> ordered = TargetGroup.canonicalOrder(empty);

    // then
    assertThat(ordered).isEmpty();
  }

  @Test
  @DisplayName("given null when canonicalOrder then 빈 Set")
  void canonicalOrder_null() {
    // given
    Set<TargetGroup> values = null;

    // when
    Set<TargetGroup> ordered = TargetGroup.canonicalOrder(values);

    // then
    assertThat(ordered).isEmpty();
  }
}
