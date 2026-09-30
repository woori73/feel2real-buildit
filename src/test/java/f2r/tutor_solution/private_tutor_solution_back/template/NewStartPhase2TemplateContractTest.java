package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Phase 2 §2·§3 화면 계약 — regex가 아니라 실제 Thymeleaf 렌더 결과로 검증한다.
 *
 * <p>이 테스트가 잡는 실패는 두 종류다. (1) HOME 카드·A to Z CTA 편집으로 생긴 parse 오류
 * (챗 화면에서 style 속성이 깨져 Thymeleaf가 죽었던 전례), (2) JS contract 테스트가 정규식으로만
 * 보는 링크가 실제로는 렌더 결과에 사라지는 경우. §2의 5개 진입점과 §3에서 실사이트로 확인한
 * 공식 경로가 렌더된 HTML에 남아 있어야 한다.
 */
@DisplayName("NEWSTART Phase 2 화면 렌더 계약")
class NewStartPhase2TemplateContractTest {

  private static String render(String view) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());

    Context context = new Context();
    context.setVariable("latestUpdates", List.of());
    return engine.process(view, context);
  }

  @Test
  @DisplayName("HOME은 §2의 5개 진입점만 practical grid에 두고 중복 도구 카드는 사라진다")
  void homeRendersFiveEntries() {
    String html = render("newstart/index");

    assertThat(html).contains("href=\"/newstart/fss-helper\"");
    assertThat(html).contains("href=\"/newstart/a-to-z\"");
    assertThat(html).contains("href=\"#ns-ai\"");
    assertThat(html).contains("href=\"/newstart/questions\"");
    assertThat(html).contains("href=\"/newstart/galmuri\"");
    // 제거된 HOME 중복 카드(Credit4U·신청 준비·진행상태·법원 대응) — 상태창의 Credit4U 재진입 링크만 남는다
    assertThat(html).doesNotContain("class=\"ns-feature-card\" href=\"/newstart/tools/");
    assertThat(html).contains("href=\"/newstart/tools/credit4u\"");
    // 모션 Phase 1 baseline: reveal 훅과 배지·상태창은 그대로
    assertThat(html).contains("id=\"ns-debt-home-state\"");
    assertThat(html).contains("data-ns-reveal");
  }

  @Test
  @DisplayName("A to Z는 §3에서 실사이트로 확인한 공식 경로만 렌더한다")
  void aToZRendersVerifiedOfficialLinks() {
    String html = render("newstart/a-to-z");

    assertThat(html).contains("https://www.youtube.com/watch?v=GALYPhiZLaw");
    assertThat(html).contains("https://www.newstartfund.or.kr/Rslt/Rslt.do");
    assertThat(html).contains("https://www.credit4u.or.kr:2443/");
    assertThat(html).contains("https://www.fss.or.kr/");
    assertThat(html).doesNotContain("https://home.newstartfund.or.kr/Rslt/Rslt.do");
    assertThat(html).doesNotContain("kamco.or.kr/portal");
    // 4-4 신청 준비 도우미 CTA 제거, STEP 7·9의 동일 도우미 참조는 보존
    String step4 = html.substring(html.indexOf("id=\"step-4\""), html.indexOf("id=\"step-5\""));
    assertThat(step4).doesNotContain("/newstart/tools/application");
    assertThat(html).contains("부실차주 신청 준비 도우미");
  }

  @Test
  @DisplayName("질문요청·갈무리 화면이 파싱 오류 없이 렌더되고 로그인 안내 셸을 갖는다")
  void newBoardsRender() {
    String questions = render("newstart/questions");
    assertThat(questions).contains("id=\"ns-req-list\"");
    assertThat(questions).contains("src=\"/js/newstart-requests.js");

    String galmuri = render("newstart/galmuri");
    assertThat(galmuri).contains("id=\"ns-gm-list\"");
    assertThat(galmuri).contains("src=\"/js/newstart-galmuri.js");
    // 갈무리는 쓰기 폼이 없는 읽기 전용 feed(§6)
    assertThat(galmuri).doesNotContain("<form");
  }
}
