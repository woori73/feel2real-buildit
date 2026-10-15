package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity.LmsEnrollment;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.repository.LmsEnrollmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * "이 학생/강사가 이 강좌의 복습영상을 볼 수 있는가"를 판정하는 공통 게이트.
 * {@code /lms/{handle}/review/{lessonId}} SSR 화면, 콘텐츠 목록 API, 영상 스트리밍 API가 모두 이걸 쓴다
 * — 세 곳에 각자 검사 로직을 두면 하나만 고치고 나머지를 놓치는 구멍이 생긴다.
 *
 * <p>owner 판정은 role이 아니라 소유권이다({@code academy.tutor}). 학생은 {@code LmsEnrollment}가
 * ACTIVE이고 만료되지 않았을 때만 통과한다 — {@code LessonApplication}(신청)은 보지 않는다.
 */
@Service
public class LmsLearnerAccessService {

  private final AcademyService academyService;
  private final LessonProductRepository lessonProductRepository;
  private final StudentRepository studentRepository;
  private final LmsEnrollmentRepository lmsEnrollmentRepository;

  public LmsLearnerAccessService(
      AcademyService academyService,
      LessonProductRepository lessonProductRepository,
      StudentRepository studentRepository,
      LmsEnrollmentRepository lmsEnrollmentRepository) {
    this.academyService = academyService;
    this.lessonProductRepository = lessonProductRepository;
    this.studentRepository = studentRepository;
    this.lmsEnrollmentRepository = lmsEnrollmentRepository;
  }

  public record Access(Academy academy, LessonProduct lesson, Student student, boolean owner) {}

  public Access requireAccess(
      String handle, Long lessonId, JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    Academy academy = academyService.getPublishedEntityByHandle(handle);
    LessonProduct lesson = lessonProductRepository
        .findByLessonIdAndAcademy_AcademyId(lessonId, academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강좌를 찾을 수 없습니다."));

    if (isOwner(academy, principal)) {
      return new Access(academy, lesson, null, true);
    }

    Student student = studentRepository.findByUser_UserId(principal.userId()).orElse(null);
    if (student == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "수강 권한이 없습니다.");
    }
    LmsEnrollment enrollment = lmsEnrollmentRepository
        .findByStudent_StudentIdAndLessonProduct_LessonId(student.getStudentId(), lessonId)
        .orElse(null);
    if (enrollment == null || !enrollment.isActive(LocalDateTime.now())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "수강 권한이 없습니다.");
    }
    return new Access(academy, lesson, student, false);
  }

  private static boolean isOwner(Academy academy, JwtTokenProvider.AuthenticatedPrincipal principal) {
    return academy.getTutor() != null && academy.getTutor().getUserId().equals(principal.userId());
  }
}
