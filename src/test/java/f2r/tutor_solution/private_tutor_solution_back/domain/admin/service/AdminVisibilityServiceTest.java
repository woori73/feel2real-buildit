package f2r.tutor_solution.private_tutor_solution_back.domain.admin.service;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonProduct;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.PriceUnit;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.ScheduleType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyVisitStatisticsService;
import f2r.tutor_solution.private_tutor_solution_back.domain.admin.dto.AdminLessonVisibilityResponse;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminVisibilityServiceTest {

  private LessonProductRepository lessonProductRepository;
  private AdminVisibilityService service;

  @BeforeEach
  void setUp() {
    AcademyRepository academyRepository = mock(AcademyRepository.class);
    lessonProductRepository = mock(LessonProductRepository.class);
    service = new AdminVisibilityService(
        academyRepository,
        lessonProductRepository,
        mock(AcademyVisitStatisticsService.class));
  }

  @Test
  void updateLessonVisibility_usesLockedLookupAndDoesNotChangeRevisionOrAuthoredState() {
    // given
    LessonProduct lesson = lesson();
    when(lessonProductRepository.findForUpdateByLessonId(200L)).thenReturn(Optional.of(lesson));

    // when
    AdminLessonVisibilityResponse response = service.updateLessonVisibility(200L, false);

    // then
    assertThat(response.isVisible()).isFalse();
    assertThat(lesson.getName()).isEqualTo("기존 수업");
    assertThat(lesson.getStatus()).isEqualTo(LessonStatus.PUBLISHED);
    assertThat(lesson.getCurrentCount()).isEqualTo(3);
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(7L);
    verify(lessonProductRepository, never()).findById(200L);
  }

  @Test
  void updateLessonVisibility_returnsNotFoundWhenLockedLookupMisses() {
    // given
    when(lessonProductRepository.findForUpdateByLessonId(999L)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.updateLessonVisibility(999L, false))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404");
  }

  private LessonProduct lesson() {
    User tutor = User.builder()
        .loginId("tutor1")
        .password("pw")
        .name("강사")
        .role(Role.TUTOR)
        .build();
    ReflectionTestUtils.setField(tutor, "userId", 10L);
    Academy academy = Academy.builder()
        .tutor(tutor)
        .slug("test-academy")
        .name("테스트 Academy")
        .logoStorageKey("logo.jpg")
        .intro("소개")
        .status(AcademyStatus.PUBLISHED)
        .targetGroups(new HashSet<>(Set.of(TargetGroup.MIDDLE)))
        .build();
    ReflectionTestUtils.setField(academy, "academyId", 100L);
    LessonProduct lesson = LessonProduct.builder()
        .academy(academy)
        .name("기존 수업")
        .lessonType(LessonType.SUBSCRIPTION)
        .priceAmount(500_000)
        .priceUnit(PriceUnit.MONTH)
        .priceSummary("50만원/1개월")
        .maxCapacity(15)
        .currentCount(3)
        .scheduleType(ScheduleType.WEEKLY)
        .weeklyCount(2)
        .scheduleSummary("주2회")
        .status(LessonStatus.PUBLISHED)
        .authoringRevision(7L)
        .build();
    ReflectionTestUtils.setField(lesson, "lessonId", 200L);
    return lesson;
  }
}
