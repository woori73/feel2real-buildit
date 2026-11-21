package f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.QrSvg;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService.CodeStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentAppCodeService.IssuedCode;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAdminService.ExamOption;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAdminService.StudentPanel;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAdminService.StudentRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.studentapp.service.StudentExamAdminService.ViewerView;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 재원생 앱 관리 API(원장 전용 — /api/admin/** 는 ROLE_ADMIN). 접속 코드 발급·재발급·폐기, 시험 배정, 응시 기록을 볼 사람 지정.
 */
@RestController
@RequestMapping("/api/admin/easy/student-app")
public class StudentAppAdminController {

  private final EasyTutorAccessService access;
  private final StudentAppCodeService codes;
  private final StudentExamAdminService admin;

  public StudentAppAdminController(EasyTutorAccessService access, StudentAppCodeService codes, StudentExamAdminService admin) {
    this.access = access;
    this.codes = codes;
    this.admin = admin;
  }

  @GetMapping("/students")
  public List<StudentRow> students(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId) {
    access.requireAllowedBusiness(p, businessId);
    return admin.students(businessId);
  }

  @GetMapping("/students/{studentId}")
  public StudentPanel panel(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long studentId) {
    access.requireAllowedBusiness(p, businessId);
    return admin.panel(businessId, studentId);
  }

  /** 코드 발급(이미 있으면 재발급 — 이전 코드는 바로 못 씀). 코드 원문과 QR은 이 응답에서만 보인다. */
  @PostMapping("/students/{studentId}/code")
  public Map<String, Object> issue(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long studentId) {
    access.requireAllowedBusiness(p, businessId);
    IssuedCode issued = codes.issue(businessId, studentId, p.userId());
    String url = ServletUriComponentsBuilder.fromCurrentContextPath().path(StudentAppCodeService.APP_PATH).toUriString()
        + "#code=" + issued.code();
    return Map.of("code", issued.display(), "appUrl", url, "qrSvg", QrSvg.of(url), "status", issued.status());
  }

  @DeleteMapping("/students/{studentId}/code")
  public CodeStatus revoke(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long studentId) {
    access.requireAllowedBusiness(p, businessId);
    return codes.revoke(businessId, studentId);
  }

  @GetMapping("/exams")
  public List<ExamOption> exams() {
    return admin.exams();
  }

  public record AssignRequest(String examCode) {}

  @PostMapping("/students/{studentId}/assignments")
  public StudentPanel assign(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long studentId, @RequestBody AssignRequest req) {
    access.requireAllowedBusiness(p, businessId);
    return admin.assign(businessId, studentId, req == null ? null : req.examCode(), p.userId());
  }

  @DeleteMapping("/students/{studentId}/assignments")
  public StudentPanel unassign(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long studentId, @RequestParam String examCode) {
    access.requireAllowedBusiness(p, businessId);
    return admin.unassign(businessId, studentId, examCode);
  }

  @GetMapping("/viewers")
  public List<ViewerView> viewers(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId) {
    access.requireAllowedBusiness(p, businessId);
    return admin.viewers(businessId);
  }

  public record ViewerRequest(String loginId) {}

  @PostMapping("/viewers")
  public List<ViewerView> addViewer(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @RequestBody ViewerRequest req) {
    access.requireAllowedBusiness(p, businessId);
    return admin.addViewer(businessId, req == null ? null : req.loginId(), p.userId());
  }

  @DeleteMapping("/viewers/{userId}")
  public List<ViewerView> removeViewer(@AuthenticationPrincipal AuthenticatedPrincipal p, @RequestParam Long businessId,
      @PathVariable Long userId) {
    access.requireAllowedBusiness(p, businessId);
    return admin.removeViewer(businessId, userId);
  }
}
