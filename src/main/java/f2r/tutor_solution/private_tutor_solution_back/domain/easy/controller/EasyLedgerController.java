package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyDashboardResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyTxnResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyTxnDirection;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyLedgerService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Easy 장부 — 월 요약 + 거래 CRUD. {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/easy")
@RequiredArgsConstructor
public class EasyLedgerController {

  private final EasyLedgerService ledgerService;

  @GetMapping("/summary")
  public ResponseEntity<EasyDashboardResponse> summary(
      @RequestParam(value = "businessId", required = false) Long businessId,
      @RequestParam(value = "month", required = false)
      @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    return ResponseEntity.ok(ledgerService.summary(businessId, month));
  }

  @GetMapping("/transactions")
  public ResponseEntity<List<EasyTxnResponse>> listTxns(
      @RequestParam(value = "businessId", required = false) Long businessId,
      @RequestParam(value = "from", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(value = "to", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(value = "direction", required = false) String direction,
      @RequestParam(value = "category", required = false) String category) {
    EasyTxnDirection parsed = null;
    if (direction != null && !direction.isBlank()) {
      try {
        parsed = EasyTxnDirection.valueOf(direction.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("수입/지출 값이 올바르지 않습니다.");
      }
    }
    return ResponseEntity.ok(ledgerService.listTxns(businessId, from, to, parsed, category));
  }

  @PostMapping("/transactions")
  public ResponseEntity<EasyTxnResponse> createTxn(@RequestBody EasyTxnRequest request) {
    return ResponseEntity.ok(ledgerService.createTxn(request));
  }

  @PutMapping("/transactions/{txnId}")
  public ResponseEntity<EasyTxnResponse> updateTxn(
      @PathVariable Long txnId, @RequestBody EasyTxnRequest request) {
    return ResponseEntity.ok(ledgerService.updateTxn(txnId, request));
  }

  @DeleteMapping("/transactions/{txnId}")
  public ResponseEntity<Void> deleteTxn(@PathVariable Long txnId) {
    ledgerService.deleteTxn(txnId);
    return ResponseEntity.noContent().build();
  }
}
