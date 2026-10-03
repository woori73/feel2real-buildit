package f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.TestCleanupRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.dto.TestCleanupResult;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service.NewStartTestCleanupService;
import f2r.tutor_solution.private_tutor_solution_back.domain.newstart.consultation.service.TestCleanupVerificationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN 전용 테스트 데이터 정리 (임시 endpoint — 정리 완료 후 제거 예정).
 *
 * <p>{@code /api/admin/**} 규칙으로 ROLE_ADMIN만 도달한다. 검증 5조건 중 하나라도 불일치하면
 * 409 + 트랜잭션 전체 롤백 (아무것도 삭제되지 않음).
 */
@RestController
@RequestMapping("/api/admin/newstart/test-cleanup")
public class AdminNewStartTestCleanupController {

  private final NewStartTestCleanupService cleanupService;

  public AdminNewStartTestCleanupController(NewStartTestCleanupService cleanupService) {
    this.cleanupService = cleanupService;
  }

  @DeleteMapping
  public ResponseEntity<?> cleanup(@RequestBody TestCleanupRequest request) {
    try {
      TestCleanupResult result = cleanupService.cleanup(request);
      return ResponseEntity.ok(result);
    } catch (TestCleanupVerificationException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("status", "error", "errorMessage", e.getMessage()));
    }
  }
}
