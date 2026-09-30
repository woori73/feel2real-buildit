package f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.StudentContentItem;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.StudentLessonDetail;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.dto.LmsContentDtos.StudentLessonSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContent;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.entity.LmsContentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmscontent.repository.LmsContentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.entity.LmsEnrollmentStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lmsenrollment.repository.LmsEnrollmentRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 학생의 "내 복습수업"(작업지시서 §5) 목록·상세. 배정된 강좌 + 그 강좌의 공개(PUBLISHED) 콘텐츠만 보여준다. */
@Service
@RequiredArgsConstructor
public class LmsContentStudentService {

  private final AcademyService academyService;
  private final StudentRepository studentRepository;
  private final LmsEnrollmentRepository lmsEnrollmentRepository;
  private final LmsContentRepository lmsContentRepository;
  private final LmsLearnerAccessService accessService;

  @Transactional(readOnly = true)
  public List<StudentLessonSummary> listMyLessons(
      String handle, JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    Academy academy = academyService.getPublishedEntityByHandle(handle);
    Student student = studentRepository.findByUser_UserId(principal.userId()).orElse(null);
    if (student == null) {
      return List.of();
    }
    LocalDateTime now = LocalDateTime.now();
    return lmsEnrollmentRepository
        .findByStudent_StudentIdAndAcademy_AcademyIdAndStatus(
            student.getStudentId(), academy.getAcademyId(), LmsEnrollmentStatus.ACTIVE)
        .stream()
        .filter(enrollment -> enrollment.isActive(now))
        .map(enrollment -> {
          LessonProduct lesson = enrollment.getLessonProduct();
          int contentCount = lmsContentRepository
              .findByLessonProduct_LessonIdAndStatusOrderByDisplayOrderAscLmsContentIdAsc(
                  lesson.getLessonId(), LmsContentStatus.PUBLISHED)
              .size();
          return new StudentLessonSummary(
              lesson.getLessonId(), lesson.getName(), lesson.getSchoolName(), lesson.getGradeLabel(),
              contentCount);
        })
        .toList();
  }

  @Transactional(readOnly = true)
  public StudentLessonDetail getLessonDetail(
      String handle, Long lessonId, JwtTokenProvider.AuthenticatedPrincipal principal) {
    LmsLearnerAccessService.Access access = accessService.requireAccess(handle, lessonId, principal);
    LessonProduct lesson = access.lesson();
    List<LmsContent> published = lmsContentRepository
        .findByLessonProduct_LessonIdAndStatusOrderByDisplayOrderAscLmsContentIdAsc(
            lessonId, LmsContentStatus.PUBLISHED);
    List<StudentContentItem> items = published.stream()
        .map(content -> new StudentContentItem(
            content.getLmsContentId(),
            content.getTitle(),
            content.getDescription(),
            content.getExamName(),
            content.getUnitLabel(),
            content.getDurationSec(),
            "/api/lms/" + handle + "/contents/" + content.getLmsContentId() + "/stream"))
        .toList();
    return new StudentLessonDetail(
        lesson.getLessonId(), lesson.getName(), lesson.getSchoolName(), lesson.getGradeLabel(), items);
  }
}
