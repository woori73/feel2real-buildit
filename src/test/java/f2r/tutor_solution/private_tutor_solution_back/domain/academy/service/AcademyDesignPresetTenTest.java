package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyRequirementAnalyzer.AcademyRequirements;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 디자인 프리셋 10종(PR #609) 통합 준비 — 분석기 분리·allowlist 일관성. */
@DisplayName("디자인 프리셋 10종 — 분석기·allowlist")
class AcademyDesignPresetTenTest {

  private AcademyRequirementAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    analyzer = new AcademyRequirementAnalyzer();
  }

  @ParameterizedTest(name = "given \"{0}\" when 분석 then {1}")
  @CsvSource({
      "미니멀하게 만들어줘,MINIMAL",
      "다크모드 느낌으로 만들어줘,DARK",
      "따뜻한 느낌으로 만들어줘,WARM",
      "사진 중심 갤러리처럼 만들어줘,PHOTO",
      "역동적이고 화려하게 만들어줘,DYNAMIC"
  })
  void analyze_styleKeyword_picksNewPreset(String message, String expected) {
    AcademyRequirements r = analyzer.analyze(message);
    assertThat(r.preset()).isEqualTo(expected);
  }

  @Test
  @DisplayName("given 스타일 단어가 앞선 업종+스타일 요청 when 분석 then 스타일이 프리셋을 정하고 과목은 오염되지 않는다")
  void analyze_styleAndSubject_styleWinsSubjectKept() {
    // given: 스타일 단어가 과목보다 먼저 나온다
    String message = "따뜻한 느낌의 영어회화 학원 홈페이지 만들어줘";

    // when
    AcademyRequirements r = analyzer.analyze(message);

    // then: 프리셋은 WARM, 과목은 영어(회화)로 남는다("따뜻한"이 과목이 되면 안 된다)
    assertThat(r.preset()).isEqualTo("WARM");
    assertThat(r.subject()).isNotEqualTo("따뜻한").contains("영어");
  }

  @Test
  @DisplayName("given 과목이 앞서고 일반 톤 표현이 뒤따르는 요청 when 분석 then 과목 프리셋 유지")
  void analyze_subjectFirstThenGenericTone_subjectPresetKept() {
    AcademyRequirements r = analyzer.analyze("영어학원 홈페이지 만들어줘. 깔끔하고 신뢰감 있게 구성해 줘.");
    assertThat(r.preset()).isEqualTo("ENGLISH");
    assertThat(r.subject()).isEqualTo("영어");
  }

  @Test
  @DisplayName("given 스타일 단어 없는 요청 when 분석 then 기존 5종 동작은 그대로")
  void analyze_noStyleKeyword_legacyBehaviourUnchanged() {
    assertThat(analyzer.analyze("영어학원 홈페이지 만들어줘").preset()).isEqualTo("ENGLISH");
    assertThat(analyzer.analyze("수학 입시학원 홈페이지 만들어줘").preset()).isEqualTo("EXAM");
    assertThat(analyzer.analyze("홈페이지 만들어줘").preset()).isEqualTo("EXAM");
  }

  @Test
  @DisplayName("given 허용 목록 when 조회 then 기존 5종 + 신규 5종 = 10종")
  void validPresets_hasTenPresets() {
    assertThat(AcademyAiCreateService.VALID_PRESETS).containsExactlyInAnyOrderElementsOf(List.of(
        "EXAM", "ENGLISH", "ARTS", "STUDY", "ONLINE",
        "MINIMAL", "DARK", "WARM", "PHOTO", "DYNAMIC"));
  }
}
