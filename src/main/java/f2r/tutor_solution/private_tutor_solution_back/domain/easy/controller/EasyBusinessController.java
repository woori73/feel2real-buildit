package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyBusinessResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyBusinessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Easy 사업체 — {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/easy/businesses")
@RequiredArgsConstructor
public class EasyBusinessController {

  private final EasyBusinessService businessService;

  @GetMapping
  public ResponseEntity<List<EasyBusinessResponse>> list() {
    return ResponseEntity.ok(businessService.listActive());
  }
}
