package f2r.tutor_solution.private_tutor_solution_back.domain.easy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 매통조 프로토콜 클라이언트 검증 — JDK 내장 HttpServer fake로만 검증한다.
 * 실제 외부 API 호출 없음, credential 없음.
 */
class CrefiaCardsalesClientTest {

  private static final CrefiaCredentialProvider.Credentials CREDS =
      new CrefiaCredentialProvider.Credentials("test-client-id", "test-client-secret");

  private HttpServer server;
  private String baseUrl;
  private final AtomicInteger tokenHits = new AtomicInteger();
  private volatile int tokenStatus = 200;
  private volatile String tokenBody = "{\"access_token\":\"fake-token-1\",\"expires_in\":86400}";
  private volatile int pageStatus = 200;
  private volatile String pageBody = "{\"items\":[]}";
  private volatile int pageDelayMillis;
  private final AtomicInteger pageHits = new AtomicInteger();

  private CrefiaCardsalesClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/oauth/token", exchange -> {
      tokenHits.incrementAndGet();
      byte[] body = tokenBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(tokenStatus, body.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(body);
      }
    });
    server.createContext("/approval", exchange -> {
      pageHits.incrementAndGet();
      String auth = exchange.getRequestHeaders().getFirst("Authorization");
      if (!"Bearer fake-token-1".equals(auth) && !"Bearer fake-token-2".equals(auth)) {
        byte[] body = "{\"error\":\"invalid_token\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(401, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
          os.write(body);
        }
        return;
      }
      if (pageDelayMillis > 0) {
        try {
          Thread.sleep(pageDelayMillis);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      byte[] body = pageBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(pageStatus, body.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(body);
      }
    });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    client = new CrefiaCardsalesClient(baseUrl, "/approval", "/approval", "/approval",
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
        new ObjectMapper(), Duration.ofSeconds(2));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private CrefiaCardsalesClient.DatasetQuery query() {
    return new CrefiaCardsalesClient.DatasetQuery("1234567890", null, null);
  }

  @Test
  @DisplayName("토큰 발급 성공 + 캐시 재사용 (2회 호출에 토큰 엔드포인트 1회 적중)")
  void issueToken_cached() {
    String first = client.issueToken(CREDS);
    String second = client.issueToken(CREDS);
    assertThat(first).isEqualTo("fake-token-1");
    assertThat(second).isEqualTo("fake-token-1");
    assertThat(tokenHits.get()).isEqualTo(1);
  }

  @Test
  @DisplayName("토큰 401 → CrefiaAuthException (스케줄러 NEEDS_REAUTH 분류 호환)")
  void issueToken_unauthorized() {
    tokenStatus = 401;
    assertThatThrownBy(() -> client.issueToken(CREDS))
        .isInstanceOf(CrefiaAuthException.class)
        .hasMessageContaining("401");
  }

  @Test
  @DisplayName("자격증명 없음 → IllegalStateException (조용히 빈 결과로 속이지 않음)")
  void issueToken_noCredentials() {
    assertThatThrownBy(() -> client.issueToken(
        new CrefiaCredentialProvider.Credentials(null, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("자격증명");
  }

  @Test
  @DisplayName("정상 조회: 레코드 파싱")
  void fetchApprovals_success() {
    pageBody = "{\"items\":[{\"승인번호\":\"A1\",\"승인금액\":\"10000\",\"거래일자\":\"2026-09-01\"}],\"totalCount\":1}";
    List<Map<String, String>> records = client.fetchApprovals(CREDS, query(), 500);
    assertThat(records).hasSize(1);
    assertThat(records.get(0)).containsEntry("승인번호", "A1");
  }

  @Test
  @DisplayName("빈 결과: 빈 리스트 반환")
  void fetchApprovals_empty() {
    pageBody = "{\"items\":[],\"totalCount\":0}";
    assertThat(client.fetchApprovals(CREDS, query(), 500)).isEmpty();
  }

  @Test
  @DisplayName("pagination: totalCount 기준 2페이지 집계")
  void fetchApprovals_pagination() {
    pageBody = "{\"items\":[{\"승인번호\":\"A1\"}],\"totalCount\":2,\"pageSize\":1}";
    CrefiaCardsalesClient paged = new CrefiaCardsalesClient(baseUrl, "/approval", "/approval",
        "/approval", HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
        new ObjectMapper(), Duration.ofSeconds(2));
    // pageSize=1, totalCount=2 → 2페이지 호출
    List<Map<String, String>> records = paged.fetchApprovals(CREDS, query(), 1);
    assertThat(records).hasSize(2);
    assertThat(pageHits.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("토큰 만료(401) 시 1회 재발급 후 재시도 성공")
  void fetchApprovals_tokenRefreshRetry() {
    // 1) 만료 토큰을 캐시에 적재
    tokenBody = "{\"access_token\":\"expired-token\",\"expires_in\":86400}";
    client.issueToken(CREDS);
    assertThat(tokenHits.get()).isEqualTo(1);
    // 2) 재발급 시 유효 토큰이 오도록 전환 → 조회는 401 1회 후 자동 복구되어야 함
    tokenBody = "{\"access_token\":\"fake-token-1\",\"expires_in\":86400}";
    pageBody = "{\"items\":[{\"승인번호\":\"A9\"}],\"totalCount\":1}";
    List<Map<String, String>> records = client.fetchApprovals(CREDS, query(), 500);
    assertThat(records).hasSize(1);
    assertThat(records.get(0)).containsEntry("승인번호", "A9");
    assertThat(tokenHits.get()).isEqualTo(2);
  }

  @Test
  @DisplayName("지속 401 → CrefiaAuthException")
  void fetchApprovals_persistent401() {
    tokenBody = "{\"access_token\":\"bad-token\",\"expires_in\":86400}";
    assertThatThrownBy(() -> client.fetchApprovals(CREDS, query(), 500))
        .isInstanceOf(CrefiaAuthException.class)
        .hasMessageContaining("401");
  }

  @Test
  @DisplayName("5xx 1회 후 성공 → 재시도 동작")
  void fetchApprovals_retryOn500() {
    pageStatus = 500;
    pageBody = "{\"items\":[{\"승인번호\":\"A5\"}],\"totalCount\":1}";
    Thread flipper = new Thread(() -> {
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      pageStatus = 200;
    });
    flipper.setDaemon(true);
    flipper.start();
    List<Map<String, String>> records = client.fetchApprovals(CREDS, query(), 500);
    assertThat(records).hasSize(1);
  }

  @Test
  @DisplayName("지속 5xx → CrefiaProviderException (인증 오류로 오분류하지 않음)")
  void fetchApprovals_persistent500() {
    pageStatus = 500;
    pageBody = "error";
    assertThatThrownBy(() -> client.fetchApprovals(CREDS, query(), 500))
        .isInstanceOf(CrefiaProviderException.class)
        .hasMessageNotContaining("401");
  }

  @Test
  @DisplayName("malformed 응답 → CrefiaProviderException")
  void fetchApprovals_malformed() {
    pageStatus = 200;
    pageBody = "this is not json {{{";
    assertThatThrownBy(() -> client.fetchApprovals(CREDS, query(), 500))
        .isInstanceOf(CrefiaProviderException.class)
        .hasMessageContaining("파싱 실패");
  }

  @Test
  @DisplayName("timeout → CrefiaProviderException")
  void fetchApprovals_timeout() {
    pageDelayMillis = 3000;
    pageBody = "{\"items\":[],\"totalCount\":0}";
    assertThatThrownBy(() -> client.fetchApprovals(CREDS, query(), 500))
        .isInstanceOf(CrefiaProviderException.class);
  }

  @Test
  @DisplayName("provenance: 메시지에 secret 원문이 포함되지 않음")
  void noSecretLeak() {
    tokenStatus = 401;
    try {
      client.issueToken(CREDS);
    } catch (CrefiaAuthException e) {
      assertThat(e.getMessage()).doesNotContain("test-client-secret");
    }
    tokenStatus = 200;
    pageStatus = 500;
    try {
      client.fetchApprovals(CREDS, query(), 500);
    } catch (CrefiaProviderException e) {
      assertThat(e.getMessage()).doesNotContain("test-client-secret");
    }
  }
}
