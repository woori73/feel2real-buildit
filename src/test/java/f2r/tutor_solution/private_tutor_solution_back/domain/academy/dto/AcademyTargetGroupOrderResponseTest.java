package f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("응답 DTO의 수업대상 순서 — 저장 순서와 무관하게 초→중→고→대→성인")
class AcademyTargetGroupOrderResponseTest {

  /** 엔티티의 targetGroups는 HashSet이라 순서가 임의 → 뒤섞인 순서를 그대로 재현한다. */
  private Academy academyWithShuffledTargets() {
    Academy academy = mock(Academy.class);
    lenient().when(academy.getAcademyId()).thenReturn(1L);
    lenient().when(academy.getSlug()).thenReturn("my-academy");
    lenient().when(academy.getHandle()).thenReturn(null);
    lenient().when(academy.getName()).thenReturn("압구정에듀홀릭");
    lenient().when(academy.getIntro()).thenReturn("소개");
    lenient().when(academy.getStatus()).thenReturn(AcademyStatus.PUBLISHED);
    lenient().when(academy.isVisible()).thenReturn(true);
    lenient().when(academy.getCoverStorageKey()).thenReturn(null);
    lenient().when(academy.getPageSettings()).thenReturn(null);
    lenient().when(academy.getTargetGroups()).thenReturn(new LinkedHashSet<>(
        List.of(TargetGroup.MIDDLE, TargetGroup.HIGH, TargetGroup.ELEMENTARY)));
    return academy;
  }

  @Test
  @DisplayName("given 중/고/초 순으로 저장된 학원 when AcademyResponse.of then 초/중/고 순")
  void academyResponse_ordersTargetGroups() {
    // given
    Academy academy = academyWithShuffledTargets();

    // when
    AcademyResponse res = AcademyResponse.of(academy, List.of(), List.of(), List.of(), null);

    // then
    assertThat(res.targetGroups()).containsExactly(
        TargetGroup.ELEMENTARY, TargetGroup.MIDDLE, TargetGroup.HIGH);
  }

  @Test
  @DisplayName("given 중/고/초 순으로 저장된 학원 when AcademyListResponse.of then 초/중/고 순")
  void academyListResponse_ordersTargetGroups() {
    // given
    Academy academy = academyWithShuffledTargets();

    // when
    AcademyListResponse res = AcademyListResponse.of(academy, List.of(), List.of());

    // then
    assertThat(res.targetGroups()).containsExactly(
        TargetGroup.ELEMENTARY, TargetGroup.MIDDLE, TargetGroup.HIGH);
  }

  @Test
  @DisplayName("given 대상 미등록 학원 when of then 빈 Set")
  void academyResponse_emptyTargetGroups() {
    // given
    Academy academy = academyWithShuffledTargets();
    lenient().when(academy.getTargetGroups()).thenReturn(Set.of());

    // when
    AcademyResponse res = AcademyResponse.of(academy, List.of(), List.of(), List.of(), null);

    // then
    assertThat(res.targetGroups()).isEmpty();
  }
}
