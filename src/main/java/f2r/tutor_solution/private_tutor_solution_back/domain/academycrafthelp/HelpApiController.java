package f2r.tutor_solution.private_tutor_solution_back.domain.academycrafthelp;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AcademyCraft 도움말 AI 비서(읽기 전용: 검색·설명·화면 안내). 쓰기 경로·학원 데이터 접근이 없다. */
@RestController
@RequestMapping("/api/help/academycraft")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','TUTOR')")
public class HelpApiController {

  public record AskRequest(String question) {}

  private static final int MAX_QUESTION = 300;

  private final HelpAssistant assistant;
  private final HelpRateLimiter limiter;

  @PostMapping("/ask")
  public ResponseEntity<?> ask(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody AskRequest request) {
    if (request == null || request.question() == null || request.question().length() > MAX_QUESTION) {
      return ResponseEntity.badRequest().body(Map.of("error", "질문은 1~" + MAX_QUESTION + "자로 입력해 주세요."));
    }
    if (!limiter.tryAcquire("u" + principal.userId(), System.currentTimeMillis())) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "잠시 후 다시 질문해 주세요."));
    }
    return ResponseEntity.ok(assistant.ask(principal.role(), request.question()));
  }
}
