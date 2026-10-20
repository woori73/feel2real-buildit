package f2r.tutor_solution.private_tutor_solution_back.domain.academycraft;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Same-origin gateway. No browser token, credential copying, account provisioning or DB writes. */
@Controller
public class AcademyCraftGateway {
  /**
   * AI 쓰기 승인(P1-b) 프로토콜 버전. 서명된 신원에 담아 AcademyCraft 로 보낸다.
   * AcademyCraft 가 같은 버전을 기대할 때만 /agent 경로를 처리하므로, 두 서비스의 버전이 다르면 에이전트 경로만 안전하게 실패한다.
   */
  static final int AGENT_PROTOCOL = 1;
  /** 에이전트 요청 본문 상한 (계획 요청·승인은 작은 JSON 이다) */
  private static final int AGENT_MAX_BODY = 16_384;
  private static final String UUID_RE = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  private final UserRepository users;
  private final AcademyRepository academies;
  private final ObjectMapper json;
  private final URI upstream;
  private final String secret;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
      .followRedirects(HttpClient.Redirect.NEVER).build();

  public AcademyCraftGateway(UserRepository users, AcademyRepository academies, ObjectMapper json,
      @Value("${academycraft.upstream:http://127.0.0.1:8082}") String upstream,
      @Value("${academycraft.gateway-secret:}") String secret) {
    this.users = users; this.academies = academies; this.json = json;
    this.upstream = URI.create(upstream); this.secret = secret;
    // This integration is deliberately local to the existing EC2. No arbitrary proxy/SSRF target.
    if (!Set.of("127.0.0.1", "localhost").contains(this.upstream.getHost())
        || !"http".equals(this.upstream.getScheme()) || this.upstream.getPort() != 8082
        || !Set.of("", "/").contains(this.upstream.getPath())
        || this.upstream.getUserInfo() != null || this.upstream.getQuery() != null) {
      throw new IllegalArgumentException("AcademyCraft upstream must be local port 8082");
    }
  }

