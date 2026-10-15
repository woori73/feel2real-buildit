package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.CreateStudentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.CreateTutorRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.UserResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.StudentSummaryResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final AuthenticatedUserResolver authResolver;

  /** 회원가입 — 인증 없이 호출 가능 (SecurityConfig에서 permitAll). */
  @PostMapping("/students")
  public ResponseEntity<UserResponse> createStudent(@RequestBody CreateStudentRequest request) {
    return ResponseEntity.ok(userService.createStudent(request));
  }

  /** 강사 회원가입 — 인증 없이 호출 가능 (SecurityConfig에서 permitAll). */
  @PostMapping("/tutors")
  public ResponseEntity<UserResponse> createTutor(@RequestBody CreateTutorRequest request) {
    return ResponseEntity.ok(userService.createTutor(request));
  }

  /** 회원가입 아이디 중복 확인 — 인증 없이 호출 가능 (SecurityConfig에서 permitAll). */
  @GetMapping("/check-login-id")
  public ResponseEntity<Map<String, Boolean>> checkLoginId(
      @RequestParam("loginId") String loginId) {
    return ResponseEntity.ok(Map.of("available", userService.isLoginIdAvailable(loginId)));
  }

  /** ADMIN 전용: 전체 학생 목록 — SecurityConfig + UserController 양쪽에서 가드. */
  @GetMapping("/students")
  public ResponseEntity<List<StudentSummaryResponse>> getAllStudents() {
    return ResponseEntity.ok(userService.getAllStudents());
  }

  /**
   * 학생 본인 상세 — 로그인 직후 프론트가 currentStudent sessionStorage를 채울 때 사용.
   * JWT principal에서 userId를 꺼내므로 query/path 파라미터 없음 (IDOR 차단).
   *
   * <p>ADMIN이 임퍼소네이트로 다른 학생 정보가 필요하면 {@code targetUserId} query로 명시.
   */
  @GetMapping("/students/me")
  public ResponseEntity<StudentSummaryResponse> getMyStudentProfile(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
    Long userId = authResolver.resolveUserId(principal, targetUserId);
    return ResponseEntity.ok(userService.getStudentByUserId(userId));
  }
}
