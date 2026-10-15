package f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.controller;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonType;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.LessonWeekday;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.TargetGroup;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonApplyRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.dto.LessonSearchCondition;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.service.LessonApplicationService;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

class LessonApplicationControllerTest {

  private LessonApplicationService service;
  private LessonApplicationController controller;

  @BeforeEach
  void setUp() {
    service = mock(LessonApplicationService.class);
    controller = new LessonApplicationController(service);
  }

  @Test
  void searchLessons_passesFiltersToService() {
    // given
    when(service.search(any())).thenReturn(List.of());

    // when
    controller.searchLessons(
        1,
        TargetGroup.HIGH,
        "서울시",
        "서초구",
        LessonType.SUBSCRIPTION,
        LessonWeekday.MONDAY,
        500_000,
        3);

    // then
    var captor = forClass(LessonSearchCondition.class);
    verify(service).search(captor.capture());
    LessonSearchCondition condition = captor.getValue();
    assertThat(condition.subjectId()).isEqualTo(1);
    assertThat(condition.targetGroup()).isEqualTo(TargetGroup.HIGH);
    assertThat(condition.city()).isEqualTo("서울시");
    assertThat(condition.district()).isEqualTo("서초구");
    assertThat(condition.lessonType()).isEqualTo(LessonType.SUBSCRIPTION);
    assertThat(condition.weekday()).isEqualTo(LessonWeekday.MONDAY);
    assertThat(condition.maxPrice()).isEqualTo(500_000);
    assertThat(condition.weeklyCount()).isEqualTo(3);
  }

  @Test
  void apply_delegatesPrincipalAndLessonIdToService() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "student", Role.STUDENT.name());
    LessonApplyRequest request = new LessonApplyRequest(100L, null);

    // when
    controller.apply(principal, request);

    // then
    verify(service).apply(principal, 100L, null);
  }

  @Test
  void apply_delegatesConversationIdToService_whenProvided() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "student", Role.STUDENT.name());
    LessonApplyRequest request = new LessonApplyRequest(100L, 77L);

    // when
    controller.apply(principal, request);

    // then
    verify(service).apply(principal, 100L, 77L);
  }

  @Test
  void apply_delegatesNullLessonId_whenRequestBodyIsMissing() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "student", Role.STUDENT.name());

    // when
    controller.apply(principal, null);

    // then
    verify(service).apply(principal, null, null);
  }

  @Test
  void cancel_delegatesPrincipalAndApplicationIdToService() {
    // given
    JwtTokenProvider.AuthenticatedPrincipal principal =
        new JwtTokenProvider.AuthenticatedPrincipal(10L, "student", Role.STUDENT.name());

    // when
    controller.cancel(principal, 500L);

    // then
    verify(service).cancel(principal, 500L);
  }
}
