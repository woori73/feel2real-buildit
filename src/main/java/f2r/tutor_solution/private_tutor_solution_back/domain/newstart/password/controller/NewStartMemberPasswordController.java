package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.NewStartPasswordChangeRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.dto.NewStartPasswordStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.password.service.NewStartMemberPasswordService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 회원 본인 비밀번호 API — 계정 메뉴의 [비밀번호 변경]과 ADMIN 초기화 후 [새 비밀번호 설정]이
 * 같은 경로를 쓴다(규칙이 동일한 작업이기 때문).
 *
 * <p>운영자 초기화 API({@code /api/admin/newstart/members/**})와 분리했다. 이 쪽은 대상 지정
 * 인자가 아예 없다 — 오직 로그인한 본인({@code principal.userId()})만 처리하므로 다른 회원을
 * 지정할 수 있는 형태 자체가 아니다.
 *
 * <p>{@code /api/newstart/**}는 SecurityConfig의 {@code .authenticated()} catch-all이 401을
 * 먼저 내보낸다. 제외 경로로 등록되어 있어도 강제로 요구된 상태의 회원이 통과할 수 있는 유일한
 * NewStart API 경로다(여기서 막히면 새 비밀번호를 정할 방법이 없어진다).
 */
@RestController
@RequestMapping("/api/newstart/me/password")
public class NewStartMemberPasswordController {

  private final NewStartMemberPasswordService passwordService;

  public NewStartMemberPasswordController(NewStartMemberPasswordService passwordService) {
    this.passwordService = passwordService;
  }

  @GetMapping
  public ResponseEntity<NewStartPasswordStatus> status(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(passwordService.status(requireUserId(principal)));
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> change(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody(required = false) NewStartPasswordChangeRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 본문을 확인해 주세요.");
    }
    passwordService.change(requireUserId(principal), request);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("changed", true, "changeRequired", false));
  }

  private Long requireUserId(AuthenticatedPrincipal principal) {
    if (principal == null || principal.userId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    return principal.userId();
  }
}
