package f2r.tutor_solution.private_tutor_solution_back.domain.user.social.v2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.social.NaverOAuthProperties;
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
 * NewStart 전용 네이버 OAuth 2.0 서버 간 호출(V2) — 기존 {@code NaverOAuthClient}(V1)와 완전히
 * 독립된 새 구현이다. client_id/secret 설정 바인딩({@link NaverOAuthProperties})만 재사용하고,
 * 실제 outbound 호출·파싱·로깅은 V1을 참고하지 않고 네이버 공식 API 명세를 기준으로 새로 작성했다.
 *
 * <p>Token endpoint는 authorize와 동일 호스트({@code nid.naver.com})다 — V1에서 겪은
 * {@code auth.naver.com} 오호스트 실수를 반복하지 않도록 주석으로 명시해 둔다.
 */
@Slf4j
@Component
public class NaverV2Client {

  private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
  private static final String PROFILE_URL = "https://openapi.naver.com/v1/nid/me";

  private final NaverOAuthProperties props;
  private final HttpClient http;
  private final ObjectMapper mapper = new ObjectMapper();

  // production이 선택하는 유일한 생성자. 2-arg 생성자(테스트 전용)와 공존하므로 반드시 @Autowired를
  // 명시한다 — 이걸 빠뜨리면 Spring이 no-arg 생성자를 찾다가 부팅에 실패한다(V1 Deploy #331의 원인과
  // 동일 클래스의 실수이므로 V2에서는 처음부터 명시).
  @Autowired
  public NaverV2Client(NaverOAuthProperties props) {
    this(props, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
  }

  NaverV2Client(NaverOAuthProperties props, HttpClient http) {
    this.props = props;
    this.http = http;
  }

  public record NaverV2Profile(String providerUserId, String name, String nickname, String email) {}

  /** authorization code -> access token. 실패 시 empty, 상세는 상태코드/예외 클래스만 로그. */
  public Optional<String> exchangeCode(String code, String state, String redirectUri) {
    log.info("NAVER_V2_TOKEN_REQUEST");
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
      if (response.statusCode() != 200) {
        log.warn("NAVER_V2_TOKEN_FAILED status={}", response.statusCode());
        return Optional.empty();
      }
      JsonNode body = mapper.readTree(response.body());
      Optional<String> token = Optional.ofNullable(body.path("access_token").asText(null))
          .filter(t -> !t.isBlank());
      if (token.isPresent()) {
        log.info("NAVER_V2_TOKEN_OK");
      } else {
        log.warn("NAVER_V2_TOKEN_FAILED reason=no_access_token_in_response");
      }
      return token;
    } catch (IOException e) {
      log.warn("NAVER_V2_TOKEN_FAILED error={}", e.getClass().getSimpleName());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  /** OpenAPI {@code /v1/nid/me} 프로필 조회. resultcode "00" 성공, result.id가 안정 식별자다. */
  public Optional<NaverV2Profile> fetchProfile(String accessToken) {
    log.info("NAVER_V2_PROFILE_REQUEST");
    try {
      HttpResponse<String> response = http.send(
          HttpRequest.newBuilder(URI.create(PROFILE_URL))
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + accessToken)
              .GET()
              .build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        log.warn("NAVER_V2_PROFILE_FAILED status={}", response.statusCode());
        return Optional.empty();
      }
      JsonNode body = mapper.readTree(response.body());
      String resultcode = body.path("resultcode").asText();
      if (!"00".equals(resultcode)) {
        log.warn("NAVER_V2_PROFILE_FAILED resultcode={}", resultcode);
        return Optional.empty();
      }
      JsonNode result = body.path("result");
      String id = result.path("id").asText(null);
      if (id == null || id.isBlank()) {
        log.warn("NAVER_V2_PROFILE_FAILED reason=no_stable_id");
        return Optional.empty();
      }
      log.info("NAVER_V2_PROFILE_OK");
      return Optional.of(new NaverV2Profile(
          id,
          emptyToNull(result.path("name").asText(null)),
          emptyToNull(result.path("nickname").asText(null)),
          emptyToNull(result.path("email").asText(null))));
    } catch (IOException e) {
      log.warn("NAVER_V2_PROFILE_FAILED error={}", e.getClass().getSimpleName());
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
