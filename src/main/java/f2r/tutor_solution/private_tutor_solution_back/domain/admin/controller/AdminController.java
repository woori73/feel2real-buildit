package f2r.tutor_solution.private_tutor_solution_back.domain.admin.controller;

import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminAcademyVisibilityResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminLessonVisibilityResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminStudentSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminVisibilityUpdateRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.service.AdminVisibilityService;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.source.repository.SourceRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Phase 10 — 어드민 페이지 API.
 *
 * <p>1차 인증 정책에 따라 권한 검증은 프론트에서만 처리하며, 외부 노출 시 별도 보강이 필요하다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

  private final StudentRepository studentRepository;
  private final SourceRepository sourceRepository;
  private final ConversationRepository conversationRepository;
  private final AdminVisibilityService adminVisibilityService;

  /**
   * 어드민 대시보드 학생 목록 — 활동 요약(자료 개수·대화 개수)을 함께 반환.
   * 학생 카드 클릭 시 임퍼소네이트로 학생 대시보드에 진입한다.
   */
  @GetMapping("/students")
  public ResponseEntity<List<AdminStudentSummary>> listStudents() {
    List<Student> students = studentRepository.findAll();
    List<AdminStudentSummary> summaries = students.stream()
        .map(s -> AdminStudentSummary.of(
            s,
            sourceRepository.countByUploadedByUserId(s.getUser().getUserId()),
            conversationRepository.countByStudent_StudentId(s.getStudentId())))
        .toList();
    return ResponseEntity.ok(summaries);
  }

  @GetMapping("/academies")
  public ResponseEntity<List<AdminAcademyVisibilityResponse>> listAcademies() {
    return ResponseEntity.ok(adminVisibilityService.listAcademies());
  }

  @PatchMapping("/academies/{academyId}/visibility")
  public ResponseEntity<AdminAcademyVisibilityResponse> updateAcademyVisibility(
      @PathVariable Long academyId,
      @RequestBody AdminVisibilityUpdateRequest request) {
    return ResponseEntity.ok(adminVisibilityService.updateAcademyVisibility(academyId, request.isVisible()));
  }

  @PatchMapping("/lessons/{lessonId}/visibility")
  public ResponseEntity<AdminLessonVisibilityResponse> updateLessonVisibility(
      @PathVariable Long lessonId,
      @RequestBody AdminVisibilityUpdateRequest request) {
    return ResponseEntity.ok(adminVisibilityService.updateLessonVisibility(lessonId, request.isVisible()));
  }
}
