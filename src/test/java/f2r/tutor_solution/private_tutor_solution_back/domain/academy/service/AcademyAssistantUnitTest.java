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
  @DisplayName("given 샘플, when 과목·대상을 보면, then 문구가 직접 말한 것만 채워져 있다")
  void samplesFillSubjectOnlyWhenSeedSaysIt() {
    var general = AcademyAssistantSamples.find("general").orElseThrow();
    assertThat(general.subjectNameKo()).isNull();
    assertThat(AcademyAssistantSamples.find("premium").orElseThrow().targets()).isNull();
    assertThat(AcademyAssistantSamples.find("junior-english").orElseThrow().subjectNameKo()).isEqualTo("영어");
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

  @Test
  @DisplayName("given 과목이 읽히거나 안 읽히는 문장, when 임시 학원 이름을 만들면, then 과목이 있으면 '새 과목학원', 없으면 '새 학원'이다")
  void provisionalNameUsesSubjectWhenKnown() {
    // given / when / then
    assertThat(AcademyAssistantService.provisionalName("영어")).isEqualTo("새 영어학원");
    assertThat(AcademyAssistantService.provisionalName(null)).isEqualTo("새 학원");
    assertThat(AcademyAssistantService.provisionalName("  ")).isEqualTo("새 학원");
  }

  @Test
  @DisplayName("given 문장과 과목 이름 목록, when 과목을 찾으면, then 문장에 들어 있는 가장 긴 이름을 돌려주고 없으면 null이다")
  void infersLongestSubjectNameInText() {
    // given
    var names = java.util.List.of("영어", "영어회화", "수학");
    // when / then
    assertThat(AcademyAssistantService.inferSubjectName("영어학원 홈페이지를 만들어줘", names)).isEqualTo("영어");
    assertThat(AcademyAssistantService.inferSubjectName("영어회화 전문 학원", names)).isEqualTo("영어회화");
    assertThat(AcademyAssistantService.inferSubjectName("과학 학원", names)).isNull();
    assertThat(AcademyAssistantService.inferSubjectName(null, names)).isNull();
  }
}
