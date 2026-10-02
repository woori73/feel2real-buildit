package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartMemberPage;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.AdminNewStartPasswordResetResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service.NewStartAdminMemberQueryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service.NewStartAdminPasswordResetService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * NewStart 회원관리 운영자 API — 검색·상세·1회용 임시 비밀번호 초기화.
 *
 * <p>접근 제어는 {@code /api/admin/**}의 {@code hasRole('ADMIN')}(SecurityConfig)이 1차로 결정하고,
 * 초기화 서비스는 실행 principal을 한 번 더 확인한다(운영자 자격으로 남의 계정의 비밀번호를
 * 갈아엎는 작업이라 경로 하나에 맡기지 않는다).
 *
 * <p>이 컨트롤러에는 비밀번호를 읽는 엔드포인트가 없다. 초기화 응답의 {@code temporaryPassword}는
 * 그 자리에서 새로 만든 값일 뿐, 저장된 것을 돌려주는 것이 아니다 — 그래서 재조회 API가 존재할 수
 * 없다. 응답은 {@code Cache-Control: no-store}(프록시·브라우저 캐시에 임시 값이 남지 않게).
 */
@RestController
@RequestMapping("/api/admin/newstart/members")
public class AdminNewStartMemberController {

  private static final int MAX_QUERY_LENGTH = 50;

  private final NewStartAdminMemberQueryService queryService;
  private final NewStartAdminPasswordResetService resetService;

  public AdminNewStartMemberController(NewStartAdminMemberQueryService queryService,
                                       NewStartAdminPasswordResetService resetService) {
    this.queryService = queryService;
    this.resetService = resetService;
  }

  /** 로그인 ID·카페 닉네임 부분 일치 검색. 검색어를 비우면 최근 가입 순 목록. */
  @GetMapping
  public AdminNewStartMemberPage search(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    if (query != null && query.strip().length() > MAX_QUERY_LENGTH) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 50자 이내로 입력해 주세요.");
    }
    return queryService.search(query, page, size);
  }

  @GetMapping("/{userId}")
  public AdminNewStartMemberDetail detail(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long userId) {
    return queryService.detail(userId);
  }

  /** 1회용 임시 비밀번호 초기화. 생성된 값은 이 응답에서만 한 번 보인다. */
  @PostMapping("/{userId}/password-reset")
  public ResponseEntity<AdminNewStartPasswordResetResponse> reset(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long userId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(resetService.reset(userId, principal));
  }
}
