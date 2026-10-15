package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestView;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart 질문요청 운영자 검토 API.
 *
 * <p>권한은 경로가 결정한다 — {@code /api/admin/**}는 SecurityConfig에서 {@code hasRole('ADMIN')}이라
 * 이 클래스 안에 추가 검사 규칙을 만들지 않는다(새 권한 규칙 금지).
 *
 * <p>상태 이동은 {@link NewStartRequestStatus} 8종 중 하나이고, REJECTED/DUPLICATE는 사유(reason)이
 * 필수다(service에서 검증). GitHub issue/PR은 자동 생성하지 않으며 {@code githubRef}는 운영자가
 * 반영 시점에 수기로 남긴다.
 */
@RestController
@RequestMapping("/api/admin/newstart/requests")
public class AdminNewStartRequestController {

  private final NewStartRequestService requestService;

  public AdminNewStartRequestController(NewStartRequestService requestService) {
    this.requestService = requestService;
  }

  /** 검토 큐: 기본값은 접수(RECEIVED), 같은 상태가 오래된 순. */
  @GetMapping("/queue")
  public List<RequestView> queue(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false, defaultValue = "RECEIVED") NewStartRequestStatus status) {
    return requestService.queue(status);
  }

  @PostMapping("/{requestId}/review")
  public ResponseEntity<ReviewResponse> review(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long requestId,
      @RequestBody ReviewRequest request) {
    RequestResult result = requestService.review(principal, requestId,
        request == null ? null : request.status(),
        request == null ? null : request.answer(),
        request == null ? null : request.reason(),
        request == null ? null : request.githubRef(),
        request == null ? null : request.resolvedVersion());
    if (!result.ok()) {
      return ResponseEntity.badRequest().body(ReviewResponse.failure(result.errorMessage()));
    }
    return ResponseEntity.ok(ReviewResponse.success(result.view()));
  }

  /** @param status 변경 후 상태. @param reason REJECTED/DUPLICATE일 때 필수 사유(회원에게 노출되는 문구). */
  public record ReviewRequest(NewStartRequestStatus status, String answer, String reason,
      String githubRef, String resolvedVersion) {}

  public record ReviewResponse(String status, String errorMessage, RequestView request) {
    static ReviewResponse success(RequestView view) {
      return new ReviewResponse("ok", null, view);
    }

    static ReviewResponse failure(String errorMessage) {
      return new ReviewResponse("error", errorMessage, null);
    }
  }
}
