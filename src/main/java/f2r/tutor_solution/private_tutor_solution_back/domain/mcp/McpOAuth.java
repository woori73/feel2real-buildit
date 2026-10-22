package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.AdminSessionValidator;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class McpOAuth {
  private final JdbcTemplate db;
  private final UserRepository users;
  private final AdminSessionValidator stamps;
  private final ObjectMapper json;

  @Value("${app.mcp.enabled:false}")
  private boolean enabled;

  @Value("${app.mcp.public-base-url:https://feel2real.co.kr}")
  private String base;

  @Value("${app.mcp.redirect-uris:https://claude.ai/api/mcp/auth_callback}")
  private List<String> redirects;

  public static final Set<String> SCOPES =
      Collections.unmodifiableSet(
          new TreeSet<>(
              Set.of(
                  "catalog:read",
                  "studio:read",
                  "academy:read",
                  "easy:read",
                  "admissions:read",
                  "wiki:read",
                  "grammar:read",
                  "buildit:read",
                  "chart:read",
                  "newstart:read",
                  "members:read",
                  "content:read",
                  "learning:read",
                  "analytics:read",
                  "wemade:read",
                  "choonggi:read")));

  public record Identity(String grantId, AuthenticatedPrincipal principal, Set<String> scopes) {}

  public void enabled() {
    if (!enabled) throw error(503, "MCP_DISABLED");
  }

  public String base() {
    return base.replaceAll("/+$", "");
  }

  public String resource() {
    return base() + "/mcp";
  }

  static String random() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String challenge(String s) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(HexFormat.of().parseHex(hash(s)));
  }

  static ResponseStatusException error(int status, String text) {
    return new ResponseStatusException(HttpStatus.valueOf(status), text);
  }

  public Map<String, Object> metadata() {
    enabled();
    return Map.of(
        "issuer",
        base(),
        "authorization_endpoint",
        base() + "/mcp/oauth/authorize",
        "token_endpoint",
        base() + "/mcp/oauth/token",
        "registration_endpoint",
        base() + "/mcp/oauth/register",
        "revocation_endpoint",
        base() + "/mcp/oauth/revoke",
        "response_types_supported",
        List.of("code"),
        "grant_types_supported",
        List.of("authorization_code", "refresh_token"),
        "code_challenge_methods_supported",
        List.of("S256"),
        "token_endpoint_auth_methods_supported",
        List.of("none"),
        "scopes_supported",
        scopes());
  }

  public List<String> scopes() {
    var all = new ArrayList<>(SCOPES);
    all.add("offline_access");
    all.add("originals:read");
    return all;
  }

  boolean allowedRedirect(String value) {
    if (value == null || !redirects.contains(value)) return false;
    // Callback IDs come from the product's management page. No host/path wildcard registration.
    return value.equals("https://claude.ai/api/mcp/auth_callback")
        || value.matches("https://chatgpt\\.com/connector/oauth/[A-Za-z0-9_-]{1,200}")
        || value.equals("https://chatgpt.com/connector_platform_oauth_redirect");
  }

  @Transactional
  public Map<String, Object> register(Map<String, Object> body) {
    enabled();
    if (!"none".equals(body.getOrDefault("token_endpoint_auth_method", "none")))
      throw error(400, "invalid_client_metadata");
    Object r = body.get("redirect_uris");
    if (!(r instanceof List<?> list)
        || list.isEmpty()
        || list.size() > 5
        || list.stream().anyMatch(v -> !(v instanceof String) || !allowedRedirect((String) v)))
      throw error(400, "invalid_redirect_uri");
    if (db.queryForObject("SELECT COUNT(*) FROM mcp_clients", Long.class) > 5000)
      throw error(429, "registration_limit");
    String name = String.valueOf(body.getOrDefault("client_name", "MCP client"));
    if (name.length() > 100) throw error(400, "invalid_client_metadata");
    String id = random();
    db.update(
        "INSERT INTO mcp_clients(id,name,redirect_uris) VALUES (?,?,?)",
        id,
        name,
        json.writeValueAsString(r));
    return Map.of(
        "client_id",
        id,
        "client_name",
        name,
        "redirect_uris",
        r,
        "token_endpoint_auth_method",
        "none",
        "grant_types",
        List.of("authorization_code", "refresh_token"),
        "response_types",
        List.of("code"));
  }

  public Map<String, String> validateAuthorization(Map<String, String> q) {
    enabled();
    String client = q.get("client_id"),
        redirect = q.get("redirect_uri"),
        scope = q.getOrDefault("scope", "");
    var c = db.queryForList("SELECT * FROM mcp_clients WHERE id=?", client);
    if (c.isEmpty()
        || !allowedRedirect(redirect)
        || !json.readTree((String) c.getFirst().get("redirect_uris"))
            .valueStream()
            .anyMatch(n -> redirect.equals(n.asText()))) throw error(400, "invalid_redirect_uri");
    if (!"code".equals(q.get("response_type"))
        || !"S256".equals(q.get("code_challenge_method"))
        || !q.getOrDefault("code_challenge", "").matches("[A-Za-z0-9_-]{43}")
        || q.getOrDefault("state", "").length() < 1
        || q.getOrDefault("state", "").length() > 1000
        || !resource().equals(q.get("resource"))) throw error(400, "invalid_request");
    for (String s : scope.split(" +"))
      if (!s.isBlank() && !scopes().contains(s)) throw error(400, "invalid_scope");
    return Map.of(
        "client_id",
        client,
        "client_name",
        (String) c.getFirst().get("name"),
        "redirect_uri",
        redirect,
        "code_challenge",
        q.get("code_challenge"),
        "resource",
        resource(),
        "state",
        q.get("state"),
        "scope",
        scope);
  }

  @Transactional
  public String pending(AuthenticatedPrincipal p, Map<String, String> q) {
    if (p == null) throw error(401, "login_required");
    String nonce = random();
    db.update(
        "INSERT INTO mcp_pending(hash,user_id,request_json,expires_at) VALUES (?,?,?,?)",
        hash(nonce),
        p.userId(),
        json.writeValueAsString(validateAuthorization(q)),
        Timestamp.from(Instant.now().plusSeconds(600)));
    return nonce;
  }

  @Transactional
  public String authorize(AuthenticatedPrincipal p, String nonce, Set<String> selected) {
    return authorize(p, nonce, selected, false);
  }

  @Transactional
  public String authorize(
      AuthenticatedPrincipal p, String nonce, Set<String> selected, boolean sensitiveAcknowledged) {
    enabled();
    if (p == null) throw error(401, "login_required");
    boolean sensitive =
        selected.contains("originals:read")
            || "ADMIN".equals(p.role())
                && selected.stream()
                    .anyMatch(
                        s ->
                            !Set.of(
                                    "catalog:read",
                                    "studio:read",
                                    "chart:read",
                                    "grammar:read",
                                    "offline_access")
                                .contains(s));
    if (sensitive && !sensitiveAcknowledged)
      throw error(400, "sensitive_data_acknowledgement_required");
    var rows =
        db.queryForList(
            "SELECT * FROM mcp_pending WHERE hash=? AND user_id=? AND expires_at>? FOR UPDATE",
            hash(nonce),
            p.userId(),
            Timestamp.from(Instant.now()));
    if (rows.isEmpty()) throw error(400, "consent_expired");
    var q = json.readTree((String) rows.getFirst().get("request_json"));
    if (selected.isEmpty() || !scopes().containsAll(selected)) throw error(400, "invalid_scope");
    String requested = q.get("scope").asText();
    if (!requested.isBlank()
        && !new HashSet<>(List.of(requested.split(" +"))).containsAll(selected))
      throw error(400, "invalid_scope");
    var user = users.findById(p.userId()).orElseThrow(() -> error(401, "invalid_user"));
    if (!user.getRole().name().equals(p.role())) throw error(401, "reauthenticate");
    String id = UUID.randomUUID().toString(), code = random();
    db.update(
        "INSERT INTO mcp_grants(id,user_id,client_id,credential_stamp,user_role,scopes,expires_at)"
            + " VALUES (?,?,?,?,?,?,?)",
        id,
        p.userId(),
        q.get("client_id").asText(),
        stamps.stampFor(p.userId()),
        p.role(),
        String.join(" ", new TreeSet<>(selected)),
        Timestamp.from(Instant.now().plusSeconds(30L * 86400)));
    db.update(
        "INSERT INTO mcp_codes(hash,grant_id,redirect_uri,challenge,resource,expires_at) VALUES"
            + " (?,?,?,?,?,?)",
        hash(code),
        id,
        q.get("redirect_uri").asText(),
        q.get("code_challenge").asText(),
        resource(),
        Timestamp.from(Instant.now().plusSeconds(120)));
    db.update("DELETE FROM mcp_pending WHERE hash=?", hash(nonce));
    audit(
        new Identity(id, p, selected),
        "oauth_consent",
        null,
        sensitive ? "SENSITIVE_ACK" : "ALLOW",
        0);
    return q.get("redirect_uri").asText()
        + "?code="
        + encode(code)
        + "&state="
        + encode(q.get("state").asText());
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public Map<String, Object> token(Map<String, String> q) {
    enabled();
    String kind = q.get("grant_type"), grant;
    if ("authorization_code".equals(kind)) {
      var rows =
          db.queryForList(
              "SELECT * FROM mcp_codes WHERE hash=? AND expires_at>? FOR UPDATE",
              hash(q.getOrDefault("code", "")),
              Timestamp.from(Instant.now()));
      if (rows.isEmpty()) throw error(400, "invalid_grant");
      var c = rows.getFirst();
      String verifier = q.getOrDefault("code_verifier", "");
      if (!verifier.matches("[A-Za-z0-9._~-]{43,128}")
          || !constant(challenge(verifier), (String) c.get("challenge"))
          || !Objects.equals(c.get("redirect_uri"), q.get("redirect_uri"))
          || !resource().equals(q.get("resource"))) throw error(400, "invalid_grant");
      grant = (String) c.get("grant_id");
      var g = grant(grant);
      if (!Objects.equals(g.get("client_id"), q.get("client_id")))
        throw error(400, "invalid_client");
      db.update("DELETE FROM mcp_codes WHERE hash=?", c.get("hash"));
    } else if ("refresh_token".equals(kind)) {
      var rows =
          db.queryForList(
              "SELECT * FROM mcp_tokens WHERE hash=? FOR UPDATE",
              hash(q.getOrDefault("refresh_token", "")));
      if (rows.isEmpty()) throw error(400, "invalid_grant");
      var t = rows.getFirst();
      grant = (String) t.get("grant_id");
      var g = grant(grant);
      if (!Objects.equals(g.get("client_id"), q.get("client_id"))
          || !resource().equals(q.get("resource"))) throw error(400, "invalid_grant");
      if ("USED".equals(t.get("kind"))) {
        db.update("UPDATE mcp_grants SET revoked=TRUE WHERE id=?", grant);
        throw error(400, "invalid_grant");
      }
      if (!"REFRESH".equals(t.get("kind"))
          || ((Timestamp) t.get("expires_at")).toInstant().isBefore(Instant.now()))
        throw error(400, "invalid_grant");
      db.update("UPDATE mcp_tokens SET kind='USED' WHERE hash=?", t.get("hash"));
    } else throw error(400, "unsupported_grant_type");
    Identity identity = identity(grant);
    String access = random();
    db.update(
        "INSERT INTO mcp_tokens VALUES (?,?,?,?)",
        hash(access),
        grant,
        "ACCESS",
        Timestamp.from(Instant.now().plusSeconds(900)));
    Map<String, Object> out =
        new LinkedHashMap<>(
            Map.of(
                "access_token",
                access,
                "token_type",
                "Bearer",
                "expires_in",
                900,
                "scope",
                String.join(" ", new TreeSet<>(identity.scopes()))));
    if (identity.scopes().contains("offline_access")) {
      String refresh = random();
      db.update(
          "INSERT INTO mcp_tokens VALUES (?,?,?,?)",
          hash(refresh),
          grant,
          "REFRESH",
          grant(grant).get("expires_at"));
      out.put("refresh_token", refresh);
    }
    return out;
  }

  private Map<String, Object> grant(String id) {
    return db.queryForList("SELECT * FROM mcp_grants WHERE id=?", id).stream()
        .findFirst()
        .orElseThrow(() -> error(401, "invalid_token"));
  }

  private Identity identity(String id) {
    var g = grant(id);
    if (Boolean.TRUE.equals(g.get("revoked"))
        || ((Timestamp) g.get("expires_at")).toInstant().isBefore(Instant.now()))
      throw error(401, "invalid_token");
    long uid = ((Number) g.get("user_id")).longValue();
    var u = users.findById(uid).orElseThrow(() -> error(401, "invalid_token"));
    if (!u.getRole().name().equals(g.get("user_role"))
        || !constant(stamps.stampFor(uid), (String) g.get("credential_stamp")))
      throw error(401, "invalid_token");
    return new Identity(
        id,
        new AuthenticatedPrincipal(
            uid, u.getLoginId(), u.getRole().name(), (String) g.get("credential_stamp")),
        Set.copyOf(List.of(((String) g.get("scopes")).split(" +"))));
  }

  public Identity authenticate(String header) {
    enabled();
    if (header == null || !header.startsWith("Bearer ") || header.length() > 200)
      throw error(401, "invalid_token");
    var t =
        db.queryForList(
            "SELECT grant_id FROM mcp_tokens WHERE hash=? AND kind='ACCESS' AND expires_at>?",
            hash(header.substring(7)),
            Timestamp.from(Instant.now()));
    if (t.isEmpty()) throw error(401, "invalid_token");
    return identity((String) t.getFirst().get("grant_id"));
  }

  public void revoke(String token) {
    enabled();
    db.update(
        "UPDATE mcp_grants SET revoked=TRUE WHERE id IN (SELECT grant_id FROM mcp_tokens WHERE"
            + " hash=?)",
        hash(token));
  }

  public List<Map<String, Object>> connections(AuthenticatedPrincipal p) {
    if (p == null) throw error(401, "login_required");
    return db.queryForList(
        "SELECT g.id,c.name,g.scopes,g.expires_at,g.revoked,g.created_at FROM mcp_grants g JOIN"
            + " mcp_clients c ON c.id=g.client_id WHERE g.user_id=? ORDER BY g.created_at DESC"
            + " LIMIT 100",
        p.userId());
  }

  public void disconnect(AuthenticatedPrincipal p, String id) {
    if (p == null) throw error(401, "login_required");
    db.update("UPDATE mcp_grants SET revoked=TRUE WHERE id=? AND user_id=?", id, p.userId());
    audit(new Identity(id, p, Set.of()), "oauth_revoke", null, "ALLOW", 0);
  }

  public void audit(Identity i, String tool, String endpoint, String outcome, long ms) {
    db.update(
        "INSERT INTO mcp_audit(grant_id,user_id,tool,endpoint_id,outcome,duration_ms) VALUES"
            + " (?,?,?,?,?,?)",
        i == null ? null : i.grantId(),
        i == null ? null : i.principal().userId(),
        tool,
        endpoint,
        outcome,
        ms);
  }

  public List<Map<String, Object>> history(Identity i) {
    return db.queryForList(
        "SELECT id,tool,endpoint_id,outcome,duration_ms,created_at FROM mcp_audit WHERE user_id=?"
            + " ORDER BY id DESC LIMIT 100",
        i.principal().userId());
  }

  static boolean constant(String a, String b) {
    return a != null
        && b != null
        && MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  static String encode(String s) {
    return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
