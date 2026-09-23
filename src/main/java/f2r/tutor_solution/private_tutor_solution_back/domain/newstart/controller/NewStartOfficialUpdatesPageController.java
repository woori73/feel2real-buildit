package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.officialupdate.service.NewStartOfficialUpdateService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class NewStartOfficialUpdatesPageController {

  private final NewStartOfficialUpdateService officialUpdateService;

  @GetMapping("/newstart/updates")
  public String updates(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/updates";
  }

  /**
   * 최신 정보 내부 상세보기 — 사용자를 외부 기관 사이트로 이동시키지 않고
   * 수집·저장된 공식 원문을 NewStart 안에서 열람하게 한다. {id:\\d+} 로 두어
   * 숫자가 아닌 경로는 목록 페이지와 충돌하지 않고 404 처리된다.
   */
  @GetMapping("/newstart/updates/{id:\\d+}")
  public String updateDetail(@PathVariable long id, Model model, HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    try {
      model.addAttribute("detail", officialUpdateService.publicUpdateDetail(id));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시된 정보를 찾을 수 없습니다.");
    }
    return "newstart/update_detail";
  }

  @GetMapping("/admin/newstart/updates")
  public String adminUpdates(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      HttpServletResponse response) {
    if (principal == null || !"ADMIN".equals(principal.role())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "운영자만 접근할 수 있습니다.");
    }
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "admin/newstart_updates";
  }
}
