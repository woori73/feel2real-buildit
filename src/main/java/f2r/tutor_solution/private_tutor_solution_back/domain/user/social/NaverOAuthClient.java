package f2r.tutor_solution.private_tutor_solution_back.domain.user.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 네이버 OAuth 2.0 서버 간 호출 (authorization code → token → profile).
 *
 * <p>브라우저에는 절대 노출되지 않는 client_secret 사용 경로이므로 controller에서 직접
 * 이 컴포넌트만 경유한다. 외부 응답 파싱은 관용적으로 실패 시 empty/예외로 통일한다.
 */
@Slf4j
@Component
public class NaverOAuthClient {

  // 공식 문서(devs.naver.com 로그인 API 명세)의 token endpoint는 authorize와 동일 호스트(nid.naver.com)다.
  // auth.naver.com은 연결 자체가 실패해 exchangeCode가 침묵 실패한다(2026-09-20 운영 진단: social count 0).
  private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
  private static final String PROFILE_URL = "https://openapi.naver.com/v1/nid/me";

  private final NaverOAuthProperties props;
  private final HttpClient http;
  private final ObjectMapper mapper = new ObjectMapper();

  // 운영 컨텍스트가 선택하는 유일한 생성자. 아래 테스트용 2-arg 생성자와 공존하므로
  // @Autowired로 명시하지 않으면 Spring이 no-arg 생성자를 찾아 기동 실패한다 (Deploy #331 원인).
  @Autowired
  public NaverOAuthClient(NaverOAuthProperties props) {
    this(props, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  NaverOAuthClient(NaverOAuthProperties props, HttpClient http) {
    this.props = props;
    this.http = http;
  }

  public record NaverProfile(String providerUserId, String name, String nickname, String email) {}

  /** authorization code를 access token으로 교환. 실패 시 empty (상세 오류를 로그 밖으로 노출하지 않음). */
  public Optional<String> exchangeCode(String code, String state, String redirectUri) {
    String form = "grant_type=authorization_code"
        + "&client_id=" + enc(props.clientId())
        + "&client_secret=" + enc(props.clientSecret())
        + "&code=" + enc(code)
        + "&state=" + enc(state)
        + "&redirect_uri=" + enc(redirectUri);
    try {
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create(TOKEN_URL))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
              .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
              .build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      // 침묵 실패 원인 규명용 stage 로그 — status만 기록하고 요청/응답 본문(code/secret/token)은 절대 남기지 않는다.
      if (response.statusCode() != 200) {
        log.warn("NAVER_TOKEN_EXCHANGE_FAILED status={}", response.statusCode());
        return Optional.empty();
      }
      JsonNode body = mapper.readTree(response.body());
      return Optional.ofNullable(body.path("access_token").asText(null))
          .filter(token -> !token.isBlank());
    } catch (IOException e) {
      log.warn("NAVER_TOKEN_EXCHANGE_FAILED error={}", e.getClass().getSimpleName());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  /**
   * OpenAPI {@code /v1/nid/me} 프로필 조회. 네이버 문서상 resultcode "00" 성공,
   * result.id가 provider의 stable user identity다.
   */
  public Optional<NaverProfile> fetchProfile(String accessToken) {
    try {
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create(PROFILE_URL))
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + accessToken)
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        log.warn("NAVER_PROFILE_FETCH_FAILED status={}", response.statusCode());
        return Optional.empty();
      }
      JsonNode body = mapper.readTree(response.body());
      String resultcode = body.path("resultcode").asText();
      if (!"00".equals(resultcode)) {
        log.warn("NAVER_PROFILE_FETCH_FAILED resultcode={}", resultcode);
        return Optional.empty();
      }
      JsonNode result = body.path("result");
      String id = result.path("id").asText(null);
      if (id == null || id.isBlank()) return Optional.empty();
      return Optional.of(new NaverProfile(
          id,
          emptyToNull(result.path("name").asText(null)),
          emptyToNull(result.path("nickname").asText(null)),
          emptyToNull(result.path("email").asText(null))));
    } catch (IOException e) {
      log.warn("NAVER_PROFILE_FETCH_FAILED error={}", e.getClass().getSimpleName());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  private static String enc(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
