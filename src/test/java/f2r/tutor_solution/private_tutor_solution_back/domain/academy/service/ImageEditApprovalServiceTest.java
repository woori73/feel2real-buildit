package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos.ApproveImageRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyWorkingCopyRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 승인 플로우 안전 규칙: 승인 전 차단 · 거부 시 미반영 · 타 학원 차단. */
class ImageEditApprovalServiceTest {

  AcademyWorkingCopyService workingCopy = mock(AcademyWorkingCopyService.class);
  AcademyWorkingCopyRepository wcRepo = mock(AcademyWorkingCopyRepository.class);
  AcademyRepository academyRepo = mock(AcademyRepository.class);
  ImageEditApprovalService service;
  AuthenticatedPrincipal tutorA = mock(AuthenticatedPrincipal.class);
  AuthenticatedPrincipal tutorB = mock(AuthenticatedPrincipal.class);

  @BeforeEach
  void setUp() {
    service = new ImageEditApprovalService(workingCopy, wcRepo, academyRepo);
    when(tutorA.userId()).thenReturn(1L);
    when(tutorB.userId()).thenReturn(2L);
    Academy a = mock(Academy.class);
    Academy b = mock(Academy.class);
    when(a.getAcademyId()).thenReturn(10L);
    when(b.getAcademyId()).thenReturn(20L);
    when(academyRepo.findByTutor_UserId(1L)).thenReturn(Optional.of(a));
    when(academyRepo.findByTutor_UserId(2L)).thenReturn(Optional.of(b));
    when(wcRepo.findByAcademyId(anyLong())).thenReturn(Optional.empty());
  }

  @Test
  void approve_beforeEditRegistered_isRejected() {
    // given: 편집 등록 없음
    // when/then
    assertThatThrownBy(() -> service.approve(tutorA, new ApproveImageRequest(99L, true, null)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(workingCopy, never()).stageMedia(any(), anyLong(), anyString(), anyString());
  }

  @Test
  void editedImage_isNotExposedUntilApproved_andRejectNeverStages() {
    // given
    service.registerEditedImage(1L, 10L, "HERO_BANNER", "10/a.png");
    // when
    boolean exposedBefore = service.isExposedToHomepage(1L);
    service.approve(tutorA, new ApproveImageRequest(1L, false, "no"));
    // then
    assertThat(exposedBefore).isFalse();
    assertThat(service.isExposedToHomepage(1L)).isFalse();
    verify(workingCopy, never()).stageMedia(any(), anyLong(), anyString(), anyString());
  }

  @Test
  void approve_ownAcademy_stagesToWorkingCopyOnly() {
    // given
    service.registerEditedImage(2L, 10L, "HERO_BANNER", "10/a.png");
    // when
    service.approve(tutorA, new ApproveImageRequest(2L, true, null));
    // then
    verify(workingCopy).stageMedia(tutorA, 0L, "HERO_BANNER", "10/a.png");
    assertThat(service.isExposedToHomepage(2L)).isTrue();
  }

  @Test
  void approve_otherAcademyAsset_isBlocked() {
    // given: 학원 10 소유 편집본
    service.registerEditedImage(3L, 10L, "HERO_BANNER", "10/secret.png");
    // when/then: 학원 20 튜터가 승인 시도
    assertThatThrownBy(() -> service.approve(tutorB, new ApproveImageRequest(3L, true, null)))
        .isInstanceOf(SecurityException.class);
    verify(workingCopy, never()).stageMedia(any(), anyLong(), anyString(), anyString());
  }

  @Test
  void getFlowStatus_otherAcademyAsset_isBlocked() {
    // given
    service.registerEditedImage(4L, 10L, "HERO_BANNER", "10/x.png");
    // when/then
    assertThatThrownBy(() -> service.getFlowStatus(tutorB, 4L)).isInstanceOf(SecurityException.class);
  }
}
