package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.service;

import f2r.tutor_solution.private_tutor_solution_back.config.AuditorContextHolder;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonApplicationResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonSearchCondition;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonSearchResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplication;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.entity.Conversation;
import f2r.tutor_solution.private_tutor_solution_back.domain.conversation.repository.ConversationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Student;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonApplicationService {

  private final LessonApplicationRepository lessonApplicationRepository;
  private final LessonProductRepository lessonProductRepository;
  private final StudentRepository studentRepository;
  private final ConversationRepository conversationRepository;

  public List<LessonSearchResponse> search(LessonSearchCondition condition) {
    LessonSearchCondition c = condition == null
        ? new LessonSearchCondition(null, null, null, null, null, null, null, null)
        : condition;
    return lessonProductRepository.searchPublishedLessons(
            AcademyStatus.PUBLISHED,
            LessonStatus.PUBLISHED,
            c.subjectId(),
            c.targetGroup(),
            trimToNull(c.city()),
            normalizeDistrict(c.district()),
            c.lessonType(),
            c.weekday(),
            c.maxPrice(),
            c.weeklyCount())
        .stream()
        .map(LessonSearchResponse::from)
        .toList();
  }

  @Transactional
  public LessonApplicationResponse apply(JwtTokenProvider.AuthenticatedPrincipal principal,
      Long lessonId, Long conversationId) {
    requireStudent(principal);
    if (lessonId == null) throw new IllegalArgumentException("수업 ID가 필요합니다.");

    Student student = loadStudent(principal.userId());
    LessonProduct lesson = lessonProductRepository.findForUpdateByLessonId(lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수업을 찾을 수 없습니다."));
    ensurePublished(lesson);

    LessonApplication application = lessonApplicationRepository
        .findByStudent_StudentIdAndLesson_LessonId(student.getStudentId(), lessonId)
        .orElse(null);
    if (application != null && application.isApproved()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 신청한 수업입니다.");
    }
    if (lesson.isFull()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "수업 정원이 가득 찼습니다.");
    }

    lesson.approveOneSeat();
    try {
      AuditorContextHolder.set(principal.loginId());
      Conversation conversation = resolveConversation(conversationId, student, lesson);
      if (application == null) {
        application = lessonApplicationRepository.save(LessonApplication.builder()
            .lesson(lesson)
            .student(student)
            .conversation(conversation)
            .status(LessonApplicationStatus.APPROVED)
            .appliedAt(LocalDateTime.now())
            .build());
      } else {
        application.reactivate(LocalDateTime.now());
        application.linkConversation(conversation);
      }
      return LessonApplicationResponse.from(application);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  /**
   * conversationId가 주어지면 소유 학생 검증 후 반환, null이면 자동 생성.
   * IDOR 차단: 다른 학생의 conversation 연결 시도 → AccessDeniedException.
   */
  private Conversation resolveConversation(Long conversationId, Student student, LessonProduct lesson) {
    if (conversationId != null) {
      Conversation conv = conversationRepository.findById(conversationId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."));
      if (conv.getStudent() == null || conv.getTutorUser() != null) {
        throw new AccessDeniedException("학생 소유 대화만 연결할 수 있습니다.");
      }
      if (!conv.getStudent().getStudentId().equals(student.getStudentId())) {
        throw new AccessDeniedException("본인 대화만 연결할 수 있습니다.");
      }
      return conv;
    }
    return conversationRepository.save(
        Conversation.forStudent(student, lesson.getName() + " 학습 대화", null));
  }

  public List<LessonApplicationResponse> listMine(JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireStudent(principal);
    Student student = loadStudent(principal.userId());
    return lessonApplicationRepository.findByStudent_StudentIdOrderByUpdatedAtDesc(student.getStudentId())
        .stream()
        .map(LessonApplicationResponse::from)
        .toList();
  }

  @Transactional
  public LessonApplicationResponse cancel(JwtTokenProvider.AuthenticatedPrincipal principal, Long applicationId) {
    requireStudent(principal);
    LessonApplication application = lessonApplicationRepository.findById(applicationId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신청을 찾을 수 없습니다."));
    if (!application.getStudent().getUser().getUserId().equals(principal.userId())) {
      throw new AccessDeniedException("자기 신청만 취소할 수 있습니다.");
    }
    if (!application.isApproved()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 취소된 신청입니다.");
    }

    LessonProduct lockedLesson = lessonProductRepository.findForUpdateByLessonId(application.getLesson().getLessonId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수업을 찾을 수 없습니다."));
    lockedLesson.cancelOneSeat();
    try {
      AuditorContextHolder.set(principal.loginId());
      application.cancel(LocalDateTime.now());
      return LessonApplicationResponse.from(application);
    } finally {
      AuditorContextHolder.clear();
    }
  }

  public List<LessonApplicationResponse> listTutorApplications(JwtTokenProvider.AuthenticatedPrincipal principal) {
    requireTutor(principal);
    return lessonApplicationRepository.findByTutorUserId(principal.userId())
        .stream()
        .map(LessonApplicationResponse::from)
        .toList();
  }

  public List<LessonApplicationResponse> listLessonApplications(
      JwtTokenProvider.AuthenticatedPrincipal principal, Long lessonId) {
    requireTutor(principal);
    LessonProduct lesson = lessonProductRepository.findById(lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수업을 찾을 수 없습니다."));
    if (!lesson.getAcademy().getTutor().getUserId().equals(principal.userId())) {
      throw new AccessDeniedException("본인 수업의 신청자만 조회할 수 있습니다.");
    }
    return lessonApplicationRepository.findApprovedByLessonId(lessonId)
        .stream()
        .map(LessonApplicationResponse::from)
        .toList();
  }

  private void ensurePublished(LessonProduct lesson) {
    if (lesson.getStatus() != LessonStatus.PUBLISHED
        || !lesson.isVisible()
        || lesson.getAcademy().getStatus() != AcademyStatus.PUBLISHED
        || !lesson.getAcademy().isVisible()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공개 수업을 찾을 수 없습니다.");
    }
  }

  private Student loadStudent(Long userId) {
    return studentRepository.findByUser_UserId(userId)
        .orElseThrow(() -> new AccessDeniedException("학생 프로필이 필요합니다."));
  }

  private void requireStudent(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null || !Role.STUDENT.name().equals(principal.role())) {
      throw new AccessDeniedException("학생 권한이 필요합니다.");
    }
  }

  private void requireTutor(JwtTokenProvider.AuthenticatedPrincipal principal) {
    if (principal == null || !Role.TUTOR.name().equals(principal.role())) {
      throw new AccessDeniedException("강사 권한이 필요합니다.");
    }
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String normalizeDistrict(String district) {
    String normalized = trimToNull(district);
    return normalized == null ? null : normalized;
  }
}
