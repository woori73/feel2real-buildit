package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import f2r.tutor_solution.private_tutor_solution_back.config.PublicSiteProperties;
import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyStatus;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRegionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.LessonProductRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.statistics.AcademyHandleHistoryService;
import f2r.tutor_solution.private_tutor_solution_back.domain.lessonapplication.repository.LessonApplicationRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.subject.repository.SubjectRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.repository.UserRepository;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.image.ImageSanitizer;
import f2r.tutor_solution.private_tutor_solution_back.infrastructure.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 학원 LMS가 쓰는 handle 기반 Academy 조회 2종.
 *
 * <p>핵심은 <b>두 메서드가 서로 다른 조회를 한다</b>는 것이다 — owner는 미발행 학원도 봐야 하고,
 * 공개 화면은 미발행 학원이 새면 안 된다. 여기가 뒤집히면 조용한 정보 노출이 된다.
 */
@DisplayName("AcademyService — LMS handle 조회")
class AcademyServiceLmsHandleLookupTest {

  private AcademyRepository academyRepository;
  private AuthenticatedUserResolver authResolver;
  private AcademyService service;

  @BeforeEach
  void setUp() {
    academyRepository = mock(AcademyRepository.class);
    authResolver = mock(AuthenticatedUserResolver.class);
    service = new AcademyService(
        academyRepository,
        mock(AcademyRegionRepository.class),
        mock(LessonProductRepository.class),
        mock(LessonApplicationRepository.class),
        mock(SubjectRepository.class),
        mock(UserRepository.class),
        mock(FileStorage.class),
        mock(ImageSanitizer.class),
        authResolver,
        new PublicSiteProperties(null),
        mock(AcademyVideoDeletionGuard.class),
        mock(AcademyHandleHistoryService.class));
  }

  @Test
  @DisplayName("requireOwnedAcademyByHandle: 소유자면 Academy를 돌려준다")
  void requireOwnedAcademyByHandle_returnsAcademyForOwner() {
    // given
    Academy academy = academy();
    when(academyRepository.findByHandle("edu-holic")).thenReturn(Optional.of(academy));

    // when
    Academy found = service.requireOwnedAcademyByHandle("edu-holic");

    // then
    assertThat(found).isSameAs(academy);
    verify(authResolver).verifyOwnsAcademy(academy);
  }

  @Test
  @DisplayName("requireOwnedAcademyByHandle: 소유자가 아니면 403")
  void requireOwnedAcademyByHandle_throwsWhenNotOwner() {
    // given: 다른 학원의 TUTOR — 단일 지점(verifyOwnsAcademy)이 거절한다
    Academy academy = academy();
    when(academyRepository.findByHandle("edu-holic")).thenReturn(Optional.of(academy));
    doThrow(new AccessDeniedException("자기 Academy만 수정할 수 있습니다."))
        .when(authResolver).verifyOwnsAcademy(any());

    // when & then
    assertThatThrownBy(() -> service.requireOwnedAcademyByHandle("edu-holic"))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("requireOwnedAcademyByHandle: handle이 없으면 404")
  void requireOwnedAcademyByHandle_throwsNotFoundWhenHandleUnknown() {
    // given
    when(academyRepository.findByHandle("ghost")).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.requireOwnedAcademyByHandle("ghost"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("requireOwnedAcademyByHandle: 미발행 학원도 소유자에게는 열린다")
  void requireOwnedAcademyByHandle_allowsUnpublishedForOwner() {
    // given: owner는 발행 전에도 운영 화면을 써야 한다 → 공개 필터 없는 findByHandle을 쓴다
    Academy academy = academy();
    when(academyRepository.findByHandle("edu-holic")).thenReturn(Optional.of(academy));

    // when
    service.requireOwnedAcademyByHandle("edu-holic");

    // then: 공개 조회 메서드는 쓰이지 않았다
    verify(academyRepository).findByHandle("edu-holic");
  }

  @Test
  @DisplayName("getPublishedEntityByHandle: 공개 학원이면 돌려준다")
  void getPublishedEntityByHandle_returnsPublishedAcademy() {
    // given
    Academy academy = academy();
    when(academyRepository.findByHandleAndStatusAndVisibleTrue("edu-holic", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.of(academy));

    // when
    Academy found = service.getPublishedEntityByHandle("edu-holic");

    // then
    assertThat(found).isSameAs(academy);
  }

  @Test
  @DisplayName("getPublishedEntityByHandle: 미발행/숨김 학원은 404 — 공개 화면으로 새지 않는다")
  void getPublishedEntityByHandle_throwsNotFoundWhenUnpublished() {
    // given: 미발행이면 공개 조회는 empty를 돌려준다
    when(academyRepository.findByHandleAndStatusAndVisibleTrue("edu-holic", AcademyStatus.PUBLISHED))
        .thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> service.getPublishedEntityByHandle("edu-holic"))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  private Academy academy() {
    return Academy.builder()
        .tutor(User.builder()
            .loginId("edu-holic")
            .password("x")
            .name("원장")
            .role(Role.TUTOR)
            .build())
        .slug("edu-holic-academy")
        .handle("edu-holic")
        .name("압구정에듀홀릭학원")
        .logoStorageKey("logo")
        .intro("소개")
        .status(AcademyStatus.PUBLISHED)
        .build();
  }
}
