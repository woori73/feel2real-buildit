package f2r.tutor_solution.private_tutor_solution_back.domain.testprep.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.testprep.service.NaesinAccessService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내신 실전 연습 이용 범위 조회. 로그인 필요(/api/** 규칙). 화면은 이 값으로 2회부터의 시작 단추를 잠근다. */
@RestController
@RequiredArgsConstructor
public class NaesinTestApiController {

  private final NaesinAccessService naesinAccessService;

  /** full=true면 전 회차(관리자·재원생), false면 1회만. */
  @GetMapping("/api/naesintest/access")
  public Map<String, Object> access(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    boolean full = naesinAccessService.hasFullAccess(principal);
    return Map.of("full", full, "message", full ? "" : NaesinAccessService.LOCKED_MESSAGE);
  }
}
