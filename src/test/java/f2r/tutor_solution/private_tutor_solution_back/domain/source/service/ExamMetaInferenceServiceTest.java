package f2r.tutor_solution.private_tutor_solution_back.domain.source.service;

import com.anthropic.client.AnthropicClient;
import f2r.tutor_solution.private_tutor_solution_back.domain.exam.entity.ExamType;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.dto.ExamAnalysisResult;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExamMetaInferenceServiceTest {

  private ExamMetaInferenceService service;

  @BeforeEach
  void setUp() {
    service = new ExamMetaInferenceService(
        mock(AnthropicClient.class),
        new ObjectMapper(),
        mock(FileStorage.class),
        "claude-sonnet-4-6");
  }

  @Test
  @DisplayName("inferMetaFromBatch — 데모(anthropic.enabled=false)면 DemoFeatureDisabledException")
  void throws_when_anthropic_disabled() {
    // given — anthropicEnabled=false로 생성 (fromEnv 미호출)
    ExamMetaInferenceService demoService =
        new ExamMetaInferenceService(false, new ObjectMapper(), mock(FileStorage.class));

    // when / then
    assertThatThrownBy(() -> demoService.inferMetaFromBatch(java.util.List.of()))
        .isInstanceOf(f2r.tutor_solution.private_tutor_solution_back.config.DemoFeatureDisabledException.class)
        .hasMessageContaining("데모 버전");
  }

  @Test
  @DisplayName("parse — 정상 JSON에서 메타를 추출한다")
  void parses_meta_from_normal_json() {
    // given
    String text = """
        {"meta": {
          "school_name": "압구정고",
          "grade": 2,
          "exam_type": "MIDTERM",
          "exam_year": 2023,
          "exam_semester": 1
        }}
        """;

    // when
    ExamAnalysisResult.ExamMeta meta = service.parse(text);

    // then
    assertThat(meta.schoolName()).isEqualTo("압구정고");
    assertThat(meta.grade()).isEqualTo((byte) 2);
    assertThat(meta.examType()).isEqualTo(ExamType.MIDTERM);
    assertThat(meta.examYear()).isEqualTo((short) 2023);
    assertThat(meta.examSemester()).isEqualTo((byte) 1);
  }

  @Test
  @DisplayName("parse — 코드펜스가 섞여 있어도 JSON을 추출한다")
  void parses_with_codefence() {
    // given
    String text = """
        ```json
        {"meta": {"school_name": "테스트중", "grade": 1, "exam_type": "FINAL", "exam_year": null, "exam_semester": null}}
        ```
        """;

    // when
    ExamAnalysisResult.ExamMeta meta = service.parse(text);

    // then
    assertThat(meta.schoolName()).isEqualTo("테스트중");
    assertThat(meta.examType()).isEqualTo(ExamType.FINAL);
    assertThat(meta.examYear()).isNull();
  }

  @Test
  @DisplayName("parse — meta 필드 자체가 비어있으면 모든 필드 null인 ExamMeta 반환")
  void returns_all_null_when_meta_missing() {
    // given
    String text = "{\"meta\": null}";

    // when
    ExamAnalysisResult.ExamMeta meta = service.parse(text);

    // then
    assertThat(meta.schoolName()).isNull();
    assertThat(meta.examType()).isNull();
  }

  @Test
  @DisplayName("parse — JSON 자체가 없으면 IllegalStateException")
  void rejects_when_no_json() {
    // when & then
    assertThatThrownBy(() -> service.parse("이건 시험지가 아닙니다"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JSON 객체를 찾지 못했습니다");
  }
}
