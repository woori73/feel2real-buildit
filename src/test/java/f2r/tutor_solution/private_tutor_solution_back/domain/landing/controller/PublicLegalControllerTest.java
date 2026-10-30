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
      "app.exam-intake.bank-public-enabled=false"
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
    // 사이트 푸터와 같은 운영자 정보(2026-10-28 확정 출판사, 가짜 연락처 금지)
    org.assertj.core.api.Assertions.assertThat(html)
        .contains("woorinaesin@naver.com")
        .contains("압구정에듀홀릭출판사")
        .doesNotContain("압구정에듀홀릭학원")
        .doesNotContain("759-91-01977");
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
    // then: the hub lists the official documents with the effective date, and no bank account is published
    assertThat(doc.selectFirst("h1").text()).contains("개인정보·이용·환불 안내");
    for (String id : new String[] {"documents", "business"}) {
      assertThat(doc.getElementById(id).text()).hasSizeGreaterThan(80);
    }
    assertThat(doc.selectFirst(".policy-notice")).isNull();
    assertThat(doc.selectFirst("header").text()).contains("시행일 2026년 10월 30일");
    assertThat(doc.select(".footer-links a[href]").eachAttr("href"))
        .containsExactly(
            "/policies/privacy", "/policies/terms", "/policies/refunds", "/policies/business", "#contactModal");
    assertThat(doc.select(".footer-links [onclick=comingSoon()]")).isEmpty();
    assertThat(doc.select("main a[href]").eachAttr("href"))
        .contains("/policies/privacy", "/policies/terms", "/policies/refunds")
        .doesNotContain("/privacy");
    assertThat(doc.text()).doesNotContain("NewStart");  // 홈페이지와 NewStart는 관련 없음(2026-10-30)
    assertThat(doc.select("form,input")).isEmpty();
    assertThat(html).doesNotContain("110-498-250866");
  }

  @Test
  @DisplayName("given 정책 안내 when 열면 then 문서 카드와 사업자 문의처를 보여 주고 출판사 정보만 쓴다")
  void policyHubShowsDocumentsAndPublisherContact() throws Exception {
    var html =
        mockMvc
            .perform(get("/policies"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var doc = Jsoup.parse(html);
    assertThat(doc.select("#documents article h3").eachText())
        .containsExactly("개인정보 처리방침", "이용약관", "환불·청약철회 정책");
    assertThat(doc.select("#business a").eachAttr("href"))
        .contains("mailto:woorinaesin@naver.com", "tel:010-8507-1453",
            "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=1469901347");
    assertThat(doc.select(".footer-biz").text())
        .contains("압구정에듀홀릭출판사", "146-99-01347", "제2026-서울송파-1411호", "송파구 중대로10길 37-25")
        .doesNotContain("압구정에듀홀릭학원", "759-91-01977", "압구정로34길");
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
    // 사업자 정보 문서에 「결제 방법」이 추가됐다(2026-10-30). 다른 문서의 운영자 정보는 본문 밖 별도 영역이다.
    // 2026-10-30 정식 문서로 개편: 개인정보 12개 항목, 약관 12개 조, 환불 5개 항목.
    "privacy, Feel2Real 홈페이지 개인정보 처리방침, 12",
    "terms, Feel2Real 홈페이지 이용약관, 12",
    "refunds, 환불·청약철회 정책, 5",
    "business, 사업자 정보, 3"
  })
  @DisplayName("네 푸터 문서는 독립 주소·본문·적용 범위를 가지며 시행일이 있는 정식 문서로 보인다")
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
    assertThat(doc.selectFirst(".policy-notice")).isNull();
    if (!section.equals("business")) {
      assertThat(doc.selectFirst(".policy-effective").text()).isEqualTo("시행일 2026년 10월 30일");
    }
    if (section.equals("privacy")) {
      assertThat(doc.text())
          .contains("6. 처리 위탁", "Amazon Web Services(AWS)", "AWS 서울 리전(ap-northeast-2)",
              "담당자가 직접 삭제합니다", "매일 자동으로 삭제합니다", "네이버 로그인으로 가입한 회원은 비밀번호가 없어",
              "연령 확인 절차가 없으므로", "방문 분석 태그(Google Analytics 등)는 설정되어 있지 않습니다",
              "개인정보 보호책임자: 박정민(대표)", "만 14세 미만", "법정대리인", "계약 또는 청약철회 등에 관한 기록: 5년",
              "ACCESS_TOKEN", "F2R_VISITOR");
      assertThat(doc.select(".policy-content a[href='/privacy']")).isEmpty();
      assertThat(doc.select("main").text()).doesNotContain("NewStart", "국외로 이전하지 않습니다");
    }
    if (section.equals("terms")) {
      assertThat(doc.text()).contains("20,000원", "무통장입금", "대한민국 법", "시험 적중을 보장하지 않습니다");
    }
    if (section.equals("refunds")) {
      assertThat(doc.select("tbody td:nth-child(2)").eachText())
          .containsExactly("20,000원", "15,000원", "10,000원", "5,000원", "0원");
      assertThat(doc.text()).contains("7일 이내", "제17조 제2항 제5호·제6항", "제공하지 않은 회차는 언제나 청약철회 대상",
          "3영업일 이내", "위 표와 관계없이");
    }
  }

  @Test
  @DisplayName("given 정책 안내·문서·FAQ when 열면 then 검토안·초안·확인 중·미확정 등 내부 작업 표시가 없다")
  void policyPagesHaveNoDraftWording() throws Exception {
    for (String path : new String[] {"/policies", "/policies/privacy", "/policies/terms", "/policies/refunds", "/policies/business", "/faq"}) {
      // given / when
      var text = Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString()).select("main, #exam-service-terms").text();
      // then
      assertThat(text).as(path).doesNotContain("검토안", "초안", "임시안", "확인 중", "미확정", "추후 확정", "최종 승인",
          "미승인", "보존안", "준비안", "기본 정산", "예정액");
    }
  }

  @Test
  @DisplayName("given 사업자 정보·정책 문서 when 열면 then 확정 출판사 정보를 한 출처로 보여 주고 학원 정보·증빙 확인 중 문구가 없다")
  void businessAndPolicyDocumentsShowConfirmedPublisher() throws Exception {
    for (String path : new String[] {"/policies/business", "/policies/privacy", "/policies/terms", "/policies/refunds"}) {
      // given / when
      var doc = Jsoup.parse(mockMvc.perform(get(path)).andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString());
      // then: 본문(.policy-seller)과 footer가 같은 값
      var seller = doc.selectFirst("main .policy-seller");
      assertThat(seller).as(path).isNotNull();
      assertThat(seller.text()).as(path)
          .contains("상호명: 압구정에듀홀릭출판사", "대표: 박정민", "사업자등록번호: 146-99-01347",
              "통신판매업 신고번호: 제2026-서울송파-1411호", "주소: 서울특별시 송파구 중대로10길 37-25, 4층(가락동)",
              "전화: 010-8507-1453", "이메일: woorinaesin@naver.com");
      assertThat(seller.select("a").eachAttr("href")).contains("https://www.ftc.go.kr/bizCommPop.do?wrkr_no=1469901347");
      assertThat(doc.select(".footer-biz").text()).as(path).contains("146-99-01347");
      assertThat(doc.text()).as(path)
          .doesNotContain("압구정에듀홀릭학원", "759-91-01977", "압구정로34길", "02-6324-1453", "admin@feel2real.co.kr",
              "증빙 확인 중", "기존 운영자 창구");
    }
    var business = mockMvc.perform(get("/policies/business")).andReturn().getResponse().getContentAsString();
    assertThat(business).contains("무통장입금").doesNotContain("최신 증빙 확인 중");
  }
}
