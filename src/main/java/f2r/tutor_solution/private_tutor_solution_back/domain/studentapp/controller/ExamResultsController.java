package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyBusiness;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.repository.EasyBusinessRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAdminService.ResultView;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * '새 응시 기록'(재원생 앱 응시 기록) API. 원장(ADMIN)과 원장이 지정한 강사만 본다.
 * 화면: /dashboard/exam-results. 응시 기록은 지우는 기능이 없고, 점검 표시만 남긴다.
 */
@RestController
@RequestMapping("/api/exam-results")
public class ExamResultsController {

  private final StudentExamAdminService admin;
  private final EasyBusinessRepository businesses;

  public ExamResultsController(StudentExamAdminService admin, EasyBusinessRepository businesses) {
    this.admin = admin;
    this.businesses = businesses;
  }

  public record BusinessOption(Long businessId, String name) {}

  /** 볼 수 있는 사업체: 원장은 활성 사업체 전부, 지정받은 강사는 지정받은 곳만. */
  @GetMapping("/businesses")
  public List<BusinessOption> businesses(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    List<EasyBusiness> list = "ADMIN".equals(p.role())
        ? businesses.findAll().stream().filter(EasyBusiness::isActive).toList()
        : businesses.findAllById(admin.viewerBusinesses(p)).stream().filter(EasyBusiness::isActive).toList();
    return list.stream().map(b -> new BusinessOption(b.getBusinessId(), b.getName())).toList();
  }

  @GetMapping
  public Map<String, Object> list(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @RequestParam(defaultValue = "true") boolean onlyNew) {
    admin.requireViewer(p, businessId);
    return Map.of("newCount", admin.newCount(businessId), "results", admin.results(businessId, onlyNew));
  }

  @PostMapping("/{resultId}/check")
  public ResultView check(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long resultId) {
    admin.requireViewer(p, businessId);
    return admin.check(businessId, resultId, p.userId());
  }
}
