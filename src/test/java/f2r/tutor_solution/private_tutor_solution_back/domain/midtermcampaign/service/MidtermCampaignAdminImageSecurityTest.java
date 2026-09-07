package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.service.AcademyService;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermAdminDtos.CampaignUpsert;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermAdminDtos.MaterialUpsert;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaignState;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterialSourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermAfterExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermMakingOfRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermNextExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermPrepStatusRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermSchoolCollectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermUpdateLogRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 운영자 이미지 연결이 (1) 항상 소유 학원 tutor 의 namespace 로만 업로드되고
 * (2) 일반 metadata upsert 가 기존 이미지를 건드리지 않음을 검증한다(§3·§5·§8).
 */
class MidtermCampaignAdminImageSecurityTest {

  private AcademyService academyService;
  private MidtermImageService imageService;
  private MidtermCampaignRepository campaignRepository;
  private MidtermMaterialRepository materialRepository;
  private MidtermCampaignAdminService service;

  private Academy academyA;

  @BeforeEach
  void setUp() {
    academyService = mock(AcademyService.class);
    imageService = mock(MidtermImageService.class);
    campaignRepository = mock(MidtermCampaignRepository.class);
    materialRepository = mock(MidtermMaterialRepository.class);
    MidtermSchoolCollectionRepository schoolCollectionRepository = mock(MidtermSchoolCollectionRepository.class);
    MidtermPrepStatusRepository prepStatusRepository = mock(MidtermPrepStatusRepository.class);
    MidtermUpdateLogRepository updateLogRepository = mock(MidtermUpdateLogRepository.class);
    MidtermMakingOfRepository makingOfRepository = mock(MidtermMakingOfRepository.class);
    MidtermAfterExamRepository afterExamRepository = mock(MidtermAfterExamRepository.class);
    MidtermNextExamRepository nextExamRepository = mock(MidtermNextExamRepository.class);
    AcademyVideoRepository academyVideoRepository = mock(AcademyVideoRepository.class);
    MidtermMaterialAccessService accessService = mock(MidtermMaterialAccessService.class);

    service = new MidtermCampaignAdminService(academyService, academyVideoRepository, imageService,
        accessService, campaignRepository, schoolCollectionRepository, prepStatusRepository,
        materialRepository, updateLogRepository, makingOfRepository, afterExamRepository,
        nextExamRepository);

    // getView() 가 조용히 빈 리스트를 돌려주도록.
    when(schoolCollectionRepository.findByCampaignIdOrderBySortOrderAscSchoolCollectionIdAsc(any()))
        .thenReturn(List.of());
    when(materialRepository.findByCampaignIdOrderBySortOrderAscMidtermMaterialIdAsc(any()))
        .thenReturn(List.of());
    when(updateLogRepository.findByCampaignIdOrderByLogDateDescSortOrderAscUpdateLogIdDesc(any()))
        .thenReturn(List.of());
    when(makingOfRepository.findByCampaignIdOrderBySortOrderAscMakingOfIdAsc(any()))
        .thenReturn(List.of());
    when(prepStatusRepository.findBySchoolCollectionIdInOrderBySortOrderAscPrepStatusIdAsc(anyList()))
        .thenReturn(List.of());

    User tutorA = mock(User.class);
    when(tutorA.getUserId()).thenReturn(42L);
    academyA = mock(Academy.class);
    when(academyA.getAcademyId()).thenReturn(1L);
    when(academyA.getHandle()).thenReturn("eduholic");
    when(academyA.getTutor()).thenReturn(tutorA);
    when(academyService.requireOwnedAcademyByHandle("eduholic")).thenReturn(academyA);
  }

  private MidtermCampaign campaign() {
    MidtermCampaign c = MidtermCampaign.builder().academy(academyA).examName("x").examYear(2026)
        .semester("2학기").examType("중간고사").state(MidtermCampaignState.PREPARING).build();
    when(campaignRepository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(c));
    return c;
  }

  private MultipartFile png() {
    return new MockMultipartFile("image", "x.png", "image/png", new byte[] {1, 2, 3});
  }

  @Test
  void popupVisualUpload_alwaysUsesOwningTutorNamespace() {
    MidtermCampaign c = campaign();
    when(imageService.save(eq(42L), any(), any())).thenReturn("prd/users/42/new.jpg");

    service.uploadPopupVisual("eduholic", png());

    ArgumentCaptor<Long> owner = ArgumentCaptor.forClass(Long.class);
    verify(imageService).save(owner.capture(), any(), any());
    assertThat(owner.getValue()).isEqualTo(42L);          // 소유 학원 tutor id 로만 업로드
    assertThat(c.getPopupVisualKey()).isEqualTo("prd/users/42/new.jpg");
  }

  @Test
  void popupVisualUpload_deletesOldFileOnlyAfterNewKeySaved() {
    MidtermCampaign c = campaign();
    c.updatePopupVisual("prd/users/42/old.jpg");
    when(imageService.save(any(), any(), any())).thenReturn("prd/users/42/new.jpg");

    service.uploadPopupVisual("eduholic", png());

    verify(imageService).delete("prd/users/42/old.jpg");
    assertThat(c.getPopupVisualKey()).isEqualTo("prd/users/42/new.jpg");
  }

  @Test
  void campaignUpsert_neverTouchesPopupVisualKey() {
    MidtermCampaign c = campaign();
    c.updatePopupVisual("prd/users/42/keep.jpg");

    service.upsertCampaign("eduholic", new CampaignUpsert("시험", 2026, "2학기", "중간고사",
        null, null, "PREPARING", null, null, null, null, true, "헤드", "본문"));

    assertThat(c.getPopupVisualKey()).isEqualTo("prd/users/42/keep.jpg");
    verify(imageService, never()).save(any(), any(), any());
  }

  @Test
  void metadataUpdatePreservesExistingThumbnail() {
    campaign();
    MidtermMaterial m = MidtermMaterial.builder().campaignId(9L).category("WORKSHEET")
        .displayTitle("t").sourceType(MidtermMaterialSourceType.NONE)
        .visibility(MidtermMaterialVisibility.PRIVATE).build();
    m.updateThumbnail("prd/users/42/thumb.jpg");
    when(materialRepository.findByMidtermMaterialIdAndCampaignId(eq(5L), any()))
        .thenReturn(Optional.of(m));

    service.updateMaterial("eduholic", 5L, new MaterialUpsert(null, "WORKSHEET", "새 제목",
        "설명", null, null, "NONE", null, "PUBLIC", null, 1, true));

    assertThat(m.getThumbnailKey()).isEqualTo("prd/users/42/thumb.jpg");   // 그대로 유지
    assertThat(m.getDisplayTitle()).isEqualTo("새 제목");
  }
}
