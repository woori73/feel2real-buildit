package f2r.tutor_solution.private_tutor_solution_back.domain.easy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentDetailResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.dto.EasyStudentResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.easy.service.EasyStudentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Easy 학생 원장 — {@code /api/admin/**} → ROLE_ADMIN (SecurityConfig).
 * Wiki users와 연결하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/easy/students")
@RequiredArgsConstructor
public class EasyStudentController {

  private final EasyStudentService studentService;

  @GetMapping
  public ResponseEntity<List<EasyStudentResponse>> list() {
    return ResponseEntity.ok(studentService.list());
  }

  @GetMapping("/{studentId}/ledger")
  public ResponseEntity<EasyStudentDetailResponse> detail(@PathVariable Long studentId) {
    return ResponseEntity.ok(studentService.detail(studentId));
  }

  @PostMapping
  public ResponseEntity<EasyStudentResponse> create(@RequestBody EasyStudentRequest request) {
    return ResponseEntity.ok(studentService.create(request));
  }

  @PutMapping("/{studentId}")
  public ResponseEntity<EasyStudentResponse> update(
      @PathVariable Long studentId, @RequestBody EasyStudentRequest request) {
    return ResponseEntity.ok(studentService.update(studentId, request));
  }
}
