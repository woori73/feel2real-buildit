package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.config.security.StudentAppTokenFilter;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.entity.StudentAppCode;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppLoginThrottle;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppPrincipals;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAppService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAppService.AppMe;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.AttemptSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.AttemptView;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.ReviewView;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.SaveAnswerRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.Scorecard;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.dto.TestPrepDtos.SubmitRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 재원생 앱 API(/api/naesintest/app). 이름·연락처는 받지 않고 원장이 발급한 접속 코드로만 학생을 알아본다.
 * 응시 API 모양은 /api/testprep/attempts와 같아서 시험·해설 화면(naesin_run.js·naesin_review.js)을 그대로 쓴다.
 */
@RestController
@RequestMapping("/api/naesintest/app")
public class StudentAppController {

  static final long SESSION_SECONDS = 180L * 24 * 3600; // 한 번 로그인하면 180일 유지(코드를 폐기하면 바로 끊긴다)

  private final StudentAppCodeService codes;
  private final StudentExamAppService app;
  private final StudentAppLoginThrottle throttle;
  private final JwtTokenProvider tokenProvider;
  private final JwtProperties jwtProperties;

  public StudentAppController(StudentAppCodeService codes, StudentExamAppService app, StudentAppLoginThrottle throttle,
      JwtTokenProvider tokenProvider, JwtProperties jwtProperties) {
    this.codes = codes;
    this.app = app;
    this.throttle = throttle;
    this.tokenProvider = tokenProvider;
    this.jwtProperties = jwtProperties;
  }

  public record LoginRequest(String code) {}

  @PostMapping("/login")
  public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req, HttpServletRequest request,
      HttpServletResponse response) {
    if (!throttle.tryAcquire(request.getRemoteAddr(), System.currentTimeMillis())) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(Map.of("message", "잠시 뒤에 다시 시도해 주세요(10분에 10번까지)."));
    }
    Optional<StudentAppCode> code = codes.login(req == null ? null : req.code());
    if (code.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(Map.of("message", "코드가 맞지 않거나 사용할 수 없는 코드입니다. 원장님께 확인해 주세요."));
    }
    AuthenticatedPrincipal p = StudentAppPrincipals.of(code.get());
    String token = tokenProvider.issue(p.userId(), p.loginId(), p.role(), null, false, SESSION_SECONDS);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie(token, SESSION_SECONDS).toString());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0).toString());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public AppMe me(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return app.me(require(p));
  }

  public record StartRequest(String examCode) {}

  @PostMapping("/attempts")
  public AttemptView start(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestBody StartRequest req) {
    return app.start(require(p), req == null ? null : req.examCode());
  }

  @GetMapping("/attempts")
  public List<AttemptSummary> history(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    return app.history(require(p));
  }

  @GetMapping("/attempts/{attemptId}")
  public AttemptView resume(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable Long attemptId) {
    return app.resume(require(p), attemptId);
  }

  @PutMapping("/attempts/{attemptId}/answers")
  public ResponseEntity<Void> save(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable Long attemptId,
      @RequestBody SaveAnswerRequest req) {
    app.saveAnswer(require(p), attemptId, req);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/attempts/{attemptId}/submit")
  public Scorecard submit(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable Long attemptId,
      @RequestBody(required = false) SubmitRequest req) {
    return app.submit(require(p), attemptId, req);
  }

  @GetMapping("/attempts/{attemptId}/review")
  public ReviewView review(@AuthenticationPrincipal AuthenticatedPrincipal p, @PathVariable Long attemptId) {
    return app.review(require(p), attemptId);
  }

  private static AuthenticatedPrincipal require(AuthenticatedPrincipal p) {
    if (!StudentAppPrincipals.is(p)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "접속 코드로 로그인해 주세요.");
    return p;
  }

  private ResponseCookie cookie(String value, long maxAge) {
    return ResponseCookie.from(StudentAppTokenFilter.COOKIE, value).httpOnly(true).secure(jwtProperties.secureCookie())
        .sameSite("Lax").path("/").maxAge(maxAge).build();
  }
}
