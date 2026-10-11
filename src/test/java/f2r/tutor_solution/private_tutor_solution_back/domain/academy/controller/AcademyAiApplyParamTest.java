package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A1 생성 승인 파라미터 매핑 테스트.
 *
 * <p>transport 키 분리, 한글 대상 변환, subjectIds 파싱을 검증한다.
 */
@DisplayName("AcademyAiChatController — 생성 승인 파라미터 매핑 (A1)")
class AcademyAiApplyParamTest {

  @Test
  @DisplayName("given 중학생 when 변환 then MIDDLE")
  void parseTargetGroupsKo_middle() {
    assertThat(AcademyAiChatController.parseTargetGroupsKo("중학생"))
        .containsExactly(TargetGroup.MIDDLE);
  }

  @Test
  @DisplayName("given 복수 대상 when 변환 then 모두 포함")
  void parseTargetGroupsKo_multiple() {
    Set<TargetGroup> result =
        AcademyAiChatController.parseTargetGroupsKo("초등학생, 고등학생");
    assertThat(result).containsExactlyInAnyOrder(
        TargetGroup.ELEMENTARY, TargetGroup.HIGH);
  }

  @Test
  @DisplayName("given null/빈값 when 변환 then 빈 집합")
  void parseTargetGroupsKo_empty() {
    assertThat(AcademyAiChatController.parseTargetGroupsKo(null)).isEmpty();
    assertThat(AcademyAiChatController.parseTargetGroupsKo("")).isEmpty();
    assertThat(AcademyAiChatController.parseTargetGroupsKo("유치원")).isEmpty();
  }

  @Test
  @DisplayName("given 1,2 when 파싱 then {1, 2}")
  void parseSubjectIds_normal() {
    assertThat(AcademyAiChatController.parseSubjectIds("1,2"))
        .containsExactlyInAnyOrder(1, 2);
  }

  @Test
  @DisplayName("given 잘못된 값 when 파싱 then 빈 집합")
  void parseSubjectIds_invalid() {
    assertThat(AcademyAiChatController.parseSubjectIds(null)).isEmpty();
    assertThat(AcademyAiChatController.parseSubjectIds("abc")).isEmpty();
  }

  @Test
  @DisplayName("TRANSPORT_KEYS는 5개 생성 전용 키를 포함한다")
  void transportKeys_defined() {
    assertThat(AcademyAiChatController.TRANSPORT_KEYS)
        .containsExactlyInAnyOrder(
            "academyName", "targetGroups", "ctaEnabled", "subjectIds", "regionLocation");
  }
}
