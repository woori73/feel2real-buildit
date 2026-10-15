package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartMemberProfileService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart 전용 표시 별명 조회/변경 — {@code users.nickname}(공용, BuildIt/Wiki/Academy와 공유)과는
 * 완전히 분리된 {@link NewStartMemberProfileService}만 다룬다.
 *
 * <p>인증은 기존 {@code /api/**} catch-all(인증 필요)을 그대로 따른다. 별명 대상 회원은 요청값이
 * 아니라 principal(서버가 JWT에서 검증한 userId)로만 결정한다 — 다른 회원 별명을 임의로
 * 바꾸는 경로 자체가 없다.
 */
@RestController
@RequestMapping("/api/newstart/profile")
public class NewStartProfileController {

  private final NewStartMemberProfileService memberProfileService;
  private final UserRepository userRepository;

  public NewStartProfileController(
      NewStartMemberProfileService memberProfileService, UserRepository userRepository) {
    this.memberProfileService = memberProfileService;
    this.userRepository = userRepository;
  }

  @GetMapping("/me")
  public ResponseEntity<NicknameResponse> me(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    User user = requireUser(principal);
    return ResponseEntity.ok(new NicknameResponse(memberProfileService.resolveDisplayNickname(user)));
  }

  @PutMapping("/nickname")
  public ResponseEntity<?> updateNickname(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody NicknameRequest request) {
    try {
      String saved = memberProfileService.updateNickname(principal.userId(), request.nickname());
      return ResponseEntity.ok(new NicknameResponse(saved));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }
  }

  private User requireUser(JwtTokenProvider.AuthenticatedPrincipal principal) {
    return userRepository.findById(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: userId=" + principal.userId()));
  }

  public record NicknameRequest(String nickname) {}

  public record NicknameResponse(String nickname) {}

  public record ErrorResponse(String errorMessage) {}
}
