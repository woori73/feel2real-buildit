package f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetAssignmentService;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetAssignmentService.AssignRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.worksheet.service.WorksheetAssignmentService.Panel;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학습지 배정 API. 학생 이름이 나오므로 학생부·출결과 같이 관리자(ADMIN) 전용(/api/admin/**)이다.
 * 모든 요청에 ?businessId=.
 */
@RestController
@RequestMapping("/api/admin/easy/worksheets")
public class WorksheetAssignmentController {

  private final EasyTutorAccessService access;
  private final WorksheetAssignmentService service;

  public WorksheetAssignmentController(EasyTutorAccessService access, WorksheetAssignmentService service) {
    this.access = access;
    this.service = service;
  }

  @GetMapping("/{worksheetId}/assignments")
  public Panel panel(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long worksheetId) {
    access.requireAllowedBusiness(p, businessId);
    return service.panel(businessId, worksheetId);
  }

  @PostMapping("/{worksheetId}/assignments")
  public Map<String, Object> assign(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long worksheetId, @RequestBody AssignRequest req) {
    access.requireAllowedBusiness(p, businessId);
    int changed = service.assign(businessId, worksheetId, req, p.userId());
    return Map.of("assignedCount", changed, "panel", service.panel(businessId, worksheetId));
  }

  @PutMapping("/assignments/{assignmentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changeStatus(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long assignmentId, @RequestParam String status) {
    access.requireAllowedBusiness(p, businessId);
    service.changeStatus(businessId, assignmentId, status);
  }

  @DeleteMapping("/assignments/{assignmentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long assignmentId) {
    access.requireAllowedBusiness(p, businessId);
    service.remove(businessId, assignmentId);
  }
}
