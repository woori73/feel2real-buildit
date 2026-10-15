package f2r.tutor_solution.private_tutor_solution_back.domain.user.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.UpdateMyProfileRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.request.WithdrawRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.dto.response.MyProfileResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

  private final UserService userService;
  private final CookieUtil cookieUtil;

  @GetMapping
  public ResponseEntity<MyProfileResponse> getMyProfile(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(userService.getMyProfile(principal.userId()));
  }

  @PatchMapping
  public ResponseEntity<MyProfileResponse> updateMyProfile(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody @Valid UpdateMyProfileRequest request) {
    return ResponseEntity.ok(userService.updateMyProfile(principal.userId(), request));
  }

  @DeleteMapping
  public ResponseEntity<Void> withdraw(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @Valid @RequestBody WithdrawRequest request,
      HttpServletResponse response) {
    userService.withdraw(principal.userId(), request);
    cookieUtil.clearAccessToken(response);
    return ResponseEntity.noContent().build();
  }
}
