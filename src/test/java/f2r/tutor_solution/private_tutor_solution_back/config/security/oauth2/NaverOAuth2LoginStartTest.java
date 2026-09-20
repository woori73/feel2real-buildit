package f2r.tutor_solution.private_tutor_solution_back.config.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import f2r.tutor_solution.private_tutor_solution_back.config.SecurityConfig;
import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller.NewStartPageController;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.SocialAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * "가장 중요한 검증" — UI를 연결하기 전에 먼저 이 하나만 PASS해야 한다: 공식 Spring Security
 * OAuth2 Client 표준 흐름의 시작점 {@code GET /oauth2/authorization/naver}이 실제로
 * {@code https://nid.naver.com/oauth2.0/authorize}로 302 하는지. 이 클래스는 V1/V2의 손수 작성한
 * {@code /oauth/naver/start}·{@code /auth/naver/v2/start}와 완전히 무관하며, 오직
 * {@link NaverClientRegistrationConfig} + {@link SecurityConfig}의
 * {@code .oauth2Login(...)} 배선만으로 이 동작이 나오는지를 검증한다 — 컨트롤러를 직접 만들지
 * 않았다는 증거이기도 하다(이 경로를 처리하는 {@code @RequestMapping}이 이 저장소 어디에도 없다).
 *
 * <p>{@code @WebMvcTest(controllers = NewStartPageController.class)}로 좁혔지만, 이 테스트가
 * 실제로 검증하는 경로(/oauth2/authorization/naver)는 그 컨트롤러와 무관하다 — Spring Security의
 * {@code OAuth2AuthorizationRequestRedirectFilter}가 DispatcherServlet의 컨트롤러 라우팅보다
 * 먼저 요청을 가로채 302를 만들기 때문에, 어떤 컨트롤러가 스캔 범위에 있는지는 이 동작과 무관하다.
 */
@WebMvcTest(controllers = NewStartPageController.class)
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    NaverClientRegistrationConfig.class,
    NaverOAuth2UserService.class,
    NaverOAuth2LoginSuccessHandler.class,
    NaverOAuth2LoginFailureHandler.class,
    CookieUtil.class,
    JwtTokenProvider.class,
    NaverOAuth2LoginStartTest.TestConfig.class
})
@TestPropertySource(properties = {
    "app.jwt.secret=12345678901234567890123456789012",
    "app.jwt.expiration-seconds=86400",
    "app.jwt.cookie-name=ACCESS_TOKEN",
    "app.jwt.secure-cookie=false",
    "app.naver.oauth.client-id=test-client-id",
    "app.naver.oauth.client-secret=test-client-secret"
})
class NaverOAuth2LoginStartTest {

  @TestConfiguration
  @EnableConfigurationProperties(NaverOAuthProperties.class)
  static class TestConfig {
  }

  @Autowired MockMvc mockMvc;
  @MockitoBean UserRepository userRepository;
  @MockitoBean SocialAccountRepository socialAccountRepository;

  @Test
  @DisplayName("GET /oauth2/authorization/naver → 302 https://nid.naver.com/oauth2.0/authorize?...")
  void authorizationStartRedirectsToNaverOfficialAuthorizeEndpoint() throws Exception {
    var result = mockMvc.perform(get("/oauth2/authorization/naver"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location",
            org.hamcrest.Matchers.startsWith("https://nid.naver.com/oauth2.0/authorize")))
        .andReturn();

    String location = result.getResponse().getHeader("Location");
    assertThat(location).contains("client_id=test-client-id");
    assertThat(location).contains("response_type=code");
    // redirect_uri는 공식 Spring 기본 템플릿({baseUrl}/login/oauth2/code/{registrationId})이
    // 실제로 /login/oauth2/code/naver로 풀렸는지까지 확인한다(추측 아님 — 실측된 Location 문자열은
    // "redirect_uri=http://localhost/login/oauth2/code/naver"였다. Spring Security 7의
    // DefaultOAuth2AuthorizationRequestResolver는 authorization_code grant에 PKCE(code_challenge/
    // code_challenge_method=S256)를 기본으로 함께 보낸다 — 네이버가 이 부가 파라미터를 무시하는지는
    // 다음 단계(실제 네이버 로그인창)에서 실측 필요, 이번 라운드는 코드로 강제 비활성화하지 않았다).
    assertThat(location).contains("redirect_uri=");
    assertThat(location).contains("/login/oauth2/code/naver");
  }
}
