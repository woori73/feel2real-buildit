package f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.dto.WikiLiveMessageResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.wikilive.service.WikiLiveMessageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * WIKI LIVE 한 줄 메시지 API.
 * <ul>
 *   <li>{@code GET /api/wiki/live-messages} — 조회(비회원 포함 공개). {@code sinceId} 있으면 그 이후만.
 *   <li>{@code POST /api/wiki/live-messages} — 작성(로그인 회원, {@code /api/**} 인증 + principal null 방어).
 *   <li>{@code DELETE /api/admin/wiki/live-messages/{id}} — 관리자 숨김(ROLE_ADMIN, {@code /api/admin/**}).
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class WikiLiveMessageController {

  private final WikiLiveMessageService service;

  @GetMapping("/api/wiki/live-messages")
  public ResponseEntity<List<WikiLiveMessageResponse>> list(
      @RequestParam(name = "sinceId", required = false) Long sinceId) {
    List<WikiLiveMessageResponse> messages =
        (sinceId == null) ? service.recent() : service.since(sinceId);
    return ResponseEntity.ok(messages);
  }

  @PostMapping("/api/wiki/live-messages")
  @ResponseStatus(HttpStatus.CREATED)
  public WikiLiveMessageResponse create(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody WikiLiveMessageCreateRequest request) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    return service.create(principal.userId(), request == null ? null : request.message());
  }

  @DeleteMapping("/api/admin/wiki/live-messages/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void adminHide(@PathVariable Long id) {
    service.adminHide(id);
  }
}
