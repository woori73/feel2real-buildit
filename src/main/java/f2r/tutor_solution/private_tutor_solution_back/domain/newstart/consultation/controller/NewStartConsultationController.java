package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationCreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationReplyRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationSummaryView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.ConsultationThreadView;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service.NewStartConsultationService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
 * 감사회원 1:1 비공개 상담 API(회원 측). {@code /api/**} catch-all로 로그인 회원만 도달하고,
 * 서비스의 {@code requireThanksMember}가 감사회원만 통과시킨다(§1). 소유권 HARD GATE는 서비스에서 강제.
 *
 * <p>발신 회원은 서버 principal에서만 결정된다 — 요청값으로 userId·authorRole을 받지 않는다.
 */
@RestController
@RequestMapping("/api/newstart/consultations")
public class NewStartConsultationController {

  private final NewStartConsultationService consultationService;

  public NewStartConsultationController(NewStartConsultationService consultationService) {
    this.consultationService = consultationService;
  }

  /** 새 상담 작성(첫 질문 + 필요하면 채무상황 연결). */
  @PostMapping
  public ResponseEntity<ConsultationThreadView> create(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @RequestBody ConsultationCreateRequest request) {
    ConsultationThreadView view = consultationService.create(principal,
        request == null ? null : request.content(),
        request != null && request.linkDebt());
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  /** 내 상담 목록(§7). */
  @GetMapping
  public ResponseEntity<List<ConsultationSummaryView>> list(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(consultationService.myConsultations(principal));
  }

  /** 상담 스레드 상세(§7 시간순 대화·답변). */
  @GetMapping("/{consultationId}")
  public ResponseEntity<ConsultationThreadView> thread(
      @AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable Long consultationId) {
    return ResponseEntity.ok(consultationService.thread(principal, consultationId));
  }

  /** 같은 상담에 추가 질문(§2). */
  @PostMapping("/{consultationId}/messages")
  public ResponseEntity<ConsultationThreadView> reply(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable Long consultationId,
      @RequestBody ConsultationReplyRequest request) {
    return ResponseEntity.ok(consultationService.reply(principal, consultationId,
        request == null ? null : request.content()));
  }

  /** 헤더 배지용 — 아직 못 본 운영자 답변 총계(§7 "새 답변 N건"). */
  @GetMapping("/unread-count")
  public ResponseEntity<Map<String, Long>> unreadCount(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(Map.of("unreadAnswerCount", consultationService.unreadAnswerCount(principal)));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(Map.of("status", "error",
        "errorMessage", e.getMessage() == null ? "요청을 처리할 수 없습니다." : e.getMessage()));
  }

  /** 운영자 전용 조사 결과는 회원 API로 절대 내려가지 않는다(§5) — 회원 경로에 조사 엔드포인트를 두지 않는다. */
}
