package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.HomeReviewGameController;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller.LandingPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.landing.dto.HomeExamOffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** 2026-11-24 검색 노출: 공개 홈·FAQ의 교습소 구조화 데이터(JSON-LD)와 검색어 계약. DB 없이 실제 렌더한다. */
@DisplayName("공개 홈·FAQ 검색 노출 구조화 데이터 계약")
class PublicSeoStructuredDataContractTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern LD = Pattern.compile(
      "<script type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL);

  private static String render(String template) {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(resolver);
    engine.addDialect(new LayoutDialect());
    Context context = new Context();
    context.setVariable("examOffer", HomeExamOffer.CURRENT);
    context.setVariable("examPromoPath", LandingPageController.DEFAULT_PROMO_PATH);
    context.setVariable("homeReviewGameUrl", LandingPageController.DEFAULT_REVIEW_GAME_URL);
    context.setVariable("homeAgentTrailerPath", "");
    context.setVariable("homeReviewGamePath", HomeReviewGameController.PAGE_PATH);
    context.setVariable("homeBankTransfer", null);
    return engine.process(template, context);
  }

  private static List<JsonNode> jsonLd(String html) throws Exception {
    List<JsonNode> nodes = new ArrayList<>();
    Matcher m = LD.matcher(html);
    while (m.find()) {
      nodes.add(JSON.readTree(m.group(1)));
    }
    return nodes;
  }

  @Test
  @DisplayName("given 공개 홈 when 그리면 then 교습소 이름·새 주소·전화가 담긴 EducationalOrganization JSON-LD가 읽힌다")
  void landing_hasAcademyStructuredData() throws Exception {
    // given / when
    String html = render("public/landing");
    List<JsonNode> nodes = jsonLd(html);

    // then
    assertThat(nodes).hasSize(1);
    JsonNode academy = nodes.get(0).get("@graph").get(0);
    assertThat(academy.get("@type").asText()).isEqualTo("EducationalOrganization");
    assertThat(academy.get("name").asText()).isEqualTo("F2R 영어 교습소");
    assertThat(academy.get("telephone").asText()).isEqualTo("+82-2-6324-1453");
    assertThat(academy.get("address").get("streetAddress").asText()).isEqualTo("언주로173길 12 압구정빌딩 4층");
    assertThat(academy.get("knowsAbout").toString()).contains("압구정중 영어 내신", "신구중 영어 내신");
  }

  @Test
  @DisplayName("given 새 상품 F2R AI 코디 when 홈을 그리면 then 제목·키워드에 들어가고 교습소가 제공하는 SoftwareApplication으로 읽힌다")
  void landing_describesAiCoordinatorProduct() throws Exception {
    // given / when
    String html = render("public/landing");
    JsonNode graph = jsonLd(html).get(0).get("@graph");

    // then
    assertThat(html).contains("<title>F2R AI 코디 · ").contains("<meta name=\"keywords\" content=\"F2R AI 코디, ");
    JsonNode coordinator = graph.get(1);
    assertThat(coordinator.get("@type").asText()).isEqualTo("SoftwareApplication");
    assertThat(coordinator.get("name").asText()).isEqualTo("F2R AI 코디");
    assertThat(coordinator.get("provider").get("@id").asText()).isEqualTo("https://feel2real.co.kr/#academy");
  }

  @Test
  @DisplayName("given 공개 홈 when 그리면 then 검색어·대표 이미지·보이는 h1이 교습소·내신을 말한다")
  void landing_metaTargetsAcademyKeywords() {
    // given / when
    String html = render("public/landing");

    // then
    assertThat(html).contains("압구정중 영어 내신, 신구중 영어 내신")
        .contains("<meta property=\"og:image\" content=\"https://feel2real.co.kr/img/landing/f2r-class-ad-20261120.jpg\">")
        .contains(">F2R AI 코디 · F2R 영어 교습소 — 휴대폰 하나로 학원 업무를 AI에게, 압구정중·신구중 영어 내신</h1>");
  }

  @Test
  @DisplayName("given FAQ 페이지 when 그리면 then FAQPage JSON-LD 질문 수와 화면의 질문 수가 같고 교습소 위치 질문이 있다")
  void faq_structuredDataMatchesVisibleQuestions() throws Exception {
    // given / when
    String html = render("public/faq");
    List<JsonNode> nodes = jsonLd(html);
    int visible = html.split("<summary>", -1).length - 1;

    // then
    assertThat(nodes).hasSize(1);
    JsonNode faq = nodes.get(0);
    assertThat(faq.get("@type").asText()).isEqualTo("FAQPage");
    assertThat(faq.get("mainEntity")).hasSize(visible);
    assertThat(faq.get("mainEntity").get(0).get("name").asText()).isEqualTo("Feel2Real은 어떤 회사인가요?");
    assertThat(faq.toString()).contains("F2R AI 코디는 무엇인가요?", "F2R 영어 교습소는 어디에 있나요?", "언주로173길 12 압구정빌딩 4층", "압구정중·신구중 영어 내신 수업이 있나요?");
  }
}
