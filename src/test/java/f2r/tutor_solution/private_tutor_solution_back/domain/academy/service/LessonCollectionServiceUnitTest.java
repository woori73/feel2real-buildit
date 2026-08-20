package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.*;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.exception.LessonCollectionException;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LessonCollectionServiceUnitTest {

  private LessonProductRepository lessonRepository;
  private AcademyRepository academyRepository;
  private AcademyVideoRepository academyVideoRepository;
  private AuthenticatedUserResolver authResolver;
  private LessonCollectionService service;
  private JwtTokenProvider.AuthenticatedPrincipal principal;

  @BeforeEach
  void setUp() {
    lessonRepository = mock(LessonProductRepository.class);
    academyRepository = mock(AcademyRepository.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    academyVideoRepository = mock(AcademyVideoRepository.class);
    service = new LessonCollectionService(
        lessonRepository, academyRepository, academyVideoRepository, authResolver);
    principal = new JwtTokenProvider.AuthenticatedPrincipal(7L, "tutor", "TUTOR");
  }

  @Test
  void save_rejectsDuplicateCreationKeysBeforeDatabaseResolution() {
    // given
    String creationKey = "6f101b03-12d7-40e2-88ed-cedbffb37e47";
    LessonCollectionSaveRequest request = new LessonCollectionSaveRequest(List.of(
        newItem(creationKey, "수업 A"),
        newItem(creationKey.toUpperCase(), "수업 B")));

    // when / then
    assertThatThrownBy(() -> service.save(principal, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("creationKey가 중복");
    verifyNoInteractions(academyRepository, lessonRepository);
  }

  @Test
  void save_returnsReplayForEquivalentStateWithoutRevisionIncrement() {
    // given
    Academy academy = academy(5L);
    LessonProduct lesson = lesson(10L, "수업 A", null);
    when(academyRepository.findByTutor_UserId(7L)).thenReturn(Optional.of(academy));
    when(lessonRepository.findAllForUpdateByAcademyIdAndLessonIds(5L, Set.of(10L)))
        .thenReturn(List.of(lesson));
    LessonCollectionSaveRequest request = new LessonCollectionSaveRequest(List.of(
        existingItem(10L, 0L, "수업 A")));

    // when
    LessonCollectionSaveResponse response = service.save(principal, request);

    // then
    assertThat(response.createdCount()).isZero();
    assertThat(response.updatedCount()).isZero();
    assertThat(response.replayedCount()).isEqualTo(1);
    assertThat(response.savedItems()).extracting(LessonResponse::authoringRevision)
        .containsExactly(0L);
    verify(lessonRepository).flush();
  }

  @Test
  void save_rejectsStaleDifferingStateWithoutMutation() {
    // given
    Academy academy = academy(5L);
    LessonProduct lesson = lesson(10L, "서버 수업", 1L);
    when(academyRepository.findByTutor_UserId(7L)).thenReturn(Optional.of(academy));
    when(lessonRepository.findAllForUpdateByAcademyIdAndLessonIds(5L, Set.of(10L)))
        .thenReturn(List.of(lesson));
    LessonCollectionSaveRequest request = new LessonCollectionSaveRequest(List.of(
        existingItem(10L, 0L, "내 수업")));

    // when / then
    assertThatThrownBy(() -> service.save(principal, request))
        .isInstanceOf(LessonCollectionException.class)
        .extracting("code")
        .isEqualTo(LessonCollectionException.AUTHORING_STALE);
    assertThat(lesson.getName()).isEqualTo("서버 수업");
    assertThat(lesson.getEffectiveAuthoringRevision()).isEqualTo(1L);
    verify(lessonRepository, never()).flush();
  }

  @Test
  void creationKeyConstraintClassification_acceptsExactFinalIdentifier() {
    // given
    DataIntegrityViolationException exact = integrityFailure(
        LessonCollectionService.CREATION_KEY_CONSTRAINT);
    DataIntegrityViolationException tableQualified = integrityFailure(
        "lesson_products." + LessonCollectionService.CREATION_KEY_CONSTRAINT);
    DataIntegrityViolationException quotedQualified = integrityFailure(
        "`lesson_products`.`" + LessonCollectionService.CREATION_KEY_CONSTRAINT + "`");

    // when / then
    assertThat(LessonCollectionService.isCreationKeyConstraintViolation(exact)).isTrue();
    assertThat(LessonCollectionService.isCreationKeyConstraintViolation(tableQualified)).isTrue();
    assertThat(LessonCollectionService.isCreationKeyConstraintViolation(quotedQualified)).isTrue();
  }

  @Test
  void creationKeyConstraintClassification_rejectsMisleadingConstraintNames() {
    // given
    DataIntegrityViolationException unrelated = integrityFailure("uk_other_constraint");
    DataIntegrityViolationException prefixed = integrityFailure(
        "lesson_products.prefix_" + LessonCollectionService.CREATION_KEY_CONSTRAINT);
    DataIntegrityViolationException suffixed = integrityFailure(
        "lesson_products." + LessonCollectionService.CREATION_KEY_CONSTRAINT + "_backup");

    // when / then
    assertThat(LessonCollectionService.isCreationKeyConstraintViolation(unrelated)).isFalse();
    assertThat(LessonCollectionService.isCreationKeyConstraintViolation(prefixed)).isFalse();
    assertThat(LessonCollectionService.isCreationKeyConstraintViolation(suffixed)).isFalse();
    assertThat(LessonCollectionService.isCreationKeyConstraintViolation(
        new DataIntegrityViolationException(
            "message mentions " + LessonCollectionService.CREATION_KEY_CONSTRAINT)))
        .isFalse();
  }

  private LessonCollectionItemRequest newItem(String creationKey, String name) {
    return item(null, creationKey, null, name);
  }

  private LessonCollectionItemRequest existingItem(
      Long lessonId,
      Long expectedRevision,
      String name) {
    return item(lessonId, null, expectedRevision, name);
  }

  private LessonCollectionItemRequest item(
      Long lessonId,
      String creationKey,
      Long expectedRevision,
      String name) {
    return new LessonCollectionItemRequest(
        lessonId,
        creationKey,
        expectedRevision,
        name,
        " 설명 ",
        null,
        null,
        LessonType.SUBSCRIPTION,
        TargetGroup.HIGH,
        null,
        PriceUnit.NEGOTIABLE,
        " 협의 ",
        8,
        ScheduleType.CONSULTATION,
        null,
        Set.of(),
        null,
        null,
        null,
        null,
        " 일정 협의 ",
        LessonStatus.DRAFT,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private Academy academy(Long academyId) {
    Academy academy = Academy.builder()
        .slug("academy")
        .name("Academy")
        .logoStorageKey("logo")
        .intro("intro")
        .build();
    ReflectionTestUtils.setField(academy, "academyId", academyId);
    return academy;
  }

  private LessonProduct lesson(Long lessonId, String name, Long revision) {
    LessonProduct lesson = LessonProduct.builder()
        .academy(academy(5L))
        .name(name)
        .description("설명")
        .lessonType(LessonType.SUBSCRIPTION)
        .targetGroup(TargetGroup.HIGH)
        .priceUnit(PriceUnit.NEGOTIABLE)
        .priceSummary("협의")
        .maxCapacity(8)
        .scheduleType(ScheduleType.CONSULTATION)
        .scheduleSummary("일정 협의")
        .status(LessonStatus.DRAFT)
        .theme(null)
        .authoringRevision(revision)
        .weekdays(Set.of())
        .build();
    ReflectionTestUtils.setField(lesson, "lessonId", lessonId);
    return lesson;
  }

  private DataIntegrityViolationException integrityFailure(String constraintName) {
    ConstraintViolationException cause = new ConstraintViolationException(
        "constraint",
        new SQLException("failure"),
        constraintName);
    return new DataIntegrityViolationException("integrity", cause);
  }
}
