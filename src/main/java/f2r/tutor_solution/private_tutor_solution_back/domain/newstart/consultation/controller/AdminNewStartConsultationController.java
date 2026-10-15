package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.AdminAnswerRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.AdminConsultationSummaryView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.AdminConsultationThreadView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationMessageView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service.NewStartConsultationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 상담함 API(§3·§4·§5). {@code /api/admin/**}는 SecurityConfig에서 ROLE_ADMIN 필수지만,
 * 서비스 {@code requireAdmin}으로 이중 확인한다(직접 호출 방어).
 *
 * <p>{@code /research}는 익명화된 질문을 지피지기에 조사해 <b>운영자 전용</b> 결과를 돌려줄 뿐,
 * 회원에게 아무것도 전송하지 않는다. 회원에게 전달하는 유일한 경로는 {@code /answer}다.
 */
@RestController
@RequestMapping("/api/admin/newstart/consultations")
public class AdminNewStartConsultationController {

  private final NewStartConsultationService consultationService;

  public AdminNewStartConsultationController(NewStartConsultationService consultationService) {
    this.consultationService = consultationService;
  }

  @GetMapping
  public ResponseEntity<List<AdminConsultationSummaryView>> list(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(consultationService.adminList(principal));
  }

  @GetMapping("/{consultationId}")
  public ResponseEntity<AdminConsultationThreadView> thread(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long consultationId) {
    return ResponseEntity.ok(consultationService.adminThread(principal, consultationId));
  }

  /** [회원에게 답변] — 운영자가 작성·보완한 답변만 회원 스레드에 전달(§5). */
  @PostMapping("/{consultationId}/answer")
  public ResponseEntity<ConsultationMessageView> answer(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long consultationId,
      @RequestBody AdminAnswerRequest request) {
    return ResponseEntity.ok(consultationService.adminAnswer(principal, consultationId,
        request == null ? null : request.content()));
  }

  /** [지피지기에게 조사시키기] — 결과는 운영자 전용. 회원에게 자동 전송되지 않는다(§4·§5). */
  @PostMapping("/{consultationId}/research")
  public ResponseEntity<ConsultationMessageView> research(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long consultationId) {
    return ResponseEntity.ok(consultationService.adminResearch(principal, consultationId));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("status", "error",
        "errorMessage", e.getMessage() == null ? "요청을 처리할 수 없습니다." : e.getMessage()));
  }
}
