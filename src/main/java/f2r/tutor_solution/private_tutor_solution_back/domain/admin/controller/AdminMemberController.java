package f2r.tutor_solution.private_tutor_solution_back.domain.admin.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminMemberResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.TemporaryPasswordService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.TemporaryPasswordService.AdminIssueResult;
import org.springframework.http.CacheControl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 "회원 전체 보기" — 기존 회원 DB({@code users})를 그대로 읽는다.
 * {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig). 복잡한 CRM 기능은 만들지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class AdminMemberController {

  private final UserRepository userRepository;
  private final TemporaryPasswordService temporaryPasswordService;

  @GetMapping("/api/admin/members")
  public ResponseEntity<List<AdminMemberResponse>> list() {
    List<AdminMemberResponse> members = userRepository
        .findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
        .stream()
        .map(AdminMemberResponse::of)
        .toList();
    return ResponseEntity.ok(members);
  }

  /**
   * 임시 비밀번호 발급(이메일 없이 가입한 회원용). 비밀번호 원문은 이 응답에서 한 번만 나가며 저장·기록하지 않는다.
   * 회원은 이 비밀번호로 로그인한 직후 새 비밀번호를 정해야 하며, 72시간이 지나면 쓸 수 없다.
   */
  @PostMapping("/api/admin/members/{userId}/temporary-password")
  public ResponseEntity<AdminIssueResult> issueTemporaryPassword(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long userId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(temporaryPasswordService.issueByAdmin(principal, userId));
  }
}
