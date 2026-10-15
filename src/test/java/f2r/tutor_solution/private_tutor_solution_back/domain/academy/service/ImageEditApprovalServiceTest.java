package f2r.tutor_solution.private_tutor_solution_back.domain.academy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.JwtTokenProvider.AuthenticatedPrincipal;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.dto.ImageEditFlowDtos.ApproveImageRequest;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.AcademyImageAsset;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyImageAssetRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyWorkingCopyRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 승인 플로우 안전 규칙: 승인 전 미반영 · 실제 저장 키로 반영 · 거부 · 타 학원 차단 · DB 기반 상태. */
class ImageEditApprovalServiceTest {

  AcademyWorkingCopyService workingCopy = mock(AcademyWorkingCopyService.class);
  AcademyWorkingCopyRepository wcRepo = mock(AcademyWorkingCopyRepository.class);
  AcademyService academyService = mock(AcademyService.class);
  AcademyImageAssetRepository assets = mock(AcademyImageAssetRepository.class);
  ImageEditApprovalService service;
  AuthenticatedPrincipal tutorA = mock(AuthenticatedPrincipal.class);
  AuthenticatedPrincipal tutorB = mock(AuthenticatedPrincipal.class);
  Academy academyA = mock(Academy.class);
  Academy academyB = mock(Academy.class);

  @BeforeEach
  void setUp() {
    service = new ImageEditApprovalService(workingCopy, wcRepo, academyService, assets);
    when(academyService.requireMyAcademy(tutorA)).thenReturn(academyA);
    when(academyService.requireMyAcademy(tutorB)).thenReturn(academyB);
    when(academyA.getAcademyId()).thenReturn(10L);
    when(academyB.getAcademyId()).thenReturn(20L);
    when(workingCopy.stagedMedia(any())).thenReturn(Map.of());
    when(wcRepo.findByAcademyId(anyLong())).thenReturn(Optional.empty());
  }

  private AcademyImageAsset editedAsset(long id, long academyId, String editedKey) {
    AcademyImageAsset original = AcademyImageAsset.createOriginal(academyId, "principal", "orig.jpg");
    AcademyImageAsset edited = original.createEditedVersion(editedKey, "밝기 +20");
    edited.markStagedForPreview();
    ReflectionTestUtils.setField(edited, "id", id);
    when(assets.findById(id)).thenReturn(Optional.of(edited));
    return edited;
  }

  @Test
  void approve_unknownAsset_isRejectedAndNothingStaged() {
    // given
    when(assets.findById(99L)).thenReturn(Optional.empty());
    // when/then
    assertThatThrownBy(() -> service.approve(tutorA, new ApproveImageRequest(99L, true, null)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(workingCopy, never()).stageMedia(any(), anyLong(), any(), any());
  }

  @Test
  void beforeApproval_statusIsPreviewStaged_andNothingIsStagedToWorkingCopy() {
    // given
    editedAsset(1L, 10L, "edit-key.png");
    // when
    String status = service.getFlowStatus(tutorA, 1L).flowStatus();
    // then
    assertThat(status).isEqualTo("PREVIEW_STAGED");
    verify(workingCopy, never()).stageMedia(any(), anyLong(), any(), any());
  }

  @Test
  void approve_stagesRealStorageKey_notPublicUrl() {
    // given
    editedAsset(2L, 10L, "u1/academy-hero-principal-edit.png");
    // when
    var res = service.approve(tutorA, new ApproveImageRequest(2L, true, null));
    // then
    assertThat(res.status()).isEqualTo("STAGED");
    verify(workingCopy).stageMedia(tutorA, 0L, "principal", "u1/academy-hero-principal-edit.png");
    verify(assets).save(any());
  }

  @Test
  void approve_twice_isBlockedOnceStaged() {
    // given: 작업본 예약이 이미 이 편집본을 가리킴 (재시작 후에도 DB에서 계산)
    editedAsset(3L, 10L, "k.png");
    when(workingCopy.stagedMedia(tutorA)).thenReturn(Map.of("principal", "k.png"));
    // when/then
    assertThatThrownBy(() -> service.approve(tutorA, new ApproveImageRequest(3L, true, null)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.getFlowStatus(tutorA, 3L).flowStatus()).isEqualTo("STAGED");
  }

  @Test
  void reject_neverStages_andStatusBecomesRejected() {
    // given
    editedAsset(4L, 10L, "k.png");
    // when
    var res = service.approve(tutorA, new ApproveImageRequest(4L, false, "no"));
    // then
    assertThat(res.status()).isEqualTo("REJECTED");
    verify(workingCopy, never()).stageMedia(any(), anyLong(), any(), any());
  }

  @Test
  void reject_afterApproval_unstagesWithoutTouchingPublished() {
    // given: 승인되어 작업본 예약 상태
    editedAsset(5L, 10L, "k.png");
    when(workingCopy.stagedMedia(tutorA)).thenReturn(Map.of("principal", "k.png"));
    // when
    service.approve(tutorA, new ApproveImageRequest(5L, false, "원본 복원"));
    // then
    verify(workingCopy).stageMedia(eq(tutorA), eq(0L), eq("principal"), eq(null));
  }

  @Test
  void approve_otherAcademyAsset_isBlocked() {
    // given: 학원 10 소유 편집본
    editedAsset(6L, 10L, "secret.png");
    // when/then: 학원 20 튜터가 승인 시도
    assertThatThrownBy(() -> service.approve(tutorB, new ApproveImageRequest(6L, true, null)))
        .isInstanceOf(SecurityException.class);
    verify(workingCopy, never()).stageMedia(any(), anyLong(), any(), any());
  }

  @Test
  void getFlowStatus_otherAcademyAsset_isBlocked() {
    // given
    editedAsset(7L, 10L, "x.png");
    // when/then
    assertThatThrownBy(() -> service.getFlowStatus(tutorB, 7L)).isInstanceOf(SecurityException.class);
  }
}
