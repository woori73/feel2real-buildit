package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksPreregistrationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksPreregistrationService.PreregView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.service.NewStartThanksVerificationService.Result;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 신규 감사회원 사전등록 — 운영자 전용 API(경로가 권한을 결정: {@code /api/admin/**} = ROLE_ADMIN). 이메일은 이 응답에만 나온다. */
@RestController
@RequestMapping("/api/admin/newstart/thanks-preregistrations")
public class AdminNewStartThanksPreregistrationController {

  private final NewStartThanksPreregistrationService service;

  public AdminNewStartThanksPreregistrationController(NewStartThanksPreregistrationService service) {
    this.service = service;
  }

  @GetMapping
  public List<PreregView> list() {
    return service.list();
  }

  @PostMapping
  public ResponseEntity<AdminResponse> register(@RequestBody(required = false) RegisterRequest request) {
    try {
      Result<PreregView> r = service.register(request == null ? null : request.email(), request == null ? null : request.memo());
      return r.ok() ? ResponseEntity.ok(new AdminResponse("ok", null, r.value()))
          : ResponseEntity.badRequest().body(new AdminResponse("error", r.errorMessage(), null));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new AdminResponse("error", "이미 사전등록된 이메일입니다.", null));
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<AdminResponse> remove(@PathVariable Long id) {
    Result<Void> r = service.remove(id);
    return r.ok() ? ResponseEntity.ok(new AdminResponse("ok", null, null))
        : ResponseEntity.badRequest().body(new AdminResponse("error", r.errorMessage(), null));
  }

  public record RegisterRequest(String email, String memo) {}

  public record AdminResponse(String status, String errorMessage, PreregView preregistration) {}
}
