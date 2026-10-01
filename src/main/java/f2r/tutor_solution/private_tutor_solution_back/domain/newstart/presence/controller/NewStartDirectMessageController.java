package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.DmMessageView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.DmSendRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.dto.DmUnreadCountView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.presence.service.NewStartDirectMessageService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart 사이트 내부 1:1 쪽지 API. {@code /api/**} catch-all로 로그인
 * 회원만 사용한다. 발신자는 서버 principal에서만 결정된다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/newstart/dm")
public class NewStartDirectMessageController {

  private final NewStartDirectMessageService directMessageService;

  /** 쪽지 전송. */
  @PostMapping("/send")
  public ResponseEntity<DmMessageView> send(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody DmSendRequest request) {
    return ResponseEntity.ok(directMessageService.send(
        principal, request.receiverUserId(), request.message()));
  }

  /** 받은 쪽지함(최신순). */
  @GetMapping("/inbox")
  public ResponseEntity<List<DmMessageView>> inbox(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) Long before,
      @RequestParam(required = false) Integer limit) {
    return ResponseEntity.ok(directMessageService.inbox(principal, before, limit));
  }

  /** 읽지 않은 받은 쪽지 수. */
  @GetMapping("/unread-count")
  public ResponseEntity<DmUnreadCountView> unreadCount(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(new DmUnreadCountView(directMessageService.unreadCount(principal)));
  }

  /** 읽음 표시(수신자 본인만). */
  @PostMapping("/{messageId}/read")
  public ResponseEntity<Void> markRead(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long messageId) {
    directMessageService.markRead(principal, messageId);
    return ResponseEntity.ok().build();
  }

  /** 받은 쪽지 삭제(수신자 본인만, 단일 복사본 삭제). */
  @DeleteMapping("/{messageId}")
  public ResponseEntity<Void> deleteMessage(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long messageId) {
    directMessageService.deleteMessage(principal, messageId);
    return ResponseEntity.ok().build();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("errorMessage",
        e.getMessage() == null ? "요청이 거부되었습니다." : e.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> handleTooManyRequests(IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("errorMessage",
        e.getMessage() == null ? "잠시 후 다시 시도해 주세요." : e.getMessage()));
  }
}
