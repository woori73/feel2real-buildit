package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여신금융협회 매통조 OpenAPI 프로토콜 클라이언트 (카드매출 자동수집용).
 *
 * <p>근거 — 협회 공식 포털(openapi.crefia.or.kr) 공개 명세:
 * <ul>
 *   <li>토큰: {@code POST {base}/oauth/token}, {@code Authorization: Basic base64(client_id:client_secret)},
 *       body {@code grant_type=client_credentials}, 만료 86400초</li>
 *   <li>데이터: Bearer 토큰 + paging 방식 (승인/매입/입금), 게이트웨이 {@code openapi.crefia.or.kr:24002}</li>
 *   <li>조회 전 가맹점 "정보 전송 이용 동의" 필수 (자체인증기관 동의/취소 API 별도)</li>
 * </ul>
 * 데이터셋별 상세 경로·응답 필드명은 이용기관 가입(로그인) 후 열람 구간이므로,
 * 경로는 생성자 주입으로 교체 가능하고 파싱은 관용적(lenient)으로 처리한다.
 * 이용기관 승인 전에는 실제 호출이 발생하지 않는다 — 자격증명 없으면 어댑터가 먼저 차단한다.
 *
 * <p>스레드 안전: 토큰 캐시는 synchronized로 갱신한다. 외부 호출은 DB 트랜잭션 밖에서
 * 수행되어야 한다 (호출자는 {@code EasyCollectionIngestService} 경계를 따른다).
 */
public class CrefiaCardsalesClient {

  public static final String DEFAULT_BASE_URL = "https://openapi.crefia.or.kr:24002";
  public static final String TOKEN_PATH = "/oauth/token";

  /** 데이터셋 기본 경로 — 가입 후 자료실 JAVA 샘플 기준으로 확정한다. */
  public static final String APPROVAL_PATH = "/oauth2/approval/page";
  public static final String PURCHASE_PATH = "/oauth2/purchase/page";
  public static final String DEPOSIT_PATH = "/oauth2/deposit/page";

  private static final long DEFAULT_TOKEN_TTL_SECONDS = 86400L;
  private static final long TOKEN_SKEW_SECONDS = 300L;
  private static final int MAX_PAGES_PER_FETCH = 100;

  private final String baseUrl;
  private final String approvalPath;
  private final String purchasePath;
  private final String depositPath;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final Duration requestTimeout;

  private String cachedToken;
  private long tokenExpiresAtMillis;

  public CrefiaCardsalesClient(String baseUrl, HttpClient http, ObjectMapper mapper,
      Duration requestTimeout) {
    this(baseUrl, APPROVAL_PATH, PURCHASE_PATH, DEPOSIT_PATH, http, mapper, requestTimeout);
  }

  public CrefiaCardsalesClient(String baseUrl, String approvalPath, String purchasePath,
      String depositPath, HttpClient http, ObjectMapper mapper, Duration requestTimeout) {
    this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : DEFAULT_BASE_URL;
    this.approvalPath = approvalPath;
    this.purchasePath = purchasePath;
    this.depositPath = depositPath;
    this.http = http;
    this.mapper = mapper != null ? mapper : new ObjectMapper();
    this.requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(15);
  }

  /** 기본 클라이언트 (운영 base URL, connect 5초). */
  public static CrefiaCardsalesClient createDefault() {
    return new CrefiaCardsalesClient(DEFAULT_BASE_URL,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
        new ObjectMapper(), Duration.ofSeconds(15));
  }

  /** 조회 조건 — 가맹점 식별 + 기간. */
  public record DatasetQuery(String merchantNo, LocalDate from, LocalDate to) {}

  /** 한 페이지 결과 — 레코드(문자열 맵) + 다음 페이지 존재 여부. */
  public record CrefiaPage(List<Map<String, String>> records, boolean hasMore) {}

