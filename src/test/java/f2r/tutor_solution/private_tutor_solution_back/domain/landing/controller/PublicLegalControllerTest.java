package f2r.tutor_solution.private_tutor_solution_back.domain.landing.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.RoleModelInterceptor;
import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.WebMvcConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /privacy — Chrome Web Store 제출용 공개 개인정보처리방침.
 * 비로그인 공개 렌더링과 NewStart Chrome Agent v0.4.0의 실제 동작과 일치하는 문구만 포함함을 검증한다.
 */
@WebMvcTest(controllers = PublicLegalController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, WebMvcConfig.class, RoleModelInterceptor.class, JwtTokenProvider.class, CookieUtil.class})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false"
})
class PublicLegalControllerTest {

  @Autowired MockMvc mockMvc;

  @Test
  @DisplayName("비로그인으로 /privacy가 200으로 렌더링된다")
  void anonymousCanOpenPrivacy() throws Exception {
    mockMvc.perform(get("/privacy"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<!DOCTYPE html>")))
        .andExpect(content().string(containsString("개인정보처리방침")));
  }

  @Test
  @DisplayName("문구는 v0.4.0 실제 동작과 일치 — storage 권한, 2개 호스트, 입력값·인증정보 미수집, 자동 제출 없음")
  void contentMatchesExtensionBehaviour() throws Exception {
    String html = mockMvc.perform(get("/privacy"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

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
        .contains("woorinaesin@naver.com")
        .contains("압구정에듀홀릭출판사");
  }

  @Test
  @DisplayName("허위·미비치 경로 금지 — 미구현 이용약관/가짜 스토어 URL을 페이지에 넣지 않는다")
  void noFakeLinks() throws Exception {
    String html = mockMvc.perform(get("/privacy"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    org.assertj.core.api.Assertions.assertThat(html)
        .doesNotContain("chromewebstore.google.com")
        .doesNotContain("chrome.google.com/webstore");
  }
}
