package f2r.tutor_solution.private_tutor_solution_back.domain.academy.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LmsManageLessonResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** handle과 인증된 Academy owner를 같은 경계에서 검증하는 LMS 관리 API. */
@RestController
@RequiredArgsConstructor
public class AcademyLmsManageApiController {

  private final AcademyService academyService;

  @GetMapping("/api/tutor/lms/{handle}/lessons")
  public ResponseEntity<List<LmsManageLessonResponse>> listLessons(@PathVariable String handle) {
    return ResponseEntity.ok(academyService.listOwnedLmsLessonsByHandle(handle));
  }
}
