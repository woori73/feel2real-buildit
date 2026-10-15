package f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.dto.BetaCommentCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.betacomment.service.BetaCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 랜딩 '/' 베타 의견 등록.
 *
 * <p>인증은 {@code SecurityConfig}의 {@code /api/**} catch-all(`.authenticated()`)이 담당한다 —
 * 별도 permitAll 예외를 두지 않으므로 미로그인 요청은 컨트롤러에 닿기 전에 401이 된다.
 *
 * <p>등록 결과는 항상 PENDING이라 응답 본문에 담을 것이 없다. 승인 전에는 랜딩에 나오지 않으므로
 * 클라이언트는 성공 여부만 알면 된다.
 */
@RestController
@RequestMapping("/api/beta-comments")
@RequiredArgsConstructor
public class BetaCommentController {

  private final BetaCommentService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public void register(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestBody BetaCommentCreateRequest request) {
    service.register(principal.userId(), request);
  }
}
