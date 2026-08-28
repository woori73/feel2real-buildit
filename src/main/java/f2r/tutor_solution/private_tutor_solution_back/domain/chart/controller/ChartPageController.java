package f2r.tutor_solution.private_tutor_solution_back.domain.chart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.chart.dto.ChartPublicEducatorDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.chart.service.ChartEducatorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class ChartPageController {

  private final ChartEducatorSearchService chartEducatorSearchService;

  @GetMapping("/chart")
  public String publicChart() {
    return "public/chart";
  }

  /**
   * 교육자 공개 상세 페이지 — 공유·뒤로가기·검색엔진용 안정 URL. 실제 데이터는 클라이언트가
   * {@code /api/chart/educators/{id}} 와 {@code /api/chart/rankings/{id}} 로 채우고, 여기서는
   * SSR 로 제목·메타·기본 식별 정보만 심는다. 비공개/미존재 교육자는 /chart 로 되돌린다.
   */
  @GetMapping("/chart/educators/{id:\\d+}")
  public String publicChartEducator(@PathVariable long id, Model model) {
    ChartPublicEducatorDetail detail;
    try {
      detail = chartEducatorSearchService.getById(id);
    } catch (ResponseStatusException notFound) {
      return "redirect:/chart";
    }
    model.addAttribute("educatorId", id);
    model.addAttribute("educatorName", detail.name());
    model.addAttribute("educatorRole", detail.role());
    model.addAttribute("educatorAcademy", detail.academyName());
    String region = detail.activityRegion() != null && !detail.activityRegion().isBlank()
        ? detail.activityRegion()
        : (nz(detail.province()) + " " + nz(detail.cityDistrict())).trim();
    model.addAttribute("educatorRegion", region);
    model.addAttribute("educatorSummary", firstNonBlank(detail.identitySummary(), detail.educationPhilosophy()));
    model.addAttribute("researchPending", detail.researchPending());
    return "public/chart_educator";
  }

  @GetMapping("/admin/chart")
  public String adminChart() {
    return "admin/chart";
  }

  private static String nz(String s) { return s == null ? "" : s; }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    return b == null || b.isBlank() ? null : b;
  }
}
