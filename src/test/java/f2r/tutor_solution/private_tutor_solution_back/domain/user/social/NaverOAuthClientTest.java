package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthClient.NaverProfile;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * NaverOAuthClient가 네이버 공식 문서의 실제 endpoint를 호출하는지 고정한다.
 *
 * <p>2026-09-20 운영 인시던트: token endpoint를 잘못된 호스트(auth.naver.com)로 보내면
 * connection이 실패해 exchangeCode가 Optional.empty로 침묵 실패하고, callback은 회원/JWT를
 * 전혀 만들지 않은 채 /newstart/chat?login=naver_failed로 되돌아간다. 컨트롤러 테스트는
 * client를 mock하므로 이 URL 오류를 잡지 못한다 — 여기서는 HttpClient만 mock하고 실제
 * request URI를 검증해 endpoint 회귀를 원천 차단한다.
 */
class NaverOAuthClientTest {

  @Test
  @SuppressWarnings("unchecked")
  void exchangeCodePostsToOfficialNidTokenEndpoint() throws Exception {
    // given
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"access_token\":\"tok\",\"token_type\":\"bearer\"}");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    NaverOAuthClient client = new NaverOAuthClient(
        new NaverOAuthProperties("cid", "csecret", "https://feel2real.co.kr/oauth/naver/callback"), http);

    // when
    Optional<String> token =
        client.exchangeCode("code-123", "state-abc", "https://feel2real.co.kr/oauth/naver/callback");

    // then — token은 잘 파싱되고, 요청은 공식 nid.naver.com/oauth2.0/token POST여 한다
    assertThat(token).contains("tok");
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    org.mockito.Mockito.verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
    HttpRequest sent = captor.getValue();
    assertThat(sent.method()).isEqualTo("POST");
    assertThat(sent.uri().toString()).isEqualTo("https://nid.naver.com/oauth2.0/token");
  }

  @Test
  @SuppressWarnings("unchecked")
  void fetchProfileGetsOpenApiNidMeWithBearerHeader() throws Exception {
    // given
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(
        "{\"resultcode\":\"00\",\"result\":{\"id\":\"375412389021\",\"name\":\"김철수\",\"nickname\":\"cheolsu\"}}");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    NaverOAuthClient client = new NaverOAuthClient(
        new NaverOAuthProperties("cid", "csecret", ""), http);

    // when
    Optional<NaverProfile> profile = client.fetchProfile("access-token");

    // then
    assertThat(profile).isPresent();
    assertThat(profile.get().providerUserId()).isEqualTo("375412389021");
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    org.mockito.Mockito.verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
    HttpRequest sent = captor.getValue();
    assertThat(sent.method()).isEqualTo("GET");
    assertThat(sent.uri().toString()).isEqualTo("https://openapi.naver.com/v1/nid/me");
    assertThat(sent.headers().firstValue("Authorization")).contains("Bearer access-token");
  }
}