  @GetMapping({"/academycraft", "/academycraft/"})
  public String entry(@AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) return "redirect:/?login=required&returnUrl=%2Facademycraft";
    if (!Set.of("ADMIN", "TUTOR").contains(principal.role())) throw new AccessDeniedException("ADMIN·TUTOR 전용 서비스입니다.");
    return "redirect:/academycraft/screens/OPS-02-dashboard.html";
  }

  @RequestMapping(value="/academycraft/**", method={RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
  @ResponseBody
  public ResponseEntity<byte[]> proxy(HttpServletRequest request,
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody(required=false) byte[] body) throws Exception {
    if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AcademyCraft 연결 설정이 준비되지 않았습니다.");
    var user = users.findById(principal.userId()).orElseThrow(() -> new AccessDeniedException("회원 연결 없음"));
    String role = user.getRole().name(); // Revalidate current role; never trust browser-supplied identity.
    if (!Set.of("ADMIN", "TUTOR").contains(role)) throw new AccessDeniedException("허용되지 않은 역할");
    String path = request.getRequestURI().substring("/academycraft".length());
    if (!allowedPath(path, request.getMethod())) {
      // 화면 이동(GET)은 빈 오류 대신 안내 페이지를 보여 준다. 권한은 그대로 거부(403)한다.
      if ("GET".equals(request.getMethod()) && path.startsWith("/screens/")) return blockedScreenPage();
      throw new AccessDeniedException("허용되지 않은 AcademyCraft 경로");
    }
    boolean api = path.startsWith("/api/rea/");
    if (!api && !"GET".equals(request.getMethod())) throw new AccessDeniedException("정적 화면은 읽기 전용");
    if (!Set.of("GET", "HEAD").contains(request.getMethod())) {
      // Existing BuildIt CSRF policy is unchanged. This gateway adds an exact-origin check for writes.
      if (!"https://feel2real.co.kr".equals(request.getHeader("Origin"))
          && !"http://localhost:8080".equals(request.getHeader("Origin")))
        throw new AccessDeniedException("동일 출처 요청만 허용됩니다.");
    }
    String query = request.getQueryString();
    boolean agent = isAgentPath(path);
    if (agent) {
      // 에이전트 경로는 쿼리 문자열을 쓰지 않고, 본문은 작은 JSON 만 받는다.
      if (query != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "쿼리 문자열을 사용할 수 없습니다.");
      String type = request.getContentType();
      if ("POST".equals(request.getMethod()) && (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")))
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JSON 요청만 허용됩니다.");
    }
    String target = path + (query == null ? "" : "?" + query);
    byte[] payload = body == null ? new byte[0] : body;
    if (payload.length > (agent ? AGENT_MAX_BODY : 1_048_576)) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    Long ownerAcademy = academies.findByTutor_UserId(user.getUserId()).map(a -> a.getAcademyId()).orElse(null);
    String assertion = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(
        newIdentity(user.getUserId(), role, ownerAcademy)));
    String signature = sign(secret, request.getMethod(), target, assertion, payload);
    var builder = HttpRequest.newBuilder(upstream.resolve(target)).timeout(Duration.ofSeconds(20))
        .header("X-BuildIt-Assertion", assertion).header("X-BuildIt-Signature", signature)
        .method(request.getMethod(), HttpRequest.BodyPublishers.ofByteArray(payload));
    if (api && request.getContentType() != null) builder.header("Content-Type", request.getContentType());
    // No incoming Cookie, Authorization, X-Auth-Key, Host or identity headers are forwarded.
    HttpResponse<byte[]> response;
    try { response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray()); }
    catch (java.io.IOException e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AcademyCraft 연결 실패"); }
    byte[] result = response.body();
    String type = response.headers().firstValue("content-type").orElse("application/octet-stream");
    var headers = new HttpHeaders(); headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.set(HttpHeaders.CONTENT_TYPE, type);
    headers.set("X-Content-Type-Options", "nosniff");
    if (type.contains("text/html")) {
      String html = new String(result, StandardCharsets.UTF_8);
      html = html.replace("<head>", "<head><script>window.ACADEMYCRAFT_COMMON_AUTH=true;</script><script defer src=\"/academycraft/components/common-service.js\"></script>");
      result = html.getBytes(StandardCharsets.UTF_8);
    }
    return new ResponseEntity<>(result, headers, HttpStatusCode.valueOf(response.statusCode()));
  }

  static final String BLOCKED_SCREEN_HTML = "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
      + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>이용할 수 없는 화면</title>"
      + "<style>body{font:16px/1.6 system-ui,sans-serif;margin:0;padding:24px;color:#1a1a1a}"
      + "a,button{display:block;box-sizing:border-box;width:100%;min-height:52px;margin-top:12px;padding:14px;border-radius:12px;"
      + "font:600 16px system-ui,sans-serif;text-align:center;text-decoration:none;border:2px solid #1a56db;background:#fff;color:#1a56db}"
      + "a.primary{background:#1a56db;color:#fff}</style></head><body><main><h1>아직 이용할 수 없는 화면입니다</h1>"
      + "<p>이 화면은 아직 연결되지 않았습니다. 학생·반·출결·상담·공지 화면은 그대로 사용할 수 있습니다.</p>"
      + "<a class=\"primary\" href=\"/academycraft/screens/OPS-02-dashboard.html\">학원 현황으로 돌아가기</a>"
      + "<button type=\"button\" onclick=\"history.back()\">이전 화면</button>"
      + "<button type=\"button\" onclick=\"location.reload()\">다시 시도</button></main></body></html>";

  static ResponseEntity<byte[]> blockedScreenPageForTest() { return blockedScreenPage(); }

  private static ResponseEntity<byte[]> blockedScreenPage() {
    var headers = new HttpHeaders();
    headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
    headers.set(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8");
    headers.set("X-Content-Type-Options", "nosniff");
    return new ResponseEntity<>(BLOCKED_SCREEN_HTML.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.FORBIDDEN);
  }

  /** 서명된 신원: BuildIt 사용자·역할·소유 학원과 AI 쓰기 프로토콜 버전 */
  static Identity newIdentity(Long userId, String role, Long ownerAcademyId) {
    return new Identity(userId, role, ownerAcademyId, Instant.now().getEpochSecond(), UUID.randomUUID().toString(), AGENT_PROTOCOL);
  }

  /** 에이전트 경로(/api/rea/{id}/agent/…) 여부 */
  static boolean isAgentPath(String path) {
    return path.matches("/api/rea/[1-9][0-9]*/agent(/.*)?");
  }

  /**
   * 에이전트 정확 경로 5개 (메서드까지 일치해야 한다). 와일드카드·접미 경로·다른 메서드는 허용하지 않는다.
   * 경로 허용은 권한 검증을 대체하지 않는다: 로그인·ADMIN/TUTOR 재확인·Origin·서명은 그대로 적용되고,
   * 학원·회원 연결·원장/강사 권한·승인 프로토콜은 AcademyCraft 가 매 요청 검증한다.
   */
  public static boolean allowedAgentPath(String path, String method) {
    String base = "/api/rea/[1-9][0-9]*/agent";
    if (path.matches(base + "/tools")) return "GET".equals(method);
    if (path.matches(base + "/execute")) return "POST".equals(method);
    if (path.matches(base + "/plans/" + UUID_RE)) return "GET".equals(method);
    if (path.matches(base + "/plans/" + UUID_RE + "/(approve|cancel)")) return "POST".equals(method);
    return false;
  }

  /** 경로와 메서드를 함께 본다. 에이전트 경로는 정확 경로 5개만, 그 외는 기존 허용 목록. */
  public static boolean allowedPath(String path, String method) {
    return isAgentPath(path) ? allowedAgentPath(path, method) : allowedPath(path);
  }

  public static boolean allowedPath(String path) {
    if (path.contains("..") || path.contains("%") || path.contains("\\") || path.contains(";")) return false;
    if (path.matches("/components/[A-Za-z0-9_-]+\\.(js|css)")) return true;
    if (path.startsWith("/screens/")) return Set.of(
        "MBR-01-student-list.html", "CLS-01-class-list.html", "CLS-02-class-form.html", "CLS-03-class-detail.html",
        "ATT-01-attendance-keypad.html", "ATT-02-attendance-monthly.html", "ATT-03-attendance-roster.html",
        "ATT-04-attendance-settings.html", "OPS-02-dashboard.html", "timetable-weekly.html", "CNS-01-consultation-list.html", "CNS-03-inquiry-inbox.html",
        "CNS-04-news-send.html", "CNS-05-consultation-detail.html", "NTC-01-notice-list.html",
        "NTC-02-class-news.html", "NTC-03-notice-form.html", "NTC-04-notice-detail.html").contains(path.substring(9));
    return (path.equals("/api/rea/integration/context") || path.equals("/api/rea/integration/teachers"))
        || path.matches("/api/rea/[1-9][0-9]*/(students|classes|attendance|dashboard/stats|timetable|consultations|notices)(/.*)?");
  }

  public static String sign(String secret, String method, String target, String assertion, byte[] body) throws Exception {
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal((method + "\n" + target + "\n" + assertion + "\n" + digest).getBytes(StandardCharsets.UTF_8)));
  }
  record Identity(Long userId, String role, Long ownerAcademyId, long issuedAt, String nonce, int agentProtocol) {}
}
