package f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.CandidateStudent;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.EnrollmentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.GrantRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.dto.LmsEnrollmentDtos.LessonEnrollmentView;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity.LmsEnrollment;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity.LmsEnrollmentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity.LmsGrantSource;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.repository.LmsEnrollmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 강사용 LMS 수강권 배정(작업지시서 §6, {@code /api/tutor/lms/{handle}/enrollments}).
 *
 * <p>후보 학생은 기존 {@code lesson_applications}(신청·승인)에서 뽑는다 — 재원생 여부를 이미
 * 그 데이터가 알고 있기 때문이다. 그렇다고 신청 레코드를 수강권으로 바꾸지 않는다. 운영자가 후보
 * 중 하나를 고르면 별도 {@code LmsEnrollment} 행이 새로 생기거나(최초) 재활성화된다(재배정).
 *
 * <p>클래스 불변식: 모든 public 메서드는 {@code requireOwnedAcademyByHandle}로 시작한다.
 */
@Service
@RequiredArgsConstructor
public class LmsEnrollmentService {

  private final AcademyService academyService;
  private final LessonProductRepository lessonProductRepository;
  private final LessonApplicationRepository lessonApplicationRepository;
  private final StudentRepository studentRepository;
  private final LmsEnrollmentRepository lmsEnrollmentRepository;

  @Transactional(readOnly = true)
  public LessonEnrollmentView getView(String handle, Long lessonId) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    requireOwnedLesson(academy, lessonId);

    List<LmsEnrollment> enrollments =
        lmsEnrollmentRepository.findByLessonProduct_LessonIdOrderByGrantedAtDesc(lessonId);
    Map<Long, LmsEnrollment> byStudentId = new LinkedHashMap<>();
    for (LmsEnrollment enrollment : enrollments) {
      byStudentId.putIfAbsent(enrollment.getStudent().getStudentId(), enrollment);
    }

    LocalDateTime now = LocalDateTime.now();
    List<CandidateStudent> candidates = lessonApplicationRepository.findApprovedByLessonId(lessonId)
        .stream()
        .map(application -> application.getStudent())
        .distinct()
        .map(student -> {
          LmsEnrollment existing = byStudentId.get(student.getStudentId());
          boolean enrolled = existing != null && existing.isActive(now);
          return new CandidateStudent(
              student.getStudentId(), student.getUser().getName(), student.getSchoolName(),
              student.getGrade() + "학년", enrolled);
        })
        .toList();

    List<EnrollmentView> views = enrollments.stream().map(LmsEnrollmentService::toView).toList();
    return new LessonEnrollmentView(lessonId, candidates, views);
  }

  @Transactional
  public EnrollmentView grant(String handle, Long lessonId, GrantRequest request) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    LessonProduct lesson = requireOwnedLesson(academy, lessonId);
    if (request == null || request.studentId() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학생을 선택해 주세요.");
    }
    Student student = studentRepository.findById(request.studentId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "학생을 찾을 수 없습니다."));
    // 후보 목록(getView)과 같은 기준으로 재검증한다 — UI가 보여준 후보가 아닌 임의 studentId로
    // 그랜트를 걸 수 없게 막는다(신청한 적 없는 학생에게 배정하는 조작을 서버에서도 차단).
    boolean applied = lessonApplicationRepository
        .findByStudent_StudentIdAndLesson_LessonId(student.getStudentId(), lessonId)
        .filter(application -> application.isApproved())
        .isPresent();
    if (!applied) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 강좌에 신청 승인된 학생만 배정할 수 있습니다.");
    }
    LocalDateTime now = LocalDateTime.now();

    LmsEnrollment existing = lmsEnrollmentRepository
        .findByStudent_StudentIdAndLessonProduct_LessonId(student.getStudentId(), lessonId)
        .orElse(null);
    if (existing != null) {
      existing.reactivate(LmsGrantSource.ACADEMY, now, request.expiresAt());
      return toView(existing);
    }
    LmsEnrollment saved = lmsEnrollmentRepository.save(LmsEnrollment.builder()
        .student(student)
        .academy(academy)
        .lessonProduct(lesson)
        .status(LmsEnrollmentStatus.ACTIVE)
        .grantSource(LmsGrantSource.ACADEMY)
        .grantedAt(now)
        .expiresAt(request.expiresAt())
        .build());
    return toView(saved);
  }

  @Transactional
  public EnrollmentView revoke(String handle, Long lessonId, Long lmsEnrollmentId) {
    Academy academy = academyService.requireOwnedAcademyByHandle(handle);
    LmsEnrollment enrollment = lmsEnrollmentRepository.findById(lmsEnrollmentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수강권을 찾을 수 없습니다."));
    if (!enrollment.getAcademy().getAcademyId().equals(academy.getAcademyId())
        || !enrollment.getLessonProduct().getLessonId().equals(lessonId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "수강권을 찾을 수 없습니다.");
    }
    enrollment.revoke();
    return toView(enrollment);
  }

  private LessonProduct requireOwnedLesson(Academy academy, Long lessonId) {
    return lessonProductRepository.findByLessonIdAndAcademy_AcademyId(lessonId, academy.getAcademyId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "강좌를 찾을 수 없습니다."));
  }

  private static EnrollmentView toView(LmsEnrollment enrollment) {
    return new EnrollmentView(
        enrollment.getLmsEnrollmentId(),
        enrollment.getStudent().getStudentId(),
        enrollment.getStudent().getUser().getName(),
        enrollment.getStatus().name(),
        enrollment.getGrantSource().name(),
        enrollment.getGrantedAt(),
        enrollment.getExpiresAt());
  }
}
