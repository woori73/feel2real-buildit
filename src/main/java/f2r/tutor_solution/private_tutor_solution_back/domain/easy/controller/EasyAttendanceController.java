package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceRosterImportService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.ClassForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.ClassView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.CorrectForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.EventView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.MemberView;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.RecordForm;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAttendanceService.SheetRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * F2R 출결부 API — ADMIN 전용(/api/admin/** 보안 규칙) + 사업체 접근 확인. 삭제 API는 없다(출결은 정정, 반은 종료, 배정은 해제).
 */
@RestController
@RequestMapping("/api/admin/easy/attendance")
@RequiredArgsConstructor
public class EasyAttendanceController {

  private final EasyAttendanceService service;
  private final EasyAttendanceRosterImportService rosterImport;
  private final EasyTutorAccessService accessService;

  /** 상태 충돌(이미 정정됨·종료된 반·명단이 바뀜)은 409 + 안내 문장. */
  @ExceptionHandler(IllegalStateException.class)
  public org.springframework.http.ResponseEntity<Map<String, Object>> conflict(IllegalStateException e) {
    return org.springframework.http.ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
  }

  private void requireBusiness(AuthenticatedPrincipal p, Long businessId) {
    accessService.requireAllowedBusiness(p, businessId);
  }

  /** 학원 홈·MCP 의논용: 재원 학생의 반·수업 요일·등원 요약(읽기 전용). */
  @GetMapping("/student-overview")
  public List<EasyAttendanceService.StudentOverviewRow> studentOverview(@RequestParam Long businessId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.studentOverview(businessId);
  }

  @GetMapping("/students/{studentId}/weekdays")
  public EasyAttendanceService.WeekdayView studentWeekdays(@RequestParam Long businessId, @PathVariable Long studentId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.studentWeekdays(businessId, studentId);
  }

  @PutMapping("/students/{studentId}/weekdays")
  public EasyAttendanceService.WeekdayView updateStudentWeekdays(@RequestParam Long businessId, @PathVariable Long studentId,
      @RequestBody EasyAttendanceService.WeekdayForm form, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.updateStudentWeekdays(businessId, studentId, form);
  }

  @GetMapping("/classes")
  public List<ClassView> classes(@RequestParam Long businessId,
      @RequestParam(defaultValue = "false") boolean includeEnded,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.listClasses(businessId, includeEnded);
  }

  @PostMapping("/classes")
  public ClassView createClass(@RequestParam Long businessId, @RequestBody ClassForm form,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.createClass(businessId, form);
  }

  @PutMapping("/classes/{classId}")
  public ClassView updateClass(@RequestParam Long businessId, @PathVariable Long classId,
      @RequestBody ClassForm form, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.updateClass(businessId, classId, form);
  }

  @PostMapping("/classes/{classId}/end")
  public ClassView endClass(@RequestParam Long businessId, @PathVariable Long classId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.endClass(businessId, classId);
  }

  @GetMapping("/classes/{classId}/members")
  public List<MemberView> members(@RequestParam Long businessId, @PathVariable Long classId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.members(businessId, classId);
  }

  public record AssignRequest(Long studentId) {}

  public record MoveRequest(Long targetClassId) {}

  @PostMapping("/classes/{classId}/members")
  public MemberView assign(@RequestParam Long businessId, @PathVariable Long classId,
      @RequestBody AssignRequest request, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.assign(businessId, classId, request.studentId());
  }

  @PostMapping("/classes/{classId}/members/{studentId}/release")
  public Map<String, Object> release(@RequestParam Long businessId, @PathVariable Long classId,
      @PathVariable Long studentId, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    service.release(businessId, classId, studentId);
    return Map.of("released", true);
  }

  @PostMapping("/classes/{classId}/members/{studentId}/move")
  public MemberView move(@RequestParam Long businessId, @PathVariable Long classId,
      @PathVariable Long studentId, @RequestBody MoveRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.move(businessId, classId, studentId, request.targetClassId());
  }

  @GetMapping("/sheet")
  public List<SheetRow> sheet(@RequestParam Long businessId, @RequestParam Long classId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.sheet(businessId, classId, date);
  }

  @PostMapping("/events")
  public EventView record(@RequestParam Long businessId, @RequestBody RecordForm form,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.record(businessId, form);
  }

  @PostMapping("/events/{eventId}/correct")
  public EventView correct(@RequestParam Long businessId, @PathVariable Long eventId,
      @RequestBody CorrectForm form, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.correct(businessId, eventId, form);
  }

  @GetMapping("/students/{studentId}/events")
  public List<EventView> studentHistory(@RequestParam Long businessId, @PathVariable Long studentId,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.studentHistory(businessId, studentId);
  }

  @GetMapping("/monthly")
  public Map<String, Object> monthly(@RequestParam Long businessId, @RequestParam Long classId,
      @RequestParam String month, @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return service.monthly(businessId, classId, month);
  }

  @GetMapping("/roster-import/preview")
  public Map<String, Object> rosterPreview(@RequestParam Long businessId, @RequestParam int year,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, businessId);
    return rosterImport.preview(p, businessId, year);
  }

  @PostMapping("/roster-import/apply")
  public Map<String, Object> rosterApply(@RequestBody EasyAttendanceRosterImportService.ApplyRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal p) {
    requireBusiness(p, request.businessId());
    return rosterImport.apply(p, request);
  }
}
