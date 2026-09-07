package f2r.tutor_solution.private_tutor_solution_back.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 「2학기 영어내신」(#sec-featured) 헤더의 "2026 중간고사 LIVE" pill CTA 계약.
 *
 * <ul>
 *   <li>eduholic 홈페이지에만(hostname gate) — 공용 Academy 페이지의 다른 학원에는 영향 없음
 *   <li>팝업 CTA 와 동일한 공개 페이지({@code /eduholic-midterm-2026.html})로 연결
 *   <li>로그인·{@code /problem-generator} 를 거치지 않음
 *   <li>접근성 있는 {@code <a href>} + aria-label + :focus-visible
 *   <li>데스크톱=제목 오른쪽 / 모바일=제목 아래(가로 overflow 방지)
 *   <li>Digital Twin 카드/슬라이더/상담 신청 버튼 마크업·동작을 건드리지 않음
 * </ul>
 */
class EduholicFeaturedCtaContractTest {

  private String read(String path) throws IOException {
    try (var in = new ClassPathResource(path).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void publicTemplate_loadsFeaturedCtaScript() throws Exception {
    String html = read("templates/public/academy_public.html");
    assertThat(html).contains("/js/eduholic_featured_cta.js");
  }

  @Test
  void ctaScript_isEduholicOnly_andLinksToPublicMidtermPage() throws Exception {
    String js = read("static/js/eduholic_featured_cta.js");

    assertThat(js).contains("eduholic.feel2real.co.kr");            // hostname gate
    assertThat(js).contains("/eduholic-midterm-2026.html");         // 팝업 CTA 와 동일 목적지
    assertThat(js).doesNotContain("/problem-generator");            // workspace 로 새지 않음
    assertThat(js).doesNotContain("fetchAuth");                     // 로그인 경로 없음
    assertThat(js).contains("#sec-featured .featured-head");        // 해당 섹션 헤더만 타겟
    assertThat(js).contains("h2.featured-title");
  }

  @Test
  void ctaScript_buildsAccessibleAnchor() throws Exception {
    String js = read("static/js/eduholic_featured_cta.js");

    assertThat(js).contains("createElement('a')");
    assertThat(js).contains("link.href");
    assertThat(js).contains("aria-label");
    assertThat(js).contains("aria-hidden");                         // 장식용 화살표
    assertThat(js).contains(":focus-visible");
    assertThat(js).contains(":hover");
  }

  @Test
  void ctaScript_isMobileSafe_andDoesNotTouchTwinCardsOrConsult() throws Exception {
    String js = read("static/js/eduholic_featured_cta.js");

    // 모바일: 제목 아래로(세로) — 가로 overflow 방지
    assertThat(js).contains("@media (max-width:600px)");
    assertThat(js).contains("flex-direction:column");
    assertThat(js).contains("white-space:nowrap");

    // 공용 섹션의 다른 부분을 건드리지 않는다
    assertThat(js).doesNotContain("data-featured-cards");
    assertThat(js).doesNotContain("requestConsult");
    assertThat(js).doesNotContain("featured-cta");
    assertThat(js).doesNotContain("innerHTML =");
  }
}
