package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartCapability;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.entity.NewStartRequestKind;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartEntitlementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartRequestService.RequestView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart 질문요청 게시판 API (설계 §5).
 *
 * <p>§7 접근 경계: 읽기는 인증 회원이면 서로의 요청을 열람할 수 있고(비로그인은 {@code /api/**}
 * catch-all이 401), <b>쓰기·취소는 THANKS_MEMBER</b>다. 운영자의 상태 관리는 이 경로가 아니라
 * {@code /api/admin/newstart/requests}(hasRole ADMIN)만 통과한다.
 *
 * <p>실패 응답은 사용자 안내 문구({@code errorMessage})만 담는다 — service 내부 검증 상세·stack은
 * 브라우저로 내려보내지 않는다. GitHub issue 자동 생성은 없다({@code githubRef}는 수동 기입).
 */
@RestController
@RequestMapping("/api/newstart/requests")
public class NewStartRequestController {

  private final NewStartRequestService requestService;
  private final NewStartEntitlementService entitlementService;

  public NewStartRequestController(NewStartRequestService requestService,
      NewStartEntitlementService entitlementService) {
    this.requestService = requestService;
    this.entitlementService = entitlementService;
  }

  /** 목록(최근 작성 순). {@code kind}로 [질문·조사]/[사이트 개선] 인박스를 나눈다. */
  @GetMapping
  public RequestPage list(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(required = false) NewStartRequestKind kind,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return requestService.list(kind, page, size);
  }

  @GetMapping("/{requestId}")
  public ResponseEntity<DetailResponse> detail(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long requestId) {
    RequestResult result = requestService.detail(requestId);
    if (!result.ok()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(DetailResponse.failure(result.errorMessage()));
    }
    return ResponseEntity.ok(DetailResponse.success(result.view()));
  }

  /** 요청 작성 — THANKS_MEMBER만(미부여 회원은 403 + 안내). */
  @PostMapping
  public ResponseEntity<WriteResponse> create(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody CreateRequest request) {
    entitlementService.require(principal, NewStartCapability.THANKS_MEMBER);
    RequestResult result = requestService.create(principal,
        request == null ? null : request.kind(),
        request == null ? null : request.title(),
        request == null ? null : request.screenLocation(),
        request == null ? null : request.aToZStep(),
        request == null ? null : request.problem(),
        request == null ? null : request.proposal());
    if (!result.ok()) {
      return ResponseEntity.badRequest().body(WriteResponse.failure(result.errorMessage()));
    }
    return ResponseEntity.ok(WriteResponse.success(result.view()));
  }

  /** Withdraw(본인 또는 ADMIN). soft delete — 원문은 파기되고 이력 row만 남는다. */
  @DeleteMapping("/{requestId}")
  public ResponseEntity<WriteResponse> withdraw(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @PathVariable Long requestId) {
    RequestResult result = requestService.withdraw(principal, requestId);
    if (result.ok()) {
      return ResponseEntity.ok(new WriteResponse("ok", null, null));
    }
    HttpStatus status = result.forbidden() ? HttpStatus.FORBIDDEN : HttpStatus.NOT_FOUND;
    return ResponseEntity.status(status).body(WriteResponse.failure(result.errorMessage()));
  }

  /** @param aToZStep SITE_IMPROVEMENT일 때만 의미를 갖는 "13-2" 형태 STEP 번호. */
  public record CreateRequest(NewStartRequestKind kind, String title, String screenLocation,
      String aToZStep, String problem, String proposal) {}

  public record DetailResponse(String status, String errorMessage, RequestView request) {
    static DetailResponse success(RequestView view) {
      return new DetailResponse("ok", null, view);
    }

    static DetailResponse failure(String errorMessage) {
      return new DetailResponse("error", errorMessage, null);
    }
  }

  public record WriteResponse(String status, String errorMessage, RequestView request) {
    static WriteResponse success(RequestView view) {
      return new WriteResponse("ok", null, view);
    }

    static WriteResponse failure(String errorMessage) {
      return new WriteResponse("error", errorMessage, null);
    }
  }
}
