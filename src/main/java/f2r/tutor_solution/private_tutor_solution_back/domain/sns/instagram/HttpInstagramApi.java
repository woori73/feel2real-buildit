package f2r.tutor_solution.private_tutor_solution_back.domain.sns.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import f2r.tutor_solution.private_tutor_solution_back.domain.sns.config.SnsProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** graph.instagram.com 실제 호출. 요청 주소에 토큰이 들어가므로 주소·응답 원문을 로그에 남기지 않는다. */
@Component
public class HttpInstagramApi implements InstagramApi {

  private static final Logger log = LoggerFactory.getLogger(HttpInstagramApi.class);
  static final String SCOPES = "instagram_business_basic,instagram_business_content_publish";

  private final SnsProperties.Meta meta;
  private final SnsProperties properties;
  private final HttpClient http;
  private final ObjectMapper mapper = new ObjectMapper();

  public HttpInstagramApi(SnsProperties properties) {
    this.properties = properties;
    this.meta = properties.getMeta();
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public String authorizeUrl(String state) {
    return meta.getAuthorizeUrl() + "?" + form(ordered(
        "client_id", meta.getAppId(),
        "redirect_uri", properties.effectiveRedirectUri(),
        "response_type", "code",
        "scope", SCOPES,
        "state", state));
  }

  @Override
  public TokenResult exchangeCode(String code) {
    JsonNode n = post(meta.getTokenUrl(), ordered(
        "client_id", meta.getAppId(),
        "client_secret", meta.getAppSecret(),
        "grant_type", "authorization_code",
        "redirect_uri", properties.effectiveRedirectUri(),
        "code", code), "인증 코드 교환");
    // 응답이 {"data":[{...}]} 형태로 오는 버전도 있다.
    if (n.has("data") && n.get("data").isArray() && n.get("data").size() > 0) n = n.get("data").get(0);
    return new TokenResult(req(n, "access_token"), n.path("user_id").asText(null), n.path("expires_in").asLong(3600));
  }

  @Override
  public TokenResult exchangeLongLived(String shortToken) {
    JsonNode n = get(meta.getGraphBaseUrl() + "/access_token?" + form(ordered(
        "grant_type", "ig_exchange_token",
        "client_secret", meta.getAppSecret(),
        "access_token", shortToken)), "긴 토큰 교환");
    return new TokenResult(req(n, "access_token"), null, n.path("expires_in").asLong(0));
  }

  @Override
  public TokenResult refresh(String longToken) {
    JsonNode n = get(meta.getGraphBaseUrl() + "/refresh_access_token?" + form(ordered(
        "grant_type", "ig_refresh_token",
        "access_token", longToken)), "토큰 갱신");
    return new TokenResult(req(n, "access_token"), null, n.path("expires_in").asLong(0));
  }

  @Override
  public Profile me(String token) {
    JsonNode n = get(versioned("/me") + "?" + form(ordered("fields", "user_id,username", "access_token", token)), "계정 정보 조회");
    String id = n.hasNonNull("user_id") ? n.get("user_id").asText() : n.path("id").asText(null);
    return new Profile(id, n.path("username").asText(null));
  }

  @Override
  public String createImageContainer(String igUserId, String token, String imageUrl, String caption) {
    JsonNode n = post(versioned("/" + igUserId + "/media"), ordered(
        "image_url", imageUrl,
        "caption", caption,
        "access_token", token), "게시물 준비");
    return req(n, "id");
  }

  @Override
  public String containerStatus(String containerId, String token) {
    JsonNode n = get(versioned("/" + containerId) + "?" + form(ordered("fields", "status_code", "access_token", token)), "게시물 준비 상태 조회");
    return n.path("status_code").asText("UNKNOWN");
  }

  @Override
  public String publish(String igUserId, String token, String creationId) {
    JsonNode n = post(versioned("/" + igUserId + "/media_publish"), ordered(
        "creation_id", creationId,
        "access_token", token), "게시");
    return req(n, "id");
  }

  private String versioned(String path) {
    return meta.getGraphBaseUrl() + "/" + meta.getGraphVersion() + path;
  }

  private JsonNode get(String url, String action) {
    return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(meta.getTimeoutSeconds())).GET().build(), action);
  }

  private JsonNode post(String url, Map<String, String> params, String action) {
    HttpRequest r = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(meta.getTimeoutSeconds()))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form(params), StandardCharsets.UTF_8))
        .build();
    return send(r, action);
  }

  private JsonNode send(HttpRequest request, String action) {
    try {
      HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode body = res.body() == null || res.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(res.body());
      if (res.statusCode() >= 400 || body.has("error") || body.has("error_message")) {
        String msg = body.path("error").path("message").asText(body.path("error_message").asText("HTTP " + res.statusCode()));
        log.warn("[sns] instagram {} 실패 status={}", action, res.statusCode());
        throw new InstagramApiException(action + " 실패: " + scrub(msg));
      }
      return body;
    } catch (IOException e) {
      log.warn("[sns] instagram {} 통신 오류: {}", action, e.getClass().getSimpleName());
      throw new InstagramApiException(action + " 실패: 통신 오류");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InstagramApiException(action + " 중단");
    }
  }

  /** 오류 메시지에 토큰 비슷한 긴 문자열이 섞여 오면 지운다. */
  static String scrub(String msg) {
    if (msg == null) return "";
    String s = msg.replaceAll("[A-Za-z0-9_\\-]{40,}", "***");
    return s.length() > 300 ? s.substring(0, 300) : s;
  }

  private static String req(JsonNode n, String field) {
    String v = n.path(field).asText(null);
    if (v == null || v.isBlank()) throw new InstagramApiException("응답에 " + field + " 값이 없습니다.");
    return v;
  }

  private static Map<String, String> ordered(String... kv) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
    return m;
  }

  static String form(Map<String, String> params) {
    return params.entrySet().stream()
        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }
}
