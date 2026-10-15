package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.config.security.AuthenticatedUserResolver;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyVideoRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermPublicDtos.CampaignView;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaignState;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterial;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterialSourceType;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermMaterialVisibility;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermSchoolCollection;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermMakingOfRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermMaterialRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermNextExamRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermPrepStatusRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermSchoolCollectionRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermUpdateLogRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 공개 aggregate 가 published 하위 행만, PRIVATE 자료 제외, 정렬 순서로 조립되는지. */
class MidtermCampaignPublicServiceTest {

  private AcademyRepository academyRepository;
  private MidtermCampaignRepository campaignRepository;
  private MidtermSchoolCollectionRepository schoolCollectionRepository;
  private MidtermPrepStatusRepository prepStatusRepository;
  private MidtermMaterialRepository materialRepository;
  private MidtermUpdateLogRepository updateLogRepository;
  private MidtermNextExamRepository nextExamRepository;
  private MidtermCampaignPublicService service;

  private Academy academy;

  @BeforeEach
  void setUp() {
    academyRepository = mock(AcademyRepository.class);
    AcademyVideoRepository academyVideoRepository = mock(AcademyVideoRepository.class);
    campaignRepository = mock(MidtermCampaignRepository.class);
    schoolCollectionRepository = mock(MidtermSchoolCollectionRepository.class);
    prepStatusRepository = mock(MidtermPrepStatusRepository.class);
    materialRepository = mock(MidtermMaterialRepository.class);
    updateLogRepository = mock(MidtermUpdateLogRepository.class);
    nextExamRepository = mock(MidtermNextExamRepository.class);
    MidtermMakingOfRepository makingOfRepository = mock(MidtermMakingOfRepository.class);
    AuthenticatedUserResolver authResolver = mock(AuthenticatedUserResolver.class);
    when(authResolver.currentPrincipal()).thenReturn(Optional.empty());

    MidtermMaterialAccessService accessService = new MidtermMaterialAccessService(
        authResolver, mock(f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.repository.WikiAnalysisMaterialRepository.class),
        mock(f2r.tutor_solution.private_tutor_solution_back.domain.wikianalysis.service.WikiAnalysisMaterialService.class));
    MidtermImageService imageService = mock(MidtermImageService.class);

    service = new MidtermCampaignPublicService(academyRepository, academyVideoRepository,
        campaignRepository, schoolCollectionRepository, prepStatusRepository, materialRepository,
        updateLogRepository, nextExamRepository, accessService, imageService, authResolver);

    academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(1L);
    when(academy.getHandle()).thenReturn("eduholic");
    when(academy.getSlug()).thenReturn("eduholic");
    when(academyRepository.findByHandle("eduholic")).thenReturn(Optional.of(academy));
    when(prepStatusRepository.findBySchoolCollectionIdInOrderBySortOrderAscPrepStatusIdAsc(any()))
        .thenReturn(List.of());
    when(updateLogRepository
        .findByCampaignIdAndPublishedTrueOrderByLogDateDescSortOrderAscUpdateLogIdDesc(anyLong()))
        .thenReturn(List.of());
    when(nextExamRepository.findByCampaignId(anyLong())).thenReturn(Optional.empty());
  }

  private MidtermCampaign publishedCampaign() {
    MidtermCampaign c = MidtermCampaign.builder()
        .academy(academy).examName("2026 2학기 중간고사").examYear(2026).semester("2학기")
        .examType("중간고사").state(MidtermCampaignState.PREPARING).published(true).build();
    setId(c, "campaignId", 10L);
    return c;
  }

  private static void setId(Object entity, String field, Long value) {
    try {
      var f = entity.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(entity, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void unpublishedCampaign_returnsEmpty() {
    MidtermCampaign c = MidtermCampaign.builder()
        .academy(academy).examName("x").examYear(2026).semester("2학기").examType("중간고사")
        .state(MidtermCampaignState.PREPARING).published(false).build();
    when(campaignRepository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(c));

    assertThat(service.findPublishedByHandle("eduholic")).isEmpty();
  }

  @Test
  void missingCampaign_returnsEmpty() {
    when(campaignRepository.findByAcademy_AcademyId(1L)).thenReturn(Optional.empty());
    assertThat(service.findPublishedByHandle("eduholic")).isEmpty();
  }

  @Test
  void publishedCampaign_excludesPrivateMaterials_andKeepsCollectionOrder() {
    MidtermCampaign campaign = publishedCampaign();
    when(campaignRepository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(campaign));

    MidtermSchoolCollection first = collection("신구중", 3, 0);
    MidtermSchoolCollection second = collection("신사중", 3, 1);
    when(schoolCollectionRepository
        .findByCampaignIdAndPublishedTrueOrderBySortOrderAscSchoolCollectionIdAsc(10L))
        .thenReturn(List.of(first, second));

    MidtermMaterial pub = material("공개 학습지", MidtermMaterialVisibility.PUBLIC, 100L);
    MidtermMaterial priv = material("비공개 원본", MidtermMaterialVisibility.PRIVATE, 101L);
    when(materialRepository
        .findByCampaignIdAndPublishedTrueOrderBySortOrderAscMidtermMaterialIdAsc(10L))
        .thenReturn(List.of(pub, priv));

    CampaignView view = service.findPublishedByHandle("eduholic").orElseThrow();

    assertThat(view.schoolCollections()).extracting("schoolName").containsExactly("신구중", "신사중");
    assertThat(view.campaignMaterials()).extracting("displayTitle").containsExactly("공개 학습지");
    assertThat(view.campaignMaterials().get(0).fileUrl()).isNotNull();
  }

  private MidtermSchoolCollection collection(String name, int grade, int order) {
    MidtermSchoolCollection c = MidtermSchoolCollection.builder()
        .campaignId(10L).schoolName(name).grade(grade).sortOrder(order).published(true).build();
    setId(c, "schoolCollectionId", (long) (order + 1));
    return c;
  }

  private MidtermMaterial material(String title, MidtermMaterialVisibility vis, long id) {
    MidtermMaterial m = MidtermMaterial.builder()
        .campaignId(10L).category("WORKSHEET").displayTitle(title)
        .sourceType(MidtermMaterialSourceType.WIKI_ANALYSIS).sourceMaterialId(id)
        .visibility(vis).published(true).sortOrder(0).build();
    setId(m, "midtermMaterialId", id);
    return m;
  }
}
