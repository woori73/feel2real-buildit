package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyPaymentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.entity.EasyPaymentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyTuitionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
 * Easy 월 수납 — {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/easy/payments")
@RequiredArgsConstructor
public class EasyTuitionController {

  private final EasyTuitionService tuitionService;

  @GetMapping
  public ResponseEntity<List<EasyPaymentResponse>> list(
      @RequestParam(value = "studentId", required = false) Long studentId,
      @RequestParam(value = "month", required = false) String targetMonth,
      @RequestParam(value = "status", required = false) String status) {
    EasyPaymentStatus parsed = null;
    if (status != null && !status.isBlank()) {
      try {
        parsed = EasyPaymentStatus.valueOf(status.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("수납 상태 값이 올바르지 않습니다.");
      }
    }
    return ResponseEntity.ok(tuitionService.list(studentId, targetMonth, parsed));
  }

  @PostMapping
  public ResponseEntity<EasyPaymentResponse> create(@RequestBody EasyPaymentRequest request) {
    return ResponseEntity.ok(tuitionService.create(request));
  }

  @PutMapping("/{paymentId}")
  public ResponseEntity<EasyPaymentResponse> update(
      @PathVariable Long paymentId, @RequestBody EasyPaymentRequest request) {
    return ResponseEntity.ok(tuitionService.update(paymentId, request));
  }

  @DeleteMapping("/{paymentId}")
  public ResponseEntity<Void> delete(@PathVariable Long paymentId) {
    tuitionService.delete(paymentId);
    return ResponseEntity.noContent().build();
  }
}
