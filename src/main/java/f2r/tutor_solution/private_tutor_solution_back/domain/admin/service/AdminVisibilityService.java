package f2r.tutor_solution.private_tutor_solution_back.domain.admin.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVisitStatisticsService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminAcademyVisibilityResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminLessonVisibilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminVisibilityService {

  private final AcademyRepository academyRepository;
  private final LessonProductRepository lessonProductRepository;
  private final AcademyVisitStatisticsService academyVisitStatisticsService;

  public List<AdminAcademyVisibilityResponse> listAcademies() {
    return academyRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public AdminAcademyVisibilityResponse updateAcademyVisibility(Long academyId, boolean visible) {
    Academy academy = academyRepository.findById(academyId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academy를 찾을 수 없습니다."));
    academy.updateVisibility(visible);
    return toResponse(academy);
  }

  @Transactional
  public AdminLessonVisibilityResponse updateLessonVisibility(Long lessonId, boolean visible) {
    LessonProduct lesson = lessonProductRepository.findForUpdateByLessonId(lessonId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "수업을 찾을 수 없습니다."));
    lesson.updateVisibility(visible);
    return AdminLessonVisibilityResponse.from(lesson);
  }

  private AdminAcademyVisibilityResponse toResponse(Academy academy) {
    List<AdminLessonVisibilityResponse> lessons = lessonProductRepository
        .findByAcademy_AcademyIdOrderByCreatedAtDesc(academy.getAcademyId())
        .stream()
        .map(AdminLessonVisibilityResponse::from)
        .toList();
    return AdminAcademyVisibilityResponse.of(
        academy, lessons, academyVisitStatisticsService.getSummary(academy));
  }
}
