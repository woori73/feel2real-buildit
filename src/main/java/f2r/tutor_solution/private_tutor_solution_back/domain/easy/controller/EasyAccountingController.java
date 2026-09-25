package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyCutover;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyOpeningService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyOpeningService.OpeningLeg;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Easy 회계 조회·개시 — 총계정원장/시산표/분개감사 + 개시잔액/cut-over.
 * {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 * 기간 마감/해제는 기존 closings API를 재사용한다 (EasyAuditController).
 * 모든 수치는 journal_entries + journal_lines에서 계산한다.
 */
@RestController
@RequestMapping("/api/admin/easy/accounting")
@RequiredArgsConstructor
public class EasyAccountingController {

  private final EasyLedgerInquiryService inquiryService;
  private final EasyOpeningService openingService;

  /** 총계정원장 — GET /api/admin/easy/accounting/general-ledger. */
  @GetMapping("/general-ledger")
  public ResponseEntity<EasyLedgerInquiryService.AccountLedger> generalLedger(
      @RequestParam Long businessId,
      @RequestParam Long accountId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to) {
    return ResponseEntity.ok(inquiryService.generalLedger(businessId, accountId, from, to));
  }

  /** 시산표 — GET /api/admin/easy/accounting/trial-balance. */
  @GetMapping("/trial-balance")
  public ResponseEntity<EasyLedgerInquiryService.TrialBalanceReport> trialBalance(
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to) {
    return ResponseEntity.ok(inquiryService.trialBalance(businessId, from, to));
  }

  /** 분개 균형 감사 — GET /api/admin/easy/accounting/balance-audit (read-only). */
  @GetMapping("/balance-audit")
  public ResponseEntity<EasyLedgerInquiryService.BalanceAudit> balanceAudit(
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to) {
    return ResponseEntity.ok(inquiryService.auditBalance(businessId, from, to));
  }

  /** 개시잔액 분개 + DRAFT cut-over — POST /api/admin/easy/accounting/openings. */
  @PostMapping("/openings")
  public ResponseEntity<EasyCutover> postOpening(@RequestBody EasyOpeningRequest request) {
    EasyCutover cutover = openingService.postOpening(request.businessId(),
        request.cutoverDate(), request.legs(), request.memo());
    return ResponseEntity.ok(cutover);
  }

  /** 전환 확정 — POST /api/admin/easy/accounting/cutovers/{businessId}/approve. */
  @PostMapping("/cutovers/{businessId}/approve")
  public ResponseEntity<EasyCutover> approveCutover(@PathVariable Long businessId) {
    return ResponseEntity.ok(openingService.approveCutover(businessId));
  }

  public record EasyOpeningRequest(Long businessId, LocalDate cutoverDate,
      List<OpeningLeg> legs, String memo) {
  }
}
