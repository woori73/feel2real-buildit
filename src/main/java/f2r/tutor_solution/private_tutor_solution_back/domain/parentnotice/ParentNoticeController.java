package f2r.tutor_solution.private_tutor_solution_back.domain.parentnotice;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/dashboard/parent-notices")
@PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
@RequiredArgsConstructor
public class ParentNoticeController {
  private final ParentNoticeService notices;

  @Value("${buildit.unified-assistant.allowed-origins:http://localhost:8080,https://feel2real.co.kr}")
  private List<String> allowedOrigins;

  @Value("${app.kakao.share.javascript-key:}")
  private String javascriptKey;

  public record Workspace(String javascriptKey, List<ParentNoticeService.Draft> drafts) {}

  @GetMapping
  public ResponseEntity<Workspace> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new Workspace(javascriptKey.strip(), notices.list(principal)));
  }

  public record MonthlyConfirmed(long monthlyConfirmed) {}

  @GetMapping("/monthly-confirmed")
  public ResponseEntity<MonthlyConfirmed> monthlyConfirmed(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(new MonthlyConfirmed(notices.monthlyConfirmed(principal)));
  }

  @PostMapping
  public ResponseEntity<ParentNoticeService.Draft> save(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestHeader(name = "Origin", required = false) String origin,
      @RequestBody ParentNoticeService.Request request) {
    if (origin == null || !allowedOrigins.contains(origin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "동일 출처 요청만 허용합니다.");
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(notices.save(principal, request, ParentNoticeService.Source.DASHBOARD));
  }
}
