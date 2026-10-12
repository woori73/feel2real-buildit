package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("대화 서비스 순수 로직 단위 테스트")
class AcademyAssistantUnitTest {

  @Test
  @DisplayName("given 샘플 6종, when 키와 프리셋을 보면, then 키는 유일하고 프리셋은 기존 10종 안에 있다")
  void samplesAreUniqueAndUseExistingPresets() {
    // given/when
    var samples = AcademyAssistantSamples.all();
    // then
    assertThat(samples).hasSize(6);
    assertThat(samples.stream().map(AcademyAssistantSamples.Sample::key).distinct().count()).isEqualTo(6);
    assertThat(samples).allSatisfy(s ->
        assertThat(AcademyAiCreateService.VALID_PRESETS).contains(s.preset()));
  }

  @Test
  @DisplayName("given 샘플 문구, when 허위 정보 패턴을 찾으면, then 전화번호·수강료·경력 숫자가 없다")
  void sampleSeedsContainNoInventedFacts() {
    // given/when/then
    assertThat(AcademyAssistantSamples.all()).allSatisfy(s ->
        assertThat(s.seed()).doesNotContainPattern("\\d{2,}"));
  }

  @Test
  @DisplayName("given 없는 키, when 샘플을 찾으면, then 비어 있다")
  void unknownSampleKeyIsEmpty() {
    assertThat(AcademyAssistantSamples.find("nope")).isEmpty();
    assertThat(AcademyAssistantSamples.find(null)).isEmpty();
  }

  @Test
  @DisplayName("given 한글 대상 문구, when 파싱하면, then 해당 TargetGroup만 나온다")
  void parsesKoreanTargets() {
    assertThat(AcademyAssistantService.parseTargets("초등학생 중등")).containsExactlyInAnyOrder(
        TargetGroup.ELEMENTARY, TargetGroup.MIDDLE);
    assertThat(AcademyAssistantService.parseTargets("")).isEmpty();
    assertThat(AcademyAssistantService.parseTargets(null)).isEmpty();
  }

  @Test
  @DisplayName("given 쉼표 과목 ID, when 파싱하면, then 숫자만 남고 잘못된 조각은 무시된다")
  void parsesSubjectIds() {
    assertThat(AcademyAssistantService.parseSubjectIds("1, 2,x")).isEqualTo(Set.of(1, 2));
    assertThat(AcademyAssistantService.parseSubjectIds(null)).isEmpty();
  }

  @Test
  @DisplayName("given DRAFT 학원, when publishIfDraft를 두 번 호출하면, then 첫 호출만 true이고 visible은 그대로다")
  void publishIfDraftConvertsOnlyOnce() {
    // given
    Academy academy = Academy.builder().status(AcademyStatus.DRAFT).visible(false).build();
    // when/then
    assertThat(academy.publishIfDraft()).isTrue();
    assertThat(academy.getStatus()).isEqualTo(AcademyStatus.PUBLISHED);
    assertThat(academy.isVisible()).isFalse();
    assertThat(academy.publishIfDraft()).isFalse();
  }
}
