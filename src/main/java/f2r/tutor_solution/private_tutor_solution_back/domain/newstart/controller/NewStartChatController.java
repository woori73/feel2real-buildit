package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService.NewStartChatDeleteResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartChatService.NewStartChatHistory;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart 회원 채팅 REST API — 히스토리 조회/삭제. 실시간 전송은 WebSocket
 * ({@code /ws/newstart-chat})이 담당하므로 여기에 전송 endpoint는 없다.
 *
 * <p>인증은 기존 {@code /api/**} catch-all(인증 필요)을 그대로 따른다 — 비로그인 조회/삭제는
 * SecurityConfig가 401로 거부한다. 작성자는 브라우저 요청값이 아니라 principal로만 결정된다.
 */
@RestController
@RequestMapping("/api/newstart/chat")
public class NewStartChatController {

  private final NewStartChatService newStartChatService;

  public NewStartChatController(NewStartChatService newStartChatService) {
    this.newStartChatService = newStartChatService;
  }

  /** 최근 메시지 조회(기본 30건, 최대 50건). {@code before} 지정 시 그보다 오래된 메시지. */
  @GetMapping("/messages")
  public ResponseEntity<NewStartChatHistory> messages(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) Long before,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(newStartChatService.history(before, limit));
  }

  /** 재접속 복구: {@code after}보다 새로운 메시지(오래된 순, 최대 50건). */
  @GetMapping("/after")
  public ResponseEntity<List<NewStartChatService.NewStartChatMessageView>> after(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long after) {
    return ResponseEntity.ok(newStartChatService.after(after));
  }

  /** 본인 메시지 삭제(ADMIN은 기존 권한으로 허용). 타인 메시지는 403. */
  @DeleteMapping("/messages/{messageId}")
  public ResponseEntity<ChatDeleteResponse> delete(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long messageId) {
    NewStartChatDeleteResult result = newStartChatService.delete(principal, messageId);
    if (result.ok()) {
      return ResponseEntity.ok(new ChatDeleteResponse("ok", null));
    }
    int httpStatus = result.forbidden() ? 403 : 400;
    return ResponseEntity.status(httpStatus).body(new ChatDeleteResponse("error", result.errorMessage()));
  }

  /** @param errorMessage 실패 시 사용자용 안내 문구(내부 예외 미포함), 성공 시 null. */
  public record ChatDeleteResponse(String status, String errorMessage) {}
}
