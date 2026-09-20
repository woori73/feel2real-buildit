package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthProperties;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2.NaverV2Client.NaverV2Profile;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * NaverV2Client가 네이버 공식 문서의 실제 endpoint를 호출하는지 고정한다 — V1의 token endpoint
 * 오호스트 인시던트(auth.naver.com)를 V2에서 처음부터 반복하지 않도록 회귀 고정한다.
 */
class NaverV2ClientTest {

  @Test
  @SuppressWarnings("unchecked")
  void exchangeCodePostsToOfficialNidTokenEndpoint() throws Exception {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"access_token\":\"tok\",\"token_type\":\"bearer\"}");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    NaverV2Client client = new NaverV2Client(
        new NaverOAuthProperties("cid", "csecret", ""), http);

    Optional<String> token =
        client.exchangeCode("code-123", "state-abc", "https://feel2real.co.kr/auth/naver/v2/callback");

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
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn(
        "{\"resultcode\":\"00\",\"result\":{\"id\":\"375412389021\",\"name\":\"김철수\",\"nickname\":\"cheolsu\"}}");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    NaverV2Client client = new NaverV2Client(
        new NaverOAuthProperties("cid", "csecret", ""), http);

    Optional<NaverV2Profile> profile = client.fetchProfile("access-token");

    assertThat(profile).isPresent();
    assertThat(profile.get().providerUserId()).isEqualTo("375412389021");
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    org.mockito.Mockito.verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
    HttpRequest sent = captor.getValue();
    assertThat(sent.method()).isEqualTo("GET");
    assertThat(sent.uri().toString()).isEqualTo("https://openapi.naver.com/v1/nid/me");
    assertThat(sent.headers().firstValue("Authorization")).contains("Bearer access-token");
  }

  @Test
  @SuppressWarnings("unchecked")
  void tokenFailureReturnsEmptyWithoutThrowing() throws Exception {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(401);
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    NaverV2Client client = new NaverV2Client(new NaverOAuthProperties("cid", "csecret", ""), http);

    assertThat(client.exchangeCode("code", "state", "https://feel2real.co.kr/auth/naver/v2/callback"))
        .isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void profileFailureReturnsEmptyWithoutThrowing() throws Exception {
    HttpClient http = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"resultcode\":\"024\",\"message\":\"Authentication failed\"}");
    when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    NaverV2Client client = new NaverV2Client(new NaverOAuthProperties("cid", "csecret", ""), http);

    assertThat(client.fetchProfile("bad-token")).isEmpty();
  }
}
