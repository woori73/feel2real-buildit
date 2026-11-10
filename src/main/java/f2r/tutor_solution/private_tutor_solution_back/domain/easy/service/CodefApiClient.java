package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * CODEF(헥토데이터) REST 프로토콜 클라이언트 — 공식 문서·공식 샘플(codef-io/codef-python)에서 확인한 형식만 쓴다.
 *
 * <ul>
 *   <li>토큰: {@code POST https://oauth.codef.io/oauth/token}, {@code Authorization: Basic
 *       base64(client_id:client_secret)}, body {@code grant_type=client_credentials&scope=read}.
 *       응답 {@code access_token}, {@code expires_in}(604799초 = 1주). 재사용 권장.</li>
 *   <li>상품 호출: {@code POST {base}{endpoint}}, {@code Authorization: Bearer}, 본문은 JSON을
 *       UTF-8 URL 인코딩한 문자열. 응답 본문도 URL 인코딩되어 있어 디코딩 후 JSON으로 읽는다.</li>
 *   <li>결과: {@code result.code} — {@code CF-00000} 성공. 토큰 오류는 HTTP 401 또는
 *       {@code CF-0999x}이며 토큰을 다시 받아 한 번만 재시도한다.</li>
 * </ul>
 *
 * <p>자격증명·토큰·connectedId·계좌번호는 예외 메시지와 로그에 넣지 않는다.
 */
public class CodefApiClient {

  public static final String TOKEN_URL = "https://oauth.codef.io/oauth/token";
  public static final String PRODUCTION_BASE = "https://api.codef.io";
  public static final String DEVELOPMENT_BASE = "https://development.codef.io";

  /** 인증 수단 문제(인증서 만료·비밀번호 오류·추가인증·connectedId 없음) — 재인증이 필요하다. */
  private static final Set<String> AUTH_CODE_PREFIXES = Set.of("CF-04", "CF-123", "CF-128",
      "CF-112");
  private static final Set<String> AUTH_CODES = Set.of("CF-03002");
  private static final Set<String> TOKEN_CODES = Set.of("CF-09990", "CF-09991", "CF-09992",
      "CF-09993", "CF-09994", "CF-00026");
  private static final Set<String> CONFIG_CODES = Set.of("CF-00004", "CF-00005", "CF-00013",
      "CF-00017", "CF-00401");

  private static final long TOKEN_SKEW_SECONDS = 300L;

  private final String baseUrl;
  private final String tokenUrl;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final Duration requestTimeout;

  private String cachedToken;
  private long tokenExpiresAtMillis;

  public CodefApiClient(String baseUrl, String tokenUrl, HttpClient http, ObjectMapper mapper,
      Duration requestTimeout) {
    this.baseUrl = baseUrl == null || baseUrl.isBlank() ? PRODUCTION_BASE : baseUrl;
    this.tokenUrl = tokenUrl == null || tokenUrl.isBlank() ? TOKEN_URL : tokenUrl;
    this.http = http;
    this.mapper = mapper == null ? new ObjectMapper() : mapper;
    this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(60) : requestTimeout;
  }

  public static CodefApiClient createDefault(String baseUrl) {
    // 수시입출 거래내역 상품의 타임아웃 안내값은 300초다. 기관 응답이 느릴 수 있어 요청 제한을 넉넉히 둔다.
    return new CodefApiClient(baseUrl, TOKEN_URL,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        new ObjectMapper(), Duration.ofSeconds(290));
  }

