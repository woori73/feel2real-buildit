package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /privacy — Chrome Web Store 제출용 공개 개인정보처리방침. 비로그인 공개 렌더링과 NewStart Chrome Agent v0.4.0의 실제
 * 동작과 일치하는 문구만 포함함을 검증한다.
 */
@WebMvcTest(controllers = PublicLegalController.class)
@AutoConfigureMockMvc
@Import({
  SecurityConfig.class,
  WebMvcConfig.class,
  RoleModelInterceptor.class,
  JwtTokenProvider.class,
  CookieUtil.class
})
@TestPropertySource(
    properties = {
      "app.jwt.secret=12345678901234567890123456789012",
      "app.jwt.expiration-seconds=86400",
      "app.jwt.cookie-name=ACCESS_TOKEN",
      "app.jwt.secure-cookie=false",
      "app.exam-intake.enabled=false",
      "app.exam-intake.bank-public-enabled=false",
      "app.home.exam-seller-confirmed=false"
    })
class PublicLegalControllerTest {

  @Autowired MockMvc mockMvc;

  @Test
  @DisplayName("비로그인으로 /privacy가 200으로 렌더링된다")
  void anonymousCanOpenPrivacy() throws Exception {
    mockMvc
        .perform(get("/privacy"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<!DOCTYPE html>")))
        .andExpect(content().string(containsString("개인정보처리방침")));
  }

  @Test
  @DisplayName("문구는 v0.4.0 실제 동작과 일치 — storage 권한, 2개 호스트, 입력값·인증정보 미수집, 자동 제출 없음")
  void contentMatchesExtensionBehaviour() throws Exception {
    String html =
        mockMvc
            .perform(get("/privacy"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.assertj.core.api.Assertions.assertThat(html)
        .contains("storage")
        .contains("https://feel2real.co.kr/*")
        .contains("https://www.fss.or.kr/*")
        .contains("읽지도 저장하지도 전송하지도")
        .contains("공동인증서, OTP, CAPTCHA, 주민등록번호")
        .contains("민원을 자동 제출하지 않습니다")
        .contains("외부 서버로 전송하지 않습니다");
    // 실제 사이트 푸터의 운영자 정보 재사용(가짜 연락처 금지)
    org.assertj.core.api.Assertions.assertThat(html)
        .contains("admin@feel2real.co.kr")
        .contains("압구정에듀홀릭학원");
  }

  @Test
  @DisplayName("허위·미비치 경로 금지 — 미구현 이용약관/가짜 스토어 URL을 페이지에 넣지 않는다")
  void noFakeLinks() throws Exception {
    String html =
        mockMvc
            .perform(get("/privacy"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.assertj.core.api.Assertions.assertThat(html)
        .doesNotContain("chromewebstore.google.com")
        .doesNotContain("chrome.google.com/webstore");
  }

  @Test
  @DisplayName("신청·판매자 확인 비활성 상태에도 정책 본문과 실제 링크를 공개한다")
  void policyNavigationDoesNotDependOnIntakeOrSellerApproval() throws Exception {
    // given: all publication flags are false; when: anonymous reader opens the policy hub
    var html =
        mockMvc
            .perform(get("/policies"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var doc = Jsoup.parse(html);
    // then: scope and draft status are readable, and no bank account is published
    assertThat(doc.selectFirst("h1").text()).contains("개인정보·이용·환불 안내");
    for (String id : new String[] {"privacy", "terms", "business"}) {
      assertThat(doc.getElementById(id).text()).hasSizeGreaterThan(80);
    }
    assertThat(doc.selectFirst(".policy-notice").text())
        .contains("검토안", "확정된 약관이나 시행 중인 개인정보 처리방침이 아닙니다");
    assertThat(doc.select(".footer-links a[href]").eachAttr("href"))
        .containsExactly(
            "/policies/privacy", "/policies/terms", "/policies/refunds", "/policies/business");
    assertThat(doc.select(".footer-links [onclick=comingSoon()]")).isEmpty();
    assertThat(doc.select("main a[href]").eachAttr("href"))
        .contains("/privacy", "/exam-service/privacy#privacy", "/exam-service/terms#terms");
    assertThat(doc.select("form,input")).isEmpty();
    assertThat(html).doesNotContain("110-498-250866");
  }

  @Test
  @DisplayName("정책 보완 범위와 기존 문의 창구를 명시하고 책임자·주소를 추정하지 않는다")
  void pendingPolicyDetailsAreNotPresentedAsApproved() throws Exception {
    var html =
        mockMvc
            .perform(get("/policies"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var doc = Jsoup.parse(html);
    assertThat(doc.select(".policy-details dt").eachText())
        .containsExactly("수집 목적·항목", "보유 기간·파기", "제3자 제공·처리위탁", "권리 행사", "보호책임자·안전조치", "아동 개인정보");
    assertThat(doc.text()).contains("만 14세 미만", "법정대리인", "최종 승인 원문을 확인 중", "증빙 확인 중");
    assertThat(doc.select("#business a").eachAttr("href"))
        .contains("mailto:admin@feel2real.co.kr", "tel:02-6324-1453");
    assertThat(doc.select(".footer-biz").text()).contains("압구정에듀홀릭학원", "압구정로34길 11");
  }

  @Test
  @DisplayName("기존 확장 정책에는 범위 구분 링크와 동작 가능한 문의 모달이 있다")
  void extensionPolicyKeepsOriginalScopeAndWorkingContact() throws Exception {
    var html =
        mockMvc
            .perform(get("/privacy"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var doc = Jsoup.parse(html);
    assertThat(doc.selectFirst(".pg-updated").text()).contains("2026년 9월 25일", "확장 프로그램 v0.4.0");
    assertThat(doc.select("a[href='/policies#privacy']")).isNotEmpty();
    assertThat(doc.getElementById("contactModal")).isNotNull();
    assertThat(html).contains("function openContactModal()");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "privacy, Feel2Real 홈페이지 개인정보 처리방침, 8",
    "terms, Feel2Real 홈페이지 이용약관, 6",
    "refunds, 환불·청약철회 정책, 4",
    "business, 사업자 정보, 2"
  })
  @DisplayName("네 푸터 문서는 독립 주소·본문·적용 범위를 가지며 확정 정책을 가장하지 않는다")
  void separatePolicyDocuments(String section, String title, int headings) throws Exception {
    // given: anonymous and all intake/publication flags disabled; when: navigate directly
    var html =
        mockMvc
            .perform(get("/policies/" + section))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var doc = Jsoup.parse(html);
    // then: the requested document, not a blank shell or extension document, is rendered
    assertThat(doc.selectFirst("h1").text()).isEqualTo(title);
    assertThat(doc.select(".policy-content h2")).hasSize(headings);
    assertThat(doc.select(".policy-content").text()).hasSizeGreaterThan(100);
    assertThat(doc.select("form,input")).isEmpty();
    assertThat(html).doesNotContain("110-498-250866");
    if (!section.equals("business")) {
      assertThat(doc.selectFirst(".policy-notice").text())
          .contains("최종 승인 전", "시행일 미확정", "확정된 정책이 아니며");
    }
    if (section.equals("privacy")) {
      assertThat(doc.text())
          .contains("보유 기간은 검토 필요", "제3자 제공·처리위탁·국외 이전", "전체 서비스의 연령 확인", "보호책임자");
      assertThat(doc.select(".policy-content a[href='/privacy']")).hasSize(1);
    }
    if (section.equals("refunds")) {
      assertThat(doc.select("tbody td:nth-child(2)").eachText())
          .containsExactly("20,000원", "15,000원", "10,000원", "5,000원", "0원");
      assertThat(doc.text()).contains("모든 법정 환불 권리의 소멸을 뜻하지 않습니다");
    }
  }
}
