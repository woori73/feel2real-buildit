package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemGeneratorTemplateContractTest {

  private static final Path TEMPLATE = Path.of("src/main/resources/templates/problem-generator/page.html");

  @Test
  @DisplayName("given 문제 생성기 템플릿 when 읽으면 then 7화면 목업과 BYO 키·자료 업로드 테스트 훅이 존재한다")
  void template_has_required_mock_and_upload_hooks() throws Exception {
    // given / when
    String html = Files.readString(TEMPLATE);

    // then
    assertThat(html)
        .contains("data-testid=\"btn-open-api-settings\"")
        .contains("data-testid=\"input-api-key\"")
        .contains("data-testid=\"form-material-upload\"")
        .contains("data-testid=\"btn-upload-material\"")
        .contains("data-testid=\"btn-generate-questions\"")
        .contains("data-testid=\"select-digital-twin\"")
        .contains("id=\"pgMaterialHint\"")
        .contains("data-testid=\"input-search\"")
        .contains("data-testid=\"select-type-filter\"")
        .contains("data-action=\"mock\"");
  }
}
