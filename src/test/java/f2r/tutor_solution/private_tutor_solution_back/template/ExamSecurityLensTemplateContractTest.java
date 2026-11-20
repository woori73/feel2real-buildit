package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * F2R 시험 보안 검사대 시연 화면(/easy/exam-security/lens) 계약.
 * 판정하지 않고('의심 없음/확인 필요'만), 촬영 화면을 저장·전송하지 않는다는 안내를 보여 준다.
 */
class ExamSecurityLensTemplateContractTest {

  private static String render() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    return engine.process("easy/exam_security_lens", new Context());
  }

  @Test
  @DisplayName("given 시연 화면 when 렌더하면 then 카메라·렌즈 찾기 버튼과 결과 칸, 스크립트가 있다")
  void rendersLensDemo() {
    // given & when
    String html = render();
    // then
    assertThat(html)
        .contains("<!DOCTYPE html>", "width=device-width, initial-scale=1.0", "noindex")
        .contains("id=\"lensApp\"", "id=\"lensVideo\"", "id=\"lensOverlay\"", "id=\"lensStart\"", "id=\"lensScan\"",
            "id=\"lensResult\"", "<script src=\"/js/exam_security_lens.js\"></script>")
        .doesNotContain("th:block", "th:replace");
  }

  @Test
  @DisplayName("given 시연 화면 when 렌더하면 then 판정하지 않음·저장하지 않음을 알리고 과장 문구와 업로드 양식이 없다")
  void statesNoJudgementAndNoStorage() {
    // given & when
    String html = render();
    // then
    assertThat(html)
        .contains("부정행위를 판정하지 않습니다", "저장하거나 서버로 보내지 않습니다", "탐지율을 보장하지 않습니다")
        .doesNotContain("<form", "type=\"file\"", "100% 적발", "완벽 차단");
  }
}
