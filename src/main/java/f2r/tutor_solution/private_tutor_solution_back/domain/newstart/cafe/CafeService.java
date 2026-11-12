package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.cafe;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** 카페 한 곳의 명시적으로 설정한 게시판에만 등록한다. 기존 글 수정·삭제는 제공하지 않는다. */
@Service
public class CafeService {
  public static final long CAFE_ID = 31312978L;
  public static final Map<String, String> BOARDS;
  static {
    var names = new LinkedHashMap<String, String>();
    names.put("getting-started", "처음 시작하기");
    names.put("debt-reduction", "감면율과 채권");
    names.put("collection", "추심·압류 상담");
    names.put("assets-income", "재산·소득 평가");
    names.put("repayment", "약정과 상환 관리");
    names.put("cases", "실제 진행 사례");
    names.put("comparison", "새출발기금과 비교");
    BOARDS = Collections.unmodifiableMap(names);
  }
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final CafeProperties config;
  private final CafeTokenCipher cipher;
  private final NaverCafeClient naver;
  private final TransactionTemplate tx;

  public CafeService(JdbcTemplate db, ObjectMapper json, CafeProperties config, CafeTokenCipher cipher,
      NaverCafeClient naver, PlatformTransactionManager transactions) {
    this.db = db; this.json = json; this.config = config; this.cipher = cipher; this.naver = naver;
    this.tx = new TransactionTemplate(transactions);
  }

  public record Post(String boardKey, String title, String content, String idempotencyKey) {}
  record Prepared(String boardKey, String title, String content, String keyHash, String payloadHash) {}

  static ResponseStatusException error(int status, String reason) {
    return new ResponseStatusException(HttpStatus.valueOf(status), reason);
  }

  public static void admin(AuthenticatedPrincipal p) {
    if (p == null || !"ADMIN".equals(p.role())) throw error(403, "cafe_admin_required");
  }

  private void ready() {
    if (!config.isEnabled()) throw error(503, "cafe_disabled");
    if (config.getClientId().isBlank() || config.getClientSecret().isBlank() || !cipher.configured())
      throw error(503, "cafe_server_setup_required");
  }

  public Map<String, Object> status(AuthenticatedPrincipal p) {
    admin(p);
    var row = connection(p.userId(), false);
    var boards = boardIds(row);
    var missing = new ArrayList<String>();
    if (!config.isEnabled()) missing.add("기능 활성화");
    if (config.getClientId().isBlank() || config.getClientSecret().isBlank()) missing.add("네이버 앱 인증정보");
    if (!cipher.configured()) missing.add("토큰 암호화 키");
    return Map.of("cafeId", CAFE_ID, "connected", row.get("access_token_enc") != null,
        "serverReady", missing.isEmpty(), "missing", missing, "callbackUrl", config.callback(),
        "settingsUrl", config.base() + "/admin/newstart/cafe", "boards",
        BOARDS.entrySet().stream().map(e -> Map.of("key", e.getKey(), "name", e.getValue(),
            "menuId", boards.getOrDefault(e.getKey(), 0))).toList());
  }

  public void saveBoards(AuthenticatedPrincipal p, Map<String, Integer> boards) {
    admin(p);
    if (boards == null || !boards.keySet().equals(BOARDS.keySet())
        || boards.values().stream().anyMatch(id -> id == null || id < 1 || id > 1000000)
        || new HashSet<>(boards.values()).size() != BOARDS.size())
      throw error(400, "seven_unique_board_ids_required");
    ensureConnection(p.userId());
    db.update("UPDATE newstart_cafe_connections SET board_ids=?,updated_at=? WHERE owner_id=?",
        json.writeValueAsString(boards), now(), p.userId());
  }

  public String connect(AuthenticatedPrincipal p) {
    admin(p); ready();
    ensureConnection(p.userId());
    byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update("UPDATE newstart_cafe_connections SET state_hash=?,state_expires_at=?,connection_epoch=? WHERE owner_id=?",
        hash(state), Timestamp.from(Instant.now().plusSeconds(600)), UUID.randomUUID().toString(), p.userId());
    return naver.authorizationUrl(state);
  }

