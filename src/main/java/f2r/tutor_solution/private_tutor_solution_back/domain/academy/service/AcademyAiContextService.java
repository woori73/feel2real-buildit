package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.AcademyAiContext.LessonSummary;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.entity.Subject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Academy의 현재 상태를 AI가 안정적으로 이해할 수 있는 하나의 normalized context로 조립한다.
 *
 * <p>여기서 만드는 {@link AcademyAiContext}가 유일한 AI 입력 표현이다 — JPA Entity를 AI 호출
 * 경로 어디에도 직접 넘기지 않는다. 조립은 순수 함수(같은 Academy 상태 → 같은 결과)이고,
 * DB 전체를 덤프하지 않고 A급 필드만 명시적으로 골라 담는다(§4 클래스 주석 참고).
 *
 * <p>인증/권한: {@link f2r...AcademyService#requireMyAcademy}를 그대로 재사용한다 — 이
 * 서비스는 절대 academyId를 파라미터로 받지 않는다. 호출자가 다른 학원의 Academy를 조회할
 * 방법이 구조적으로 없다(다른 Academy write path와 동일한 보장).
 */
@Service
public class AcademyAiContextService {

  private final AcademyService academyService;
  private final LessonProductRepository lessonProductRepository;

  public AcademyAiContextService(
      AcademyService academyService, LessonProductRepository lessonProductRepository) {
    this.academyService = academyService;
    this.lessonProductRepository = lessonProductRepository;
  }

  public AcademyAiContext buildContext(JwtTokenProvider.AuthenticatedPrincipal principal) {
    Academy academy = academyService.requireMyAcademy(principal);
    List<LessonProduct> lessons =
        lessonProductRepository.findByAcademy_AcademyIdOrderByCreatedAtDesc(academy.getAcademyId());
    return buildContext(academy, lessons);
  }

  /** Academy를 이미 들고 있는 호출자(예: 완성도 검사)를 위한 순수 조립 함수. */
  public AcademyAiContext buildContext(Academy academy, List<LessonProduct> lessons) {
    Map<String, String> draft =
        academy.getPageSettings() != null && academy.getPageSettings().builderDraft() != null
            ? academy.getPageSettings().builderDraft()
            : Map.of();

    return new AcademyAiContext(
        academy.getName(),
        academy.getIntro(),
        academy.getSubjects().stream()
            .map(Subject::getNameKo)
            .sorted()
            .toList(),
        academy.getTargetGroups().stream()
            .sorted(Comparator.comparingInt(Enum::ordinal))
            .map(TargetGroup::name)
            .toList(),
        draft.getOrDefault("principalName", ""),
        draft.getOrDefault("principalRole", ""),
        draft.getOrDefault("principalDesc", ""),
        List.of(
            draft.getOrDefault("philosophyItem1", ""),
            draft.getOrDefault("philosophyItem2", ""),
            draft.getOrDefault("philosophyItem3", ""),
            draft.getOrDefault("philosophyItem4", "")),
        lessons.stream()
            .map(lesson -> new LessonSummary(
                lesson.getName(),
                nullToEmpty(lesson.getKicker()),
                nullToEmpty(lesson.getLearnerNeed()),
                nullToEmpty(lesson.getHighlight())))
            .toList());
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
