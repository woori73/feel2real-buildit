package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 회원 1:1 비공개 상담 화면 진입점. 페이지 자체는 공개 셸(비로그인은 JS가 로그인 안내)이고,
 * 실제 상담 데이터는 {@code /api/newstart/consultations}(인증 + 소유권 판정)에서만 내려온다.
 * questions/galmuri 화면과 동일한 셸 + 인증 API 패턴(설계 §7).
 *
 * <p>2026-10 정책: 1:1 비공개 상담은 일반회원에게 허용된 기능이며 감사회원 요건을 두지 않는다.
 */
@Controller
public class NewStartConsultationPageController {

  @GetMapping("/newstart/consultations")
  public String consultations(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
    return "newstart/consultations";
  }
}
