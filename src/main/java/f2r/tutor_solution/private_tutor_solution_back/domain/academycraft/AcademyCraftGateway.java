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
    return "redirect:/academycraft/screens/" + ("STUDENT".equals(principal.role()) ? "SELF-01-student.html" : "OPS-02-dashboard.html");
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
    if (!Set.of("ADMIN", "TUTOR", "STUDENT").contains(role)) throw new AccessDeniedException("허용되지 않은 역할");
    String path = request.getRequestURI().substring("/academycraft".length());
    if (!allowedPath(path)) throw new AccessDeniedException("허용되지 않은 AcademyCraft 경로");
    boolean api = path.startsWith("/api/rea/");
    if (!api && !"GET".equals(request.getMethod())) throw new AccessDeniedException("정적 화면은 읽기 전용");
    if (!Set.of("GET", "HEAD").contains(request.getMethod())) {
      // Existing BuildIt CSRF policy is unchanged. This gateway adds an exact-origin check for writes.
      if (!"https://feel2real.co.kr".equals(request.getHeader("Origin"))
          && !"http://localhost:8080".equals(request.getHeader("Origin")))
        throw new AccessDeniedException("동일 출처 요청만 허용됩니다.");
    }
    String query = request.getQueryString();
    String target = path + (query == null ? "" : "?" + query);
    byte[] payload = body == null ? new byte[0] : body;
    if (payload.length > 1_048_576) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    Long ownerAcademy = academies.findByTutor_UserId(user.getUserId()).map(a -> a.getAcademyId()).orElse(null);
    String assertion = Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(
        new Identity(user.getUserId(), role, ownerAcademy, Instant.now().getEpochSecond(), UUID.randomUUID().toString())));
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
      html = html.replace("<head>", "<head><script>window.ACADEMYCRAFT_COMMON_AUTH=true;</script>");
      result = html.getBytes(StandardCharsets.UTF_8);
    }
    return new ResponseEntity<>(result, headers, HttpStatusCode.valueOf(response.statusCode()));
  }

  public static boolean allowedPath(String path) {
    if (path.contains("..") || path.contains("%") || path.contains("\\") || path.contains(";")) return false;
    if (path.matches("/components/[A-Za-z0-9_-]+\\.(js|css)")) return true;
    if (path.matches("/screens/[A-Za-z0-9_-]+\\.html"))
      return !path.matches(".*(AUTH-|login|ADM-|PAY-).*");
    return (path.equals("/api/rea/integration/context") || path.equals("/api/rea/integration/teachers"))
        || path.matches("/api/rea/[1-9][0-9]*/(students|classes|attendance|dashboard/stats|timetable)(/.*)?");
  }

  public static String sign(String secret, String method, String target, String assertion, byte[] body) throws Exception {
    String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal((method + "\n" + target + "\n" + assertion + "\n" + digest).getBytes(StandardCharsets.UTF_8)));
  }
  record Identity(Long userId, String role, Long ownerAcademyId, long issuedAt, String nonce) {}
}
