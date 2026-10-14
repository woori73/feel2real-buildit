package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyReviewItemResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceMonthlyResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyWorkspaceTransactionResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyAuditService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyFinancialStatementService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerInquiryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTutorAccessService;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyWorkspaceService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tutor/easy")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TUTOR')")
public class EasyTutorController {

  private final EasyTutorAccessService accessService;
  private final EasyWorkspaceService workspaceService;
  private final EasyAuditService auditService;
  private final EasyLedgerInquiryService inquiryService;
  private final EasyFinancialStatementService statementService;

  @GetMapping("/businesses")
  public ResponseEntity<List<EasyBusinessResponse>> businesses(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal) {
    return ResponseEntity.ok(accessService.allowedBusinesses(principal));
  }

  @GetMapping("/workspace/monthly")
  public ResponseEntity<EasyWorkspaceMonthlyResponse> monthly(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(workspaceService.monthly(businessId, month));
  }

  @GetMapping("/workspace/transactions")
  public ResponseEntity<List<EasyWorkspaceTransactionResponse>> transactions(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Long accountId) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(workspaceService.transactions(
        businessId, from, to, parseDirection(direction), category, accountId));
  }

  @GetMapping("/workspace/transactions/{txnId}")
  public ResponseEntity<EasyWorkspaceTransactionResponse> transaction(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @PathVariable Long txnId) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(workspaceService.transaction(businessId, txnId));
  }

  @GetMapping("/workspace/reviews")
  public ResponseEntity<List<EasyReviewItemResponse>> reviews(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam(required = false) String kind,
      @RequestParam(required = false) String status) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(auditService.listReviews(businessId, kind, status));
  }

  @GetMapping("/accounting/general-ledger")
  public ResponseEntity<EasyLedgerInquiryService.AccountLedger> generalLedger(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam Long accountId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(inquiryService.generalLedger(businessId, accountId, from, to));
  }

  @GetMapping("/accounting/trial-balance")
  public ResponseEntity<EasyLedgerInquiryService.TrialBalanceReport> trialBalance(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(inquiryService.trialBalance(businessId, from, to));
  }

  @GetMapping("/accounting/balance-audit")
  public ResponseEntity<EasyLedgerInquiryService.BalanceAudit> balanceAudit(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(inquiryService.auditBalance(businessId, from, to));
  }

  @GetMapping("/accounting/income-statement")
  public ResponseEntity<EasyFinancialStatementService.IncomeStatement> incomeStatement(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate to) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(statementService.incomeStatement(businessId, from, to));
  }

  @GetMapping("/accounting/balance-sheet")
  public ResponseEntity<EasyFinancialStatementService.BalanceSheet> balanceSheet(
      @AuthenticationPrincipal JwtTokenProvider.AuthenticatedPrincipal principal,
      @RequestParam Long businessId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate at) {
    accessService.requireAllowedBusiness(principal, businessId);
    return ResponseEntity.ok(statementService.balanceSheet(businessId, at));
  }

  private EasyTxnDirection parseDirection(String direction) {
    if (direction == null || direction.isBlank()) {
      return null;
    }
    try {
      return EasyTxnDirection.valueOf(direction.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("수입/지출 값이 올바르지 않습니다.");
    }
  }
}
