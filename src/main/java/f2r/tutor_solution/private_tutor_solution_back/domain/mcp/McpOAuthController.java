package f2r.tutor_solution.private_tutor_solution_back.domain.mcp;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class McpOAuthController {
  private final McpOAuth oauth;

  @GetMapping({
    "/.well-known/oauth-protected-resource",
    "/.well-known/oauth-protected-resource/mcp"
  })
  @ResponseBody
  public Object resource() {
    oauth.enabled();
    return Map.of(
        "resource",
        oauth.resource(),
        "authorization_servers",
        List.of(oauth.base()),
        "scopes_supported",
        oauth.scopes(),
        "bearer_methods_supported",
        List.of("header"));
  }

  @GetMapping("/.well-known/oauth-authorization-server")
  @ResponseBody
  public Object metadata() {
    return oauth.metadata();
  }

  @PostMapping("/mcp/oauth/register")
  @ResponseBody
  public Object register(@RequestBody Map<String, Object> body) {
    return oauth.register(body);
  }

  @PostMapping(value = "/mcp/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @ResponseBody
  public Object token(@RequestParam Map<String, String> params) {
    return oauth.token(params);
  }

  @PostMapping(value = "/mcp/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @ResponseBody
  public ResponseEntity<Void> revoke(@RequestParam String token) {
    oauth.revoke(token);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/mcp/oauth/authorize")
  public String consent(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam Map<String, String> q,
      Model model,
      HttpServletResponse response) {
    oauth.enabled();
    oauth.validateAuthorization(q);
    if (p == null)
      return "redirect:/?login=required&returnUrl="
          + McpOAuth.encode(
              "/mcp/oauth/authorize?"
                  + q.entrySet().stream()
                      .map(e -> McpOAuth.encode(e.getKey()) + "=" + McpOAuth.encode(e.getValue()))
                      .collect(java.util.stream.Collectors.joining("&")));
    var details = oauth.validateAuthorization(q);
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Content-Security-Policy", "frame-ancestors 'none'; form-action 'self'");
    model.addAttribute("adminAccount", "ADMIN".equals(p.role()));
    model.addAttribute("clientName", details.get("client_name"));
    model.addAttribute("nonce", oauth.pending(p, q));
    model.addAttribute(
        "scopeLabels",
        Map.ofEntries(
            Map.entry("catalog:read", "전체 서비스·페이지 목록"),
            Map.entry("studio:read", "AI AGENT 제작 자료"),
            Map.entry(
                "collaboration:append", "별도 지정한 AI·프로젝트에 DRAFT 첨부·수정 제안 추가 (정본 교체·삭제·승인·배포 불가)"),
            Map.entry("academy:read", "AcademyCraft 학생·반·출결·상담·공지"),
            Map.entry("easy:read", "이지장부 금융·거래 자료"),
            Map.entry("admissions:read", "입시상담 자료와 상담 이력"),
            Map.entry("wiki:read", "내신·모의고사 위키 자료"),
            Map.entry("grammar:read", "문법 문제은행"),
            Map.entry("buildit:read", "BUILD IT 홈페이지·작업 이력"),
            Map.entry("chart:read", "BUILD IT CHART"),
            Map.entry("newstart:read", "NewStart·지피지기 자료"),
            Map.entry("members:read", "회원·학원 정보"),
            Map.entry("content:read", "콘텐츠·상담·공지·출판 자료"),
            Map.entry("learning:read", "수업·학습·시험·영상 자료"),
            Map.entry("analytics:read", "서비스 이용 현황"),
            Map.entry("wemade:read", "위메이드 저장 분석 자료"),
            Map.entry("choonggi:read", "CHOONGGI 콘텐츠"),
            Map.entry("originals:read", "원본 파일을 마스킹 없이 외부 AI에 제공 (별도 경고 확인 필요)"),
            Map.entry("offline_access", "30일 동안 연결 자동 갱신")));
    model.addAttribute(
        "scopes",
        details.get("scope").isBlank()
            ? oauth.scopes()
            : List.of(details.get("scope").split(" +")));
    return "studio/mcp-consent";
  }

  @PostMapping("/mcp/oauth/authorize")
  public ResponseEntity<Void> authorize(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @RequestParam String nonce,
      @RequestParam Set<String> scopes,
      @RequestParam(defaultValue = "false") boolean sensitiveAcknowledged,
      HttpServletRequest req) {
    sameOrigin(req);
    return ResponseEntity.status(303)
        .location(URI.create(oauth.authorize(p, nonce, scopes, sensitiveAcknowledged)))
        .build();
  }

  @GetMapping("/mcp/connections")
  public String connections(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      Model model,
      HttpServletResponse response) {
    oauth.enabled();
    if (p == null) return "redirect:/?login=required&returnUrl=%2Fmcp%2Fconnections";
    response.setHeader("Cache-Control", "no-store");
    model.addAttribute("connections", oauth.connections(p));
    return "studio/mcp-connections";
  }

  @GetMapping("/api/mcp/connections")
  @ResponseBody
  public Object list(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return oauth.connections(p);
  }

  @PostMapping("/api/mcp/connections/{id}/revoke")
  @ResponseBody
  public void disconnect(
      @AuthenticationPrincipal AuthenticatedPrincipal p,
      @PathVariable String id,
      HttpServletRequest req) {
    sameOrigin(req);
    oauth.disconnect(p, id);
  }

  private void sameOrigin(HttpServletRequest req) {
    if (!oauth.base().equals(req.getHeader("Origin")))
      throw McpOAuth.error(403, "same_origin_required");
  }

  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  @ResponseBody
  public ResponseEntity<?> error(org.springframework.web.server.ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .cacheControl(CacheControl.noStore())
        .body(Map.of("error", e.getReason() == null ? "invalid_request" : e.getReason()));
  }
}
