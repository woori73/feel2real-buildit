package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** 합성 자료·H2 MySQL 모드. 네이버 호출은 전부 mock이며 실제 카페에 글을 올리지 않는다. */
class CafeServiceTest {
  static final AuthenticatedPrincipal ADMIN = new AuthenticatedPrincipal(1L, "operator", "ADMIN");
  CafeService cafe;
  CafeProperties config;
  NaverCafeClient naver;
  JdbcTemplate db;
  Map<String, Integer> boards;

  @BeforeEach
  void givenConnectedAdminAndSevenBoards() {
    var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    db = new JdbcTemplate(source);
    db.execute("CREATE TABLE users(user_id BIGINT PRIMARY KEY)");
    db.execute("INSERT INTO users VALUES(1),(2)");
    new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1109__newstart_cafe_publishing.sql")).execute(source);
    config = new CafeProperties(); config.setEnabled(true); config.setClientId("synthetic"); config.setClientSecret("synthetic");
    config.setTokenKeyBase64(Base64.getEncoder().encodeToString(new byte[32]));
    naver = mock(NaverCafeClient.class);
    cafe = new CafeService(db, JsonMapper.builder().build(), config, new CafeTokenCipher(config), naver, new DataSourceTransactionManager(source));
    boards = new LinkedHashMap<>(); int id = 100;
    for (String key : CafeService.BOARDS.keySet()) boards.put(key, id++);
    cafe.saveBoards(ADMIN, boards);
    when(naver.authorizationUrl(anyString())).thenAnswer(call -> call.getArgument(0));
    when(naver.exchange(anyString(), anyString())).thenReturn(new NaverCafeClient.Tokens("synthetic-access", "synthetic-refresh", 3600));
    cafe.callback(ADMIN, cafe.connect(ADMIN), "synthetic-code", null);
    when(naver.publish(anyString(), anyInt(), anyString(), anyString()))
        .thenReturn(new NaverCafeClient.Published(123L, "https://cafe.naver.com/ca-fe/cafes/31312978/articles/123"));
  }

  CafeService.Post post(String key) { return new CafeService.Post("cases", "개인워크아웃 사례", "출처와 시점을 구분한 합성 사례입니다.\n두 번째 줄", key); }

  @Test
  void givenSameRequest_whenRepeatedWithSameOrNewKey_thenOnlyOneNaverPost() {
    assertEquals("POSTED", cafe.publish(ADMIN, "grant", post("request-0001")).get("state"));
    assertEquals(true, cafe.publish(ADMIN, "grant", post("request-0001")).get("duplicate"));
    assertEquals(true, cafe.publish(ADMIN, "grant", post("request-0002")).get("duplicate"));
    verify(naver, times(1)).publish(anyString(), anyInt(), anyString(), anyString());
    assertEquals(1, cafe.history(ADMIN).size());
    assertEquals(106, boards.get("comparison"));
  }

  @Test
  void givenImagePost_whenRepeatedOrImageChanged_thenDeduplicatesAndRejectsClaimedKey() throws Exception {
    when(naver.publish(anyString(), anyInt(), anyString(), anyString(), anyList()))
        .thenReturn(new NaverCafeClient.Published(321L, "https://cafe.naver.com/ca-fe/cafes/31312978/articles/321"));
    var first = new CafeService.Post("cases", "이미지 안내", "본문", "image-request-001", List.of(CafeImageTest.image(0)));
    assertEquals("POSTED", cafe.publish(ADMIN, "grant", first).get("state"));
    assertEquals(true, cafe.publish(ADMIN, "grant", new CafeService.Post("cases", "이미지 안내", "본문", "image-request-002", first.images())).get("duplicate"));
    assertThrows(ResponseStatusException.class, () -> cafe.publish(ADMIN, "grant", new CafeService.Post(
        "cases", "이미지 안내", "본문", "image-request-001", List.of(CafeImageTest.image(1)))));
    verify(naver, times(1)).publish(anyString(), anyInt(), anyString(), anyString(), anyList());
    verify(naver, never()).publish(anyString(), anyInt(), anyString(), anyString());
  }

  @Test
  void givenImagePublishTimeout_whenRepeatedWithNewKey_thenPreservesUnknownWithoutResending() throws Exception {
    when(naver.publish(anyString(), anyInt(), anyString(), anyString(), anyList()))
        .thenThrow(new RuntimeException("synthetic timeout"));
    var images = List.of(CafeImageTest.image(0));
    assertThrows(ResponseStatusException.class, () -> cafe.publish(ADMIN, "grant", new CafeService.Post(
        "cases", "그림 안내", "본문", "image-timeout-001", images)));
    assertEquals("UNKNOWN", cafe.publish(ADMIN, "grant", new CafeService.Post(
        "cases", "그림 안내", "본문", "image-timeout-002", images)).get("state"));
    verify(naver, times(1)).publish(anyString(), anyInt(), anyString(), anyString(), anyList());
  }

  @Test
  void givenClaimedKey_whenPayloadChanges_thenConflictWithoutAnotherPost() {
    cafe.publish(ADMIN, "grant", post("request-0001"));
    var changed = new CafeService.Post("cases", "다른 제목", "다른 내용", "request-0001");
    assertEquals(409, assertThrows(ResponseStatusException.class, () -> cafe.publish(ADMIN, "grant", changed)).getStatusCode().value());
    verify(naver, times(1)).publish(anyString(), anyInt(), anyString(), anyString());
  }

