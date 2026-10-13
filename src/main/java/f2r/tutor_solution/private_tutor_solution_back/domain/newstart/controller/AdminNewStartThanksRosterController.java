package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksRosterAdminService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksRosterAdminService.AddResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksRosterAdminService.RosterView;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사회원 명단 수동 관리 — 운영자 API.
 *
 * <p>명단은 DB 데이터이므로 이 API로 추가하면 재배포 없이 회원가입 시 즉시 적용된다
 * (uniqueMatch 로직이 DB를 직접 읽기 때문).
 *
 * <p>권한은 경로가 결정한다({@code /api/admin/**} = ROLE_ADMIN).
 */
@RestController
@RequestMapping("/api/admin/newstart/thanks-roster")
public class AdminNewStartThanksRosterController {

  private final NewStartThanksRosterAdminService rosterAdminService;

  public AdminNewStartThanksRosterController(NewStartThanksRosterAdminService rosterAdminService) {
    this.rosterAdminService = rosterAdminService;
  }

  /** 수동 등록분 목록 (최근 N건). */
  @GetMapping
  public List<RosterView> list(@RequestParam(defaultValue = "50") int limit) {
    return rosterAdminService.listManual(Math.min(Math.max(limit, 1), 200));
  }

  /** 명단 1건 수동 추가. */
  @PostMapping
  public ResponseEntity<AdminResponse> add(@RequestBody(required = false) AddRequest request) {
    AddResult r = rosterAdminService.addManual(
        request == null ? null : request.nickname(),
        request == null ? null : request.naverIdPrefix(),
        request == null ? null : request.gradeChangedAt());
    return r.ok() ? ResponseEntity.ok(new AdminResponse("ok", null, r.roster()))
        : ResponseEntity.badRequest().body(new AdminResponse("error", r.errorMessage(), null));
  }

  /** 미연결 수동 등록분 삭제. */
  @DeleteMapping("/{rosterId}")
  public ResponseEntity<AdminResponse> remove(@PathVariable Long rosterId) {
    AddResult r = rosterAdminService.removeManual(rosterId);
    return r.ok() ? ResponseEntity.ok(new AdminResponse("ok", null, null))
        : ResponseEntity.badRequest().body(new AdminResponse("error", r.errorMessage(), null));
  }

  public record AddRequest(String nickname, String naverIdPrefix, LocalDate gradeChangedAt) {}

  public record AdminResponse(String status, String errorMessage, RosterView roster) {}
}
