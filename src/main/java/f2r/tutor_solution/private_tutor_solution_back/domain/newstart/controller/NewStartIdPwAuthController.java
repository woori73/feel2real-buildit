package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.CookieUtil;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartIdPwSignupService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartIdPwSignupService.MemberType;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartIdPwSignupService.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NewStart ID/PW 가입 API(공개). 가입 성공 시 바로 로그인 쿠키를 발급한다. 로그인 자체는 기존 {@code POST /api/auth/login}.
 * 응답은 회원 유형(감사회원/일반회원)만 알리고, 감사회원 판정의 세부 조건은 드러내지 않는다.
 */
@RestController
@RequestMapping("/api/newstart/auth")
public class NewStartIdPwAuthController {

  private final NewStartIdPwSignupService signupService;
  private final JwtTokenProvider tokenProvider;
  private final CookieUtil cookieUtil;

  public NewStartIdPwAuthController(NewStartIdPwSignupService signupService, JwtTokenProvider tokenProvider, CookieUtil cookieUtil) {
    this.signupService = signupService;
    this.tokenProvider = tokenProvider;
    this.cookieUtil = cookieUtil;
  }

  public record SignupRequest(String loginId, String password, String cafeNickname) {}

  public record SignupResponse(boolean ok, String message, String memberType) {}

  @PostMapping("/signup")
  public ResponseEntity<SignupResponse> signup(@RequestBody(required = false) SignupRequest request, HttpServletResponse response) {
    if (request == null) return ResponseEntity.badRequest().body(new SignupResponse(false, "입력값이 없습니다.", null));
    Result r;
    try {
      r = signupService.signup(request.loginId(), request.password(), request.cafeNickname());
    } catch (DataIntegrityViolationException e) {   // 동시 가입(아이디 UNIQUE)
      return ResponseEntity.status(409).body(new SignupResponse(false, "이미 가입된 아이디입니다. 로그인해 주세요.", null));
    }
    if (!r.ok()) return ResponseEntity.badRequest().body(new SignupResponse(false, r.errorMessage(), null));
    cookieUtil.writeAccessToken(response, tokenProvider.issue(r.userId(), r.loginId(), r.role()));
    boolean thanks = r.memberType() == MemberType.THANKS;
    return ResponseEntity.ok(new SignupResponse(true,
        thanks ? "가입되었습니다. 감사회원으로 확인되었습니다." : "가입되었습니다. 일반회원으로 로그인됩니다. 감사회원은 운영자 확인 후 변경됩니다.",
        thanks ? "THANKS" : "GENERAL"));
  }
}