  @Test
  void givenNetworkFailure_whenRepeated_thenUnknownIsPreservedAndNotResent() {
    when(naver.publish(anyString(), anyInt(), anyString(), anyString())).thenThrow(new RuntimeException("synthetic timeout"));
    assertThrows(ResponseStatusException.class, () -> cafe.publish(ADMIN, "grant", post("timeout-0001")));
    assertEquals("UNKNOWN", cafe.publish(ADMIN, "grant", post("timeout-0002")).get("state"));
    verify(naver, times(1)).publish(anyString(), anyInt(), anyString(), anyString());
  }

  @Test
  void givenConcurrentDuplicate_whenFirstIsInFlight_thenSecondReturnsPending() throws Exception {
    var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
    when(naver.publish(anyString(), anyInt(), anyString(), anyString())).thenAnswer(call -> {
      entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
      return new NaverCafeClient.Published(123L, "https://cafe.naver.com/ca-fe/cafes/31312978/articles/123");
    });
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<?> first = workers.submit(() -> cafe.publish(ADMIN, "grant", post("concurrent-01")));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertEquals("PENDING", cafe.publish(ADMIN, "grant", post("concurrent-02")).get("state"));
      } finally { release.countDown(); }
      first.get(5, TimeUnit.SECONDS);
    }
    verify(naver, times(1)).publish(anyString(), anyInt(), anyString(), anyString());
  }

  @Test
  void givenState_whenReplayedOrUsedByAnotherAdmin_thenRejected() {
    String state = cafe.connect(ADMIN);
    var other = new AuthenticatedPrincipal(2L, "other", "ADMIN");
    assertThrows(ResponseStatusException.class, () -> cafe.callback(other, state, "code", null));
    cafe.callback(ADMIN, state, "code", null);
    assertThrows(ResponseStatusException.class, () -> cafe.callback(ADMIN, state, "code", null));
  }

  @Test
  void givenDisconnectDuringTokenExchange_whenCallbackCompletes_thenCannotRestoreConnection() {
    String state = cafe.connect(ADMIN);
    when(naver.exchange(anyString(), anyString())).thenAnswer(call -> {
      cafe.disconnect(ADMIN);
      return new NaverCafeClient.Tokens("synthetic", "synthetic-refresh", 3600);
    });
    assertThrows(ResponseStatusException.class, () -> cafe.callback(ADMIN, state, "code", null));
    assertEquals(false, cafe.status(ADMIN).get("connected"));
  }

  @Test
  void givenDisconnectedOrDisabled_whenPublishing_thenNoNaverPost() {
    cafe.disconnect(ADMIN);
    assertThrows(ResponseStatusException.class, () -> cafe.publish(ADMIN, "grant", post("disconnected-01")));
    config.setEnabled(false);
    assertThrows(ResponseStatusException.class, () -> cafe.publish(ADMIN, "grant", post("disabled-00001")));
    verify(naver, never()).publish(anyString(), anyInt(), anyString(), anyString());
  }

  @Test
  void givenExpiredToken_whenPublishing_thenRefreshesWithoutDisclosure() {
    db.execute("UPDATE newstart_cafe_connections SET token_expires_at=TIMESTAMP '2020-01-01 00:00:00'");
    when(naver.refresh("synthetic-refresh")).thenReturn(new NaverCafeClient.Tokens("new-access", "", 3600));
    cafe.publish(ADMIN, "grant", post("refresh-0001"));
    verify(naver).publish(eq("new-access"), eq(boards.get("cases")), anyString(), anyString());
    assertFalse(cafe.status(ADMIN).toString().contains("synthetic-access"));
    assertFalse(cafe.history(ADMIN).toString().contains("new-access"));
    String encrypted = db.queryForObject("SELECT access_token_enc FROM newstart_cafe_connections WHERE owner_id=1", String.class);
    assertTrue(encrypted.startsWith("v1:")); assertFalse(encrypted.contains("new-access"));
  }

  @Test
  void givenUntrustedHtml_whenPrepared_thenScriptsAndUnsafeUrlsRemovedButKoreanAndLineBreaksRemain() {
    var p = CafeService.prepare(post("sanitized-01"));
    assertTrue(p.content().contains("<br>")); assertTrue(p.content().contains("출처"));
    var cleaned = CafeService.prepare(new CafeService.Post("cases", "제목", "<script>alert(1)</script><p>본문</p><a href='javascript:alert(1)'>링크</a><img src='https://example.com/private'>", "html-0001"));
    assertFalse(cleaned.content().contains("script")); assertFalse(cleaned.content().contains("<img"));
    assertThrows(ResponseStatusException.class, () -> CafeService.prepare(new CafeService.Post("arbitrary", "제목", "내용", "bad-00001")));
    var duplicateIds = new LinkedHashMap<>(boards); duplicateIds.put("cases", duplicateIds.get("repayment"));
    assertThrows(ResponseStatusException.class, () -> cafe.saveBoards(ADMIN, duplicateIds));
  }
}
