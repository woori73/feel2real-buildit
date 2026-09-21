package f2r.tutor_solution.private_tutor_solution_back.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * NewStart 네이버 로그인 STEP 1 — SecurityConfig에 oauth2Login을 추가해도 기존 인가 규칙이
 * 회귀하지 않음을 확인한다(docs/newstart/oauth/NAVER_LOGIN_REBUILD_DESIGN_2026-09-21.md §11).
 *
 * <p>NAVER_CLIENT_ID/SECRET 환경변수는 쓰지 않는다 — 이 테스트 컨텍스트에서만
 * {@code @TestPropertySource}로 dummy registration을 주입해 ClientRegistrationRepository 빈을
 * 존재하게 만든다(그래야 SecurityConfig의 ObjectProvider 분기가 oauth2Login을 구성한다). 실제
 * 네이버 로그인은 절대 수행하지 않는다 — 여기서 검증하는 것은 인가 규칙 회귀뿐이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.naver.client-id=test-dummy-client-id",
    "spring.security.oauth2.client.registration.naver.client-secret=test-dummy-client-secret",
    "spring.security.oauth2.client.registration.naver.redirect-uri=https://feel2real.co.kr/login/oauth2/code/naver",
    "spring.security.oauth2.client.registration.naver.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.naver.client-name=Naver",
    "spring.security.oauth2.client.provider.naver.authorization-uri=https://nid.naver.com/oauth2.0/authorize",
    "spring.security.oauth2.client.provider.naver.token-uri=https://nid.naver.com/oauth2.0/token",
    "spring.security.oauth2.client.provider.naver.user-info-uri=https://openapi.naver.com/v1/nid/me",
    "spring.security.oauth2.client.provider.naver.user-name-attribute=response",
})
@DisplayName("NAVER OAuth2 추가 후 SecurityConfig 회귀 테스트")
class NaverOAuth2SecurityRegressionTest {

  @Autowired MockMvc mockMvc;

  @Test
  @DisplayName("익명 사용자가 /oauth2/authorization/naver로 접근하면 네이버 인가 화면으로 302 리다이렉트된다")
  void anonymousCanStartAuthorization() throws Exception {
    MvcResult result = mockMvc.perform(get("/oauth2/authorization/naver"))
        .andExpect(status().is3xxRedirection())
        .andReturn();

    String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
    assertThat(location).startsWith("https://nid.naver.com/oauth2.0/authorize");
    // Client Secret은 authorization 요청 자체에 포함되지 않는다 — URL에 노출되면 안 된다.
    assertThat(location).doesNotContain("test-dummy-client-secret");
  }

  @Test
  @DisplayName("code/state 없이 콜백 경로를 anonymous로 쳐도 ROLE 기반 403이 아니라 " +
      "OAuth 실패 핸들러가 401 JSON으로 응답한다(비밀정보 미노출)")
  void anonymousCanReachCallbackAndGetsControlledFailure() throws Exception {
    MvcResult result = mockMvc.perform(get("/login/oauth2/code/naver"))
        .andExpect(status().isUnauthorized())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("\"provider\":\"NAVER\"");
    assertThat(body).contains("\"success\":false");
    assertThat(body).doesNotContain("test-dummy-client-secret");
    assertThat(body).doesNotContainIgnoringCase("access_token");
  }

  @Test
  @DisplayName("기존 ADMIN 전용 API는 oauth2Login 추가 후에도 익명 접근 시 401을 유지한다")
  void adminApiStillRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/admin/students"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("기존 TUTOR 전용 API는 oauth2Login 추가 후에도 익명 접근 시 401을 유지한다")
  void tutorApiStillRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/tutor/academy/me"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("공개 랜딩('/')은 oauth2Login 추가 후에도 여전히 permitAll이다")
  void publicLandingStillPermitAll() throws Exception {
    mockMvc.perform(get("/"))
        .andExpect(status().isOk());
  }
}
