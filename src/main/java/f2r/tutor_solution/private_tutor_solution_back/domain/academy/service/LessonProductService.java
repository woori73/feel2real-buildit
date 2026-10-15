package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.LessonUpsertRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.entity.LessonApplicationStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonProductService {

  private final LessonProductRepository lessonProductRepository;
  private final AcademyRepository academyRepository;
  private final LessonApplicationRepository lessonApplicationRepository;
  private final AuthenticatedUserResolver authResolver;

  @Transactional
  public LessonResponse create(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      LessonUpsertRequest request) {
    authResolver.requireTutor(principal);
    validate(request);
    Academy academy = academyRepository.findByTutor_UserId(principal.userId())
        .orElseThrow(() -> new IllegalArgumentException("Academy를 먼저 생성해야 합니다."));
    authResolver.verifyOwnsAcademy(academy);

    LessonProduct lesson = LessonProduct.builder()
        .academy(academy)
        .name(request.name().trim())
        .description(trimToNull(request.description()))
        .lessonType(request.lessonType())
        .targetGroup(normalizeTargetGroup(request.targetGroup()))
        .priceAmount(request.priceAmount())
        .priceUnit(request.priceUnit())
        .priceSummary(request.priceSummary().trim())
        .maxCapacity(request.maxCapacity())
        .currentCount(0)
        .scheduleType(request.scheduleType())
        .weeklyCount(request.weeklyCount())
        .startDate(request.startDate())
        .endDate(request.endDate())
        .durationWeeks(request.durationWeeks())
        .durationMonths(request.durationMonths())
        .scheduleSummary(request.scheduleSummary().trim())
        .status(normalizeStatus(request.status()))
        .weekdays(request.weekdays() == null ? null : new HashSet<>(request.weekdays()))
        .build();
    return LessonResponse.from(lessonProductRepository.save(lesson));
  }

  public LessonResponse getMine(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long lessonId) {
    authResolver.requireTutor(principal);
    LessonProduct lesson = lessonProductRepository.findById(lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수업 상품을 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(lesson.getAcademy());
    if (lesson.getStatus() == LessonStatus.HIDDEN) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "수업 상품을 찾을 수 없습니다.");
    }
    return LessonResponse.from(lesson);
  }

  @Transactional
  public LessonResponse update(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long lessonId,
      LessonUpsertRequest request) {
    authResolver.requireTutor(principal);
    LessonProduct lesson = lessonProductRepository.findForUpdateByLessonId(lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수업 상품을 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(lesson.getAcademy());
    validate(request);

    lesson.updateDetails(
        request.name().trim(),
        trimToNull(request.description()),
        request.lessonType(),
        normalizeTargetGroup(request.targetGroup()),
        request.priceAmount(),
        request.priceUnit(),
        request.priceSummary().trim(),
        request.maxCapacity(),
        request.scheduleType(),
        request.weeklyCount(),
        request.startDate(),
        request.endDate(),
        request.durationWeeks(),
        request.durationMonths(),
        request.scheduleSummary().trim(),
        normalizeStatus(request.status()),
        request.weekdays() == null ? null : new HashSet<>(request.weekdays()));
    return LessonResponse.from(lesson);
  }

  @Transactional
  public void hide(
      JwtTokenProvider.AuthenticatedPrincipal principal,
      Long lessonId) {
    authResolver.requireTutor(principal);
    LessonProduct lesson = lessonProductRepository.findForUpdateByLessonId(lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수업 상품을 찾을 수 없습니다."));
    authResolver.verifyOwnsAcademy(lesson.getAcademy());
    if (lessonApplicationRepository.existsByLesson_LessonIdAndStatus(lessonId, LessonApplicationStatus.APPROVED)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "신청/수강생이 있는 수업은 삭제할 수 없습니다.");
    }
    lesson.hideFromTutorManagement();
  }

  private void validate(LessonUpsertRequest request) {
    if (request == null) throw new IllegalArgumentException("수업 상품 정보가 필요합니다.");
    if (isBlank(request.name())) throw new IllegalArgumentException("수업명은 필수입니다.");
    if (request.lessonType() == null) throw new IllegalArgumentException("수업 유형은 필수입니다.");
    if (request.priceUnit() == null) throw new IllegalArgumentException("가격 단위는 필수입니다.");
    if (isBlank(request.priceSummary())) throw new IllegalArgumentException("가격 설명은 필수입니다.");
    if (request.maxCapacity() == null || request.maxCapacity() <= 0) {
      throw new IllegalArgumentException("정원은 1명 이상이어야 합니다.");
    }
    if (request.scheduleType() == null) throw new IllegalArgumentException("일정 유형은 필수입니다.");
    if (isBlank(request.scheduleSummary())) throw new IllegalArgumentException("일정 설명은 필수입니다.");
    validatePrice(request);
    validateSchedule(request);
  }

  private void validatePrice(LessonUpsertRequest request) {
    if (request.priceUnit() == PriceUnit.NEGOTIABLE) return;
    if (request.priceAmount() == null || request.priceAmount() <= 0) {
      throw new IllegalArgumentException("고정 가격은 1원 이상이어야 합니다.");
    }
  }

  private void validateSchedule(LessonUpsertRequest request) {
    if (request.scheduleType() == ScheduleType.WEEKLY) {
      if (request.weeklyCount() == null || request.weeklyCount() <= 0) {
        throw new IllegalArgumentException("주간 수업 횟수는 1회 이상이어야 합니다.");
      }
      if (request.weekdays() == null || request.weekdays().isEmpty()) {
        throw new IllegalArgumentException("주간 일정은 요일을 1개 이상 선택해야 합니다.");
      }
      return;
    }
    if (request.scheduleType() == ScheduleType.PERIOD) {
      boolean hasDateRange = request.startDate() != null && request.endDate() != null
          && !request.endDate().isBefore(request.startDate());
      boolean hasDuration = positive(request.durationWeeks()) || positive(request.durationMonths());
      if (!hasDateRange && !hasDuration) {
        throw new IllegalArgumentException("기간형 수업은 날짜 범위 또는 기간을 입력해야 합니다.");
      }
    }
  }

  private LessonStatus normalizeStatus(LessonStatus status) {
    return status == null ? LessonStatus.PUBLISHED : status;
  }

  private TargetGroup normalizeTargetGroup(TargetGroup targetGroup) {
    return targetGroup == null ? TargetGroup.HIGH : targetGroup;
  }

  private boolean positive(Integer value) {
    return value != null && value > 0;
  }

  private String trimToNull(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