  /**
   * OAuth2 client_credentials 토큰 발급 (캐시 1일).
   *
   * @throws CrefiaAuthException 401/403 또는 토큰 파싱 실패
   * @throws CrefiaProviderException 타임아웃·5xx·네트워크 실패
   */
  public synchronized String issueToken(CrefiaCredentialProvider.Credentials credentials) {
    if (credentials == null || !credentials.present()) {
      throw new IllegalStateException(
          "매통조 이용기관 자격증명이 없습니다. 협회 이용기관 승인 후 CREFIA_CLIENT_ID/SECRET을 등록하세요.");
    }
    if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAtMillis) {
      return cachedToken;
    }
    String basic = Base64.getEncoder().encodeToString(
        (credentials.clientId() + ":" + credentials.clientSecret())
            .getBytes(StandardCharsets.UTF_8));
    String body = "grant_type=client_credentials";
    HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(baseUrl + TOKEN_PATH))
        .timeout(requestTimeout)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Authorization", "Basic " + basic)
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
        .build());
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new CrefiaAuthException(
          "매통조 인증 실패(" + response.statusCode() + "): 이용기관 승인·client_secret을 확인하세요.");
    }
    if (response.statusCode() != 200) {
      throw new CrefiaProviderException(
          "매통조 토큰 발급 실패(HTTP " + response.statusCode() + ")");
    }
    JsonNode root = parseJson(response.body(), "토큰 응답");
    String token = textOf(root, "access_token");
    if (token == null || token.isBlank()) {
      throw new CrefiaAuthException("매통조 인증 실패: 토큰 응답에 access_token이 없습니다.");
    }
    long ttl = longOf(root, "expires_in", DEFAULT_TOKEN_TTL_SECONDS);
    cachedToken = token;
    tokenExpiresAtMillis = System.currentTimeMillis()
        + Math.max(60, ttl - TOKEN_SKEW_SECONDS) * 1000L;
    return token;
  }

  /** 캐시 토큰 무효화 — 401 발생 시 1회 재발급 후 재시도하기 위해 사용한다. */
  public synchronized void invalidateToken() {
    cachedToken = null;
    tokenExpiresAtMillis = 0L;
  }

  public List<Map<String, String>> fetchApprovals(
      CrefiaCredentialProvider.Credentials credentials, DatasetQuery query, int pageSize) {
    return fetchAll(credentials, approvalPath, query, pageSize);
  }

  public List<Map<String, String>> fetchPurchases(
      CrefiaCredentialProvider.Credentials credentials, DatasetQuery query, int pageSize) {
    return fetchAll(credentials, purchasePath, query, pageSize);
  }

  public List<Map<String, String>> fetchDeposits(
      CrefiaCredentialProvider.Credentials credentials, DatasetQuery query, int pageSize) {
    return fetchAll(credentials, depositPath, query, pageSize);
  }

  private List<Map<String, String>> fetchAll(
      CrefiaCredentialProvider.Credentials credentials, String path, DatasetQuery query,
      int pageSize) {
    List<Map<String, String>> all = new ArrayList<>();
    int pageIndex = 1;
    boolean refreshed = false;
    while (pageIndex <= MAX_PAGES_PER_FETCH) {
      String token = issueToken(credentials);
      CrefiaPage page;
      try {
        page = fetchPage(token, path, query, pageIndex, pageSize);
      } catch (CrefiaAuthException e) {
        if (refreshed) {
          throw e;
        }
        refreshed = true;
        invalidateToken();
        continue;
      }
      all.addAll(page.records());
      if (!page.hasMore()) {
        return all;
      }
      pageIndex++;
    }
    throw new CrefiaProviderException(
        "매통조 페이징 상한(" + MAX_PAGES_PER_FETCH + "페이지) 초과 — 조회 기간을 좁히세요.");
  }

  /** 단일 페이지 조회 — package-private (테스트에서 fake 서버로 검증). */
  CrefiaPage fetchPage(String token, String path, DatasetQuery query, int pageIndex,
      int pageSize) {
    String url = baseUrl + path + "?pageIndex=" + pageIndex + "&pageSize=" + pageSize;
    if (query != null && query.merchantNo() != null) {
      url += "&merNo=" + URLEncoder.encode(query.merchantNo(), StandardCharsets.UTF_8);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    if (query != null) {
      payload.put("merchantNo", query.merchantNo());
      payload.put("from", query.from() == null ? null : query.from().toString());
      payload.put("to", query.to() == null ? null : query.to().toString());
    }
    String jsonBody;
    try {
      jsonBody = mapper.writeValueAsString(payload);
    } catch (IOException e) {
      throw new CrefiaProviderException("매통조 요청 생성 실패", e);
    }
    HttpResponse<String> response = sendWithRetry(HttpRequest.newBuilder(URI.create(url))
        .timeout(requestTimeout)
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + token)
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
        .build());
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      throw new CrefiaAuthException(
          "매통조 인증 실패(" + response.statusCode() + " Unauthorized): 토큰·가맹점 동의를 확인하세요.");
    }
    if (response.statusCode() != 200) {
      throw new CrefiaProviderException(
          "매통조 조회 실패(HTTP " + response.statusCode() + ")");
    }
    return parsePage(response.body(), pageIndex, pageSize);
  }

  private CrefiaPage parsePage(String body, int pageIndex, int pageSize) {
    JsonNode root = parseJson(body, "조회 응답");
    JsonNode array = firstArray(root, "items", "list", "data", "rows", "resultList", "result");
    List<Map<String, String>> records = new ArrayList<>();
    if (array != null && array.isArray()) {
      for (JsonNode node : array) {
        Map<String, String> row = new LinkedHashMap<>();
        node.fields().forEachRemaining(e ->
            row.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString()));
        records.add(row);
      }
    }
    boolean hasMore = booleanOf(root, "hasMore", false)
        || booleanOf(root, "has_next", false);
    long totalCount = longOf(root, "totalCount", longOf(root, "total", -1L));
    if (totalCount >= 0) {
      hasMore = (long) pageIndex * pageSize < totalCount;
    }
    long totalPages = longOf(root, "totalPages", -1L);
    if (totalPages > 0) {
      hasMore = pageIndex < totalPages;
    }
    return new CrefiaPage(records, hasMore && !records.isEmpty());
  }

  private HttpResponse<String> sendWithRetry(HttpRequest request) {
    try {
      HttpResponse<String> response = send(request);
      if (response.statusCode() >= 500) {
        sleepQuietly(500L);
        response = send(request);
      }
      return response;
    } catch (CrefiaProviderException e) {
      // 전송 레벨 실패(타임아웃·연결 실패)는 1회 재시도
      sleepQuietly(500L);
      return send(request);
    }
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new CrefiaProviderException(
          "매통조 호출 실패(네트워크·타임아웃): " + e.getClass().getSimpleName(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CrefiaProviderException("매통조 호출 중단됨", e);
    }
  }

  private JsonNode parseJson(String body, String what) {
    try {
      return mapper.readTree(body == null ? "" : body);
    } catch (IOException e) {
      throw new CrefiaProviderException("매통조 " + what + " 파싱 실패(비정상 응답)", e);
    }
  }

  private static JsonNode firstArray(JsonNode root, String... keys) {
    for (String key : keys) {
      JsonNode node = root.path(key);
      if (node.isArray()) {
        return node;
      }
    }
    if (root.isArray()) {
      return root;
    }
    return null;
  }

  private static String textOf(JsonNode root, String key) {
    JsonNode node = root.path(key);
    return node.isValueNode() ? node.asText(null) : null;
  }

  private static long longOf(JsonNode root, String key, long def) {
    JsonNode node = root.path(key);
    if (node.isNumber()) {
      return node.asLong(def);
    }
    if (node.isTextual()) {
      try {
        return Long.parseLong(node.asText().trim());
      } catch (NumberFormatException e) {
        return def;
      }
    }
    return def;
  }

  private static boolean booleanOf(JsonNode root, String key, boolean def) {
    JsonNode node = root.path(key);
    if (node.isBoolean()) {
      return node.asBoolean(def);
    }
    if (node.isTextual()) {
      String v = node.asText().trim().toLowerCase();
      if ("true".equals(v) || "y".equals(v) || "1".equals(v)) {
        return true;
      }
      if ("false".equals(v) || "n".equals(v) || "0".equals(v)) {
        return false;
      }
    }
    return def;
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
