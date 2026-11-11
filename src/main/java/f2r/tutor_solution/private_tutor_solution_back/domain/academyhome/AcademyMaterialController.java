package f2r.tutor_solution.private_tutor_solution_back.domain.academyhome;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 정보 화면 API(ADMIN·TUTOR 로그인, 자료 권한은 기존 스튜디오 규칙). 응답은 캐시하지 않는다. */
@RestController
@RequestMapping("/api/academy-home")
@RequiredArgsConstructor
public class AcademyMaterialController {
  private final AcademyMaterialService service;

  @Value("${buildit.unified-assistant.allowed-origins:http://localhost:8080,https://feel2real.co.kr}")
  private List<String> allowedOrigins;

  @ModelAttribute
  public void guard(@AuthenticationPrincipal AuthenticatedPrincipal p, HttpServletRequest request, HttpServletResponse response) {
    if (p == null) throw AcademyMaterialService.error(401, "로그인이 필요합니다.");
    if (!Set.of("ADMIN", "TUTOR").contains(p.role())) throw AcademyMaterialService.error(403, "원장·강사 전용입니다.");
    response.setHeader("Cache-Control", "private, no-store");
    if (!Set.of("GET", "HEAD").contains(request.getMethod()) && !allowedOrigins.contains(Objects.toString(request.getHeader("Origin"), "")))
      throw AcademyMaterialService.error(403, "같은 사이트에서 작업하세요.");
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> error(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", Objects.toString(e.getReason(), "요청 실패")));
  }

  @GetMapping("/materials")
  public Object list(@AuthenticationPrincipal AuthenticatedPrincipal p) { return service.list(p); }

  @GetMapping("/materials/text")
  public Object text(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam String projectId, @RequestParam String fileId) throws IOException {
    return service.read(p, projectId, fileId);
  }

  @GetMapping("/roster/reconcile")
  public Object reconcile(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam int year, @RequestParam Long academyId) throws IOException {
    return service.reconcile(p, year, academyId);
  }

  @PostMapping("/roster/links")
  public Object link(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody AcademyMaterialService.LinkRequest r) throws IOException {
    return service.link(p, r);
  }
}
