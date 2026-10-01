package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * NewStart 운영자 화면(/admin/newstart) — ROLE_ADMIN 전용 진입점.
 *
 * <p>보호는 두 겹이다. 1차: SecurityConfig의 {@code /admin/newstart/**} hasRole("ADMIN") 규칙
 * (비로그인 401, 일반 회원 403). 2차: 본 controller의 principal role 재검사 — viewController
 * 등록이 아니라 실제 controller로 두는 이유는, 메뉴 숨김만으로는 막을 수 없는 직접 URL 접근을
 * 서버가 다시 한 번 판정하게 하기 위해서다.
 *
 * <p>이 화면은 진입점뿐이다 — 회원 채팅 관리/참여는 기존 {@code /newstart/chat}과
 * WS({@code /ws/newstart-chat})를 그대로 쓰며, 운영자 표기("관리자")와 타인 메시지 삭제는
 * 이미 병합된 {@code User.getDisplayName()}·{@code NewStartChatService}의 ROLE_ADMIN
 * 판별을 재사용한다. 미구현 기능(회원 관리·상담 관리 등)은 링크만의 자리를 만들지 않는다.
 */
@Controller
public class AdminNewStartPageController {

  @GetMapping("/admin/newstart")
  public String adminNewStart(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      HttpServletResponse response) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운영자만 접근할 수 있습니다.");
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "admin/newstart";
  }

  /** 감사회원 인증 예외함 화면(자동 인증이 안전하게 끝나지 못한 건만) — 데이터는 /api/admin/newstart/thanks-exceptions (ROLE_ADMIN). */
  @GetMapping("/admin/newstart/thanks-exceptions")
  public String adminThanksExceptions(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      HttpServletResponse response) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운영자만 접근할 수 있습니다.");
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "admin/newstart_thanks_exceptions";
  }
}
