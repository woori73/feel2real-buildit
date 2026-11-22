package f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillLinkService.AdminRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionBillLinkService.CreateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatchService.MatchRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.billing.TuitionDepositMatchService.RunSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수강료 청구 링크·입금 대조 관리 — {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 * 스위치(app.payment.enabled)가 꺼져 있으면 status만 답하고 나머지는 404다.
 */
@RestController
@RequestMapping("/api/admin/easy/billing")
@RequiredArgsConstructor
public class TuitionBillingAdminController {

  private final TuitionBillingSwitch billingSwitch;
  private final TuitionBillLinkService linkService;
  private final TuitionDepositMatchService matchService;

  public record RunRequest(Long businessId, LocalDate from, LocalDate to) {}

  public record ConfirmRequest(Long paymentId) {}

  @GetMapping("/status")
  public Map<String, Object> status() {
    return Map.of("enabled", billingSwitch.isEnabled(),
        "cardTest", billingSwitch.isCardTestEnabled());
  }

  @GetMapping("/links")
  public List<AdminRow> links(@RequestParam("month") String month) {
    billingSwitch.requireEnabled();
    return linkService.listForMonth(month, LocalDateTime.now());
  }

  /** 응답의 url은 이때 한 번만 볼 수 있다(서버에는 토큰 해시만 남는다). */
  @PostMapping("/links")
  public Map<String, Object> createLink(@RequestBody CreateRequest request) {
    billingSwitch.requireEnabled();
    TuitionBillLinkService.CreatedLink created = linkService.create(request, LocalDateTime.now());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("linkId", created.linkId());
    out.put("url", "/pay/" + created.token());
    out.put("depositorCode", created.depositorCode());
    out.put("expiresAt", created.expiresAt());
    return out;
  }

  @PostMapping("/links/{linkId}/revoke")
  public ResponseEntity<Void> revoke(@PathVariable Long linkId) {
    billingSwitch.requireEnabled();
    linkService.revoke(linkId, LocalDateTime.now());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/matches/run")
  public RunSummary run(@RequestBody RunRequest request) {
    billingSwitch.requireEnabled();
    return matchService.run(request.businessId(), request.from(), request.to(),
        LocalDateTime.now());
  }

  @GetMapping("/matches")
  public List<MatchRow> matches(
      @RequestParam(value = "applied", defaultValue = "false") boolean applied) {
    billingSwitch.requireEnabled();
    return matchService.list(applied);
  }

  @PostMapping("/matches/{matchId}/confirm")
  public ResponseEntity<Void> confirm(@PathVariable Long matchId,
      @RequestBody(required = false) ConfirmRequest request) {
    billingSwitch.requireEnabled();
    matchService.confirm(matchId, request == null ? null : request.paymentId(),
        LocalDateTime.now());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/matches/{matchId}/dismiss")
  public ResponseEntity<Void> dismiss(@PathVariable Long matchId) {
    billingSwitch.requireEnabled();
    matchService.dismiss(matchId, LocalDateTime.now());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/matches/{matchId}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable Long matchId) {
    billingSwitch.requireEnabled();
    matchService.cancel(matchId, LocalDateTime.now());
    return ResponseEntity.noContent().build();
  }
}
