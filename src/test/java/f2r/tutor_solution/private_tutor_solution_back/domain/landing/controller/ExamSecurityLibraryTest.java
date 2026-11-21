package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.assertj.core.api.Assertions.assertThat;

import f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.ExamSecurityLibraryController.Entry;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.ExamSecurityLibraryController.Library;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.ExamSecurityLibraryController.Section;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 부정행위 방지 기술 자료 페이지(/exam-security) 데이터 계약. 매주 갱신(docs/exam-security/LIBRARY_UPDATE.md)할 때도 이 테스트가 지켜야 할 규칙을 확인한다.
 */
class ExamSecurityLibraryTest {

  private static final Set<String> KINDS = Set.of("논문", "기술 문서", "제품", "기사", "F2R");

  @Test
  @DisplayName("given 자료 파일 when 읽으면 then 렌즈 찾기·종이 무늬·언론 보도 세 섹션과 갱신일·F2R 현황·주의가 있다")
  void library_hasSectionsAndMeta() throws Exception {
    // given / when
    Library lib = new ExamSecurityLibraryController().load();
    // then
    assertThat(lib.updatedAt()).matches("\\d{4}-\\d{2}-\\d{2}");
    assertThat(lib.sections()).extracting(Section::id).containsExactly("lens", "paper", "news");
    assertThat(lib.f2rStatus()).isNotEmpty();
    assertThat(lib.cautions()).isNotEmpty();
    assertThat(lib.sections()).allSatisfy(s -> assertThat(s.entries()).isNotEmpty());
  }

  @Test
  @DisplayName("given 모든 항목 when 확인하면 then 종류·제목·출처가 있고 주소는 https, 과장 표현과 F2R 탐지율 수치가 없다")
  void entries_followFactRules() throws Exception {
    // given
    Library lib = new ExamSecurityLibraryController().load();
    List<Entry> all = lib.sections().stream().flatMap(s -> s.entries().stream()).toList();
    // when / then
    assertThat(all).allSatisfy(e -> {
      assertThat(KINDS).as(e.title()).contains(e.kind());
      assertThat(e.title()).isNotBlank();
      assertThat(e.by()).as(e.title()).isNotBlank();
      assertThat(e.checked()).as(e.title()).isIn("원문", "검색");
      if (!"F2R".equals(e.kind())) assertThat(e.url()).as(e.title()).startsWith("https://");
      String text = String.join(" ", e.title(), String.valueOf(e.summary()), String.valueOf(e.result()));
      assertThat(text).doesNotContain("혁신", "최고", "완벽 차단", "100% 적발");
      if ("F2R".equals(e.kind())) assertThat(text).as("F2R 성능 수치 없음").doesNotContainPattern("탐지율|\\d+\\s*%");
    });
    String raw = new String(new org.springframework.core.io.ClassPathResource(ExamSecurityLibraryController.RESOURCE)
        .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(raw).doesNotContain("Claude");
  }

  @Test
  @DisplayName("given 페이지 when 요청하면 then 자료 화면을 그린다")
  void page_rendersTemplate() throws Exception {
    // given
    var model = new org.springframework.ui.ExtendedModelMap();
    // when
    String view = new ExamSecurityLibraryController().page(model);
    // then
    assertThat(view).isEqualTo("public/exam_security");
    assertThat(model.getAttribute("lib")).isInstanceOf(Library.class);
  }
}