  public void callback(AuthenticatedPrincipal p, String state, String code, String providerError) {
    admin(p); ready();
    if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) throw error(400, "cafe_oauth_state_invalid");
    // 한 번만 소비하고 커밋한 뒤 네이버에 요청한다. 실패한 code/state도 재사용하지 않는다.
    String pendingEpoch = tx.execute(ignored -> {
      var pending = connection(p.userId(), true);
      int consumed = db.update("UPDATE newstart_cafe_connections SET state_hash=NULL,state_expires_at=NULL"
          + " WHERE owner_id=? AND state_hash=? AND state_expires_at>?", p.userId(), hash(state), now());
      if (consumed != 1) throw error(400, "cafe_oauth_state_invalid");
      return (String) pending.get("connection_epoch");
    });
    if (providerError != null || code == null || code.isBlank() || code.length() > 4096)
      throw error(400, "cafe_oauth_not_completed");
    var tokens = naver.exchange(code, state);
    if (tokens.refresh().isBlank() || tokens.refresh().length() > 8192)
      throw error(502, "naver_auth_failed_reconnect");
    // 연결 해제 또는 다른 연결 시도가 token 교환과 겹치면 오래된 결과가 덮어쓰지 않게 한다.
    var epoch = UUID.randomUUID().toString();
    int linked = db.update("UPDATE newstart_cafe_connections SET access_token_enc=?,refresh_token_enc=?,"
        + "token_expires_at=?,connection_epoch=?,updated_at=? WHERE owner_id=? AND connection_epoch=? AND state_hash IS NULL",
        cipher.encrypt(p.userId(), tokens.access()), cipher.encrypt(p.userId(), tokens.refresh()),
        Timestamp.from(Instant.now().plusSeconds(tokens.expiresIn())), epoch, now(), p.userId(), pendingEpoch);
    if (linked != 1) throw error(409, "cafe_connection_changed_retry");
  }

  public void disconnect(AuthenticatedPrincipal p) {
    admin(p);
    db.update("UPDATE newstart_cafe_connections SET access_token_enc=NULL,refresh_token_enc=NULL,"
        + "token_expires_at=NULL,state_hash=NULL,state_expires_at=NULL,connection_epoch=?,updated_at=? WHERE owner_id=?",
        UUID.randomUUID().toString(), now(), p.userId());
  }

  public Map<String, Object> publish(AuthenticatedPrincipal p, String grantId, Post post) {
    admin(p); ready();
    Prepared prepared = prepare(post);
    var connection = connection(p.userId(), false);
    Integer menuId = boardIds(connection).get(prepared.boardKey());
    if (menuId == null) throw error(409, "cafe_board_setup_required");
    // 성공/결과불명 요청은 네이버에 다시 보내지 않는다. 다른 키로 우회해도 내용 해시로 차단한다.
    var old = existing(p.userId(), menuId, prepared);
    if (!old.isEmpty()) return prior(old, prepared);
    String token = access(p.userId()); // 갱신 실패는 게시 요청을 만들기 전에 종료한다.
    String id = UUID.randomUUID().toString();
    try {
      tx.executeWithoutResult(ignored -> {
        var current = connection(p.userId(), true);
        if (current.get("access_token_enc") == null
            || !Objects.equals(connection.get("connection_epoch"), current.get("connection_epoch"))
            || !Objects.equals(menuId, boardIds(current).get(prepared.boardKey())))
          throw error(409, "cafe_connection_changed_retry");
        Instant dayStart = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
            .atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toInstant();
        Long count = db.queryForObject("SELECT COUNT(*) FROM newstart_cafe_publications WHERE owner_id=? AND created_at>=?",
            Long.class, p.userId(), Timestamp.from(dayStart));
        if (count != null && count >= 180) throw error(429, "cafe_daily_limit");
        db.update("INSERT INTO newstart_cafe_publications(id,owner_id,grant_id,cafe_id,menu_id,board_key,"
            + "key_hash,payload_hash,title,content,state,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING',?,?)",
            id, p.userId(), grantId, CAFE_ID, menuId, prepared.boardKey(), prepared.keyHash(),
            prepared.payloadHash(), prepared.title(), prepared.content(), now(), now());
      });
    } catch (DuplicateKeyException e) {
      return prior(existing(p.userId(), menuId, prepared), prepared);
    }
    try {
      var result = naver.publish(token, menuId, prepared.title(), prepared.content());
      db.update("UPDATE newstart_cafe_publications SET state='POSTED',article_id=?,article_url=?,updated_at=? WHERE id=?",
          result.articleId(), result.articleUrl(), now(), id);
      return Map.of("id", id, "state", "POSTED", "articleUrl", result.articleUrl(), "duplicate", false);
    } catch (RuntimeException e) {
      // 타임아웃·오류 응답·DB 결과 저장 실패 모두 실제 게시 여부를 단정하지 않는다.
      db.update("UPDATE newstart_cafe_publications SET state='UNKNOWN',updated_at=? WHERE id=? AND state='PENDING'", now(), id);
      throw error(502, "cafe_publish_result_unknown_check_cafe_before_retry");
    }
  }

  public List<Map<String, Object>> history(AuthenticatedPrincipal p) {
    admin(p);
    return db.queryForList("SELECT id,board_key,title,state,article_url,created_at FROM newstart_cafe_publications"
        + " WHERE owner_id=? ORDER BY created_at DESC LIMIT 100", p.userId());
  }

  private String access(long owner) {
    return tx.execute(ignored -> {
      var row = connection(owner, true);
      if (row.get("access_token_enc") == null) throw error(409, "cafe_connect_naver_first");
      if (((Timestamp) row.get("token_expires_at")).toInstant().isAfter(Instant.now().plusSeconds(60)))
        return cipher.decrypt(owner, (String) row.get("access_token_enc"));
      var tokens = naver.refresh(cipher.decrypt(owner, (String) row.get("refresh_token_enc")));
      String refresh = tokens.refresh().isBlank() ? (String) row.get("refresh_token_enc") : cipher.encrypt(owner, tokens.refresh());
      db.update("UPDATE newstart_cafe_connections SET access_token_enc=?,refresh_token_enc=?,token_expires_at=?,updated_at=? WHERE owner_id=?",
          cipher.encrypt(owner, tokens.access()), refresh, Timestamp.from(Instant.now().plusSeconds(tokens.expiresIn())), now(), owner);
      return tokens.access();
    });
  }

  private List<Map<String, Object>> existing(long owner, int menuId, Prepared p) {
    return db.queryForList("SELECT id,key_hash,payload_hash,state,article_url FROM newstart_cafe_publications"
        + " WHERE (owner_id=? AND key_hash=?) OR (cafe_id=? AND menu_id=? AND payload_hash=?) ORDER BY created_at",
        owner, p.keyHash(), CAFE_ID, menuId, p.payloadHash());
  }

  private Map<String, Object> prior(List<Map<String, Object>> rows, Prepared p) {
    if (rows.isEmpty()) throw error(409, "cafe_request_in_progress");
    if (rows.stream().anyMatch(r -> Objects.equals(r.get("key_hash"), p.keyHash())
        && !Objects.equals(r.get("payload_hash"), p.payloadHash()))) throw error(409, "cafe_idempotency_conflict");
    var row = rows.getFirst();
    return Map.of("id", row.get("id"), "state", row.get("state"),
        "articleUrl", Objects.toString(row.get("article_url"), ""), "duplicate", true);
  }

  static Prepared prepare(Post p) {
    if (p == null || !BOARDS.containsKey(p.boardKey()) || p.title() == null || p.content() == null
        || p.idempotencyKey() == null || !p.idempotencyKey().matches("[A-Za-z0-9._:-]{8,120}"))
      throw error(400, "invalid_cafe_post");
    String title = p.title().strip();
    if (title.isBlank() || title.length() > 150 || title.chars().anyMatch(c -> c < 32 || c == 127)
        || p.content().isBlank() || p.content().length() > 8000) throw error(400, "invalid_cafe_post");
    String input = p.content();
    // 일반 텍스트도 줄바꿈이 유지되도록 처리. 스크립트·스타일·이미지·폼은 받지 않는다.
    if (!input.matches("(?s).*<[A-Za-z/][^>]*>.*")) input = org.jsoup.nodes.Entities.escape(input).replace("\n", "<br>");
    var safe = Safelist.basic().addTags("h2", "h3", "table", "thead", "tbody", "tr", "th", "td")
        .removeProtocols("a", "href", "ftp", "mailto");
    String content = Jsoup.clean(input, safe);
    if (Jsoup.parse(content).text().isBlank()) throw error(400, "empty_cafe_content");
    return new Prepared(p.boardKey(), title, content, hash(p.idempotencyKey()), hash(p.boardKey() + "\n" + title + "\n" + content));
  }

  private void ensureConnection(long owner) {
    try { db.update("INSERT INTO newstart_cafe_connections(owner_id,board_ids,connection_epoch,updated_at) VALUES(?,'{}',?,?)", owner, UUID.randomUUID().toString(), now()); }
    catch (DuplicateKeyException ignored) { /* 기존 연결·게시판 설정은 보존. */ }
  }

  private Map<String, Object> connection(long owner, boolean lock) {
    var rows = db.queryForList("SELECT * FROM newstart_cafe_connections WHERE owner_id=?" + (lock ? " FOR UPDATE" : ""), owner);
    return rows.isEmpty() ? Map.of() : rows.getFirst();
  }

  private Map<String, Integer> boardIds(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Integer>();
    if (row.get("board_ids") == null) return result;
    json.readTree((String) row.get("board_ids")).properties().forEach(e -> {
      if (BOARDS.containsKey(e.getKey()) && e.getValue().isIntegralNumber()) result.put(e.getKey(), e.getValue().asInt());
    });
    return result;
  }

  static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private static Timestamp now() { return Timestamp.from(Instant.now()); }
}
