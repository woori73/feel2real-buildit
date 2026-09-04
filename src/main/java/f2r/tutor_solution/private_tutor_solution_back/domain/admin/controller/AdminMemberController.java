package f2r.tutor_solution.private_tutor_solution_back.domain.admin.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminMemberResponse;
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

  @GetMapping("/api/admin/members")
  public ResponseEntity<List<AdminMemberResponse>> list() {
    List<AdminMemberResponse> members = userRepository
        .findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
        .stream()
        .map(AdminMemberResponse::of)
        .toList();
    return ResponseEntity.ok(members);
  }
}
