package f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.wikistats.service.WikiVisitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /wiki 방문 비컨. wiki.js 가 허브 로드 시 한 번 호출한다. 비회원 포함 공개(SecurityConfig permitAll).
 * 중복 방지는 {@link WikiVisitService} 가 HttpSession 으로 처리한다.
 */
@RestController
@RequiredArgsConstructor
public class WikiVisitController {

  private final WikiVisitService wikiVisitService;

  @PostMapping("/api/wiki/visits")
  public ResponseEntity<Void> record(HttpServletRequest request) {
    wikiVisitService.recordVisit(request.getSession(true));
    return ResponseEntity.noContent().build();
  }
}
