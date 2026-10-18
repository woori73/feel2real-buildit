package f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto.ExamRequestResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.dto.ExamRequestStatusUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.examrequest.service.ExamRequestService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 예상문제 의뢰 관리(ROLE_ADMIN은 SecurityConfig의 {@code /api/admin/**} 규칙). */
@RestController
@RequestMapping("/api/admin/exam-requests")
@RequiredArgsConstructor
public class ExamRequestAdminController {

  private final ExamRequestService service;

  @GetMapping
  public List<ExamRequestResponse> list() {
    return service.listAll();
  }

  @PatchMapping("/{id}/status")
  public ExamRequestResponse updateStatus(
      @PathVariable Long id, @RequestBody ExamRequestStatusUpdateRequest request) {
    return service.updateStatus(id, request.status());
  }
}
