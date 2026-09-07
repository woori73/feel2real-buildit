package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 내신 LIVE 공개 페이지·팝업·운영자 화면의 정적 계약.
 *
 * <p>핵심: 학부모 CTA 가 BuildIt Wiki workspace({@code /problem-generator})로 새면 안 된다(§11·§19).
 * 모바일 필수 요소(viewport·safe-area)와 서버 판정 원칙(정적 HTML 에 PRIVATE 파일 직링크 부재)을
 * 못박는다.
 */
class MidtermLiveTemplateContractTest {

  private static final Path ROOT =
      Path.of(System.getProperty("lms.sourceRoot", "src/main/resources"));

  private String read(String rel) throws Exception {
    return Files.readString(ROOT.resolve(rel));
  }

  @Test
  void publicPage_isMobileFirst_andNeverLinksToWikiWorkspace() throws Exception {
    String html = read("static/eduholic-midterm-2026.html");
    String js = read("static/js/eduholic_midterm_2026.js");
    String css = read("static/css/eduholic_midterm_2026.css");

    assertThat(html).contains("viewport-fit=cover");
    assertThat(html).contains("name=\"viewport\"");
    // 캠페인 데이터는 전용 공개 API 에서 온다(Wiki workspace 아님).
    assertThat(js).contains("/api/academies/");
    assertThat(js).contains("/midterm-campaign");

    // 어떤 파일에서도 제작/관리 workspace 로 보내지 않는다.
    assertThat(html).doesNotContain("/problem-generator");
    assertThat(js).doesNotContain("/problem-generator");
    assertThat(js).doesNotContain("/wiki/index.html");

    // 모바일 필수: safe-area + sticky CTA + reduced-motion.
    assertThat(css).contains("safe-area-inset");
    assertThat(css).contains("mt-sticky");
    assertThat(css).contains("prefers-reduced-motion");
  }

  @Test
  void popup_keepsHostGate_usesServerFlag_andHasAccessibleDismiss() throws Exception {
    String js = read("static/js/eduholic_midterm_popup.js");

    assertThat(js).contains("eduholic.feel2real.co.kr");            // 타 Academy 미노출
    assertThat(js).contains("/api/academies/eduholic/midterm-campaign/popup"); // 서버 판정
    assertThat(js).doesNotContain("/problem-generator");
    assertThat(js).contains("Escape");                              // ESC 닫기
    assertThat(js).contains("emp-lock");                            // body scroll lock
    assertThat(js).contains("safe-area-inset-bottom");
    // 과거 하드코딩 학교 목록/전단지 문구가 남아 있지 않다.
    assertThat(js).doesNotContain("모집!!!");
  }

  @Test
  void adminPage_loadsApiFirst_andSitsUnderOwnerGuardedRoute() throws Exception {
    String html = read("templates/lms/lms_midterm_live.html");

    assertThat(html).contains("~{fragments/_lms_shell :: sidebar('midterm-live')}");
    int api = html.indexOf("/js/api.js");
    int page = html.indexOf("/js/lms_midterm_live.js");
    assertThat(api).isGreaterThan(-1);
    assertThat(page).isGreaterThan(api);
  }
}
