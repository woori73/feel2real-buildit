package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class LmsManageTemplateContractTest {

  private static final Path ROOT = Path.of(System.getProperty("lms.sourceRoot", "src/main/resources"));

  @Test
  void manageTableIsReadOnlyWithExactColumnsAndEditorRegistrationLink() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(ROOT.resolve("templates/lms/lms_manage.html")));

    // when
    List<String> headings = document.select(".manage-table thead th").eachText();

    // then
    assertThat(headings).containsExactly("학교", "학년", "강좌명", "가격", "소개", "관리");
    assertThat(document.select("[contenteditable], button.edit")).isEmpty();
    assertThat(document.select("a[href=/tutor/academy/lessons/editor]")).hasSize(1);
  }

  @Test
  void apiLoadsBeforeDataAndManageScripts() throws Exception {
    // given
    Document document = Jsoup.parse(Files.readString(ROOT.resolve("templates/lms/lms_manage.html")));

    // when
    List<String> scripts = document.select("script[src]").eachAttr("src");

    // then
    assertThat(scripts.indexOf("/js/api.js")).isLessThan(scripts.indexOf("/js/lms_data.js"));
    assertThat(scripts.indexOf("/js/lms_data.js")).isLessThan(scripts.indexOf("/js/lms_manage.js"));
  }
}
