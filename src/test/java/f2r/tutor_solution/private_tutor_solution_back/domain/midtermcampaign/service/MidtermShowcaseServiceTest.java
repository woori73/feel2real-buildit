package f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import f2r.tutor_solution.private_tutor_solution_back.domain.academy.entity.Academy;
import f2r.tutor_solution.private_tutor_solution_back.domain.academy.repository.AcademyRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermShowcaseDtos.ShowcaseScene;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.dto.MidtermWikiSourceDtos.WikiSourceRow;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaign;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermCampaignState;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.entity.MidtermWikiSourceExposure;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermCampaignRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.midtermcampaign.repository.MidtermWikiSourceExposureRepository;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.Role;
import f2r.tutor_solution.private_tutor_solution_back.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MidtermShowcaseServiceTest {

  private AcademyRepository academyRepository;
  private MidtermCampaignRepository campaignRepository;
  private MidtermWikiSourceExposureRepository exposureRepository;
  private MidtermWikiSourceGateway gateway;
  private MidtermShowcaseService service;
  private MidtermCampaign campaign;

  @BeforeEach
  void setUp() {
    academyRepository = mock(AcademyRepository.class);
    campaignRepository = mock(MidtermCampaignRepository.class);
    exposureRepository = mock(MidtermWikiSourceExposureRepository.class);
    gateway = mock(MidtermWikiSourceGateway.class);
    service = new MidtermShowcaseService(
        academyRepository, campaignRepository, exposureRepository, gateway);

    User tutor = mock(User.class);
    when(tutor.getUserId()).thenReturn(7L);
    when(tutor.getLoginId()).thenReturn("t");
    when(tutor.getRole()).thenReturn(Role.TUTOR);
    Academy academy = mock(Academy.class);
    when(academy.getAcademyId()).thenReturn(1L);
    when(academy.getTutor()).thenReturn(tutor);
    when(academyRepository.findByHandle("eduholic")).thenReturn(Optional.of(academy));

    campaign = MidtermCampaign.builder()
        .academy(academy).examName("2026 2학기 중간고사").examYear(2026)
        .semester("2학기").examType("중간고사")
        .state(MidtermCampaignState.PREPARING).published(true).build();
    setId(campaign, "campaignId", 10L);
    when(campaignRepository.findByAcademy_AcademyId(1L)).thenReturn(Optional.of(campaign));
  }

  private WikiSourceRow row(long id, String school, String grade) {
    return new WikiSourceRow("school-print", id, "", school, grade,
        2026, "2학기", "중간고사", "2026-09-0" + id + "T00:00:00");
  }

  private MidtermWikiSourceExposure exposure(long id) {
    return MidtermWikiSourceExposure.builder()
        .campaignId(10L).sourceKind("school-print").sourceId(id)
        .visible(true).featured(false).sortOrder(0).build();
  }

  private void wikiHtml(long id, String html) {
    when(gateway.fetchHtml(any(), eq("school-print"), eq(id)))
        .thenReturn(Optional.of(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  @Test
  void buildsSceneWithIdentityAndExtractedText() {
    when(gateway.listCandidates(any(), any())).thenReturn(List.of(row(2, "신구중", "2학년")));
    when(exposureRepository.findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(10L))
        .thenReturn(List.of(exposure(2)));
    wikiHtml(2, """
        <html><head><title>신구중 2학년 5과 학습지 - 관계대명사</title></head>
        <body><h2>Lesson 5</h2>
        <p>The cat that followed us to school was black and white.</p></body></html>
        """);

    List<ShowcaseScene> scenes = service.getShowcase("eduholic");

    assertThat(scenes).hasSize(1);
    ShowcaseScene s = scenes.get(0);
    assertThat(s.school()).isEqualTo("신구중");
    assertThat(s.grade()).isEqualTo(2);
    assertThat(s.examLabel()).isEqualTo("중간고사");
    assertThat(s.lesson()).isEqualTo("Lesson 5");
    assertThat(s.grammarPoints()).contains("관계대명사");
    assertThat(s.sampleSentences()).isNotEmpty();
    assertThat(s.sourceId()).isEqualTo("school-print-2");
  }

  @Test
  void oneScenePerSchoolGrade_representativeIsFirstVisible() {
    when(gateway.listCandidates(any(), any()))
        .thenReturn(List.of(row(2, "신구중", "2학년"), row(9, "신구중", "2학년")));
    when(exposureRepository.findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(10L))
        .thenReturn(List.of(exposure(2), exposure(9)));
    wikiHtml(2, "<html><head><title>Lesson 3</title></head><body></body></html>");
    wikiHtml(9, "<html><head><title>Lesson 8</title></head><body></body></html>");

    List<ShowcaseScene> scenes = service.getShowcase("eduholic");

    assertThat(scenes).hasSize(1);
    assertThat(scenes.get(0).lesson()).isEqualTo("Lesson 3");   // 첫 visible = 대표
  }

  @Test
  void identityPresentEvenWhenExtractionEmpty() {
    when(gateway.listCandidates(any(), any())).thenReturn(List.of(row(2, "압구정중", "3학년")));
    when(exposureRepository.findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(10L))
        .thenReturn(List.of(exposure(2)));
    when(gateway.fetchHtml(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
        .thenReturn(Optional.empty());

    List<ShowcaseScene> scenes = service.getShowcase("eduholic");

    assertThat(scenes).hasSize(1);
    assertThat(scenes.get(0).school()).isEqualTo("압구정중");
    assertThat(scenes.get(0).grade()).isEqualTo(3);
    assertThat(scenes.get(0).lesson()).isNull();
    assertThat(scenes.get(0).grammarPoints()).isEmpty();
  }

  @Test
  void unpublishedCampaign_returnsEmpty() {
    setPublished(false);
    assertThat(service.getShowcase("eduholic")).isEmpty();
  }

  @Test
  void noCandidates_returnsEmpty() {
    when(gateway.listCandidates(any(), any())).thenReturn(List.of());
    assertThat(service.getShowcase("eduholic")).isEmpty();
  }

  @Test
  void exposureWithoutMatchingCandidate_isSkipped_noWrongSchool() {
    when(gateway.listCandidates(any(), any())).thenReturn(List.of(row(2, "신구중", "2학년")));
    when(exposureRepository.findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(10L))
        .thenReturn(List.of(exposure(99)));   // 후보에 없는 id

    assertThat(service.getShowcase("eduholic")).isEmpty();
  }

  @Test
  void gatewayFailure_returnsEmptyNotError() {
    when(gateway.listCandidates(any(), any())).thenThrow(new RuntimeException("wiki down"));
    assertThat(service.getShowcase("eduholic")).isEmpty();
  }

  @Test
  void examLabelFallsBackWhenExtractionEmpty_stillHasIdentity() {
    when(gateway.listCandidates(any(), any())).thenReturn(List.of(row(2, "언북중", "3학년")));
    when(exposureRepository.findByCampaignIdAndVisibleTrueOrderBySortOrderAscExposureIdAsc(10L))
        .thenReturn(List.of(exposure(2)));
    when(gateway.fetchHtml(any(), any(), org.mockito.ArgumentMatchers.anyLong()))
        .thenThrow(new RuntimeException("html fetch failed"));

    List<ShowcaseScene> scenes = service.getShowcase("eduholic");
    assertThat(scenes).hasSize(1);
    assertThat(scenes.get(0).school()).isEqualTo("언북중");
    assertThat(scenes.get(0).examLabel()).isEqualTo("중간고사");
  }

  private void setPublished(boolean p) {
    try {
      var f = MidtermCampaign.class.getDeclaredField("published");
      f.setAccessible(true);
      f.setBoolean(campaign, p);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
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
}