  /** 상품 호출. 성공 시 {@code data} 노드를 반환한다. */
  public JsonNode call(CodefCredentialProvider.Credentials credentials, String endpoint,
      Map<String, Object> body) {
    String token = token(credentials, false);
    HttpResponse<String> response = post(endpoint, token, body);
    JsonNode root = decode(response);
    if (response.statusCode() == 401 || TOKEN_CODES.contains(code(root))) {
      token = token(credentials, true);
      response = post(endpoint, token, body);
      root = decode(response);
    }
    if (response.statusCode() == 401) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "CODEF access token was rejected.");
    }
    if (response.statusCode() >= 500) {
      throw new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
          "CODEF returned HTTP " + response.statusCode() + ".");
    }
    String code = code(root);
    if ("CF-00000".equals(code)) {
      JsonNode data = root.path("data");
      return data.isMissingNode() ? mapper.createObjectNode() : data;
    }
    throw classify(code);
  }

  /** CODEF 결과 코드 → 수집 오류 분류. 원문 메시지는 싣지 않는다(코드만). */
  static EasyCollectionException classify(String code) {
    String safe = code == null || code.isBlank() ? "UNKNOWN" : code;
    if (AUTH_CODES.contains(safe)
        || AUTH_CODE_PREFIXES.stream().anyMatch(safe::startsWith)) {
      return new EasyCollectionException(EasyCollectionErrorCode.AUTH_REQUIRED,
          "Bank authentication must be renewed (" + safe + ").");
    }
    if (CONFIG_CODES.contains(safe) || TOKEN_CODES.contains(safe)) {
      return new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "CODEF contract or token configuration error (" + safe + ").");
    }
    if ("CF-00007".equals(safe) || "CF-13001".equals(safe)) {
      return new EasyCollectionException(EasyCollectionErrorCode.INVALID_DATA,
          "CODEF rejected the request parameters (" + safe + ").");
    }
    return new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
        "CODEF request failed (" + safe + ").");
  }

  private synchronized String token(CodefCredentialProvider.Credentials credentials,
      boolean forceRefresh) {
    if (credentials == null || !credentials.present()) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "CODEF client credentials are not configured.");
    }
    if (!forceRefresh && cachedToken != null && System.currentTimeMillis() < tokenExpiresAtMillis) {
      return cachedToken;
    }
    String basic = Base64.getEncoder().encodeToString(
        (credentials.clientId() + ":" + credentials.clientSecret())
            .getBytes(StandardCharsets.UTF_8));
    HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(tokenUrl))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/json")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Authorization", "Basic " + basic)
        .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=read",
            StandardCharsets.UTF_8))
        .build());
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "CODEF client credentials were rejected.");
    }
    if (response.statusCode() != 200) {
      throw new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
          "CODEF token request failed (HTTP " + response.statusCode() + ").");
    }
    JsonNode root = parse(response.body());
    String token = root.path("access_token").asText("");
    if (token.isBlank()) {
      throw new EasyCollectionException(EasyCollectionErrorCode.CONFIGURATION_ERROR,
          "CODEF token response had no access_token.");
    }
    long ttl = root.path("expires_in").asLong(604799L);
    cachedToken = token;
    tokenExpiresAtMillis = System.currentTimeMillis()
        + Math.max(60L, ttl - TOKEN_SKEW_SECONDS) * 1000L;
    return token;
  }

  private HttpResponse<String> post(String endpoint, String token, Map<String, Object> body) {
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (IOException e) {
      throw new EasyCollectionException(EasyCollectionErrorCode.INVALID_DATA,
          "CODEF request body could not be encoded.", e);
    }
    return send(HttpRequest.newBuilder(URI.create(baseUrl + endpoint))
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + token)
        .POST(HttpRequest.BodyPublishers.ofString(
            URLEncoder.encode(json, StandardCharsets.UTF_8), StandardCharsets.UTF_8))
        .build());
  }

  private JsonNode decode(HttpResponse<String> response) {
    String body = response.body() == null ? "" : response.body();
    if (body.isBlank()) {
      return mapper.createObjectNode();
    }
    String decoded = URLDecoder.decode(body, StandardCharsets.UTF_8);
    return parse(decoded);
  }

  private JsonNode parse(String text) {
    try {
      JsonNode node = mapper.readTree(text);
      return node == null ? mapper.createObjectNode() : node;
    } catch (IOException e) {
      throw new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
          "CODEF response was not valid JSON.", e);
    }
  }

  private static String code(JsonNode root) {
    return root == null ? null : root.path("result").path("code").asText(null);
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
          "CODEF network request failed.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EasyCollectionException(EasyCollectionErrorCode.PROVIDER_TEMPORARY,
          "CODEF request was interrupted.", e);
    }
  }
}
